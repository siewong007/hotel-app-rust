# Internationalisation & Terminology

Canonical guide for multi-language implementation, bundle parity enforcement, formatting rules, UI/locale coverage (`2026-11-25`), and the EN/ZH/MS/ZH-TW domain terminology glossary. Design rationale lives in [ADR 012](../architecture/decision-records.md#adr-012-hand-rolled-internationalisation-on-intl).

Supported locales: **English (`en`, source of truth)**, **Bahasa Melayu (`ms`)**, **简体中文 (`zh`, `Intl` tag `zh-CN`)**, and **繁體中文 (`zh-TW`)**.

---

## 1. Where Things Live

| Concern | Location |
|---|---|
| Locale registry (`LOCALES`, native names, direction) | `hotel-web-fe/src/i18n/locales.ts` |
| Translation engine (lookup, plurals, interpolation) | `hotel-web-fe/src/i18n/translator.ts` |
| Active locale store & precedence | `hotel-web-fe/src/i18n/localeStore.ts` |
| Web bundles (30 namespaces per locale) | `hotel-web-fe/src/i18n/resources/<locale>/<ns>.json` (`en` bundled; `ms`/`zh`/`zh-TW` lazy chunks via `ensureLocaleLoaded`) |
| `Intl` formatters | `hotel-web-fe/src/i18n/format.ts` |
| Language switcher | `hotel-web-fe/src/components/common/LanguageSwitcher.tsx` |
| Server locale negotiation | `hotel-app-be/src/core/i18n.rs` |
| Server email catalogs | `hotel-app-be/src/core/locales/<locale>.json` |

---

## 2. Usage, Plurals & Adding Languages

```tsx
import { useTranslation } from '../../i18n';

const { t, tOr, locale, setLocale } = useTranslation('nav');

t('routes.bookings.label');
t('common:actions.save');
t('common:count.nights', { count: 3 });
tOr('actions.book', 'Book'); // progressive fallback when bundle key not yet added
```

Outside React components (interceptors, utils), import bare `t` from `src/i18n` for one-shot lookups.

- **Adding keys**: Add to `resources/en/<namespace>.json` and **every** other locale using `{{name}}` placeholders; `resources.test.ts` fails on drift.
- **Plurals**: Suffix with `_other` (required) and `_zero` / `_one` per `Intl.PluralRules`. Malay and Chinese only use `other` (and optional `_zero`), so `_one` is intentionally omitted there.
- **Adding a language**: Register in `LOCALES`, create all 30 JSON namespaces + `resources/index.ts`, add `hotel-app-be/src/core/locales/<code>.json` + `SUPPORTED_LOCALES`, widen `consent_records.locale` via baseline + catalog patch, extend `LocalizedText` (`src/features/legal/content/types.ts`), add help articles (`articles.<code>.ts`), and add a landing-page `Copy` module (`hotel-web-fe/salim-inn/src/content/`). RTL languages additionally set `dir: 'rtl'`.

### Language Resolution Order

- **Web**: `?lang=<code>` URL param (persists to localStorage `locale`) → stored switcher choice → hotel `default_locale` setting → `navigator.languages` → `en`.
- **Server (email)**: `guests.language_preference` → `default_locale` system setting (default `en`, patch `0011`) → `en`. `Locale::from_accept_language` handles `zh-Hant*`/`zh-TW`/`zh-HK`/`zh-MO` → `zh-TW` and `zh-Hans*`/`zh-CN`/`zh-SG`/`zh-MY`/`zh` → `zh`.

---

## 3. Surfaces Outside JSON Bundles

1. **Legal corpus (`hotel-web-fe/src/features/legal/content/`)**: Typed `LocalizedText` requires all four locales at compile time (`2026-11-18` generation; `en`/`ms` statutory floor, `zh`/`zh-TW` annotated for native legal sign-off).
2. **Help articles (`hotel-web-fe/src/features/help/content/articles.<locale>.ts`)**: Resolved per locale via `ARTICLE_SETS` (`zh-TW` converted from `zh` and meriting a native review).
3. **PDF exports**: jsPDF `zh`/`zh-TW` exports lazily embed Noto Sans CJK SC via `src/utils/pdfFont.ts`; `en`/`ms` use `helvetica`.
4. **Salim Inn landing (`hotel-web-fe/salim-inn/`)**: Standalone Vite document with typed `Copy` (`src/content/{en,ms,zh,zhTW}.ts`), `data-i18n` parity tested in `salim-inn/src/i18n.test.ts`, sharing the browser `locale` storage key.
5. **Formatting**: Dates use `src/utils/date.ts` (`hotel_today` / hotel timezone), numbers use `formatNumber`/`formatPercent`, and money uses `src/utils/currency.ts` (tracks property currency, deliberately independent of interface language). Server emails format dates via `Locale::format_date` (avoiding English-only `%b`).

---

## 4. Coverage & Test Gates (`2026-11-25`)

- **30 namespaces per locale**: Flattened leaf keys measured `2026-11-25`: **`en` 8,134 · `ms` 8,077 · `zh` 8,062 · `zh-TW` 8,011** (`ms`/`zh`/`zh-TW` omit `_one` plural keys by design).
- **Enforced CI gates**:
  - `hotel-web-fe/src/i18n/resources.test.ts`: key completeness, no extra keys, plural category rendering, `{{var}}` placeholder parity, non-blank values, bundle registration on disk.
  - `hotel-web-fe/src/i18n/keyUsage.test.ts`: every literal `t()`/`tOr()`/`translate*()`/`statusLabel()` exists in `en`.
  - `hotel-web-fe/src/i18n/hardcoded.test.ts`: blocks un-allowlisted hardcoded JSX text/props.
  - `hotel-app-be/src/core/i18n.rs`: backend email catalog key & placeholder parity.
- **Accepted dynamic `tOr` & known caveats**: Dynamic enum/route lookups use `tOr` deliberately (~102 call sites across `CustomerLedger`, `NightAuditReportViews`, `DataTransfer`, `Support`, `GuestRelations`, `routeLabels`, `CommandPalette`, `UserMenu`). Known minors: `segments:page.activeGuestsMatch` lacks pluralization, `charts/format.ts` `fmtShortDate` uses day-first order, and `audit.service.ts` PDF body rows humanize backend enums via `formatStatusLabel`.

---

## 5. Terminology Glossary (`en` / `zh` / `ms` / `zh-TW`)

Sources of truth: `hotel-web-fe/src/i18n/resources/{en,ms,zh,zh-TW}/*.json` and `hotel-app-be/src/core/locales/`. `zh-TW` mirrors `zh` terminology in Traditional script unless noted.

### Core Entities & Booking Lifecycle

| en | zh | ms | Notes |
|---|---|---|---|
| booking, reservation | 预订 | tempahan | `nav`, `status`, `email` |
| guest | 客人 | tetamu | compound register uses 宾客 (宾客关系, 宾客门户) |
| room / room type | 客房 (staff) / 房间 (guest) / 房型 | bilik / jenis bilik | register split |
| check-in / check in | 入住 / 办理入住 | daftar masuk | never 登记入住 |
| check-out | 退房 | daftar keluar | |
| walk-in / no-show | 上门客 / 预订未到 | walk-in / tidak hadir | `zh-TW`: 現場散客 / 未入住 |
| confirmed / pending / completed | 已确认 / 待处理 / 已完成 | disahkan / menunggu / selesai | `status` |
| void (action) / voided (state) | 作废 / 已作废 | void / terbatal | keep verb vs state distinct |
| folio / night audit | 账单 / 夜审 | folio / audit malam | `zh-TW`: 帳單 / 夜間稽核 |
| tourism tax / extra bed | 旅游税 / 加床 | cukai pelancongan / katil tambahan | statutory RM10/night |
| complimentary / free nights | 免费 / 免费房晚 | percuma / malam percuma | |

### Operations, Finance & Marketing

| en | zh | ms | Notes |
|---|---|---|---|
| room status | 房态 | status bilik | |
| available / occupied / reserved | 可售 / 占用中 / 已预订 | tersedia / diduduki / ditempah | guest portal uses 可订 |
| clean / dirty / cleaning / inspected | 已清洁 / 待清洁 / 清洁中 / 已检查 | bersih / kotor / dibersihkan / diperiksa | |
| housekeeping / maintenance | 客房服务 (zh-TW: 房務) / 维修 | pengemasan / penyelenggaraan | daily cleaning: 每日客房清洁 |
| payment / payment approvals | 支付 (email: 付款) / 支付审批 | pembayaran / kelulusan pembayaran | |
| deposit / refund | 押金 / 退款 | deposit / bayaran balik | |
| ledger / company ledger | 台账 / 公司台账 | lejar / lejar syarikat | |
| revenue / rates / promotions / campaign | 收益 / 房价 / 促销 / 营销活动 | hasil / kadar / promosi / kempen | |
| offer(s) / voucher / loyalty | 优惠 / 优惠券 / 会员忠诚度 | tawaran / baucar / kesetiaan | 优惠 guest-facing, 促销 staff |
| insights / reports | 经营分析 / 报表 | wawasan / laporan | |

### Admin, Auth & Security

| en | zh | ms | Notes |
|---|---|---|---|
| administration / settings | 系统管理 / 设置 | pentadbiran / tetapan | |
| access control / role / permission | 访问控制 / 角色 / 权限 | kawalan akses / peranan / kebenaran | |
| audit log / data transfer | 审计日志 / 数据迁移 | log audit / pemindahan data | |
| guest portal / identity (eKYC) | 宾客门户 / 身份认证 | portal tetamu / identiti | |
| two-factor authentication | 双重身份验证 | pengesahan dua faktor | |
| passkey / backup code | 通行密钥 / 备用码 | kunci laluan / kod sandaran | `zh-TW`: 密碼金鑰 / 備援碼 |
| sign in / sign out / register | 登录 / 退出登录 / 注册 | log masuk / log keluar / daftar | |

---

## 6. Tone, Grammar & Translator Rules

- **Do not translate**: Acronyms (`eKYC`, `RBAC`, `API`, `KPI`, `2FA`, `PDF`, `QR`, `OTA`, `PMS`, `ADR`, `RevPAR`), OTA vendor names (`Booking.com`, `Agoda`, `Expedia`), `PayPal`, `Google`, formats (`JPEG`, `PNG`, `WebP`, `PDF`), internal API enum values, JSON keys, URLs, or `{{var}}` placeholder names.
- **Mandarin (`zh`) conventions**: Use `您` (never `你`), hotel terms `入住` / `退房` / `房态` / `夜审`, compact nav labels (≤4 chars where natural), full-width punctuation (`。，；：、“”、《》`), spaces around CJK–Latin/digit boundaries (`30 晚`, `6 位验证码`, `eKYC 管理`), counter measure words (`晚`, `位客人`, `间客房`), and `_other` + `_zero` plural keys only (no `_one`). Email pattern keeps `{{year}}年{{month}}{{day_num}}日`.
- **Open register watch items**: Standardize minor variants (`客群细分` vs `客人细分`, `总计` vs `总额`, `Audit Malam` vs English jargon in `ms/help`, `Daftar masuk auto`) when those UI surfaces are next revised, and update this section when settled.
