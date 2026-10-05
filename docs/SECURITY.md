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

## Phase 3/4 notes and accepted trade-offs

1. **Attachment ids are capability tokens (MED-01..04).** Attachment UUIDs are
   never enumerable: they travel only inside E2EE `AttachmentContent` payloads,
   and every `/v1/attachments/{id}` endpoint answers **404** for anyone who is
   neither the sender nor the declared recipient — including an existence
   check. Blob keys and nonces exist only inside those E2EE payloads and in
   the client's encrypted local store; the server stores ciphertext only and
   verifies uploads against the declared SHA-256. Download paths verify the
   ciphertext digest before decrypting, so a tampered blob fails closed.
2. **Group membership metadata is server-visible (GRP-*).** By design the
   server knows group membership, roles and names — this is the same metadata
   model as the 1:1 transport (envelope headers). Group *message content*
   stays E2EE: with the placeholder cipher (note §1 under Phase 2) the client
   fans out one pairwise-encrypted envelope per member; Sender Keys (libsignal)
   replace that fan-out once ADR-0002 clears. Strangers receive 404 for group
   reads, mirroring the attachment capability model.
3. **Voice notes and media files are plaintext only inside app-private
   storage** (`filesDir/attachments`, never shared except through the
   FileProvider grant for an explicit user "open" action).

## Phase 5/6 notes and accepted trade-offs

1. **Call signalling is relayed, never parsed (CALL-*).** `call_signal`
   frames are re-addressed by swapping sender/recipient ids; the payload is an
   opaque `CallSignalContent` blob encrypted under the same E2EE session
   cipher as messages (placeholder cipher note in Phase 2 applies). The server
   cannot read SDP offers/answers or ICE candidates. It can observe call
   metadata (who called whom, when, call duration from signal timing) — the
   same exposure class as envelope headers, and documented as such.
2. **Presence is hidden both ways across blocks (STS/SAF).** `GET
   /v1/presence/{accountId}` returns a generic offline/hidden view when either
   party blocks the other, so presence cannot be used to probe a blocked
   account. Block rows themselves are per-user; the block *list* of other
   users is never exposed.
3. **Blocked pairs are cut off server-side (SAF-02, Phase 7).** Messages are
   silently dropped before persistence when either party blocks the other
   (sender keeps normal acks; the block state is never disclosed), queued
   envelopes from before the block are filtered at drain time, and typing,
   receipts and call signalling between the pair are suppressed too. Presence
   stays hidden both ways. Unblocking restores normal delivery immediately.
4. **Call media is not yet SFU-routed.** Peer-to-peer WebRTC exposes both
   parties' IP addresses to each other (standard WebRTC trade-off); TURN/SFU
   relay for IP concealment is designed for the LiveKit SFU milestone.
5. **ICE candidates round-trip with full routing metadata (Phase 7).**
   `sdpMid`/`sdpMLineIndex` now travel inside the E2EE `CallSignalContent`
   (fields 8/9), so trickle candidates apply to the correct transceiver.
6. **OTP entry is screen-capture protected (Phase 7).** The OTP route sets
   `FLAG_SECURE`, keeping codes out of screenshots, recents and recordings.

## Pre-launch gates

- OWASP MASVS L2 checklist + MASTG cases (`docs/masvs-l2-checklist.md`).
- Penetration test commissioned before beta.
- Dependency scanning (CI advisory today; hard gate from Phase 1).
