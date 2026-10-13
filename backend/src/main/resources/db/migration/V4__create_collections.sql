-- ==============================================================================
-- Flyway Migration V4: Collections & Deck Management
-- Authoritative Spec: docs/architecture/DATABASE.md Section 3.2
-- Multi-Tenant Composite Foreign Key Guarantees
-- ==============================================================================

CREATE TABLE collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_collection_id_user UNIQUE (id, user_id),
    CONSTRAINT uq_collection_user_title UNIQUE (user_id, title)
);

CREATE INDEX idx_collections_user_id ON collections (user_id);

CREATE TABLE vocabulary_collections (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    collection_id UUID NOT NULL,
    vocabulary_id UUID NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (collection_id, vocabulary_id),
    CONSTRAINT fk_vocab_col_collection FOREIGN KEY (collection_id, user_id)
        REFERENCES collections(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_vocab_col_vocabulary FOREIGN KEY (vocabulary_id, user_id)
        REFERENCES vocabularies(id, user_id) ON DELETE CASCADE
);

CREATE INDEX idx_vocab_col_user_vocab ON vocabulary_collections (user_id, vocabulary_id);
