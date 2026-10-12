package com.englishflow.vocabulary;

import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.dto.CreateVocabularyRequest;
import com.englishflow.vocabulary.dto.UpdateVocabularyRequest;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class VocabularyIntegrationTest {

    private static final String MOCK_CSRF = "vocab-test-csrf-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        vocabularyRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(new User(
            "user.a@example.com",
            passwordEncoder.encode("Pass123!"),
            "User Alpha",
            "UTC",
            20
        ));

        userB = userRepository.save(new User(
            "user.b@example.com",
            passwordEncoder.encode("Pass123!"),
            "User Beta",
            "UTC",
            20
        ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpSession createSessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        context.setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        return builder
            .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
            .header("X-XSRF-TOKEN", MOCK_CSRF);
    }

    @Test
    @DisplayName("1. Authenticated user creates vocabulary with correct owner and safe defaults")
    void createVocabularySuccess() throws Exception {
        MockHttpSession session = createSessionFor(userA);

        CreateVocabularyRequest request = new CreateVocabularyRequest(
            "resilience",
            "noun",
            "/rɪˈzɪl.jəns/",
            "khả năng phục hồi",
            "The capacity to recover quickly from difficulties.",
            "Her resilience in adversity was remarkable.",
            "Khả năng phục hồi của cô ấy thật đáng kinh ngạc.",
            "Academic word",
            "Oxford 3000",
            "B2"
        );

        MvcResult result = mockMvc.perform(withCsrf(post("/api/v1/vocabulary"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.term").value("resilience"))
            .andExpect(jsonPath("$.partOfSpeech").value("noun"))
            .andExpect(jsonPath("$.phoneticIpa").value("/rɪˈzɪl.jəns/"))
            .andExpect(jsonPath("$.vietnameseMeaning").value("khả năng phục hồi"))
            .andExpect(jsonPath("$.difficultyLevel").value("B2"))
            .andExpect(jsonPath("$.srsStatus.state").value("NEW"))
            .andReturn();

        // 2. Created record has correct owner in PostgreSQL
        List<Vocabulary> userAVocabs = vocabularyRepository.findByUserIdAndNormalizedTermAndDeletedAtIsNull(userA.getId(), "resilience");
        assertThat(userAVocabs).hasSize(1);
        Vocabulary saved = userAVocabs.get(0);
        assertThat(saved.getUserId()).isEqualTo(userA.getId());
        assertThat(saved.getTerm()).isEqualTo("resilience");
        assertThat(saved.getNormalizedTerm()).isEqualTo("resilience");
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("3. Unauthenticated create is rejected with 401 Unauthorized")
    void unauthenticatedCreateRejected() throws Exception {
        CreateVocabularyRequest request = new CreateVocabularyRequest(
            "perseverance", "noun", null, "sự kiên trì", null, null, null, null, null, null
        );

        mockMvc.perform(withCsrf(post("/api/v1/vocabulary"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("4. Missing CSRF token rejects mutation with 403 Forbidden")
    void missingCsrfRejectsMutation() throws Exception {
        MockHttpSession session = createSessionFor(userA);

        CreateVocabularyRequest request = new CreateVocabularyRequest(
            "tenacity", "noun", null, "sự ngoan cường", null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/vocabulary")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("6. User A cannot read User B's vocabulary (returns 404)")
    void multiTenantReadIsolation() throws Exception {
        // User B owns a word
        Vocabulary vocabB = vocabularyRepository.save(new Vocabulary(
            userB.getId(), "secret", "noun", null, "bí mật", null, null, null, null, null, "C1"
        ));

        // User A attempts to read User B's word
        MockHttpSession sessionA = createSessionFor(userA);
        mockMvc.perform(get("/api/v1/vocabulary/" + vocabB.getId())
                .session(sessionA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("7. User A cannot modify User B's vocabulary (returns 404)")
    void multiTenantUpdateIsolation() throws Exception {
        Vocabulary vocabB = vocabularyRepository.save(new Vocabulary(
            userB.getId(), "integrity", "noun", null, "chính trực", null, null, null, null, null, "B2"
        ));

        MockHttpSession sessionA = createSessionFor(userA);
        UpdateVocabularyRequest update = new UpdateVocabularyRequest(
            "tampered", "noun", null, "bị can thiệp", null, null, null, null, null, "B2"
        );

        mockMvc.perform(withCsrf(put("/api/v1/vocabulary/" + vocabB.getId()))
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Verify User B's word remains intact
        Vocabulary unchanged = vocabularyRepository.findById(vocabB.getId()).orElseThrow();
        assertThat(unchanged.getTerm()).isEqualTo("integrity");
    }

    @Test
    @DisplayName("8. User A cannot delete User B's vocabulary (returns 404)")
    void multiTenantDeleteIsolation() throws Exception {
        Vocabulary vocabB = vocabularyRepository.save(new Vocabulary(
            userB.getId(), "fortitude", "noun", null, "sự chịu đựng", null, null, null, null, null, "C1"
        ));

        MockHttpSession sessionA = createSessionFor(userA);
        mockMvc.perform(withCsrf(delete("/api/v1/vocabulary/" + vocabB.getId()))
                .session(sessionA))
            .andExpect(status().isNotFound());

        // Verify User B's word is NOT deleted
        Vocabulary unchanged = vocabularyRepository.findById(vocabB.getId()).orElseThrow();
        assertThat(unchanged.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("9 & 10. Search and pagination return only owner-matching records with accurate totals")
    void searchAndPaginationScopedToUser() throws Exception {
        // Seed 15 words for User A
        for (int i = 1; i <= 15; i++) {
            vocabularyRepository.save(new Vocabulary(
                userA.getId(), "word" + String.format("%02d", i), "noun", null, "nghĩa " + i,
                "Definition of word " + i, null, null, null, null, "B1"
            ));
        }

        // Seed 5 words for User B
        for (int i = 1; i <= 5; i++) {
            vocabularyRepository.save(new Vocabulary(
                userB.getId(), "other" + i, "verb", null, "khác", null, null, null, null, null, "A2"
            ));
        }

        MockHttpSession sessionA = createSessionFor(userA);

        // Page 0 (size 10)
        mockMvc.perform(get("/api/v1/vocabulary?page=0&size=10")
                .session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(15))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content.length()").value(10));

        // Search within User A words
        mockMvc.perform(get("/api/v1/vocabulary?search=word05")
                .session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].term").value("word05"));

        // User A cannot find User B words via search
        mockMvc.perform(get("/api/v1/vocabulary?search=other")
                .session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("11. Duplicate check endpoint advisory and duplicate rejection on identical term + pos")
    void duplicateDetectionAndRejection() throws Exception {
        MockHttpSession session = createSessionFor(userA);

        // Add word first
        CreateVocabularyRequest initial = new CreateVocabularyRequest(
            "lead", "noun", "/liːd/", "chì", "A heavy metal", null, null, null, null, "B1"
        );
        mockMvc.perform(withCsrf(post("/api/v1/vocabulary"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initial)))
            .andExpect(status().isCreated());

        // Advisory duplicate check returns exists: true
        mockMvc.perform(get("/api/v1/vocabulary/duplicates/check?term=LEAD&partOfSpeech=noun")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exists").value(true))
            .andExpect(jsonPath("$.existingEntries.length()").value(1))
            .andExpect(jsonPath("$.existingEntries[0].term").value("lead"));

        // Attempt duplicate creation with same term & part of speech returns 409 Conflict
        CreateVocabularyRequest duplicate = new CreateVocabularyRequest(
            "  Lead  ", "noun", null, "chì kim loại", null, null, null, null, null, "B1"
        );
        mockMvc.perform(withCsrf(post("/api/v1/vocabulary"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_VOCABULARY"));

        // However, same term with DIFFERENT part of speech (verb) is allowed
        CreateVocabularyRequest diffPos = new CreateVocabularyRequest(
            "lead", "verb", "/liːd/", "dẫn đường", "To guide or direct", null, null, null, null, "A2"
        );
        mockMvc.perform(withCsrf(post("/api/v1/vocabulary"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(diffPos)))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("12. Update modifies allowed fields while preserving owner and creation timestamp")
    void updateVocabularySuccess() throws Exception {
        MockHttpSession session = createSessionFor(userA);

        Vocabulary vocab = vocabularyRepository.save(new Vocabulary(
            userA.getId(), "ephemeral", "adjective", "/ɪˈfem.ər.əl/", "ngắn ngủi",
            "Lasting for a very short time.", null, null, null, null, "C1"
        ));
        UUID vocabId = vocab.getId();
        java.time.Instant originalCreatedAt = vocab.getCreatedAt();

        UpdateVocabularyRequest update = new UpdateVocabularyRequest(
            "ephemeral",
            "adjective",
            "/ɪˈfem.ər.əl/",
            "phù du, ngắn ngủi",
            "Lasting for a very short time; transient.",
            "Fame in the digital age is ephemeral.",
            "Danh tiếng trong kỷ nguyên số là phù du.",
            "Synonyms: fleeting, transient",
            "Academic Word List",
            "C2"
        );

        mockMvc.perform(withCsrf(put("/api/v1/vocabulary/" + vocabId))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vietnameseMeaning").value("phù du, ngắn ngủi"))
            .andExpect(jsonPath("$.difficultyLevel").value("C2"))
            .andExpect(jsonPath("$.notes").value("Synonyms: fleeting, transient"));

        Vocabulary reloaded = vocabularyRepository.findById(vocabId).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo(userA.getId());
        assertThat(reloaded.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(reloaded.getVietnameseMeaning()).isEqualTo("phù du, ngắn ngủi");
        assertThat(reloaded.getDifficultyLevel()).isEqualTo("C2");
    }

    @Test
    @DisplayName("13 & 14. Soft delete sets deleted_at and excludes entry from subsequent list/detail")
    void softDeleteExcludesFromQueries() throws Exception {
        MockHttpSession session = createSessionFor(userA);

        Vocabulary vocab = vocabularyRepository.save(new Vocabulary(
            userA.getId(), "temporary", "adjective", null, "tạm thời", null, null, null, null, null, "B1"
        ));
        UUID vocabId = vocab.getId();

        // Perform soft delete
        mockMvc.perform(withCsrf(delete("/api/v1/vocabulary/" + vocabId))
                .session(session))
            .andExpect(status().isNoContent());

        // Verify deleted_at is set in database
        Vocabulary inDb = vocabularyRepository.findById(vocabId).orElseThrow();
        assertThat(inDb.isDeleted()).isTrue();
        assertThat(inDb.getDeletedAt()).isNotNull();

        // 14. Excluded from detail (returns 404)
        mockMvc.perform(get("/api/v1/vocabulary/" + vocabId)
                .session(session))
            .andExpect(status().isNotFound());

        // Excluded from list query
        mockMvc.perform(get("/api/v1/vocabulary")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("15. Malformed inputs produce RFC 7807 validation errors")
    void malformedInputsValidation() throws Exception {
        MockHttpSession session = createSessionFor(userA);

        CreateVocabularyRequest invalid = new CreateVocabularyRequest(
            "", // blank term
            "invalid_pos", // invalid part of speech
            null,
            "", // blank meaning
            null, null, null, null, null,
            "INVALID_CEFR" // invalid difficulty
        );

        mockMvc.perform(withCsrf(post("/api/v1/vocabulary"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.validationErrors").isArray());
    }
}
