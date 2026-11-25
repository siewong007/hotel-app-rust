# CLAUDE.md

Routing index for coding agents. Hard budget: **≤150 lines**, index/routing only — detail goes to
`.claude/refs/` (on demand), `.claude/rules/` (auto-loaded, keep lean), or `docs/`. Do not add
here without removing something. Facts verified `2026-11-25` against `master`.
**What deploys is `origin/master`, not an uncommitted tree** — confirm with `git show origin/master:<path>`.
Line anchors rot; Grep first. This volume path has a trailing space — quote paths in shell.

## Read this first

| Situation | Read |
|---|---|
| Any non-trivial work | [.claude/rules/00-diagnosis.md](.claude/rules/00-diagnosis.md) — failure modes + checklists |
| Done / escalate / ask-user calls; past corrections | [.claude/rules/judgment-rubrics.md](.claude/rules/judgment-rubrics.md), [.claude/rules/lessons.md](.claude/rules/lessons.md) |
| Delegating to a subagent | [.claude/refs/model-dispatch.md](.claude/refs/model-dispatch.md), [.claude/refs/delegation-templates.md](.claude/refs/delegation-templates.md) |
| Architecture / caller / impact question | [.claude/refs/codegraph.md](.claude/refs/codegraph.md) — CodeGraph before broad searches |
| Bookings, ledgers, invoicing | [.claude/refs/booking-workflow.md](.claude/refs/booking-workflow.md), [.claude/refs/ledger-workflow.md](.claude/refs/ledger-workflow.md) |
| Editing `.claude/`, or after any failure | [.claude/refs/maintenance.md](.claude/refs/maintenance.md) (+ append `lessons.md`) |
| UI work / i18n / backup & restore | [docs/design-system.md](docs/design-system.md), [docs/guides/internationalization.md](docs/guides/internationalization.md), [docs/guides/data-transfer.md](docs/guides/data-transfer.md) |
| Deploy, VPS, PG19 upgrade, security | [docs/guides/deployment.md](docs/guides/deployment.md), [docs/security.md](docs/security.md) |
| Architecture, ADRs (001–016), gRPC | [docs/architecture/overview.md](docs/architecture/overview.md), [docs/architecture/decision-records.md](docs/architecture/decision-records.md) |
| Desktop & Android guides | [docs/guides/desktop.md](docs/guides/desktop.md), [docs/guides/android.md](docs/guides/android.md) |
| What exists / what's next / index | [docs/features.md](docs/features.md), [docs/ongoing-dev.md](docs/ongoing-dev.md), [docs/README.md](docs/README.md) |

[AGENTS.md](AGENTS.md) owns naming, refactoring safety, and dependency policy — not repeated here. The two must agree: fix both or neither.

## Overview and layout

Hotel PMS: bookings, rooms, housekeeping, maintenance, rates, payments, ledgers, invoicing, night
audit, loyalty, promotions, eKYC, guest portal, analytics. Ships across four projects (no root workspace — run commands from each subdirectory):

- `hotel-app-be/` — Rust 1.95.0 (edition 2024), Axum 0.8, SQLx 0.9, tonic 0.14, PostgreSQL 19, JWT + bcrypt.
- `hotel-web-fe/` — React 19, TypeScript ~6.0, Vite 8, MUI 9, TanStack Query/Router/Table, ky 2, Nivo, Three.js (`salim-inn/`).
- `hotel-desktop/` — Tauri 2; backend sidecar + embedded PostgreSQL (`19beta2`) in `src-tauri/pgsql/`.
- `hotel-android/` — Native Kotlin `2.2.21` + Jetpack Compose Material 3 staff app (AGP `8.13.2`, SDK `36`).

`bun` 1.3.14 is the FE/desktop package manager (`bun.lock`). `.codegraph/` is Git-ignored and local; `codegraph.json` owns excludes. `proto/` (buf) is the gRPC contract.

## Commands

```bash
# hotel-app-be/
cargo check --all-features && cargo fmt --check && cargo clippy --all-features -- -D warnings
cargo test --all-features                     # set DATABASE_URL or PG suites silently skip
cargo run --bin hotel-app-be                  # :3030 (bare `cargo run` errors: multiple bins)
# hotel-web-fe/  — independent gates; vitest transpiles without types
bun run start                                 # Vite :3000 → 127.0.0.1:3030
bun run typecheck && bun run lint:strict && bun run test && bun run build
# hotel-desktop/
bun run dev | build | build:fast | desktop:prepare:force | sync:resources | test:scripts
# hotel-android/
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --console=plain
```

Root `Makefile`: `dev-be`, `lint`, `format`, `typecheck`, `test`, `build`, `install-hooks`, `check-all`, `test-all`, `db-*`, `sync-proto`, `docs-check`.

## Database — PostgreSQL only

`make db-baseline` initializes an empty DB **once**; `make db-seed` populates deterministic demo data (`cargo run --bin seed -- --list`; refuses production); `make db-patch` converges a V1 DB, and the backend refuses startup until patched (`core/schema_catalog.rs`). Only `database/postgres/patches/` (`manifest.tsv`, generation 1, head version 32) executes via `apply-patches.sh` and `src-tauri/src/postgres/patches.rs` — details in `hotel-app-be/database/README.md`.

- **PG19 Beta 4 reverted SQL/PGQ and `SPLIT/MERGE PARTITION`** — patches 0015/0016 removed both; apply on `19beta3` before GA dump-and-restore (`docs/guides/deployment.md`).
- **Nothing discovers loose SQL.** Register every `000N_*.sql` in `patches/manifest.tsv`, `deploy/deploy.sh`, `deploy/deploy-staging.sh`, and both `.github/workflows/deploy*.yml` (`tests/postgres_patch_catalog.rs` checks parity).
- Additive change → baseline **and** a new patch (columns last in `CREATE TABLE`, pg_dump-shaped bodies→PKs→FKs). Prove with `pg_dump --schema-only` diff, then run `bun run sync:resources` in `hotel-desktop/`.
- Shipped patches & the V1 baseline checksum token (`_begin.sql`/`seed.sql`) are **immutable**. `seed.sql` is one transaction that raises on rerun. `.gitattributes` pins `*.sql`/`*.tsv` to `eol=lf`.

## Backend

`modules/<domain>/routes.rs` → auth middleware → `handlers.rs` → `service.rs` → `repository.rs` → `models.rs`. All 40 domains live in `modules/<domain>/` (38 routers `.merge()`d in `routes/mod.rs::create_router`; `consent` and `booking_groups` are internal). Residual globals: `routes/mod.rs`, `services/{audit,account_emails,google_identity,invoice_numbers}.rs`, `repositories/`, `models/`, `src/grpc/` (5 tonic adapters at `/hotel.*`, `make sync-proto`).

- `core/middleware.rs` (`require_auth`, `check_permission`, `<resource>:manage`); `core/db.rs` (`hotel_today()`, `system_settings.timezone` sets SQL `CURRENT_DATE`); `core/sql_compat.rs` (`param!(N)`, `current_timestamp()`, `current_date()`).
- `core/i18n.rs` (`en`/`ms`/`zh`/`zh-TW`, `core/locales/`), `services/audit.rs`, `utils/sanitization.rs::Sanitizer`.
- `main.rs` spawns advisory-locked loops (`core::leader::spawn_exclusive`) + `core::cache_bus` `pg_notify` fan-out. Rate limits use `rate_limit_buckets` (`RateLimiters::new(pool)`). Route changes require regenerating `docs/api/openapi.json` via `HOTEL_APP_UPDATE_OPENAPI=1 cargo test --all-features --test openapi_drift`.
- `sqlx` is runtime-checked only — new `FromRow` over dates/timestamps/numeric/arrays needs a live-PG test.

## Frontend & Android

- **Web (`hotel-web-fe`)**: 26 features (`src/features/`), 24 `src/api/*.service.ts` wrapping `ky` in `src/api/client.ts` (never raw `fetch`), TanStack Query (no client store). Register pages in `src/routes/` and `src/navigation/routeRegistry.tsx` (53 routes).
- Vite proxies `/api`, `/uploads`, `/health`, `/ws`, `/hotel.` (`PROXY_PREFIXES` must match `ROOT_API_PREFIXES` in `runtimeApi.ts`; `origin-parity.test.mjs` checks). `deploy/Caddyfile` routes `/hotel.`, while `deploy{,-staging}.sh` do not yet (keep gRPC flags off outside dev).
- ESLint `lint:strict` bans `toISOString().split/.slice` (`utils/date.ts`), `*.response.json()` (`readErrorData`), and `replace(/_/g,' ')` (`formatStatusLabel`). UI uses `--hotel-*` tokens (`src/theme/tokens.ts`); `Intl`-based i18n across 30 namespaces.
- **Android (`hotel-android`)**: 13 features (`feature/`), 13 Retrofit APIs, 12 repositories, Android Keystore `EncryptedCookieJar`, `BiometricPrompt` cold lock, Credential Manager passkeys, guest-only guard, SQLite offline cache + WorkManager, Glance widget, `PrintManager`.

## Testing & CI

Backend: 83 files in `tests/` (79 read `DATABASE_URL`; skip = PASS in 0.00s — judge by wall-clock, 0 `#[ignore]`). Frontend: 337 Vitest files (17 in `salim-inn/`). Android: 27 test files (67 tests incl. env-gated `LiveBackendE2eTest`).
`ci.yml` runs 9 jobs (secrets/audit, Markdown link check, proto, DB/proto mirrors, FE, BE, PG suite, desktop placeholder builds). `android-ci.yml` runs unit tests, lint, and debug APK. `deploy.yml` triggers only from green `master` CI.
Never read real `.env` files into docs/logs. Required prod backend vars: `DATABASE_URL`, `JWT_SECRET` (≥32 chars), `TOTP_ENCRYPTION_KEY`, `ALLOWED_ORIGINS` (HTTPS, non-localhost), `PASSKEY_RP_ID`.

## Changing existing behavior

Treat existing behavior as spec unless buggy or unsafe. Do not alter routes, status codes, fields, permissions, storage keys, or column semantics in a refactor. Financial, tax, auth, RBAC, eKYC, ledger, and night-audit changes require targeted tests or approval. `hotel-backup` v1 standard/full/backup scopes exclude secrets, RBAC grants, sessions, `user_guests`, and eKYC (encrypted super-admin `system` scope only). Verify `git status --short --branch` in shared trees; sibling worktrees live in `.worktrees/`.
