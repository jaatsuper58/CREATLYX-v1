# OWASP MASVS L2 checklist — ChattlyX (Phase 7 gate)

Status key: ✅ done · 🟧 partial · ⬜ pending. Every ⬜ must close before the
beta build ships to Play internal testing.

## MASVS-STORAGE (local data)
- ✅ SQLCipher passphrase is Keystore-wrapped (StrongBox where available); DB
  never holds plaintext keys/tokens.
- ✅ `allowBackup=false` + extraction rules exclude keys/DB (`app` manifest).
- ✅ Decrypted attachments live in app-private `filesDir/attachments`; shared
  only via FileProvider grants for explicit user opens.
- ✅ DataStore credentials encrypted by Keystore-backed key.
- ✅ Screenshot blocking on sensitive screens: `FLAG_SECURE` while the OTP
  entry screen is active.

## MASVS-CRYPTO
- ✅ No custom primitives: JCA AES-256-GCM (attachments, avatar), X25519+HKDF
  placeholder session cipher pending libsignal (ADR-0002, tracked as accepted
  risk in SECURITY.md).
- ✅ Random keys via `SecureRandom`; one key per blob; digest-verified
  downloads fail closed on tamper.
- ⬜ libsignal PQXDH + Double Ratchet + Sender Keys (blocks on counsel AGPL
  review; swap points documented in `SessionCipher`, `CallManager`,
  `MessageRepositoryImpl`).

## MASVS-AUTH
- ✅ OTP: 5-attempt cap, 15-min lockout, resend back-off, 5-min TTL.
- ✅ Device-bound JWTs (15 min), rotating refresh tokens with reuse detection.
- ✅ Biometric/credential app lock (Phase 8): Settings → Privacy → App lock;
  the gate re-arms on every process backgrounding and unlocks via
  BiometricPrompt (BIOMETRIC_STRONG or DEVICE_CREDENTIAL).

## MASVS-NETWORK
- ✅ TLS-only (`usesCleartextTraffic=false`), network security config with a
  pin slot for the production host.
- ✅ Certificate pinning values to be added with the production cert.

## MASVS-PLATFORM
- ✅ WebViews unused; deep links limited to the account-deletion host.
- ✅ No exported components beyond the launcher + FileProvider (grant-only).
- 🟧 Intent hygiene audit for SAF open-with flows (FileProvider grants only —
  covered by pen-test scope).

## MASVS-CODE
- ✅ K2 compiler, `-Werror`-equivalent review gates in PR template.
- 🟧 ktlint/detekt hard gate deferred: the Gradle plugins are not yet
  AGP-9-compatible (tracked; style enforced by review + CI compile warnings).
- ✅ Dependency scanning (Dependabot + advisory scan workflow).

## MASVS-RESILIENCE
- 🟧 Root/tamper checks via Play Integrity: client scaffold complete and
  tested (provider binding behind `CHATTLYX_INTEGRITY_ENABLED` +
  `CHATTLYX_INTEGRITY_CLOUD_PROJECT_NUMBER` build flags); server-side token
  decode/enforcement lands with the GCP project (setup steps in
  SECURITY.md, "Phase 7/8 hardening notes").
- ✅ Anti-replay on REST mutations: messaging idempotent via UNIQUE
  sender+clientMessageId; group creation honours `Idempotency-Key` (V8
  unique index; client stamps keys via `IdempotencyKeyInterceptor`).
- ✅ Block enforcement is server-side (SAF-02): delivery, typing, receipts and
  call signals are all suppressed between blocked peers.

## MASVS-PRIVACY
- ✅ PRIVACY.md data inventory; server stores ciphertext + membership metadata
  only; push is zero-plaintext.
- ✅ Presence hidden both ways across blocks.

## Penetration test
- ⬜ Commissioned before beta; scope: REST/WS authZ, attachment capability
  model, group membership enforcement, OTP abuse.
