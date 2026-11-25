# Desktop Application Guide (`hotel-desktop`)

Canonical guide for building, preparing, provisioning embedded PostgreSQL (`19beta2`), packaging, code-signing, auto-updating, and backing up/restoring `hotel-desktop/` (Tauri 2 wrapper + `hotel-app-be` sidecar).

## 1. Supported Targets & Compatibility Floor

| OS | Architecture | Artifacts | CI job |
|---|---|---|---|
| macOS | `aarch64` (Apple Silicon) | `.app`, `.dmg` | `desktop-build-macos` (`macos-14`) |
| Windows | `x86_64` | NSIS `-setup.exe`, MSI, portable `.zip` | `desktop-build-windows` (`windows-latest`) |
| Linux | `x86_64` | `.deb`, `.AppImage`, `.rpm`, portable `.tar.gz` | `desktop-build-linux` (`ubuntu-24.04`) |

- **Linux compatibility floor**: Ubuntu 24.04 runner builds require **glibc ≥ 2.39**, `libwebkit2gtk-4.1`, `libayatana-appindicator3` (`.deb`), and `libfuse2` / `libfuse2t64` (`.AppImage`). `.rpm` packages are generated natively via Tauri's pure-Rust `rpm` bundler and smoke-tested on `fedora:41`.
- **Windows / Linux ARM64**: Not built or verified.

---

## 2. Build Pipeline & Development Commands

All commands run from `hotel-desktop/` (`src-tauri/tauri.dev.conf.json` + `src-tauri/capabilities/dev.json` configure local dev).

```bash
bun run dev                    # prepare (pgsql + DB sync + debug sidecar) + tauri dev
bun run dev:no-prepare         # tauri dev skipping prepare
bun run desktop:prepare        # cache-aware step 1–5 preparation
bun run desktop:prepare:force  # force-rebuild all prepared outputs
bun run sync:resources         # sync backend baseline, seed, and patches into src-tauri/database/
bun run test:scripts           # run Bun unit tests for provision/sync scripts (31 tests)

bun run build                  # release binary + platform installers
bun run build:no-bundle        # release binary without installer bundling
bun run build:fast             # debug build + debug sidecar (fastest local verification)
bun run build:debug            # debug build with bundling
bun run build:nsis | build:msi | build:deb | build:appimage | build:rpm
bun run package:portable       # create portable .zip (win/mac) or .tar.gz (linux)
```

### Cache-Aware `desktop:prepare` Steps

1. **Provision embedded PostgreSQL** into `src-tauri/pgsql/` (~44 MB, gitignored). Fast-paths if binaries already match `CONFIGURED_POSTGRES_MAJOR_VERSION` (`src-tauri/src/postgres.rs`). Exits `2` (hard failure) if an on-disk tree reports the wrong build identity.
2. **Sync backend database resources** (`0001_v1_baseline.sql`, `seed.sql`, `patches/manifest.tsv`, `_begin.sql`, `_end.sql`, `000N_*.sql`) into `src-tauri/database/` when changed (verified by CI `db-mirror` job).
3. **Build Tauri frontend bundle** (`hotel-web-fe/dist/`, skipped if inputs & `VITE_*`/`TAURI_ENV_*` unchanged).
4. **Compile backend sidecar** (`hotel-app-be`; `build:fast`/`build:debug` use `DESKTOP_BACKEND_PROFILE=debug`).
5. **Copy sidecar binary** into `src-tauri/binaries/` with its target-triple suffix.

Cache stamps live in `src-tauri/target/desktop-build-cache/`. Release Cargo profiles enable LTO, 1 codegen unit, size optimization, and symbol stripping, while `build-override` accelerates proc-macros and build scripts. Set `RUSTC_WRAPPER=sccache` optionally for faster local/CI Rust rebuilds.

### Runtime Environment Variables (Sidecar)

```bash
HOTEL_DESKTOP_MODE=true        # binds 127.0.0.1 on dynamic free port from BACKEND_PORT
BACKEND_PORT=3030
SKIP_EMAIL_VERIFICATION=true
RUST_LOG=info
HOTEL_LOG_DIR=./logs
```

> **Accepted session limitation:** The Tauri webview loads from `tauri://localhost` / `http://tauri.localhost` while the sidecar serves `http://127.0.0.1:<port>`, so `SameSite=Lax` refresh cookies never cross origins — staff re-authenticate after app restarts.

---

## 3. Embedded PostgreSQL Provisioning (`19beta2`)

`bun run provision:pgsql` populates `src-tauri/pgsql/{bin,lib,share}` (`initdb`, `pg_ctl`, `pg_dump`, `pg_isready`, `pg_restore`, `postgres`, `psql`) matching `CONFIGURED_POSTGRES_BUILD_IDENTITY` (`19beta2` in `src-tauri/src/postgres.rs`). Note the desktop engine (`19beta2`) lags server/CI (`19beta3`) until the coordinated PG 19 GA bump.

| OS | Provisioning source & relocation |
|---|---|
| macOS | Uses `POSTGRES_PREFIX` or `brew --prefix postgresql@19`; rewrites dylibs to `@loader_path/…` via `install_name_tool`, copies external libs, ad-hoc re-signs |
| Linux | Requires `POSTGRES_PREFIX` from-source install; sets `$ORIGIN` rpaths via `patchelf` and bundles external `.so`s into `lib/` |
| Windows | Requires `POSTGRES_PREFIX` Meson install with vcpkg dependency `.dll`s copied into `<prefix>\bin` beforehand |

Reference from-source build recipes live in `.github/workflows/desktop-build.yml` (`Build PostgreSQL 19 Beta 2`). Force re-provisioning with `bun run provision:pgsql:force`.

---

## 4. Platform Packaging, OS Signing & CI

- **Windows**: NSIS (`-setup.exe`, primary per-user installer under `%LOCALAPPDATA%` with `installer-hooks.nsh` cleanly stopping sidecar/`postgres.exe`), MSI (on `v*` tag releases via WiX), portable `.zip`. WebView2 bootstrapper runs automatically; app must not run elevated (`postgres` rejects Admin/root).
  - **Signing secrets**: `WINDOWS_CERT_PFX_BASE64` + `WINDOWS_CERT_PASSWORD` (imported per CI run; takes precedence), or `WINDOWS_CERT_THUMBPRINT`, plus optional `WINDOWS_SIGN_TIMESTAMP_URL`.
- **macOS**: `.app` + `.dmg` (aarch64). `scripts/sign-macos-resources.sh` signs Mach-O binaries in `pgsql/` and the sidecar before `tauri build`; `scripts/notarize-macos.sh` notarizes and staples `.dmg` when secrets exist.
  - **Signing secrets**: `APPLE_CERTIFICATE`, `APPLE_CERTIFICATE_PASSWORD`, `APPLE_SIGNING_IDENTITY`, `KEYCHAIN_PASSWORD`, plus either App Store Connect API (`APPLE_API_KEY`, `APPLE_API_ISSUER`, `APPLE_API_KEY_P8`) or Apple ID (`APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID`).
- **CI pipelines**:
  - `ci.yml` checks desktop Rust compilation/tests on Linux & Windows against placeholder resources on every PR.
  - `.github/workflows/desktop-build.yml` builds full installers across macOS, Windows, and Linux on `v*` tags and manual `workflow_dispatch` (including Linux `.deb` install + `xvfb` launch smoke, `.rpm` `fedora:41` install smoke, and Windows NSIS `/S` smoke), with a `timeout 3600` guard around known upstream `tauri-bundler` RPM hangs (`tauri-apps/tauri#15698`).

> **Note:** Absence of OS signing secrets produces unsigned OS installers, whereas **updater signing (`TAURI_SIGNING_PRIVATE_KEY` + `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`) is always mandatory** because `createUpdaterArtifacts: true` and `pubkey` are armed in `tauri.conf.json`.

---

## 5. Auto-Updater

- **Plugins & Commands**: `tauri-plugin-updater` + `tauri-plugin-process` registered in `src-tauri/src/lib.rs`, exposing `check_for_updates`, `install_update`, and `restart_app` (`src-tauri/src/commands.rs`) gated by `updater:default` / `process:default` capabilities and `VITE_DESKTOP_UPDATER_ENABLED=true`.
- **Endpoint**: `https://github.com/siewong007/hotel-app-rust/releases/latest/download/latest.json` verified against the baked minisign `pubkey`.
- **Frontend UX**: `src/desktop/UpdateNotifier.tsx` checks quietly 15s after launch + every 6h; `src/desktop/UpdateChecker.tsx` on System Health provides manual checks.
- **Release flow**: Tag an annotated commit matching `tauri.conf.json` `version` (`git tag -a v0.3.0 ... && git push origin v0.3.0`). `desktop-build.yml`'s `desktop-release` job runs `scripts/build-update-manifest.mjs`, builds `latest.json`, and uploads installers + `*.sig` assets to GitHub Releases. Verify live output with `curl -sL https://github.com/siewong007/hotel-app-rust/releases/latest/download/latest.json`.

---

## 6. Managed Local Backup & Restore

Unlike `hotel-backup` v1 JSON exports ([data-transfer.md](data-transfer.md)), desktop managed backups capture the entire bundled PostgreSQL cluster plus uploaded files.

- **Artifacts** (stored in `<data dir>/backups/`, newest 14 pairs retained):
  - `hotel-backup-YYYYMMDD-HHMMSS.dump` (custom-format `pg_dump`, verified with `pg_restore --list`)
  - `hotel-backup-YYYYMMDD-HHMMSS-uploads.tar.gz` (`uploads/` + `private_uploads/`)
- **Data directory**: `~/Library/Application Support/HotelApp` (macOS), `%LOCALAPPDATA%\HotelApp` (Windows), `~/.local/share/HotelApp` (Linux).
- **Schedule & UI**: First backup ~2 min after startup, then every 24h. Managed in `/data-transfer` → **Local backups** (path-restricted to data dir via `ensure_within_data_dir`). Every in-app restore writes a `hotel-backup-<now>.dump` safety backup first before swapping upload trees and restarting.
- **Major-version upgrades**: On PG build mismatch at boot, the app offers **Restore from backup** and retires the old directory to `pgdata-pg<ver>-retired-<ts>`.
- **CLI restore** (against running bundled Postgres on port `5433`, password in `<data dir>/postgres-password.txt`):

```bash
PGPASSWORD=$(cat ~/Library/Application\ Support/HotelApp/postgres-password.txt) \
  pg_restore -h localhost -p 5433 -U hotel_admin -d hotel_management \
  --clean --if-exists --no-owner --no-privileges \
  ~/Library/Application\ Support/HotelApp/backups/hotel-backup-<ts>.dump
```

Then move aside existing `uploads/` and `private_uploads/` before extracting `hotel-backup-<ts>-uploads.tar.gz`.

---

## 7. Troubleshooting

- **`POSTGRES_PREFIX is not set` (Linux/Windows)**: Point to a compiled PostgreSQL 19 Beta 2 install prefix.
- **`contains no DLLs` (Windows)**: Copy vcpkg dependency DLLs into `<prefix>\bin` before provisioning.
- **`libpq.so.5 => not found` / absolute symlinks (Linux)**: Ensure `patchelf` is installed and re-run `bun run provision:pgsql:force`.
- **Stale pre-fold database (`patch 1.2 checksum mismatch`)**: Delete generation-1 `version > 1` rows from `hotel_schema_revisions` against port `5433` or export → wipe → re-import ([deployment.md#one-time-reset-stale-pre-fold-patch-lineage](deployment.md#one-time-reset-stale-pre-fold-patch-lineage)).
