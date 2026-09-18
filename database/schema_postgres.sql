-- =====================================================================
-- Secure Stego Messenger — PostgreSQL / Supabase production schema.
--
-- This is the RECOMMENDED schema for a real deployment (see
-- ARCHITECTURE.md section E/F for the PostgreSQL vs Supabase vs MongoDB
-- comparison). The runnable classroom demo in this repository uses the
-- SQLite-equivalent schema (schema_sqlite.sql) for zero-config local
-- development; this file is what you'd run against Postgres/Supabase
-- to take the same design to production.
--
-- Differences from the SQLite schema:
--  * UUID type + gen_random_uuid() (pgcrypto) instead of TEXT ids.
--  * TIMESTAMPTZ instead of ISO-8601 TEXT.
--  * BOOLEAN instead of INTEGER 0/1 CHECK constraints.
--  * image_path stores an object-storage KEY (e.g. Supabase Storage /
--    S3 object key), not a local filesystem path — see ARCHITECTURE.md
--    section 37 for the storage-lifecycle rationale.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   CITEXT NOT NULL UNIQUE,        -- CITEXT = case-insensitive text (pg extension)
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
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE sessions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    TEXT NOT NULL UNIQUE,        -- SHA-256 of the bearer token; raw token never persisted
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_sessions_user ON sessions(user_id);
CREATE INDEX idx_sessions_token_hash ON sessions(token_hash);

CREATE TABLE conversations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_a_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_distinct_participants CHECK (user_a_id <> user_b_id),
    CONSTRAINT uq_conversation_pair UNIQUE (user_a_id, user_b_id)
);
CREATE INDEX idx_conversations_user_a ON conversations(user_a_id);
CREATE INDEX idx_conversations_user_b ON conversations(user_b_id);

CREATE TABLE messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    image_path        TEXT NOT NULL,   -- object storage key, e.g. "stego/<uuid>.png"
    payload_bytes     INTEGER NOT NULL CHECK (payload_bytes >= 0),
    image_width       INTEGER NOT NULL CHECK (image_width > 0),
    image_height      INTEGER NOT NULL CHECK (image_height > 0),
    status            TEXT NOT NULL DEFAULT 'sent' CHECK (status IN ('sending','sent','delivered','failed')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at           TIMESTAMPTZ,
    CONSTRAINT chk_distinct_sender_recipient CHECK (sender_id <> recipient_id)
);
CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at);
CREATE INDEX idx_messages_recipient_unread ON messages(recipient_id, read_at);

CREATE TABLE image_history (
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
CREATE INDEX idx_image_history_user ON image_history(user_id, created_at);

CREATE TABLE security_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type    TEXT NOT NULL,
    detail        TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_security_events_user ON security_events(user_id, created_at);

-- Row-Level Security (Supabase's headline authorization primitive): even if application
-- code has a bug, Postgres itself refuses cross-user reads at the database layer. This is
-- what section 22 (Authorization) means by "defense in depth" for the IDOR-prevention
-- requirement — belt-and-suspenders on top of the service-layer checks in ChatService.
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
CREATE POLICY messages_owner_only ON messages
    USING (sender_id = current_setting('app.current_user_id')::uuid
           OR recipient_id = current_setting('app.current_user_id')::uuid);
