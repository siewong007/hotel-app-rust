# Refactor Progress Log (`refactor/standards`)

- **Branch**: `refactor/standards` (branched from `146cf7bec`)
- **Current Phase**: Phase 0 complete — starting Phase 1

## Phase Tracker
- [x] **Phase 0**: Understand the project & publish [`AUDIT.md`](AUDIT.md)
- [ ] **Phase 1**: Tooling and standards
- [ ] **Phase 2**: Structure and naming
- [ ] **Phase 3**: Code quality
- [ ] **Phase 4**: Security, privacy and performance
- [ ] **Phase 5**: Fill the gaps
- [ ] **Phase 6**: Final check & documentation sync

## Bug Fixes (`fix:` commits)
- *(Pending Phase 1 & Phase 4)*

## Decisions
- Preserve 6 pre-existing untracked files from concurrent sessions (`0032_*`, `0033_*` SQL patches in backend + desktop mirror, `backup_completeness_check.rs`, `role_hierarchy.rs`) untouched and unstaged.
- Keep `CLAUDE.md` within its hard 150-line budget and keep `CLAUDE.md` + `AGENTS.md` consistent.
- Leave ask-first items (database schema changes, auth/RBAC logic, CI workflow edits, major dependency upgrades, new external SaaS dependencies like Sentry/Playwright, and PDPA retention period policy) unbuilt and listed as open questions for owner sign-off.

## Open Questions / Awaiting User Decision
1. **PostgreSQL 19 GA upgrade**: When ready to run the `pg_dump` -> `pg_restore` rehearsal from `19beta3` to PG 19 GA (and bump desktop `19beta2`) per [`docs/guides/postgres-engine-upgrade.md`](../guides/postgres-engine-upgrade.md).
2. **Off-site backups & restore drill**: Whether to provision an off-site encrypted bucket (`age` + `rclone`) and schedule the first quarterly restore drill per [`docs/security/production-go-live-checklist.md`](../security/production-go-live-checklist.md).
3. **External alerting & error monitoring (Sentry / webhook)**: Whether to configure `SALIMINN_ALERT_WEBHOOK` on the host and adopt Sentry (requiring new package + PDPA processor DPA approval).
4. **PDPA retention schedule sign-off**: Retention windows for automated post-stay eKYC image purging and inactive-guest PII anonymization in [`docs/security/data-retention-policy-draft.md`](../security/data-retention-policy-draft.md).
5. **Browser E2E runner & Desktop OS signing certs**: Whether to add Playwright as a dev dependency and provision Windows PFX / Apple Developer ID signing credentials.
