-- NULL distinguishes legacy accounts from an explicitly selected preference.
ALTER TABLE app_users ADD COLUMN preferred_locale VARCHAR(16);
ALTER TABLE app_users ADD CONSTRAINT chk_user_preferred_locale
    CHECK (preferred_locale IS NULL OR preferred_locale IN ('vi-VN', 'en'));
