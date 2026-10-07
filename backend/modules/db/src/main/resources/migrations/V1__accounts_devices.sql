-- ChattlyX core identity schema (AUTH-*). Zero-knowledge: no message content
-- ever lands in this database; envelopes get their own store in Phase 2.

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    e164_hash       TEXT NOT NULL UNIQUE,          -- peppered hash, primary lookup
    e164_encrypted  BYTEA NOT NULL,                -- only for OTP delivery; encrypted
    username        TEXT UNIQUE,                   -- optional @username (3-32)
    display_name    TEXT NOT NULL,
    about           TEXT NOT NULL DEFAULT '',
    avatar_blob_id  UUID,
    created_at      BIGINT NOT NULL,
    deleted_at      BIGINT                         -- AUTH-10: soft delete, purge <= 30 d
);

CREATE TABLE devices (
    id          BIGSERIAL PRIMARY KEY,
    account_id  UUID NOT NULL REFERENCES accounts(id),
    name        TEXT NOT NULL,
    created_at  BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    revoked_at  BIGINT                             -- remote log-out (AUTH-07)
);
CREATE INDEX idx_devices_account ON devices(account_id);

CREATE TABLE identity_keys (
    account_id  UUID NOT NULL,
    device_id   BIGINT NOT NULL,
    public_key  BYTEA NOT NULL,
    created_at  BIGINT NOT NULL,
    PRIMARY KEY (account_id, device_id)
);

CREATE TABLE signed_prekeys (
    account_id  UUID NOT NULL,
    device_id   BIGINT NOT NULL,
    prekey_id   INT NOT NULL,
    record      BYTEA NOT NULL,
    created_at  BIGINT NOT NULL,
    PRIMARY KEY (account_id, device_id, prekey_id)
);

CREATE TABLE one_time_prekeys (
    account_id  UUID NOT NULL,
    device_id   BIGINT NOT NULL,
    prekey_id   INT NOT NULL,
    record      BYTEA NOT NULL,
    PRIMARY KEY (account_id, device_id, prekey_id)
);

CREATE TABLE kyber_prekeys (
    account_id  UUID NOT NULL,
    device_id   BIGINT NOT NULL,
    prekey_id   INT NOT NULL,
    record      BYTEA NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (account_id, device_id, prekey_id)
);

CREATE TABLE refresh_tokens (
    token_hash      TEXT PRIMARY KEY,              -- SHA-256 of the opaque token
    account_id      UUID NOT NULL REFERENCES accounts(id),
    device_id       BIGINT NOT NULL,
    created_at      BIGINT NOT NULL,
    expires_at      BIGINT NOT NULL,
    replaced_by     TEXT,                          -- rotation chain (reuse detection)
    revoked_at      BIGINT
);
CREATE INDEX idx_refresh_account ON refresh_tokens(account_id);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    account_id  UUID,
    action      TEXT NOT NULL,
    detail      TEXT,
    created_at  BIGINT NOT NULL
);
