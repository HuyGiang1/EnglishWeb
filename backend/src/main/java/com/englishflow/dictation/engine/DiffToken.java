package com.englishflow.dictation.engine;

public record DiffToken(
    String text,
    DiffTokenType type,
    String expectedWord
) {
    public static DiffToken equal(String text) {
        return new DiffToken(text, DiffTokenType.EQUAL, null);
    }

    public static DiffToken missing(String expectedWord) {
        return new DiffToken(expectedWord, DiffTokenType.MISSING, expectedWord);
    }

    public static DiffToken extra(String text) {
        return new DiffToken(text, DiffTokenType.EXTRA, null);
    }

    public static DiffToken substitution(String actualWord, String expectedWord) {
        return new DiffToken(actualWord, DiffTokenType.SUBSTITUTION, expectedWord);
    }
}
