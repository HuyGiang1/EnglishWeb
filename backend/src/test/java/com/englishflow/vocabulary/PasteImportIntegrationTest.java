package com.englishflow.vocabulary;

import com.englishflow.user.*;
import com.englishflow.review.*;
import com.englishflow.review.dto.ReviewSubmissionRequest;
import com.englishflow.review.scheduler.ReviewRating;
import com.englishflow.vocabulary.dto.*;
import com.englishflow.vocabulary.exception.BulkImportException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PasteImportIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired VocabularyRepository words;
    @Autowired CollectionRepository sets;
    @Autowired VocabularyCollectionRepository memberships;
    @Autowired PasteImportService service;
    @Autowired CollectionService collectionService;
    @Autowired FolderService folders;
    @Autowired ReviewService reviews;
    @Autowired SrsItemRepository srs;
    @Autowired SrsReviewHistoryRepository history;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    User a,b; UUID set; MockHttpSession session;
    static final String ROOT="/api/v1/vocabulary/bulk-import/paste/";
    static final String CSRF="paste-fixture-csrf";
    @BeforeEach void setup() throws Exception {
        try (var connection=dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl());
        }
        users.deleteAll();
        a=users.save(new User("paste-a@example.test","fixture-not-login-hash","A","UTC",20));
        b=users.save(new User("paste-b@example.test","fixture-not-login-hash","B","UTC",20));
        set=collectionService.createCollection(a.getId(),new CreateCollectionRequest("Destination",null,null)).id();
        session=new MockHttpSession();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(a.getEmail(),null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
    }
    @AfterEach void cleanup() { users.deleteAll(); }
    PasteImportRow row(int number,String term,String meaning) { return new PasteImportRow(number,term,meaning,"","noun",false); }
    PasteBatchRequest batch(PasteImportRow... rows) { return new PasteBatchRequest(List.of(rows),set.toString(),"PRESERVE_EXISTING"); }
    Vocabulary word(User user,String term,String meaning) {
        return words.save(new Vocabulary(user.getId(),term,"noun","/old/",meaning,null,null,null,null,null,null));
    }
    ResultActions postJson(String endpoint,Object request) throws Exception {
        return mvc.perform(post(ROOT+endpoint).session(session).cookie(new Cookie("XSRF-TOKEN",CSRF))
            .header("X-XSRF-TOKEN",CSRF).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request)));
    }
    @Test void previewAndValidateWriteNothing() throws Exception {
        postJson("preview",new PasteImportRequest("analyze : phân tích","COLON",null,false,set.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].status").value("NEW"))
            .andExpect(jsonPath("$.rows[0].partOfSpeech").value("other"));
        postJson("validate",batch(row(1,"word","nghĩa"))).andExpect(status().isOk());
        assertThat(words.count()).isZero(); assertThat(srs.count()).isZero(); assertThat(history.count()).isZero(); assertThat(memberships.count()).isZero();
    }
    @Test void createsOneWordOneScheduleAndMembership() {
        var result=service.confirm(a.getId(),batch(row(1," Word ","nghĩa")));
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(words.findAll().getFirst().getNormalizedTerm()).isEqualTo("word");
        assertThat(srs.count()).isEqualTo(1); assertThat(history.count()).isZero(); assertThat(memberships.count()).isEqualTo(1);
        assertThat(reviews.getDueQueue(a.getId(),set,50).totalDue()).isEqualTo(1);
    }
    @Test void existingLinkPreservesMetadataScheduleAndReviewHistory() {
        Vocabulary w=word(a,"Word","original");
        reviews.provisionCardForVocabulary(a.getId(),w.getId(),null);
        var card=srs.findByUserIdAndVocabularyId(a.getId(),w.getId()).orElseThrow();
        reviews.submitGrade(a.getId(),new ReviewSubmissionRequest(card.getId(),UUID.randomUUID(),ReviewRating.GOOD,2));
        var before=srs.findById(card.getId()).orElseThrow();
        var request=batch(row(1,"WORD","different"));
        var preview=service.validate(a.getId(),request).rows().getFirst();
        assertThat(preview.status()).isEqualTo("EXISTING"); assertThat(preview.issues()).contains("PRESERVE_EXISTING");
        assertThat(service.confirm(a.getId(),request).linkedCount()).isEqualTo(1);
        assertThat(words.findById(w.getId()).orElseThrow().getVietnameseMeaning()).isEqualTo("original");
        assertThat(words.findById(w.getId()).orElseThrow().getPhoneticIpa()).isEqualTo("/old/");
        assertThat(srs.count()).isEqualTo(1); assertThat(history.countByUserId(a.getId())).isEqualTo(1);
        assertThat(srs.findById(card.getId()).orElseThrow().getDueDate()).isEqualTo(before.getDueDate());
        assertThat(service.confirm(a.getId(),request).alreadyPresentCount()).isEqualTo(1);
        assertThat(memberships.count()).isEqualTo(1);
    }
    @Test void duplicateInputFirstIncludedDefinitionWinsAndCountsAreDistinct() {
        var excluded=new PasteImportRow(3,"ignored","","","noun",true);
        var result=service.confirm(a.getId(),batch(row(1,"word","first"),row(2,"WORD","second"),excluded));
        assertThat(result.createdCount()).isEqualTo(1); assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(result.excludedCount()).isEqualTo(1); assertThat(result.rejectedCount()).isZero();
        assertThat(words.findAll().getFirst().getVietnameseMeaning()).isEqualTo("first");
    }
    @Test void exclusionDoesNotConsumeCanonicalIdentity() {
        var excluded=new PasteImportRow(1,"word","first","","noun",true);
        assertThat(service.confirm(a.getId(),batch(excluded,row(2,"WORD","second"))).createdCount()).isEqualTo(1);
        assertThat(words.findAll().getFirst().getVietnameseMeaning()).isEqualTo("second");
    }
    @Test void termAndPosIdentityNotMeaningAndCaseInsensitive() {
        word(a,"word","old");
        var verb=new PasteImportRow(2,"WORD","new","","verb",false);
        var r=service.confirm(a.getId(),batch(row(1," WoRd ","changed"),verb));
        assertThat(r.linkedCount()).isEqualTo(1); assertThat(r.createdCount()).isEqualTo(1);
    }
    @Test void requiredMeaningAndInvalidPosBlockWholeBatch() throws Exception {
        for(PasteImportRow bad:List.of(row(2,"incomplete",""),new PasteImportRow(2,"bad","nghĩa","","pronoun",false),
            row(2,"x".repeat(101),"nghĩa"),new PasteImportRow(2,"bad","nghĩa","x".repeat(101),"noun",false))) {
            postJson("confirm",batch(row(1,"valid","hợp lệ"),bad)).andExpect(status().isBadRequest());
            assertThat(words.count()).isZero(); assertThat(srs.count()).isZero(); assertThat(memberships.count()).isZero();
        }
    }
    @Test void ambiguousPreexistingIdentityCannotBeMerged() throws Exception {
        word(a,"word","first"); word(a,"WORD","second");
        postJson("validate",batch(row(1,"word","third"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].status").value("AMBIGUOUS_EXISTING"));
        postJson("confirm",batch(row(1,"new","new"),row(2,"word","third"))).andExpect(status().isBadRequest());
        assertThat(words.count()).isEqualTo(2); assertThat(memberships.count()).isZero();
    }
    @Test void foreignAndMissingDestinationsReturnSameNotFound() throws Exception {
        UUID foreign=collectionService.createCollection(b.getId(),new CreateCollectionRequest("Private",null,null)).id();
        for(UUID id:List.of(foreign,UUID.randomUUID()))
            postJson("confirm",new PasteBatchRequest(List.of(row(1,"word","meaning")),id.toString(),"PRESERVE_EXISTING"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Collection not found."));
        assertThat(words.count()).isZero();
    }
    @Test void foreignVocabularyIsNeitherEnumeratedNorLinked() {
        Vocabulary foreign=word(b,"private","bí mật");
        assertThat(service.validate(a.getId(),batch(row(1,"private","mine"))).rows().getFirst().existingMeaning()).isNull();
        assertThat(service.confirm(a.getId(),batch(row(1,"private","mine"))).createdCount()).isEqualTo(1);
        assertThat(memberships.existsByCollectionIdAndVocabularyId(set,foreign.getId())).isFalse();
    }
    @Test void invalidUuidAndConflictPolicyFailBeforeWrites() throws Exception {
        for(String id:List.of("","not-uuid","1-1-1-1-1"))
            postJson("confirm",new PasteBatchRequest(List.of(row(1,"word","nghĩa")),id,"PRESERVE_EXISTING")).andExpect(status().isBadRequest());
        postJson("confirm",new PasteBatchRequest(List.of(row(1,"word","nghĩa")),null,"OVERWRITE")).andExpect(status().isBadRequest());
        assertThat(words.count()).isZero();
    }
    @Test void authenticationAndCsrfRemainRequired() throws Exception {
        mvc.perform(post(ROOT+"confirm").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(ROOT+"confirm").session(session).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(ROOT+"preview").cookie(new Cookie("XSRF-TOKEN",CSRF)).header("X-XSRF-TOKEN",CSRF)
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }
    @Test void rowsNullElementsDuplicateNumbersAndCountsAreBounded() throws Exception {
        postJson("validate",new PasteBatchRequest(Collections.nCopies(1001,row(1,"a","b")),null,null)).andExpect(status().isBadRequest());
        postJson("validate",new PasteBatchRequest(Arrays.asList((PasteImportRow)null),null,null)).andExpect(status().isBadRequest());
        postJson("validate",batch(row(1,"a","b"),row(1,"c","d"))).andExpect(status().isBadRequest());
        postJson("confirm",batch(new PasteImportRow(1,"a","","","noun",true))).andExpect(status().isBadRequest());
    }
    @Test void requestAndRawTextLimitsAreEnforced() throws Exception {
        postJson("preview",new PasteImportRequest("x".repeat(PasteImportParser.MAX_TEXT_BYTES+1),"NONE",null,false,null))
            .andExpect(status().isBadRequest());
        mvc.perform(post(ROOT+"preview").session(session).cookie(new Cookie("XSRF-TOKEN",CSRF)).header("X-XSRF-TOKEN",CSRF)
            .contentType(MediaType.APPLICATION_JSON).content("x".repeat(PasteImportParser.MAX_REQUEST_BYTES+1)))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }
    @Test void databaseFailureRollsBackWordsMembershipAndSchedules() {
        jdbc.execute("CREATE FUNCTION paste_test_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.term = 'fail-after-first' THEN RAISE EXCEPTION 'fixture failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER paste_test_trigger BEFORE INSERT ON vocabularies FOR EACH ROW EXECUTE FUNCTION paste_test_fail()");
        try {
            assertThatThrownBy(()->service.confirm(a.getId(),batch(row(1,"valid","good"),row(2,"fail-after-first","bad")))).isInstanceOf(RuntimeException.class);
            assertThat(words.count()).isZero(); assertThat(srs.count()).isZero(); assertThat(memberships.count()).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER paste_test_trigger ON vocabularies"); jdbc.execute("DROP FUNCTION paste_test_fail()");
        }
    }
    @Test void retryRevalidatesWithoutClaimingExactResponseReplay() {
        var request=batch(row(1,"word","nghĩa"));
        assertThat(service.confirm(a.getId(),request).createdCount()).isEqualTo(1);
        assertThat(service.confirm(a.getId(),request).alreadyPresentCount()).isEqualTo(1);
        assertThat(words.count()).isEqualTo(1); assertThat(srs.count()).isEqualTo(1); assertThat(memberships.count()).isEqualTo(1);
    }
    @Test void concurrentIdenticalImportsSerializeOnExistingUserLock() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var jobs=new ArrayList<Future<PasteCommitResponse>>();
            for(int i=0;i<2;i++) jobs.add(pool.submit(()->{start.await(); return service.confirm(a.getId(),batch(row(1,"word","nghĩa")));}));
            start.countDown();
            assertThat(jobs.get(0).get(15,TimeUnit.SECONDS).createdCount()+jobs.get(1).get(15,TimeUnit.SECONDS).createdCount()).isEqualTo(1);
            assertThat(words.count()).isEqualTo(1); assertThat(srs.count()).isEqualTo(1); assertThat(memberships.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void libraryDestinationCreatesWithoutMembershipAndRetrySkips() {
        var req=new PasteBatchRequest(List.of(row(1,"word","nghĩa")),null,"PRESERVE_EXISTING");
        assertThat(service.confirm(a.getId(),req).createdCount()).isEqualTo(1);
        assertThat(service.confirm(a.getId(),req).alreadyPresentCount()).isEqualTo(1);
        assertThat(memberships.count()).isZero(); assertThat(srs.count()).isEqualTo(1);
    }
    @Test void folderAssociatedStudySetImportsIntoExactDestination() {
        var folder=folders.create(a.getId(),new FolderRequest("Folder",null));
        UUID target=collectionService.createCollection(a.getId(),new CreateCollectionRequest("In folder",null,folder.id())).id();
        var r=service.confirm(a.getId(),new PasteBatchRequest(List.of(row(1,"word","nghĩa")),target.toString(),"PRESERVE_EXISTING"));
        assertThat(r.collectionId()).isEqualTo(target);
        assertThat(sets.findById(target).orElseThrow().getFolderId()).isEqualTo(folder.id());
        assertThat(reviews.getDueQueue(a.getId(),target,50).totalDue()).isEqualTo(1);
        assertThat(reviews.getDueQueue(a.getId(),set,50).totalDue()).isZero();
    }
    @Test void concurrentManualMembershipAndPasteShareLock() throws Exception {
        Vocabulary w=word(a,"word","old"); reviews.provisionCardForVocabulary(a.getId(),w.getId(),null);
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var paste=pool.submit(()->{start.await();return service.confirm(a.getId(),batch(row(1,"word","new")));});
            var assign=pool.submit(()->{start.await();return collectionService.assignVocabularies(a.getId(),set,new AssignVocabularyRequest(List.of(w.getId())));});
            start.countDown(); paste.get(15,TimeUnit.SECONDS); assign.get(15,TimeUnit.SECONDS);
            assertThat(words.count()).isEqualTo(1); assertThat(memberships.count()).isEqualTo(1); assertThat(srs.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Autowired org.springframework.boot.autoconfigure.web.servlet.MultipartProperties multipartLimits;
    @Test void multipartLimitsMatchExistingTwoMibContract() {
        assertThat(multipartLimits.getMaxFileSize().toBytes()).isEqualTo(2L*1024*1024);
        assertThat(multipartLimits.getMaxRequestSize().toBytes()).isEqualTo(3L*1024*1024);
    }
    @Test void excludedInvalidFieldsAndSafeHtmlContent() throws Exception {
        postJson("confirm",batch(row(1,"<script>alert(1)</script>","<b>content</b>"),
            new PasteImportRow(2,null,null,null,null,true))).andExpect(status().isOk()).andExpect(jsonPath("$.excludedCount").value(1));
        assertThat(words.findAll().getFirst().getTerm()).isEqualTo("<script>alert(1)</script>");
    }
}
