# Contributing to ChattlyX

## Development setup

1. **JDK 21** (Temurin recommended).
2. **Android SDK**: platform 36 + build-tools 36.0.0 (Android Studio manages this).
3. **Docker** for the local backend stack:
   `docker compose -f infra/compose/docker-compose.local.yml up -d`
4. Clone and open `android/` in Android Studio (K2 enabled by default).

First run of `./gradlew` bootstraps the pinned Gradle distribution. To install the
canonical wrapper jar, run `gradle wrapper` once and commit the generated files.

## Build & test

```bash
cd android
./gradlew build                          # assemble + lint + unit + screenshot tests
# ktlint/detekt gates are pending AGP-9 plugin compatibility (root build.gradle.kts note);
# style is enforced by review against the official Kotlin style until then
./gradlew :core:designsystem:testDebugUnitTest   # design-system suite incl. Roborazzi

cd ../backend
./gradlew build
./gradlew :server:run                    # http://localhost:8080/health/live
```

### Screenshot tests (Roborazzi)

Goldens live under `android/core/designsystem/src/test/screenshots/`. Regenerate
locally with `-Proborazzi.test.record=true` and commit the images. CI records and
uploads goldens until the first set is committed; afterwards CI verifies.

## Conventions

- Kotlin official style; no wildcard imports; no `!!`; no `GlobalScope`;
  no blocking calls on Main; structured concurrency with injected dispatchers.
- Every screen: immutable `UiState` + `UiEvent` + one-off `UiEffect`.
- All user-visible strings in `strings.xml` (feature IDs in code comments).
- Commits: Conventional Commits (`feat:`, `fix:`, `chore:`, …); reference feature
  IDs (e.g. `feat(auth): AUTH-03 OTP screen with SMS Retriever`).
- Never commit secrets; never log message content, keys, tokens or full numbers.
- Work branch model: trunk-based with short-lived branches; PRs require green CI.

## Phase gates

Each roadmap phase has acceptance criteria in the master spec; a feature is "done"
only per the Definition of Done (tests, budgets, accessibility, localisation,
states, docs, CI, security checklist, low-end device verification).
