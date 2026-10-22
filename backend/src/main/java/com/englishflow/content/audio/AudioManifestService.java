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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioManifestService {

    private static final Logger log = LoggerFactory.getLogger(AudioManifestService.class);

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Value("${englishflow.media.production-mode:false}")
    private boolean productionMode;

    @Value("${englishflow.media.strict-startup-check:false}")
    private boolean strictStartupCheck;

    private String manifestVersion = "1.0.0";
    private String topLevelMode = "LOCAL_SPOKEN";
    private final Map<UUID, AudioManifestAsset> assetsByContentId = new ConcurrentHashMap<>();
    private final Map<String, AudioManifestAsset> assetsByStorageKey = new ConcurrentHashMap<>();
    private final Map<String, AudioManifestAsset> assetsByFileName = new ConcurrentHashMap<>();

    public AudioManifestService(ResourceLoader resourceLoader, ObjectMapper objectMapper, Environment environment) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        boolean isProdProfile = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));

        String explicitMode = environment.getProperty("englishflow.media.production-mode");

        if (isProdProfile) {
            if ("false".equalsIgnoreCase(explicitMode)) {
                throw new IllegalStateException(
                    "FATAL CONFIGURATION ERROR: englishflow.media.production-mode cannot be set to false when running in production profile (prod/production). Security policy requires fail-closed audio validation in production."
                );
            }
            this.productionMode = true;
        }

        reload();
    }

    public synchronized void reload() {
        assetsByContentId.clear();
        assetsByStorageKey.clear();
        assetsByFileName.clear();

        JsonNode rootNode = loadManifestJson();
        if (rootNode == null) {
            log.warn("No audio-manifest.json found on classpath or filesystem. Defaulting to empty manifest.");
            this.topLevelMode = "MISSING";
            return;
        }

        if (rootNode.hasNonNull("manifestVersion")) {
            this.manifestVersion = rootNode.get("manifestVersion").asText();
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
        // 1. Check classpath
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

        // 2. Check local workspace paths
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
        // 1. Classpath probes
        String[] cpLocations = {
            "classpath:/static/media/audio/curated/audio-manifest.json",
            "classpath:/audio/audio-manifest.json"
        };
        for (String loc : cpLocations) {
            try {
                Resource res = resourceLoader.getResource(loc);
                if (res.exists()) {
                    try (InputStream is = res.getInputStream()) {
                        return objectMapper.readTree(is);
                    } catch (Exception ex) {
                        log.error("Failed to parse audio-manifest.json from classpath location {}: {}", loc, ex.getMessage());
                        return null;
                    }
                }
            } catch (Exception ex) {
                log.debug("Classpath probe error for {}: {}", loc, ex.getMessage());
            }
        }

        // 2. Filesystem probes
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
                    return objectMapper.readTree(f);
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

    public Map<UUID, AudioManifestAsset> getAssets() {
        return Collections.unmodifiableMap(assetsByContentId);
    }

    public synchronized void resetEmptyForTesting() {
        assetsByContentId.clear();
        assetsByStorageKey.clear();
        assetsByFileName.clear();
        this.topLevelMode = "MISSING";
    }
}
