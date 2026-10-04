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

## Pre-launch gates

- OWASP MASVS L2 checklist + MASTG cases (`docs/masvs-l2-checklist.md`, created
  in Phase 7).
- Penetration test commissioned before beta.
- Dependency scanning (CI advisory today; hard gate from Phase 1).
