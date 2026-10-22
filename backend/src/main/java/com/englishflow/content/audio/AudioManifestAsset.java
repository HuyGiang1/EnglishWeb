package com.englishflow.content.audio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public record AudioManifestAsset(
    UUID contentId,
    String contentType,
    String title,
    String storageKey,
    String fileName,
    String sha256,
    String mimeType,
    Long fileSizeBytes,
    Double durationSeconds,
    String transcriptSha256,
    String accent,
    String provider,
    String voiceId,
    String provenance,
    String licenseEvidenceRef,
    boolean distributionApproved,
    boolean humanQaVerified,
    String reviewedAt,
    AudioReadinessStatus readinessStatus
) {
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    public boolean isApprovedForProduction() {
        return distributionApproved
            && humanQaVerified
            && readinessStatus == AudioReadinessStatus.LICENSED_DISTRIBUTABLE
            && licenseEvidenceRef != null && !licenseEvidenceRef.isBlank()
            && !licenseEvidenceRef.contains("LOCAL_DEV_RESTRICTIONS")
            && transcriptSha256 != null && !transcriptSha256.isBlank()
            && sha256 != null && !sha256.isBlank()
            && !EMPTY_SHA256.equalsIgnoreCase(sha256);
    }

    public boolean matchesCanonicalTranscript(String canonicalTranscript) {
        if (canonicalTranscript == null || transcriptSha256 == null || transcriptSha256.isBlank()) {
            return false;
        }
        String computed = computeSha256(canonicalTranscript.strip());
        return transcriptSha256.equalsIgnoreCase(computed);
    }

    public static String computeSha256(String input) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
