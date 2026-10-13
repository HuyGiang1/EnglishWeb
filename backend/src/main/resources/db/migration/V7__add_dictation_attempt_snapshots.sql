-- ==============================================================================
-- Flyway Migration V7: Add Evaluation Snapshot Columns to Dictation Attempts
-- Milestone: M2-T03 Daily Dictation Vertical Slice (Release Readiness Gate)
-- Canonical reference: docs/architecture/DATABASE.md & DATA_LIFECYCLE.md
-- ==============================================================================

ALTER TABLE dictation_attempts
    ADD COLUMN canonical_transcript_snapshot TEXT,
    ADD COLUMN diff_tokens JSONB;

COMMENT ON COLUMN dictation_attempts.canonical_transcript_snapshot IS
    'Frozen canonical reference transcript at the exact time of evaluation';

COMMENT ON COLUMN dictation_attempts.diff_tokens IS
    'Serialized JSONB array of DiffToken objects generated at evaluation time';
