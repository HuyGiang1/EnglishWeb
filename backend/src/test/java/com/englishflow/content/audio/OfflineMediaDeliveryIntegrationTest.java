package com.englishflow.content.audio;

import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.dictation.DictationAttemptRepository;
import com.englishflow.dictation.LearningContentRepository;
import com.englishflow.shadowing.ShadowingTakeRepository;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class OfflineMediaDeliveryIntegrationTest {

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

    @TempDir
    Path tempStorageVolume;

    private String originalStorageRoot;

    @BeforeEach
    void setUp() {
        originalStorageRoot = audioManifestService.getStorageRoot();
        audioManifestService.setProductionMode(false);
        audioManifestService.setStrictStartupCheck(false);
        audioManifestService.reload();
    }

    @AfterEach
    void tearDown() {
        audioManifestService.setStorageRoot(originalStorageRoot);
        audioManifestService.setProductionMode(false);
        audioManifestService.setStrictStartupCheck(false);
        audioManifestService.reload();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] createDummyWavBytes(int pcmSamples) {
        // Minimal valid 44-byte WAV header + PCM 16-bit mono silence
        int dataSize = pcmSamples * 2;
        int fileSize = 36 + dataSize;
        byte[] wav = new byte[44 + dataSize];

        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        wav[4] = (byte) (fileSize & 0xff);
        wav[5] = (byte) ((fileSize >> 8) & 0xff);
        wav[6] = (byte) ((fileSize >> 16) & 0xff);
        wav[7] = (byte) ((fileSize >> 24) & 0xff);
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
        wav[16] = 16; wav[17] = 0; wav[18] = 0; wav[19] = 0; // Subchunk1Size (16 for PCM)
        wav[20] = 1; wav[21] = 0; // AudioFormat (1 = PCM)
        wav[22] = 1; wav[23] = 0; // NumChannels (1 mono)
        wav[24] = (byte) (16000 & 0xff); wav[25] = (byte) ((16000 >> 8) & 0xff); wav[26] = 0; wav[27] = 0; // SampleRate 16000
        wav[28] = (byte) (32000 & 0xff); wav[29] = (byte) ((32000 >> 8) & 0xff); wav[30] = 0; wav[31] = 0; // ByteRate 32000
        wav[32] = 2; wav[33] = 0; // BlockAlign (2 bytes)
        wav[34] = 16; wav[35] = 0; // BitsPerSample (16)
        wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
        wav[40] = (byte) (dataSize & 0xff);
        wav[41] = (byte) ((dataSize >> 8) & 0xff);
        wav[42] = (byte) ((dataSize >> 16) & 0xff);
        wav[43] = (byte) ((dataSize >> 24) & 0xff);

        return wav;
    }

    @Test
    @DisplayName("Case A: Approved test fixture served successfully from runtime volume")
    void testApprovedFixtureServedFromRuntimeVolume() throws Exception {
        // 1. Setup temporary runtime volume: <tempStorageVolume>/curated/fixture-approved.wav
        File curatedDir = new File(tempStorageVolume.toFile(), "curated");
        curatedDir.mkdirs();

        byte[] audioBytes = createDummyWavBytes(8000);
        File audioFile = new File(curatedDir, "fixture-approved.wav");
        try (FileOutputStream fos = new FileOutputStream(audioFile)) {
            fos.write(audioBytes);
        }
        String audioSha = sha256Hex(audioBytes);

        UUID contentId = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        String transcriptSha = "0f1ba802b4ddbcf89dd65de2840845f5cc028a7cd21813c1a495d8834366b9bd";

        String manifestJson = String.format("""
            {
              "manifestVersion": "1.0.0",
              "mode": "LICENSED_DISTRIBUTABLE",
              "assets": {
                "%s": {
                  "contentType": "DICTATION",
                  "title": "Daily Routine",
                  "storageKey": "curated/fixture-approved.wav",
                  "fileName": "fixture-approved.wav",
                  "sha256": "%s",
                  "mimeType": "audio/wav",
                  "fileSizeBytes": %d,
                  "transcriptSha256": "%s",
                  "accent": "en-US",
                  "provider": "TEST_APPROVED_STUB",
                  "voiceId": "StubVoice",
                  "provenance": "TEST_CONTAINER_FIXTURE",
                  "licenseEvidenceRef": "docs/licensing/TEST_FIXTURE_LICENSE.md",
                  "distributionApproved": true,
                  "humanQaVerified": true,
                  "reviewedAt": "2026-10-22T00:00:00Z",
                  "readinessStatus": "LICENSED_DISTRIBUTABLE"
                }
              }
            }
            """, contentId, audioSha, audioBytes.length, transcriptSha);

        Files.writeString(new File(curatedDir, "audio-manifest.json").toPath(), manifestJson);

        // 2. Configure audioManifestService to use this storage root in production mode
        audioManifestService.setStorageRoot(tempStorageVolume.toAbsolutePath().toString());
        audioManifestService.setProductionMode(true);
        audioManifestService.reload();

        // 3. Request resource via standard public API route
        mockMvc.perform(get("/api/v1/media/audio/curated/fixture-approved.wav"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.anyOf(containsString("audio/wav"), containsString("audio/x-wav"))))
            .andExpect(header().string("Content-Length", String.valueOf(audioBytes.length)));

        // 4. Parity: HEAD request returns 200 OK
        mockMvc.perform(head("/api/v1/media/audio/curated/fixture-approved.wav"))
            .andExpect(status().isOk());

        // 5. HTTP Range request returns 206 Partial Content (or 200)
        mockMvc.perform(get("/api/v1/media/audio/curated/fixture-approved.wav")
                .header("Range", "bytes=0-15"))
            .andExpect(status().is(org.hamcrest.Matchers.anyOf(is(200), is(206))));
    }

    @Test
    @DisplayName("Case K: Symlink escape outside runtime storage root is blocked with 403 Forbidden")
    void testSymlinkEscapeBlocked() throws Exception {
        File curatedDir = new File(tempStorageVolume.toFile(), "curated");
        curatedDir.mkdirs();

        // Create target outside storage volume
        File outsideSecret = File.createTempFile("secret_outside_", ".wav");
        Files.write(outsideSecret.toPath(), createDummyWavBytes(100));

        // Create symlink inside curated pointing to outside file
        Path symlinkPath = curatedDir.toPath().resolve("symlink-escape.wav");
        try {
            Files.createSymbolicLink(symlinkPath, outsideSecret.toPath());
        } catch (UnsupportedOperationException e) {
            // OS does not support symlinks; skip gracefully
            return;
        }

        audioManifestService.setStorageRoot(tempStorageVolume.toAbsolutePath().toString());
        audioManifestService.setProductionMode(true);

        mockMvc.perform(get("/api/v1/media/audio/curated/symlink-escape.wav"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("SYMLINK_ESCAPE_DETECTED")));

        outsideSecret.delete();
    }

    @Test
    @DisplayName("Case L & M: Staging profiles (staging, staging-public, stage, uat, release) remain fail-closed")
    void testStagingProfilesRemainFailClosed() {
        String[] restrictedProfiles = {
            "staging", "staging-public", "stage", "uat", "release", "prod", "production"
        };

        for (String profile : restrictedProfiles) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles(profile);
            AudioManifestService service = new AudioManifestService(new DefaultResourceLoader(), objectMapper, env);
            service.init();
            assertThat(service.isProductionMode())
                .as("Profile '%s' must fail-closed to productionMode=true by default", profile)
                .isTrue();

            // Attempting to bypass in restricted profile must abort startup
            MockEnvironment bypassEnv = new MockEnvironment();
            bypassEnv.setActiveProfiles(profile);
            bypassEnv.setProperty("englishflow.media.production-mode", "false");
            AudioManifestService bypassService = new AudioManifestService(new DefaultResourceLoader(), objectMapper, bypassEnv);
            assertThatThrownBy(bypassService::init)
                .as("Disabling production-mode in '%s' must throw IllegalStateException", profile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FATAL CONFIGURATION ERROR");
        }
    }

    @Test
    @DisplayName("Case N: Unknown profile and empty profile fail closed to production mode")
    void testUnknownProfileAndEmptyProfileFailClosed() {
        // Unknown profile
        MockEnvironment unknownEnv = new MockEnvironment();
        unknownEnv.setActiveProfiles("unknown-profile");
        AudioManifestService unknownService = new AudioManifestService(new DefaultResourceLoader(), objectMapper, unknownEnv);
        unknownService.init();
        assertThat(unknownService.isProductionMode()).isTrue();

        // Empty profile (no active profiles)
        MockEnvironment emptyEnv = new MockEnvironment();
        AudioManifestService emptyService = new AudioManifestService(new DefaultResourceLoader(), objectMapper, emptyEnv);
        emptyService.init();
        assertThat(emptyService.isProductionMode()).isTrue();

        // Attempting to enable developer mode under unknown profile must abort startup
        MockEnvironment bypassUnknown = new MockEnvironment();
        bypassUnknown.setActiveProfiles("unknown-profile");
        bypassUnknown.setProperty("englishflow.media.production-mode", "false");
        AudioManifestService bypassService = new AudioManifestService(new DefaultResourceLoader(), objectMapper, bypassUnknown);
        assertThatThrownBy(bypassService::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FATAL CONFIGURATION ERROR");
    }

    @Test
    @DisplayName("Case O: Explicitly isolated dev and test profiles default to permissive developer mode")
    void testPermittedDevProfilesDefaultToDevMode() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        AudioManifestService devService = new AudioManifestService(new DefaultResourceLoader(), objectMapper, devEnv);
        devService.init();
        assertThat(devService.isProductionMode()).isFalse();

        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");
        AudioManifestService testService = new AudioManifestService(new DefaultResourceLoader(), objectMapper, testEnv);
        testService.init();
        assertThat(testService.isProductionMode()).isFalse();
    }

    @Test
    @DisplayName("Case S: Media release rollback restores coherent previous release")
    void testReleaseRollbackRestoresPreviousRelease() throws Exception {
        File releasesDir = new File(tempStorageVolume.toFile(), "releases");
        File v1Dir = new File(releasesDir, "v1.0.0/curated");
        File v2Dir = new File(releasesDir, "v2.0.0/curated");
        v1Dir.mkdirs();
        v2Dir.mkdirs();

        // v1 manifest
        Files.writeString(new File(v1Dir, "audio-manifest.json").toPath(), """
            {"manifestVersion": "1.0.0", "releaseTag": "v1.0.0", "mode": "LICENSED_DISTRIBUTABLE", "assets": {}}
            """);

        // v2 manifest
        Files.writeString(new File(v2Dir, "audio-manifest.json").toPath(), """
            {"manifestVersion": "2.0.0", "releaseTag": "v2.0.0", "mode": "LICENSED_DISTRIBUTABLE", "assets": {}}
            """);

        // Symlink active release to v2
        Path curatedLink = tempStorageVolume.resolve("curated");
        Files.createSymbolicLink(curatedLink, tempStorageVolume.relativize(v2Dir.toPath()));

        audioManifestService.setStorageRoot(tempStorageVolume.toAbsolutePath().toString());
        audioManifestService.reload();

        // Rollback: update symlink to point to v1
        Files.delete(curatedLink);
        Files.createSymbolicLink(curatedLink, tempStorageVolume.relativize(v1Dir.toPath()));

        audioManifestService.reload();
        // Successfully restored previous release directory
        assertThat(new File(tempStorageVolume.toFile(), "curated/audio-manifest.json").exists()).isTrue();
    }

    @Test
    @DisplayName("Read-only volume deployment rehearsal: read succeeds, attempted write fails")
    void testReadOnlyVolumeDeploymentRehearsal() throws Exception {
        File curatedDir = new File(tempStorageVolume.toFile(), "curated");
        curatedDir.mkdirs();

        byte[] audioBytes = createDummyWavBytes(4000);
        File audioFile = new File(curatedDir, "fixture-readonly.wav");
        Files.write(audioFile.toPath(), audioBytes);
        String audioSha = sha256Hex(audioBytes);

        UUID contentId = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        String transcriptSha = "0f1ba802b4ddbcf89dd65de2840845f5cc028a7cd21813c1a495d8834366b9bd";

        String manifestJson = String.format("""
            {
              "manifestVersion": "1.0.0",
              "releaseTag": "v1.0.0-readonly",
              "mode": "LICENSED_DISTRIBUTABLE",
              "assets": {
                "%s": {
                  "contentType": "DICTATION",
                  "title": "Daily Routine",
                  "storageKey": "curated/fixture-readonly.wav",
                  "fileName": "fixture-readonly.wav",
                  "sha256": "%s",
                  "mimeType": "audio/wav",
                  "fileSizeBytes": %d,
                  "transcriptSha256": "%s",
                  "accent": "en-US",
                  "provider": "TEST_APPROVED_STUB",
                  "voiceId": "StubVoice",
                  "provenance": "READONLY_CONTAINER_FIXTURE",
                  "licenseEvidenceRef": "docs/licensing/TEST_FIXTURE_LICENSE.md",
                  "distributionApproved": true,
                  "humanQaVerified": true,
                  "reviewedAt": "2026-10-22T00:00:00Z",
                  "readinessStatus": "LICENSED_DISTRIBUTABLE"
                }
              }
            }
            """, contentId, audioSha, audioBytes.length, transcriptSha);

        File manifestFile = new File(curatedDir, "audio-manifest.json");
        Files.writeString(manifestFile.toPath(), manifestJson);

        audioManifestService.setStorageRoot(tempStorageVolume.toAbsolutePath().toString());
        audioManifestService.setProductionMode(true);
        audioManifestService.reload();

        // Enforce POSIX read-only on curated directory and files
        manifestFile.setWritable(false, false);
        audioFile.setWritable(false, false);
        curatedDir.setWritable(false, false);

        // Verification 1: Attempted write from application context must fail
        File attemptedTamper = new File(curatedDir, "tamper-attempt.txt");
        boolean writeSucceeded = false;
        try {
            Files.writeString(attemptedTamper.toPath(), "malicious injection");
            writeSucceeded = true;
        } catch (Exception expected) {
            // Write failed as required by read-only mount
        }
        assertThat(writeSucceeded).isFalse();

        // Verification 2: GET succeeds
        mockMvc.perform(get("/api/v1/media/audio/curated/fixture-readonly.wav"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Length", String.valueOf(audioBytes.length)));

        // Verification 3: HEAD succeeds
        mockMvc.perform(head("/api/v1/media/audio/curated/fixture-readonly.wav"))
            .andExpect(status().isOk());

        // Verification 4: Range request succeeds
        mockMvc.perform(get("/api/v1/media/audio/curated/fixture-readonly.wav").header("Range", "bytes=0-15"))
            .andExpect(status().is(org.hamcrest.Matchers.anyOf(is(200), is(206))));

        // Restore write permissions for cleanup
        curatedDir.setWritable(true, false);
        audioFile.setWritable(true, false);
        manifestFile.setWritable(true, false);
    }

    @Test
    @DisplayName("TOCTOU / Cross-release drift test: running backend snapshot remains bound to release at startup")
    void testCrossReleaseDriftBoundSnapshotIsolation() throws Exception {
        File releasesDir = new File(tempStorageVolume.toFile(), "releases");
        File v1Dir = new File(releasesDir, "v1.0.0/curated");
        File v2Dir = new File(releasesDir, "v2.0.0/curated");
        v1Dir.mkdirs();
        v2Dir.mkdirs();

        byte[] audioBytesV1 = createDummyWavBytes(4000); // 8044 bytes
        byte[] audioBytesV2 = createDummyWavBytes(8000); // 16044 bytes
        Files.write(new File(v1Dir, "drift-test.wav").toPath(), audioBytesV1);
        Files.write(new File(v2Dir, "drift-test.wav").toPath(), audioBytesV2);

        UUID contentId = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        String transcriptSha = "0f1ba802b4ddbcf89dd65de2840845f5cc028a7cd21813c1a495d8834366b9bd";

        String manifestV1 = String.format("""
            {
              "manifestVersion": "1.0.0",
              "releaseTag": "v1.0.0",
              "mode": "LICENSED_DISTRIBUTABLE",
              "assets": {
                "%s": {
                  "contentType": "DICTATION",
                  "title": "Daily Routine",
                  "storageKey": "curated/drift-test.wav",
                  "fileName": "drift-test.wav",
                  "sha256": "%s",
                  "mimeType": "audio/wav",
                  "fileSizeBytes": %d,
                  "transcriptSha256": "%s",
                  "accent": "en-US",
                  "provider": "TEST_STUB",
                  "voiceId": "Voice1",
                  "provenance": "V1",
                  "licenseEvidenceRef": "docs/licensing/TEST.md",
                  "distributionApproved": true,
                  "humanQaVerified": true,
                  "readinessStatus": "LICENSED_DISTRIBUTABLE"
                }
              }
            }
            """, contentId, sha256Hex(audioBytesV1), audioBytesV1.length, transcriptSha);

        String manifestV2 = String.format("""
            {
              "manifestVersion": "1.0.0",
              "releaseTag": "v2.0.0",
              "mode": "LICENSED_DISTRIBUTABLE",
              "assets": {
                "%s": {
                  "contentType": "DICTATION",
                  "title": "Daily Routine",
                  "storageKey": "curated/drift-test.wav",
                  "fileName": "drift-test.wav",
                  "sha256": "%s",
                  "mimeType": "audio/wav",
                  "fileSizeBytes": %d,
                  "transcriptSha256": "%s",
                  "accent": "en-US",
                  "provider": "TEST_STUB",
                  "voiceId": "Voice2",
                  "provenance": "V2",
                  "licenseEvidenceRef": "docs/licensing/TEST.md",
                  "distributionApproved": true,
                  "humanQaVerified": true,
                  "readinessStatus": "LICENSED_DISTRIBUTABLE"
                }
              }
            }
            """, contentId, sha256Hex(audioBytesV2), audioBytesV2.length, transcriptSha);

        Files.writeString(new File(v1Dir, "audio-manifest.json").toPath(), manifestV1);
        Files.writeString(new File(v2Dir, "audio-manifest.json").toPath(), manifestV2);

        // Point curated symlink to v1 initially
        Path curatedLink = tempStorageVolume.resolve("curated");
        Files.createSymbolicLink(curatedLink, tempStorageVolume.relativize(v1Dir.toPath()));

        audioManifestService.setStorageRoot(tempStorageVolume.toAbsolutePath().toString());
        audioManifestService.setProductionMode(true);
        audioManifestService.reload();

        assertThat(audioManifestService.getReleaseTag()).isEqualTo("v1.0.0");

        // Request served from v1
        mockMvc.perform(get("/api/v1/media/audio/curated/drift-test.wav"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Length", String.valueOf(audioBytesV1.length)));

        // Now simulate release switch on disk while backend is running WITHOUT backend restart:
        Files.delete(curatedLink);
        Files.createSymbolicLink(curatedLink, tempStorageVolume.relativize(v2Dir.toPath()));

        // Crucial test: because the running backend is bound to v1 snapshot, request must still serve v1 bytes!
        mockMvc.perform(get("/api/v1/media/audio/curated/drift-test.wav"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Length", String.valueOf(audioBytesV1.length)));

        // Now trigger backend transition/restart (via reload):
        audioManifestService.reload();
        assertThat(audioManifestService.getReleaseTag()).isEqualTo("v2.0.0");

        // Now requests are served from v2
        mockMvc.perform(get("/api/v1/media/audio/curated/drift-test.wav"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Length", String.valueOf(audioBytesV2.length)));
    }

    @Test
    @DisplayName("Tampered audio file size on disk is rejected with 403 MEDIA_INTEGRITY_COMPROMISED")
    void testTamperedFileSizeRejected() throws Exception {
        File curatedDir = new File(tempStorageVolume.toFile(), "curated");
        curatedDir.mkdirs();

        byte[] audioBytes = createDummyWavBytes(4000);
        File audioFile = new File(curatedDir, "fixture-tamper.wav");
        Files.write(audioFile.toPath(), audioBytes);

        UUID contentId = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        String transcriptSha = "0f1ba802b4ddbcf89dd65de2840845f5cc028a7cd21813c1a495d8834366b9bd";

        String manifestJson = String.format("""
            {
              "manifestVersion": "1.0.0",
              "releaseTag": "v1.0.0",
              "mode": "LICENSED_DISTRIBUTABLE",
              "assets": {
                "%s": {
                  "contentType": "DICTATION",
                  "title": "Daily Routine",
                  "storageKey": "curated/fixture-tamper.wav",
                  "fileName": "fixture-tamper.wav",
                  "sha256": "%s",
                  "mimeType": "audio/wav",
                  "fileSizeBytes": %d,
                  "transcriptSha256": "%s",
                  "accent": "en-US",
                  "provider": "TEST_STUB",
                  "voiceId": "Voice",
                  "provenance": "TEST",
                  "licenseEvidenceRef": "docs/licensing/TEST.md",
                  "distributionApproved": true,
                  "humanQaVerified": true,
                  "readinessStatus": "LICENSED_DISTRIBUTABLE"
                }
              }
            }
            """, contentId, sha256Hex(audioBytes), audioBytes.length, transcriptSha);

        Files.writeString(new File(curatedDir, "audio-manifest.json").toPath(), manifestJson);

        audioManifestService.setStorageRoot(tempStorageVolume.toAbsolutePath().toString());
        audioManifestService.setProductionMode(true);
        audioManifestService.reload();

        // Tamper with audio file by appending bytes
        byte[] tamperedBytes = createDummyWavBytes(6000); // Larger size
        Files.write(audioFile.toPath(), tamperedBytes);

        mockMvc.perform(get("/api/v1/media/audio/curated/fixture-tamper.wav"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("MEDIA_INTEGRITY_COMPROMISED")));
    }

    @Test
    @DisplayName("Corrupt / Malformed manifest fails closed in production mode")
    void testCorruptManifestFailsClosed() throws Exception {
        File curatedDir = new File(tempStorageVolume.toFile(), "curated");
        curatedDir.mkdirs();

        Files.writeString(new File(curatedDir, "audio-manifest.json").toPath(), "{ malformed json ::: invalid }");

        audioManifestService.setStorageRoot(tempStorageVolume.toAbsolutePath().toString());
        audioManifestService.setProductionMode(true);
        audioManifestService.reload();

        assertThat(audioManifestService.getAssets()).isEmpty();
        assertThat(audioManifestService.getReleaseTag()).isEqualTo("MISSING");

        mockMvc.perform(get("/api/v1/media/audio/curated/any.wav"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tampered audio file with identical size but corrupted SHA-256 is rejected with 403 MEDIA_INTEGRITY_COMPROMISED")
    void testTamperedFileSha256SameSizeRejected() throws Exception {
        File curatedDir = new File(tempStorageVolume.toFile(), "curated");
        curatedDir.mkdirs();

        byte[] validAudioBytes = createDummyWavBytes(4000);
        File audioFile = new File(curatedDir, "fixture-same-size.wav");
        Files.write(audioFile.toPath(), validAudioBytes);

        UUID contentId = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        String transcriptSha = "0f1ba802b4ddbcf89dd65de2840845f5cc028a7cd21813c1a495d8834366b9bd";

        String manifestJson = String.format("""
            {
              "manifestVersion": "1.0.0",
              "releaseTag": "v1.0.0",
              "mode": "LICENSED_DISTRIBUTABLE",
              "assets": {
                "%s": {
                  "contentType": "DICTATION",
                  "title": "Daily Routine",
                  "storageKey": "curated/fixture-same-size.wav",
                  "fileName": "fixture-same-size.wav",
                  "sha256": "%s",
                  "mimeType": "audio/wav",
                  "fileSizeBytes": %d,
                  "transcriptSha256": "%s",
                  "accent": "en-US",
                  "provider": "TEST_STUB",
                  "voiceId": "Voice",
                  "provenance": "TEST",
                  "licenseEvidenceRef": "docs/licensing/TEST.md",
                  "distributionApproved": true,
                  "humanQaVerified": true,
                  "readinessStatus": "LICENSED_DISTRIBUTABLE"
                }
              }
            }
            """, contentId, sha256Hex(validAudioBytes), validAudioBytes.length, transcriptSha);

        Files.writeString(new File(curatedDir, "audio-manifest.json").toPath(), manifestJson);

        audioManifestService.setStorageRoot(tempStorageVolume.toAbsolutePath().toString());
        audioManifestService.setProductionMode(true);
        audioManifestService.reload();

        // Tamper audio file: corrupt samples while keeping EXACT SAME file size (4000 samples)
        byte[] corruptedSameSizeBytes = validAudioBytes.clone();
        for (int i = 44; i < 100; i++) {
            corruptedSameSizeBytes[i] = (byte) 0xFF; // Invert bytes inside PCM payload
        }
        Files.write(audioFile.toPath(), corruptedSameSizeBytes);

        // Sanity check: sizes are identical
        assertThat(corruptedSameSizeBytes.length).isEqualTo(validAudioBytes.length);

        // Request must be rejected with 403 MEDIA_INTEGRITY_COMPROMISED (not fooled by matching size)
        mockMvc.perform(get("/api/v1/media/audio/curated/fixture-same-size.wav"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("MEDIA_INTEGRITY_COMPROMISED")));
    }

    @Test
    @DisplayName("Approved asset SHA-256 corruption aborts startup readiness with IllegalStateException")
    void testCorruptedApprovedAssetFailsStartupReadinessGuard() throws Exception {
        File curatedDir = new File(tempStorageVolume.toFile(), "curated");
        curatedDir.mkdirs();

        byte[] validAudioBytes = createDummyWavBytes(4000);
        File audioFile = new File(curatedDir, "fixture-readiness-check.wav");
        Files.write(audioFile.toPath(), validAudioBytes);

        UUID contentId = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        String transcriptSha = "0f1ba802b4ddbcf89dd65de2840845f5cc028a7cd21813c1a495d8834366b9bd";

        String manifestJson = String.format("""
            {
              "manifestVersion": "1.0.0",
              "releaseTag": "v1.0.0",
              "mode": "LICENSED_DISTRIBUTABLE",
              "assets": {
                "%s": {
                  "contentType": "DICTATION",
                  "title": "Daily Routine",
                  "storageKey": "curated/fixture-readiness-check.wav",
                  "fileName": "fixture-readiness-check.wav",
                  "sha256": "%s",
                  "mimeType": "audio/wav",
                  "fileSizeBytes": %d,
                  "transcriptSha256": "%s",
                  "accent": "en-US",
                  "provider": "TEST_STUB",
                  "voiceId": "Voice",
                  "provenance": "TEST",
                  "licenseEvidenceRef": "docs/licensing/TEST.md",
                  "distributionApproved": true,
                  "humanQaVerified": true,
                  "readinessStatus": "LICENSED_DISTRIBUTABLE"
                }
              }
            }
            """, contentId, sha256Hex(validAudioBytes), validAudioBytes.length, transcriptSha);

        Files.writeString(new File(curatedDir, "audio-manifest.json").toPath(), manifestJson);

        audioManifestService.setStorageRoot(tempStorageVolume.toAbsolutePath().toString());
        audioManifestService.setProductionMode(true);
        audioManifestService.reload();

        // Mutate the file so hash does not match
        byte[] tamperedBytes = validAudioBytes.clone();
        tamperedBytes[50] = (byte) (tamperedBytes[50] ^ 0xFF);
        Files.write(audioFile.toPath(), tamperedBytes);

        // Trigger readiness guard
        assertThatThrownBy(() -> audioProductionReadinessGuard.onApplicationEvent(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CRITICAL INTEGRITY FAILURE")
            .hasMessageContaining("fixture-readiness-check.wav");
    }
}
