# Runbook: security incident response

Severity ladder: **SEV1** (account compromise, plaintext leak) → page on-call,
freeze sign-ups if abuse is auth-driven, preserve logs. **SEV2** (feature-level
abuse) → next business day. **SEV3** → backlog.

## Common scenarios

### Suspicious account / OTP abuse
1. Revoke all refresh tokens for the account (delete from `auth` table).
2. Check `login_attempts` row for brute-force signature.
3. If credential stuffing: force re-verification on next sign-in.

### Block-evasion reports
1. Confirm via `blocked_peers` table that the block row exists.
2. Note: server-side delivery suppression of blocked senders is Phase 7
   hardening; until then victims stop receiving messages from blocked peers
   only client-side. Track under incident notes.

### Signed URL / blob abuse
- Blob ids are capability tokens: any leaked id exposes that one ciphertext.
  Rotate by deleting the `blobs` row; clients see `404` and fail closed.

### Push pipeline
- FCM push carries only the envelope id; compromise leaks metadata, never
  content. Token rotation is automatic on next app sign-in.

## Post-incident
Write an incident record using `docs/decisions/ADR-template.md`; include
timeline, blast radius, and the hardening tasks filed.
