-- EnglishFlow — Migration V10: User Preferred Audio Playback Speed
-- Authority: Task M2-T07 (Learner Profile & Settings)

ALTER TABLE app_users
    ADD COLUMN preferred_audio_speed DOUBLE PRECISION NOT NULL DEFAULT 1.0;

ALTER TABLE app_users
    ADD CONSTRAINT chk_app_users_audio_speed CHECK (preferred_audio_speed IN (0.75, 1.0, 1.25));
