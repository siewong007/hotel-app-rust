# Data-Retention Policy (DRAFT — requires legal sign-off for unimplemented stores)

Status: **partial — session & staging-file hygiene live; remaining stores draft**.
Malaysian PDPA's Retention Principle (Personal Data Protection Act 2010 [Act 709]
s.10 — personal data shall not be kept longer than necessary for the fulfilment
of its purpose) must be reconciled with statutory hotel, tax, and company
record-keeping laws by the hotel's designated officer/legal counsel before
automated purges are enabled on long-lived business or audit records.

## Scope & current enforcement state

| Store | Contents | Target / active retention | Enforcement state |
|---|---|---|---|
| `refresh_tokens`, `guest_portal_sessions`, `user_sessions` | expired or revoked auth/portal sessions | **Active:** 7 days after expiry/revocation (`session_purge_expired_days = 7`, `session_purge_revoked_days = 7`, `session_max_active_per_user = 10`, `session_idle_days = 0`) | **Live** — hourly `modules/auth/session_retention.rs` job (patch 0017), audited + logged to `job_runs` |
| Data-transfer staged uploads (`private_uploads/data-transfer`) | temporary uploaded `hotel-backup` files | **Active:** deleted on import completion/discard; orphaned files swept after 24 hours (`STAGED_UPLOAD_TTL`) | **Live** — `modules/data_transfer/jobs.rs` |
| `job_runs` | scheduler execution history | **Active:** pruned during night audit upkeep | **Live** — `modules/night_audit/scheduler.rs` |
| Backend/Caddy logs | request + error logs | **Active:** 14 days local (`logrotate`); longer only if shipped off-host | **Live** — host `logrotate` |
| Nightly backups | DB dump + uploads tar | **Active:** 14 nightly / 5 predeploy DB dumps, 7 uploads tarballs local (local-only by owner decision, 2026-09-25) | **Live** — `deploy/database-backup.sh` |
| `audit_logs` (monthly partitions) | staff actions, auth events, permission changes, data exports | **Proposed:** 24 months, then detach & drop old monthly partitions | **Draft** — DB triggers (`0022_audit_logs_append_only_guards.sql`) block `DELETE`/`TRUNCATE`; purge must `DROP TABLE` detached partitions as owner `hotel_admin` |
| eKYC identity & selfie images (`private_uploads/ekyc`) | MyKad/MyPR/passport scans + biometric selfies (PDPA s.40 sensitive data) | **Proposed:** stay duration + 12 months after check-out (or earlier on verified PDPA s.42 withdrawal), keeping pass/fail status only | **Manual today** (runbook in `production-operations.md`); automated nightly job pending sign-off |
| `guests` (inactive non-financial PII) | contact/identity particulars of guests with no open stays or statutory holds | **Proposed:** anonymise non-statutory PII 24 months after last stay (note: `GuestRepository::hard_delete` currently runs `DELETE FROM guests` and is blocked by booking/support FKs; soft-delete/anonymisation is **not** implemented yet) | **Draft** — requires anonymisation spec + legal sign-off |
| `email_deliveries` | queued/sent guest mail bodies | **Proposed:** 90 days after terminal state | **Draft** — outbox worker cleanup pending sign-off |
| `consent_records`, `notification_consent_events` | PDPA s.7/s.40/s.43 consent & opt-out evidence | **Proposed:** duration of relationship + statutory limitation period (6–7 years) to evidence lawful processing | **Retained** |
| Guest register particulars (`bookings`, `booking_guests`) | arrival/departure & identity particulars | **Statutory:** retained per Registration of Guests Act 1965 (Act 381) & Tourism Tax Act 2017 (Act 791) | **Do not purge** inside statutory window |
| Common-area CCTV (on-site NVR, outside app DB) | 24-hour public-area video without audio | **Operational:** short rolling NVR overwrite cycle (typically 14–30 days) unless preserved for police/incident investigation | **Hardware NVR** |
| Invoices, ledgers, payments, night audit | financial & tax records | **Statutory — do not purge** before 7 years: Income Tax Act 1967 (Act 53) s.82 (7 years), Companies Act 2016 (Act 777) s.245 (7 years), Service Tax Act 2018 (Act 807) & Tourism Tax Act 2017 (Act 791) (7 years) | **Retained** |

## Non-negotiables

- Purge jobs must write an `audit_logs` row and `job_runs` entry per run (counts
  per store, never row contents) so retention itself is auditable.
- Legal hold: an active dispute, chargeback, security incident, or law-enforcement
  request freezes the relevant retention clock.
- eKYC biometric images are the highest-sensitivity store under PDPA ss. 4 & 40:
  minimising image retention while keeping the pass/fail decision record reduces
  breach exposure without weakening operational auditability.
- Financial, tax, and statutory guest-register rows must never be hard-deleted
  inside their 7-year Malaysian statutory retention window; guest erasure
  requests must anonymise only non-statutory fields once stays settle.

## Implementation checklist (for remaining draft rows once approved)

1. Encode each approved period as a `system_settings` key (days), following
   `session_purge_expired_days` / `session_purge_revoked_days` (patch 0017).
2. Run one exclusive `retention` job via `core::leader::spawn_exclusive`
   (batched, leased, audited). For `audit_logs`, partition drops require an
   owner-privileged maintenance script rather than the restricted `hotel_app`
   runtime role.
3. File deletion for eKYC images must canonicalize and verify paths stay strictly
   within `private_uploads/ekyc/` — never `rm` raw column text unchecked.
4. Update `production-readiness-assessment.md` item #5 and `production-go-live-checklist.md`
   rows 7 and 11 once approved and deployed.
