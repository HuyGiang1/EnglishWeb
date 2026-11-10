package com.englishflow.review;

import com.englishflow.user.*;
import com.englishflow.vocabulary.*;
import com.englishflow.vocabulary.dto.*;
import com.englishflow.review.dto.ReviewSubmissionRequest;
import com.englishflow.review.scheduler.ReviewRating;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StudySetLearningIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired UserRepository users;
    @Autowired VocabularyRepository words;
    @Autowired CollectionService collections;
    @Autowired VocabularyCollectionRepository membership;
    @Autowired ReviewService reviews;
    @Autowired SrsItemRepository schedules;
    @Autowired SrsReviewHistoryRepository history;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    User a,b; UUID set; MockHttpSession session;
    @BeforeEach void setup() throws Exception {
        try(var connection=dataSource.getConnection()) { assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl()); }
        users.deleteAll();
        a=users.save(new User("study-a@example.test","fixture-not-login-hash","A","Pacific/Kiritimati",20));
        b=users.save(new User("study-b@example.test","fixture-not-login-hash","B","UTC",20));
        set=collections.createCollection(a.getId(),new CreateCollectionRequest("Study",null,null)).id();
        session=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(a.getEmail(),null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
    }
    @AfterEach void cleanup() {users.deleteAll();}
    LocalDate today() {return LocalDate.now(clock.withZone(ZoneId.of(a.getTimezone())));}
    Vocabulary word(User owner,String term,UUID target) {
        Vocabulary w=words.save(new Vocabulary(owner.getId(),term,"noun",null,"nghĩa",null,null,null,null,null,null));
        if(target!=null) membership.save(new VocabularyCollection(owner.getId(),target,w.getId(),Instant.parse("2026-01-01T00:00:00Z")));
        return w;
    }
    SrsItem scheduled(String term,LocalDate due,String state) {
        Vocabulary w=word(a,term,set);reviews.provisionCardForVocabulary(a.getId(),w.getId(),due);
        var s=schedules.findByUserIdAndVocabularyId(a.getId(),w.getId()).orElseThrow();
        s.setDueDate(due);s.setState(state);return schedules.save(s);
    }
    @Test void scopedCountIncludesMissingSchedulesButDoesNotProvision() throws Exception {
        word(a,"missing",set);word(a,"outside",null);
        mvc.perform(get("/api/v1/review/due-count").session(session).param("collectionId",set.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalDue").value(1));
        assertThat(schedules.count()).isZero();assertThat(history.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_activity_events",Integer.class)).isZero();
    }
    @Test void countsDueTodayAndOverdueButNotFutureSuspendedOrDeleted() {
        scheduled("today",today(),"NEW");scheduled("overdue",today().minusDays(1),"REVIEW");
        scheduled("future",today().plusDays(1),"REVIEW");scheduled("suspended",today(),"SUSPENDED");
        var deleted=scheduled("deleted",today(),"NEW");
        jdbc.update("UPDATE vocabularies SET deleted_at=NOW() WHERE id=?",deleted.getVocabularyId());
        assertThat(reviews.getDueCount(a.getId(),set).totalDue()).isEqualTo(2);
        assertThat(reviews.getDueQueue(a.getId(),set,1).totalDue()).isEqualTo(2);
    }
    @Test void globalCountRemainsUnscopedAndIncludesOtherWords() throws Exception {
        word(a,"inside",set);word(a,"outside",null);word(b,"foreign",null);
        mvc.perform(get("/api/v1/review/due-count").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.totalDue").value(2));
        assertThat(schedules.count()).isZero();
    }
    @Test void emptySetHasZeroVocabularyAndZeroDue() throws Exception {
        mvc.perform(get("/api/v1/review/due-count").session(session).param("collectionId",set.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalDue").value(0));
        mvc.perform(get("/api/v1/collections/"+set+"/vocabularies").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void foreignMissingAndDeletedSetsAreRejectedBeforeCounting() throws Exception {
        UUID foreign=collections.createCollection(b.getId(),new CreateCollectionRequest("Private",null,null)).id();
        for(UUID id:List.of(foreign,UUID.randomUUID()))
            mvc.perform(get("/api/v1/review/due-count").session(session).param("collectionId",id.toString())).andExpect(status().isNotFound());
        collections.deleteCollection(a.getId(),set);
        mvc.perform(get("/api/v1/review/due-count").session(session).param("collectionId",set.toString())).andExpect(status().isNotFound());
        assertThat(schedules.count()).isZero();
    }
    @Test void invalidScopeDoesNotFallBackToGlobal() throws Exception {
        for(String id:List.of("","bad","1-1-1-1-1"))
            mvc.perform(get("/api/v1/review/due-count").session(session).param("collectionId",id)).andExpect(status().isBadRequest());
    }
    @Test void anonymousCannotReadCountsOrCollectionWords() throws Exception {
        mvc.perform(get("/api/v1/review/due-count").param("collectionId",set.toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/collections/"+set+"/vocabularies")).andExpect(status().isUnauthorized());
    }
    @Test void crossUserVocabularyCannotLeakThroughScopedRetrieval() throws Exception {
        Vocabulary mine=word(a,"mine",set);word(b,"private",null);
        mvc.perform(get("/api/v1/collections/"+set+"/vocabularies").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(mine.getId().toString()));
        UUID foreign=collections.createCollection(b.getId(),new CreateCollectionRequest("Private",null,null)).id();
        mvc.perform(get("/api/v1/collections/"+foreign+"/vocabularies").session(session)).andExpect(status().isNotFound());
    }
    @Test void tiedMembershipDatesHaveStablePaginationAndNoDuplicateIdentity() throws Exception {
        var ids=new ArrayList<UUID>();for(int i=0;i<5;i++)ids.add(word(a,"word"+i,set).getId());
        ids.sort(Comparator.comparing(UUID::toString));
        for(int pass=0;pass<2;pass++) for(int page=0;page<3;page++) {
            var response=collections.getVocabulariesInCollection(a.getId(),set,org.springframework.data.domain.PageRequest.of(page,2));
            assertThat(response.content().stream().map(VocabularyResponse::id)).containsExactlyElementsOf(ids.subList(page*2,Math.min(page*2+2,5)));
        }
        assertThat(schedules.count()).isZero();
    }
    @Test void collectionPageBoundsAreEnforced() throws Exception {
        word(a,"word",set);
        mvc.perform(get("/api/v1/collections/"+set+"/vocabularies").session(session).param("size","100000").param("page","-1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.page").value(0));
    }
    @Test void browsingAndCountPreserveScheduleHistoryAndEvents() throws Exception {
        var s=scheduled("word",today(),"NEW");
        reviews.submitGrade(a.getId(),new ReviewSubmissionRequest(s.getId(),UUID.randomUUID(),ReviewRating.GOOD,1));
        var before=jdbc.queryForMap("SELECT * FROM srs_items WHERE id=?",s.getId());
        var oldHistory=jdbc.queryForList("SELECT * FROM srs_review_history");var events=jdbc.queryForList("SELECT * FROM learning_activity_events");
        word(a,"unprovisioned",set);
        for(int i=0;i<3;i++) {
            mvc.perform(get("/api/v1/collections/"+set+"/vocabularies").session(session)).andExpect(status().isOk());
            mvc.perform(get("/api/v1/review/due-count").session(session).param("collectionId",set.toString())).andExpect(status().isOk());
        }
        assertThat(schedules.count()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM srs_items WHERE id=?",s.getId())).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM srs_review_history")).isEqualTo(oldHistory);
        assertThat(jdbc.queryForList("SELECT * FROM learning_activity_events")).isEqualTo(events);
    }
    @Test void sharedVocabularyKeepsOneScheduleAndGradeChangesBothScopes() {
        var s=scheduled("shared",today(),"NEW");
        UUID other=collections.createCollection(a.getId(),new CreateCollectionRequest("Other",null,null)).id();
        membership.save(new VocabularyCollection(a.getId(),other,s.getVocabularyId()));
        assertThat(reviews.getDueCount(a.getId(),set).totalDue()).isEqualTo(1);
        assertThat(reviews.getDueCount(a.getId(),other).totalDue()).isEqualTo(1);
        reviews.submitGrade(a.getId(),new ReviewSubmissionRequest(s.getId(),UUID.randomUUID(),ReviewRating.GOOD,1));
        assertThat(reviews.getDueCount(a.getId(),set).totalDue()).isZero();
        assertThat(reviews.getDueCount(a.getId(),other).totalDue()).isZero();
        assertThat(schedules.count()).isEqualTo(1);assertThat(history.count()).isEqualTo(1);
    }
}
