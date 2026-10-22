package com.englishflow.content.audio;

import com.englishflow.dictation.LearningContent;
import com.englishflow.dictation.LearningContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AudioProductionReadinessGuard implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AudioProductionReadinessGuard.class);

    private final AudioManifestService audioManifestService;
    private final LearningContentRepository learningContentRepository;

    public AudioProductionReadinessGuard(
        AudioManifestService audioManifestService,
        LearningContentRepository learningContentRepository
    ) {
        this.audioManifestService = audioManifestService;
        this.learningContentRepository = learningContentRepository;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!audioManifestService.isProductionMode()) {
            log.info("AudioProductionReadinessGuard: Running in DEVELOPMENT mode. Local speech and fixtures are permitted.");
            return;
        }

        log.info("AudioProductionReadinessGuard: Running in PRODUCTION mode. Enforcing strict licensing and QA gates.");

        // 1. Verify cryptographic SHA-256 integrity for every approved runtime asset in the manifest
        List<String> corruptedApprovedAssets = new ArrayList<>();
        for (AudioManifestAsset asset : audioManifestService.getAssets().values()) {
            if (asset.isApprovedForProduction()) {
                if (!audioManifestService.verifyAssetSha256(asset)) {
                    corruptedApprovedAssets.add(String.format("Asset '%s' (storageKey=%s, expectedSha256=%s)",
                        asset.fileName(), asset.storageKey(), asset.sha256()));
                }
            }
        }

        if (!corruptedApprovedAssets.isEmpty()) {
            String corruptionSummary = String.format(
                "CRITICAL INTEGRITY FAILURE: %d approved production asset(s) failed cryptographic SHA-256 verification on disk:\n - %s",
                corruptedApprovedAssets.size(), String.join("\n - ", corruptedApprovedAssets)
            );
            log.error(corruptionSummary);
            throw new IllegalStateException("Production startup aborted: Approved asset SHA-256 integrity validation failure on runtime media volume.\n" + corruptionSummary);
        }

        // 2. Validate published learning content against approved audio assets
        List<LearningContent> publishedAudioContents = learningContentRepository.findAll().stream()
            .filter(LearningContent::isPublished)
            .filter(c -> c.getAudioStorageKey() != null && !c.getAudioStorageKey().isBlank())
            .toList();

        List<String> unapprovedExercises = new ArrayList<>();

        for (LearningContent content : publishedAudioContents) {
            String storageKey = content.getAudioStorageKey();
            boolean isAvailable = audioManifestService.isAudioAvailable(storageKey, content.getTranscriptText());
            if (!isAvailable) {
                AudioReadinessStatus status = audioManifestService.getAudioReadiness(storageKey);
                unapprovedExercises.add(String.format("Exercise [%s - %s] '%s' (storageKey=%s, readiness=%s)",
                    content.getContentType(), content.getCefrLevel(), content.getTitle(), storageKey, status));
            }
        }

        if (!unapprovedExercises.isEmpty()) {
            String summary = String.format(
                "PRODUCTION AUDIO SAFETY WARNING: %d published exercise(s) lack approved production audio:\n - %s",
                unapprovedExercises.size(), String.join("\n - ", unapprovedExercises)
            );

            if (audioManifestService.isStrictStartupCheck()) {
                log.error("CRITICAL INTEGRITY FAILURE: Strict startup check failed. Blocking startup.\n{}", summary);
                throw new IllegalStateException("Production startup aborted: Unapproved audio assets detected in published exercises.\n" + summary);
            } else {
                log.warn("{}\nGraceful Feature Degradation active: Affected audio exercises will be marked audio-unavailable for learner access; unrelated modules remain operational.", summary);
            }
        } else {
            log.info("AudioProductionReadinessGuard: All {} published audio exercises satisfy production readiness contracts.",
                publishedAudioContents.size());
        }
    }
}
