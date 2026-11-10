package com.englishflow.vocabulary;

import com.englishflow.vocabulary.dto.*;
import com.englishflow.vocabulary.exception.BulkImportException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PasteImportParserTest {
    private final PasteImportParser parser = new PasteImportParser();
    private PasteImportParser.Parsed parse(String text, String delimiter) {
        return parser.parse(new PasteImportRequest(text, delimiter, null, false, null));
    }
    @Test void fourDelimitersAndVietnamese() {
        for (String[] pair : new String[][]{{"TAB","\t"},{"SEMICOLON",";"},{"COMMA",","},{"COLON",":"}}) {
            var result = parse(" analyze " + pair[1] + " phân tích ", pair[0]);
            assertThat(result.rows().getFirst().item().term()).isEqualTo("analyze");
            assertThat(result.rows().getFirst().item().vietnameseMeaning()).isEqualTo("phân tích");
        }
    }
    @Test void firstColonSeparatesAndRemainingPunctuationIsContent() {
        assertThat(parse("approach : phương pháp: cách tiếp cận", "COLON").rows().getFirst().cells())
            .containsExactly("approach", "phương pháp: cách tiếp cận");
        assertThat(parse("\"a:b\" : \"nghĩa: \"\"đúng\"\"\"", "COLON").rows().getFirst().cells())
            .containsExactly("a:b", "nghĩa: \"đúng\"");
    }
    @Test void quotedDelimitersEscapedQuotesAndMultiline() {
        var result = parse("\"word,one\",\"nghĩa, \"\"đúng\"\"\nsecond line\"\r\nnext,tiếp", "COMMA");
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().getFirst().cells()).containsExactly("word,one","nghĩa, \"đúng\"\nsecond line");
        assertThat(result.rows().get(1).line()).isEqualTo(3);
    }
    @Test void blankLinesAndHeaderKeepPhysicalRowNumbers() {
        var r = parser.parse(new PasteImportRequest("\uFEFF\r\nTerm\tMeaning\r\n\r\none\tmột\r\ntwo\thai","TAB",null,true,null));
        assertThat(r.rows().stream().map(PasteImportParser.SourceRow::line)).containsExactly(4,5);
    }
    @Test void termOnlyDoesNotInventMeaning() {
        var r = parse("analyze\nsignificant","NONE");
        assertThat(r.rows().getFirst().item().vietnameseMeaning()).isEmpty();
    }
    @Test void autoDetectionRemainsASuggestionWithAmbiguityWarning() {
        var r = parse("word;meaning, punctuation","AUTO");
        assertThat(r.delimiter()).isEqualTo("SEMICOLON");
        assertThat(r.warnings()).contains("AMBIGUOUS_DELIMITER");
        assertThat(parse("word\tnghĩa","AUTO").delimiter()).isEqualTo("TAB");
    }
    @Test void explicitMappingReordersAndWarnsIgnoredColumns() {
        var r = parser.parse(new PasteImportRequest("nghĩa;word;discard","SEMICOLON",
            List.of("vietnameseMeaning","term","ignore"),false,null));
        assertThat(r.rows().getFirst().item().term()).isEqualTo("word");
        assertThat(r.warnings()).contains("UNUSED_COLUMNS");
        assertThat(r.rows().getFirst().cells()).contains("discard");
    }
    @Test void conflictingMissingAndNullMappingsAreRejected() {
        for (List<String> mapping : List.of(List.of("term","term"),List.of("ignore"),Arrays.asList("term",null))) {
            assertThatThrownBy(() -> parser.parse(new PasteImportRequest("a;b","SEMICOLON",mapping,false,null)))
                .isInstanceOf(BulkImportException.class).extracting("errorCode").isEqualTo("INVALID_MAPPING");
        }
    }
    @Test void raggedRowsAndRepeatedDelimitersAreVisible() {
        var r = parse("a;b;c\nx;;","SEMICOLON");
        assertThat(r.rows().get(1).cells()).containsExactly("x","","");
        assertThat(parse("a;b\nx;y;z","SEMICOLON").warnings()).contains("RAGGED_ROW");
    }
    @Test void invalidQuotingAndControlCharactersFailSafely() {
        assertThatThrownBy(() -> parse("a;\"unterminated","SEMICOLON")).isInstanceOf(BulkImportException.class);
        assertThatThrownBy(() -> parse("a;bad\u0000text","SEMICOLON")).isInstanceOf(BulkImportException.class);
    }
    @Test void boundedTextRowsAndColumnsAreRejectedWithoutTruncation() {
        assertThatThrownBy(() -> parse("x".repeat(PasteImportParser.MAX_TEXT_BYTES + 1),"NONE")).isInstanceOf(BulkImportException.class);
        assertThatThrownBy(() -> parse("a;b\n".repeat(1001),"SEMICOLON")).isInstanceOf(BulkImportException.class);
        assertThatThrownBy(() -> parse(String.join(";", Collections.nCopies(17,"x")),"SEMICOLON")).isInstanceOf(BulkImportException.class);
        assertThat(parse("a;b\n".repeat(1000),"SEMICOLON").rows()).hasSize(1000);
    }
    @Test void longFieldsAreNotSilentlyTruncatedAndPosMatchesDatabase() {
        assertThat(parse("x".repeat(101)+";"+ "y".repeat(501),"SEMICOLON").rows().getFirst().item().term()).hasSize(101);
        for (String pos : List.of("pronoun","pron","interjection"))
            assertThat(BulkImportParser.validateFields("word","nghĩa",pos,null,null,null,null,null,null,null).isValid()).isFalse();
        assertThat(BulkImportParser.validateFields("word","nghĩa","n",null,null,null,null,null,null,null).normalizedPos()).isEqualTo("noun");
    }
}
