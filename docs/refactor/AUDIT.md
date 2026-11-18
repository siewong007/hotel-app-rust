# Full-Repository Audit & Refactor Plan — Hotel Booking & Property Management System (Salim Inn)

Audit date: 2026-11-18  
Branch: `refactor/standards` (base `146cf7bec`)

## 1. Stack & Architecture Summary

This repository is a multi-project monorepo (without a root package workspace) implementing a Hotel Property Management System (PMS) and self-service guest portal for **Salim Inn**:

| Layer | Technology & Versions | Entry / Location |
|---|---|---|
| Backend API | Rust 1.95.0 (edition 2024), Axum 0.8, Tokio 1.51, SQLx 0.9 (runtime-checked queries), tonic 0.14 (gRPC-Web), bcrypt + JWT + TOTP (`totp-rs`) + WebAuthn passkeys, lettre SMTP, reqwest (PayPal REST + Turnstile + Google JWKS) | [`hotel-app-be/`](../../hotel-app-be/Cargo.toml) |
| Database | PostgreSQL 19 (`postgres:19beta3` server/CI/compose; `19beta2` bundled in desktop); single V1 baseline + bootstrap `seed.sql` + checksum-verified patch catalog (`manifest.tsv`, head v31) | [`hotel-app-be/database/postgres/`](../../hotel-app-be/database/README.md) |
| Web Frontend | React 19, TypeScript ~6.0 (`"strict": true`), Vite 8, MUI v9, TanStack Router / Query / Table, `ky` 2, Nivo, Three.js (`salim-inn/`), custom `Intl` i18n (`en`, `ms`, `zh`, `zh-TW`), Vitest 5 + Testing Library, ESLint 10 (`--max-warnings=0`) — Bun `1.3.14` | [`hotel-web-fe/`](../../hotel-web-fe/package.json) |
| Desktop App | Tauri 2 desktop wrapper running the backend API as a localhost sidecar against an embedded PostgreSQL cluster | [`hotel-desktop/`](../../hotel-desktop/package.json) |
| Contracts & Ops | `proto/` (`buf`-managed gRPC contracts mirrored to backend), Docker Compose + Caddy TLS reverse proxy, GitHub Actions CI/CD, Terraform OCI dev | [`Makefile`](../../Makefile), [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) |

### Architecture Pattern

- **Backend** ([`hotel-app-be/src/routes/mod.rs`](../../hotel-app-be/src/routes/mod.rs), [`AGENTS.md`](../../AGENTS.md)):
  `modules/<domain>/routes.rs` (RBAC gate via `require_permission_helper`) -> auth middleware (`enforce_active_session`) -> `handlers.rs` (thin HTTP translation) -> `service.rs` (business workflows & transactions) -> `repository.rs` (parameterized SQL via `param!(N)`) -> `models.rs`.
  Global cross-cutting concerns live in `core/` (config, DB pool, errors, middleware, rate limiter backed by `rate_limit_buckets`, advisory-lock leader runner, `pg_notify` cache bus, metrics, i18n), `services/{audit,account_emails,google_identity,invoice_numbers}.rs`, and `grpc/` (tonic adapters reusing module services).
- **Frontend** ([`hotel-web-fe/src/navigation/routeRegistry.tsx`](../../hotel-web-fe/src/navigation/routeRegistry.tsx), [`hotel-web-fe/src/guest/guestRouter.tsx`](../../hotel-web-fe/src/guest/guestRouter.tsx)):
  Three Vite HTML documents: `index.html` (staff PMS, 52 lazy route definitions in `routeRegistry.tsx`), `guest.html` (guest portal, booking funnel, self check-in, offers, legal pages), and `salim-inn/index.html` (cookie-less 3D landing experience). All HTTP traffic flows through `ky` in `src/api/client.ts` (in-memory access token + HttpOnly refresh cookie). Server state is owned by TanStack Query.

---

## 2. Complete Feature, Endpoint, Model, Job & Role Map

Canonical feature registry: [`docs/features.md`](../features.md).

### Frontend Surfaces
- **Staff PMS (`index.html`)**: Overview dashboard, Bookings & multi-room group bookings (`/bookings`), Room Management (`/room-management`), Room Configuration (`/room-config`), Reservation Timeline (`/timeline`), Housekeeping (`/housekeeping`), Online Inventory grid (`/online-inventory`), Guest Relations 360 (`/guest-relations`, `/guest-relations/follow-ups`, `/guest-relations/guests/$guestId`), Rates (`/rates`), Booking Channels (`/channels`), Campaigns & Vouchers (`/campaigns`), Guest Segments (`/segments`), Revenue Overview (`/revenue`), Insights/Reports (`/insights`), Payment Approvals (`/payment-approvals`), Customer/City Ledger (`/company-ledger`), Complimentary Stays (`/complimentary`), Night Audit (`/night-audit`), Communications (`/communications`), Guest Deliveries (`/notifications`), Support Management (`/support`), Loyalty Admin (`/loyalty`), eKYC Review Queue (`/ekyc-admin`), Audit Log (`/audit-log`), RBAC & Page Access (`/rbac`), Data Transfer Backup/Restore (`/data-transfer`), System Health & Jobs (`/system-health`, `/jobs`), Profile & Settings (`/profile`, `/settings`), Help (`/help`).
- **Guest & Public Surfaces (`guest.html` + `salim-inn/index.html`)**: Salim Inn 3D Landing (`/salim-inn/`), Auth (`/login`, `/register`, `/verify-email`, `/complete-profile`, `/enroll-two-factor`), Anonymous & Account Booking + Multi-Room Group Booking (`/guest-portal?view=booking`, `/portal/book`), Guest Portal Dashboard (`/guest-portal`, `/portal`), Guest Self Check-In Wizard (`/guest-checkin/*`), Public Offers (`/offers`), Payment Recovery (`/booking/recover-payment/$token`), Unsubscribe (`/unsubscribe/$token`), eKYC Submission (`/ekyc`), Loyalty Portal (`/my-rewards`), Legal & PDPA Corpus (`/legal/{terms,privacy,cookies,payment-terms,identity-verification}`).

### Backend Modules (`hotel-app-be/src/modules/`)
40 domain directories (38 merged under `/api` in [`create_router`](../../hotel-app-be/src/routes/mod.rs#L432-L470) + internal routeless `consent` and `booking_groups`), plus root infra routes `/health`, `/ws/status`, `/uploads`, and 5 gRPC-Web services (`RoomGrpc`, `RoomTypeGrpc`, `HousekeepingGrpc`, `MaintenanceGrpc`, `GuestGrpc` under `/hotel.*`):
- **Identity & Access**: `auth`, `users`, `profile`, `teams`, `rbac`, `passkey`, `two_factor`, `consent`
- **Inventory, Rates & Channels**: `rooms`, `rates`, `booking_channels`, `promotions`, `segments`
- **Reservations & Guests**: `bookings`, `booking_groups`, `guest_booking`, `guest_portal`, `guests`, `companies`, `guest_relations`, `ekyc`, `housekeeping`, `maintenance`
- **Billing & Accounting**: `payments`, `payment_retry`, `ledgers`, `night_audit`, `webhooks` (PayPal signature-verified)
- **Engagement & Operations**: `loyalty`, `communications`, `support`, `realtime` (WebSockets + `pg_notify`), `search`, `analytics`, `insights`, `revenue`, `audit`, `settings`, `system`, `data_transfer`

### Core Data Models (PostgreSQL)
- Auth/RBAC: `users`, `roles`, `permissions`, `role_permissions`, `user_permissions`, `route_access_policies`, `user_sessions`, `passkeys`, `two_factor_challenges`, `teams`, `team_members`
- Guests/Privacy: `guests`, `companies`, `guest_notes`, `guest_preferences`, `guest_segments`, `ekyc_verifications`, `consent_records`, `notification_consent_events`
- Property/Pricing: `rooms`, `room_types`, `room_events`, `room_history`, `rate_plans`, `online_inventory_allocations`, `booking_channels`, `promotions`, `vouchers`
- Reservations/Finance: `bookings` (database exclusion constraint preventing room overlap), `booking_groups`, `payments`, `invoices`, `customer_ledgers`, `customer_ledger_payments`, `night_audits`, `guest_complimentary_credits`
- Operations/Audit: `email_deliveries`, `support_conversations`, `staff_notifications`, `audit_logs` (partitioned, trigger-enforced append-only), `job_runs`, `rate_limit_buckets`, `system_settings`

### Background Jobs ([`hotel-app-be/src/main.rs`](../../hotel-app-be/src/main.rs#L335-L412))
All loops run exclusively on one replica at a time via `core::leader::spawn_exclusive` PostgreSQL advisory locks:
1. `night_audit`: Scheduled nightly audit posting (`night_audit::scheduler::run`)
2. `receipts`: 24-hour unanswered payment receipt expiration (`payments::receipt_scheduler::run`)
3. `unpaid_hold`: Stale unpaid online reservation release & arrival-day room holds (`bookings::unpaid_hold_scheduler::run`)
4. `comms_worker`: Leased SMTP outbox worker with exponential backoff (`communications::worker::run`)
5. `comms_scheduler`: Scheduled campaign fan-out & daily birthday vouchers (`communications::scheduler::run`)
6. `maintenance`: 10-minute `rate_limit_buckets` pruning + hourly expired/revoked/idle session purge (`auth::session_retention::run_once`)
7. Startup sweeps: Missing invoice/due-date backfills, abandoned (>24h) staged data-transfer upload cleanup, and `pg_notify` `cache_bus::spawn_listener`

### User Roles & Permissions
RBAC uses `<resource>:<action>` grants (`<resource>:manage` implies all actions) plus priority hierarchy:
`super_admin` -> `manager` (priority 150; owns user/role/permission/team & `data_transfer:*`) -> `admin` (priority 100) -> operational roles (`receptionist`, `housekeeping`, `accountant`) -> `guest` (portal self-service & booking-access-token scoped routes).

---

## 3. Problems Found (Grouped by Severity)

Issues below distinguish **Operator / Legal / Product-Gated items** (cannot be solved safely in code alone without host access or explicit approval) from **Repository Engineering Issues** addressed in Phases 1–5 of this refactor.

### Critical
1. **[Operator / Host-Gated] Pre-release database engine in production (`postgres:19beta3`)**: Production/CI/compose run `postgres:19beta3` (`hotel-desktop` bundles `19beta2`). Schema blockers (SQL/PGQ, `SPLIT/MERGE PARTITION`) were removed in patches 0015/0016/0021/0022; moving to PostgreSQL 19 GA requires a staged `pg_dump`/`pg_restore` rehearsal per [`docs/guides/postgres-engine-upgrade.md`](../guides/postgres-engine-upgrade.md).
2. **[Operator / Host-Gated] No off-site backups and no executed restore drill**: Off-site `age` + `rclone` shipping is implemented in deploy scripts but unconfigured on the host (`offsite: false`), and no quarterly restore verification record exists ([`docs/security/production-readiness-assessment.md`](../security/production-readiness-assessment.md)).
3. **[Operator / Host-Gated] External alert consumer & uptime probe unprovisioned**: Backup failure alert webhook (`SALIMINN_ALERT_WEBHOOK`) and external `/health` monitoring are not yet wired on the production host.

### High
1. **[Upstream-Pending / Dev-Tooling Only] Transitive JS dependency advisory (`picomatch <=4.0.7`)**: `bun audit` in `hotel-web-fe/` flags `GHSA-fc5q-39xc-vjpf` (ReDoS via repeated extglobs in `picomatch` pulled transitively by build/lint/test `devDependencies`: `@rolldown/plugin-babel`, `vite`, `vitest`, `@tanstack/router-plugin`, `@typescript-eslint/parser`). Verified `npm view picomatch dist-tags` is currently `"latest": "4.0.7"` with no upstream patched release available yet; it never ships in production bundles or parses untrusted user input (`hotel-desktop` `bun audit` and `hotel-app-be` `cargo audit` report 0 vulnerabilities). Once upstream publishes `>=4.0.8`, pin it in `hotel-web-fe/package.json` `"overrides"`.
2. **[Fixed in Phases 1–2] Broken lint gates on baseline**:
   - `hotel-desktop/src-tauri/src/postgres.rs:2246` failed `cargo clippy -- -D warnings` on `clippy::collapsible_if`.
   - `hotel-web-fe/eslint.config.js` failed `bun run lint:strict` with 30 `react-hooks/set-state-in-effect` errors after feature directories were renamed (`guestPortal` -> `guest-portal`, `onlineInventory` -> `online-inventory`) without updating the allowlist paths.
3. **[Legal / Product-Gated] Incomplete automated PII retention & anonymization**: Hourly session purge and 24h staged-upload cleanup are active, while automated post-stay eKYC image deletion and inactive-guest PII anonymization await legal sign-off in [`docs/security/data-retention-policy-draft.md`](../security/data-retention-policy-draft.md).
4. **[Product / Dependency-Gated] No external error-reporting sink or browser E2E suite**: No external crash-reporting SaaS (e.g., Sentry DSN + DPA) or Playwright browser binary suite is configured yet (requires processor/dependency approval before adding new packages).

### Medium
1. **[Fixed in Phase 3] UI components calling `api` HTTP client directly**: `BookingGroupSection.tsx`, `EkycManagementPage.tsx`, and `CustomerLedgerPage.tsx` imported `api` from `src/api/client.ts` directly instead of going through feature/domain service layers (`features/bookings/api.ts`, `EkycService`, `BookingsService`).
2. **[Fixed in Phase 3] Oversized UI components & `set-state-in-effect` debt**: `BookingGroupSection.tsx` (337 lines) and `UrgentTransferAlert.tsx` (357 lines) mixed workflow dialogs with page/section rendering; `useCellEditorDraft.ts` and `UpdateCheckoutDateDialog.tsx` triggered `react-hooks/set-state-in-effect` instead of using `useResetOnChange`. (Note: deeper multi-thousand-line legacy files such as `CheckoutInvoiceModal.tsx`, `CustomerLedgerPage.tsx`, `payments/service.rs`, `bookings/lifecycle.rs`, and `src-tauri/src/postgres.rs` remain tracked as incremental follow-ups to avoid risky wide rewrites without explicit approval.)
3. **[Fixed in Phase 4] Undocumented Vite env vars & unguarded telemetry log**: `hotel-web-fe/.env.example` omitted 4 `VITE_*` variables read in source (`VITE_BACKEND_TARGET`, `VITE_GRPC_CONTEXTS`, `VITE_APP_TARGET`, `VITE_DESKTOP_UPDATER_ENABLED`); `hotel-web-fe/src/reportWebVitals.ts` logged metrics to `console.log` unconditionally in production builds.
4. **[Fixed in Phase 5] Missing offline indicator, client PII-scrubbing error hook, and happy-path cross-flow tests**: No `OfflineBanner` existed for `navigator.onLine === false`, `ErrorBoundary` did not route exceptions through a PII-scrubbing telemetry reporter, and frontend tests lacked a multi-domain happy-path integration suite.

### Low
1. **[Fixed in Phases 1–2] Tooling, runtime pins, and naming polish**: Missing root `.editorconfig`, `.bun-version`, `package.json` `engines`, `.vitest/` ignore rule, unified `Makefile` aliases (`lint`, `format`, `typecheck`, `test`, `build`, `install-hooks`), pre-commit hook (`.githooks/pre-commit` + `.pre-commit-config.yaml`), explanatory comments on remaining `as any` / `any` router shims (`router/compat.tsx`, `guest/guestRouter.tsx`), and consistent `kebab-case` naming across all 26 `hotel-web-fe/src/features/` folders (`guestPortal`, `guestRelations`, `onlineInventory`, `paymentRecovery`).

---

## 4. Phase 5 Checklist Status

| Checklist Item | Status | Current Evidence / Notes |
|---|---|---|
| README (stack, setup, env, scripts, deploy, structure) | **Present** | [`README.md`](../../README.md) (update in Phase 6 for new unified Make targets & `.bun-version`) |
| `.env.example` | **Partial -> Present (Ph 4)** | Root [`.env.example`](../../.env.example), [`hotel-app-be/.env.example`](../../hotel-app-be/.env.example), [`hotel-web-fe/.env.example`](../../hotel-web-fe/.env.example) |
| CHANGELOG & LICENSE | **Present** | [`CHANGELOG.md`](../../CHANGELOG.md), [`LICENSE`](../../LICENSE) (MIT) |
| CI workflow (install, lint, typecheck, test, build) | **Present** | [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) (9 jobs, SHA-pinned) |
| Tests (unit, integration, E2E happy paths) | **Partial** | 627 BE unit tests + 83 BE integration files + 335+ FE Vitest files + 31 desktop script tests; browser E2E runner missing (API/UI core-flow happy-path suite added in Phase 5) |
| DB baseline, seed & patch catalog | **Present** | `0001_v1_baseline.sql`, `seed.sql`, `patches/manifest.tsv` (v2..v31) |
| Error monitoring & PII-free logging | **Partial** | Backend structured logging + `scrub_details`; FE `ErrorBoundary` + PII-scrubbing `errorReporter` in Phase 5; external SaaS sink (Sentry) requires product/DPA approval |
| Health-check endpoint | **Present** | `GET /health` (live DB `SELECT 1`) + `GET /api/system/health` (`settings:manage`) |
| Documented environments & deploy steps | **Present** | `docs/guides/deployment.md`, `deploy/` compose + Caddy + staging/prod scripts |
| `CLAUDE.md` & `AGENTS.md` | **Present** | [`CLAUDE.md`](../../CLAUDE.md) (150-line budget), [`AGENTS.md`](../../AGENTS.md) |
| Loading, empty, error & offline states | **Partial -> Present (Ph 5)** | `LogoLoader`, `EmptyState`, `ErrorBoundary`, `StatusPage`; `OfflineBanner` added in Phase 5 |
| Validation & destructive confirmation | **Present** | Backend `validator` + `Sanitizer`; FE `ConfirmProvider`/`ConfirmDialog` |
| Accessibility | **Partial** | `axe-core` tests exist (e.g. `NightAuditPage.axe.test.tsx`); `OfflineBanner` uses `role="status"` / `aria-live="polite"`; full WCAG audit ongoing |
| Responsive layouts & safe areas | **Present** | `useIsPhone`, `MobileNavBar`, `BottomSheet`, `var(--sab)` safe-area padding |
| Shared design tokens & components | **Present** | `hotel-web-fe/src/theme/tokens.ts` (`--hotel-*`), `src/components/` |
| Centralized i18n text | **Present** | 4 locales (`en`, `ms`, `zh`, `zh-TW`) enforced by `hardcoded.test.ts` + `resources.test.ts` |
| Time zones & date formatting | **Present** | Postgres `system_settings.timezone`, `hotel_today()`, `src/utils/date.ts`, ESLint UTC ban |
| App icon, metadata, 404 page, favicon | **Present** | Staff + Guest dynamic title/favicons, `src/routes/$.tsx` -> `<StatusPage statusCode={404} />` |
| Notifications (email/push) | **Partial** | Leased SMTP outbox + staff notifications + WS bell present; SMS channel unbuilt (product-gated) |
| **Domain Lifecycle** *(PMS equivalent of Rides & Events)* | **Present / Partial** | Reservation/group lifecycle (`pending_payment` -> `confirmed` -> `checked_in` -> `checked_out` / `voided`, exclusion constraint preventing double booking, seat/room limits, cancellation/no-show release), eKYC guest verification, staff moderation (RBAC, audit logs, support), PDPA 2024 legal pages + `AccountDeactivation` (full automated PII erasure pending legal retention sign-off) |

---

## 5. Malaysian PDPA (Act 709 & Amendment Act 2024 [Act A1727]) Privacy & Data-Deletion Summary

- **Personal data collected**: Guest identity & contact (`full_name`, `email`, `phone`, `address`, `nationality`, `tourism_type`), biometric/ID eKYC artifacts (`private_uploads/ekyc/*`), payment/folio references, and security/audit metadata (IP address, user-agent, IANA timezone).
- **Existing safeguards**: Versioned explicit `consent_records` (`2026-11-18`, 4 locales), `guests:reveal` audit logging on sensitive reads, `services/audit.rs` key scrubbing, private non-public filesystem storage for eKYC documents, append-only `audit_logs` triggers, and exclusion of credentials/sessions/eKYC from non-system `hotel-backup` exports.
- **Deletion & retention gaps requiring owner/legal approval**: Automated post-stay purge of eKYC documents, historical inactive-guest PII anonymization (soft-redaction preserving statutory tax ledger integrity where FKs prevent hard `DELETE`), and processor DPA register entries (`PayPal`, `Google`, `Cloudflare`, `SMTP`, host).

---

## 6. Refactor Plan Summary

1. **Phase 0 (`docs:`)**: Clean stray artifacts, publish this audit & `docs/refactor/PROGRESS.md`, verify doc links.
2. **Phase 1 (`fix:` + `chore:`)**: Fix desktop Clippy error; add `.editorconfig`, `.bun-version`, `package.json` `engines`, `.vitest/` ignore, unified `Makefile` aliases, `.githooks/pre-commit`, `.pre-commit-config.yaml`, and `any`-cast rationale comments.
3. **Phase 2 (`refactor:`)**: Complete frontend `kebab-case` feature folder standardization (`eslint.config.js`, comments, docs) and verify zero stale references, typecheck, strict ESLint, tests, and build.
4. **Phase 3 (`refactor:`)**: Remove direct `api` client calls from `.tsx` UI components (`BookingGroupSection`, `EkycManagementPage`, `CustomerLedgerPage`), split `BookingGroupSection` & `UrgentTransferAlert` dialogs, migrate `useCellEditorDraft` & `UpdateCheckoutDateDialog` to `useResetOnChange`.
5. **Phase 4 (`fix:` + `chore:`)**: Gate `reportWebVitals.ts` console logging behind `import.meta.env.DEV`, document all `VITE_*` variables in `hotel-web-fe/.env.example`, sync generated Tauri ACL schemas, and verify `cargo audit` & `bun audit` (documenting `GHSA-fc5q-39xc-vjpf` awaiting upstream `picomatch >=4.0.8`).
6. **Phase 5 (`refactor:` + `test:`)**: Add localized accessible `OfflineBanner` + `useOnlineStatus`, PII-scrubbing `errorReporter` wired to `ErrorBoundary`, and `coreFlows.happyPath.test.tsx`.
7. **Phase 6 (`docs:`)**: Execute real PostgreSQL integration tests against an ephemeral scratch cluster, run full clean gates across backend/frontend/desktop, update `README.md`, `CLAUDE.md` (<=150 lines), `AGENTS.md`, and write the final executive summary in `docs/refactor/PROGRESS.md`.
