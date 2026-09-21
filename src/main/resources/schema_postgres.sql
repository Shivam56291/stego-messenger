-- =====================================================================
-- Secure Stego Messenger — PostgreSQL / Supabase schema
--
-- This schema is safe to execute at every application startup.
-- IDs are UUIDs, timestamps are TIMESTAMPTZ, and quick-login flags are
-- BOOLEANs to match PostgreSQL rather than SQLite's TEXT/INTEGER model.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE IF NOT EXISTS users (
                                     id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   CITEXT NOT NULL UNIQUE,
    display_alias           TEXT,
    password_hash           TEXT NOT NULL,
    public_key              TEXT NOT NULL,
    protected_private_key   TEXT NOT NULL,
    pin_hash                TEXT,
    pin_enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts   INTEGER NOT NULL DEFAULT 0,
    account_locked_until    TIMESTAMPTZ,
    pin_failed_attempts     INTEGER NOT NULL DEFAULT 0,
    pin_locked_until        TIMESTAMPTZ,
    status                  TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','locked','deleted')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
    );
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

CREATE TABLE IF NOT EXISTS sessions (
                                        id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    TEXT NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT FALSE
    );
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_token_hash ON sessions(token_hash);

CREATE TABLE IF NOT EXISTS conversations (
                                             id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_a_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_distinct_participants CHECK (user_a_id <> user_b_id),
    CONSTRAINT uq_conversation_pair UNIQUE (user_a_id, user_b_id)
    );
CREATE INDEX IF NOT EXISTS idx_conversations_user_a ON conversations(user_a_id);
CREATE INDEX IF NOT EXISTS idx_conversations_user_b ON conversations(user_b_id);

CREATE TABLE IF NOT EXISTS messages (
                                        id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    image_path        TEXT NOT NULL,
    payload_bytes     INTEGER NOT NULL CHECK (payload_bytes >= 0),
    image_width       INTEGER NOT NULL CHECK (image_width > 0),
    image_height      INTEGER NOT NULL CHECK (image_height > 0),
    status            TEXT NOT NULL DEFAULT 'sent' CHECK (status IN ('sending','sent','delivered','failed')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at           TIMESTAMPTZ,
    CONSTRAINT chk_distinct_sender_recipient CHECK (sender_id <> recipient_id)
    );
CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_recipient_unread ON messages(recipient_id, read_at);

CREATE TABLE IF NOT EXISTS image_history (
                                             id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_id        UUID REFERENCES messages(id) ON DELETE SET NULL,
    image_name        TEXT NOT NULL,
    width             INTEGER NOT NULL,
    height            INTEGER NOT NULL,
    capacity_bytes    BIGINT NOT NULL,
    payload_bytes     BIGINT NOT NULL,
    recipient_alias   TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
    );
CREATE INDEX IF NOT EXISTS idx_image_history_user ON image_history(user_id, created_at);

CREATE TABLE IF NOT EXISTS security_events (
                                               id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type    TEXT NOT NULL,
    detail        TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
    );
CREATE INDEX IF NOT EXISTS idx_security_events_user ON security_events(user_id, created_at);

-- Defense-in-depth policy retained from the original PostgreSQL design.
-- The current desktop implementation connects with the database owner role,
-- so application-layer authorization checks in the repositories remain the
-- primary authorization boundary. A future API/non-owner role can use this
-- policy with app.current_user_id session context.
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS messages_owner_only ON messages;
CREATE POLICY messages_owner_only ON messages
    USING (
        sender_id = current_setting('app.current_user_id', true)::uuid
        OR recipient_id = current_setting('app.current_user_id', true)::uuid
    );
