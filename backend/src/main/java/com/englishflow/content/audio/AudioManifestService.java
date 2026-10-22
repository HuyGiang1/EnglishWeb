package com.englishflow.content.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioManifestService {

    private static final Logger log = LoggerFactory.getLogger(AudioManifestService.class);

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    private static final Set<String> RESTRICTED_PROFILES = Set.of(
        "prod", "production", "staging", "staging-public", "stage", "uat", "release"
    );
    private static final Set<String> PERMITTED_DEV_PROFILES = Set.of(
        "dev", "test"
    );

    @Value("${englishflow.media.storage-root:}")
    private String storageRoot;

    @Value("${englishflow.media.production-mode:}")
    private String configuredProductionMode;

    private boolean productionMode = true;

    @Value("${englishflow.media.strict-startup-check:false}")
    private boolean strictStartupCheck;

    private String manifestVersion = "1.0.0";
    private String topLevelMode = "LOCAL_SPOKEN";
    private String releaseTag = "DEVELOPMENT";
    private String manifestSha256 = null;
    private File boundCuratedDir = null;
    private String loadedAt = null;
    private final Map<UUID, AudioManifestAsset> assetsByContentId = new ConcurrentHashMap<>();
    private final Map<String, AudioManifestAsset> assetsByStorageKey = new ConcurrentHashMap<>();
    private final Map<String, AudioManifestAsset> assetsByFileName = new ConcurrentHashMap<>();
    private final Map<String, Boolean> verifiedShaCache = new ConcurrentHashMap<>();

    public AudioManifestService(ResourceLoader resourceLoader, ObjectMapper objectMapper, Environment environment) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        String[] activeProfiles = environment.getActiveProfiles();
        Set<String> activeSet = Arrays.stream(activeProfiles)
            .map(String::toLowerCase)
            .collect(java.util.stream.Collectors.toSet());

        String explicitProdMode = environment.getProperty("englishflow.media.production-mode");
        String explicitDevMode = environment.getProperty("englishflow.media.developer-mode");

        boolean hasRestrictedProfile = activeSet.stream().anyMatch(RESTRICTED_PROFILES::contains);

        if (hasRestrictedProfile) {
            if ("false".equalsIgnoreCase(explicitProdMode) || "true".equalsIgnoreCase(explicitDevMode)) {
                throw new IllegalStateException(
                    "FATAL CONFIGURATION ERROR: developer mode or disabling production media enforcement is strictly forbidden in restricted/staging/production profiles (" + activeSet + "). englishflow.media.production-mode cannot be set to false when running in production profile."
                );
            }
            this.productionMode = true;
        } else {
            boolean isExclusivelyDevOrTest = !activeSet.isEmpty() && PERMITTED_DEV_PROFILES.containsAll(activeSet);
            if (isExclusivelyDevOrTest) {
                if ("true".equalsIgnoreCase(explicitProdMode)) {
                    this.productionMode = true;
                } else {
                    this.productionMode = false;
                }
            } else {
                // Unknown profile or no active profile: FAIL-CLOSED SECURE DEFAULT
                if ("false".equalsIgnoreCase(explicitProdMode) || "true".equalsIgnoreCase(explicitDevMode)) {
                    throw new IllegalStateException(
                        "FATAL CONFIGURATION ERROR: developer mode can only be enabled in explicitly authorized 'dev' or 'test' profiles. Active profiles: " + activeSet
                    );
                }
                this.productionMode = true;
                log.warn("AudioManifestService: Running under unverified/unknown profile(s) {}. Fail-closed production security policy enforced by default.", activeSet);
            }
        }

        reload();
    }

    public synchronized void reload() {
        assetsByContentId.clear();
        assetsByStorageKey.clear();
        assetsByFileName.clear();
        verifiedShaCache.clear();
        this.loadedAt = java.time.Instant.now().toString();

        JsonNode rootNode = loadManifestJson();
        if (rootNode == null) {
            log.warn("No audio-manifest.json found on classpath or filesystem. Defaulting to empty manifest.");
            this.topLevelMode = "MISSING";
            this.releaseTag = "MISSING";
            return;
        }

        if (rootNode.hasNonNull("manifestVersion")) {
            this.manifestVersion = rootNode.get("manifestVersion").asText();
        }
        if (rootNode.hasNonNull("releaseTag")) {
            this.releaseTag = rootNode.get("releaseTag").asText();
        } else {
            this.releaseTag = "UNTAGGED";
        }
        if (rootNode.hasNonNull("mode")) {
            this.topLevelMode = rootNode.get("mode").asText();
        }

        if (rootNode.has("assets") && rootNode.get("assets").isObject()) {
            JsonNode assetsNode = rootNode.get("assets");
            assetsNode.fields().forEachRemaining(entry -> {
                try {
                    UUID contentId = UUID.fromString(entry.getKey());
                    JsonNode aNode = entry.getValue();

                    String contentType = aNode.hasNonNull("contentType") ? aNode.get("contentType").asText() : "UNKNOWN";
                    String title = aNode.hasNonNull("title") ? aNode.get("title").asText() : "";
                    String storageKey = aNode.hasNonNull("storageKey") ? aNode.get("storageKey").asText() : "";
                    String fileName = aNode.hasNonNull("fileName") ? aNode.get("fileName").asText() : extractFileName(storageKey);
                    String sha256 = aNode.hasNonNull("sha256") ? aNode.get("sha256").asText() : "";
                    String mimeType = aNode.hasNonNull("mimeType") ? aNode.get("mimeType").asText() : "audio/wav";
                    Long fileSizeBytes = aNode.hasNonNull("fileSizeBytes") ? aNode.get("fileSizeBytes").asLong() : null;
                    Double durationSeconds = aNode.hasNonNull("durationSeconds") ? aNode.get("durationSeconds").asDouble() : null;
                    String transcriptSha256 = aNode.hasNonNull("transcriptSha256") ? aNode.get("transcriptSha256").asText() : "";
                    String accent = aNode.hasNonNull("accent") ? aNode.get("accent").asText() : "en-US";
                    String provider = aNode.hasNonNull("provider") ? aNode.get("provider").asText() : "UNKNOWN";
                    String voiceId = aNode.hasNonNull("voiceId") ? aNode.get("voiceId").asText() : "";
                    String provenance = aNode.hasNonNull("provenance") ? aNode.get("provenance").asText() : "";
                    String licenseEvidenceRef = aNode.hasNonNull("licenseEvidenceRef") ? aNode.get("licenseEvidenceRef").asText() : "";
                    boolean distributionApproved = aNode.hasNonNull("distributionApproved") && aNode.get("distributionApproved").asBoolean();
                    boolean humanQaVerified = aNode.hasNonNull("humanQaVerified") && aNode.get("humanQaVerified").asBoolean();
                    String reviewedAt = aNode.hasNonNull("reviewedAt") ? aNode.get("reviewedAt").asText() : null;

                    AudioReadinessStatus readinessStatus;
                    if (aNode.hasNonNull("readinessStatus")) {
                        try {
                            readinessStatus = AudioReadinessStatus.valueOf(aNode.get("readinessStatus").asText());
                        } catch (Exception ex) {
                            readinessStatus = AudioReadinessStatus.MISSING;
                        }
                    } else {
                        readinessStatus = AudioReadinessStatus.LOCAL_SPOKEN;
                    }

                    AudioManifestAsset asset = new AudioManifestAsset(
                        contentId, contentType, title, storageKey, fileName, sha256, mimeType,
                        fileSizeBytes, durationSeconds, transcriptSha256, accent, provider,
                        voiceId, provenance, licenseEvidenceRef, distributionApproved,
                        humanQaVerified, reviewedAt, readinessStatus
                    );

                    assetsByContentId.put(contentId, asset);
                    if (!storageKey.isBlank()) {
                        assetsByStorageKey.put(storageKey, asset);
                    }
                    if (!fileName.isBlank()) {
                        assetsByFileName.put(fileName, asset);
                    }
                } catch (Exception ex) {
                    log.error("Failed to parse manifest asset entry for key: {}", entry.getKey(), ex);
                }
            });
            log.info("Loaded Audio Manifest v{} with {} registered assets (productionMode={})",
                manifestVersion, assetsByContentId.size(), productionMode);
        } else {
            log.info("Loaded legacy Audio Manifest v{} (mode={}, productionMode={})",
                manifestVersion, topLevelMode, productionMode);
        }
    }

    public AudioReadinessStatus getAudioReadiness(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return AudioReadinessStatus.MISSING;
        }

        String fileName = extractFileName(storageKey);
        boolean exists = checkFileExists(fileName);
        if (!exists) {
            return AudioReadinessStatus.MISSING;
        }

        Optional<AudioManifestAsset> assetOpt = getAssetByStorageKey(storageKey);
        if (assetOpt.isPresent()) {
            return assetOpt.get().readinessStatus();
        }

        if (productionMode || "MISSING".equalsIgnoreCase(topLevelMode)) {
            return AudioReadinessStatus.MISSING;
        }

        // Fallback for legacy v1 manifests in dev mode
        if ("SYNTHETIC_TEST_FIXTURE".equalsIgnoreCase(topLevelMode)) {
            return AudioReadinessStatus.SYNTHETIC_FIXTURE;
        } else if ("LICENSED_DISTRIBUTABLE".equalsIgnoreCase(topLevelMode)) {
            return AudioReadinessStatus.LICENSED_DISTRIBUTABLE;
        } else {
            return AudioReadinessStatus.LOCAL_SPOKEN;
        }
    }

    public boolean isAudioAvailable(String storageKey) {
        return isAudioAvailable(storageKey, null);
    }

    public boolean isAudioAvailable(String storageKey, String canonicalTranscript) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }

        AudioReadinessStatus readiness = getAudioReadiness(storageKey);
        if (readiness == AudioReadinessStatus.MISSING) {
            return false;
        }

        if (productionMode) {
            Optional<AudioManifestAsset> assetOpt = getAssetByStorageKey(storageKey);
            if (assetOpt.isEmpty()) {
                return false;
            }
            AudioManifestAsset asset = assetOpt.get();
            if (!asset.isApprovedForProduction()) {
                return false;
            }
            if (canonicalTranscript != null && !canonicalTranscript.isBlank()) {
                if (!asset.matchesCanonicalTranscript(canonicalTranscript)) {
                    log.warn("Production audio rejected for {}: transcript SHA-256 mismatch against canonical transcript", storageKey);
                    return false;
                }
            }
            if (!verifyAssetSha256(asset)) {
                log.warn("Production audio rejected for {}: SHA-256 hash mismatch between file on disk and manifest", storageKey);
                return false;
            }
            return true;
        }

        return true;
    }

    public boolean isSpokenSpeech(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }

        if (productionMode) {
            Optional<AudioManifestAsset> assetOpt = getAssetByStorageKey(storageKey);
            return assetOpt.isPresent() && assetOpt.get().isApprovedForProduction();
        }

        AudioReadinessStatus readiness = getAudioReadiness(storageKey);
        return readiness.isSpokenSpeech();
    }

    public boolean isDistributionApproved(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        return getAssetByStorageKey(storageKey)
            .map(AudioManifestAsset::distributionApproved)
            .orElse(false);
    }

    public Optional<AudioManifestAsset> getAsset(UUID contentId) {
        if (contentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(assetsByContentId.get(contentId));
    }

    public Optional<AudioManifestAsset> getAssetByStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return Optional.empty();
        }
        AudioManifestAsset asset = assetsByStorageKey.get(storageKey);
        if (asset != null) {
            return Optional.of(asset);
        }
        String fileName = extractFileName(storageKey);
        return Optional.ofNullable(assetsByFileName.get(fileName));
    }

    public boolean checkFileExists(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }

        // 1. If storage-root runtime volume is configured, check inside boundCuratedDir or storageRoot/curated/
        if (boundCuratedDir != null && boundCuratedDir.exists()) {
            File f = new File(boundCuratedDir, fileName);
            if (f.exists() && f.length() > 0) {
                return true;
            }
            if (productionMode) {
                return false;
            }
        } else if (storageRoot != null && !storageRoot.isBlank()) {
            File f = new File(storageRoot, "curated/" + fileName);
            if (f.exists() && f.length() > 0) {
                return true;
            }
            if (productionMode) {
                // In production mode with configured volume, deny probing dev paths
                return false;
            }
        }

        // 2. Check classpath
        try {
            Resource res = resourceLoader.getResource("classpath:/static/media/audio/curated/" + fileName);
            if (res.exists() && res.contentLength() > 0) {
                return true;
            }
            Resource altRes = resourceLoader.getResource("classpath:/audio/" + fileName);
            if (altRes.exists() && altRes.contentLength() > 0) {
                return true;
            }
        } catch (Exception ignored) {
        }

        // In production mode without volume, only classpath is allowed, never developer source tree
        if (productionMode) {
            return false;
        }

        // 3. Development-only workspace paths (only evaluated in non-production mode)
        String[] probePaths = {
            "backend/src/main/resources/static/media/audio/curated/" + fileName,
            "src/main/resources/static/media/audio/curated/" + fileName,
            "frontend/public/media/audio/curated/" + fileName,
            "../backend/src/main/resources/static/media/audio/curated/" + fileName,
            "../frontend/public/media/audio/curated/" + fileName
        };

        for (String probe : probePaths) {
            File f = new File(probe);
            if (f.exists() && f.length() > 0) {
                return true;
            }
        }
        return false;
    }

    private JsonNode loadManifestJson() {
        // 1. Check storageRoot runtime volume if configured
        if (storageRoot != null && !storageRoot.isBlank()) {
            try {
                File rootDir = new File(storageRoot).getCanonicalFile();
                File candidateCurated = new File(rootDir, "curated");
                File manifestInCurated = new File(candidateCurated, "audio-manifest.json");
                if (manifestInCurated.exists() && manifestInCurated.length() > 0) {
                    try {
                        this.boundCuratedDir = candidateCurated.getCanonicalFile();
                        byte[] bytes = java.nio.file.Files.readAllBytes(manifestInCurated.toPath());
                        this.manifestSha256 = calculateSha256(bytes);
                        return objectMapper.readTree(bytes);
                    } catch (Exception ex) {
                        log.error("Failed to parse audio-manifest.json from storage-root/curated: {}", ex.getMessage());
                        return null;
                    }
                }
                File manifestInRoot = new File(rootDir, "audio-manifest.json");
                if (manifestInRoot.exists() && manifestInRoot.length() > 0) {
                    try {
                        this.boundCuratedDir = rootDir.getCanonicalFile();
                        byte[] bytes = java.nio.file.Files.readAllBytes(manifestInRoot.toPath());
                        this.manifestSha256 = calculateSha256(bytes);
                        return objectMapper.readTree(bytes);
                    } catch (Exception ex) {
                        log.error("Failed to parse audio-manifest.json from storage-root: {}", ex.getMessage());
                        return null;
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to resolve storage root canonical path: {}", ex.getMessage());
            }
            if (productionMode) {
                log.warn("Storage root configured ({}) but no audio-manifest.json found. Fail-closed production rejects dev fallback.", storageRoot);
                return null;
            }
        }

        // 2. Classpath probes
        String[] cpLocations = {
            "classpath:/static/media/audio/curated/audio-manifest.json",
            "classpath:/audio/audio-manifest.json"
        };
        for (String loc : cpLocations) {
            try {
                Resource res = resourceLoader.getResource(loc);
                if (res.exists()) {
                    try (InputStream is = res.getInputStream()) {
                        byte[] bytes = is.readAllBytes();
                        this.manifestSha256 = calculateSha256(bytes);
                        this.boundCuratedDir = null;
                        return objectMapper.readTree(bytes);
                    } catch (Exception ex) {
                        log.error("Failed to parse audio-manifest.json from classpath location {}: {}", loc, ex.getMessage());
                        return null;
                    }
                }
            } catch (Exception ex) {
                log.debug("Classpath probe error for {}: {}", loc, ex.getMessage());
            }
        }

        // In production mode without volume, only classpath is allowed, never developer source tree
        if (productionMode) {
            return null;
        }

        // 3. Filesystem probes (development only)
        String[] fsLocations = {
            "backend/src/main/resources/static/media/audio/curated/audio-manifest.json",
            "src/main/resources/static/media/audio/curated/audio-manifest.json",
            "frontend/public/media/audio/curated/audio-manifest.json",
            "../backend/src/main/resources/static/media/audio/curated/audio-manifest.json",
            "../frontend/public/media/audio/curated/audio-manifest.json"
        };
        for (String path : fsLocations) {
            File f = new File(path);
            if (f.exists() && f.length() > 0) {
                try {
                    byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                    this.manifestSha256 = calculateSha256(bytes);
                    this.boundCuratedDir = f.getParentFile().getCanonicalFile();
                    return objectMapper.readTree(bytes);
                } catch (Exception ex) {
                    log.error("Failed to parse audio-manifest.json from file location {}: {}", path, ex.getMessage());
                    return null;
                }
            }
        }

        return null;
    }

    public static String extractFileName(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return "";
        }
        int idx = storageKey.lastIndexOf('/');
        return idx >= 0 ? storageKey.substring(idx + 1) : storageKey;
    }

    public boolean isProductionMode() {
        return productionMode;
    }

    public void setProductionMode(boolean productionMode) {
        this.productionMode = productionMode;
    }

    public boolean isStrictStartupCheck() {
        return strictStartupCheck;
    }

    public void setStrictStartupCheck(boolean strictStartupCheck) {
        this.strictStartupCheck = strictStartupCheck;
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public File getBoundCuratedDir() {
        return boundCuratedDir;
    }

    public String getReleaseTag() {
        return releaseTag;
    }

    public String getManifestSha256() {
        return manifestSha256;
    }

    public String getLoadedAt() {
        return loadedAt;
    }

    private static String calculateSha256(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return "";
        }
    }

    public Map<UUID, AudioManifestAsset> getAssets() {
        return Collections.unmodifiableMap(assetsByContentId);
    }

    public synchronized void resetEmptyForTesting() {
        assetsByContentId.clear();
        assetsByStorageKey.clear();
        assetsByFileName.clear();
        this.verifiedShaCache.clear();
        this.topLevelMode = "MISSING";
        this.releaseTag = "MISSING";
        this.manifestSha256 = null;
        this.boundCuratedDir = null;
    }

    public boolean verifyAssetSha256(AudioManifestAsset asset) {
        if (asset == null || asset.sha256() == null || asset.sha256().isBlank()) {
            return false;
        }
        String cacheKey = asset.fileName();
        if (Boolean.TRUE.equals(verifiedShaCache.get(cacheKey))) {
            return true;
        }

        String calculatedSha = calculateAssetSha256(asset.fileName());
        if (calculatedSha == null) {
            log.warn("verifyAssetSha256: Could not calculate SHA-256 for file '{}' (file missing or unreadable)", asset.fileName());
            return false;
        }

        boolean matches = asset.sha256().trim().equalsIgnoreCase(calculatedSha);
        if (matches) {
            verifiedShaCache.put(cacheKey, true);
            return true;
        } else {
            log.error("verifyAssetSha256: Hash mismatch for '{}'! Expected: {}, Found: {}",
                asset.fileName(), asset.sha256().trim().toLowerCase(), calculatedSha);
            return false;
        }
    }

    public String calculateAssetSha256(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        // 1. Storage root / bound curated directory
        if (boundCuratedDir != null && boundCuratedDir.exists()) {
            File f = new File(boundCuratedDir, fileName);
            if (f.exists() && f.isFile()) {
                return hashFile(f);
            }
            if (productionMode) {
                return null;
            }
        } else if (storageRoot != null && !storageRoot.isBlank()) {
            File f = new File(storageRoot, "curated/" + fileName);
            if (f.exists() && f.isFile()) {
                return hashFile(f);
            }
            if (productionMode) {
                return null;
            }
        }

        // 2. Classpath check
        try {
            Resource res = resourceLoader.getResource("classpath:/static/media/audio/curated/" + fileName);
            if (res.exists() && res.isReadable()) {
                try (InputStream is = res.getInputStream()) {
                    return hashInputStream(is);
                }
            }
            Resource altRes = resourceLoader.getResource("classpath:/audio/" + fileName);
            if (altRes.exists() && altRes.isReadable()) {
                try (InputStream is = altRes.getInputStream()) {
                    return hashInputStream(is);
                }
            }
        } catch (Exception ignored) {
        }

        if (productionMode) {
            return null;
        }

        // 3. Dev filesystem probe
        String[] probePaths = {
            "backend/src/main/resources/static/media/audio/curated/" + fileName,
            "src/main/resources/static/media/audio/curated/" + fileName,
            "frontend/public/media/audio/curated/" + fileName,
            "../backend/src/main/resources/static/media/audio/curated/" + fileName,
            "../frontend/public/media/audio/curated/" + fileName
        };
        for (String probe : probePaths) {
            File f = new File(probe);
            if (f.exists() && f.isFile()) {
                return hashFile(f);
            }
        }

        return null;
    }

    private String hashFile(File file) {
        try (InputStream is = Files.newInputStream(file.toPath())) {
            return hashInputStream(is);
        } catch (Exception ex) {
            log.error("Failed to hash file {}: {}", file.getAbsolutePath(), ex.getMessage());
            return null;
        }
    }

    private String hashInputStream(InputStream is) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception ex) {
            log.error("Failed to calculate SHA-256 on stream: {}", ex.getMessage());
            return null;
        }
    }
}
