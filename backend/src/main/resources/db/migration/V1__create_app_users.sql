-- EnglishFlow — Migration V1: User Identity & Account Persistence
-- Authority: docs/architecture/DATABASE.md Section 3.1

CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    daily_goal_items INT NOT NULL DEFAULT 20,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_app_users_email UNIQUE (email),
    CONSTRAINT chk_app_users_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_app_users_daily_goal CHECK (daily_goal_items >= 1 AND daily_goal_items <= 500)
);

CREATE INDEX idx_app_users_email ON app_users (email);
