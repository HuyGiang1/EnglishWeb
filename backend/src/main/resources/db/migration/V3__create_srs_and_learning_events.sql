-- EnglishFlow — Migration V3: Spaced Repetition (SRS) & Learning Activity Events
-- Authority: docs/architecture/DATABASE.md Sections 3.3 and 3.8

CREATE TABLE srs_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    vocabulary_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'NEW',
    algorithm VARCHAR(16) NOT NULL DEFAULT 'SM2',
    repetition_count INT NOT NULL DEFAULT 0,
    interval_days INT NOT NULL DEFAULT 0,
    ease_factor NUMERIC(4, 2) NOT NULL DEFAULT 2.50,
    due_date DATE NOT NULL DEFAULT CURRENT_DATE,
    last_reviewed_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fsrs_stability NUMERIC(8, 4),
    fsrs_difficulty NUMERIC(8, 4),
    CONSTRAINT uq_srs_id_user UNIQUE (id, user_id),
    CONSTRAINT uq_srs_user_vocab UNIQUE (user_id, vocabulary_id),
    CONSTRAINT fk_srs_user_vocabulary FOREIGN KEY (vocabulary_id, user_id)
        REFERENCES vocabularies(id, user_id) ON DELETE CASCADE,
    CONSTRAINT chk_srs_state CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'SUSPENDED')),
    CONSTRAINT chk_srs_ease_factor CHECK (ease_factor >= 1.30)
);

CREATE INDEX idx_srs_user_due ON srs_items (user_id, due_date) WHERE state != 'SUSPENDED';
CREATE INDEX idx_srs_user_vocab ON srs_items (user_id, vocabulary_id);

CREATE TABLE srs_review_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    srs_item_id UUID NOT NULL,
    attempt_id UUID NOT NULL,
    grade INT NOT NULL,
    previous_interval_days INT NOT NULL,
    new_interval_days INT NOT NULL,
    previous_ease_factor NUMERIC(4, 2) NOT NULL,
    new_ease_factor NUMERIC(4, 2) NOT NULL,
    duration_seconds INT DEFAULT 0,
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_srs_review_attempt UNIQUE (user_id, attempt_id),
    CONSTRAINT fk_srs_history_item FOREIGN KEY (srs_item_id, user_id)
        REFERENCES srs_items(id, user_id) ON DELETE CASCADE,
    CONSTRAINT chk_srs_history_grade CHECK (grade BETWEEN 1 AND 4)
);

CREATE INDEX idx_srs_history_user_item ON srs_review_history (user_id, srs_item_id);
CREATE INDEX idx_srs_history_reviewed_at ON srs_review_history (user_id, reviewed_at);

CREATE TABLE learning_activity_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL,
    attempt_id UUID NOT NULL,
    completed_at_utc TIMESTAMPTZ NOT NULL,
    timezone_at_event VARCHAR(64) NOT NULL,
    historical_local_date DATE NOT NULL,
    duration_seconds INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_activity_user_attempt UNIQUE (user_id, event_type, attempt_id),
    CONSTRAINT chk_activity_event_type CHECK (event_type IN ('FLASHCARD_REVIEW', 'DICTATION_SENTENCE', 'SHADOWING_TAKE', 'QUIZ_QUESTION'))
);

CREATE INDEX idx_activity_user_local_date ON learning_activity_events (user_id, historical_local_date);
CREATE INDEX idx_activity_user_utc ON learning_activity_events (user_id, completed_at_utc);
