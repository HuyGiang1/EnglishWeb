package com.englishflow.vocabulary;

import com.englishflow.review.ReviewService;
import com.englishflow.vocabulary.dto.*;
import com.englishflow.vocabulary.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PasteImportService {
    private final PasteImportParser parser;
    private final VocabularyRepository words;
    private final CollectionRepository collections;
    private final VocabularyCollectionRepository memberships;
    private final UserVocabularyLockCoordinator locks;
    private final ReviewService reviews;

    public PasteImportService(PasteImportParser parser, VocabularyRepository words, CollectionRepository collections,
        VocabularyCollectionRepository memberships, UserVocabularyLockCoordinator locks, ReviewService reviews) {
        this.parser = parser; this.words = words; this.collections = collections;
        this.memberships = memberships; this.locks = locks; this.reviews = reviews;
    }

    private record Key(String term, String pos) {}
    private record Assessment(List<PastePreviewRow> rows, Map<Key, List<Vocabulary>> existing) {}

    private UUID destination(UUID owner, String value, boolean lock) {
        if (value == null) return null;
        UUID id;
        try {
            id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) { throw new BulkImportException("INVALID_DESTINATION", "Select a valid Study Set."); }
        (lock ? collections.findByIdAndUserIdForUpdate(id, owner) : collections.findByIdAndUserId(id, owner))
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found."));
        return id;
    }

    @Transactional(readOnly = true)
    public PastePreviewResponse preview(UUID owner, PasteImportRequest request) {
        UUID collection = destination(owner, request.collectionId(), false);
        PasteImportParser.Parsed parsed = parser.parse(request);
        Assessment result = assess(owner, collection, parsed.rows().stream().map(PasteImportParser.SourceRow::item).toList());
        Map<Integer, PasteImportParser.SourceRow> originals = parsed.rows().stream()
            .collect(Collectors.toMap(PasteImportParser.SourceRow::line, row -> row));
        List<PastePreviewRow> enriched = result.rows().stream().map(row -> {
            var source = originals.get(row.rowNumber());
            List<String> issues = new ArrayList<>(row.issues()); issues.addAll(source.warnings());
            return new PastePreviewRow(row.rowNumber(), source.cells(), row.term(), row.vietnameseMeaning(),
                row.phoneticIpa(), row.partOfSpeech(), row.excluded(), row.status(), issues,
                row.existingMeaning(), row.existingIpa());
        }).toList();
        return new PastePreviewResponse(parsed.delimiter(), parsed.mapping(), parsed.warnings(), enriched);
    }

    @Transactional(readOnly = true)
    public PastePreviewResponse validate(UUID owner, PasteBatchRequest request) {
        UUID collection = destination(owner, request.collectionId(), false);
        return new PastePreviewResponse("EDITED", List.of(), List.of(), assess(owner, collection, request.rows()).rows());
    }

    private BulkImportParser.ValidationResult validation(PasteImportRow row) {
        return BulkImportParser.validateFields(row.term(), row.vietnameseMeaning(), row.partOfSpeech(),
            null, row.phoneticIpa(), null, null, null, null, null);
    }
    private String trim(String value) { return value == null ? "" : value.trim(); }

    private List<String> issues(PasteImportRow row, BulkImportParser.ValidationResult result) {
        List<String> issues = new ArrayList<>();
        if (trim(row.term()).isEmpty()) issues.add("MISSING_TERM");
        if (trim(row.vietnameseMeaning()).isEmpty()) issues.add("MISSING_MEANING");
        if (trim(row.term()).length() > 100) issues.add("TERM_TOO_LONG");
        if (trim(row.vietnameseMeaning()).length() > 500) issues.add("MEANING_TOO_LONG");
        if (trim(row.phoneticIpa()).length() > 100) issues.add("IPA_TOO_LONG");
        String pos = trim(row.partOfSpeech()).toLowerCase(Locale.ROOT);
        pos = BulkImportParser.POS_ABBREVIATIONS.getOrDefault(pos, pos);
        if (!pos.isEmpty() && !BulkImportParser.VALID_PARTS_OF_SPEECH.contains(pos)) issues.add("INVALID_POS");
        if (!result.isValid() && issues.isEmpty()) issues.add("INVALID_FIELDS");
        return issues;
    }

    private Assessment assess(UUID owner, UUID collection, List<PasteImportRow> input) {
        if (input == null || input.isEmpty()) fail("NO_ITEMS");
        if (input.size() > BulkImportParser.MAX_ROW_LIMIT) fail("ROW_LIMIT_EXCEEDED");
        Set<Integer> numbers = new HashSet<>();
        for (PasteImportRow row : input) {
            if (row == null || row.rowNumber() < 1 || row.rowNumber() > PasteImportParser.MAX_TEXT_BYTES + 1 || !numbers.add(row.rowNumber()))
                fail("INVALID_ITEM_DATA");
        }
        List<String> terms = input.stream().filter(row -> !row.excluded() && validation(row).isValid())
            .map(row -> Vocabulary.normalize(row.term())).distinct().toList();
        Map<Key, List<Vocabulary>> existing = terms.isEmpty() ? Map.of() :
            words.findActiveByTerms(owner, terms).stream().collect(Collectors.groupingBy(word -> new Key(word.getNormalizedTerm(), word.getPartOfSpeech())));
        List<UUID> candidateIds = existing.values().stream().filter(list -> list.size() == 1).map(list -> list.getFirst().getId()).toList();
        Set<UUID> linked = collection == null || candidateIds.isEmpty() ? Set.of()
            : new HashSet<>(memberships.findVocabularyIdsByCollectionAndUser(collection, owner, candidateIds));
        Set<Key> seen = new HashSet<>();
        List<PastePreviewRow> result = new ArrayList<>();
        for (PasteImportRow row : input) {
            var valid = validation(row);
            List<String> issues = row.excluded() ? new ArrayList<>() : issues(row, valid);
            Key key = new Key(Vocabulary.normalize(row.term()), valid.normalizedPos());
            List<Vocabulary> candidates = existing.getOrDefault(key, List.of());
            Vocabulary current = candidates.size() == 1 ? candidates.getFirst() : null;
            String status;
            if (row.excluded()) status = "EXCLUDED";
            else if (!issues.isEmpty()) status = "INVALID";
            else if (candidates.size() > 1) { status = "AMBIGUOUS_EXISTING"; issues.add("AMBIGUOUS_EXISTING"); }
            else if (!seen.add(key)) { status = "DUPLICATE_INPUT"; issues.add("FIRST_ROW_KEPT"); }
            else if (current == null) status = "NEW";
            else {
                status = collection == null || linked.contains(current.getId()) ? "ALREADY_PRESENT" : "EXISTING";
                if (!trim(row.vietnameseMeaning()).equals(trim(current.getVietnameseMeaning()))
                    || (!trim(row.phoneticIpa()).isEmpty() && !trim(row.phoneticIpa()).equals(trim(current.getPhoneticIpa()))))
                    issues.add("PRESERVE_EXISTING");
            }
            result.add(new PastePreviewRow(row.rowNumber(), List.of(), trim(row.term()), trim(row.vietnameseMeaning()),
                trim(row.phoneticIpa()), valid.isValid() ? valid.normalizedPos() : trim(row.partOfSpeech()),
                row.excluded(), status, List.copyOf(issues),
                current == null ? null : current.getVietnameseMeaning(), current == null ? null : current.getPhoneticIpa()));
        }
        return new Assessment(result, existing);
    }

    @Transactional
    public PasteCommitResponse confirm(UUID owner, PasteBatchRequest request) {
        if (!"PRESERVE_EXISTING".equals(request.conflictPolicy())) fail("CONFLICT_POLICY_REQUIRED");
        locks.acquireUserVocabularyLock(owner);
        UUID collection = destination(owner, request.collectionId(), true);
        Assessment checked = assess(owner, collection, request.rows());
        if (checked.rows().stream().anyMatch(row -> Set.of("INVALID", "AMBIGUOUS_EXISTING").contains(row.status()))) fail("INVALID_ITEM_DATA");
        if (checked.rows().stream().allMatch(PastePreviewRow::excluded)) fail("NO_ITEMS");
        int created = 0, linked = 0, present = 0, duplicates = 0, excluded = 0;
        Instant now = Instant.now();
        for (PastePreviewRow row : checked.rows()) {
            if (row.excluded()) { excluded++; continue; }
            if ("DUPLICATE_INPUT".equals(row.status())) { duplicates++; continue; }
            if ("ALREADY_PRESENT".equals(row.status())) { present++; continue; }
            Vocabulary vocabulary;
            if ("NEW".equals(row.status())) {
                vocabulary = words.save(new Vocabulary(owner, row.term(), row.partOfSpeech(), row.phoneticIpa().isBlank() ? null : row.phoneticIpa(),
                    row.vietnameseMeaning(), null, null, null, null, null, null));
                reviews.provisionCardForVocabulary(owner, vocabulary.getId(), null);
                created++;
            } else {
                vocabulary = checked.existing().get(new Key(Vocabulary.normalize(row.term()), row.partOfSpeech())).getFirst();
                linked++;
            }
            if (collection != null) memberships.save(new VocabularyCollection(owner, collection, vocabulary.getId(), now));
        }
        if (collection != null && created + linked > 0)
            collections.findByIdAndUserId(collection, owner).orElseThrow().setUpdatedAt(now);
        // Flush within this transaction so constraint failures roll back the complete batch.
        words.flush(); memberships.flush();
        return new PasteCommitResponse(created, linked, present, duplicates, excluded, 0, collection);
    }
    private static void fail(String code) { throw new BulkImportException(code, "Review the batch, destination and import policy before confirming."); }
}
