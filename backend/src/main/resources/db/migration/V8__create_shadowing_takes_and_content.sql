-- ==============================================================================
-- Flyway Migration V8: Create Shadowing Takes and Seed Shadowing Learning Contents
-- Milestone: M2-T04 Speech Shadowing Full-Stack Vertical Slice
-- Canonical reference: docs/architecture/DATABASE.md Section 3.6 & API_CONTRACT.md Section 7
-- ==============================================================================

-- 1. Table: shadowing_takes (user speech practice takes, self-ratings, optional audio metadata)
CREATE TABLE shadowing_takes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES learning_contents(id) ON DELETE RESTRICT,
    attempt_id UUID NOT NULL,
    self_rating VARCHAR(32) NOT NULL, -- NEEDS_WORK, ACCEPTABLE, GOOD, FLUENT
    recording_storage_key VARCHAR(255), -- NULLABLE: supports browser-only ephemeral practice
    recording_duration_seconds NUMERIC(6, 2),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shadowing_user_attempt UNIQUE (user_id, attempt_id),
    CONSTRAINT chk_shadowing_rating CHECK (self_rating IN ('NEEDS_WORK', 'ACCEPTABLE', 'GOOD', 'FLUENT'))
);

CREATE INDEX idx_shadowing_user_content ON shadowing_takes (user_id, content_id);
CREATE INDEX idx_shadowing_user_date ON shadowing_takes (user_id, completed_at);

-- 2. Seed curated starter dataset for Speech Shadowing practice
INSERT INTO learning_contents (
    id, content_type, title, cefr_level, topic_tag, transcript_text,
    audio_storage_key, audio_duration_seconds, audio_mime_type, audio_file_size_bytes,
    source_attribution, is_published, created_at, updated_at
) VALUES
(
    'a1000000-0000-0000-0001-000000000001',
    'SHADOWING',
    'Morning Greetings',
    'A1',
    'Daily Life',
    'Good morning, how are you doing today?',
    '/api/v1/media/audio/curated/shadowing-a1-001.wav',
    2.50,
    'audio/wav',
    115000,
    'EnglishFlow Native Speech Corpus',
    TRUE,
    NOW(),
    NOW()
),
(
    'a2000000-0000-0000-0001-000000000002',
    'SHADOWING',
    'Asking for Directions',
    'A2',
    'Travel',
    'Could you please tell me the way to the train station?',
    '/api/v1/media/audio/curated/shadowing-a2-002.wav',
    3.10,
    'audio/wav',
    142000,
    'EnglishFlow Native Speech Corpus',
    TRUE,
    NOW(),
    NOW()
),
(
    'b1000000-0000-0000-0001-000000000015',
    'SHADOWING',
    'Building Confidence',
    'B1',
    'Fluency',
    'Practicing every day helps build natural speaking rhythm and confidence.',
    '/api/v1/media/audio/curated/shadowing-b1-015.wav',
    4.20,
    'audio/wav',
    190000,
    'EnglishFlow Native Speech Corpus',
    TRUE,
    NOW(),
    NOW()
),
(
    'b2000000-0000-0000-0001-000000000042',
    'SHADOWING',
    'Connection Over Perfection',
    'B2',
    'Communication',
    'Fluency is not about perfection, but connection and rhythm.',
    '/api/v1/media/audio/curated/shadowing-b2-042.wav',
    3.80,
    'audio/wav',
    172000,
    'EnglishFlow Native Speech Corpus',
    TRUE,
    NOW(),
    NOW()
),
(
    'c1000000-0000-0000-0001-000000000088',
    'SHADOWING',
    'Articulate Cadence',
    'C1',
    'Rhetoric',
    'Articulating complex ideas clearly requires deliberate practice and thoughtful cadence.',
    '/api/v1/media/audio/curated/shadowing-c1-088.wav',
    5.10,
    'audio/wav',
    230000,
    'EnglishFlow Native Speech Corpus',
    TRUE,
    NOW(),
    NOW()
);
