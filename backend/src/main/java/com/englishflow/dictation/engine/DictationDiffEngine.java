package com.englishflow.dictation.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DictationDiffEngine {

    public static final double PASS_THRESHOLD = 80.0;

    private DictationDiffEngine() {}

    /**
     * Evaluates a user's transcription against the authoritative canonical sentence.
     * Computes character-level Levenshtein accuracy score and token-level semantic diff.
     */
    public static DictationEvaluationResult evaluate(String canonicalTranscript, String submittedTranscript) {
        String cleanCanonical = TranscriptNormalizer.clean(canonicalTranscript);
        String cleanSubmitted = TranscriptNormalizer.clean(submittedTranscript);

        String normCanonical = TranscriptNormalizer.normalizeForComparison(cleanCanonical);
        String normSubmitted = TranscriptNormalizer.normalizeForComparison(cleanSubmitted);

        String[] canonicalTokens = cleanCanonical.isEmpty() ? new String[0] : cleanCanonical.split(" ");
        String[] submittedTokens = cleanSubmitted.isEmpty() ? new String[0] : cleanSubmitted.split(" ");

        int wordCount = canonicalTokens.length;

        // 1. Handle completely empty submission
        if (normSubmitted.isEmpty()) {
            List<DiffToken> tokens = new ArrayList<>();
            for (String cToken : canonicalTokens) {
                tokens.add(DiffToken.missing(cToken));
            }
            return new DictationEvaluationResult(0.0, wordCount, wordCount, false, tokens);
        }

        // 2. Character-level Levenshtein accuracy calculation
        int levDistance = computeLevenshtein(normSubmitted, normCanonical);
        int denom = Math.max(normCanonical.length(), 1);
        double rawRatio = 1.0 - ((double) levDistance / denom);
        double accuracyScore = Math.max(0.0, Math.min(100.0, Math.round(rawRatio * 1000.0) / 10.0));
        boolean isPassed = accuracyScore >= PASS_THRESHOLD;

        // 3. Word-level alignment
        List<DiffToken> diffTokens = alignWordTokens(canonicalTokens, submittedTokens);

        // Count errors from token diff
        int errorCount = 0;
        for (DiffToken token : diffTokens) {
            if (token.type() != DiffTokenType.EQUAL) {
                errorCount++;
            }
        }

        return new DictationEvaluationResult(
            accuracyScore,
            wordCount,
            errorCount,
            isPassed,
            diffTokens
        );
    }

    /**
     * Word-level dynamic programming alignment using Needleman-Wunsch / Wagner-Fischer.
     */
    private static List<DiffToken> alignWordTokens(String[] canonical, String[] submitted) {
        int m = canonical.length;
        int n = submitted.length;

        double[][] dp = new double[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i * 1.0;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j * 1.0;
        }

        for (int i = 1; i <= m; i++) {
            String cWord = TranscriptNormalizer.normalizeWord(canonical[i - 1]);
            for (int j = 1; j <= n; j++) {
                String sWord = TranscriptNormalizer.normalizeWord(submitted[j - 1]);

                if (cWord.equals(sWord)) {
                    dp[i][j] = dp[i - 1][j - 1]; // Exact match (cost 0)
                } else {
                    double subCost = calculateWordSubCost(cWord, sWord);
                    double replace = dp[i - 1][j - 1] + subCost;
                    double delete = dp[i - 1][j] + 1.0;   // Canonical omitted
                    double insert = dp[i][j - 1] + 1.0;   // Submitted extra

                    dp[i][j] = Math.min(replace, Math.min(delete, insert));
                }
            }
        }

        // Backtracking
        List<DiffToken> result = new ArrayList<>();
        int i = m;
        int j = n;

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                String cWord = TranscriptNormalizer.normalizeWord(canonical[i - 1]);
                String sWord = TranscriptNormalizer.normalizeWord(submitted[j - 1]);
                double subCost = cWord.equals(sWord) ? 0.0 : calculateWordSubCost(cWord, sWord);

                if (Math.abs(dp[i][j] - (dp[i - 1][j - 1] + subCost)) < 0.001) {
                    if (subCost == 0.0) {
                        result.add(DiffToken.equal(submitted[j - 1]));
                    } else {
                        result.add(DiffToken.substitution(submitted[j - 1], canonical[i - 1]));
                    }
                    i--;
                    j--;
                    continue;
                }
            }

            if (i > 0 && Math.abs(dp[i][j] - (dp[i - 1][j] + 1.0)) < 0.001) {
                result.add(DiffToken.missing(canonical[i - 1]));
                i--;
            } else if (j > 0) {
                result.add(DiffToken.extra(submitted[j - 1]));
                j--;
            } else {
                break;
            }
        }

        Collections.reverse(result);
        return result;
    }

    private static double calculateWordSubCost(String cWord, String sWord) {
        int dist = computeLevenshtein(cWord, sWord);
        int maxLen = Math.max(cWord.length(), sWord.length());
        if (maxLen == 0) return 0.0;
        double ratio = (double) dist / maxLen;
        // If words share substantial similarity (typo), favor substitution (cost ~ 1.0)
        // If completely dissimilar, slightly higher (1.5) but still preferred over delete+insert (2.0)
        return ratio < 0.6 ? 1.0 : 1.5;
    }

    /**
     * Standard Levenshtein edit distance implementation.
     */
    public static int computeLevenshtein(String s1, String s2) {
        int[] prev = new int[s2.length() + 1];
        int[] curr = new int[s2.length() + 1];

        for (int j = 0; j <= s2.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                    Math.min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                );
            }
            System.arraycopy(curr, 0, prev, 0, curr.length);
        }

        return prev[s2.length()];
    }
}
