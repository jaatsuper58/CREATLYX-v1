# ChattlyX Privacy — data inventory

Living document (Section 9.4 of the master spec). A formal DPIA lands before beta.

## Collected

| Data | Purpose | Retention | Access |
| --- | --- | --- | --- |
| Phone number (E.164) | Account identity, OTP delivery | Account lifetime; peppered hash primary | Auth subsystem |
| Profile fields (name, avatar, about, @username) — user-provided | Display to contacts | Until user edits/deletes | Profile subsystem |
| FCM push token | Wake device for encrypted envelopes | Until rotated/uninstalled | Push subsystem (encrypted at rest) |
| Opt-in contact hashes | Discover who is on ChattlyX (CON-01) | Until consent withdrawn | Contact-discovery subsystem, rate-limited |
| Opt-in anonymous diagnostics | Reliability metrics | 90 days aggregated | Analytics (no identifiers beyond random install id) |
| Transient source IP | Abuse prevention | ≤ 30 days, then aggregated | Abuse/rate-limit subsystem |

## NOT collected

Message/media/call content (E2EE — server holds ciphertext only), contact names,
advertising identifier, location (unless the user shares it inside an E2EE chat),
browsing/usage graphs.

Optional local chat backups (BKP-01/02) are encrypted with a key derived from
the user's own passphrase and written only to a location the user picks; they
are never uploaded to or readable by ChattlyX.

## No third-party advertising or analytics SDKs.

## User rights

Export, deletion (in-app + public web URL — Google Play requirement), correction,
consent withdrawal. Account deletion erases server data within 30 days (AUTH-10).

## Children

Minimum age 13 (16 where local law requires) enforced by an age gate at sign-up
(SAF-03, Phase 4).
