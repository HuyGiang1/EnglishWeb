package com.englishflow.vocabulary;

import com.englishflow.review.ReviewService;
import com.englishflow.vocabulary.dto.*;
import com.englishflow.vocabulary.exception.BulkImportException;
import com.englishflow.vocabulary.exception.CollectionNotFoundException;
import com.englishflow.vocabulary.exception.DuplicateVocabularyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class BulkImportService {

    private static final Logger log = LoggerFactory.getLogger(BulkImportService.class);

    private final BulkImportParser bulkImportParser;
    private final VocabularyRepository vocabularyRepository;
    private final CollectionRepository collectionRepository;
    private final VocabularyCollectionRepository vocabularyCollectionRepository;
    private final ReviewService reviewService;
    private final UserVocabularyLockCoordinator userVocabularyLockCoordinator;

    public BulkImportService(
        BulkImportParser bulkImportParser,
        VocabularyRepository vocabularyRepository,
        CollectionRepository collectionRepository,
        VocabularyCollectionRepository vocabularyCollectionRepository,
        ReviewService reviewService,
        UserVocabularyLockCoordinator userVocabularyLockCoordinator
    ) {
        this.bulkImportParser = bulkImportParser;
        this.vocabularyRepository = vocabularyRepository;
        this.collectionRepository = collectionRepository;
        this.vocabularyCollectionRepository = vocabularyCollectionRepository;
        this.reviewService = reviewService;
        this.userVocabularyLockCoordinator = userVocabularyLockCoordinator;
    }

    /**
     * Dry-run preview: parses file and validates rows against user library without writing to DB.
     */
    @Transactional(readOnly = true)
    public BulkImportPreviewResponse previewImport(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BulkImportException("EMPTY_FILE", "Please select a valid CSV or TSV file to upload.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BulkImportException("FILE_READ_ERROR", "Failed to read uploaded file: " + e.getMessage());
        }

        BulkImportParser.ParsedImportFile parsed = bulkImportParser.parse(bytes, file.getOriginalFilename());

        // Check for duplicates against existing user library in database
        List<BulkImportRowPreview> enrichedRows = new ArrayList<>(parsed.rows().size());
        List<BulkImportRowError> allErrors = new ArrayList<>(parsed.errors());

        for (BulkImportRowPreview row : parsed.rows()) {
            if ("VALID".equals(row.status())) {
                String normalizedTerm = Vocabulary.normalize(row.term());
                List<Vocabulary> existing = vocabularyRepository.findByUserIdAndNormalizedTermAndPartOfSpeechAndDeletedAtIsNull(
                    userId, normalizedTerm, row.partOfSpeech()
                );

                if (!existing.isEmpty()) {
                    enrichedRows.add(new BulkImportRowPreview(
                        row.rowNumber(),
                        row.term(),
                        row.partOfSpeech(),
                        row.vietnameseMeaning(),
                        row.phoneticIpa(),
                        row.englishDefinition(),
                        row.exampleSentence(),
                        row.exampleTranslation(),
                        row.notes(),
                        row.source(),
                        row.difficultyLevel(),
                        "DUPLICATE_LIBRARY",
                        "Already exists in your Vocabulary Library."
                    ));
                    continue;
                }
            }

            enrichedRows.add(row);
        }

        int validCount = (int) enrichedRows.stream().filter(r -> "VALID".equals(r.status())).count();
        int duplicateCount = (int) enrichedRows.stream().filter(r -> r.status().startsWith("DUPLICATE_")).count();
        int errorCount = (int) enrichedRows.stream().filter(r -> "INVALID".equals(r.status())).count();

        List<BulkImportRowPreview> sample = enrichedRows.stream()
            .limit(10)
            .toList();

        return new BulkImportPreviewResponse(
            enrichedRows.size(),
            validCount,
            duplicateCount,
            errorCount,
            allErrors,
            enrichedRows,
            sample
        );
    }

    /**
     * Atomic/Transactional import confirmation: persists vocabulary, provisions SRS cards,
     * assigns to collection (if provided), and inserts ZERO learning activity events.
     * Uses PostgreSQL transaction-scoped advisory locks for cluster-wide concurrency coordination.
     */
    @Transactional
    public BulkImportConfirmResponse confirmImport(UUID userId, BulkImportConfirmRequest request) {
        // 1. Acquire PostgreSQL transaction-scoped advisory lock for this user
        userVocabularyLockCoordinator.acquireUserVocabularyLock(userId);

        if (request.items() == null || request.items().isEmpty()) {
            throw new BulkImportException("NO_ITEMS", "No vocabulary items provided for import.");
        }

        if (request.items().size() > BulkImportParser.MAX_ROW_LIMIT) {
            throw new BulkImportException(
                "ROW_LIMIT_EXCEEDED",
                "Cannot import more than " + BulkImportParser.MAX_ROW_LIMIT + " items in a single request."
            );
        }

        Collection collection = null;
        if (request.collectionId() != null) {
            collection = collectionRepository.findByIdAndUserId(request.collectionId(), userId)
                .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + request.collectionId()));
        }

        boolean rejectOnDuplicate = BulkImportConfirmRequest.POLICY_REJECT.equalsIgnoreCase(request.duplicatePolicy());

        int importedCount = 0;
        int skippedCount = 0;
        int rejectedCount = 0;

        Set<String> seenInBatch = new HashSet<>();
        Instant now = Instant.now();

        for (BulkImportItemRequest item : request.items()) {
            // Independent field validation on every item (never trust client JSON / badges)
            BulkImportParser.ValidationResult validation = BulkImportParser.validateFields(
                item.term(),
                item.vietnameseMeaning(),
                item.partOfSpeech(),
                item.difficultyLevel(),
                item.phoneticIpa(),
                item.englishDefinition(),
                item.exampleSentence(),
                item.exampleTranslation(),
                item.source(),
                item.notes()
            );

            if (!validation.isValid()) {
                throw new BulkImportException(
                    "INVALID_ITEM_DATA",
                    "Item validation failed for '" + (item.term() != null ? item.term().trim() : "") + "': "
                        + String.join("; ", validation.errors())
                );
            }

            String term = item.term().trim();
            String normalized = Vocabulary.normalize(term);
            String pos = validation.normalizedPos();

            String batchKey = normalized + "#" + pos;

            // 1. Check duplicate within the request batch
            if (seenInBatch.contains(batchKey)) {
                if (rejectOnDuplicate) {
                    throw new DuplicateVocabularyException(
                        "Import rejected: duplicate item '" + term + "' (" + pos + ") found within import batch."
                    );
                }
                skippedCount++;
                continue;
            }
            seenInBatch.add(batchKey);

            // 2. Check duplicate against authoritative database state inside transaction
            List<Vocabulary> existing = vocabularyRepository.findByUserIdAndNormalizedTermAndPartOfSpeechAndDeletedAtIsNull(
                userId, normalized, pos
            );

            if (!existing.isEmpty()) {
                if (rejectOnDuplicate) {
                    throw new DuplicateVocabularyException(
                        "Import rejected: vocabulary item '" + term + "' (" + pos + ") already exists in your library."
                    );
                }
                skippedCount++;
                continue;
            }

            // 3. Persist new vocabulary entry
            Vocabulary vocab = new Vocabulary(
                userId,
                term,
                pos,
                item.phoneticIpa() != null ? item.phoneticIpa().trim() : null,
                item.vietnameseMeaning().trim(),
                item.englishDefinition() != null ? item.englishDefinition().trim() : null,
                item.exampleSentence() != null ? item.exampleSentence().trim() : null,
                item.exampleTranslation() != null ? item.exampleTranslation().trim() : null,
                item.notes() != null ? item.notes().trim() : null,
                item.source() != null ? item.source().trim() : null,
                validation.normalizedLevel()
            );

            Vocabulary saved = vocabularyRepository.save(vocab);

            // 4. Provision exactly one SRS card (starts with easeFactor=2.5, interval=0, state=LEARNING/NEW)
            reviewService.provisionCardForVocabulary(userId, saved.getId(), null);

            // 5. Optional collection membership assignment
            if (collection != null) {
                VocabularyCollection vc = new VocabularyCollection(userId, collection.getId(), saved.getId(), now);
                vocabularyCollectionRepository.save(vc);
            }

            importedCount++;
        }

        if (collection != null && importedCount > 0) {
            collection.setUpdatedAt(now);
            collectionRepository.save(collection);
        }

        log.info("Bulk import completed for user {}: {} imported, {} skipped", userId, importedCount, skippedCount);

        return new BulkImportConfirmResponse(importedCount, skippedCount, rejectedCount);
    }

    /**
     * Direct multipart file import with dryRun parameter support (satisfies API_COVERAGE.md).
     */
    @Transactional
    public BulkImportConfirmResponse importDirect(
        UUID userId,
        MultipartFile file,
        UUID collectionId,
        String duplicatePolicy
    ) {
        userVocabularyLockCoordinator.acquireUserVocabularyLock(userId);

        if (file == null || file.isEmpty()) {
            throw new BulkImportException("EMPTY_FILE", "Please select a valid CSV or TSV file to upload.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BulkImportException("FILE_READ_ERROR", "Failed to read uploaded file: " + e.getMessage());
        }

        BulkImportParser.ParsedImportFile parsed = bulkImportParser.parse(bytes, file.getOriginalFilename());

        List<BulkImportItemRequest> items = new ArrayList<>();
        for (BulkImportRowPreview row : parsed.rows()) {
            if ("VALID".equals(row.status())) {
                items.add(new BulkImportItemRequest(
                    row.term(),
                    row.partOfSpeech(),
                    row.vietnameseMeaning(),
                    row.phoneticIpa(),
                    row.englishDefinition(),
                    row.exampleSentence(),
                    row.exampleTranslation(),
                    row.notes(),
                    row.source(),
                    row.difficultyLevel()
                ));
            }
        }

        return confirmImport(userId, new BulkImportConfirmRequest(items, collectionId, duplicatePolicy));
    }

    /**
     * Downloadable template generation in CSV or TSV format with UTF-8 BOM.
     */
    public byte[] generateTemplate(String format) {
        boolean isTsv = "tsv".equalsIgnoreCase(format) || "tab".equalsIgnoreCase(format);
        String delimiter = isTsv ? "\t" : ",";

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM
        sb.append('\uFEFF');

        // Header
        sb.append(String.join(delimiter,
            "term", "partOfSpeech", "vietnameseMeaning", "phoneticIpa",
            "englishDefinition", "exampleSentence", "exampleTranslation",
            "notes", "source", "difficultyLevel"
        )).append("\n");

        // Row 1
        sb.append(escapeCell("metaphor", delimiter)).append(delimiter)
            .append(escapeCell("noun", delimiter)).append(delimiter)
            .append(escapeCell("phép ẩn dụ", delimiter)).append(delimiter)
            .append(escapeCell("/ˈmet.ə.fɔːr/", delimiter)).append(delimiter)
            .append(escapeCell("A figure of speech.", delimiter)).append(delimiter)
            .append(escapeCell("Her heart is a cold iron.", delimiter)).append(delimiter)
            .append(escapeCell("Trái tim cô ấy là một khối sắt lạnh giá.", delimiter)).append(delimiter)
            .append(escapeCell("Literary term", delimiter)).append(delimiter)
            .append(escapeCell("Oxford Dictionary", delimiter)).append(delimiter)
            .append(escapeCell("B2", delimiter)).append("\n");

        // Row 2
        sb.append(escapeCell("ephemeral", delimiter)).append(delimiter)
            .append(escapeCell("adjective", delimiter)).append(delimiter)
            .append(escapeCell("phù du, chóng tàn", delimiter)).append(delimiter)
            .append(escapeCell("/ɪˈfem.ər.əl/", delimiter)).append(delimiter)
            .append(escapeCell("Lasting for a very short time.", delimiter)).append(delimiter)
            .append(escapeCell("Fame in the internet age is often ephemeral.", delimiter)).append(delimiter)
            .append(escapeCell("Sự nổi tiếng thời đại internet thường rất phù du.", delimiter)).append(delimiter)
            .append(escapeCell("Academic vocabulary", delimiter)).append(delimiter)
            .append(escapeCell("Cambridge Dictionary", delimiter)).append(delimiter)
            .append(escapeCell("C1", delimiter)).append("\n");

        // Row 3
        sb.append(escapeCell("serendipity", delimiter)).append(delimiter)
            .append(escapeCell("noun", delimiter)).append(delimiter)
            .append(escapeCell("sự tình cờ may mắn", delimiter)).append(delimiter)
            .append(escapeCell("/ˌser.ənˈdɪp.ə.ti/", delimiter)).append(delimiter)
            .append(escapeCell("Finding valuable or agreeable things not sought for.", delimiter)).append(delimiter)
            .append(escapeCell("We found each other by pure serendipity.", delimiter)).append(delimiter)
            .append(escapeCell("Chúng tôi tìm thấy nhau hoàn toàn nhờ sự tình cờ may mắn.", delimiter)).append(delimiter)
            .append(escapeCell("Common idiom/noun", delimiter)).append(delimiter)
            .append(escapeCell("Merriam-Webster", delimiter)).append(delimiter)
            .append(escapeCell("C1", delimiter)).append("\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCell(String value, String delimiter) {
        if (value == null) return "";
        if (value.contains(delimiter) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
