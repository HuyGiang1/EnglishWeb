package com.englishflow.dictation.engine;

import java.util.regex.Pattern;

public final class TranscriptNormalizer {

    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern TRAILING_SENTENCE_PUNCT = Pattern.compile("[.?!]+$");

    private TranscriptNormalizer() {}

    /**
     * Standardizes text representation:
     * - Trims leading/trailing whitespace
     * - Standardizes typographic apostrophes and quotes to standard ASCII
     * - Collapses internal whitespace runs to single space
     */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();
        cleaned = cleaned.replace('’', '\'')
                         .replace('‘', '\'')
                         .replace('“', '"')
                         .replace('”', '"')
                         .replace('`', '\'');
        return MULTI_WHITESPACE.matcher(cleaned).replaceAll(" ");
    }

    /**
     * Normalizes text for canonical comparison:
     * - clean()
     * - toLowerCase()
     * - strips trailing sentence-level punctuation (. ? !)
     */
    public static String normalizeForComparison(String text) {
        String cleaned = clean(text);
        if (cleaned.isEmpty()) {
            return "";
        }
        String lower = cleaned.toLowerCase();
        return TRAILING_SENTENCE_PUNCT.matcher(lower).replaceAll("").trim();
    }

    /**
     * Normalizes an individual word token for equality checks
     * (removes outer punctuation like commas, periods, quotes).
     */
    public static String normalizeWord(String word) {
        if (word == null) {
            return "";
        }
        return word.trim()
            .toLowerCase()
            .replaceAll("^[^a-zA-Z0-9']+|[^a-zA-Z0-9']+$", "");
    }
}
