# ChattlyX Architecture (Phase 0 baseline)

This document grows with each phase. It is the single map of the system; ADRs in
`docs/decisions/` record the "why" for each significant choice.

## 1. System overview

```
+----------------------+        HTTPS (TLS 1.3, pinned)        +-------------------+
|  Android client      | ------------------------------------> |  REST API (Ktor)  |
|  Jetpack Compose     |                                       |  stateless nodes  |
|  multi-module Gradle | <------- WebSocket (Protobuf) ------ |  WS gateway       |
+----------+-----------+                                       +---------+---------+
           |                                                             |
   SQLCipher DB, Keystore                                        PostgreSQL · Redis
   media cache                                                   envelopes (TTL 30 d)
           |                                                             |
   +-------v--------+                                        +-----------v-----------+
   | S3-compatible  | <--- presigned/chunked uploads --------| media plane (blobs,    |
   | object store   |      (ciphertext only)                 | ciphertext only)       |
   +----------------+                                        +------------------------+
           coturn (STUN/TURN UDP+TCP+TLS443) · LiveKit SFU (group calls, P1)
           FCM data-only push (opaque envelope id — zero plaintext)
```

## 2. Android client

Layering (dependencies point inward; features never depend on each other):

```
feature/*  ->  core/ui, core/designsystem, core/network, core/database,
               core/datastore, core/crypto, core/push, core/work  ->  domain
                                                                     ^
                                                                  data
```

- `:app` — DI root, navigation host, application class, splash, theme wiring.
- `:core:designsystem` — Section 5.5 tokens + components; no business logic.
- `:core:common` — error model, dispatchers, UUIDv7, clocks, safe logging.
- `:core:network` — OkHttp/Retrofit REST, WebSocket realtime client,
  connectivity monitor, reconnect/heartbeat policies.
- `:core:database` — Room + SQLCipher (single source of truth; FTS5; Paging later).
- `:core:crypto` — Signal-protocol store/cipher interfaces (Section 6.6),
  Keystore key wrapping, attachment chunk cipher. No UI dependencies.
- `:domain` — pure Kotlin models, use cases, repository interfaces.
- `:data` — repository implementations bridging network ↔ database ↔ crypto.

UI conventions: one immutable `UiState` (`StateFlow`) per screen, `UiEvent` in,
one-off `UiEffect` out (Channel); `collectAsStateWithLifecycle`; Room flows are
the only source the UI observes.

## 3. Backend (Ktor)

```
backend/
  server/          bootstrap: engine, plugins (JSON, logging, RFC9457 errors), routes
  modules/common/  shared kernel: problem details, error taxonomy, UUIDv7, clocks
  modules/...      auth, keys, profile, contacts, messages, groups, media, calls,
                   push, safety, realtime  (land in Phases 1-5)
  openapi/         chattlyx-openapi.yaml — OpenAPI 3.1 source of truth
```

Zero-knowledge content policy: the server stores only ciphertext envelopes
(TTL 30 days, deleted on ack). Phone numbers are stored as peppered hashes except
where OTP delivery requires the E.164 value.

## 4. Wire contract

- REST + JSON under `/v1` (errors: RFC 9457 `application/problem+json`).
- WebSocket frames are Protobuf (`proto/chattlyx/v1/*.proto`).
- OpenAPI 3.1 (`backend/openapi/chattlyx-openapi.yaml`) is the REST source of truth;
  changes are additive and backward-compatible.

## 5. Cross-cutting decisions

| Concern | Decision | Reference |
| --- | --- | --- |
| Stack | Kotlin/Compose client, Ktor backend | ADR-0001 |
| E2EE engine | libsignal planned; licence gate open | ADR-0002 |
| SDK levels | compile/target 36, min 24 | ADR-0003 |
| Kotlin tooling on AGP 9 | opt-out of built-in Kotlin (`android.builtInKotlin=false`), revisit after Phase 1 | build docs |
