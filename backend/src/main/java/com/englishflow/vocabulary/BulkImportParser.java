package com.englishflow.vocabulary;

import com.englishflow.vocabulary.dto.BulkImportRowError;
import com.englishflow.vocabulary.dto.BulkImportRowPreview;
import com.englishflow.vocabulary.exception.BulkImportException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class BulkImportParser {

    public static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB
    public static final int MAX_ROW_LIMIT = 1000;

    public static final Set<String> VALID_PARTS_OF_SPEECH = Set.of(
        "noun", "verb", "adjective", "adverb", "preposition",
        "conjunction", "interjection", "pronoun", "phrase", "idiom", "other"
    );

    public static final Map<String, String> POS_ABBREVIATIONS = Map.of(
        "n", "noun",
        "v", "verb",
        "adj", "adjective",
        "adv", "adverb",
        "prep", "preposition",
        "conj", "conjunction",
        "pron", "pronoun"
    );

    public static final Set<String> VALID_CEFR_LEVELS = Set.of(
        "A1", "A2", "B1", "B2", "C1", "C2"
    );

    // Header aliases (lowercased, spaces and underscores removed)
    private static final Set<String> TERM_ALIASES = Set.of("term", "word", "vocabulary", "english", "englishword", "tuvung", "từvựng");
    private static final Set<String> MEANING_ALIASES = Set.of("vietnamesemeaning", "vietnamese_meaning", "meaning", "definitionvi", "nghia", "nghĩa", "tiengviet", "tiếngviệt");
    private static final Set<String> POS_ALIASES = Set.of("partofspeech", "part_of_speech", "pos", "type", "loaitu", "loạitừ");
    private static final Set<String> IPA_ALIASES = Set.of("phoneticipa", "phonetic_ipa", "ipa", "phonetic", "phienam", "phiênâm");
    private static final Set<String> DEF_ALIASES = Set.of("englishdefinition", "english_definition", "definition", "dinhnghia", "địnhnghĩa");
    private static final Set<String> EX_SENTENCE_ALIASES = Set.of("examplesentence", "example_sentence", "example", "vidu", "ví dụ", "ví_dụ");
    private static final Set<String> EX_TRANS_ALIASES = Set.of("exampletranslation", "example_translation", "translation", "dich", "dịch");
    private static final Set<String> NOTES_ALIASES = Set.of("notes", "note", "ghichu", "ghichú", "ghi_chú");
    private static final Set<String> SOURCE_ALIASES = Set.of("source", "nguon", "nguồn");
    private static final Set<String> LEVEL_ALIASES = Set.of("difficultylevel", "difficulty_level", "level", "cefr");

    public record ValidationResult(
        String normalizedTerm,
        String normalizedPos,
        String normalizedLevel,
        List<String> errors
    ) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    public record ParsedImportFile(
        List<BulkImportRowPreview> rows,
        List<BulkImportRowError> errors,
        int totalRows,
        int validRows,
        int duplicateRows,
        int errorRows
    ) {}

    /**
     * Canonical single-row field validator used by both BulkImportParser (preview)
     * and BulkImportService (confirmation).
     */
    public static ValidationResult validateFields(
        String term,
        String vietnameseMeaning,
        String partOfSpeech,
        String difficultyLevel,
        String phoneticIpa,
        String englishDefinition,
        String exampleSentence,
        String exampleTranslation,
        String source,
        String notes
    ) {
        List<String> errors = new ArrayList<>();

        // Reject illegal NUL control characters that corrupt database string storage
        if (containsNul(term) || containsNul(vietnameseMeaning) || containsNul(partOfSpeech)
            || containsNul(difficultyLevel) || containsNul(phoneticIpa) || containsNul(englishDefinition)
            || containsNul(exampleSentence) || containsNul(exampleTranslation) || containsNul(source)
            || containsNul(notes)) {
            errors.add("Fields cannot contain NUL (\\0) control characters");
        }

        if (term == null || term.isBlank()) {
            errors.add("Missing required 'term'");
        } else if (term.trim().length() > 100) {
            errors.add("Term exceeds 100 characters");
        }

        if (vietnameseMeaning == null || vietnameseMeaning.isBlank()) {
            errors.add("Missing required 'vietnameseMeaning'");
        } else if (vietnameseMeaning.trim().length() > 500) {
            errors.add("Vietnamese meaning exceeds 500 characters");
        }

        String normalizedPos = "other";
        if (partOfSpeech != null && !partOfSpeech.isBlank()) {
            String cleanPos = partOfSpeech.toLowerCase(Locale.ROOT).trim();
            if (POS_ABBREVIATIONS.containsKey(cleanPos)) {
                cleanPos = POS_ABBREVIATIONS.get(cleanPos);
            }
            if (!VALID_PARTS_OF_SPEECH.contains(cleanPos)) {
                errors.add("Invalid part of speech '" + partOfSpeech + "'. Allowed: " + VALID_PARTS_OF_SPEECH);
            } else {
                normalizedPos = cleanPos;
            }
        }

        String normalizedLevel = null;
        if (difficultyLevel != null && !difficultyLevel.isBlank()) {
            String cleanLevel = difficultyLevel.toUpperCase(Locale.ROOT).trim();
            if (!VALID_CEFR_LEVELS.contains(cleanLevel)) {
                errors.add("Invalid CEFR difficulty level '" + difficultyLevel + "'. Allowed: A1, A2, B1, B2, C1, C2");
            } else {
                normalizedLevel = cleanLevel;
            }
        }

        if (phoneticIpa != null && phoneticIpa.trim().length() > 100) {
            errors.add("Phonetic IPA exceeds 100 characters");
        }
        if (englishDefinition != null && englishDefinition.trim().length() > 1000) {
            errors.add("English definition exceeds 1000 characters");
        }
        if (exampleSentence != null && exampleSentence.trim().length() > 1000) {
            errors.add("Example sentence exceeds 1000 characters");
        }
        if (exampleTranslation != null && exampleTranslation.trim().length() > 1000) {
            errors.add("Example translation exceeds 1000 characters");
        }
        if (notes != null && notes.trim().length() > 1000) {
            errors.add("Notes exceed 1000 characters");
        }
        if (source != null && source.trim().length() > 100) {
            errors.add("Source exceeds 100 characters");
        }

        String normalizedTerm = term != null ? term.trim() : null;
        return new ValidationResult(normalizedTerm, normalizedPos, normalizedLevel, errors);
    }

    private static boolean containsNul(String val) {
        return val != null && val.indexOf('\0') >= 0;
    }

    public ParsedImportFile parse(byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw new BulkImportException("EMPTY_FILE", "The uploaded file is empty.");
        }

        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new BulkImportException("FILE_TOO_LARGE", "The file exceeds the maximum allowed size of 2 MB.");
        }

        // Handle UTF-8 BOM (0xEF, 0xBB, 0xBF)
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }

        String content;
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
            CharBuffer charBuffer = decoder.decode(buffer);
            content = charBuffer.toString();
        } catch (CharacterCodingException e) {
            throw new BulkImportException("MALFORMED_UTF8", "The uploaded file contains invalid or malformed UTF-8 byte sequences.");
        }

        if (content.trim().isEmpty()) {
            throw new BulkImportException("EMPTY_FILE", "The uploaded file contains no text data.");
        }

        char delimiter = detectDelimiter(content, originalFilename);

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
            .setDelimiter(delimiter)
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .setIgnoreSurroundingSpaces(true)
            .setAllowMissingColumnNames(true)
            .setAllowDuplicateHeaderNames(false)
            .build();

        List<BulkImportRowPreview> rows = new ArrayList<>();
        List<BulkImportRowError> errors = new ArrayList<>();

        Map<String, String> columnMapping;
        try (CSVParser parser = csvFormat.parse(new BufferedReader(new InputStreamReader(
            new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8
        )))) {
            Map<String, Integer> headerMap = parser.getHeaderMap();
            if (headerMap == null || headerMap.isEmpty()) {
                throw new BulkImportException("MISSING_HEADER", "No valid header row was found in the uploaded file.");
            }

            columnMapping = resolveColumnMapping(headerMap.keySet());
            if (!columnMapping.containsKey("term") || !columnMapping.containsKey("vietnameseMeaning")) {
                throw new BulkImportException(
                    "MISSING_REQUIRED_HEADERS",
                    "Missing required headers: the file must contain 'term' and 'vietnameseMeaning' columns."
                );
            }

            Set<String> seenInFile = new HashSet<>();
            int rowIndex = 1; // 1-based data row counter (header was row 1 in file, first data row is line 2)

            for (CSVRecord record : parser) {
                if (rowIndex > MAX_ROW_LIMIT) {
                    throw new BulkImportException(
                        "ROW_LIMIT_EXCEEDED",
                        "The uploaded file exceeds the maximum allowed limit of " + MAX_ROW_LIMIT + " rows."
                    );
                }

                int displayRowNumber = (int) record.getRecordNumber() + 1; // matching actual file line number

                String term = getRecordValue(record, columnMapping.get("term"));
                String vietnameseMeaning = getRecordValue(record, columnMapping.get("vietnameseMeaning"));
                String partOfSpeech = getRecordValue(record, columnMapping.get("partOfSpeech"));
                String phoneticIpa = getRecordValue(record, columnMapping.get("phoneticIpa"));
                String englishDefinition = getRecordValue(record, columnMapping.get("englishDefinition"));
                String exampleSentence = getRecordValue(record, columnMapping.get("exampleSentence"));
                String exampleTranslation = getRecordValue(record, columnMapping.get("exampleTranslation"));
                String notes = getRecordValue(record, columnMapping.get("notes"));
                String source = getRecordValue(record, columnMapping.get("source"));
                String difficultyLevel = getRecordValue(record, columnMapping.get("difficultyLevel"));

                // Validate row fields using canonical shared validator
                ValidationResult validation = validateFields(
                    term, vietnameseMeaning, partOfSpeech, difficultyLevel,
                    phoneticIpa, englishDefinition, exampleSentence, exampleTranslation,
                    source, notes
                );

                String status;
                String validationMessage = null;

                if (!validation.isValid()) {
                    status = "INVALID";
                    validationMessage = String.join("; ", validation.errors());
                    errors.add(new BulkImportRowError(displayRowNumber, validationMessage, null));
                } else {
                    // Check duplicate within file
                    String fileKey = Vocabulary.normalize(term) + "#" + validation.normalizedPos();
                    if (seenInFile.contains(fileKey)) {
                        status = "DUPLICATE_FILE";
                        validationMessage = "Duplicate term and part of speech within this file.";
                    } else {
                        seenInFile.add(fileKey);
                        status = "VALID";
                    }
                }

                rows.add(new BulkImportRowPreview(
                    displayRowNumber,
                    term,
                    validation.normalizedPos(),
                    vietnameseMeaning,
                    phoneticIpa,
                    englishDefinition,
                    exampleSentence,
                    exampleTranslation,
                    notes,
                    source,
                    validation.normalizedLevel(),
                    status,
                    validationMessage
                ));

                rowIndex++;
            }

            if (rows.isEmpty()) {
                throw new BulkImportException("EMPTY_FILE", "The uploaded file contains no data rows.");
            }

        } catch (BulkImportException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("duplicate")) {
                throw new BulkImportException("DUPLICATE_HEADERS", "Duplicate header names detected in uploaded file: " + e.getMessage());
            }
            throw new BulkImportException("PARSER_ERROR", "Failed to parse CSV/TSV file: " + e.getMessage());
        } catch (Exception e) {
            throw new BulkImportException("PARSER_ERROR", "Failed to parse CSV/TSV file: " + e.getMessage());
        }

        int totalRows = rows.size();
        int validRows = (int) rows.stream().filter(r -> "VALID".equals(r.status())).count();
        int duplicateRows = (int) rows.stream().filter(r -> "DUPLICATE_FILE".equals(r.status())).count();
        int errorRows = (int) rows.stream().filter(r -> "INVALID".equals(r.status())).count();

        return new ParsedImportFile(rows, errors, totalRows, validRows, duplicateRows, errorRows);
    }

    private char detectDelimiter(String content, String filename) {
        if (filename != null) {
            String lower = filename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".tsv") || lower.endsWith(".tab")) {
                return '\t';
            }
        }

        // Inspect first 2048 chars to count tabs vs commas
        int sampleLen = Math.min(content.length(), 2048);
        String sample = content.substring(0, sampleLen);
        int commaCount = 0;
        int tabCount = 0;
        for (int i = 0; i < sample.length(); i++) {
            char c = sample.charAt(i);
            if (c == ',') commaCount++;
            else if (c == '\t') tabCount++;
        }

        return tabCount > commaCount ? '\t' : ',';
    }

    private Map<String, String> resolveColumnMapping(Set<String> headers) {
        Map<String, String> mapping = new HashMap<>();

        for (String rawHeader : headers) {
            if (rawHeader == null) continue;
            String normalizedHeader = rawHeader.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-]", "");

            if (!mapping.containsKey("term") && isAlias(normalizedHeader, TERM_ALIASES)) {
                mapping.put("term", rawHeader);
            } else if (!mapping.containsKey("vietnameseMeaning") && isAlias(normalizedHeader, MEANING_ALIASES)) {
                mapping.put("vietnameseMeaning", rawHeader);
            } else if (!mapping.containsKey("partOfSpeech") && isAlias(normalizedHeader, POS_ALIASES)) {
                mapping.put("partOfSpeech", rawHeader);
            } else if (!mapping.containsKey("phoneticIpa") && isAlias(normalizedHeader, IPA_ALIASES)) {
                mapping.put("phoneticIpa", rawHeader);
            } else if (!mapping.containsKey("englishDefinition") && isAlias(normalizedHeader, DEF_ALIASES)) {
                mapping.put("englishDefinition", rawHeader);
            } else if (!mapping.containsKey("exampleSentence") && isAlias(normalizedHeader, EX_SENTENCE_ALIASES)) {
                mapping.put("exampleSentence", rawHeader);
            } else if (!mapping.containsKey("exampleTranslation") && isAlias(normalizedHeader, EX_TRANS_ALIASES)) {
                mapping.put("exampleTranslation", rawHeader);
            } else if (!mapping.containsKey("notes") && isAlias(normalizedHeader, NOTES_ALIASES)) {
                mapping.put("notes", rawHeader);
            } else if (!mapping.containsKey("source") && isAlias(normalizedHeader, SOURCE_ALIASES)) {
                mapping.put("source", rawHeader);
            } else if (!mapping.containsKey("difficultyLevel") && isAlias(normalizedHeader, LEVEL_ALIASES)) {
                mapping.put("difficultyLevel", rawHeader);
            }
        }

        return mapping;
    }

    private boolean isAlias(String normalized, Set<String> aliases) {
        for (String alias : aliases) {
            String cleanAlias = alias.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
            if (cleanAlias.equals(normalized)) return true;
        }
        return false;
    }

    private String getRecordValue(CSVRecord record, String headerName) {
        if (headerName == null || !record.isMapped(headerName)) return null;
        String val = record.get(headerName);
        if (val == null) return null;
        String trimmed = val.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
