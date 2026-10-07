# ADR-0003: compileSdk / targetSdk = 36 (Android 16)

- Status: accepted
- Date: 2026-10-04
- Deciders: architecture

## Context

Google Play requires new apps to target Android 16 (API 36) from 2026-08-31
(verified against Play Console policy). The master spec said "API 35 or newer —
verify"; verification resolved to 36.

## Decision

`compileSdk = targetSdk = 36`, `minSdk = 24`. Robolectric screenshot tests pin
the simulated SDK explicitly. Every native library shipped must be 16 KB
page-size compatible.

## Consequences

- AGP 9.3.x / Gradle 9.7.x are the minimum verified toolchain (see version catalog
  "verify latest stable" markers).
- Android 16 runtime behaviours (16 KB pages, foreground-service limits,
  `dataSync` timeouts) must be honoured by transfer/call services (Phases 3/5).
