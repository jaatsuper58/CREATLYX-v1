# ADR-0001: Technology stack

- Status: accepted
- Date: 2026-10-04
- Deciders: product owner, architecture

## Context

ChattlyX targets low-end Android devices (1–3 GB RAM) and weak 2G/3G networks while
delivering E2EE messaging and HD calls. The stack must keep the APK ≤ 25 MB,
cold start < 2 s, and remain maintainable by a small team.

## Decision

- Client: Kotlin 2.4.x, Jetpack Compose + Material 3, MVVM + unidirectional data
  flow, Clean Architecture multi-module Gradle with convention plugins, Hilt,
  Room + SQLCipher, OkHttp/Retrofit + WebSocket with Protobuf frames.
- Backend: Kotlin/Ktor stateless API + WebSocket gateway, PostgreSQL, Redis,
  S3-compatible object storage, coturn + LiveKit for calls, FCM data-only push.
- Realtime frames: Protocol Buffers; REST contract: OpenAPI 3.1.

## Consequences

- One language (Kotlin) across client/server lowers context switching.
- Protobuf keeps per-message wire overhead under the 1 KB budget.
- Ktor/Netty scales horizontally; envelope store can move to ScyllaDB later
  without API changes.
