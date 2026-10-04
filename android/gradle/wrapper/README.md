# Gradle wrapper

Phase 0 ships a bootstrap `gradlew` (see repo-root script) instead of the binary
`gradle-wrapper.jar`. The Gradle version is pinned here and in CI. To materialize
the canonical wrapper jar, run once locally:

    gradle wrapper

then commit `gradle/wrapper/gradle-wrapper.jar` and remove the bootstrap note.
