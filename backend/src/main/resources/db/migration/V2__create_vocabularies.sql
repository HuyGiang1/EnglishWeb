CREATE TABLE vocabularies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    term VARCHAR(150) NOT NULL,
    normalized_term VARCHAR(150) NOT NULL,
    part_of_speech VARCHAR(32) NOT NULL,
    phonetic_ipa VARCHAR(100),
    vietnamese_meaning TEXT NOT NULL,
    english_definition TEXT,
    example_sentence TEXT,
    example_translation TEXT,
    notes TEXT,
    source VARCHAR(100),
    difficulty_level VARCHAR(8),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_vocab_id_user UNIQUE (id, user_id),
    CONSTRAINT chk_vocab_part_of_speech CHECK (part_of_speech IN ('noun', 'verb', 'adjective', 'adverb', 'idiom', 'phrase', 'preposition', 'conjunction', 'other')),
    CONSTRAINT chk_vocab_difficulty CHECK (difficulty_level IS NULL OR difficulty_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'))
);

CREATE INDEX idx_vocab_user_active ON vocabularies (user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_vocab_user_normalized ON vocabularies (user_id, normalized_term) WHERE deleted_at IS NULL;
CREATE INDEX idx_vocab_search_gin ON vocabularies USING gin (to_tsvector('english', term || ' ' || COALESCE(english_definition, '') || ' ' || COALESCE(vietnamese_meaning, ''))) WHERE deleted_at IS NULL;
