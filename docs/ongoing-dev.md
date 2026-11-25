# Ongoing Development

Single live tracker for remaining open work (`2026-11-25`). Keep entries concise and **delete finished items** rather than striking them through. Shipped features belong in [features.md](features.md); security/readiness blockers belong in [security.md](security.md).

## P0 — Infrastructure & Security Gates Remaining

- **PostgreSQL 19 GA upgrade (`19beta3` server/CI/compose; `19beta2` desktop).** Schema blockers from PG 19 Beta 4 (`hotel_graph` SQL/PGQ and `SPLIT PARTITION`) are already removed by patches `0015`/`0016`/`0021`/`0022` (applied to prod/staging `2026-10-06`). Execute the staged dump-and-restore onto PostgreSQL 19 GA per [guides/deployment.md](guides/deployment.md#postgresql-engine-upgrade-dump-and-restore) once ready, moving desktop `CONFIGURED_POSTGRES_BUILD_IDENTITY` in lockstep ([guides/desktop.md](guides/desktop.md)).
- **Runtime DB privilege verification on live host.** Deploy scripts provision least-privilege `hotel_app` (`deploy/db-least-privilege.sql` + patches `0017`/`0024`); verify `SELECT current_user = 'hotel_app'` on production/staging backend connections.
- **PDPA automated retention & eKYC erasure sign-off.** Automated hourly session purge and 24h staged-upload cleanup are active; automated eKYC post-stay image deletion, inactive-guest PII anonymization, `email_deliveries` purge, and owner-run `audit_logs` partition detach/drop await legal retention-period sign-off ([security.md#4-data-retention-policy-active--draft-matrix](security.md#4-data-retention-policy-active--draft-matrix)).

## P1 — Decided, External / Operator Gated

- **Backup restore drill & webhook monitoring.** Configure `SALIMINN_ALERT_WEBHOOK` + external `/health` uptime probe and execute one quarterly isolated restore drill ([security.md#5-backup--quarterly-restore-drill](security.md#5-backup--quarterly-restore-drill)).
- **Legal review & processor DPA register.** Legal sign-off on `2026-11-18` PDPA corpus, Sarawak Cap. 64 disclosures, and third-party processor/DPA register (`PayPal`, `Google`, `Cloudflare`, `SMTP`, AIC host).

## P2 — Engineering Backlog

- **Desktop OS signing certificates.** Minisign updater signing (`TAURI_SIGNING_PRIVATE_KEY`) is live; provision Windows PFX and Apple Developer ID + notarization secrets and verify signed installers end-to-end ([guides/desktop.md#4-platform-packaging-os-signing--ci](guides/desktop.md#4-platform-packaging-os-signing--ci)). Session persistence across desktop restarts remains an accepted limitation (SameSite cookie origin boundary).
- **Android release signing keystore & distribution.** Debug/release APK builds compile and shrink cleanly (`android-ci.yml`); provisioning a production Play/enterprise upload keystore (`signingConfigs.release`) remains when preparing store/MDM distribution ([guides/android.md](guides/android.md)).
- **Dependabot alert #13 (Linux desktop `glib 0.18.5` soundness).** `gtk 0.18` pins `glib ^0.18` across Tauri 2.x; unfixable until Tauri migrates to GTK 4.
- **Notifications v2 remaining scope.** SMS channel (unbuilt) and DB-editable transactional email templates (checkout receipt PDF attachment shipped).
- **UI/UX table consolidation.** `PageHeader`, `EmptyState`, and `JobsTable` (`DataTable`) are shipped; `DataTable` adoption across remaining legacy admin views remains open.
- **gRPC host edge routing.** `deploy/Caddyfile` already routes `/hotel.*`, whereas the generated host site matchers in `deploy/deploy.sh` and `deploy/deploy-staging.sh` do not yet include `/hotel.*`. Add `/hotel.*` there before enabling `VITE_GRPC_CONTEXTS` in production ([architecture/overview.md#grpc-web-transport-partial-rollout](architecture/overview.md#grpc-web-transport-partial-rollout)).
- **Staging demo-data ergonomic path.** Staging initializes baseline + `seed.sql` + patches only (`seed` binary not in backend container image, DB port not host-published).
- **Automated restore & browser Playwright E2E CI depth.** Add full-DB restore boot smoke, web browser E2E runner, and load baseline tests.

## Standing Policy Decisions

- **Branch protection (`master-protection`).** Active ruleset requires PR + 17 status checks for non-admins while allowing repo admin bypass (`RepositoryRole: always`).
- **Guest portal forgot-password & max-booking-window: won't do.** Intentionally omitted by product decision.
- **Zero-balance un-invoiced ledger rows read "Draft", not "Paid".** Matches `ui_status='draft'` backend semantics; reversal rows remain "Paid".
