-- Phase 1 avatar storage: small encrypted blobs in Postgres (<= 256 KB after
-- compression). Migrates to the S3 media plane with the Phase 3 chunked
-- upload pipeline; blob ids are stable across that move.

CREATE TABLE avatar_blobs (
    id          UUID PRIMARY KEY,
    owner_id    UUID NOT NULL REFERENCES accounts(id),
    ciphertext  BYTEA NOT NULL,
    created_at  BIGINT NOT NULL,
    CHECK (octet_length(ciphertext) <= 400000)
);
