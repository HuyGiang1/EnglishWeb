package com.englishflow.vocabulary;

import com.englishflow.vocabulary.dto.*;
import com.englishflow.vocabulary.exception.BulkImportException;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class PasteImportParser {
    public static final int MAX_TEXT_BYTES = (int) BulkImportParser.MAX_FILE_SIZE_BYTES;
    public static final int MAX_REQUEST_BYTES = 4 * 1024 * 1024;
    public static final int MAX_COLUMNS = 16;
    public static final Set<String> FIELDS = Set.of("term", "vietnameseMeaning", "phoneticIpa", "partOfSpeech", "ignore");
    public static final List<String> DELIMITERS = List.of("TAB", "COLON", "SEMICOLON", "COMMA", "NONE");
    public record Parsed(String delimiter, List<String> mapping, List<String> warnings, List<SourceRow> rows) {}
    public record SourceRow(int line, List<String> cells, PasteImportRow item, List<String> warnings) {}

    public Parsed parse(PasteImportRequest request) {
        String text = request.text();
        if (text == null || text.isBlank()) fail("EMPTY_FILE");
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) fail("FILE_TOO_LARGE");
        if (text.matches("(?s).*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F].*")) fail("INVALID_ITEM_DATA");
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String delimiter = request.delimiter() == null ? "AUTO" : request.delimiter();
        List<String> warnings = new ArrayList<>();
        if ("AUTO".equals(delimiter)) {
            int bestScore = 0; List<String> best = new ArrayList<>();
            for (String candidate : DELIMITERS.subList(0, 4)) {
                try {
                    List<SourceRow> sample = records(text, candidate, false, 20);
                    int score = (int) sample.stream().filter(row -> row.cells().size() > 1).count();
                    if (score > bestScore) { bestScore = score; best.clear(); }
                    if (score > 0 && score == bestScore) best.add(candidate);
                } catch (BulkImportException ignored) { }
            }
            delimiter = best.isEmpty() ? "NONE" : best.getFirst();
            if (best.size() > 1) warnings.add("AMBIGUOUS_DELIMITER");
        }
        if (!DELIMITERS.contains(delimiter)) fail("INVALID_DELIMITER");
        List<SourceRow> source = records(text, delimiter, request.skipHeader(), BulkImportParser.MAX_ROW_LIMIT + 1);
        if (source.isEmpty()) fail("EMPTY_FILE");
        if (source.size() > BulkImportParser.MAX_ROW_LIMIT) fail("ROW_LIMIT_EXCEEDED");
        int width = source.stream().mapToInt(row -> row.cells().size()).max().orElse(1);
        if (width > MAX_COLUMNS) fail("COLUMN_LIMIT_EXCEEDED");
        List<String> mapping = request.mapping();
        if (mapping == null) {
            String[] defaults = {"term", "vietnameseMeaning", "phoneticIpa", "partOfSpeech"};
            mapping = new ArrayList<>();
            for (int col = 0; col < width; col++) mapping.add(col < defaults.length ? defaults[col] : "ignore");
        }
        if (mapping.isEmpty() || mapping.size() > MAX_COLUMNS || mapping.stream().anyMatch(field -> field == null || !FIELDS.contains(field))) fail("INVALID_MAPPING");
        Set<String> used = new HashSet<>();
        for (String field : mapping) if (!"ignore".equals(field) && !used.add(field)) fail("INVALID_MAPPING");
        if (!used.contains("term")) fail("INVALID_MAPPING");
        if (mapping.contains("ignore") || width > mapping.size()) warnings.add("UNUSED_COLUMNS");
        int expected = source.getFirst().cells().size();
        List<SourceRow> rows = new ArrayList<>();
        for (SourceRow row : source) {
            Map<String, String> values = new HashMap<>();
            for (int col = 0; col < Math.min(mapping.size(), row.cells().size()); col++)
                if (!"ignore".equals(mapping.get(col))) values.put(mapping.get(col), row.cells().get(col));
            List<String> rowWarnings = row.cells().size() != expected ? List.of("RAGGED_ROW") : List.of();
            if (!rowWarnings.isEmpty() && !warnings.contains("RAGGED_ROW")) warnings.add("RAGGED_ROW");
            rows.add(new SourceRow(row.line(), row.cells(), new PasteImportRow(row.line(),
                values.getOrDefault("term", ""), values.getOrDefault("vietnameseMeaning", ""),
                values.getOrDefault("phoneticIpa", ""), values.getOrDefault("partOfSpeech", ""), false), rowWarnings));
        }
        return new Parsed(delimiter, List.copyOf(mapping), List.copyOf(warnings), rows);
    }

    private List<SourceRow> records(String original, String delimiter, boolean skipHeader, int limit) {
        char separator = switch (delimiter) {
            case "TAB" -> '\t'; case "COLON" -> ':'; case "SEMICOLON" -> ';'; case "COMMA" -> ','; default -> '\u001f';
        };
        String text = "COLON".equals(delimiter) ? protectMeaningColons(original) : original;
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(separator)
            .setIgnoreEmptyLines(false).setTrim(true).setIgnoreSurroundingSpaces(true).build();
        List<SourceRow> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(text, format)) {
            int cursor = 0, line = 1; boolean header = skipHeader;
            for (CSVRecord record : parser) {
                int position = Math.toIntExact(record.getCharacterPosition());
                while (cursor < position) {
                    char c = original.charAt(cursor++);
                    if (c == '\n' || (c == '\r' && (cursor >= original.length() || original.charAt(cursor) != '\n'))) line++;
                }
                List<String> cells = new ArrayList<>();
                for (String value : record) cells.add(value.replace('\u001f', ':').trim());
                if (cells.stream().allMatch(String::isBlank)) continue;
                if (header) { header = false; continue; }
                rows.add(new SourceRow(line, cells, null, List.of()));
                if (rows.size() >= limit) break;
            }
        } catch (Exception error) {
            throw new BulkImportException("PARSER_ERROR", "Check pasted text quotation and delimiter.");
        }
        return rows;
    }

    // Colon paste is a two-field dialect: only the first unquoted colon separates
    // Term from Meaning. Commons CSV still handles quoting/escaped quotes/newlines.
    private String protectMeaningColons(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean quoted = false, fieldStart = true, separated = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                result.append(c);
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') result.append(text.charAt(++i));
                    else quoted = false;
                }
            } else if (c == '"' && fieldStart) {
                quoted = true; fieldStart = false; result.append(c);
            } else if (c == '\r' || c == '\n') {
                separated = false; fieldStart = true; result.append(c);
            } else if (c == ':') {
                result.append(separated ? '\u001f' : c); fieldStart = !separated; separated = true;
            } else {
                result.append(c);
                if (c != ' ' && c != '\t') fieldStart = false;
            }
        }
        return result.toString();
    }

    private static void fail(String code) { throw new BulkImportException(code, "Check paste input, limits and column mapping."); }
}
