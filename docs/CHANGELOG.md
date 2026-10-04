# Changelog

All notable changes to ChattlyX are documented here, per phase of the roadmap.

## [Unreleased]

## [0.1.0] — Phase 0: Foundations (2026-10-04)

### Added
- Monorepo layout: `android/`, `backend/`, `proto/`, `infra/`, `docs/`.
- Android Gradle scaffold: 28 modules (Section 6.1 module list), `build-logic`
  convention plugins (application/library/compose/hilt/test/jvm/feature),
  version catalog, compileSdk/targetSdk 36, minSdk 24 (ADR-0003).
- `core:designsystem`: Material 3 theme with Section 5.5 tokens (light/dark/AMOLED,
  dynamic-colour opt-in), typography scale, motion tokens, and components:
  buttons (incl. brand gradient), text field, OTP field, avatar, message bubble,
  chat list item (72 dp spec), delivery ticks, unread pill, connection banner,
  filter chip, skeleton shimmer, empty state, modal sheet.
- App shell: Hilt, splash screen, edge-to-edge, bottom-nav/rail adaptive navigation
  (Chats · Calls · Contacts · Settings), type-safe Navigation Compose routes.
- `core:common`: `ChattlyError` sealed hierarchy, `Result`, UUIDv7, clock, E.164
  normalisation, safe logging (PII-scrubbing Timber tree), StrictMode wiring.
- `core:network`: connectivity monitor, realtime connection states, reconnect
  back-off with jitter, adaptive heartbeat policy (tested).
- `core:crypto`: Section 6.6 interface set, Keystore key wrapper (StrongBox-aware),
  chunked AES-256-GCM attachment cipher with SHA-256 digest (unit-tested).
- `core:datastore`: appearance settings (theme mode, AMOLED, dynamic colour,
  reduce motion) driving the theme.
- Screenshot tests for design-system components (Robolectric + Roborazzi,
  light/dark/RTL/200 % font).
- Backend scaffold: Ktor 3.5 server module + common module; health endpoints,
  `/v1/config`, RFC 9457 `application/problem+json` error rendering; OpenAPI 3.1
  skeleton (`backend/openapi/chattlyx-openapi.yaml`).
- Infra: Docker Compose dev stack (Postgres 16, Redis 7, MinIO, coturn, LiveKit,
  fake OTP/FCM gateway with dev magic-code support).
- CI: Android CI (build + tests + screenshot record), Backend CI, Infra compose
  validation, advisory security scan, nightly unit matrix; Dependabot.
- Docs: ARCHITECTURE, SECURITY, PRIVACY, CONTRIBUTING, ADR-0001/0002/0003.

### Known limitations (tracked)
- ktlint/detekt run as a soft gate in CI until first local format pass lands.
- Gradle wrapper jar materialises on first `gradle wrapper` run (bootstrap script).
- libsignal not yet integrated (ADR-0002 pending counsel); crypto module exposes
  interfaces + attachment cipher only.
