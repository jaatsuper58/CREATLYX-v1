# ChattlyX Security

## Reporting a vulnerability

Please report suspected vulnerabilities privately:

- Email: `security@chattlyx.example` (replace with the production address before launch;
  see `infra/security/security.txt` once deployed).
- PGP key: published with the security.txt deployment.
- We aim to acknowledge within 48 h and remediate critical issues within 7 days.
  Do **not** file exploitable details in public issues.

## Security posture (Phase 0 baseline)

1. **No custom cryptography.** E2EE uses an audited protocol library
   (ADR-0002 pending); file encryption uses JCA AES-256-GCM with per-file random
   keys whose material travels only inside the E2EE envelope.
2. **Zero-knowledge server.** Content is stored as ciphertext envelopes only,
   deleted on acknowledgement, TTL 30 days.
3. **Zero-plaintext push.** FCM data messages carry an opaque envelope id only.
4. **Secrets hygiene.** No secrets in the repository. Dev credentials (DB, MinIO,
   TURN static secret) are dev-only values in `infra/compose` and are overridden
   per-environment via secret managers in staging/production.
5. **Logging discipline.** Message content, keys, tokens and full phone numbers
   must never reach logs; `SafeLog` scrubs client logs and the Timber tree is
   no-op in release. Backend structured logs follow the same rule.
6. **Transport.** TLS 1.3-only intent with TLS 1.2 floor, cleartext traffic
   disabled, certificate pinning configuration ships in
   `android/app/src/main/res/xml/network_security_config.xml` (pins are added
   when the production host exists — see comments in that file).

## Phase 2 notes and accepted trade-offs

1. **Placeholder session cipher (temporary, pre-libsignal).** The 1:1 message
   path uses a stop-gap cipher — static-static ECDH (X25519/EC) → HKDF-SHA256 →
   AES-256-GCM — implemented in `SessionCipher` (Android `data` module). It
   provides confidentiality + integrity but **no forward secrecy and no
   ratcheting**. This contradicts posture §1 and is acceptable only because
   ADR-0002 (libsignal adoption, pending counsel's AGPL review) blocks the
   real protocol. The swap replaces `SessionCipher` wholesale; the server is
   opaque to session content either way. Messages sent on the placeholder
   cipher must be treated as best-effort confidential until libsignal lands.
2. **Contact discovery trade-off (CON-03).** Clients upload up to 1,000
   unpeppered SHA-256 digests of E.164 numbers per request; the server answers
   with matching registered public profiles only and never echoes the matched
   hash, so responses cannot be attributed to a specific number. Residual
   risks, accepted for Phase 2: (a) an authenticated attacker who can query the
   endpoint can probe individual numbers (unpeppered hashes are precomputable
   over the global E.164 space); mitigations in place are authentication,
   per-account rate limiting and batch caps. Planned hardening: server-side
   pepper issued at registration, and a PSI/CDSI-style protocol once scale
   justifies it.
3. **Receipts and typing metadata.** Read/delivered receipts ride inside
   encrypted session content (zero-knowledge preserved). Typing indicators are
   server-routed envelopes whose *content* is encrypted; the envelope
   metadata (sender, recipient, timing) is visible to the server by design —
   metadata minimisation is a Phase 7 hardening item.

## Pre-launch gates

- OWASP MASVS L2 checklist + MASTG cases (`docs/masvs-l2-checklist.md`, created
  in Phase 7).
- Penetration test commissioned before beta.
- Dependency scanning (CI advisory today; hard gate from Phase 1).
