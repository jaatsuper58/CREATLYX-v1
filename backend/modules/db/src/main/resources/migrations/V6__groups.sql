-- Phase 4 (GRP-*): groups + membership. Group message content stays E2EE;
-- the server only stores membership metadata and routes per-member envelopes.
CREATE TABLE groups (
    id                 UUID PRIMARY KEY,
    name               TEXT NOT NULL,
    created_by         UUID NOT NULL REFERENCES accounts (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Bumped on every membership/name change; clients use it to resync.
    membership_version BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE group_members (
    group_id   UUID NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    role       TEXT NOT NULL CHECK (role IN ('owner', 'admin', 'member')),
    added_by   UUID REFERENCES accounts (id),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, account_id)
);

CREATE INDEX idx_group_members_account ON group_members (account_id);
