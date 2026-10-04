-- ChattlyX Phase 3 attachment metadata (MED-*). Blob bytes live in the blob
-- store (ciphertext only); this table is the declaration/authorization
-- record. Attachment ids are capability tokens: they only ever travel inside
-- E2EE message payloads, so possession + authentication implies access.

CREATE TABLE attachments (
    id                UUID PRIMARY KEY,
    sender_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    recipient_account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
    conversation_id   TEXT,
    kind              TEXT NOT NULL CHECK (kind IN ('image', 'video', 'file', 'voice')),
    mime_type         TEXT NOT NULL,
    size_bytes        BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256            BYTEA NOT NULL,
    width             INT,
    height            INT,
    duration_ms       INT,
    file_name         TEXT,
    status            TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'ready')),
    created_at        BIGINT NOT NULL,
    uploaded_at       BIGINT
);

CREATE INDEX idx_attachments_sender ON attachments (sender_account_id, created_at);
