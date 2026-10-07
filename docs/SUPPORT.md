# ChattlyX Support

## Getting help
- In-app: **Settings → Help** links to the community channel and FAQs.
- Security issues: see the disclosure policy in `SECURITY.md` (never file
  vulnerabilities in public trackers).
- Account recovery: sign-in is phone-number + OTP only. If you lose the
  device, install ChattlyX on the new device and sign in with the same
  number — the old device is signed out automatically (AUTH-07).

## Known recovery paths
- **Local key rotation.** Keystore keys are never exported; sign out (drops
  the local DB key reference) and sign back in to regenerate them. Message
  history on the old device stays encrypted at rest and is wiped with the app
  data.
- **Blocked users.** Settings shows the block list; unblocking restores
  presence visibility immediately and delivery resumes for new messages.
  Messages sent while blocked are never delivered (they are dropped, not
  queued) — this is by design.
- **Account deletion.** Settings → Account → Delete. Deletion also exists on
  the web (AUTH-10); server-side blobs and envelopes are purged within the
  retention window documented in `PRIVACY.md`.

## Beta expectations
Crash-free target ≥ 99.7%. If you hit a problem, the in-app report flow
attaches the build id (`0.10.0-beta`, code 10) — include it when reporting.
