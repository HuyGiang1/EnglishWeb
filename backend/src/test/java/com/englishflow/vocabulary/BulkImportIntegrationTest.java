package com.englishflow.vocabulary;

import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.review.SrsItem;
import com.englishflow.review.SrsItemRepository;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.dto.BulkImportConfirmRequest;
import com.englishflow.vocabulary.dto.BulkImportConfirmResponse;
import com.englishflow.vocabulary.dto.BulkImportItemRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BulkImportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private VocabularyCollectionRepository vocabularyCollectionRepository;

    @Autowired
    private SrsItemRepository srsItemRepository;

    @Autowired
    private LearningActivityEventRepository learningActivityEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VocabularyService vocabularyService;

    @Autowired
    private BulkImportService bulkImportService;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(new User(
            "import_a_" + UUID.randomUUID() + "@englishflow.local",
            passwordEncoder.encode("Password123!"),
            "User A",
            "Asia/Ho_Chi_Minh",
            20
        ));

        userB = userRepository.save(new User(
            "import_b_" + UUID.randomUUID() + "@englishflow.local",
            passwordEncoder.encode("Password123!"),
            "User B",
            "UTC",
            20
        ));
    }

    @AfterEach
    void tearDown() {
        learningActivityEventRepository.deleteAll();
        srsItemRepository.deleteAll();
        vocabularyCollectionRepository.deleteAll();
        collectionRepository.deleteAll();
        vocabularyRepository.deleteAll();
        userRepository.deleteAll();
    }

    private MockHttpServletRequestBuilder withAuthAndCsrf(MockHttpServletRequestBuilder builder, User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return builder
            .session(session)
            .cookie(new Cookie("XSRF-TOKEN", "test-csrf-token"))
            .header("X-XSRF-TOKEN", "test-csrf-token");
    }

    @Test
    @DisplayName("01. Valid CSV import dry-run preview and confirm")
    void testValidCsvImport() throws Exception {
        String csvContent = """
            term,partOfSpeech,vietnameseMeaning,phoneticIpa,englishDefinition,difficultyLevel
            metaphor,noun,phép ẩn dụ,/ˈmet.ə.fɔːr/,A figure of speech.,B2
            ephemeral,adjective,"phù du, chóng tàn",/ɪˈfem.ər.əl/,Lasting for a very short time.,C1
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file", "words.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        // 1. Preview
        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(2))
            .andExpect(jsonPath("$.validRows").value(2))
            .andExpect(jsonPath("$.errorRows").value(0))
            .andExpect(jsonPath("$.previewRows[0].term").value("metaphor"))
            .andExpect(jsonPath("$.previewRows[0].status").value("VALID"))
            .andExpect(jsonPath("$.previewRows[1].term").value("ephemeral"));

        // 2. Confirm import
        BulkImportConfirmRequest confirmReq = new BulkImportConfirmRequest(
            List.of(
                new BulkImportItemRequest("metaphor", "noun", "phép ẩn dụ", "/ˈmet.ə.fɔːr/", "A figure of speech.", null, null, null, null, "B2"),
                new BulkImportItemRequest("ephemeral", "adjective", "phù du, chóng tàn", "/ɪˈfem.ər.əl/", "Lasting for a very short time.", null, null, null, null, "C1")
            ),
            null,
            "SKIP"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(2))
            .andExpect(jsonPath("$.skippedCount").value(0));

        // Verify database state: exactly 2 vocabularies and 2 SRS items, zero activity events
        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(2);
        assertThat(srsItemRepository.count()).isEqualTo(2);
        assertThat(learningActivityEventRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("02. Valid TSV import with tabs and Vietnamese text")
    void testValidTsvImport() throws Exception {
        String tsvContent = "term\tpartOfSpeech\tvietnameseMeaning\tdifficultyLevel\n"
            + "lạc quan\tadjective\tlạc quan yêu đời\tB1\n"
            + "kiên trì\tnoun\tsự kiên trì bền bỉ\tB2\n";

        MockMultipartFile file = new MockMultipartFile(
            "file", "vietnamese.tsv", "text/tab-separated-values", tsvContent.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(2))
            .andExpect(jsonPath("$.validRows").value(2))
            .andExpect(jsonPath("$.previewRows[0].vietnameseMeaning").value("lạc quan yêu đời"));
    }

    @Test
    @DisplayName("03. UTF-8 BOM is transparently handled without corrupting header")
    void testUtf8BomHandled() throws Exception {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvText = "term,vietnameseMeaning\nsunshine,ánh nắng mặt trời\n".getBytes(StandardCharsets.UTF_8);

        byte[] combined = new byte[bom.length + csvText.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(csvText, 0, combined, bom.length, csvText.length);

        MockMultipartFile file = new MockMultipartFile("file", "with_bom.csv", "text/csv", combined);

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.validRows").value(1))
            .andExpect(jsonPath("$.previewRows[0].term").value("sunshine"));
    }

    @Test
    @DisplayName("04. Vietnamese text with full diacritics preserves character encoding")
    void testVietnameseDiacritics() throws Exception {
        String csvContent = "term,vietnameseMeaning\n"
            + "độc lập,sự tự do độc lập không phụ thuộc\n";

        MockMultipartFile file = new MockMultipartFile("file", "diacritics.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previewRows[0].term").value("độc lập"))
            .andExpect(jsonPath("$.previewRows[0].vietnameseMeaning").value("sự tự do độc lập không phụ thuộc"));
    }

    @Test
    @DisplayName("05. Quoted commas are preserved inside cells")
    void testQuotedCommas() throws Exception {
        String csvContent = """
            term,vietnameseMeaning,englishDefinition
            "apple, orange, and banana","táo, cam, và chuối","A list of common fruits, such as apples, oranges, and bananas."
            """;

        MockMultipartFile file = new MockMultipartFile("file", "quotes.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previewRows[0].term").value("apple, orange, and banana"))
            .andExpect(jsonPath("$.previewRows[0].vietnameseMeaning").value("táo, cam, và chuối"));
    }

    @Test
    @DisplayName("06. Escaped quotes are handled cleanly")
    void testEscapedQuotes() throws Exception {
        String csvContent = "term,vietnameseMeaning,exampleSentence\n"
            + "proverb,tục ngữ,\"He said, \"\"Practice makes perfect.\"\"\"\n";

        MockMultipartFile file = new MockMultipartFile("file", "escaped.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previewRows[0].exampleSentence").value("He said, \"Practice makes perfect.\""));
    }

    @Test
    @DisplayName("07. Multiline quoted cells are parsed without breaking row boundaries")
    void testMultilineQuotedCells() throws Exception {
        String csvContent = "term,vietnameseMeaning,notes\n"
            + "\"complex term\",\"ý nghĩa phức tạp\",\"Dòng 1 ghi chú\nDòng 2 ghi chú\"\n";

        MockMultipartFile file = new MockMultipartFile("file", "multiline.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(1))
            .andExpect(jsonPath("$.previewRows[0].notes").value("Dòng 1 ghi chú\nDòng 2 ghi chú"));
    }

    @Test
    @DisplayName("08. Empty file is rejected with HTTP 400")
    void testEmptyFileRejected() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(emptyFile), userA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
    }

    @Test
    @DisplayName("09. Missing required headers rejected with HTTP 400")
    void testMissingRequiredHeaderRejected() throws Exception {
        String invalidCsv = "phonetic,notes\n/test/,some note\n";
        MockMultipartFile file = new MockMultipartFile("file", "no_term.csv", "text/csv", invalidCsv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_HEADERS"));
    }

    @Test
    @DisplayName("10. Missing required cells flagged as INVALID in preview")
    void testMissingRequiredCellFlagged() throws Exception {
        String csv = """
            term,vietnameseMeaning
            ,ý nghĩa không có từ
            word without meaning,
            valid word,nghĩa hợp lệ
            """;

        MockMultipartFile file = new MockMultipartFile("file", "missing_cell.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(3))
            .andExpect(jsonPath("$.validRows").value(1))
            .andExpect(jsonPath("$.errorRows").value(2))
            .andExpect(jsonPath("$.previewRows[0].status").value("INVALID"))
            .andExpect(jsonPath("$.previewRows[1].status").value("INVALID"))
            .andExpect(jsonPath("$.previewRows[2].status").value("VALID"));
    }

    @Test
    @DisplayName("11. Invalid part of speech flagged in preview")
    void testInvalidPartOfSpeechFlagged() throws Exception {
        String csv = "term,partOfSpeech,vietnameseMeaning\n"
            + "test,alien_pos,thử nghiệm\n";

        MockMultipartFile file = new MockMultipartFile("file", "pos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errorRows").value(1))
            .andExpect(jsonPath("$.previewRows[0].status").value("INVALID"))
            .andExpect(jsonPath("$.previewRows[0].validationMessage", containsString("Invalid part of speech")));
    }

    @Test
    @DisplayName("12. Invalid CEFR level flagged in preview")
    void testInvalidCefrLevelFlagged() throws Exception {
        String csv = "term,vietnameseMeaning,difficultyLevel\n"
            + "test,thử nghiệm,Z9\n";

        MockMultipartFile file = new MockMultipartFile("file", "cefr.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errorRows").value(1))
            .andExpect(jsonPath("$.previewRows[0].status").value("INVALID"))
            .andExpect(jsonPath("$.previewRows[0].validationMessage", containsString("Invalid CEFR difficulty level")));
    }

    @Test
    @DisplayName("13. Maximum row boundary (> 1000 rows) is rejected with HTTP 400")
    void testRowLimitExceeded() throws Exception {
        StringBuilder sb = new StringBuilder("term,vietnameseMeaning\n");
        for (int i = 1; i <= 1005; i++) {
            sb.append("word").append(i).append(",nghia").append(i).append("\n");
        }

        MockMultipartFile file = new MockMultipartFile("file", "large.csv", "text/csv", sb.toString().getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ROW_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("14. Duplicate within uploaded file is identified as DUPLICATE_FILE")
    void testDuplicateWithinFileIdentified() throws Exception {
        String csv = """
            term,partOfSpeech,vietnameseMeaning
            serendipity,noun,may mắn tình cờ
            serendipity,noun,may mắn tình cờ lần hai
            """;

        MockMultipartFile file = new MockMultipartFile("file", "dups.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(2))
            .andExpect(jsonPath("$.validRows").value(1))
            .andExpect(jsonPath("$.duplicateRows").value(1))
            .andExpect(jsonPath("$.previewRows[0].status").value("VALID"))
            .andExpect(jsonPath("$.previewRows[1].status").value("DUPLICATE_FILE"));
    }

    @Test
    @DisplayName("15. Duplicate already in user database identified as DUPLICATE_LIBRARY")
    void testDuplicateInDatabaseIdentified() throws Exception {
        // Create word in database for User A
        vocabularyRepository.save(new Vocabulary(userA.getId(), "eloquent", "adjective", null, "hùng hồn", null, null, null, null, null, "B2"));

        String csv = """
            term,partOfSpeech,vietnameseMeaning
            eloquent,adjective,hùng hồn lưu loát
            resilient,adjective,kiên cường
            """;

        MockMultipartFile file = new MockMultipartFile("file", "db_dups.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(2))
            .andExpect(jsonPath("$.validRows").value(1))
            .andExpect(jsonPath("$.duplicateRows").value(1))
            .andExpect(jsonPath("$.previewRows[0].status").value("DUPLICATE_LIBRARY"))
            .andExpect(jsonPath("$.previewRows[1].status").value("VALID"));
    }

    @Test
    @DisplayName("16. Same term with legitimately different part of speech is permitted")
    void testSameTermDifferentPartOfSpeechPermitted() throws Exception {
        // User A already has "record" as a NOUN
        vocabularyRepository.save(new Vocabulary(userA.getId(), "record", "noun", null, "bản ghi, kỷ lục", null, null, null, null, null, "A2"));

        // Upload has "record" as a VERB
        String csv = """
            term,partOfSpeech,vietnameseMeaning
            record,verb,ghi lại, thu âm
            """;

        MockMultipartFile file = new MockMultipartFile("file", "distinct_pos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.validRows").value(1))
            .andExpect(jsonPath("$.duplicateRows").value(0))
            .andExpect(jsonPath("$.previewRows[0].status").value("VALID"));
    }

    @Test
    @DisplayName("17. Duplicate Policy SKIP skips existing words without error")
    void testDuplicatePolicySkip() throws Exception {
        vocabularyRepository.save(new Vocabulary(userA.getId(), "ancient", "adjective", null, "cổ xưa", null, null, null, null, null, "B1"));

        BulkImportConfirmRequest req = new BulkImportConfirmRequest(
            List.of(
                new BulkImportItemRequest("ancient", "adjective", "cổ xưa", null, null, null, null, null, null, "B1"),
                new BulkImportItemRequest("modern", "adjective", "hiện đại", null, null, null, null, null, null, "A2")
            ),
            null,
            "SKIP"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(1))
            .andExpect(jsonPath("$.skippedCount").value(1));

        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("18. Duplicate Policy REJECT aborts entire batch when duplicate exists")
    void testDuplicatePolicyRejectAbortsBatch() throws Exception {
        vocabularyRepository.save(new Vocabulary(userA.getId(), "ancient", "adjective", null, "cổ xưa", null, null, null, null, null, "B1"));

        BulkImportConfirmRequest req = new BulkImportConfirmRequest(
            List.of(
                new BulkImportItemRequest("ancient", "adjective", "cổ xưa", null, null, null, null, null, null, "B1"),
                new BulkImportItemRequest("futuristic", "adjective", "thuộc về tương lai", null, null, null, null, null, null, "B2")
            ),
            null,
            "REJECT"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_VOCABULARY"));

        // Transaction rolled back: "futuristic" was NOT saved
        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("19. Preview endpoint makes zero database writes")
    void testPreviewMakesZeroDatabaseWrites() throws Exception {
        long vocabCountBefore = vocabularyRepository.count();
        long srsCountBefore = srsItemRepository.count();
        long eventCountBefore = learningActivityEventRepository.count();

        String csv = "term,vietnameseMeaning\nspectacular,ngoạn mục\n";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk());

        assertThat(vocabularyRepository.count()).isEqualTo(vocabCountBefore);
        assertThat(srsItemRepository.count()).isEqualTo(srsCountBefore);
        assertThat(learningActivityEventRepository.count()).isEqualTo(eventCountBefore);
    }

    @Test
    @DisplayName("20. Optional collection assignment adds imported words to the target deck")
    void testOptionalCollectionAssignment() throws Exception {
        Collection deck = collectionRepository.save(new Collection(userA.getId(), "TOEFL Academic", "Essential words"));

        BulkImportConfirmRequest req = new BulkImportConfirmRequest(
            List.of(
                new BulkImportItemRequest("synthesize", "verb", "tổng hợp", null, null, null, null, null, null, "C1"),
                new BulkImportItemRequest("analyze", "verb", "phân tích", null, null, null, null, null, null, "B1")
            ),
            deck.getId(),
            "SKIP"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(2));

        // Check deck count
        assertThat(vocabularyCollectionRepository.countActiveWordsByCollectionId(deck.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("21. Cross-user collection assignment is rejected with HTTP 404")
    void testCrossUserCollectionRejected() throws Exception {
        Collection deckB = collectionRepository.save(new Collection(userB.getId(), "User B Deck", null));

        BulkImportConfirmRequest req = new BulkImportConfirmRequest(
            List.of(new BulkImportItemRequest("stealth", "noun", "sự lén lút", null, null, null, null, null, null, "B2")),
            deckB.getId(),
            "SKIP"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Nothing saved
        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(0);
    }

    @Test
    @DisplayName("22. Every imported word provisions exactly one SRS item ready for review")
    void testImportProvisionsExactlyOneSrsItemPerWord() throws Exception {
        BulkImportConfirmRequest req = new BulkImportConfirmRequest(
            List.of(
                new BulkImportItemRequest("clarity", "noun", "sự rõ ràng", null, null, null, null, null, null, "B2"),
                new BulkImportItemRequest("brevity", "noun", "sự ngắn gọn súc tích", null, null, null, null, null, null, "C1")
            ),
            null,
            "SKIP"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(2));

        List<SrsItem> items = srsItemRepository.findAll();
        assertThat(items).hasSize(2);
        for (SrsItem item : items) {
            assertThat(item.getUserId()).isEqualTo(userA.getId());
            assertThat(item.getState()).isEqualTo("NEW");
            assertThat(item.getRepetitionCount()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("23. Import generates ZERO learning activity events")
    void testImportCreatesZeroActivityEvents() throws Exception {
        BulkImportConfirmRequest req = new BulkImportConfirmRequest(
            List.of(new BulkImportItemRequest("zenith", "noun", "đỉnh cao", null, null, null, null, null, null, "C2")),
            null,
            "SKIP"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        assertThat(learningActivityEventRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Matrix A: Two simultaneous imports containing same new words under SKIP policy produce consistent outcome and exactly 1 SRS card per word")
    void testConcurrentImportsSameWordsSkipPolicy() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger totalImported = new AtomicInteger(0);
        AtomicInteger totalSkipped = new AtomicInteger(0);

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    BulkImportConfirmRequest req = new BulkImportConfirmRequest(
                        List.of(
                            new BulkImportItemRequest("apple", "noun", "quả táo", null, null, null, null, null, null, "A1"),
                            new BulkImportItemRequest("banana", "noun", "quả chuối", null, null, null, null, null, null, "A1")
                        ),
                        null,
                        "SKIP"
                    );

                    mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk())
                        .andDo(result -> {
                            String body = result.getResponse().getContentAsString();
                            BulkImportConfirmResponse resp = objectMapper.readValue(body, BulkImportConfirmResponse.class);
                            totalImported.addAndGet(resp.importedCount());
                            totalSkipped.addAndGet(resp.skippedCount());
                        });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exactly 2 logical vocabulary entries in database
        long vocabCount = vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId());
        assertThat(vocabCount).isEqualTo(2);
        // Exactly 2 SRS cards (1 per word)
        assertThat(srsItemRepository.count()).isEqualTo(2);
        // Exactly 2 imported across both threads, and 2 skipped
        assertThat(totalImported.get()).isEqualTo(2);
        assertThat(totalSkipped.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Matrix B: Two simultaneous imports with REJECT policy and overlapping terms yield deterministic conflict without partial batch commit")
    void testConcurrentImportsRejectPolicyOverlappingTerms() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<BulkImportItemRequest> batch1 = List.of(
            new BulkImportItemRequest("resilience", "noun", "sự kiên cường", null, null, null, null, null, null, "B2"),
            new BulkImportItemRequest("tenacity", "noun", "sự bền bỉ", null, null, null, null, null, null, "C1")
        );
        List<BulkImportItemRequest> batch2 = List.of(
            new BulkImportItemRequest("resilience", "noun", "sự kiên cường", null, null, null, null, null, null, "B2"),
            new BulkImportItemRequest("perseverance", "noun", "sự kiên trì", null, null, null, null, null, null, "C1")
        );

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkImportConfirmRequest(batch1, null, "REJECT"))))
                    .andDo(result -> {
                        int st = result.getResponse().getStatus();
                        if (st == 200) successCount.incrementAndGet();
                        else if (st == 409) conflictCount.incrementAndGet();
                    });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkImportConfirmRequest(batch2, null, "REJECT"))))
                    .andDo(result -> {
                        int st = result.getResponse().getStatus();
                        if (st == 200) successCount.incrementAndGet();
                        else if (st == 409) conflictCount.incrementAndGet();
                    });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        f1.get();
        f2.get();
        executor.shutdown();

        // Exactly one batch succeeded, and exactly one batch failed with 409 Conflict
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // The failed batch must NOT have left partial data committed in the database!
        long totalWords = vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId());
        assertThat(totalWords).isEqualTo(2); // Only the 2 words of the winning batch exist!
        assertThat(srsItemRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Matrix C: Quick Add and Bulk Import racing on same logical vocabulary coordinate atomically without duplicate rows or orphan cards")
    void testQuickAddAndBulkImportRaceOnSameLogicalVocabulary() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger quickAddStatus = new AtomicInteger(0);
        AtomicInteger importStatus = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                com.englishflow.vocabulary.dto.CreateVocabularyRequest req =
                    new com.englishflow.vocabulary.dto.CreateVocabularyRequest(
                        "serendipity", "noun", "/ˌser.ənˈdɪp.ə.ti/", "sự tình cờ may mắn",
                        "A happy chance", null, null, null, null, "C1", null
                    );
                mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary"), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                    .andDo(res -> quickAddStatus.set(res.getResponse().getStatus()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                BulkImportConfirmRequest req = new BulkImportConfirmRequest(
                    List.of(new BulkImportItemRequest("serendipity", "noun", "sự tình cờ may mắn", null, null, null, null, null, null, "C1")),
                    null,
                    "REJECT"
                );
                mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                    .andDo(res -> importStatus.set(res.getResponse().getStatus()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        f1.get();
        f2.get();
        executor.shutdown();

        // Exactly one succeeded (200/201), and the other failed with 409 Conflict
        int qa = quickAddStatus.get();
        int imp = importStatus.get();
        assertThat((qa == 201 && imp == 409) || (qa == 409 && imp == 200)).isTrue();

        // Database has exactly 1 vocabulary item and 1 SRS item
        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(1);
        assertThat(srsItemRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Matrix D: Two concurrent imports into same collection correctly maintain membership and count without duplicate join rows")
    void testConcurrentImportsIntoSameCollection() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Core Deck", "Deck description"));
        UUID colId = col.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        List<BulkImportItemRequest> batch1 = List.of(
            new BulkImportItemRequest("word_a1", "noun", "nghĩa a1", null, null, null, null, null, null, "A1"),
            new BulkImportItemRequest("word_a2", "noun", "nghĩa a2", null, null, null, null, null, null, "A1")
        );
        List<BulkImportItemRequest> batch2 = List.of(
            new BulkImportItemRequest("word_b1", "noun", "nghĩa b1", null, null, null, null, null, null, "A1"),
            new BulkImportItemRequest("word_b2", "noun", "nghĩa b2", null, null, null, null, null, null, "A1")
        );

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkImportConfirmRequest(batch1, colId, "SKIP"))))
                    .andExpect(status().isOk());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkImportConfirmRequest(batch2, colId, "SKIP"))))
                    .andExpect(status().isOk());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        f1.get();
        f2.get();
        executor.shutdown();

        // 4 vocabulary items total
        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(4);
        // 4 collection memberships
        long activeInCol = vocabularyCollectionRepository.countActiveWordsByCollectionId(colId);
        assertThat(activeInCol).isEqualTo(4);
    }

    @Test
    @DisplayName("Matrix E: Concurrent imports for different users execute with strict isolation and no global serialization")
    void testConcurrentImportsDifferentUsersIsolation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        List<BulkImportItemRequest> batch = List.of(
            new BulkImportItemRequest("common_word", "noun", "từ chung", null, null, null, null, null, null, "A1")
        );

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkImportConfirmRequest(batch, null, "REJECT"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.importedCount").value(1));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkImportConfirmRequest(batch, null, "REJECT"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.importedCount").value(1));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        f1.get();
        f2.get();
        executor.shutdown();

        // Both users now have their own copy of "common_word"
        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(1);
        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userB.getId())).isEqualTo(1);
        assertThat(srsItemRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Matrix F: Import request failing mid-operation rolls back atomically without orphan records")
    void testImportRollbackOnMidOperationFailure() throws Exception {
        UUID nonExistentColId = UUID.randomUUID();
        BulkImportConfirmRequest req = new BulkImportConfirmRequest(
            List.of(
                new BulkImportItemRequest("word1", "noun", "nghĩa 1", null, null, null, null, null, null, "A1"),
                new BulkImportItemRequest("word2", "noun", "nghĩa 2", null, null, null, null, null, null, "A1")
            ),
            nonExistentColId,
            "SKIP"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Verify atomic rollback
        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(0);
        assertThat(srsItemRepository.count()).isEqualTo(0);
        assertThat(vocabularyCollectionRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Security: Malformed UTF-8 byte stream rejected with HTTP 400 MALFORMED_UTF8")
    void testMalformedUtf8ByteSequencesRejected() throws Exception {
        byte[] malformedBytes = new byte[]{'t', 'e', 'r', 'm', ',', 'v', 'i', 'e', 't', 'n', 'a', 'm', 'e', 's', 'e', 'M', 'e', 'a', 'n', 'i', 'n', 'g', '\n', (byte) 0xC3, (byte) 0x28, '\n'};
        MockMultipartFile file = new MockMultipartFile("file", "bad_utf8.csv", "text/csv", malformedBytes);

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_UTF8"));
    }

    @Test
    @DisplayName("Security: Upload containing duplicate headers is rejected with HTTP 400 DUPLICATE_HEADERS")
    void testDuplicateHeadersRejected() throws Exception {
        String csv = "term,term,vietnameseMeaning\ncat,cat,con mèo\n";
        MockMultipartFile file = new MockMultipartFile("file", "dup_header.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DUPLICATE_HEADERS"));
    }

    @Test
    @DisplayName("Security: Fields containing NUL control characters (\\0) are rejected in preview and confirm")
    void testControlCharacterNulRejected() throws Exception {
        // In preview
        String csv = "term,vietnameseMeaning\npoison\0term,nghĩa\n";
        MockMultipartFile file = new MockMultipartFile("file", "nul.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/bulk-import/preview").file(file), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errorRows").value(1))
            .andExpect(jsonPath("$.previewRows[0].status").value("INVALID"))
            .andExpect(jsonPath("$.previewRows[0].validationMessage").value(containsString("NUL")));

        // In confirm direct JSON
        BulkImportConfirmRequest req = new BulkImportConfirmRequest(
            List.of(new BulkImportItemRequest("poison\0term", "noun", "nghĩa", null, null, null, null, null, null, "A1")),
            null,
            "SKIP"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ITEM_DATA"));
    }

    @Test
    @DisplayName("Security: Tampered JSON payload with invalid POS rejected by confirm service validation")
    void testConfirmRejectsInvalidRowTamperedPayload() throws Exception {
        BulkImportConfirmRequest req = new BulkImportConfirmRequest(
            List.of(new BulkImportItemRequest("validterm", "invalid_pos", "nghĩa hợp lệ", null, null, null, null, null, null, "A1")),
            null,
            "SKIP"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ITEM_DATA"))
            .andExpect(jsonPath("$.message").value(containsString("Item validation failed")));
    }

    @Test
    @DisplayName("Security: Tampered JSON payload with oversized term (>100 chars) rejected by bean validation")
    void testConfirmRejectsOversizedFieldTamperedPayload() throws Exception {
        String longTerm = "a".repeat(101);
        BulkImportConfirmRequest req = new BulkImportConfirmRequest(
            List.of(new BulkImportItemRequest(longTerm, "noun", "nghĩa hợp lệ", null, null, null, null, null, null, "A1")),
            null,
            "SKIP"
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary/bulk-import/confirm"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("25. Direct import via /import?dry_run=false works end-to-end")
    void testDirectImportEndpoint() throws Exception {
        String csv = "term,vietnameseMeaning\nsolitude,sự cô độc thanh tịnh\n";
        MockMultipartFile file = new MockMultipartFile("file", "direct.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(withAuthAndCsrf(multipart("/api/v1/vocabulary/import?dry_run=false").file(file), userA))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.importedCount").value(1));

        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("26. Downloadable CSV and TSV templates are generated with UTF-8 BOM")
    void testDownloadTemplateEndpoint() throws Exception {
        // 1. CSV
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/vocabulary/bulk-import/template?format=csv"), userA))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/csv")))
            .andExpect(content().string(containsString("metaphor")))
            .andExpect(content().string(containsString("phép ẩn dụ")));

        // 2. TSV
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/vocabulary/bulk-import/template?format=tsv"), userA))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/tab-separated-values")))
            .andExpect(content().string(containsString("metaphor")))
            .andExpect(content().string(containsString("phép ẩn dụ")));
    }
}
