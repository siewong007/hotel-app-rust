# Refactor Progress Log (`refactor/standards`)

- **Branch**: `refactor/standards` (branched from `146cf7bec`)
- **Current Phase**: Phase 5 complete — moving to Phase 6 (Final checks, docs & master merge)

## Phase Tracker
- [x] **Phase 0**: Understand the project & publish [`AUDIT.md`](AUDIT.md)
- [x] **Phase 1**: Tooling and standards (`.editorconfig`, `.bun-version`, `package.json` `engines`, `.gitignore` `.vitest/` ignores, `.githooks/pre-commit`, `.pre-commit-config.yaml`, unified `Makefile` aliases, `any`-cast rationale comments, desktop Clippy fix)
- [x] **Phase 2**: Structure and naming (standardized frontend feature directories `guest-portal`, `guest-relations`, `online-inventory`, `payment-recovery` + updated imports, `eslint.config.js`, comments, and docs)
- [x] **Phase 3**: Code quality (enforced service-layer API boundary across `BookingGroupSection`, `EkycManagementPage`, `CustomerLedgerPage`; extracted `features/bookings/api.ts`, `BookingGroupDialogs.tsx`, `UrgentTransferDialogs.tsx`; migrated `useCellEditorDraft` and `UpdateCheckoutDateDialog` to `useResetOnChange`)
- [x] **Phase 4**: Security, privacy and performance (gated Web Vitals console logging to `import.meta.env.DEV`, documented all `VITE_*` vars in [`hotel-web-fe/.env.example`](../../hotel-web-fe/.env.example), synced Tauri ACL schemas, audited Cargo/Bun dependencies)
- [x] **Phase 5A**: App & UX essentials (added accessible localized `<OfflineBanner />` with `useOnlineStatus` hook, wired into `RootLayout` and `GuestRootLayout` across all 4 locales `en`, `ms`, `zh`, `zh-TW`; added PII-scrubbing `reportClientError` utility and integrated into `ErrorBoundary` emitting `app:client-error` telemetry)
- [x] **Phase 5B**: Verification & happy-path test suite (added [`hotel-web-fe/src/test/coreFlows.happyPath.test.tsx`](../../hotel-web-fe/src/test/coreFlows.happyPath.test.tsx) testing happy path integration across anonymous guest booking, guest portal token verification, staff booking lifecycle & group folio settlement, and staff eKYC compliance & loyalty statistics)
- [ ] **Phase 6**: Final check & documentation sync

## Bug Fixes (`fix:` commits)
- `fix(desktop): collapse nested if in uploads restore for clippy` — collapsed nested `if let` in [`hotel-desktop/src-tauri/src/postgres.rs`](../../hotel-desktop/src-tauri/src/postgres.rs#L2246-L2254) (`cargo clippy -- -D warnings` clean; 31 desktop script tests pass).
- `fix(security): gate Web Vitals console telemetry behind development mode` — wrapped `console.log` in [`hotel-web-fe/src/reportWebVitals.ts`](../../hotel-web-fe/src/reportWebVitals.ts#L13-L23) inside `if (import.meta.env.DEV)` so production sessions do not emit console telemetry logs.

## Decisions
- Preserve 6 pre-existing untracked files from concurrent sessions (`0032_*`, `0033_*` SQL patches in backend + desktop mirror, `backup_completeness_check.rs`, `role_hierarchy.rs`) untouched and unstaged.
- Per [`AGENTS.md`](../../AGENTS.md) (avoid unnecessary dependencies and mechanical repo-wide formatting churn), use zero-dependency `.githooks/pre-commit` (`make install-hooks`) + `.pre-commit-config.yaml` and existing `rustfmt` / ESLint 10 instead of adding Prettier or Husky dependencies across 1,100+ untouched TS files.
- Keep `CLAUDE.md` within its hard 150-line budget and keep `CLAUDE.md` + `AGENTS.md` consistent.
- Leave ask-first items (database schema changes, auth/RBAC logic, CI workflow edits, major dependency upgrades, new external SaaS dependencies like Sentry/Playwright, and PDPA retention period policy) unbuilt and listed as open questions for owner sign-off.

## Open Questions / Awaiting User Decision
1. **PostgreSQL 19 GA upgrade**: When ready to run the `pg_dump` -> `pg_restore` rehearsal from `19beta3` to PG 19 GA (and bump desktop `19beta2`) per [`docs/guides/postgres-engine-upgrade.md`](../guides/postgres-engine-upgrade.md).
2. **Off-site backups & restore drill**: Whether to provision an off-site encrypted bucket (`age` + `rclone`) and schedule the first quarterly restore drill per [`docs/security/production-go-live-checklist.md`](../security/production-go-live-checklist.md).
3. **External alerting & error monitoring (Sentry / webhook)**: Whether to configure `SALIMINN_ALERT_WEBHOOK` on the host and adopt Sentry (requiring new package + PDPA processor DPA approval).
4. **PDPA retention schedule sign-off**: Retention windows for automated post-stay eKYC image purging and inactive-guest PII anonymization in [`docs/security/data-retention-policy-draft.md`](../security/data-retention-policy-draft.md).
5. **Browser E2E runner & Desktop OS signing certs**: Whether to add Playwright as a dev dependency and provision Windows PFX / Apple Developer ID signing credentials.
