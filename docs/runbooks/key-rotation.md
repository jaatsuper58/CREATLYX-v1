# Runbook: key rotation

## Backend HMAC signing keys (OTP codes, JWT)
1. Add the new key as `HS256_KEY_NEXT`; the server validates both current and
   next during the rotation window.
2. Wait one full OTP TTL + JWT validity (≈ 30 min) so outstanding codes verify.
3. Promote: `HS256_KEY_CURRENT=$HS256_KEY_NEXT`, unset next, restart pods.

## JWT token-signing key
- Same two-key dance; access tokens live 15 min, so a 1-hour window is safe.

## Client Keystore keys
- Keystore keys are never exported and cannot be rotated remotely; a user can
  rotate locally by signing out (drops the DB key reference) and signing back
  in — documented in SUPPORT.md as a recovery path.

## Postgres credentials
1. Create a new role, update the connection pool secret, roll pods.
2. Revoke the old role after the deploy completes.

## Redis
- Stateless TTL store; rotate AUTH and restart clients — presence data
  regenerates within one TTL.
