# Android Staff Application Guide (`hotel-android`)

Canonical guide for the native Android staff application (`com.hotelapp.staff`), covering architecture, security/RBAC enforcement, offline sync, native Android integrations, build variants, testing, and CI.

## 1. Overview & Stack

| Layer | Technology |
|---|---|
| Language & Build | Kotlin `2.2.21`, AGP `8.13.2`, Gradle `8.13`, Java 17 toolchain |
| Android SDK | `minSdk 26`, `targetSdk 36`, `compileSdk 36`, version `0.3.0` |
| UI | Jetpack Compose (`composeBom 2025.10.00`), Material 3 + `material3-window-size-class` (adaptive `NavigationBar` / `NavigationRail`), Core SplashScreen, edge-to-edge display |
| Networking & Serialization | OkHttp `4.12.0`, Retrofit `2.11.0`, `kotlinx-serialization-json` `1.9.0`, OkHttp WebSocket client |
| Security & Auth | Android Keystore (`EncryptedCookieJar` / `AndroidKeystoreCipher`), AndroidX Biometric (`1.1.0`), Credential Manager + Play Services Auth (`1.5.0`) |
| Persistence & Background Work | DataStore Preferences (`1.1.7`), native Android SQLite (`HotelLocalDatabase`), WorkManager (`2.10.1`) |
| Native Surfaces | Jetpack Glance AppWidget (`1.1.1`), `PrintManager` folio printing, ProfileInstaller (`1.4.1`) |
| Tests | JUnit 4, `kotlinx-coroutines-test`, OkHttp `MockWebServer`, plus `LiveBackendE2eTest` |

---

## 2. Package Architecture

```text
hotel-android/app/src/main/java/com/hotelapp/staff/
  HotelStaffApp.kt      Application entrypoint + manual AppContainer dependency graph
  MainActivity.kt       Single-activity Compose host, splash screen, biometric cold-lock gate, deep links
  core/
    auth/               SessionManager, StaffSession RBAC helpers, StaffCredentialHelper (Credential Manager)
    config/             ServerConfigStore (DataStore), ServerUrlNormalizer
    design/             HotelStaffTheme (dark/light/dynamic colors), typography, spacing tokens, common M3 UI
    format/             DateFormatter, MoneyFormatter, StatusFormatter (aligned with backend status enums)
    network/            NetworkFactory, TokenAuthenticator, EncryptedCookieJar, BaseUrlInterceptor, RealtimeSocketClient, NetworkMonitor, FlexibleDecimalSerializer
    notifications/      StaffNotificationManager (permission-filtered OS notification channels & alerts)
    print/              FolioPrinter (Android PrintManager PDF/print adapter)
    storage/            HotelLocalDatabase (SQLite cache for offline reads + pending mutation queue)
    sync/               OfflineOpsCoordinator, SyncPendingMutationsWorker (WorkManager)
    ui/                 Shared Compose UI scaffolding & offline/error banners
  data/
    api/                13 Retrofit interfaces (Auth, Passkey, Settings, Bookings, Rooms, Housekeeping, Maintenance, Guests, Notifications, Search, Payments, Ekyc, Operations)
    dto/                kotlinx.serialization DTOs matching backend JSON contracts
    repository/         12 Repository interfaces & Default implementations
  feature/              13 feature modules (auth, bookings, dashboard, ekyc, guests, housekeeping, maintenance, more, notifications, payments, rooms, search, server)
  navigation/           AppDestination, DeepLinkRouter, HotelNavHost
  widget/               HotelOpsWidget, HotelOpsWidgetReceiver, OpsWidgetSnapshot, WidgetRefreshWorker
```

---

## 3. Security, Authentication & RBAC Enforcement

- **Staff-only login guard**: `AuthRepository` inspects `user_type` and role permissions on password login, passkey login, and cold session restore (`isGuestOnlyAccount`). Guest-only accounts are immediately signed out, cookies wiped, and rejected with an explicit staff-only error.
- **Token & cookie storage**: JWT access tokens remain in memory (`SessionManager`); refresh cookies are AES-GCM encrypted via Android Keystore (`AndroidKeystoreCipher`) inside `FileCookieStorage` (`EncryptedCookieJar`). `TokenAuthenticator` coordinates single-flight `/api/auth/refresh` retries on `401`.
- **Biometrics, 2FA & Passkeys**:
  - Cold-launch `BiometricPrompt` lock only engages during `Checking -> Authenticated` session restoration (never re-locking immediately after fresh sign-in or toggling the setting) and defers deep links until unlocked.
  - Supports TOTP 2FA verification, backup-code submission, and Android Credential Manager password save/autofill + WebAuthn passkeys (`PasskeyApi`).
- **Transport security**: `debug` enables cleartext (`http://10.0.2.2:3030` emulator default + configurable server URL); `release` sets `ALLOW_CLEARTEXT=false`, points to `https://saliminn.my`, and enables R8 minification & resource shrinking.
- **RBAC parity across all surfaces**:
  - `StaffSession` mirrors backend `<resource>:<action>` (`:manage` implies all actions).
  - Top-level tabs, `More` destinations, TopAppBar Search button, OS notifications, deep links (`DeepLinkRouter.canAccess`), search scopes (`SearchViewModel`), cross-screen links (Rooms → Bookings, Guests → Bookings, Payments → Bookings), eKYC `reasonCodes()` (`ekyc:review`), and check-in payment collection (`payments:create`) are strictly gated.

---

## 4. Operational Features & Native Capabilities

- **Dashboard**: Arrival/departure/in-house operational overview, permission-gated urgent pending payment banner, and Night Audit preview (`GET /api/night-audit/preview?date=YYYY-MM-DD`).
- **Bookings**: Paged booking list/filters, walk-in/staff booking creation, folio/extra-bed/payment recording (`duitnow`, `online_banking`, card, cash), check-in/out/void flows, and native folio printing via Android `PrintManager`.
- **Rooms, Housekeeping & Maintenance**: Live occupancy board, room status transitions with offline cache banners when disconnected, task creation with inline error surfacing, and maintenance ticket management.
- **eKYC & Payment Approvals**: Filterable eKYC review queue (`pending_manual_review`, `submitted`, `in_review`, `additional_information_required`, `escalated`, `approved`, `rejected`) with claim/approve/reject/resubmit/escalate reason validation, plus bank-transfer claim approval/rejection (`pending`, `pending_payment`, `pending_confirmation`).
- **Guests & Global Search**: Guest directory & profile lookup, plus multi-scope global search with local history (`SearchRepository`).
- **Realtime & Offline Resilience**:
  - `RealtimeSocketClient` subscribes to `/api/updates/socket` using `Sec-WebSocket-Protocol` (`hotel-updates`), reconnecting automatically when `NetworkMonitor` regains connectivity.
  - Read caches and offline mutations persist in SQLite (`HotelLocalDatabase`) and drain in the background via `SyncPendingMutationsWorker`.
- **Glance Widget**: `HotelOpsWidget` displays operational metrics on the Android home screen and refreshes periodically via `WidgetRefreshWorker`.

---

## 5. Build, Verification & CI

```bash
cd hotel-android

# Unit tests (MockWebServer & ViewModel/Auth/Formatter/Router unit suite; 65 offline tests, 2 live-backend E2E skip without env)
./gradlew testDebugUnitTest --console=plain

# Android Lint + Debug & R8-minified Release APK builds
./gradlew lintDebug assembleDebug assembleRelease --console=plain
```

### Live Backend E2E Suite

`LiveBackendE2eTest` (2 tests) exercises guest rejection, all 12 seeded staff roles, 2FA recovery, Night Audit, Search, Bookings, Rooms + WebSocket, Housekeeping, Maintenance, Guest CRM, Payments, and eKYC claim/approve against a running PostgreSQL 19 + `hotel-app-be` server when configured:

```bash
HOTEL_E2E_BASE_URL=http://127.0.0.1:13039 \
  ./gradlew testDebugUnitTest --tests "com.hotelapp.staff.e2e.LiveBackendE2eTest" --rerun-tasks --console=plain
```

### GitHub Actions CI

[`.github/workflows/android-ci.yml`](../../.github/workflows/android-ci.yml) triggers on `master` pushes/PRs touching `hotel-android/**` or the workflow itself and runs JDK 17 + Gradle `./gradlew testDebugUnitTest lintDebug assembleDebug`.
