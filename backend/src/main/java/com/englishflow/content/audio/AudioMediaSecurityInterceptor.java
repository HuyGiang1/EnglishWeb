package com.englishflow.content.audio;

import com.englishflow.shared.error.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Component
public class AudioMediaSecurityInterceptor implements HandlerInterceptor {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".wav", ".mp3", ".m4a");
    private final AudioManifestService audioManifestService;
    private final ObjectMapper objectMapper;

    public AudioMediaSecurityInterceptor(AudioManifestService audioManifestService, ObjectMapper objectMapper) {
        this.audioManifestService = audioManifestService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();

        // 1. Method guard: only GET and HEAD permitted
        if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)) {
            writeError(response, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method " + method + " not allowed on media endpoint");
            return false;
        }

        String uri = request.getRequestURI();

        // 2. Path traversal guard
        if (hasPathTraversal(uri)) {
            writeError(response, HttpStatus.BAD_REQUEST, "INVALID_MEDIA_PATH", "Path traversal characters are strictly prohibited");
            return false;
        }

        // 3. File extension guard (must be legitimate audio file, not json/yml/class)
        String fileName = AudioManifestService.extractFileName(uri);
        String lowerName = fileName.toLowerCase();
        boolean hasAllowedExt = ALLOWED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
        if (!hasAllowedExt) {
            writeError(response, HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Audio resource not found");
            return false;
        }

        // 4. Canonical path and symlink escape guard (when storage-root is configured)
        String storageRoot = audioManifestService.getStorageRoot();
        java.io.File boundDir = audioManifestService.getBoundCuratedDir();
        java.io.File rootDir = null;
        if (storageRoot != null && !storageRoot.isBlank()) {
            try {
                rootDir = new java.io.File(storageRoot).getCanonicalFile();
                java.io.File targetFile = (boundDir != null && boundDir.exists())
                    ? new java.io.File(boundDir, fileName).getCanonicalFile()
                    : new java.io.File(rootDir, "curated/" + fileName).getCanonicalFile();
                if (targetFile.exists()) {
                    if (!targetFile.toPath().startsWith(rootDir.toPath())) {
                        writeError(response, HttpStatus.FORBIDDEN, "SYMLINK_ESCAPE_DETECTED", "Symlink escapes configured media storage root");
                        return false;
                    }
                }
            } catch (IOException ex) {
                writeError(response, HttpStatus.BAD_REQUEST, "INVALID_MEDIA_PATH", "Failed to resolve canonical path");
                return false;
            }
        }

        // 5. Production gate: reject unapproved / synthetic / local macOS dev audio
        if (audioManifestService.isProductionMode()) {
            Optional<AudioManifestAsset> assetOpt = audioManifestService.getAssetByStorageKey(uri);
            if (assetOpt.isEmpty()) {
                writeError(response, HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Audio asset is not registered in production manifest");
                return false;
            }

            AudioManifestAsset asset = assetOpt.get();
            if (!asset.isApprovedForProduction()) {
                writeError(
                    response,
                    HttpStatus.FORBIDDEN,
                    "AUDIO_UNAPPROVED_FOR_PRODUCTION",
                    "Audio asset '" + fileName + "' is not approved for production distribution (readiness=" + asset.readinessStatus() + ")"
                );
                return false;
            }

            // Integrity check: verify file length matches manifest
            java.io.File targetFile = (boundDir != null && boundDir.exists())
                ? new java.io.File(boundDir, fileName)
                : (rootDir != null ? new java.io.File(rootDir, "curated/" + fileName) : null);
            if (targetFile != null && targetFile.exists() && asset.fileSizeBytes() != null && asset.fileSizeBytes() > 0) {
                if (targetFile.length() != asset.fileSizeBytes()) {
                    writeError(
                        response,
                        HttpStatus.FORBIDDEN,
                        "MEDIA_INTEGRITY_COMPROMISED",
                        "Audio asset file size mismatch against manifest"
                    );
                    return false;
                }
            }

            // Cryptographic integrity check: verify SHA-256 digest against manifest
            if (!audioManifestService.verifyAssetSha256(asset)) {
                writeError(
                    response,
                    HttpStatus.FORBIDDEN,
                    "MEDIA_INTEGRITY_COMPROMISED",
                    "Audio asset cryptographic SHA-256 mismatch against manifest"
                );
                return false;
            }
        }

        return true;
    }

    private boolean hasPathTraversal(String uri) {
        if (uri == null) {
            return false;
        }
        String lower = uri.toLowerCase();
        return lower.contains("..")
            || lower.contains("//")
            || lower.contains("\\")
            || lower.contains("%2e")
            || lower.contains("%2f")
            || lower.contains("%5c");
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse error = ApiErrorResponse.of(code, message, status.value());
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
