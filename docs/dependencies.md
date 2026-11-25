# Dependencies

Architecturally significant dependencies and the reasoning behind them — not a lockfile dump. Last reviewed `2026-11-25`.

## Frontend (`hotel-web-fe`)

| Package | Purpose | Notes |
|---|---|---|
| react / react-dom 19 | UI | React Compiler enabled via `babel-plugin-react-compiler` |
| vite 8 | Build/dev server | `@vitejs/plugin-react` + `@rolldown/plugin-babel` |
| typescript 6 | Language | Full `strict` mode + `noImplicitOverride`, `noImplicitReturns`, `erasableSyntaxOnly` |
| @mui/material + @emotion/* | Component system | MUI 9; `@mui/lab` removed (unused) |
| @tanstack/react-router | Routing | File routes + generated `routeTree.gen.ts` |
| @tanstack/react-query | Server state | The only state library by design (ADR 006) |
| @tanstack/react-table 9 | Tables | Behind shared `DataTable`; v9 feature-gated API (`tableFeatures`) |
| ky | HTTP client | Wrapped by `src/api/client.ts` — never call `fetch` directly |
| @connectrpc/connect + connect-web + @bufbuild/protobuf | gRPC-Web (Connect) clients | For the strangler migration only — generated stubs in `src/gen/` (do not hand-edit), dispatched per context by `src/api/grpc/flags.ts`; REST via ky stays default/fallback (ADR 014) |
| Native `Date` + `Intl` (`src/utils/date.ts`) | Dates | No `date-fns` or `dayjs`; business-day math belongs to the backend (`hotel_today`) |
| @nivo/{bar,line,pie,core} | Charts | Dashboard/reports; wrapped by `src/components/charts/` — never imported directly |
| jspdf + jspdf-autotable | PDF export | Receipts/reports (with embedded CJK font for `zh`/`zh-TW`) |
| three + postprocessing + n8ao + three-mesh-bvh + polygon-clipping + lenis + detect-gpu + stats-gl | 3D scenes on the public landing page | Imported only by `salim-inn/` (the flythrough film, interiors, smooth scroll, and GPU tiering). The React app in `src/` never loads them. |
| @paypal/react-paypal-js | PayPal buttons | Guest + staff payment surfaces |
| qrcode.react | QR rendering | 2FA enrollment, share links |
| web-vitals 6 | RUM metrics | `onINP` (FID removed in v6) |
| vitest 5 + jsdom 30 + @testing-library/* | Tests | Three independent gates: `typecheck`, `lint`, `test` |
| eslint 10 + `@typescript-eslint/parser` (`^8.72.0`) | Lint | Parser only — neither the `typescript-eslint` meta-package nor `@typescript-eslint/eslint-plugin` is installed; `eslint.config.js` pairs the parser with `eslint-plugin-react-hooks` v7 (which now bundles React Compiler diagnostics directly; standalone `eslint-plugin-react-compiler` was removed). The parser's `typescript` peer range `<6.1.0` is the TypeScript 7 blocker — see below |

### Why TypeScript 6, not 7

TypeScript **7.0.2** is published, but no released `@typescript-eslint` supports
it. Verified against **npm**, not just `node_modules`, on 2026-09-15:

| Package | Version | `peerDependencies.typescript` |
|---|---|---|
| `@typescript-eslint/parser` (installed) | `^8.72.0` | `>=4.8.4 <6.1.0` |
| `@typescript-eslint/parser` (npm `latest` at check) | `8.72.0` | `>=4.8.4 <6.1.0` |

`@typescript-eslint/typescript-estree` hard-codes the same range as
`SUPPORTED_TYPESCRIPT_VERSIONS`, so on TS 7 the parser throws at module load and
`lint`/`lint:strict` — a CI gate — die even though `tsc --noEmit` passes. There is
no stable *or* pre-release escape today. TS 6 with full strictness is the newest
version this project can actually run. Upstream: typescript-eslint#10940, aimed at
TS ≥ 7.1. Do not retry the bump until that issue closes — re-check with
`npm view @typescript-eslint/parser@latest peerDependencies`.

### Dependency hygiene

Checked via `cargo audit` and `bun audit` in CI (`security.yml`). Direct
dependencies are kept on current stable releases (e.g. `@tanstack/react-router`
`^1.170.46`, `web-vitals` `^6.2.3`, `dompurify` override `^3.4.17`). Re-check
upstream versions with `npm view <pkg> version` and
`curl -H 'User-Agent: …' https://crates.io/api/v1/crates/<crate>` (crates.io
rejects requests without a User-Agent and returns nothing useful).

## Backend (`hotel-app-be`)

| Crate | Purpose | Notes |
|---|---|---|
| axum 0.8 + tower-http 0.7 | HTTP | `catch-panic`, `set-header`, CORS, tracing |
| tonic 0.14 + prost 0.14 | gRPC services | `tonic-web` (Connect/gRPC-Web for the browser), `tonic-reflection`, `tonic-health`, `tonic-types`, `tonic-prost(-build)`; services merged into the same Axum router/port — no sidecar; contracts in `proto/` (ADR 014) |
| sqlx 0.9 | PostgreSQL | Runtime queries only — no `query!` macros, so type/column mismatches need live-DB tests. v9 requires `AssertSqlSafe` on dynamic SQL — every wrapped site is template SQL (constants + `param!` bind placeholders), never user input |
| tokio 1 | Async runtime | |
| jsonwebtoken 11 | JWT | `rust_crypto` provider (HMAC only); exactly one provider required |
| bcrypt 0.19 | Password hashing | On `spawn_blocking` |
| totp-rs 6 | TOTP 2FA | `Totp`/`Builder` API; `check*` returns `Option<step>` (v6) |
| ring | AES-256-GCM | TOTP secret encryption at rest |
| reqwest 0.13 | Outbound HTTP (PayPal, Turnstile) | `rustls` = platform cert verifier (image ships `ca-certificates`); `form` feature for Turnstile siteverify |
| lettre 0.11 | SMTP | `tokio1-rustls-tls` — no OpenSSL anywhere |
| krilla 0.8 | PDF receipts | Checkout-receipt email PDF attachments (`receipt_pdf.rs`) with embedded `NotoSansCJKsc-Regular.ttf` across all 4 locales |
| rust_decimal | Money | Never floats |
| validator 0.21 | Request validation | Derives on request models |
| ammonia | HTML sanitization | Campaign/email HTML |
| sha2 + hmac + constant_time_eq + data-encoding + hex + base64 | Crypto/encoding | Webhook signatures, fingerprints, tokens |
| uuid, chrono, rand 0.10 | Primitives | `v7` UUIDs where ordering matters |
| simplelog + log | Logging | |
| dirs 7 | Data dirs | |
| qrcode + image | TOTP QR images | |
| clap 4 | Helper binaries | `hash_password`, `fix_password`, and the `seed` demo-dataset binary |

Deliberately absent: an ORM (repositories hold explicit SQL), a dedicated
rate-limit store (shared `rate_limit_buckets` table in the existing database —
supersedes ADR 005), webauthn-rs (commented out — OpenSSL conflict on Windows
builds), `lazy_static` (replaced by `std::sync::LazyLock`).

## Desktop (`hotel-desktop/src-tauri`)

| Crate | Purpose | Notes |
|---|---|---|
| tauri 2 + shell/updater/process plugins | Desktop shell | Updater shares the reqwest instance via feature unification |
| reqwest 0.13 | Outbound HTTP | Same `rustls` feature set as backend; `charset`/`http2`/`system-proxy` kept for the updater |
| tokio, serde/serde_json, thiserror 2, log/env_logger | Plumbing | |
| tar 0.4, flate2 1 | Managed backup archives | Uploads `.tar.gz` bundling & extraction (`guides/desktop.md`) |
| rand 0.10, sha2 0.11, hex, bcrypt | Local crypto | Patch checksums, bootstrap credentials |
| dirs 7 | Platform data dirs | Embedded PostgreSQL location |

## Android (`hotel-android`)

Version catalog: `hotel-android/gradle/libs.versions.toml` (`agp 8.13.2`, `kotlin 2.2.21`, `compileSdk`/`targetSdk 36`, `minSdk 26`).

| Library | Purpose | Notes |
|---|---|---|
| Jetpack Compose BOM `2025.10.00` + Material 3 + `material3-window-size-class` | UI & adaptive navigation | Phone `NavigationBar` vs tablet `NavigationRail`; Material 3 icons |
| `androidx.navigation:navigation-compose` `2.9.0` + `androidx.lifecycle` `2.9.0` | Navigation & ViewModels | Strict RBAC route & deep-link enforcement |
| Retrofit `2.11.0` + OkHttp `4.12.0` + `kotlinx-serialization-json` `1.9.0` | HTTP + WebSocket | Reuses backend `/api/*` + `/api/updates/socket`; zero codegen plugins needed |
| `androidx.datastore:datastore-preferences` `1.1.7` + Android SQLite | Preferences & offline store | Server config + offline read cache & pending mutation queue (`HotelLocalDatabase`) |
| `androidx.biometric` `1.1.0` + `androidx.credentials` `1.5.0` | Biometric cold-lock & passkeys | Hardware-backed lock + Credential Manager saved logins/WebAuthn |
| `androidx.work:work-runtime-ktx` `2.10.1` | Background sync & widget refresh | Connectivity-gated `SyncPendingMutationsWorker` + `WidgetRefreshWorker` |
| `androidx.glance:glance-{appwidget,material3}` `1.1.1` | Home-screen widget | `HotelOpsWidget` operational metrics |
| `androidx.core:core-splashscreen` `1.0.1` + `androidx.profileinstaller` `1.4.1` | Cold start UX | System splash integration + baseline profile optimization |
| JUnit `4.13.2` + `kotlinx-coroutines-test` `1.10.2` + `okhttp-mockwebserver` | Testing | Offline ViewModel/network suite + env-gated `LiveBackendE2eTest` |

## Modernization & TLS Notes

- **TLS trust store (`reqwest 0.13`)**: `rustls` uses `aws-lc` + platform cert verifier (honoring enterprise TLS-inspection CAs, paired with `ca-certificates` in containers); `lettre` uses `tokio1-rustls-tls` with zero OpenSSL runtime dependencies.
- **`sqlx 0.9`**: Runtime-checked queries wrap template-built dynamic SQL in `AssertSqlSafe` (literals + `param!` placeholders only; values stay `.bind()`ed); DB TLS runs via `tls-rustls-ring-webpki`.
- **Frontend toolchain pins**: `@mui/lab`, `react-to-print`, `eslint-plugin-react-compiler` (folded into `eslint-plugin-react-hooks` v7), and `lazy_static` (replaced by `std::sync::LazyLock`) were removed in `2026-09`.
