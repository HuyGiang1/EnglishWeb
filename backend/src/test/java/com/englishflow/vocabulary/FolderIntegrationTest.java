package com.englishflow.vocabulary;

import com.englishflow.user.*;
import com.englishflow.review.*;
import com.englishflow.review.dto.ReviewSubmissionRequest;
import com.englishflow.review.scheduler.ReviewRating;
import com.englishflow.vocabulary.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FolderIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired FolderRepository folders;
    @Autowired CollectionRepository collections;
    @Autowired VocabularyRepository vocabularies;
    @Autowired VocabularyCollectionRepository memberships;
    @Autowired FolderService service;
    @Autowired CollectionService collectionService;
    @Autowired ReviewService reviews;
    @Autowired SrsItemRepository srs;
    @Autowired SrsReviewHistoryRepository history;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    private User a, b;
    private MockHttpSession session;
    private static final String CSRF = "folder-fixture-csrf";

    @BeforeEach void setup() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl());
        }
        users.deleteAll();
        a = users.save(new User("folder-a@example.test", "fixture-not-login-hash", "A", "UTC", 20));
        b = users.save(new User("folder-b@example.test", "fixture-not-login-hash", "B", "UTC", 20));
        session = session(a);
    }
    @AfterEach void cleanup() { users.deleteAll(); }
    private MockHttpSession session(User user) {
        MockHttpSession result = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        result.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return result;
    }
    private ResultActions mutate(MockHttpServletRequestBuilder builder, String json) throws Exception {
        return mvc.perform(builder.session(session).cookie(new Cookie("XSRF-TOKEN", CSRF))
            .header("X-XSRF-TOKEN", CSRF).contentType(MediaType.APPLICATION_JSON).content(json));
    }
    private FolderResponse folder(User owner, String name) { return service.create(owner.getId(), new FolderRequest(name, null)); }
    private CollectionResponse set(User owner, String name, UUID folder) {
        return collectionService.createCollection(owner.getId(), new CreateCollectionRequest(name, null, folder));
    }
    private Vocabulary word() {
        return vocabularies.save(new Vocabulary(a.getId(), "resilience", "noun", null, "kiên cường",
            null, null, null, null, null, "B2"));
    }

    @Test void createNormalizesWhitespaceAndReturnsBoundedMetadata() throws Exception {
        mutate(post("/api/v1/folders"), "{\"name\":\"  My\\t\\n Words  \",\"description\":\"Original description\"}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("My Words"))
            .andExpect(jsonPath("$.studySetCount").value(0)).andExpect(jsonPath("$.description").value("Original description"));
    }

    @Test void duplicateNamesAreOwnerScopedAndAsciiCaseInsensitive() throws Exception {
        folder(a, "My Words");
        mutate(post("/api/v1/folders"), "{\"name\":\" my   words \"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FOLDER_NAME_EXISTS"));
        assertThat(folder(b, "MY WORDS").id()).isNotNull();
        assertThat(folder(a, "Bộ từ").id()).isNotEqualTo(folder(a, "bộ TỪ").id());
    }

    @Test void namesAndDescriptionsAreValidated() throws Exception {
        for (String name : List.of("", " \t\n ", "a".repeat(101))) {
            mutate(post("/api/v1/folders"), mapper.writeValueAsString(Map.of("name", name)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mutate(post("/api/v1/folders"), mapper.writeValueAsString(Map.of("name", "Valid", "description", "x".repeat(1001))))
            .andExpect(status().isBadRequest());
    }

    @Test void listingIsOwnerScopedOrderedBoundedAndCountsStudySets() throws Exception {
        FolderResponse z = folder(a, "Z"), first = folder(a, "A");
        folder(b, "Private");
        set(a, "one", first.id()); set(a, "two", first.id()); set(a, "three", z.id());
        mvc.perform(get("/api/v1/folders").session(session).param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].name").value("A"))
            .andExpect(jsonPath("$.content[0].studySetCount").value(2));
        mvc.perform(get("/api/v1/folders").session(session).param("size", "1000"))
            .andExpect(jsonPath("$.size").value(100));
    }

    @Test void ownerCanReadRenameAndKeepCount() throws Exception {
        FolderResponse f = folder(a, "Before"); set(a, "set", f.id());
        mutate(put("/api/v1/folders/" + f.id()), "{\"name\":\" After \",\"description\":\"new\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("After"))
            .andExpect(jsonPath("$.studySetCount").value(1));
        mvc.perform(get("/api/v1/folders/" + f.id()).session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("new"));
    }

    @Test void renameConflictDoesNotAlterTheFolder() throws Exception {
        FolderResponse f = folder(a, "One"); folder(a, "Two");
        mutate(put("/api/v1/folders/" + f.id()), "{\"name\":\"TWO\"}")
            .andExpect(status().isConflict());
        assertThat(service.get(a.getId(), f.id()).name()).isEqualTo("One");
    }

    @Test void foreignReadsUpdatesAndDeletesReturnSameNotFoundAsMissing() throws Exception {
        FolderResponse f = folder(b, "Private Name");
        for (UUID id : List.of(f.id(), UUID.randomUUID())) {
            mvc.perform(get("/api/v1/folders/" + id).session(session)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Folder not found."));
            mutate(put("/api/v1/folders/" + id), "{\"name\":\"Hacked\"}").andExpect(status().isNotFound());
            mutate(delete("/api/v1/folders/" + id), "").andExpect(status().isNotFound());
        }
        assertThat(service.get(b.getId(), f.id()).name()).isEqualTo("Private Name");
    }

    @Test void anonymousAccessIsRejected() throws Exception {
        mvc.perform(get("/api/v1/folders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/folders/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test void allNewMutationRoutesRequireCsrf() throws Exception {
        FolderResponse f = folder(a, "CSRF"); CollectionResponse c = set(a, "set", null);
        for (MockHttpServletRequestBuilder request : List.of(post("/api/v1/folders"),
            put("/api/v1/folders/" + f.id()), delete("/api/v1/folders/" + f.id()),
            put("/api/v1/collections/" + c.id() + "/folder"))) {
            mvc.perform(request.session(session).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"folderId\":null}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test void invalidIdentifiersUseSafe400Envelope() throws Exception {
        mvc.perform(get("/api/v1/folders/not-a-uuid").session(session))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        CollectionResponse c = set(a, "set", null);
        mutate(put("/api/v1/collections/" + c.id() + "/folder"), "{\"folderId\":\"bad\"}")
            .andExpect(status().isBadRequest());
        mutate(post("/api/v1/collections"), "{\"title\":\"invalid destination\",\"folderId\":\"\"}")
            .andExpect(status().isBadRequest());
    }

    @Test void moveAssignsMovesAndExplicitlyUnassignsWithoutChangingId() throws Exception {
        FolderResponse one = folder(a, "One"), two = folder(a, "Two");
        CollectionResponse c = set(a, "set", null);
        for (UUID destination : List.of(one.id(), two.id())) {
            mutate(put("/api/v1/collections/" + c.id() + "/folder"), mapper.writeValueAsString(Map.of("folderId", destination)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(c.id().toString()))
                .andExpect(jsonPath("$.folderId").value(destination.toString()));
        }
        mutate(put("/api/v1/collections/" + c.id() + "/folder"), "{\"folderId\":null}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.folderId").doesNotExist());
        assertThat(collections.findById(c.id()).orElseThrow().getFolderId()).isNull();
    }

    @Test void moveRejectsMissingPropertyInsteadOfSilentlyUnassigning() throws Exception {
        FolderResponse f = folder(a, "Folder"); CollectionResponse c = set(a, "set", f.id());
        for (String invalid : List.of("{}", "{\"folderId\":\"\"}", "{\"folderId\":\" \"}", "{\"folderId\":[]}", "{\"folderId\":false}")) {
            mutate(put("/api/v1/collections/" + c.id() + "/folder"), invalid).andExpect(status().isBadRequest());
        }
        assertThat(collections.findById(c.id()).orElseThrow().getFolderId()).isEqualTo(f.id());
    }

    @Test void createSupportsFolderAndLegacyUpdatePreservesAssignment() throws Exception {
        FolderResponse f = folder(a, "Folder");
        String body = mutate(post("/api/v1/collections"),
            mapper.writeValueAsString(Map.of("title", "new", "folderId", f.id())))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.folderId").value(f.id().toString()))
            .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(body).get("id").asText());
        mutate(put("/api/v1/collections/" + id), "{\"title\":\"renamed\",\"description\":\"legacy\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.folderId").value(f.id().toString()));
        mutate(post("/api/v1/collections"), "{\"title\":\"legacy\"}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.folderId").doesNotExist());
    }

    @Test void foreignAssignmentAndMissingDestinationNeverChangeTheCollection() throws Exception {
        FolderResponse own = folder(a, "Own"), foreign = folder(b, "Other");
        CollectionResponse c = set(a, "set", own.id()), foreignSet = set(b, "secret set", null);
        for (UUID dest : List.of(foreign.id(), UUID.randomUUID())) {
            mutate(put("/api/v1/collections/" + c.id() + "/folder"),
                mapper.writeValueAsString(Map.of("folderId", dest))).andExpect(status().isNotFound());
        }
        mutate(put("/api/v1/collections/" + foreignSet.id() + "/folder"), "{\"folderId\":null}")
            .andExpect(status().isNotFound());
        mutate(post("/api/v1/collections"), mapper.writeValueAsString(Map.of("title", "bad create", "folderId", foreign.id())))
            .andExpect(status().isNotFound());
        assertThat(collections.findById(c.id()).orElseThrow().getFolderId()).isEqualTo(own.id());
    }

    @Test void folderAndUnfiledFiltersKeepLegacyGlobalListing() throws Exception {
        FolderResponse f = folder(a, "Folder"); set(a, "filed", f.id()); set(a, "legacy", null);
        mvc.perform(get("/api/v1/collections").session(session)).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/collections").session(session).param("folderId", f.id().toString()))
            .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].title").value("filed"));
        mvc.perform(get("/api/v1/collections").session(session).param("unfiled", "true"))
            .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].title").value("legacy"));
        mvc.perform(get("/api/v1/collections").session(session).param("folderId", f.id().toString()).param("unfiled", "true"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/collections").session(session).param("folderId", ""))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/collections").session(session).param("folderId", folder(b, "Secret").id().toString()))
            .andExpect(status().isNotFound());
    }

    @Test void databaseRejectsCrossOwnerAssignmentAndNonCanonicalOrDuplicateName() {
        FolderResponse f = folder(b, "Other"); CollectionResponse c = set(a, "set", null);
        assertThatThrownBy(() -> jdbc.update("UPDATE collections SET folder_id=? WHERE id=?", f.id(), c.id()))
            .isInstanceOf(DataIntegrityViolationException.class);
        folder(a, "Unique");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO folders(user_id,name) VALUES(?,?)", a.getId(), "UNIQUE"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO folders(user_id,name) VALUES(?,?)", a.getId(), "  invalid  "))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void deletionUnassignsAndPreservesMembershipScheduleHistoryAndReview() throws Exception {
        FolderResponse f = folder(a, "Folder"); CollectionResponse c = set(a, "set", f.id());
        Vocabulary word = word(); memberships.save(new VocabularyCollection(a.getId(), c.id(), word.getId()));
        reviews.getDueQueue(a.getId(), c.id(), 50);
        SrsItem card = srs.findByUserIdAndVocabularyId(a.getId(), word.getId()).orElseThrow();
        reviews.submitGrade(a.getId(), new ReviewSubmissionRequest(card.getId(), UUID.randomUUID(), ReviewRating.GOOD, 1));
        SrsItem before = srs.findById(card.getId()).orElseThrow();
        mutate(delete("/api/v1/folders/" + f.id()), "").andExpect(status().isNoContent());
        assertThat(collections.findById(c.id()).orElseThrow().getFolderId()).isNull();
        assertThat(memberships.existsByCollectionIdAndVocabularyId(c.id(), word.getId())).isTrue();
        assertThat(vocabularies.findById(word.getId())).isPresent();
        assertThat(srs.findById(card.getId()).orElseThrow().getDueDate()).isEqualTo(before.getDueDate());
        assertThat(history.countByUserId(a.getId())).isEqualTo(1);
        assertThat(reviews.getDueQueue(a.getId(), c.id(), 50).totalDue()).isZero();
        assertThat(reviews.getDueQueue(a.getId(), 50).totalDue()).isZero();
        mutate(delete("/api/v1/folders/" + f.id()), "").andExpect(status().isNotFound());
    }

    @Test void failedDeleteRollsBackUnassignment() {
        FolderResponse f = folder(a, "Rollback"); CollectionResponse c = set(a, "set", f.id());
        jdbc.execute("CREATE FUNCTION folder_test_block() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture blocked delete'; END $$");
        jdbc.execute("CREATE TRIGGER folder_test_block BEFORE DELETE ON folders FOR EACH ROW EXECUTE FUNCTION folder_test_block()");
        try {
            assertThatThrownBy(() -> service.delete(a.getId(), f.id())).isInstanceOf(RuntimeException.class);
            assertThat(collections.findById(c.id()).orElseThrow().getFolderId()).isEqualTo(f.id());
            assertThat(folders.findById(f.id())).isPresent();
        } finally {
            jdbc.execute("DROP TRIGGER folder_test_block ON folders"); jdbc.execute("DROP FUNCTION folder_test_block()");
        }
    }

    @Test void concurrentDuplicateCreationHasOneWinnerAndOneConflict() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<String> create = () -> {
                start.await();
                try { folder(a, "Concurrent"); return "created"; }
                catch (com.englishflow.vocabulary.exception.FolderNameConflictException expected) { return "conflict"; }
            };
            Future<String> one = pool.submit(create), two = pool.submit(create); start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("created", "conflict");
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentMoveAndDeleteLeaveNoDanglingAssignment() throws Exception {
        FolderResponse f = folder(a, "Race"); CollectionResponse c = set(a, "set", null);
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> move = pool.submit(() -> { try { start.await(); service.move(a.getId(), c.id(), f.id()); }
                catch (com.englishflow.vocabulary.exception.FolderNotFoundException expected) { }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); } });
            Future<?> remove = pool.submit(() -> { try { start.await(); service.delete(a.getId(), f.id()); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); } });
            start.countDown(); move.get(20, TimeUnit.SECONDS); remove.get(20, TimeUnit.SECONDS);
            assertThat(folders.findById(f.id())).isEmpty();
            assertThat(collections.findById(c.id()).orElseThrow().getFolderId()).isNull();
        } finally { pool.shutdownNow(); }
    }

    @Test void v11UpgradePreservesLegacyRowsAndAddsNullableAssignment() {
        Flyway old = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("folder_upgrade_fixture").defaultSchema("folder_upgrade_fixture").target("11").load();
        old.migrate();
        UUID user = UUID.randomUUID(), set = UUID.randomUUID(), word = UUID.randomUUID(), item = UUID.randomUUID(), attempt = UUID.randomUUID();
        jdbc.update("INSERT INTO folder_upgrade_fixture.app_users(id,email,password_hash,display_name) VALUES(?,?,?,?)",
            user, "upgrade@example.test", "fixture", "Fixture");
        jdbc.update("INSERT INTO folder_upgrade_fixture.collections(id,user_id,title) VALUES(?,?,?)", set, user, "Legacy");
        jdbc.update("INSERT INTO folder_upgrade_fixture.vocabularies(id,user_id,term,normalized_term,part_of_speech,vietnamese_meaning) VALUES(?,?,?,?,?,?)",
            word, user, "word", "word", "noun", "từ");
        jdbc.update("INSERT INTO folder_upgrade_fixture.vocabulary_collections(user_id,collection_id,vocabulary_id) VALUES(?,?,?)", user, set, word);
        jdbc.update("INSERT INTO folder_upgrade_fixture.srs_items(id,user_id,vocabulary_id) VALUES(?,?,?)", item, user, word);
        jdbc.update("INSERT INTO folder_upgrade_fixture.srs_review_history(user_id,srs_item_id,attempt_id,grade,previous_interval_days,new_interval_days,previous_ease_factor,new_ease_factor) VALUES(?,?,?,3,0,1,2.5,2.5)",
            user, item, attempt);
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("folder_upgrade_fixture").defaultSchema("folder_upgrade_fixture").target("12").load().migrate();
        assertThat(jdbc.queryForObject("SELECT folder_id FROM folder_upgrade_fixture.collections WHERE id=?", UUID.class, set)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM folder_upgrade_fixture.vocabulary_collections", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM folder_upgrade_fixture.srs_items", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempt_id FROM folder_upgrade_fixture.srs_review_history WHERE user_id=?", UUID.class, user)).isEqualTo(attempt);
    }
}
