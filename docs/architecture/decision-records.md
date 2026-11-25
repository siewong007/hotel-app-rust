# Architecture Decision Records (ADRs)

Canonical record of key architectural decisions and amendments across the platform.

## ADR 001: Four-Project Monorepo Structure

**Status:** Accepted (2025); amended 2026-11 (native Android app added)

**Context:** The platform serves web, desktop, and mobile staff/guest clients from a shared REST + WebSocket backend without coupling native mobile or desktop build toolchains to web builds.

**Decision:** Organize as a monorepo with four independent subdirectories:
- `hotel-app-be/` — Rust backend API & PostgreSQL resources
- `hotel-web-fe/` — React web frontend (staff SPA, guest portal, Salim Inn landing)
- `hotel-desktop/` — Tauri 2 desktop wrapper with sidecar backend + embedded PostgreSQL
- `hotel-android/` — Native Kotlin / Jetpack Compose Material 3 Android staff application

Each project owns its build system, lockfile, dependencies, and CI pipeline. There is no root workspace manager.

**Consequences:**
- ✅ Independent toolchains and CI pipelines (`ci.yml`, `android-ci.yml`, `desktop-build.yml`)
- ✅ Shared backend API, RBAC, and database truth without duplicating business rules
- ❌ Cross-cutting changes require updating multiple projects and documentation mirrors

---

## ADR 002: PostgreSQL-Only Persistence

**Status:** Accepted (2026)

**Context:** Web, Docker, and desktop deployments all use PostgreSQL. Maintaining an additional compile-time database backend created schema drift, duplicated queries, and unrepresentative test paths.

**Decision:** PostgreSQL is the sole supported database engine. SQLx compiles for PostgreSQL only, `DATABASE_URL` is required, and all server and desktop targets run the PostgreSQL V1 lifecycle.

**Consequences:**
- ✅ Single schema, exclusion constraints, triggers, and query semantics everywhere
- ✅ Integration tests exercise the exact production engine
- ❌ Local development and backend integration suites require PostgreSQL

---

## ADR 003: Layered Domain-Module Backend Architecture (`modules/<domain>/`)

**Status:** Accepted (2025); domain-module migration completed (2026)

**Context:** Early flat-by-layer backend code mixed HTTP extraction, business rules, and SQL inside handlers.

**Decision:** Organize all 40 backend domains inside `modules/<domain>/`:
- `routes.rs` — path registration, RBAC guards, rate limiters
- `handlers.rs` — thin HTTP/DTO translation
- `service.rs` — transactions, business workflows, cross-entity validation
- `repository.rs` — parameterized SQL only (`param!(N)`)
- `models.rs` — request/response DTOs and `validator` derives

Cross-cutting globals stay in `core/`, `routes/mod.rs`, `services/{audit,account_emails,google_identity,invoice_numbers}.rs`, `repositories/{audit,invoice_numbers}.rs`, and `models/{audit,common,row_mappers}.rs`.

**Consequences:**
- ✅ Clear domain ownership and isolated service/repository layers
- ❌ Discipline required when splitting large legacy modules (`bookings/`, `payments/`)

---

## ADR 004: JWT-based Authentication with RBAC

**Status:** Accepted (2025); cache sync updated 2026-09

**Context:** Staff and guests require distinct auth surfaces and granular `<resource>:<action>` permissions.

**Decision:** Issue short-lived stateless JWT access tokens paired with server-side revocable refresh tokens (HttpOnly `SameSite=Lax` cookie on web; Android Keystore encrypted cookie jar on Android). Permissions follow `<resource>:<action>`, with `:manage` implying every action on that resource. Process-local RBAC and settings caches converge across replicas via `core::cache_bus` `pg_notify` invalidation with a 30s TTL floor.

**Consequences:**
- ✅ Stateless per-request verification + revocable refresh sessions + cross-replica cache invalidation
- ❌ Refresh cookies cannot cross Tauri webview/localhost origin boundaries on desktop restart (accepted)

---

## ADR 005: In-Memory Rate Limiting (Superseded)

**Status:** Superseded (2026-09-17)

**Decision & Supersession:** Originally used an in-memory token bucket (`RateLimiter::new`), which now survives only for isolated unit tests. Production/multi-replica rate limiting uses PostgreSQL fixed-window counters in `rate_limit_buckets` (`RateLimiters::new(pool)`), preserving zero external dependencies (no Redis) across multiple replicas.

---

## ADR 006: Frontend State Management (TanStack Query, no client-state library)

**Status:** Accepted (2025), amended 2026-08-02

**Decision:** Server state lives in TanStack Query (`src/api/queryKeys.ts`), HTTP goes exclusively through `src/api/client.ts` (`ky` with in-memory access token, refresh cookie, single-flight 401 refresh-and-retry), route state lives in TanStack Router, and auth state lives in `AuthContext`. Zustand was never adopted and introducing any client-state library requires a new ADR.

---

## ADR 007: Desktop Sidecar Pattern (Tauri + Backend Binary)

**Status:** Accepted (2025)

**Decision:** Package `hotel-web-fe` with Tauri 2 (`hotel-desktop/`), spawning `hotel-app-be` as a localhost sidecar (`HOTEL_DESKTOP_MODE=true`) against an embedded PostgreSQL runtime (`src-tauri/pgsql/`, `19beta2`), backed by resource sync scripts and local managed `pg_dump` + uploads backup/restore pairs (see [../guides/desktop.md](../guides/desktop.md)).

---

## ADR 008: Append-Only Audit Logging

**Status:** Accepted (2025); hardened 2026-10 (`0022_audit_logs_append_only_guards.sql`)

**Decision:** Every mutating path logs via `services/audit.rs` to partitioned `audit_logs`, protected at the database layer by `prevent_audit_log_mutation` (`BEFORE UPDATE/DELETE` row triggers cloned across partitions plus per-partition `BEFORE TRUNCATE` statement triggers).

---

## ADR 009: MCP Server Integration (Never Implemented)

**Status:** Proposed — never implemented (corrected 2026-08-02)

**Context & Constraint:** Early planning proposed `hotel-app-be/mcp-server/` wrapping backend access, which was never built. Any future implementation must call the authenticated HTTP API as a normal client (or enforce `check_permission` identically) rather than bypassing route-layer RBAC directly against PostgreSQL.

---

## ADR 010: Schema-as-Source for PostgreSQL (No sqlx Migrations)

**Status:** Accepted (2025); amended 2026-08-21 (checksum-verified patch catalog)

**Decision:** Initialize empty databases once from `0001_v1_baseline.sql` + self-validating `seed.sql`, then converge installed V1 databases strictly via `patches/manifest.tsv` (generation 1, sha256-verified, immutable once published). Every additive schema change lands in **both** the baseline and a new manifest-registered patch, proven with a `pg_dump --schema-only` convergence diff and mirrored to `hotel-desktop/` (`bun run sync:resources`). Backend startup refuses to boot if any compiled-in revision is missing or mismatched. See [../guides/deployment.md](../guides/deployment.md) and [../../hotel-app-be/database/README.md](../../hotel-app-be/database/README.md).

---

## ADR 011: React 19 with TanStack Router (File-Based Routing)

**Status:** Accepted (2026)

**Decision:** Use TanStack Router file routes (`src/routes/`) paired with lazy-loaded views in `src/navigation/routeRegistry.tsx` and generated `routeTree.gen.ts`, enforced per role via `route_access_policies`.

---

## ADR 012: Hand-Rolled Internationalisation on `Intl`

**Status:** Accepted (2026-09-09)

**Decision:** Implement i18n directly on native `Intl.PluralRules` / `Intl.NumberFormat` / `Intl.DateTimeFormat` / `Intl.RelativeTimeFormat` across `hotel-web-fe/src/i18n/` and `hotel-app-be/src/core/i18n.rs` with shared `{{name}}` placeholders and four locales (`en`, `ms`, `zh`, `zh-TW`), enforced by key-parity, plural, placeholder, and hardcoded-string tests rather than adding `i18next`. See [../guides/internationalization.md](../guides/internationalization.md).

---

## ADR 013: Write-Time Snapshots for Channel Economics

**Status:** Accepted (2026-09-16)

**Decision:** Resolve channel pricing (`channel_pricing_rules`) and commission (`channel_commission_rules`) in `modules/booking_channels/pricing.rs` and freeze `channel_pricing_snapshot`, `commission_amount`, and `net_revenue` onto each `bookings` row at write time so historical net-revenue reports (`channel_net_revenue.rs`) remain invariant under future rule edits.

---

## ADR 014: gRPC-Web for Selected Domains (Strangler Migration)

**Status:** Accepted (2026-09); partial rollout (5 services / 47 RPCs live)

**Decision:** Serve tonic gRPC + `tonic_web::GrpcWebLayer` adapters (`src/grpc/`) inside the same Axum process at `/hotel.<package>.<Service>/<Method>`, delegating directly to the domain service/repository layer so auth, RBAC, audit, and realtime logic stay single-sourced. Browser Connect-ES clients in `hotel-web-fe/src/gen/` are opted in per bounded context via `src/api/grpc/flags.ts` (`VITE_GRPC_CONTEXTS` or `grpcContexts` override), with REST as default/fallback. While `deploy/Caddyfile` routes `/hotel.*`, the host deploy scripts (`deploy/deploy{,-staging}.sh`) do not yet match `/hotel.*`, keeping rollout flags disabled in production. See [overview.md#grpc-web-transport-partial-rollout](overview.md#grpc-web-transport-partial-rollout).

---

## ADR 015: WebSocket + `pg_notify` for Realtime Fan-out (Not SSE)

**Status:** Accepted (2026)

**Decision:** Use authenticated WebSocket hubs (`/api/updates/socket`, loyalty, support, availability) with access tokens passed in `Sec-WebSocket-Protocol` and session checks prior to upgrade. Mutating requests publish thin domain names (no PII/data payloads) locally and across replicas via PostgreSQL `pg_notify` (`core::cache_bus`); clients refetch via permission-checked REST queries and reconnect with exponential backoff.

---

## ADR 016: Native Kotlin / Jetpack Compose Android Staff Application

**Status:** Accepted (2026-11)

**Context:** Hotel staff needed a fast, reliable mobile operations client for front-desk check-in/out, room turns, housekeeping, maintenance, payment approvals, eKYC review, guest lookup, global search, and offline resilience on Android devices without shipping a sluggish wrapped WebView.

**Decision:** Build a native Android staff app under `hotel-android/` (`com.hotelapp.staff`) using Kotlin, Jetpack Compose Material 3, Retrofit/OkHttp, kotlinx.serialization, DataStore, SQLite offline caching + WorkManager (`SyncPendingMutationsWorker`), Glance widget (`HotelOpsWidget`), Android Keystore-backed encrypted refresh cookies (`EncryptedCookieJar`), AndroidX Biometric cold lock, Credential Manager passkeys & saved login, and Android `PrintManager` folio printing — consuming the existing REST `/api/*` endpoints and `/api/updates/socket` WebSocket without backend forks.

**Consequences:**
- ✅ Native Material 3 ergonomics, adaptive phone/tablet rail navigation, Keystore hardware-backed secrets, biometrics, passkeys, and home-screen Glance widget
- ✅ Zero backend fork: Android exercises the exact RBAC, validation, idempotency, and guest-account rejection (`isGuestOnlyAccount`) policies as web/desktop
- ❌ Kotlin DTOs and RBAC route maps must stay aligned with backend contract changes (mitigated by CI `android-ci.yml` and `LiveBackendE2eTest`)
