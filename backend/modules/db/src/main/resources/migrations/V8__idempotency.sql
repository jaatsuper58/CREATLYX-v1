-- Phase 7 hardening (MASVS-RESILIENCE): idempotency keys on REST mutations.
-- Messaging is already idempotent (unique sender + client message id); the
-- only non-naturally-idempotent mutation is group creation. A retried create
-- carrying the same Idempotency-Key returns the original group instead of
-- duplicating it. Keys are scoped per creator; NULL = pre-feature rows.
ALTER TABLE groups ADD COLUMN idempotency_key TEXT;

CREATE UNIQUE INDEX uq_groups_creator_idem
    ON groups (created_by, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
