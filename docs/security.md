# Security, Readiness & Operations

Canonical production security runbook, PDPA retention & breach procedures, backup/restore drill, readiness assessment (`2026-11-25`), and go-live checklist. Root vulnerability-reporting policy lives in [../SECURITY.md](../SECURITY.md).

## Table of Contents

1. [Release & Runtime Security Controls](#1-release--runtime-security-controls)
2. [Operational Runbooks](#2-operational-runbooks)
3. [PDPA Compliance, Requests & Breach Response](#3-pdpa-compliance-requests--breach-response)
4. [Data Retention Policy (Active + Draft Matrix)](#4-data-retention-policy-active--draft-matrix)
5. [Backup & Quarterly Restore Drill](#5-backup--quarterly-restore-drill)
6. [Production Readiness Assessment & Backlog](#6-production-readiness-assessment--backlog)
7. [Production Go-Live Checklist](#7-production-go-live-checklist)

---

## 1. Release & Runtime Security Controls

- **Release gates**: CI ([`.github/workflows/ci.yml`](../.github/workflows/ci.yml), [`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml)) and Security (`security.yml`: Gitleaks, CodeQL, `cargo audit`, `bun audit` + dependency review) must pass cleanly before deployment. Production deploys run strictly from green `master` CI runs.
- **Production environment invariants** (startup refuses invalid combinations in `hotel-app-be/src/core/config.rs`):
  - `ENVIRONMENT=production` (or `APP_ENV=production`)
  - HTTPS-only non-localhost `ALLOWED_ORIGINS` and valid `PASSKEY_RP_ID`
  - `JWT_SECRET` (≥32 chars, no placeholder prefixes)
  - `TOTP_ENCRYPTION_KEY` set and valid (base64/hex of 32 bytes or ≥32 ASCII chars — required in production startup so TOTP seeds are always AES-256-GCM encrypted at rest)
  - `SKIP_EMAIL_VERIFICATION=false`
  - Valid non-sandbox `PAYPAL_API_BASE` when `PAYPAL_ENABLED=true`
- **Database privilege separation**: Production and staging deploy scripts apply catalog patches as `hotel_admin`, provision the restricted `hotel_app` runtime role (`deploy/db-least-privilege.sql` + patches `0017`/`0024`), and run the backend under `hotel_app` with `HOTEL_APP_PASSWORD`. Never use `hotel_admin` for runtime API requests.
- **Native Android client security (`hotel-android/`)**: Staff-only login/session guard rejects guest-only accounts (`isGuestOnlyAccount`), JWT access tokens live in memory, refresh cookies use AES-GCM encryption backed by Android Keystore (`EncryptedCookieJar`), cold-launch `BiometricPrompt` gates cached sessions, RBAC filters routes/deep-links/search/actions, and release builds enforce `ALLOW_CLEARTEXT=false` + R8 shrinking ([guides/android.md](guides/android.md)).
- **Desktop client boundary (`hotel-desktop/`)**: Offline/local runtime is explicitly outside web production server controls (`postgres-password.txt` mode `0600` in app data dir; local managed backups under `<data dir>/backups/`, [guides/desktop.md](guides/desktop.md)).

---

## 2. Operational Runbooks

### Monthly Access Reviews & Incident Response

Monthly: review active staff accounts, assigned RBAC roles, passkeys, and `audit_logs`; remove dormant accounts and force credential re-enrollment on uncertain ownership.

For suspected account or token compromise:

1. Deactivate the account or reset its password in User Management (`manager` or `super_admin` per patch `0031`), revoking active sessions.
2. Copy relevant application, Caddy journald, and GitHub Actions logs off-host before cleanup.
3. Audit `audit_logs` and DB state across the incident window.
4. Rotate `JWT_SECRET` (invalidates all staff access tokens), SSH/API credentials, and confirm CORS/proxy rules.
5. Require independent reviewer approval before reactivation.

### Backup Health Alerting & Off-Site Shipping

- `deploy/check-backup-health.sh` fires transition-deduped webhook POSTs (`{"text": "saliminn backup-health: UNHEALTHY: …"}` and recovery notifications) when `/opt/saliminn/backup-alert.env` sets `SALIMINN_ALERT_WEBHOOK=<url>`.
- Nightly `saliminn-backup.timer` (`database-backup.sh`) supports encrypted off-site sync (`age` + `rclone copy` + `rclone check`) when `SALIMINN_OFFSITE_REMOTE` and `SALIMINN_AGE_RECIPIENTS_FILE` are configured on `saliminn-backup.service`. **By owner decision (`2026-09-25`)**, production backups remain local-only on the AIC VPS (`"offsite": false` expected; [guides/deployment.md#aic-vps-access--operations](guides/deployment.md#aic-vps-access--operations)).

### Maintenance Mode

No app toggle exists; Caddy returns HTTP `503` while keeping `/health` open:

- `deploy/docker-compose.prod.yml` does not run Caddy; maintenance mode is controlled in the host Caddy configuration (`/etc/caddy/saliminn.Caddyfile` or `deploy/Caddyfile` via `MAINTENANCE_MODE=on`).
- **Host Caddy (`/etc/caddy/saliminn.Caddyfile`)**: Insert `@maintenance not path /health` → `respond 503` directly above `@backend` and run `caddy validate && systemctl reload caddy` (`deploy.sh` regenerates the site file automatically on next release).

### Payment Reconciliation (Weekly or Incident)

1. Export period payments (`payments:read`) and compare against PayPal activity logs and bank statements.
2. Inspect missing PayPal/bank entries in the app first (missed webhooks / unentered claims), then app records absent at the processor, and review `system/jobs/failures` (`receipts` / webhook errors).
3. Correct via payment void/re-record screens (patch `0019` closed-day reversals) — never mutate `payments` rows directly in SQL.
4. Log reconciliation outcome and escalations.

---

## 3. PDPA Compliance, Requests & Breach Response

Governing Malaysian & Sarawakian statutes: **PDPA 2010 (Act 709) as amended by PDPA (Amendment) Act 2024 (Act A1727)**, **CPETTR 2024**, **Registration of Guests Act 1965 (Act 381)**, **Service Tax Act 2018 (Act 807)**, **Tourism Tax Act 2017 (Act 791)**, **Income Tax Act 1967 (Act 53)**, and **Control of Smoking Products for Public Health Act 2024 (Act 852)**.

### Data-Breach Response (PDPA s.12B)

1. **Contain**: Deactivate compromised accounts; switch edge proxy to maintenance mode (`503`) if public intake is exposed.
2. **Preserve**: Export backend logs, Caddy journal, and relevant `audit_logs` partitions off-host.
3. **Scope**: Correlate `audit_logs` (`resource_type`, `action`, `ekyc_document_downloaded`) to identify affected data subjects.
4. **Notify (mandatory statutory windows)**: For breaches causing or likely to cause significant harm:
   - Notify **JPDP (Personal Data Protection Commissioner)** as soon as practicable and **within 72 hours** of awareness.
   - Notify **affected data subjects within 7 days** after initial Commissioner notification.
5. **Remediate & rotate**: Rotate `JWT_SECRET`, DB/API credentials, review independently before closing.

### Data-Subject Requests & eKYC Biometric Withdrawal (PDPA ss. 30, 34, 40, 42, 43A)

1. Verify identity or lawful parental/guardian authority (`Regulations 2013 r.4` for minors <18).
2. **21-day clock**: Fulfill Data Access (s.30), Correction (s.34, plus self-service `PATCH /api/guest-portal/me/profile`), and Portability (s.43A JSON/CSV) within **21 calendar days** (written extension notice up to +14 days if strictly necessary).
3. **eKYC image erasure on verified s.42 withdrawal (manual runbook today)**: After checkout with no active legal hold, verify `ekyc_verifications` image paths resolve strictly inside `/opt/saliminn/private_uploads/ekyc/`, remove image blobs from disk, `NULL` out image path columns while preserving non-biometric verification state/`ekyc_decision_history`, and log an `ekyc_notes` record (earlier tarballs age out on the 7-copy uploads cycle).

---

## 4. Data Retention Policy (Active + Draft Matrix)

Under PDPA s.10, personal data must not outlive its purpose while honoring 7-year Malaysian tax/corporate retention and Registration of Guests statutes. Draft rows require legal counsel sign-off before enabling automated purges.

| Store | Contents | Retention / Policy | State |
|---|---|---|---|
| `refresh_tokens`, `guest_portal_sessions`, `user_sessions` | Expired/revoked sessions | **7 days** post-expiry/revocation (`session_purge_*_days`, cap `10` active/user) | **Live** — hourly `modules/auth/session_retention.rs` (patch `0017`) |
| `private_uploads/data-transfer` | Staged backup imports | Deleted on completion/discard; orphans swept after **24h** | **Live** — `modules/data_transfer/jobs.rs` |
| `job_runs` | Background scheduler history | Pruned during night audit upkeep | **Live** — `modules/night_audit/scheduler.rs` |
| `rate_limit_buckets` | Fixed-window counters | Pruned every 10 min | **Live** — `main.rs` maintenance loop |
| Host logs & backups | App/Caddy logs; dumps & uploads tarballs | Logs **14d** (`logrotate`); **14** nightly / **5** predeploy dumps, **7** upload tarballs | **Live** — host cron/systemd |
| `audit_logs` partitions | Partitioned append-only trail (`0022`) | **Proposed:** 24 months, then owner `hotel_admin` detach & `DROP TABLE` | **Draft** — awaits legal sign-off |
| `private_uploads/ekyc` | Biometric selfies & ID scans | **Proposed:** stay + 12 months post-checkout (or earlier s.42 withdrawal) | **Manual runbook** — automated purge awaits sign-off |
| `guests` (inactive non-financial PII) | Inactive contacts (`hard_delete` FK-blocked) | **Proposed:** PII anonymization 24 months post-last-stay | **Draft** — requires anonymization spec + legal sign-off |
| `email_deliveries` | Outbound mail queue/history | **Proposed:** 90 days post-terminal state | **Draft** — awaits legal sign-off |
| `consent_records`, `notification_consent_events` | Explicit PDPA consent proofs | Relationship + statutory limitation (6–7 years) | **Retained** |
| `bookings`, `booking_guests` | Guest register particulars | Statutory retention per Act 381 & Act 791 | **Retained (do not purge)** |
| `invoices`, `customer_ledgers`, `payments`, `night_audit_*` | Financial & tax ledgers | **7 years** statutory retention (Income Tax Act s.82, Companies Act s.245, SST/TTx) | **Retained (do not purge)** |

---

## 5. Backup & Quarterly Restore Drill

Nightly host dumps (`nightly-*.dump` + `uploads-*.tar.gz`) remain on `/opt/saliminn/backups`. Off-host shipping requires an external encrypted storage destination (`age` + `rclone` check).

### Quarterly Isolated Restore Acceptance Drill

1. Decrypt the target dump and matching uploads tarball only on an isolated restore host.
2. Start a fresh PostgreSQL 19 container with an empty volume (never target production).
3. Run `pg_restore --clean --if-exists --no-owner -d hotel_management <dump>`.
4. Extract `uploads-*.tar.gz` into the restore host's data dir and confirm sample eKYC/document paths resolve.
5. Boot the matching backend image against the restored DB and verify `GET /health`.
6. Spot-check bookings, ledgers, invoices, users, guest documents metadata, and audit rows.
7. Record RTO, RPO verification (≤24h implied by nightly schedule), verifier, and remediation gaps.

---

## 6. Production Readiness Assessment & Backlog

Verified `2026-11-25` (incorporating patch `0032_full_audit_schema_fixes`, production `TOTP_ENCRYPTION_KEY` enforcement, `hotel_app` least-privilege role, `booking_groups`, `2026-11-18` PDPA legal corpus, and native Android client `hotel-android/`). **Verdict: Ready with conditions** — application auth, RBAC, SQL parameterization, audit immutability, CSP/CORS, and CI/security pipelines are strong; operator/legal gates remain before calling the system fully certified.

### Open Prioritized Backlog

| # | Sev | Area | Finding & Current Status | Closing Evidence |
|---|---|---|---|---|
| 1 | P1 | Database | Server/CI pin `postgres:19beta3` (desktop `19beta2`); beta clusters require `pg_dump`/`pg_restore` to reach PG 19 GA (patches `0015`/`0016`/`0021` removed all Beta 4 reverts) | Staged `pg_dump`→`pg_restore` rehearsal & GA cutover ([guides/deployment.md](guides/deployment.md)) |
| 2 | P1 | Backup | Off-site `age`+`rclone` wired in `database-backup.sh`, **declined by owner `2026-09-25`** (`"offsite": false` accepted single-VPS risk) | Configured remote destination + verified remote pull/restore |
| 3 | P1 | Database | Least-privilege `hotel_app` runtime role wired in deploy scripts (`0017`/`0024`/`db-least-privilege.sql`) — confirm live on host | `SELECT current_user` on backend connection returns `hotel_app` |
| 4 | P1 | Observability | Backup webhook alerting (`SALIMINN_ALERT_WEBHOOK`) wired; no external webhook/uptime probe provisioned on host, and job/audit failures are in-app only | Live webhook POST on forced fail + external `/health` monitor |
| 5 | P1 | DR | Zero executed restore drill record on file | Completed quarterly drill log (§5) |
| 6 | P1 | Compliance | Session/staging retention live; automated eKYC purge, inactive-guest anonymization, `email_deliveries` prune, and `audit_logs` partition drop await legal sign-off | Signed-off retention policy (§4) + leader-locked purge jobs |
| 7 | P2 | Compliance | Third-party processor / DPA register (`PayPal`, `Google`, `Cloudflare`, `SMTP`, AIC host) not yet documented outside privacy notice | Completed DPA register + counsel sign-off |
| 8 | P2 | Desktop | Auto-updater minisign signing active (`TAURI_SIGNING_PRIVATE_KEY`); OS installers unsigned pending Windows PFX & Apple Developer ID secrets | Signed/notarized installers verified on Win/macOS |
| 9 | P2 | Testing | 83 BE integration files, 337 FE Vitest files, 31 desktop script tests, and 27 Android test files (incl. live E2E) pass; full-DB restore boot smoke, web browser Playwright E2E, and load baseline remain open | Browser E2E + full restore CI check + annual load baseline |

---

## 7. Production Go-Live Checklist

1. **Operator/Host**: Confirm `SELECT current_user = 'hotel_app'`, configure `SALIMINN_ALERT_WEBHOOK` + external `/health` monitor, complete one quarterly restore drill (§5), execute PG 19 GA upgrade when ready ([guides/deployment.md](guides/deployment.md)), provision desktop OS signing certs ([guides/desktop.md](guides/desktop.md)), and configure Android release keystore signing ([guides/android.md](guides/android.md)).
2. **External/Legal**: Counsel sign-off on `2026-11-18` PDPA legal corpus, Sarawak Cap. 64 / DPO disclosures, §4 retention schedule, third-party processor/DPA register, authenticated penetration test, and Cloudflare WAF/SSH review.
3. **Code Follow-up**: Implement approved retention purge jobs (`modules/ekyc`, guest anonymization, `email_deliveries`, `audit_logs` partition drop) and externalize job/audit failure alerts.

Update Backlog (§6), Verdict, and [../README.md](../README.md) Status once all gates carry closing evidence.
