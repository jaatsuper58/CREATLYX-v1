# ChattlyX

> Chat freely. Stay private.

ChattlyX is a production-grade, privacy-first, lightweight instant-messaging and
calling app for Android (API 24+), with end-to-end encrypted messaging, free HD
voice/video calls, large-file sharing and group chats that stay usable on weak
2G/3G networks. This repository is a monorepo: Android client, Kotlin/Ktor
backend, infrastructure and documentation.

Status: **Phase 8 — Launch prep (code complete)** — foundations, auth/keys, 1:1 messaging, media & voice notes, groups, encrypted calling (incl. system telecom integration), presence/blocks, local search and hardening are in and CI-green; remaining gates are ops/store-side (see `docs/CHANGELOG.md` and the ADRs in
`docs/decisions/`). Feature catalogue and acceptance criteria are tracked in the
master build specification; every artefact references feature IDs (e.g. `AUTH-03`).

## Repository map

```
android/   Multi-module Jetpack Compose client (Gradle, build-logic conventions)
backend/   Kotlin/Ktor API + realtime gateway (Gradle, multi-module)
infra/     Docker Compose dev stack; Terraform/K8s land here in later phases
proto/     Shared Protobuf wire contract (WebSocket frames, envelopes)
docs/      Architecture, security, privacy, ADRs, runbooks
```

## Quickstart

Prereqs: JDK 21, Android SDK (platform 36, build-tools 36.0.0), Docker.

```bash
# Local backend stack: PostgreSQL 16, Redis 7, MinIO, coturn, LiveKit, fake OTP/FCM gateway
docker compose -f infra/compose/docker-compose.local.yml up -d

# Backend API (http://localhost:8080/health/live)
cd backend && ./gradlew :server:run

# Android app (debug build; first run downloads the pinned Gradle 9.7.0)
cd android && ./gradlew :app:assembleDebug
```

The first `./gradlew` invocation bootstraps the pinned Gradle distribution (the
binary wrapper jar is materialised by running `gradle wrapper` once — see
`android/gradle/wrapper/README.md`). CI is the build authority: see
`.github/workflows/`.

## Security & privacy

- E2EE for messages, files and calls via audited protocols only (no custom crypto).
  The E2EE engine decision is tracked in `docs/decisions/ADR-0002` (libsignal is
  AGPL-3.0 — licence resolution is a gate before Phase 2 completes).
- Zero-plaintext push: FCM carries only an opaque envelope id.
- `docs/SECURITY.md` — vulnerability reporting; `docs/PRIVACY.md` — data inventory.

## Licensing

Client licence is intentionally **not yet chosen** — it depends on the E2EE library
decision (ADR-0002). Until then, all rights reserved on the cryptographic code paths;
the rest of the tree targets an OSI licence decided alongside ADR-0002.
