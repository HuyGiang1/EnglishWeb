package com.englishflow.content.audio;

import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.dictation.DictationAttempt;
import com.englishflow.dictation.DictationAttemptRepository;
import com.englishflow.dictation.DictationService;
import com.englishflow.dictation.LearningContent;
import com.englishflow.dictation.LearningContentRepository;
import com.englishflow.dictation.dto.DictationSubmitRequest;
import com.englishflow.shadowing.ShadowingService;
import com.englishflow.shadowing.ShadowingTakeRepository;
import com.englishflow.shadowing.dto.ShadowingCompleteRequest;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ProductionAudioSafetyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AudioManifestService audioManifestService;

    @Autowired
    private AudioProductionReadinessGuard audioProductionReadinessGuard;

    @Autowired
    private DictationService dictationService;

    @Autowired
    private ShadowingService shadowingService;

    @Autowired
    private LearningContentRepository learningContentRepository;

    @Autowired
    private DictationAttemptRepository dictationAttemptRepository;

    @Autowired
    private ShadowingTakeRepository shadowingTakeRepository;

    @Autowired
    private LearningActivityEventRepository learningActivityEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private static final UUID B2_DICTATION_ID = UUID.fromString("b2000000-0000-0000-0000-000000000042");
    private static final UUID A1_SHADOWING_ID = UUID.fromString("a1000000-0000-0000-0001-000000000001");

    @BeforeEach
    void setUp() {
        learningActivityEventRepository.deleteAll();
        dictationAttemptRepository.deleteAll();
        shadowingTakeRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(new User(
            "audio.tester@example.com",
            passwordEncoder.encode("Password123!"),
            "Audio Tester",
            "Asia/Bangkok",
            20
        ));

        // Ensure dev mode by default
        audioManifestService.setProductionMode(false);
        audioManifestService.setStrictStartupCheck(false);
        audioManifestService.reload();
    }

    @AfterEach
    void tearDown() {
        audioManifestService.setProductionMode(false);
        audioManifestService.setStrictStartupCheck(false);
    }

    private MockHttpServletRequestBuilder withAuthAndCsrf(MockHttpServletRequestBuilder builder) {
        return builder
            .with(user(testUser.getEmail()))
            .with(csrf());
    }

    @Test
    @DisplayName("01. Development mode serves legitimate local development spoken WAV asset")
    void testDevModeServesLocalAudio() throws Exception {
        mockMvc.perform(get("/api/v1/media/audio/curated/dictation-b2-042.wav"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.anyOf(containsString("audio/wav"), containsString("audio/x-wav"))))
            .andExpect(header().exists("Content-Length"));
    }

    @Test
    @DisplayName("02. Development mode supports HTTP Range partial content requests")
    void testDevModeHttpRangeRequest() throws Exception {
        mockMvc.perform(get("/api/v1/media/audio/curated/dictation-b2-042.wav")
                .header("Range", "bytes=0-1023"))
            .andExpect(status().is(org.hamcrest.Matchers.anyOf(is(200), is(206))))
            .andExpect(header().exists("Content-Type"));
    }

    @Test
    @DisplayName("03. Production mode strictly rejects unapproved local macOS speech assets with 403 Forbidden")
    void testProductionModeRejectsUnapprovedAudio() throws Exception {
        audioManifestService.setProductionMode(true);

        mockMvc.perform(get("/api/v1/media/audio/curated/dictation-b2-042.wav"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("AUDIO_UNAPPROVED_FOR_PRODUCTION")))
            .andExpect(jsonPath("$.message", containsString("not approved for production distribution")));
    }

    @Test
    @DisplayName("04. HEAD requests obey identical production gate authorization as GET")
    void testHeadRequestFollowsSameApprovalPolicy() throws Exception {
        // In dev mode: HEAD is OK
        mockMvc.perform(head("/api/v1/media/audio/curated/dictation-b2-042.wav"))
            .andExpect(status().isOk());

        // In production mode: HEAD yields 403
        audioManifestService.setProductionMode(true);
        mockMvc.perform(head("/api/v1/media/audio/curated/dictation-b2-042.wav"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("05. Path traversal attempts are rejected with 400 Bad Request")
    void testPathTraversalRejected() throws Exception {
        mockMvc.perform(get("/api/v1/media/audio/curated/%2e%2e/application.yml"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/media/audio/curated/..%2fapplication.yml"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("06. Non-audio file extensions like .json or .yml are rejected with 404")
    void testNonAudioExtensionRejected() throws Exception {
        mockMvc.perform(get("/api/v1/media/audio/curated/audio-manifest.json"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("MEDIA_NOT_FOUND")));
    }

    @Test
    @DisplayName("07. Nonexistent audio asset returns 404 Not Found")
    void testMissingAudioAssetReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/media/audio/curated/nonexistent-999.wav"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("08. Alternate static URLs cannot bypass media guard (spring.web.resources.add-mappings=false)")
    void testAlternateStaticUrlsBlocked() throws Exception {
        // Authenticated direct static access yields 404 (add-mappings=false)
        mockMvc.perform(withAuthAndCsrf(get("/media/audio/curated/dictation-b2-042.wav")))
            .andExpect(status().isNotFound());

        // Unauthenticated direct static access yields 401 Unauthorized (SecurityConfig catch-all)
        mockMvc.perform(get("/media/audio/curated/dictation-b2-042.wav"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(withAuthAndCsrf(get("/dictation-b2-042.wav")))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("09. Strict production startup guard throws IllegalStateException when unapproved audio is published")
    void testStrictProductionStartupThrows() {
        audioManifestService.setProductionMode(true);
        audioManifestService.setStrictStartupCheck(true);

        assertThatThrownBy(() -> audioProductionReadinessGuard.onApplicationEvent(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Production startup aborted");
    }

    @Test
    @DisplayName("10. Dictation submission is rejected with 400 AUDIO_UNAVAILABLE when audio is unapproved in production")
    void testDictationSubmissionRejectedWhenAudioUnavailable() throws Exception {
        audioManifestService.setProductionMode(true);

        DictationSubmitRequest req = new DictationSubmitRequest(
            UUID.randomUUID(),
            "The country showed great economic resilience during the crisis.",
            20
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_DICTATION_ID + "/submit"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("AUDIO_UNAVAILABLE")));

        assertThat(dictationAttemptRepository.count()).isZero();
        assertThat(learningActivityEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("11. Shadowing take completion is rejected with 400 AUDIO_UNAVAILABLE when audio is unapproved in production")
    void testShadowingSubmissionRejectedWhenAudioUnavailable() throws Exception {
        audioManifestService.setProductionMode(true);

        ShadowingCompleteRequest req = new ShadowingCompleteRequest(
            UUID.randomUUID(),
            "GOOD",
            new BigDecimal("2.50")
        );

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + A1_SHADOWING_ID + "/complete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("AUDIO_UNAVAILABLE")));

        assertThat(shadowingTakeRepository.count()).isZero();
        assertThat(learningActivityEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("12. Historical dictation attempt can still be replayed idempotently even in production mode")
    void testHistoricalAttemptReplayedIdempotently() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String submitted = "The country showed great economic resilience during the crisis.";

        // 1. Submit legitimately in dev mode
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, submitted, 20);
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_DICTATION_ID + "/submit"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        assertThat(dictationAttemptRepository.count()).isEqualTo(1);
        assertThat(learningActivityEventRepository.count()).isEqualTo(1);

        // 2. Switch to production mode (where unapproved audio is barred)
        audioManifestService.setProductionMode(true);

        // 3. Replaying the identical historical attempt MUST succeed with Idempotent-Replay: true
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_DICTATION_ID + "/submit"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.attemptId", is(attemptId.toString())));

        // Zero additional events or attempt rows
        assertThat(dictationAttemptRepository.count()).isEqualTo(1);
        assertThat(learningActivityEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("13. Production profile automatically enforces audio approval without extra flag (Case B)")
    void testProductionProfileAutoEnforcesAudioApproval() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        AudioManifestService service = new AudioManifestService(new DefaultResourceLoader(), objectMapper, prodEnv);
        service.init();
        assertThat(service.isProductionMode()).isTrue();
    }

    @Test
    @DisplayName("14. Production profile with enforcement explicitly set to false aborts startup with IllegalStateException (Case C)")
    void testProductionProfileExplicitlyDisabledThrowsFatalError() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        prodEnv.setProperty("englishflow.media.production-mode", "false");
        AudioManifestService service = new AudioManifestService(new DefaultResourceLoader(), objectMapper, prodEnv);
        assertThatThrownBy(service::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FATAL CONFIGURATION ERROR")
            .hasMessageContaining("cannot be set to false when running in production profile");
    }

    @Test
    @DisplayName("15. Development profile allows explicitly designated local development audio (Case A)")
    void testDevelopmentProfileDefaultsToPermissiveDevAudio() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        AudioManifestService service = new AudioManifestService(new DefaultResourceLoader(), objectMapper, devEnv);
        service.init();
        assertThat(service.isProductionMode()).isFalse();
    }

    @Test
    @DisplayName("16. Production mode with missing manifest denies audio (Case D)")
    void testMissingManifestDeniesAudioInProduction() {
        DefaultResourceLoader emptyLoader = new DefaultResourceLoader() {
            @Override
            public org.springframework.core.io.Resource getResource(String location) {
                return new ByteArrayResource(new byte[0]) {
                    @Override
                    public boolean exists() {
                        return false;
                    }
                };
            }
        };
        AudioManifestService emptyManifestService = new AudioManifestService(
            emptyLoader,
            objectMapper,
            new MockEnvironment()
        );
        emptyManifestService.setProductionMode(true);
        emptyManifestService.resetEmptyForTesting();

        assertThat(emptyManifestService.isAudioAvailable("curated/dictation-b2-042.wav")).isFalse();
        assertThat(emptyManifestService.getAudioReadiness("curated/dictation-b2-042.wav"))
            .isEqualTo(AudioReadinessStatus.MISSING);
    }

    @Test
    @DisplayName("17. Production mode with malformed manifest fails closed and denies audio (Case E)")
    void testMalformedManifestFailsClosedInProduction() {
        DefaultResourceLoader malformedLoader = new DefaultResourceLoader() {
            @Override
            public org.springframework.core.io.Resource getResource(String location) {
                return new ByteArrayResource("{ invalid-json: true".getBytes()) {
                    @Override
                    public boolean exists() {
                        return true;
                    }
                };
            }
        };
        AudioManifestService malformedService = new AudioManifestService(
            malformedLoader,
            objectMapper,
            new MockEnvironment()
        );
        malformedService.setProductionMode(true);
        malformedService.reload();

        assertThat(malformedService.isAudioAvailable("curated/dictation-b2-042.wav")).isFalse();
        assertThat(malformedService.getAssets()).isEmpty();
    }

    @Test
    @DisplayName("18. Asset with missing or local dev license evidence ref fails production approval (Case F)")
    void testMissingOrDevLicenseEvidenceDeniesProductionApproval() {
        AudioManifestAsset assetMissingLicense = new AudioManifestAsset(
            UUID.randomUUID(), "DICTATION", "Title", "curated/test.wav", "test.wav",
            "abc123sha256", "audio/wav", 1024L, 5.0, "transcriptSha256", "en-US",
            "Polly", "Joanna", "Licensed", null, true, true, "2026-10-17T00:00:00Z",
            AudioReadinessStatus.LICENSED_DISTRIBUTABLE
        );
        assertThat(assetMissingLicense.isApprovedForProduction()).isFalse();

        AudioManifestAsset assetDevRestriction = new AudioManifestAsset(
            UUID.randomUUID(), "DICTATION", "Title", "curated/test.wav", "test.wav",
            "abc123sha256", "audio/wav", 1024L, 5.0, "transcriptSha256", "en-US",
            "Polly", "Joanna", "Licensed", "docs/licensing/LOCAL_DEV_RESTRICTIONS.md",
            true, true, "2026-10-17T00:00:00Z", AudioReadinessStatus.LICENSED_DISTRIBUTABLE
        );
        assertThat(assetDevRestriction.isApprovedForProduction()).isFalse();
    }

    @Test
    @DisplayName("19. Production audio with outdated or mismatched transcript hash is denied (Case G)")
    void testOutdatedTranscriptHashDeniesProductionAudio() {
        audioManifestService.setProductionMode(true);
        // Valid storage key with mismatched canonical transcript text
        boolean available = audioManifestService.isAudioAvailable(
            "curated/dictation-b2-042.wav",
            "This is a modified or outdated canonical transcript that will not match the sha256."
        );
        assertThat(available).isFalse();
    }

    @Test
    @DisplayName("20. Asset with unknown or non-distributable readiness status is denied in production (Case H)")
    void testUnknownOrNonDistributableReadinessStatusDeniesProduction() {
        AudioManifestAsset spokenAsset = new AudioManifestAsset(
            UUID.randomUUID(), "DICTATION", "Title", "curated/test.wav", "test.wav",
            "abc123sha256", "audio/wav", 1024L, 5.0, "transcriptSha256", "en-US",
            "macOS", "Samantha", "Local", "docs/licensing/COMMERCIAL_LICENSE.md",
            true, true, "2026-10-17T00:00:00Z", AudioReadinessStatus.LOCAL_SPOKEN
        );
        assertThat(spokenAsset.isApprovedForProduction()).isFalse();

        AudioManifestAsset fixtureAsset = new AudioManifestAsset(
            UUID.randomUUID(), "DICTATION", "Title", "curated/test.wav", "test.wav",
            "abc123sha256", "audio/wav", 1024L, 5.0, "transcriptSha256", "en-US",
            "SoX", "Synth", "Fixture", "docs/licensing/COMMERCIAL_LICENSE.md",
            true, true, "2026-10-17T00:00:00Z", AudioReadinessStatus.SYNTHETIC_FIXTURE
        );
        assertThat(fixtureAsset.isApprovedForProduction()).isFalse();
    }

    @Test
    @DisplayName("21. Unrelated modules remain fully operational when audio lessons are unavailable (Case J)")
    void testUnrelatedModulesRemainFullyOperationalWhenAudioUnavailable() throws Exception {
        // Enforce production mode (audio unavailable)
        audioManifestService.setProductionMode(true);

        // 1. Vocabulary review queue query
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/review/queue")))
            .andExpect(status().isOk());

        // 2. Vocabulary collection search/list
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/vocabulary")))
            .andExpect(status().isOk());

        // 3. User settings
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/settings")))
            .andExpect(status().isOk());

        // 4. Analytics heatmap
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/analytics/activity-heatmap")))
            .andExpect(status().isOk());
    }
}
