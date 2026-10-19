-- Idempotent safety net for auth_sessions.
-- On some databases an older auth_sessions table existed before migration 001,
-- so CREATE TABLE IF NOT EXISTS skipped it and left it with mismatched columns.
-- This migration adds any missing columns the AuthService.login() INSERT relies on,
-- without dropping or recreating the table, so existing accounts and rows are preserved.

ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS token_hash CHAR(64);
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ;
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE auth_sessions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Link user_id to users(id) if a FK is not already present.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_type = 'FOREIGN KEY'
          AND table_name = 'auth_sessions'
          AND constraint_name = 'auth_sessions_user_fk'
    ) THEN
        ALTER TABLE auth_sessions
            ADD CONSTRAINT auth_sessions_user_fk
            FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE;
    END IF;
END $$;

-- Enforce token uniqueness if a unique constraint is not already present.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_type = 'UNIQUE'
          AND table_name = 'auth_sessions'
          AND constraint_name = 'auth_sessions_token_unique'
    ) THEN
        ALTER TABLE auth_sessions
            ADD CONSTRAINT auth_sessions_token_unique UNIQUE (token_hash);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_sessions_user ON auth_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires ON auth_sessions (expires_at);
