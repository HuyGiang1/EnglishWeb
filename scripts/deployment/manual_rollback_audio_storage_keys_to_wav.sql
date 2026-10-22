-- ==============================================================================
-- EnglishFlow Manual Deployment Rollback Script: Revert Audio Storage Keys to WAV
-- ==============================================================================
-- AUDIENCE: Database Administrator / Production Release Engineer
-- CLASSIFICATION: OPTIONAL MANUAL ROLLBACK PROCEDURE (NOT A FLYWAY MIGRATION)
--
-- PURPOSE:
-- Safely and idempotently reverts learning_contents.audio_storage_key and
-- audio_mime_type from .mp3 back to .wav if an emergency rollback is required.
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

    -- 2. Idempotent rollback: replace .mp3 with .wav
    UPDATE learning_contents
    SET audio_storage_key = REPLACE(audio_storage_key, '.mp3', '.wav'),
        audio_mime_type = 'audio/wav',
        updated_at = NOW()
    WHERE id = ANY(expected_ids)
      AND audio_storage_key LIKE '%.mp3';

    RAISE NOTICE 'Manual deployment rollback completed: verified % canonical records; storage keys reverted to WAV.', matched_count;
END $$;
