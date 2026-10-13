-- ==============================================================================
-- Flyway Migration V6: Create Learning Contents and Dictation Attempts
-- Milestone: M2-T03 Daily Dictation Vertical Slice
-- Canonical reference: docs/architecture/DATABASE.md & API_CONTRACT.md
-- ==============================================================================

-- 1. Table: learning_contents (curated bank of native sentences, audio clips, exercises)
CREATE TABLE learning_contents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type VARCHAR(32) NOT NULL, -- DICTATION, SHADOWING, QUIZ
    title VARCHAR(150) NOT NULL,
    cefr_level VARCHAR(8) NOT NULL,
    topic_tag VARCHAR(64) NOT NULL,
    transcript_text TEXT NOT NULL,
    transcript_segments JSONB,
    audio_storage_key VARCHAR(255),
    audio_duration_seconds NUMERIC(6, 2),
    audio_mime_type VARCHAR(64),
    audio_file_size_bytes BIGINT,
    source_attribution VARCHAR(255),
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_content_type CHECK (content_type IN ('DICTATION', 'SHADOWING', 'QUIZ')),
    CONSTRAINT chk_content_cefr CHECK (cefr_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'))
);

CREATE INDEX idx_content_type_level ON learning_contents (content_type, cefr_level) WHERE is_published = TRUE;

-- 2. Table: dictation_attempts (user transcription submissions, accuracy scores, evaluation metadata)
CREATE TABLE dictation_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES learning_contents(id) ON DELETE RESTRICT,
    attempt_id UUID NOT NULL,
    submitted_transcript TEXT NOT NULL,
    accuracy_score NUMERIC(5, 2) NOT NULL, -- 0.00 to 100.00%
    word_count INT NOT NULL,
    error_count INT NOT NULL,
    duration_seconds INT DEFAULT 0,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dictation_user_attempt UNIQUE (user_id, attempt_id),
    CONSTRAINT chk_dictation_accuracy CHECK (accuracy_score >= 0.00 AND accuracy_score <= 100.00)
);

CREATE INDEX idx_dictation_user_content ON dictation_attempts (user_id, content_id);
CREATE INDEX idx_dictation_user_date ON dictation_attempts (user_id, completed_at);

-- 3. Seed authoritative starter dataset with original project-owned audio assets
INSERT INTO learning_contents (
    id, content_type, title, cefr_level, topic_tag, transcript_text,
    audio_storage_key, audio_duration_seconds, audio_mime_type, audio_file_size_bytes,
    source_attribution, is_published, created_at, updated_at
) VALUES
(
    'a1000000-0000-0000-0000-000000000001',
    'DICTATION',
    'Daily Routine',
    'A1',
    'Daily Life',
    'I drink a cup of coffee every morning before work.',
    '/api/v1/media/audio/curated/dictation-a1-001.wav',
    2.71,
    'audio/wav',
    124000,
    'EnglishFlow Native Audio Corpus',
    TRUE,
    NOW(),
    NOW()
),
(
    'a2000000-0000-0000-0000-000000000002',
    'DICTATION',
    'Weekend Plans',
    'A2',
    'Leisure',
    'We are going to visit the modern art museum on Saturday afternoon.',
    '/api/v1/media/audio/curated/dictation-a2-002.wav',
    3.59,
    'audio/wav',
    163000,
    'EnglishFlow Native Audio Corpus',
    TRUE,
    NOW(),
    NOW()
),
(
    'b1000000-0000-0000-0000-000000000015',
    'DICTATION',
    'Technology in Education',
    'B1',
    'Education',
    'Online learning platforms allow students to study at their own pace.',
    '/api/v1/media/audio/curated/dictation-b1-015.wav',
    3.76,
    'audio/wav',
    170000,
    'EnglishFlow Native Audio Corpus',
    TRUE,
    NOW(),
    NOW()
),
(
    'b2000000-0000-0000-0000-000000000042',
    'DICTATION',
    'Economic Resilience',
    'B2',
    'Economics',
    'The country showed great economic resilience during the crisis.',
    '/api/v1/media/audio/curated/dictation-b2-042.wav',
    3.44,
    'audio/wav',
    156000,
    'EnglishFlow Native Audio Corpus',
    TRUE,
    NOW(),
    NOW()
),
(
    'c1000000-0000-0000-0000-000000000088',
    'DICTATION',
    'Scientific Methodology',
    'C1',
    'Science',
    'Empirical evidence is fundamental to formulating hypotheses and drawing rigorous conclusions.',
    '/api/v1/media/audio/curated/dictation-c1-088.wav',
    5.33,
    'audio/wav',
    239000,
    'EnglishFlow Native Audio Corpus',
    TRUE,
    NOW(),
    NOW()
);
