package com.englishflow.vocabulary;

import com.englishflow.analytics.LearningActivityEvent;
import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.review.SrsItem;
import com.englishflow.review.SrsItemRepository;
import com.englishflow.review.SrsReviewHistory;
import com.englishflow.review.SrsReviewHistoryRepository;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.dto.AssignVocabularyRequest;
import com.englishflow.vocabulary.dto.CreateCollectionRequest;
import com.englishflow.vocabulary.dto.CreateVocabularyRequest;
import com.englishflow.vocabulary.dto.UpdateCollectionRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CollectionIntegrationTest {

    private static final String MOCK_CSRF = "collection-test-csrf-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private VocabularyCollectionRepository vocabularyCollectionRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private SrsItemRepository srsItemRepository;

    @Autowired
    private SrsReviewHistoryRepository srsReviewHistoryRepository;

    @Autowired
    private LearningActivityEventRepository activityEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        cleanUp();
        userA = createTestUser("collection-user-a@example.com", "User Alpha");
        userB = createTestUser("collection-user-b@example.com", "User Beta");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        SecurityContextHolder.clearContext();
        activityEventRepository.deleteAll();
        srsReviewHistoryRepository.deleteAll();
        srsItemRepository.deleteAll();
        vocabularyCollectionRepository.deleteAll();
        collectionRepository.deleteAll();
        vocabularyRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createTestUser(String email, String displayName) {
        User user = new User(
            email,
            passwordEncoder.encode("Password123!"),
            displayName,
            "Asia/Ho_Chi_Minh",
            20
        );
        return userRepository.save(user);
    }

    private MockHttpSession createAuthSession(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        securityContext.setAuthentication(authentication);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return session;
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        return builder
            .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
            .header("X-XSRF-TOKEN", MOCK_CSRF);
    }

    private MockHttpServletRequestBuilder withAuthAndCsrf(MockHttpServletRequestBuilder builder, User user) {
        return withCsrf(builder).session(createAuthSession(user));
    }

    private Vocabulary createVocabulary(User user, String term, String pos, String meaning) {
        Vocabulary v = new Vocabulary(
            user.getId(),
            term,
            pos,
            "/ipa/",
            meaning,
            "English definition",
            "Example sentence",
            "Bản dịch ví dụ",
            "Notes",
            "IELTS 17",
            "B2"
        );
        return vocabularyRepository.save(v);
    }

    @Test
    @DisplayName("1. Authenticated user creates collection successfully")
    void testAuthenticatedUserCreatesCollection() throws Exception {
        CreateCollectionRequest req = new CreateCollectionRequest("IELTS Academic Core", "Essential words for IELTS");

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.title").value("IELTS Academic Core"))
            .andExpect(jsonPath("$.description").value("Essential words for IELTS"))
            .andExpect(jsonPath("$.wordCount").value(0));

        assertThat(collectionRepository.countByUserId(userA.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("2. Anonymous requests to collections are rejected with 401 and missing CSRF with 403")
    void testAnonymousRequestsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/collections"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        CreateCollectionRequest req = new CreateCollectionRequest("Unauthorized Deck", null);
        mockMvc.perform(withCsrf(post("/api/v1/collections"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // Missing CSRF token rejects mutation with 403 Forbidden
        mockMvc.perform(post("/api/v1/collections")
                .session(createAuthSession(userA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("3. Duplicate collection title for same user returns 409 TITLE_ALREADY_EXISTS")
    void testDuplicateCollectionNamingBehavior() throws Exception {
        CreateCollectionRequest req1 = new CreateCollectionRequest("Business English", "Workplace vocab");
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated());

        CreateCollectionRequest req2 = new CreateCollectionRequest("  Business English  ", "Duplicate deck");
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TITLE_ALREADY_EXISTS"));

        // But user B can create a collection with the same title
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections"), userB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("4. Invalid collection input is rejected with 400 VALIDATION_FAILED")
    void testInvalidCollectionInputRejected() throws Exception {
        CreateCollectionRequest emptyTitleReq = new CreateCollectionRequest("", "Desc");
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyTitleReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String longTitle = "A".repeat(101);
        CreateCollectionRequest longTitleReq = new CreateCollectionRequest(longTitle, "Desc");
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(longTitleReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("5. Collection list is strictly owner-scoped")
    void testCollectionListIsOwnerScoped() throws Exception {
        collectionRepository.save(new Collection(userA.getId(), "Deck A1", null));
        collectionRepository.save(new Collection(userA.getId(), "Deck A2", null));
        collectionRepository.save(new Collection(userB.getId(), "Deck B1", null));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].title").isNotEmpty());

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections"), userB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Deck B1"));
    }

    @Test
    @DisplayName("6. User A cannot access User B's collection detail (404 NOT_FOUND)")
    void testUserACannotAccessUserBCollection() throws Exception {
        Collection colB = collectionRepository.save(new Collection(userB.getId(), "Secret B", null));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + colB.getId()), userA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("7. User A cannot modify or delete User B's collection (404 NOT_FOUND)")
    void testUserACannotModifyOrDeleteUserBCollection() throws Exception {
        Collection colB = collectionRepository.save(new Collection(userB.getId(), "Secret B", null));

        UpdateCollectionRequest updateReq = new UpdateCollectionRequest("Hacked Title", null);
        mockMvc.perform(withAuthAndCsrf(put("/api/v1/collections/" + colB.getId()), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(withAuthAndCsrf(delete("/api/v1/collections/" + colB.getId()), userA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(collectionRepository.findById(colB.getId())).isPresent();
    }

    @Test
    @DisplayName("8. Add vocabulary to collection successfully")
    void testAddVocabularyToCollection() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Col 1", null));
        Vocabulary v1 = createVocabulary(userA, "perseverance", "noun", "kiên trì");

        AssignVocabularyRequest assignReq = new AssignVocabularyRequest(List.of(v1.getId()));
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections/" + col.getId() + "/vocabularies"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.addedCount").value(1));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + col.getId()), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.wordCount").value(1));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + col.getId() + "/vocabularies"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].term").value("perseverance"));
    }

    @Test
    @DisplayName("9. Add same vocabulary to multiple collections")
    void testAddSameVocabularyToMultipleCollections() throws Exception {
        Collection col1 = collectionRepository.save(new Collection(userA.getId(), "Col 1", null));
        Collection col2 = collectionRepository.save(new Collection(userA.getId(), "Col 2", null));
        Vocabulary v = createVocabulary(userA, "ubiquitous", "adjective", "phổ biến ở khắp nơi");

        AssignVocabularyRequest req = new AssignVocabularyRequest(List.of(v.getId()));
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections/" + col1.getId() + "/words"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.addedCount").value(1));

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections/" + col2.getId() + "/words"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.addedCount").value(1));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + col1.getId()), userA))
            .andExpect(jsonPath("$.wordCount").value(1));
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + col2.getId()), userA))
            .andExpect(jsonPath("$.wordCount").value(1));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/vocabulary/" + v.getId() + "/collections"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("10. Duplicate membership addition is idempotent with no duplicate rows")
    void testDuplicateMembershipBehavior() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Col 1", null));
        Vocabulary v = createVocabulary(userA, "tenacity", "noun", "sự bền bỉ");

        AssignVocabularyRequest req = new AssignVocabularyRequest(List.of(v.getId()));
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections/" + col.getId() + "/vocabularies"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.addedCount").value(1));

        // Re-adding same word returns addedCount: 0
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections/" + col.getId() + "/vocabularies"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.addedCount").value(0));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + col.getId()), userA))
            .andExpect(jsonPath("$.wordCount").value(1));
    }

    @Test
    @DisplayName("11. Cross-user vocabulary association is rejected (User A cannot add User B's vocabulary)")
    void testCrossUserVocabularyAssociationRejected() throws Exception {
        Collection colA = collectionRepository.save(new Collection(userA.getId(), "Col A", null));
        Vocabulary vocabB = createVocabulary(userB, "clandestine", "adjective", "bí mật");

        AssignVocabularyRequest req = new AssignVocabularyRequest(List.of(vocabB.getId()));
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections/" + colA.getId() + "/vocabularies"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("12. Cross-user collection association is rejected (User A cannot add vocabulary to User B's collection)")
    void testCrossUserCollectionAssociationRejected() throws Exception {
        Collection colB = collectionRepository.save(new Collection(userB.getId(), "Col B", null));
        Vocabulary vocabA = createVocabulary(userA, "eloquent", "adjective", "hùng hồn");

        AssignVocabularyRequest req = new AssignVocabularyRequest(List.of(vocabA.getId()));
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections/" + colB.getId() + "/vocabularies"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("13. Database hard constraint rejects mismatched ownership FK")
    void testDatabaseRejectsMismatchedOwnershipFk() {
        Collection colA = collectionRepository.save(new Collection(userA.getId(), "Deck A", null));
        Vocabulary vocabB = createVocabulary(userB, "anomaly", "noun", "bất thường");

        // Attempt direct SQL insertion into vocabulary_collections with mismatched user_id
        // Try user_id = userA.id with vocabB (which belongs to userB)
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                "INSERT INTO vocabulary_collections (user_id, collection_id, vocabulary_id, added_at) VALUES (?, ?, ?, NOW())",
                userA.getId(), colA.getId(), vocabB.getId()
            );
        }).isInstanceOf(DataIntegrityViolationException.class);

        // Try user_id = userB.id with colA (which belongs to userA)
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                "INSERT INTO vocabulary_collections (user_id, collection_id, vocabulary_id, added_at) VALUES (?, ?, ?, NOW())",
                userB.getId(), colA.getId(), vocabB.getId()
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("14. Removing membership preserves vocabulary intact")
    void testRemovingMembershipPreservesVocabulary() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Deck A", null));
        Vocabulary vocab = createVocabulary(userA, "pragmatic", "adjective", "thực dụng");
        vocabularyCollectionRepository.save(new VocabularyCollection(userA.getId(), col.getId(), vocab.getId()));

        mockMvc.perform(withAuthAndCsrf(delete("/api/v1/collections/" + col.getId() + "/vocabularies/" + vocab.getId()), userA))
            .andExpect(status().isNoContent());

        // Membership row is gone
        assertThat(vocabularyCollectionRepository.existsByCollectionIdAndVocabularyId(col.getId(), vocab.getId())).isFalse();

        // Underlying vocabulary is COMPLETELY intact and active in library
        Optional<Vocabulary> activeVocab = vocabularyRepository.findByIdAndUserIdAndDeletedAtIsNull(vocab.getId(), userA.getId());
        assertThat(activeVocab).isPresent();
        assertThat(activeVocab.get().getTerm()).isEqualTo("pragmatic");
        assertThat(activeVocab.get().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("15. Deleting collection preserves vocabulary intact")
    void testDeletingCollectionPreservesVocabulary() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Deck To Delete", null));
        Vocabulary vocab = createVocabulary(userA, "serendipity", "noun", "may mắn bất ngờ");
        vocabularyCollectionRepository.save(new VocabularyCollection(userA.getId(), col.getId(), vocab.getId()));

        mockMvc.perform(withAuthAndCsrf(delete("/api/v1/collections/" + col.getId()), userA))
            .andExpect(status().isNoContent());

        // Collection is gone
        assertThat(collectionRepository.findById(col.getId())).isEmpty();

        // Join rows are gone
        assertThat(vocabularyCollectionRepository.existsByCollectionIdAndVocabularyId(col.getId(), vocab.getId())).isFalse();

        // Underlying vocabulary remains alive and untouched
        Optional<Vocabulary> aliveVocab = vocabularyRepository.findByIdAndUserIdAndDeletedAtIsNull(vocab.getId(), userA.getId());
        assertThat(aliveVocab).isPresent();
        assertThat(aliveVocab.get().getTerm()).isEqualTo("serendipity");
    }

    @Test
    @DisplayName("16. Deleting collection preserves SRS schedule intact")
    void testDeletingCollectionPreservesSrsSchedule() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Deck SRS", null));
        Vocabulary vocab = createVocabulary(userA, "lucid", "adjective", "rõ ràng, minh mẫn");
        vocabularyCollectionRepository.save(new VocabularyCollection(userA.getId(), col.getId(), vocab.getId()));

        SrsItem srsItem = new SrsItem(userA.getId(), vocab.getId(), LocalDate.now());
        srsItem.setRepetitionCount(3);
        srsItem.setIntervalDays(6);
        srsItem.setDueDate(LocalDate.now().plusDays(6));
        srsItem = srsItemRepository.save(srsItem);

        mockMvc.perform(withAuthAndCsrf(delete("/api/v1/collections/" + col.getId()), userA))
            .andExpect(status().isNoContent());

        // SRS item remains intact with identical values
        Optional<SrsItem> preservedSrs = srsItemRepository.findById(srsItem.getId());
        assertThat(preservedSrs).isPresent();
        assertThat(preservedSrs.get().getRepetitionCount()).isEqualTo(3);
        assertThat(preservedSrs.get().getIntervalDays()).isEqualTo(6);
        assertThat(preservedSrs.get().getDueDate()).isEqualTo(LocalDate.now().plusDays(6));
    }

    @Test
    @DisplayName("17. Deleting collection preserves SRS review history and learning activity events")
    void testDeletingCollectionPreservesHistoryAndActivityEvents() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Deck Analytics", null));
        Vocabulary vocab = createVocabulary(userA, "ephemeral", "adjective", "ngắn ngủi");
        vocabularyCollectionRepository.save(new VocabularyCollection(userA.getId(), col.getId(), vocab.getId()));

        SrsItem srsItem = srsItemRepository.save(new SrsItem(userA.getId(), vocab.getId(), LocalDate.now()));
        UUID attemptId = UUID.randomUUID();

        SrsReviewHistory history = new SrsReviewHistory(
            userA.getId(),
            srsItem.getId(),
            attemptId,
            4,
            1,
            2,
            new java.math.BigDecimal("2.50"),
            new java.math.BigDecimal("2.60"),
            5,
            Instant.now()
        );
        srsReviewHistoryRepository.save(history);

        LearningActivityEvent event = new LearningActivityEvent(
            userA.getId(),
            "FLASHCARD_REVIEW",
            attemptId,
            Instant.now(),
            "Asia/Ho_Chi_Minh",
            LocalDate.now(),
            5
        );
        activityEventRepository.save(event);

        mockMvc.perform(withAuthAndCsrf(delete("/api/v1/collections/" + col.getId()), userA))
            .andExpect(status().isNoContent());

        // Review history and activity event must remain 100% intact
        assertThat(srsReviewHistoryRepository.findByUserIdAndAttemptId(userA.getId(), attemptId)).isPresent();
        assertThat(activityEventRepository.findByUserIdAndEventTypeAndAttemptId(userA.getId(), "FLASHCARD_REVIEW", attemptId)).isPresent();
    }

    @Test
    @DisplayName("18. Soft-deleted words cannot be newly assigned to a collection")
    void testSoftDeletedWordsCannotBeNewlyAssigned() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Deck Soft Del", null));
        Vocabulary vocab = createVocabulary(userA, "obsolete", "adjective", "lỗi thời");
        vocab.setDeletedAt(Instant.now());
        vocabularyRepository.save(vocab);

        AssignVocabularyRequest req = new AssignVocabularyRequest(List.of(vocab.getId()));
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections/" + col.getId() + "/vocabularies"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("19. Collection word counts update accurately and exclude soft-deleted words")
    void testCountsUpdateCorrectly() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Deck Dynamic Counts", null));
        Vocabulary v1 = createVocabulary(userA, "word1", "noun", "nghĩa 1");
        Vocabulary v2 = createVocabulary(userA, "word2", "noun", "nghĩa 2");

        // 1. Initial count is 0
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + col.getId()), userA))
            .andExpect(jsonPath("$.wordCount").value(0));

        // 2. Add 2 words -> count becomes 2
        AssignVocabularyRequest req = new AssignVocabularyRequest(List.of(v1.getId(), v2.getId()));
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/collections/" + col.getId() + "/vocabularies"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.addedCount").value(2));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + col.getId()), userA))
            .andExpect(jsonPath("$.wordCount").value(2));

        // 3. Remove 1 word -> count becomes 1
        mockMvc.perform(withAuthAndCsrf(delete("/api/v1/collections/" + col.getId() + "/vocabularies/" + v1.getId()), userA))
            .andExpect(status().isNoContent());

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + col.getId()), userA))
            .andExpect(jsonPath("$.wordCount").value(1));

        // 4. Soft-delete the remaining word from vocabulary library -> collection count drops to 0
        v2.setDeletedAt(Instant.now());
        vocabularyRepository.save(v2);

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + col.getId()), userA))
            .andExpect(jsonPath("$.wordCount").value(0));

        // And collection vocabularies list returns 0 active elements
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections/" + col.getId() + "/vocabularies"), userA))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("20. Pagination and ordering remain correct")
    void testPaginationAndOrderingRemainCorrect() throws Exception {
        for (int i = 1; i <= 15; i++) {
            collectionRepository.save(new Collection(userA.getId(), String.format("Deck %02d", i), "Desc " + i));
        }

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections?page=0&size=10&sort=title,asc"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(15))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content.length()").value(10))
            .andExpect(jsonPath("$.content[0].title").value("Deck 01"));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/collections?page=1&size=10&sort=title,asc"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.content.length()").value(5))
            .andExpect(jsonPath("$.content[0].title").value("Deck 11"));
    }

    @Test
    @DisplayName("21. M2-T01 Regression: Case-insensitive collection title uniqueness under concurrent creation")
    void testConcurrentCaseInsensitiveCollectionCreation() throws Exception {
        int threads = 4;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threads);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger conflictCount = new java.util.concurrent.atomic.AtomicInteger(0);

        String[] titles = {"Advanced Vocabulary", "ADVANCED VOCABULARY", "advanced vocabulary", "Advanced Vocabulary"};

        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String title = titles[i];
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    CreateCollectionRequest req = new CreateCollectionRequest(title, "Test Desc");
                    org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(
                            withAuthAndCsrf(post("/api/v1/collections"), userA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                        )
                        .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (java.util.concurrent.Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threads - 1);

        // Verify database contains exactly 1 collection
        long count = collectionRepository.countByUserId(userA.getId());
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("22. M2-T01 Regression: Vocabulary Quick Add with collection assignment is atomic")
    void testAtomicQuickAddWithCollection() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Atomic Deck", null));

        // 1. Success case: word + collection assignment is atomic
        CreateVocabularyRequest validReq = new CreateVocabularyRequest(
            "serendipity",
            "noun",
            "/ˌser.ənˈdɪp.ə.ti/",
            "sự may mắn tình cờ",
            "Good luck in finding things by chance",
            "A fortunate stroke of serendipity.",
            "Một sự may mắn tình cờ.",
            null,
            "Book",
            "C1",
            col.getId()
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.term").value("serendipity"));

        // Verify membership created
        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(1);
        Vocabulary savedWord = vocabularyRepository.findByUserIdAndNormalizedTermAndDeletedAtIsNull(userA.getId(), "serendipity").get(0);
        assertThat(vocabularyCollectionRepository.existsByCollectionIdAndVocabularyId(col.getId(), savedWord.getId())).isTrue();

        // 2. Failure case: invalid collection ID rejects creation atomically
        UUID nonExistentColId = UUID.randomUUID();
        CreateVocabularyRequest invalidReq = new CreateVocabularyRequest(
            "ephemeral",
            "adjective",
            null,
            "phù du, chóng tàn",
            null,
            null,
            null,
            null,
            null,
            null,
            nonExistentColId
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/vocabulary"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidReq)))
            .andExpect(status().isNotFound());

        // Verify word was NOT created (no partial/orphan row)
        assertThat(vocabularyRepository.countByUserIdAndDeletedAtIsNull(userA.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("23. M2-T01 Regression: Concurrent duplicate membership insertion returns consistent result without duplicates or HTTP 500")
    void testConcurrentDuplicateMembershipAssignment() throws Exception {
        Collection col = collectionRepository.save(new Collection(userA.getId(), "Concurrent Deck", null));
        Vocabulary vocab = createVocabulary(userA, "ubiquitous", "adjective", "phổ biến");

        int threads = 6;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threads);
        java.util.concurrent.atomic.AtomicInteger count200 = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger count409 = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger count500 = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    AssignVocabularyRequest req = new AssignVocabularyRequest(List.of(vocab.getId()));
                    org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(
                            withAuthAndCsrf(post("/api/v1/collections/" + col.getId() + "/vocabularies"), userA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                        )
                        .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        count200.incrementAndGet();
                    } else if (status == 409) {
                        count409.incrementAndGet();
                    } else if (status >= 500) {
                        count500.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (java.util.concurrent.Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Must never return HTTP 500
        assertThat(count500.get()).isEqualTo(0);
        assertThat(count200.get() + count409.get()).isEqualTo(threads);

        // Database must have EXACTLY 1 association row
        assertThat(vocabularyCollectionRepository.existsByCollectionIdAndVocabularyId(col.getId(), vocab.getId())).isTrue();
        assertThat(vocabularyCollectionRepository.countActiveWordsByCollectionId(col.getId())).isEqualTo(1);
    }
}

