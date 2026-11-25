# Feature Registry

Canonical list of what the application currently supports. Statuses:
**Delivered** (implemented end to end), **Partially Delivered** (usable subset
shipped), **Experimental**, **Deprecated**, **Not delivered**, **In progress**.

| Feature | Status | Frontend | Backend | Notes |
|---|---|---|---|---|
| Staff authentication (JWT + refresh) | Delivered | ✓ `features/auth` | ✓ `modules/auth` | In-memory access token, HttpOnly refresh cookie, revocable refresh rows; hourly session retention (`session_purge_expired_days`, `session_purge_revoked_days`, `session_idle_days`) + `session_max_active_per_user` cap (patch 0017) |
| Two-factor (TOTP or passkey second factor + backup codes) | Delivered | ✓ | ✓ `modules/two_factor`, `modules/passkey` | Secrets encrypted at rest via `TOTP_ENCRYPTION_KEY` (`enc1:` prefix; required in production startup). Sign-in second step: authenticator code (default) or passkey (`/auth/passkey/2fa/*`, 5-min single-use `mfa_token`); wrong codes count toward lockout. |
| Passkeys (WebAuthn) | Delivered | ✓ | ✓ `modules/passkey` | Enrollment step-up; revoked on password change/reset |
| Google sign-in / One Tap | Delivered | ✓ | ✓ (`services/google_identity.rs`) | Staff and guest surfaces |
| Email verification | Delivered | ✓ `/verify-email` | ✓ | `SKIP_EMAIL_VERIFICATION` bypass for desktop/dev |
| RBAC roles & permissions | Delivered | ✓ `features/admin/components/rbac` | ✓ `modules/rbac` | `<resource>:<action>`; `:manage` implies all; route-layer enforcement; `manager` (priority 150) outranks `admin` (100) and owns user, role, permission, team, and `data_transfer:*` management (patch 0031). Access Control → Page access edits each page's `route_access_policies` rule with per-role preview; super admins create custom permissions |
| User & team management | Delivered | ✓ `features/admin`, `features/user` | ✓ `modules/users`, `modules/teams` | Owned by `manager` and `super_admin` (patch 0031); `teams*` and `user_guests` excluded from standard/full/backup `hotel-backup` v1 scopes |
| Rooms & room types | Delivered | ✓ `features/rooms` (`/room-management`, `/room-config`, `/timeline`) | ✓ `modules/rooms` | Status board, room events, room-type photos (low-photo `<3` badge in room config + full-screen guest photo lightbox), bulk room-type price update with preview, audit and one-step revert (`room-type-prices/*`) |
| Housekeeping | Delivered | ✓ `features/housekeeping` | ✓ `modules/housekeeping` | Shared room-status dialog with rooms |
| Maintenance | Delivered | ✓ | ✓ `modules/maintenance` | |
| Bookings (create/check-in/checkout/void) | Delivered | ✓ `features/bookings` | ✓ `modules/bookings` | Deposits, folio, transfers; allocation race-safe (`FOR UPDATE` / `SKIP LOCKED`); `late_checkout` status & overlap guard + complimentary exclusion (patch 0032); arrival-day-only room holds (`reconcile_room_holds`, patch 0018); soft smoking-room preference (patch 0009); OTA/online check-in holds back desk charges (Tourism Tax, extra bed, upgrade diff) for reception settlement |
| Multi-room / group bookings | Delivered | ✓ `features/guest-portal`, `features/bookings` | ✓ `modules/booking_groups`, `bookings/group_*`, `guest_booking/group.rs` | Multi-room reservations (`booking_groups`, patches 0025–0027, 0029), unified group PayPal/bank-transfer checkout, proportional per-room voucher split & resplit, group hold expiry, staff group check-in/cancel |
| Online inventory / availability grid | Delivered | ✓ `features/online-inventory` (`/online-inventory`) | ✓ `online_inventory_allocations` | Changes need `online_inventory:manage` (patch 0010). Optimistic concurrency: each cell carries `expected_updated_at`, so a stale write is rejected rather than overwriting. |
| Revenue overview | Delivered | ✓ `features/revenue` (`/revenue`) | ✓ `modules/revenue` | ADR/RevPAR, occupancy, channel mix, receivables ageing, period comparison |
| Rates, rate plans, rate codes | Delivered | ✓ `features/rates` (`/rates`) | ✓ `modules/rates` | Plan CRUD, rate calendar, bulk bands; market/rate codes, channel net revenue; room and extra-bed rates are SST-inclusive (`service_tax_rate` extracted at night audit / invoice), Tourism Tax (`tourism_tax_rate`, RM10/night) is additive for foreign tourists |
| Booking channels | Delivered | ✓ `features/channels` (`/channels`) | ✓ `modules/booking_channels` | `bookings.booking_channel_id` is canonical attribution; channel pricing rules (markup/discount/fixed/net-rate) + dated commission rules + preview/matrix; `settlement_payment_methods` tender lock (patch 0023) |
| Guests & companies | Delivered | ✓ `features/guests` | ✓ `modules/{guests,companies}` | Corporate accounts + city-ledger links; `nick_name` booking display name + `guest_name_taken` conflict code |
| Guest relations (CRM 360) | Delivered | ✓ `features/guest-relations` (`/guest-relations/*`) | ✓ `modules/guest_relations` | Overview dashboard, follow-up queue, interactions, preferences, reviews — see `architecture/overview.md` |
| Invoices & folio | Delivered | ✓ `features/invoices` | ✓ `modules/payments` (`/api/invoices*`), `services/invoice_numbers.rs` | Numbered invoices, SST/Tourism Tax breakdown, checkout receipts + PDF email attachment (`krilla`) |
| Payments (PayPal + staff-recorded) | Delivered | ✓ | ✓ `modules/payments` | Idempotency keys + fingerprints; conflict audit events; payment void reason + closed-day night-audit reversing journal entries (`void_reversal_date`, patch 0019) + reception `payments:delete` void permission (patch 0020). Bank-transfer claims await staff approval (`payments:read`); PayPal payments cannot be approved by hand |
| Payment approvals | Delivered | ✓ `/payment-approvals` | ✓ `/api/admin/payments/*` | Incl. `paypal-conflicts` endpoint (`payments:read`) |
| PayPal webhooks | Delivered | — | ✓ `modules/webhooks` (`/api/webhooks/paypal`) | Signature-verified, IP-limited, no bearer by design |
| PayPal auto-refund/dispute apply | Partially Delivered | — | ✓ | Refund webhooks verified + audit-logged, not auto-applied (manual reconciliation, open product call) |
| Customer / city ledgers | Delivered | ✓ `features/admin/components/CustomerLedger` (`/company-ledger`) | ✓ `modules/ledgers` | Company payment allocation across entries in one tx; summary nets debit/credit amounts and excludes synthetic reversal settlements from cash collected |
| Deposit refunds | Delivered | ✓ | ✓ | Bounded by held deposit; one-refund-per-booking + partial refunds; `deposit_forfeited` payment type (patch 0002) |
| Payment retry / recovery | Delivered | ✓ `features/payment-recovery` (`/booking/recover-payment/$token`) | ✓ `modules/payment_retry` | Public token page reached from payment-rejected email |
| Campaigns (deals + vouchers) | Delivered | ✓ `features/promotions` (`/campaigns`; `/promotions` redirects) | ✓ `modules/promotions` | Derived lifecycle, channel + loyalty-tier targeting, `promotions:approve` publish gate, performance report |
| Vouchers | Delivered | ✓ (via promotions) | ✓ `/api/admin/vouchers*`, guest endpoints | Issue/revoke, summary, portal claim/options |
| Guest segments | Delivered | ✓ `features/segments` (`/segments`) | ✓ `modules/segments` | JSONB rules compiled to bound-parameter SQL; live evaluation, never materialized |
| Loyalty | Delivered | ✓ `features/loyalty` (`/loyalty`, `/my-rewards`) | ✓ `modules/loyalty` | Member portal + admin; realtime hub. Includes **point redemption with an approval workflow**: `POST /api/loyalty/rewards/{id}/redeem`, `GET /api/admin/loyalty/redemptions`, `PUT …/{id}/{approve,reject}` |
| Night audit | Delivered | ✓ `features/admin/components/NightAuditPage` (`/night-audit`) | ✓ `modules/night_audit` | Scheduler loop in `main.rs` |
| Insights / report catalog | Delivered | ✓ `features/insights` (`/insights`; `/reports` redirects) | ✓ `modules/insights`, `modules/analytics` | `report_catalog.rs` registry; arrivals/departures rosters |
| Audit log viewer | Delivered | ✓ `features/admin/components/AuditLogPage` (`/audit-log`) | ✓ `modules/audit` | Partitioned append-only store guarded by row/statement triggers across all partitions (patch 0022) |
| eKYC | Delivered | ✓ `features/ekyc` (`/ekyc`, `/ekyc-admin`) | ✓ `modules/ekyc` | Submission + staff review queue; sensitive-field reveal permissions; PDPA s.40 explicit biometric consent gate |
| Consent ledger (`consent_records`) | Delivered | ✓ `features/legal` (`useConsent`, `ConsentBlock`, `ConsentNotice`) | ✓ `modules/consent` | Version-exact (`2026-11-18`), locale-stamped (`en`/`ms`/`zh`/`zh-TW`), IP + user-agent consent evidence across registration, single/group booking, payment, and eKYC |
| Guest portal (booking, pre-check-in, docs) | Delivered | ✓ `guest/` entry + `features/guest-portal` | ✓ `modules/guest_portal`, `modules/guest_booking` | Booking access tokens, consent gate, self-service profile edit (`PATCH /api/guest-portal/me/profile`), portal WS sockets, guest feedback submission |
| Anonymous online booking | Delivered | ✓ public booking pages | ✓ `guest_booking` — `/api/booking/{offers,room-types,quote,reservations,groups*}` | Single and multi-room bookings without an account; random access token (SHA-256 at rest, stay-relative expiry) gates portal/payment/pre-check-in; IP rate-limited; transactional room allocation; optional `claim_account` later |
| Guest self check-in wizard | Delivered | ✓ `/guest-checkin/*` | ✓ | Token-gated multi-step flow; `auto_checkin` + eKYC-gated auto check-in |
| Communications (email campaigns) | Delivered | ✓ `features/communications` (`/communications`) | ✓ `modules/communications` | lettre SMTP worker; per-guest preferences; transactional sends; `segment_id` audience intersection |
| SMS channel | Not delivered | — | — | Open item; no implementation |
| Support tickets | Delivered | ✓ `features/support` (`/support`) | ✓ `modules/support` | Incl. guest-portal support widget + WS. A new guest conversation, each later guest message, and a paid cancellation request ring the staff bell for support readers. Staff replies do not. |
| Notifications | Delivered | ✓ `features/notifications` (`/notifications` "Guest Deliveries") + topbar bell | ✓ `staff_notifications`, `email_deliveries` | Guest email triggers: checkout receipt, pre-arrival reminder. Staff bell sources include guest support messages and bank-transfer claims. `staff_notifications` reaches pre-existing databases through patch 0013. |
| New reservations strip | Delivered | ✓ `features/dashboard` `NewReservationsStrip` (front-desk board + reports dashboard) | ✓ `new_reservation_visible_time` setting | Lists room-holding reservations checking in today, only after the hotel's clock (07:00 default); a reservation created today after that time appears immediately. Does not hide anything from the timeline, room grid, guest stays or booking list. |
| Data transfer (backup/restore) | Delivered | ✓ `features/admin/components/DataTransferPage` (`/data-transfer`) | ✓ `modules/data_transfer` | Versioned `hotel-backup` v1 JSON: streamed export (standard/full/backup/system scopes) + staged upload→preview→execute→poll import, one active import per database; owned by `manager` and `super_admin` (`data_transfer:*`, patch 0031) + step-up re-auth; credentials/teams/eKYC only in the encrypted super-admin `system` scope — see `guides/data-transfer.md` |
| System settings | Delivered | ✓ `features/user` (`/settings`) | ✓ `modules/settings` | Timezone drives the hotel business day. Also holds hotel currency (`currency` / `public.hotel_currency()`, patch 0030), `default_locale` (patch 0011), `new_reservation_visible_time` (07:00 default, patches 0012/0014), and `session_*` retention knobs (patch 0017). |
| System health & jobs | Delivered | ✓ `features/admin/system` (`/system-health`, `/jobs`) | ✓ `modules/system`, `core/job_runs.rs` | `/api/system/health`, job-failure feed, `job_runs` records (patch 0028), `JobsTable` via shared `DataTable` |
| Help centre | Delivered | ✓ `features/help` (`/help`, `/help/$slug`) | — (content bundles) | One article set per locale (`articles.{en,ms,zh,zhTW}.ts`). zh-TW was converted from zh and still merits a native pass. |
| Legal/policy pages | Delivered | ✓ `features/legal` (`/legal/*`) | — | Booking terms, privacy notice, cookie usage (`/legal/cookies`), payment terms, identity verification, consent prompts (version `2026-11-18`), aligned with PDPA (Amendment) Act 2024, CPETTR 2024, Registration of Guests Act 1965, Act 852, SST/TTx, and Sarawak business disclosures across all four required locales — `guides/internationalization.md` |
| Offers landing | Delivered | ✓ `/offers` (public) | ✓ public `/api/promotions*` | |
| Public landing page (Salim Inn) | Delivered | ✓ `hotel-web-fe/salim-inn/` (standalone document, `/salim-inn/`) | — (static; hands off to the guest booking flow) | Cookie-less visitors to `/` are redirected here by `nginx.conf`. Calibrated 3D flythrough film, real interiors, booking card, Farley neighbourhood guide, live Sibu weather (MET Norway CC BY 4.0 via `/salim-inn/weather.json`) with atmosphere relighting, Google Maps link, FAQ, About Us, CPETTR 2024 registration disclosure, and footer links to Booking Terms (`/legal/terms`), Privacy Policy (`/legal/privacy`), Payment Terms (`/legal/payment-terms`), and Cookie Usage (`/legal/cookies`) in four languages. |
| Complimentary stays | Delivered | ✓ `features/admin/components/ComplimentaryManagementPage` (`/complimentary`) | ✓ `guest_complimentary_credits` | Credit allocation + restoration on void |
| Internationalization (EN + BM + zh-CN + zh-TW) | Delivered | ✓ `src/i18n` | ✓ `core/i18n.rs` + `core/locales/` | Intl-based, in-house (ADR 012); parity tests both sides; `zh-Hant*` tags resolve to `zh-TW`, `zh-Hans*`/bare `zh` to `zh`. Locale precedence: `?lang=` link, then switcher choice, then hotel `default_locale`, then browser. Non-English bundles lazy-load; zh/zh-TW PDF exports embed a CJK font; documented in `guides/internationalization.md` |
| Search (global) | Delivered | ✓ | ✓ `modules/search` | |
| Desktop app (Tauri + embedded PG) | Delivered | ✓ shared | ✓ `hotel-desktop/src-tauri` | Sidecar backend; bundled postgres **19beta2** while the server stack runs 19beta3 (`architecture/overview.md`); macOS aarch64, Windows x64 (NSIS/MSI + portable), Linux x64 (deb/AppImage/rpm + portable); GitHub Releases minisign updater + managed backup/restore — `guides/desktop.md` |
| Native Android staff app | Delivered | ✓ `hotel-android/` (Kotlin + Jetpack Compose M3) | shared REST + WS API | Dashboard, bookings, folio print (`PrintManager`), rooms, housekeeping, maintenance, guest 360, eKYC review, payment approvals, global search, night audit preview, Android Keystore encrypted cookies, `BiometricPrompt`, Credential Manager passkeys, guest-account login guard, strict RBAC, SQLite offline sync + Glance widget (`HotelOpsWidget`) — `guides/android.md` |
| Realtime (WebSocket) | Delivered | ✓ | ✓ `modules/realtime`, loyalty/support hubs | `/api/updates/socket` (staff Web + Android), `/api/admin/loyalty/socket`, `/api/guest-portal/me/{loyalty,support}/socket`, `/api/guest-portal/me/availability`; domain-name payloads + permission-checked refetch; reconnect + lag-drop logging; `pg_notify` cross-replica fan-out (ADR 015) |
| gRPC-Web transport | In progress | ✓ `api/grpc/` + `gen/` Connect-ES clients | ✓ `src/grpc/` (tonic on the same Axum port) | Rooms, room types, housekeeping, maintenance, guests behind per-context flags (default off); REST default/fallback — ADR 014, `architecture/overview.md` |
| Turnstile bot protection | Delivered | ✓ | ✓ `modules/auth/turnstile.rs` | Public guest forms |
| Unpaid online-hold release | Delivered | — | ✓ `modules/bookings/unpaid_hold_scheduler.rs` | `unpaid_hold_release_hours` (24 default, 0 disables) |
| Phone/tablet UX | Delivered | ✓ `useIsPhone`, `MobileNavBar`, per-page phone layouts | — | Phone-first pass across staff + guest surfaces; bookings + online-inventory grids. Shared primitives in `components/common/`: `ActionsMenu`, `BottomSheet`, `CollapsibleSection`, `ResponsiveTabs`, `StickyActionBar` |
| Row-detail drawers | Delivered | ✓ `BookingDetailDrawer`, `GuestDetailDrawer` | — | Row clicks in `/bookings` and `/guest-relations/guests` open a right-side drawer (details + quick-edit + actions); full detail pages still reachable from inside the drawer |
| Auto-refresh | Delivered | ✓ | — | Live pages poll/invalidate on a cadence; sockets cover the realtime domains |
| MCP server | Not delivered | — | — | ADR 009: recorded historically, never implemented |

## Intentional absences

- **No client-state library** — server state is TanStack Query; see ADR 006.
- **No sqlx migration runner** — schema lifecycle is baseline + seed +
  checksum-verified patch catalog; see ADR 010.
- **No external rate-limit store** — counters live in the existing PostgreSQL
  database (`rate_limit_buckets`), so replicas share one budget without adding
  Redis; see ADR 005 (superseded).
- **No SSE endpoint** — real-time updates use WebSocket hubs (domain-name
  payloads + permission-checked refetch); see ADR 015.
- **No message broker / generalized outbox** — cross-replica fan-out uses
  `pg_notify` and outbound email uses the leased `email_deliveries` queue;
  there is no domain-event outbox table and no Redis/NATS (optional future
  infrastructure only).
- **No payroll/HR module** — staff management stops at `teams`/`team_members`,
  and the `seed` binary deliberately seeds no payroll tables. (Re-verified
  2026-09-15: zero `payroll` matches in `src/`, zero payroll tables in the
  baseline.)
- **No inventory / purchasing / stock module** — no purchase orders, suppliers,
  or stock items exist in the schema or code. The only "supplier" matches are
  prose in the privacy notice.
- **No separate "Hot Deals" module** — public offers are promotions.
  `features/promotions` (`/campaigns`, public `/offers`) covers deals and
  vouchers; there is no distinct hot-deals entity.
- **No "Front Desk" module** — front-desk work is served by the bookings, rooms
  and housekeeping features rather than a dedicated surface.
- **No direct LHDN MyInvois API connector** — the system generates itemized
  numbered invoices and receipts; operators falling below Malaysia's mandatory
  LHDN e-Invoice turnover exemption threshold (or filing via the MyInvois
  portal) record taxpayer particulars offline/at check-out rather than
  submitting live CTC API payloads from the backend.
