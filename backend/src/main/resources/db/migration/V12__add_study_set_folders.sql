-- M3-EVO-3B: Optional, same-owner organization above existing Collections.
CREATE TABLE folders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(100) COLLATE "C" NOT NULL,
    description VARCHAR(1000),
    name_key VARCHAR(100) COLLATE "C" GENERATED ALWAYS AS
        (translate(name, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_folder_id_user UNIQUE (id, user_id),
    CONSTRAINT uq_folder_user_name UNIQUE (user_id, name_key),
    CONSTRAINT chk_folder_name CHECK (
        char_length(name) > 0
        AND name = btrim(regexp_replace(name COLLATE "C", '[[:space:]]+', ' ', 'g'))
    )
);
-- uq_folder_user_name also supports owner-scoped listing.
ALTER TABLE collections ADD COLUMN folder_id UUID;
ALTER TABLE collections ADD CONSTRAINT fk_collection_folder_owner
    FOREIGN KEY (folder_id, user_id) REFERENCES folders(id, user_id);
-- No cascading delete or composite SET NULL: service unassigns before deleting.
CREATE INDEX idx_collections_user_folder ON collections(user_id, folder_id);
