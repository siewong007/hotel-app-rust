# Documentation Index

Canonical documentation map (`2026-11-25`). **One canonical file owns each subject** — update facts in the single owning document rather than restating them elsewhere. If two documents disagree, the code wins and the stale doc is fixed.

**Rule of truth:** source code > schema/seed > routes/services > config/deploy files > tests > docs.

## Directory Layout

| Directory | Purpose |
|---|---|
| `docs/` | Cross-cutting system specs: feature registry, development, dependencies, design system, security, open work |
| `docs/api/` | REST, gRPC, WebSocket, and auth contract + generated `openapi.json` |
| `docs/architecture/` | System architecture, flows, transports, CRM boundary, and ADRs |
| `docs/guides/` | Operational & user guides: deployment/VPS/PG upgrade, desktop, Android, data transfer, i18n, Chinese user guides |

## Canonical Document Map (One File per Subject)

| Subject | Canonical Document |
|---|---|
| Project overview, quick start, stack summary | [`../README.md`](../README.md) |
| System architecture, flows, gRPC-Web & Guest Relations | [`architecture/overview.md`](architecture/overview.md) |
| Architecture Decision Records (ADRs 001–016) | [`architecture/decision-records.md`](architecture/decision-records.md) |
| Feature delivery registry | [`features.md`](features.md) |
| API contract (REST, auth, errors, rate limits, WS, gRPC index) | [`api/README.md`](api/README.md) + [`api/openapi.json`](api/openapi.json) |
| Dev setup, commands, testing & troubleshooting | [`development.md`](development.md) |
| Dependency rationale across BE, FE, Desktop & Android | [`dependencies.md`](dependencies.md) |
| Design system (Web tokens, loading, print, Android M3) | [`design-system.md`](design-system.md) |
| Deployment, staging, AIC VPS, patching & PG19 upgrade | [`guides/deployment.md`](guides/deployment.md) |
| Desktop packaging, build speed, updater & backup/restore | [`guides/desktop.md`](guides/desktop.md) |
| Native Android staff app architecture, security & CI | [`guides/android.md`](guides/android.md) |
| Data transfer (`hotel-backup` v1) | [`guides/data-transfer.md`](guides/data-transfer.md) |
| Internationalisation, coverage baseline & glossary | [`guides/internationalization.md`](guides/internationalization.md) |
| Admin & staff user guide (Simplified Chinese) | [`guides/admin-user-guide-zh.md`](guides/admin-user-guide-zh.md) · [`PDF`](guides/admin-user-guide-zh.pdf) |
| Normal user / guest guide (Simplified Chinese) | [`guides/guest-user-guide-zh.md`](guides/guest-user-guide-zh.md) · [`PDF`](guides/guest-user-guide-zh.pdf) |
| Security operations, retention policy, DR drill, readiness & go-live | [`security.md`](security.md) |
| Open work & policy decisions | [`ongoing-dev.md`](ongoing-dev.md) |
| Database lifecycle (baseline, `seed`, patch catalog) | [`../hotel-app-be/database/README.md`](../hotel-app-be/database/README.md) |
| Backend quick start | [`../hotel-app-be/README.md`](../hotel-app-be/README.md) |
| Web UI sitemap for design sync | [`../hotel-web-fe/design-guidelines/app-structure.md`](../hotel-web-fe/design-guidelines/app-structure.md) |
| Claude Design component sync | [`../.design-sync/NOTES.md`](../.design-sync/NOTES.md) + [`../.design-sync/conventions.md`](../.design-sync/conventions.md) |
| OCI dev Terraform slice | [`../infra/terraform/oci/README.md`](../infra/terraform/oci/README.md) |
| Contribution process & commit conventions | [`../CONTRIBUTING.md`](../CONTRIBUTING.md) |
| Vulnerability reporting & security policy | [`../SECURITY.md`](../SECURITY.md) |
| Community code of conduct | [`../CODE_OF_CONDUCT.md`](../CODE_OF_CONDUCT.md) |
| Release changelog | [`../CHANGELOG.md`](../CHANGELOG.md) |
| Agent conventions (layers, naming, refactoring safety) | [`../AGENTS.md`](../AGENTS.md) |
| Agent routing index (commands, CI, env; ≤150 lines) | [`../CLAUDE.md`](../CLAUDE.md) |
| Booking & ledger internals (agent refs) | [`../.claude/refs/booking-workflow.md`](../.claude/refs/booking-workflow.md) · [`../.claude/refs/ledger-workflow.md`](../.claude/refs/ledger-workflow.md) |

## Maintenance Rules

1. **One owner per fact.** Link to the owning doc rather than duplicating lists, tables, or status labels.
2. **Update docs in the same PR as code:**
   - Route added/removed → regenerate `api/openapi.json` (CI `openapi_drift` enforces).
   - New module/surface → update `features.md` and `architecture/overview.md`.
   - New env var → update the matching `.env.example` and config tables in `guides/deployment.md`.
3. **No shipped plan archives.** Completed implementation plans, audit snapshots, and point-in-time inventories are erased once their conclusions land in the owning docs; git history serves as the archive. Remove finished items from `ongoing-dev.md` rather than striking them through.
4. **Verify numbers before writing them.** Recount via CLI/scripts rather than repeating stale counts.
5. **Secrets never enter docs.**
6. **Links are CI-gated.** `make docs-check` (`scripts/check-doc-links.py`) fails CI on any broken relative Markdown link (case-sensitive on Linux).
7. **Naming:** Lower-case `kebab-case` (`desktop.md`), with `README.md` as directory entrypoints.
