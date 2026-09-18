-- =====================================================================
-- Secure Stego Messenger — SQLite schema (used by the runnable student
-- demo; see database/schema_postgres.sql for the production-equivalent
-- schema recommended for a real deployment on PostgreSQL/Supabase).
--
-- Design notes:
--  * No plaintext passwords, PINs, private keys, or message content ever
--    appear in any column — only hashes, encrypted blobs, and ciphertext
--    references.
--  * Foreign keys + CHECK constraints enforce the relational rules the
--    application logic also enforces, so a bug in the Java layer can't
--    silently corrupt referential integrity.
--  * Timestamps are stored as ISO-8601 text (SQLite has no native
--    timestamp type); the Postgres schema uses TIMESTAMPTZ instead.
-- =====================================================================

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
    id                      TEXT PRIMARY KEY,
    email                   TEXT NOT NULL UNIQUE,
    display_alias           TEXT,
    password_hash           TEXT NOT NULL,             -- PBKDF2-encoded string, never plaintext
    public_key              TEXT NOT NULL,              -- base64 X.509 RSA public key
    protected_private_key   TEXT NOT NULL,              -- AES-GCM-encrypted PKCS8 key, password-derived key
    pin_hash                TEXT,                        -- nullable: only set once quick-login is enabled
    pin_enabled             INTEGER NOT NULL DEFAULT 0 CHECK (pin_enabled IN (0,1)),
    failed_login_attempts   INTEGER NOT NULL DEFAULT 0,
    account_locked_until    TEXT,
    pin_failed_attempts     INTEGER NOT NULL DEFAULT 0,
    pin_locked_until        TEXT,
    status                  TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','locked','deleted')),
    created_at              TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

CREATE TABLE IF NOT EXISTS sessions (
    id                TEXT PRIMARY KEY,
    user_id           TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash        TEXT NOT NULL UNIQUE,     -- SHA-256 of the session token; raw token never stored
    created_at        TEXT NOT NULL,
    expires_at        TEXT NOT NULL,
    revoked           INTEGER NOT NULL DEFAULT 0 CHECK (revoked IN (0,1))
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_token_hash ON sessions(token_hash);

CREATE TABLE IF NOT EXISTS conversations (
    id            TEXT PRIMARY KEY,
    user_a_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TEXT NOT NULL,
    CHECK (user_a_id <> user_b_id),
    UNIQUE (user_a_id, user_b_id)
);
CREATE INDEX IF NOT EXISTS idx_conversations_user_a ON conversations(user_a_id);
CREATE INDEX IF NOT EXISTS idx_conversations_user_b ON conversations(user_b_id);

CREATE TABLE IF NOT EXISTS messages (
    id                TEXT PRIMARY KEY,
    conversation_id   TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id         TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    image_path        TEXT NOT NULL,          -- reference to the stego-image file (object storage in production)
    payload_bytes     INTEGER NOT NULL CHECK (payload_bytes >= 0),
    image_width       INTEGER NOT NULL CHECK (image_width > 0),
    image_height      INTEGER NOT NULL CHECK (image_height > 0),
    status            TEXT NOT NULL DEFAULT 'sent' CHECK (status IN ('sending','sent','delivered','failed')),
    created_at        TEXT NOT NULL,
    read_at           TEXT,
    CHECK (sender_id <> recipient_id)
);
CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_recipient_unread ON messages(recipient_id, read_at);

CREATE TABLE IF NOT EXISTS image_history (
    id                TEXT PRIMARY KEY,
    user_id           TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_id        TEXT REFERENCES messages(id) ON DELETE SET NULL,
    image_name        TEXT NOT NULL,
    width             INTEGER NOT NULL,
    height            INTEGER NOT NULL,
    capacity_bytes    INTEGER NOT NULL,
    payload_bytes     INTEGER NOT NULL,
    recipient_alias   TEXT NOT NULL,
    created_at        TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_image_history_user ON image_history(user_id, created_at);

-- Append-only, minimal security/audit trail (section 36: never log secrets).
CREATE TABLE IF NOT EXISTS security_events (
    id            TEXT PRIMARY KEY,
    user_id       TEXT REFERENCES users(id) ON DELETE SET NULL,
    event_type    TEXT NOT NULL,   -- e.g. LOGIN_SUCCESS, LOGIN_FAILURE, PIN_ENABLED, ACCOUNT_LOCKED
    detail        TEXT,            -- short, non-sensitive context only
    created_at    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_security_events_user ON security_events(user_id, created_at);
