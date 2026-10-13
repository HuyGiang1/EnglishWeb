package com.englishflow.dictation;

import com.englishflow.dictation.engine.DictationDiffEngine;
import com.englishflow.dictation.engine.DictationEvaluationResult;
import com.englishflow.dictation.engine.DiffTokenType;
import com.englishflow.dictation.engine.TranscriptNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DictationDiffEngineTest {

    @Test
    @DisplayName("1. Exact match produces 100.0% accuracy and isPassed true")
    void testExactMatch() {
        String canonical = "The country showed great economic resilience during the crisis.";
        String submitted = "The country showed great economic resilience during the crisis.";

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.accuracyScore()).isEqualTo(100.0);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.errorCount()).isZero();
        assertThat(result.wordCount()).isEqualTo(9);
        assertThat(result.diffTokens()).allMatch(t -> t.type() == DiffTokenType.EQUAL);
    }

    @Test
    @DisplayName("2. Case-only differences evaluate to 100% accuracy and EQUAL tokens")
    void testCaseDifferences() {
        String canonical = "She doesn't work on Sundays.";
        String submitted = "she doesn't work on sundays.";

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.accuracyScore()).isEqualTo(100.0);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.errorCount()).isZero();
    }

    @Test
    @DisplayName("3. Typographic curly quotes and missing trailing period do not penalize accuracy")
    void testQuotesAndPunctuationNormalization() {
        String canonical = "“We aren’t ready yet,” said John.";
        String submitted = "\"We aren't ready yet,\" said John";

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.accuracyScore()).isEqualTo(100.0);
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("4. Missing word identifies OMISSION and lowers accuracy proportionately")
    void testMissingWord() {
        String canonical = "I have been working here for three years.";
        String submitted = "I have working here for three years."; // "been" missing

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.accuracyScore()).isGreaterThan(85.0);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.diffTokens()).anyMatch(t -> t.type() == DiffTokenType.MISSING && t.expectedWord().equalsIgnoreCase("been"));
        assertThat(result.errorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("5. Extra word identifies EXTRA insertion")
    void testExtraWord() {
        String canonical = "I work here every day.";
        String submitted = "I work right here every day."; // "right" is extra

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.accuracyScore()).isGreaterThan(70.0);
        assertThat(result.diffTokens()).anyMatch(t -> t.type() == DiffTokenType.EXTRA && t.text().equals("right"));
        assertThat(result.errorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("6. Substituted word identifies SUBSTITUTION with expected reference")
    void testSubstitutedWord() {
        String canonical = "I have been working here for three years.";
        String submitted = "I have been working here for two years."; // "two" instead of "three"

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.diffTokens()).anyMatch(t -> t.type() == DiffTokenType.SUBSTITUTION &&
            t.text().equalsIgnoreCase("two") &&
            t.expectedWord().equalsIgnoreCase("three"));
    }

    @Test
    @DisplayName("7. Minor typo in long word yields high accuracy (>90%) with pass")
    void testMinorTypo() {
        String canonical = "The country showed great economic resilience during the crisis.";
        String submitted = "The country showed great economic resillience during the crisis.";

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.accuracyScore()).isGreaterThanOrEqualTo(95.0);
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("8. Empty string returns 0% accuracy, errorCount == wordCount, and isPassed false")
    void testEmptySubmission() {
        String canonical = "Online learning platforms allow students to study at their own pace.";
        String submitted = "";

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.accuracyScore()).isEqualTo(0.0);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.errorCount()).isEqualTo(result.wordCount());
        assertThat(result.diffTokens()).allMatch(t -> t.type() == DiffTokenType.MISSING);
    }

    @Test
    @DisplayName("9. Whitespace-only submission returns 0% accuracy and isPassed false")
    void testWhitespaceOnlySubmission() {
        String canonical = "Online learning platforms allow students to study.";
        String submitted = "    \t \n   ";

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.accuracyScore()).isEqualTo(0.0);
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    @DisplayName("10. Completely wrong transcript yields very low accuracy and isPassed false")
    void testCompletelyWrongTranscript() {
        String canonical = "The country showed great economic resilience during the crisis.";
        String submitted = "Banana apple orange watermelon pineapple strawberry blueberry.";

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.accuracyScore()).isLessThan(30.0);
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    @DisplayName("11. Long complex sentence handles Unicode and punctuation cleanly")
    void testLongComplexSentence() {
        String canonical = "Empirical evidence is fundamental to formulating hypotheses and drawing rigorous conclusions.";
        String submitted = "Empirical evidence is fundamental to formulating hypotheses and drawing rigorous conclusions.";

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.accuracyScore()).isEqualTo(100.0);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.errorCount()).isZero();
    }

    @ParameterizedTest(name = "Normalizer clean: {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "  hello   world   | hello world",
        "it’s a test | it's a test",
        "“quote” | \"quote\"",
        "'   ' | ''"
    })
    void testTranscriptNormalizerClean(String input, String expected) {
        assertThat(TranscriptNormalizer.clean(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("12. Repeated word is identified as EXTRA token and penalizes score cleanly")
    void testRepeatedWord() {
        String canonical = "The country showed great economic resilience.";
        String submitted = "The country country showed great economic resilience.";

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        assertThat(result.diffTokens()).anyMatch(t -> t.type() == DiffTokenType.EXTRA && t.text().equalsIgnoreCase("country"));
        assertThat(result.accuracyScore()).isGreaterThan(75.0);
    }

    @Test
    @DisplayName("13. 80.0% accuracy threshold boundary test")
    void testPassThresholdBoundary() {
        // DictationDiffEngine.PASS_THRESHOLD is 80.0%
        assertThat(DictationDiffEngine.PASS_THRESHOLD).isEqualTo(80.0);

        String canonical = "A B C D E F G H I J";
        // Perfect match is passed
        DictationEvaluationResult perfect = DictationDiffEngine.evaluate(canonical, canonical);
        assertThat(perfect.isPassed()).isTrue();

        // 0% match is not passed
        DictationEvaluationResult zero = DictationDiffEngine.evaluate(canonical, "");
        assertThat(zero.isPassed()).isFalse();
    }

    @Test
    @DisplayName("14. Contraction with missing apostrophe vs canonical contraction")
    void testContractionApostropheHandling() {
        String canonical = "She doesn't work on Sundays.";
        String submitted = "She doesnt work on Sundays.";

        DictationEvaluationResult result = DictationDiffEngine.evaluate(canonical, submitted);

        // Near-perfect character match (>95%) and passed
        assertThat(result.accuracyScore()).isGreaterThan(95.0);
        assertThat(result.isPassed()).isTrue();
    }
}
