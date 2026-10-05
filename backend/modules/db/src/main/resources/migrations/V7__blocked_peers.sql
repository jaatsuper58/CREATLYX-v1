-- Phase 6 (SAF-*): user block list. Blocks hide presence and (Phase 7)
-- suppress envelope delivery from the blocked account.
CREATE TABLE blocked_peers (
    account_id        UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    blocked_account_id UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, blocked_account_id)
);
