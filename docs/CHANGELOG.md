# Changelog

All notable changes to ChattlyX are documented here, per phase of the roadmap.

## [Unreleased]

## [0.2.0] — Phase 1: Auth, keys & profile (2026-10-04)

### Added
- Backend `modules:db`: forward-only schema migrator (tracked in
  `schema_migrations`; no Flyway dependency), HikariCP pool, and repositories
  for accounts, devices, identity/signed/one-time/Kyber prekeys, OTP sessions,
  rotating refresh tokens, avatar blobs and a PII-light audit log. Migrations
  V1–V3 implement the Phase 1 schema contract.
- Backend `modules:auth`: peppered E.164 hashing + AES-GCM number vault,
  OTP lifecycle (5 min TTL, 5 attempts, 15 min lockout, doubling resend
  back-off), HS256 JWT access tokens (15 min), rotating refresh tokens with
  reuse detection that revokes the device, sliding-window rate limiter.
- REST endpoints (OpenAPI `0.1.0-phase1`): `POST /v1/auth/otp/request|verify`,
  `POST /v1/auth/token/refresh`, `GET|PUT /v1/profile`, avatar upload/download,
  `GET /v1/devices`, `DELETE /v1/devices/{id}`, `DELETE /v1/account`,
  `PUT /v1/devices/keys`, `GET /v1/keys/count`, `GET /v1/keys/{account}/{device}`.
  Key fetch atomically consumes one-time and Kyber prekeys.
- Backend tests: JWT/rate-limiter/E164-vault unit tests plus a Testcontainers
  integration suite covering registration, key upload/consumption, refresh
  rotation + reuse, attempt lockout and account deletion.
- Android `core:network` REST layer: Retrofit + kotlinx.serialization converter,
  bearer interceptor, 401 refresh authenticator (single-flight, bare-HTTP
  refresh to avoid client cycles), RFC 9457 -> `ChattlyError` mapping.
- Android `domain`: auth models, repository ports and use cases
  (request/verify OTP, profile CRUD, devices, deletion, key bundles).
- Android `data`: Keystore-wrapped credential DataStore, placeholder X25519
  key generation with documented libsignal swap point (ADR-0002), avatar
  AES-256-GCM encryption with on-device key storage, REST repository
  implementations.
- `feature:onboarding`: Welcome, phone entry (20 launch markets, SIM region
  hint, no permissions), OTP screen with SMS Retriever auto-read (GMS-guarded),
  profile setup with Photo Picker avatar. App gates on registration state.
- `feature:settings`: Account (S36), Linked devices (S47) and Delete
  account (S50) screens wired to the backend; session end returns to onboarding.
- Unit tests for all new ViewModels, use cases and network components.

### Fixed
- Restored the `build-logic` convention plugin sources (registered in Phase 0
  but the implementation classes were missing from the tree).

### Known limitations (tracked)
- Key material uses JCA placeholder records until the libsignal licence
  decision (ADR-0002); Kyber prekey sets are empty until then.
- OTP sender is the dev fake gateway; Twilio Verify wiring point is documented
  in `AuthServices`.
- Rate limiter is in-memory (single node); Redis-backed variant planned for
  Phase 2 scale-out.
- Avatars live in Postgres `bytea` until the S3 blob service lands (Phase 3).

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
