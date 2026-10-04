-- ChattlyX Phase 2 messaging schema (MSG-*). Envelope bodies are ciphertext
-- only; the server cannot decrypt them. Acked envelopes are hard-deleted
-- (delete-on-ack); anything undelivered is purged after 30 days (TTL job).

-- Contact-discovery index (CON-03): unpeppered SHA-256 of the E.164 number.
-- SHA-256 of a phone number is the industry-standard trade-off for private
-- contact discovery (see SECURITY.md); the peppered hash stays primary auth.
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS e164_sha256 TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_e164_sha256
    ON accounts(e164_sha256) WHERE e164_sha256 IS NOT NULL;

-- FCM data-only push routing (NOT-01): token per device, opaque payloads only.
ALTER TABLE devices ADD COLUMN IF NOT EXISTS push_token TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS push_updated_at BIGINT;

CREATE TABLE envelopes (
    id                TEXT PRIMARY KEY,             -- server envelope id (UUIDv7)
    conversation_id   TEXT NOT NULL,                -- deterministic peer-pair id
    sender_account_id UUID NOT NULL,
    sender_device_id  BIGINT NOT NULL,
    recipient_id      UUID NOT NULL,                -- 1:1 only in Phase 2
    seq               BIGINT NOT NULL,              -- per-conversation cursor
    envelope_type     INT NOT NULL,                 -- proto EnvelopeType
    ciphertext        BYTEA NOT NULL,
    client_message_id UUID NOT NULL,                -- idempotency key (UUIDv7)
    created_at        BIGINT NOT NULL,
    delivered_at      BIGINT,                       -- device read from queue
    acked_at          BIGINT                        -- recipient acked; row dies
);
CREATE UNIQUE INDEX idx_envelopes_idempotency
    ON envelopes(sender_account_id, client_message_id);
CREATE INDEX idx_envelopes_recipient_seq
    ON envelopes(recipient_id, conversation_id, seq);
CREATE INDEX idx_envelopes_created ON envelopes(created_at);

-- Conversations are derived (client-side canonical id: sorted account pair),
-- so no server conversations table is needed for 1:1.
