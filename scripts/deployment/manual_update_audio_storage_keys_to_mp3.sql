-- ==============================================================================
-- EnglishFlow Manual Deployment Script: Migrate Audio Storage Keys to MP3
-- ==============================================================================
-- AUDIENCE: Database Administrator / Production Release Engineer
-- CLASSIFICATION: OPTIONAL MANUAL DEPLOYMENT PROCEDURE (NOT A FLYWAY MIGRATION)
--
-- ARCHITECTURAL CONTEXT:
-- In EnglishFlow, audio playback URLs are dynamically resolved from the active,
-- immutable runtime manifest (audio-manifest.json) via AudioManifestService.
-- Therefore, modifying persistent database columns is NOT required for runtime
-- audio playback. This manual script is provided solely for database storage-key
-- hygiene during major milestone deployments.
--
-- PREREQUISITES FOR MANUAL EXECUTION:
--   1. All 10 production MP3 audio assets have been acquired and verified.
--   2. All 10 licensing dossiers are marked APPROVED with valid legal references.
--   3. Production release has been staged and activated via promote-audio-release.py.
--   4. DO NOT EXECUTE in local development environments holding only WAV assets.
--
-- GUARANTEES:
--   - 100% idempotent (safe to run multiple times).
--   - Preflight assertion verifies all 10 canonical UUIDs before mutation.
--   - Zero impact on dictation_attempts, shadowing_takes, or learning_activity_events.
-- ==============================================================================

DO $$
DECLARE
    matched_count INT;
    expected_ids UUID[] := ARRAY[
        'a1000000-0000-0000-0000-000000000001'::uuid,
        'a2000000-0000-0000-0000-000000000002'::uuid,
        'b1000000-0000-0000-0000-000000000015'::uuid,
        'b2000000-0000-0000-0000-000000000042'::uuid,
        'c1000000-0000-0000-0000-000000000088'::uuid,
        'a1000000-0000-0000-0001-000000000001'::uuid,
        'a2000000-0000-0000-0001-000000000002'::uuid,
        'b1000000-0000-0000-0001-000000000015'::uuid,
        'b2000000-0000-0000-0001-000000000042'::uuid,
        'c1000000-0000-0000-0001-000000000088'::uuid
    ];
BEGIN
    -- 1. Preflight check: verify all 10 canonical content UUIDs exist
    SELECT COUNT(*) INTO matched_count
    FROM learning_contents
    WHERE id = ANY(expected_ids);

    IF matched_count != 10 THEN
        RAISE EXCEPTION 'Preflight verification failed: expected 10 canonical learning_contents matching authorative UUIDs, but found %', matched_count;
    END IF;

    -- 2. Idempotent update: replace .wav with .mp3
    UPDATE learning_contents
    SET audio_storage_key = REPLACE(audio_storage_key, '.wav', '.mp3'),
        audio_mime_type = 'audio/mpeg',
        updated_at = NOW()
    WHERE id = ANY(expected_ids)
      AND audio_storage_key LIKE '%.wav';

    RAISE NOTICE 'Manual deployment update completed: verified % canonical records; storage keys set to MP3.', matched_count;
END $$;
