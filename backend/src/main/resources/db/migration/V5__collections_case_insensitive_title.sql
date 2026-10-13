-- ==============================================================================
-- Flyway Migration V5: Enforce Case-Insensitive Uniqueness on Collection Titles
-- Resolves M2-T01 regression audit: Prevents race conditions under concurrent
-- collection creation with differing letter casing.
-- ==============================================================================

ALTER TABLE collections DROP CONSTRAINT IF EXISTS uq_collection_user_title;

CREATE UNIQUE INDEX IF NOT EXISTS uq_collection_user_lower_title
    ON collections (user_id, LOWER(title));
