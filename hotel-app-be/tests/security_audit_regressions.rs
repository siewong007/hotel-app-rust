//! Live-PostgreSQL regression suite covering the security audit remediations
//! across auth/passkeys/OAuth, loyalty isolation, group voucher resplits, guest
//! cancel races, negative charge constraints, data-transfer hardening, email
//! template sanitization/allowlists, CSRF origin validation, closed business-day
//! guards, ledger immutability, promotion claim windows, anonymous replay
//! bounds, audit LIKE escaping, group receipt GET/POST separation, gRPC guest
//! isolation, and team reactivation authority checks.
//!
//! Opt-in via `DATABASE_URL` (`postgres:19` in local/CI runs). Judge real DB
//! coverage by wall-clock duration (>0.00s) and per-test execution.

use axum::Router;
use axum::body::Body;
use axum::extract::{ConnectInfo, Extension, Path, State};
use axum::http::{Request, StatusCode, header};
use axum::response::Json;
use base64::Engine;
use base64::engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD};
use chrono::{Duration, NaiveDate, Utc};
use hotel_app_be::AuthService;
use hotel_app_be::core::error::ApiError;
use hotel_app_be::core::middleware::validate_request_origin;
use hotel_app_be::grpc::RoomGrpc;
use hotel_app_be::grpc::pb::hotel::rooms::v1 as rmpb;
use hotel_app_be::grpc::pb::hotel::rooms::v1::room_service_server::RoomService;
use hotel_app_be::models::{
    AuditLogQuery, BookingUpdateInput, CustomerLedgerCreateRequest, CustomerLedgerUpdateRequest,
    LoginRequest, PasskeyLoginFinish, PasskeyLoginStart, RecordPaymentRequest,
    ResendVerificationRequest,
};
use hotel_app_be::modules::auth::repository::AuthRepository;
use hotel_app_be::modules::auth::service as auth_service;
use hotel_app_be::modules::booking_groups::models::NewBookingGroup;
use hotel_app_be::modules::booking_groups::repository::BookingGroupRepository;
use hotel_app_be::modules::booking_groups::voucher_resplit::{ShareRelease, release_room_share_tx};
use hotel_app_be::modules::bookings::service as bookings_service;
use hotel_app_be::modules::bookings::update_booking_handler;
use hotel_app_be::modules::communications::models::TemplateInput;
use hotel_app_be::modules::communications::validation::{
    sanitize_email_html, validate_template_input,
};
use hotel_app_be::modules::data_transfer::models::ImportPreview;
use hotel_app_be::modules::guest_booking::repository::GuestBookingRepository;
use hotel_app_be::modules::guest_booking::validation::validate_anonymous_client_request_id;
use hotel_app_be::modules::ledgers::service as ledgers_service;
use hotel_app_be::modules::loyalty::repository::LoyaltyRepository;
use hotel_app_be::modules::passkey::repository::PasskeyRepository;
use hotel_app_be::modules::passkey::service as passkey_service;
use hotel_app_be::modules::payments::service as payments_service;
use hotel_app_be::modules::promotions::repository::PromotionRepository;
use hotel_app_be::modules::teams::models::TeamUpdateInput;
use hotel_app_be::modules::teams::service as teams_service;
use hotel_app_be::repositories::audit::AuditRepository;
use hotel_app_be::routes;
use hotel_app_be::services::google_identity::GoogleIdentity;
use ring::rand::SystemRandom;
use ring::signature::{ECDSA_P256_SHA256_ASN1_SIGNING, EcdsaKeyPair, KeyPair};
use rust_decimal::Decimal;
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use sqlx::{PgPool, postgres::PgPoolOptions};
use std::net::SocketAddr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, OnceLock};
use tonic::{Code, Request as TonicRequest};
use tower::ServiceExt;

const TEST_JWT_SECRET: &str = "security-audit-regressions-jwt-secret-32chars-min";
const PEER_ADDR: SocketAddr =
    SocketAddr::new(std::net::IpAddr::V4(std::net::Ipv4Addr::LOCALHOST), 45_000);

fn serial_lock() -> Arc<tokio::sync::Mutex<()>> {
    static LOCK: OnceLock<Arc<tokio::sync::Mutex<()>>> = OnceLock::new();
    LOCK.get_or_init(|| Arc::new(tokio::sync::Mutex::new(())))
        .clone()
}

fn ensure_test_config() {
    static INIT: OnceLock<()> = OnceLock::new();
    INIT.get_or_init(|| {
        let _ = AuthService::init_jwt_secret(TEST_JWT_SECRET);
        // SAFETY: initialized once under OnceLock before tests inspect config.
        unsafe {
            if std::env::var("JWT_SECRET").is_err() {
                std::env::set_var("JWT_SECRET", TEST_JWT_SECRET);
            }
            std::env::set_var("SETTINGS_CACHE_TTL_SECS", "0");
            std::env::set_var("ALLOWED_ORIGINS", "https://app.hotel.test");
            std::env::set_var("SKIP_EMAIL_VERIFICATION", "false");
        }
        let _ = hotel_app_be::core::config::init_from_env();
    });
}

async fn setup_pg() -> Option<(PgPool, tokio::sync::OwnedMutexGuard<()>)> {
    let Ok(database_url) = std::env::var("DATABASE_URL") else {
        eprintln!("Skipping PostgreSQL security audit regressions because DATABASE_URL is not set");
        return None;
    };
    ensure_test_config();
    let guard = serial_lock().lock_owned().await;
    let pool = PgPoolOptions::new()
        .max_connections(6)
        .after_connect(|conn, _| {
            Box::pin(async move {
                sqlx::query("SET app.allow_audit_mutation = 'on'")
                    .execute(conn)
                    .await
                    .map(|_| ())
            })
        })
        .connect(&database_url)
        .await
        .expect("failed to connect to PostgreSQL test database");
    Some((pool, guard))
}

fn uniq_tag() -> String {
    static SEQ: AtomicU64 = AtomicU64::new(0);
    let n = SEQ.fetch_add(1, Ordering::Relaxed);
    let ts = Utc::now()
        .timestamp_nanos_opt()
        .unwrap_or_default()
        .unsigned_abs();
    format!("secaudit_{ts:x}_{n}")
}

fn short_code(prefix: &str) -> String {
    static SEQ: AtomicU64 = AtomicU64::new(0);
    let n = SEQ.fetch_add(1, Ordering::Relaxed);
    let ts = Utc::now()
        .timestamp_nanos_opt()
        .unwrap_or_default()
        .unsigned_abs();
    format!("{prefix}{:06x}{n:x}", ts & 0x00ff_ffff)
}

fn d(s: &str) -> Decimal {
    s.parse().expect("valid decimal literal")
}

async fn mint_session(
    pool: &PgPool,
    user_id: i64,
    username: &str,
    roles: Vec<String>,
) -> (String, String) {
    let refresh_token = AuthService::generate_refresh_token();
    let sid = AuthService::store_refresh_token(
        pool,
        user_id,
        &refresh_token,
        1,
        Some("127.0.0.1"),
        Some("security-audit-test"),
        None,
    )
    .await
    .expect("store refresh token");
    let jwt = AuthService::generate_session_jwt(user_id, username.to_string(), roles, sid.clone())
        .expect("generate session jwt");
    (jwt, sid)
}

async fn grant_role_with_permissions(
    pool: &PgPool,
    user_id: i64,
    role_name: &str,
    perms: &[&str],
) -> i64 {
    let role_id: i64 = sqlx::query_scalar(
        "INSERT INTO roles (name, display_name, description, priority) \
         VALUES ($1, $2, 'security audit test role', 50) \
         ON CONFLICT (name) DO UPDATE SET display_name = EXCLUDED.display_name \
         RETURNING id",
    )
    .bind(role_name)
    .bind(role_name)
    .fetch_one(pool)
    .await
    .expect("upsert test role");

    sqlx::query("INSERT INTO user_roles (user_id, role_id) VALUES ($1, $2) ON CONFLICT DO NOTHING")
        .bind(user_id)
        .bind(role_id)
        .execute(pool)
        .await
        .expect("assign test role");

    for perm in perms {
        sqlx::query(
            "INSERT INTO role_permissions (role_id, permission_id) \
             SELECT $1, id FROM permissions WHERE name = $2 \
             ON CONFLICT (role_id, permission_id) DO NOTHING",
        )
        .bind(role_id)
        .bind(*perm)
        .execute(pool)
        .await
        .expect("grant permission");
    }
    hotel_app_be::core::rbac_cache::clear_all();
    role_id
}

async fn http_call(
    app: &Router,
    method: &str,
    uri: &str,
    bearer: Option<&str>,
    body: Vec<u8>,
    extra_headers: &[(&str, &str)],
) -> (StatusCode, Vec<u8>) {
    let mut builder = Request::builder().method(method).uri(uri);
    if let Some(tok) = bearer {
        builder = builder.header(header::AUTHORIZATION, format!("Bearer {tok}"));
    }
    for (k, v) in extra_headers {
        builder = builder.header(*k, *v);
    }
    if method == "POST"
        && !extra_headers
            .iter()
            .any(|(k, _)| k.eq_ignore_ascii_case("content-type"))
    {
        builder = builder.header(header::CONTENT_TYPE, "application/json");
    }
    let mut req = builder.body(Body::from(body)).expect("build request");
    req.extensions_mut().insert(ConnectInfo(PEER_ADDR));
    let resp = app.clone().oneshot(req).await.expect("router oneshot");
    let status = resp.status();
    let bytes = axum::body::to_bytes(resp.into_body(), usize::MAX)
        .await
        .expect("read body");
    (status, bytes.to_vec())
}

// ---------------------------------------------------------------------------
// H-01: Inactive passkey filtering & unverified passkey login gate
// ---------------------------------------------------------------------------

#[tokio::test]
async fn h01_passkey_filters_inactive_and_blocks_unverified_login() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();
    let username = format!("h01_{tag}");
    let email = format!("h01-{tag}@example.test");

    let user_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, NULL, 'Unverified Passkey Guest', 'guest', true, false) RETURNING id",
    )
    .bind(&username)
    .bind(&email)
    .fetch_one(&pool)
    .await
    .expect("insert user");

    let rng = SystemRandom::new();
    let pkcs8 = EcdsaKeyPair::generate_pkcs8(&ECDSA_P256_SHA256_ASN1_SIGNING, &rng).unwrap();
    let key =
        EcdsaKeyPair::from_pkcs8(&ECDSA_P256_SHA256_ASN1_SIGNING, pkcs8.as_ref(), &rng).unwrap();
    let active_cred = format!("active-{tag}").into_bytes();
    let inactive_cred = format!("inactive-{tag}").into_bytes();

    sqlx::query(
        "INSERT INTO passkeys (user_id, credential_id, public_key, counter, is_active) VALUES ($1, $2, $3, 0, true)",
    )
    .bind(user_id)
    .bind(&active_cred)
    .bind(key.public_key().as_ref())
    .execute(&pool)
    .await
    .unwrap();
    sqlx::query(
        "INSERT INTO passkeys (user_id, credential_id, public_key, counter, is_active) VALUES ($1, $2, $3, 0, false)",
    )
    .bind(user_id)
    .bind(&inactive_cred)
    .bind(key.public_key().as_ref())
    .execute(&pool)
    .await
    .unwrap();

    let listed = PasskeyRepository::list_passkeys(&pool, user_id)
        .await
        .unwrap();
    let count = PasskeyRepository::passkey_count(&pool, user_id)
        .await
        .unwrap();
    assert_eq!(
        listed.len(),
        1,
        "list_passkeys must exclude inactive passkeys"
    );
    assert_eq!(count, 1, "passkey_count must exclude inactive passkeys");
    assert_eq!(listed[0].credential_id, active_cred);

    let start = passkey_service::login_start(
        &pool,
        PasskeyLoginStart {
            username: username.clone(),
        },
    )
    .await
    .expect("login_start returns challenge");
    let challenge_b64 = start["challenge"].as_str().unwrap().to_string();
    let challenge = STANDARD.decode(&challenge_b64).unwrap();
    let rp_id = hotel_app_be::core::config::get().passkey_rp_id.clone();
    let client_data = json!({
        "type": "webauthn.get",
        "challenge": URL_SAFE_NO_PAD.encode(&challenge),
        "origin": format!("https://{rp_id}"),
        "crossOrigin": false,
    })
    .to_string();
    let mut auth_data = Sha256::digest(rp_id.as_bytes()).to_vec();
    auth_data.push(0x05);
    auth_data.extend_from_slice(&1_u32.to_be_bytes());
    let mut signed = auth_data.clone();
    signed.extend_from_slice(&Sha256::digest(client_data.as_bytes()));
    let signature = key.sign(&rng, &signed).unwrap();

    let res = passkey_service::login_finish(
        &pool,
        PasskeyLoginFinish {
            username: username.clone(),
            credential_id: URL_SAFE_NO_PAD.encode(&active_cred),
            authenticator_data: STANDARD.encode(&auth_data),
            client_data_json: STANDARD.encode(client_data.as_bytes()),
            signature: STANDARD.encode(signature.as_ref()),
            challenge: challenge_b64,
        },
        Some("127.0.0.1"),
        Some("sec-audit"),
        None,
    )
    .await;

    assert!(
        matches!(res, Err(ApiError::Unauthorized(ref msg)) if msg.contains("verify your email")),
        "unverified account passkey login must be rejected, got {res:?}"
    );

    let _ = sqlx::query("DELETE FROM passkey_challenges WHERE user_id = $1")
        .bind(user_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM passkeys WHERE user_id = $1")
        .bind(user_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM audit_logs WHERE details->>'username' = $1")
        .bind(&username)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = $1")
        .bind(user_id)
        .execute(&pool)
        .await;
}

// ---------------------------------------------------------------------------
// M-01: Google OAuth token anti-replay & unverified account reclaim PII scrub
// ---------------------------------------------------------------------------

#[tokio::test]
async fn m01_oauth_anti_replay_and_reclaim_scrub() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();
    let token_hash = format!("sha256_oauth_replay_{tag}");

    let first =
        AuthRepository::consume_oauth_token(&pool, &token_hash, Utc::now() + Duration::minutes(10))
            .await
            .expect("first consume");
    let second =
        AuthRepository::consume_oauth_token(&pool, &token_hash, Utc::now() + Duration::minutes(10))
            .await
            .expect("second consume");
    assert!(first, "first OAuth token presentation must succeed");
    assert!(
        !second,
        "replayed OAuth token presentation must be rejected"
    );
    let _ = sqlx::query("DELETE FROM consumed_oauth_tokens WHERE token_hash = $1")
        .bind(&token_hash)
        .execute(&pool)
        .await;

    let email = format!("reclaim-{tag}@example.test");
    let guest_id: i64 = sqlx::query_scalar(
        "INSERT INTO guests (first_name, last_name, nick_name, email, phone) \
         VALUES ('Sq', 'Uatter', $1, $2, '+60123456000') RETURNING id",
    )
    .bind(format!("Squatter {tag}"))
    .bind(&email)
    .fetch_one(&pool)
    .await
    .unwrap();
    let user_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, phone, user_type, guest_id, is_active, is_verified) \
         VALUES ($1, $2, 'stale_hash', 'Squatter Name', '+60123456000', 'guest', $3, true, false) RETURNING id",
    )
    .bind(format!("squatter_{tag}"))
    .bind(&email)
    .bind(guest_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    let squatter_cred = format!("squatter-cred-{tag}").into_bytes();
    let squatter_chal = format!("squatter-challenge-{tag}").into_bytes();
    sqlx::query(
        "INSERT INTO passkeys (user_id, credential_id, public_key, counter, is_active) VALUES ($1, $2, $3, 0, true)",
    )
    .bind(user_id)
    .bind(&squatter_cred)
    .bind(b"squatter-pubkey".as_slice())
    .execute(&pool)
    .await
    .unwrap();
    sqlx::query(
        "INSERT INTO passkey_challenges (user_id, challenge, challenge_type, expires_at) \
         VALUES ($1, $2, 'authentication', CURRENT_TIMESTAMP + interval '5 minutes')",
    )
    .bind(user_id)
    .bind(&squatter_chal)
    .execute(&pool)
    .await
    .unwrap();

    let identity = GoogleIdentity {
        subject: format!("google-sub-{tag}"),
        email: email.clone(),
        email_verified: true,
        given_name: None,
        family_name: None,
        expiry: i64::MAX,
    };
    let resolution = AuthRepository::resolve_google_guest(&pool, &identity, None)
        .await
        .expect("reclaim must succeed");
    assert_eq!(resolution.user.id, user_id);
    assert!(
        resolution.user.is_verified,
        "reclaimed account must become verified"
    );
    assert!(
        resolution.user.phone.is_none(),
        "reclaimed unverified account must scrub squatter phone"
    );
    assert_ne!(
        resolution.user.full_name.as_deref(),
        Some("Squatter Name"),
        "reclaimed unverified account must overwrite squatter full_name"
    );

    let active_passkey_rows: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM passkeys WHERE user_id = $1 AND is_active = true")
            .bind(user_id)
            .fetch_one(&pool)
            .await
            .unwrap();
    let repo_passkey_count = PasskeyRepository::passkey_count(&pool, user_id)
        .await
        .unwrap();
    let remaining_challenges: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM passkey_challenges WHERE user_id = $1")
            .bind(user_id)
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(
        active_passkey_rows, 0,
        "reclaim must deactivate squatter passkeys"
    );
    assert_eq!(
        repo_passkey_count, 0,
        "reclaim must hide deactivated passkeys"
    );
    assert_eq!(
        remaining_challenges, 0,
        "reclaim must purge squatter passkey challenges"
    );

    let _ = sqlx::query("DELETE FROM user_roles WHERE user_id = $1")
        .bind(user_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = $1")
        .bind(user_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM guests WHERE id = $1")
        .bind(guest_id)
        .execute(&pool)
        .await;
}

// ---------------------------------------------------------------------------
// H-02: Loyalty staff/guest isolation
// ---------------------------------------------------------------------------

#[tokio::test]
async fn h02_staff_user_isolated_from_loyalty_guest_routes_and_repo() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();
    let shared_email = format!("h02-{tag}@example.test");

    let guest_id: i64 = sqlx::query_scalar(
        "INSERT INTO guests (first_name, last_name, nick_name, email) VALUES ('Victim', 'Guest', $1, $2) RETURNING id",
    )
    .bind(format!("Victim {tag}"))
    .bind(&shared_email)
    .fetch_one(&pool)
    .await
    .unwrap();

    // Staff account with matching email & NULL guest_id
    let staff1: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'Staff One', 'staff', true, true) RETURNING id",
    )
    .bind(format!("h02_s1_{tag}"))
    .bind(&shared_email)
    .fetch_one(&pool)
    .await
    .unwrap();
    // Defense-in-depth: staff user_type even with non-null guest_id
    let staff2: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, guest_id, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'Staff Two', 'staff', $3, true, true) RETURNING id",
    )
    .bind(format!("h02_s2_{tag}"))
    .bind(format!("h02-s2-{tag}@example.test"))
    .bind(guest_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    assert!(
        LoyaltyRepository::find_guest_for_user(&pool, staff1)
            .await
            .unwrap()
            .is_none(),
        "staff user must not resolve guest via email match"
    );
    assert!(
        LoyaltyRepository::find_guest_for_user(&pool, staff2)
            .await
            .unwrap()
            .is_none(),
        "staff user_type must never resolve a loyalty guest profile"
    );

    let (jwt, _) = mint_session(
        &pool,
        staff1,
        &format!("h02_s1_{tag}"),
        vec!["staff".into()],
    )
    .await;
    let app = routes::create_router(pool.clone());

    let (me_status, _) =
        http_call(&app, "GET", "/api/loyalty/me", Some(&jwt), Vec::new(), &[]).await;
    assert_eq!(
        me_status,
        StatusCode::FORBIDDEN,
        "GET /api/loyalty/me must reject staff users"
    );

    let (redeem_status, _) = http_call(
        &app,
        "POST",
        "/api/loyalty/rewards/1/redeem",
        Some(&jwt),
        b"{}".to_vec(),
        &[],
    )
    .await;
    assert_eq!(
        redeem_status,
        StatusCode::FORBIDDEN,
        "POST /api/loyalty/rewards/{{id}}/redeem must reject staff users"
    );

    let _ = sqlx::query("DELETE FROM refresh_tokens WHERE user_id = ANY($1)")
        .bind([staff1, staff2])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = ANY($1)")
        .bind([staff1, staff2])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM guests WHERE id = $1")
        .bind(guest_id)
        .execute(&pool)
        .await;
}

// ---------------------------------------------------------------------------
// H-03: Group voucher resplit enforces min_subtotal & promotion_room_types
// ---------------------------------------------------------------------------

#[tokio::test]
async fn h03_group_voucher_resplit_enforces_min_subtotal_and_room_types() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();
    let mut tx = pool.begin().await.unwrap();

    let guest_id: i64 =
        sqlx::query_scalar("INSERT INTO guests (nick_name, email) VALUES ($1, $2) RETURNING id")
            .bind(format!("H03 {tag}"))
            .bind(format!("h03-{tag}@example.test"))
            .fetch_one(&mut *tx)
            .await
            .unwrap();

    let rt_a: i64 = sqlx::query_scalar(
        "INSERT INTO room_types (code, name, base_price, max_occupancy) VALUES ($1, $2, 200.00, 2) RETURNING id",
    )
    .bind(short_code("HA"))
    .bind(format!("Type A {tag}"))
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    let rt_b: i64 = sqlx::query_scalar(
        "INSERT INTO room_types (code, name, base_price, max_occupancy) VALUES ($1, $2, 200.00, 2) RETURNING id",
    )
    .bind(short_code("HB"))
    .bind(format!("Type B {tag}"))
    .fetch_one(&mut *tx)
    .await
    .unwrap();

    let room_a: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'available', true) RETURNING id",
    )
    .bind(short_code("RA"))
    .bind(rt_a)
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    let room_b: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'available', true) RETURNING id",
    )
    .bind(short_code("RB"))
    .bind(rt_b)
    .fetch_one(&mut *tx)
    .await
    .unwrap();

    // Variant A: min_subtotal = 300 breached when one 200 room cancels.
    let promo_a: i64 = sqlx::query_scalar(
        "INSERT INTO promotions (slug, name, status, promotion_kind, discount_type, discount_value, min_subtotal) \
         VALUES ($1, $2, 'published', 'voucher', 'fixed_amount', 40.00, 300.00) RETURNING id",
    )
    .bind(format!("h03a-{tag}"))
    .bind(format!("H03 MinSubtotal {tag}"))
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    let voucher_a: i64 = sqlx::query_scalar(
        "INSERT INTO vouchers (promotion_id, guest_id, code, status, source, redeemed_at) \
         VALUES ($1, $2, $3, 'redeemed', 'admin_issue', CURRENT_TIMESTAMP) RETURNING id",
    )
    .bind(promo_a)
    .bind(guest_id)
    .bind(format!("VCHA-{tag}"))
    .fetch_one(&mut *tx)
    .await
    .unwrap();

    let group_a = BookingGroupRepository::create_tx(
        &mut tx,
        &NewBookingGroup {
            guest_id,
            check_in_date: NaiveDate::from_ymd_opt(2031, 4, 1).unwrap(),
            check_out_date: NaiveDate::from_ymd_opt(2031, 4, 3).unwrap(),
            source: "website".into(),
            currency: "MYR".into(),
            voucher_id: Some(voucher_a),
            hold_expires_at: None,
            portal_request_id: None,
            created_by: None,
        },
    )
    .await
    .unwrap();

    let bk_a1: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, booking_group_id, check_in_date, check_out_date, \
             room_rate, subtotal, discount_amount, group_voucher_share, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, $4, '2031-04-01', '2031-04-03', 100.00, 200.00, 20.00, 20.00, 180.00, 'pending_payment', 'unpaid') RETURNING id",
    )
    .bind(format!("BKA1-{tag}"))
    .bind(guest_id)
    .bind(room_a)
    .bind(group_a.id)
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    let bk_a2: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, booking_group_id, check_in_date, check_out_date, \
             room_rate, subtotal, discount_amount, group_voucher_share, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, $4, '2031-04-01', '2031-04-03', 100.00, 200.00, 20.00, 20.00, 180.00, 'pending_payment', 'unpaid') RETURNING id",
    )
    .bind(format!("BKA2-{tag}"))
    .bind(guest_id)
    .bind(room_b)
    .bind(group_a.id)
    .fetch_one(&mut *tx)
    .await
    .unwrap();

    release_room_share_tx(&mut tx, bk_a1, None).await.unwrap();
    sqlx::query("UPDATE bookings SET status = 'voided' WHERE id = $1")
        .bind(bk_a1)
        .execute(&mut *tx)
        .await
        .unwrap();

    let (share_a2, discount_a2, total_a2): (Decimal, Decimal, Decimal) = sqlx::query_as(
        "SELECT group_voucher_share, COALESCE(discount_amount, 0), total_amount FROM bookings WHERE id = $1",
    )
    .bind(bk_a2)
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    let vch_a_status: String = sqlx::query_scalar("SELECT status FROM vouchers WHERE id = $1")
        .bind(voucher_a)
        .fetch_one(&mut *tx)
        .await
        .unwrap();
    assert_eq!(
        share_a2,
        d("0"),
        "min_subtotal breach must clear remaining room share"
    );
    assert_eq!(
        discount_a2,
        d("0"),
        "min_subtotal breach must clear remaining discount"
    );
    assert_eq!(
        total_a2,
        d("200"),
        "remaining room total must restore without discount"
    );
    assert_eq!(
        vch_a_status, "available",
        "unfrozen voucher must be returned when min_subtotal fails"
    );

    // Variant B: promotion_room_types restricted strictly to rt_a; cancelling rt_a must NOT shift share to rt_b.
    let promo_b: i64 = sqlx::query_scalar(
        "INSERT INTO promotions (slug, name, status, promotion_kind, discount_type, discount_value, max_discount_amount, min_subtotal) \
         VALUES ($1, $2, 'published', 'voucher', 'percentage', 50.00, 50.00, 0.00) RETURNING id",
    )
    .bind(format!("h03b-{tag}"))
    .bind(format!("H03 RoomType {tag}"))
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    sqlx::query("INSERT INTO promotion_room_types (promotion_id, room_type_id) VALUES ($1, $2)")
        .bind(promo_b)
        .bind(rt_a)
        .execute(&mut *tx)
        .await
        .unwrap();
    let voucher_b: i64 = sqlx::query_scalar(
        "INSERT INTO vouchers (promotion_id, guest_id, code, status, source, redeemed_at) \
         VALUES ($1, $2, $3, 'redeemed', 'admin_issue', CURRENT_TIMESTAMP) RETURNING id",
    )
    .bind(promo_b)
    .bind(guest_id)
    .bind(format!("VCHB-{tag}"))
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    let group_b = BookingGroupRepository::create_tx(
        &mut tx,
        &NewBookingGroup {
            guest_id,
            check_in_date: NaiveDate::from_ymd_opt(2031, 4, 5).unwrap(),
            check_out_date: NaiveDate::from_ymd_opt(2031, 4, 7).unwrap(),
            source: "website".into(),
            currency: "MYR".into(),
            voucher_id: Some(voucher_b),
            hold_expires_at: None,
            portal_request_id: None,
            created_by: None,
        },
    )
    .await
    .unwrap();

    let bk_b1: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, booking_group_id, check_in_date, check_out_date, \
             room_rate, subtotal, discount_amount, group_voucher_share, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, $4, '2031-04-05', '2031-04-07', 100.00, 200.00, 50.00, 50.00, 150.00, 'pending_payment', 'unpaid') RETURNING id",
    )
    .bind(format!("BKB1-{tag}"))
    .bind(guest_id)
    .bind(room_a)
    .bind(group_b.id)
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    let bk_b2: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, booking_group_id, check_in_date, check_out_date, \
             room_rate, subtotal, discount_amount, group_voucher_share, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, $4, '2031-04-05', '2031-04-07', 100.00, 200.00, 0.00, 0.00, 200.00, 'pending_payment', 'unpaid') RETURNING id",
    )
    .bind(format!("BKB2-{tag}"))
    .bind(guest_id)
    .bind(room_b)
    .bind(group_b.id)
    .fetch_one(&mut *tx)
    .await
    .unwrap();

    let release_b = release_room_share_tx(&mut tx, bk_b1, None).await.unwrap();
    sqlx::query("UPDATE bookings SET status = 'voided' WHERE id = $1")
        .bind(bk_b1)
        .execute(&mut *tx)
        .await
        .unwrap();
    let (share_b2, discount_b2): (Decimal, Decimal) = sqlx::query_as(
        "SELECT group_voucher_share, COALESCE(discount_amount, 0) FROM bookings WHERE id = $1",
    )
    .bind(bk_b2)
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    assert_eq!(
        share_b2,
        d("0"),
        "ineligible room type must not receive released voucher share"
    );
    assert_eq!(
        discount_b2,
        d("0"),
        "ineligible room type must remain undiscounted"
    );
    assert!(
        matches!(
            release_b,
            ShareRelease::Dropped { .. } | ShareRelease::Moved { .. } | ShareRelease::Nothing
        ),
        "unexpected release variant: {release_b:?}"
    );

    tx.rollback().await.unwrap();
}

// ---------------------------------------------------------------------------
// H-04: Guest pending-cancel TOCTOU payment guard
// ---------------------------------------------------------------------------

#[tokio::test]
async fn h04_guest_cancel_blocks_completed_payment_on_booking_and_group() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();

    let guest_id: i64 =
        sqlx::query_scalar("INSERT INTO guests (nick_name, email) VALUES ($1, $2) RETURNING id")
            .bind(format!("H04 {tag}"))
            .bind(format!("h04-{tag}@example.test"))
            .fetch_one(&pool)
            .await
            .unwrap();
    let user_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, guest_id, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'H04 Guest', 'guest', $3, true, true) RETURNING id",
    )
    .bind(format!("h04_{tag}"))
    .bind(format!("h04-u-{tag}@example.test"))
    .bind(guest_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    let rt_id: i64 = sqlx::query_scalar(
        "INSERT INTO room_types (code, name, base_price, max_occupancy) VALUES ($1, $2, 100.00, 2) RETURNING id",
    )
    .bind(short_code("H4"))
    .bind(format!("H04 Type {tag}"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let room_id: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'available', true) RETURNING id",
    )
    .bind(short_code("R4"))
    .bind(rt_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    let booking_id: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, '2031-05-01', '2031-05-02', 100.00, 100.00, 100.00, 'pending_payment', 'unpaid') RETURNING id",
    )
    .bind(format!("BK4-{tag}"))
    .bind(guest_id)
    .bind(room_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    sqlx::query(
        "INSERT INTO payments (uuid, booking_id, amount, currency, payment_method, status) \
         VALUES (gen_random_uuid(), $1, 100.00, 'MYR', 'card', 'completed')",
    )
    .bind(booking_id)
    .execute(&pool)
    .await
    .unwrap();

    let err = bookings_service::cancel_pending_booking_by_guest(
        &pool,
        user_id,
        booking_id,
        Some("changed mind".into()),
    )
    .await
    .expect_err("completed payment must block guest pending cancel");
    assert!(
        matches!(err, ApiError::Conflict(_)),
        "expected 409 Conflict, got {err:?}"
    );
    let status_after: String = sqlx::query_scalar("SELECT status FROM bookings WHERE id = $1")
        .bind(booking_id)
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(status_after, "pending_payment");

    let group_id: i64 = sqlx::query_scalar(
        "INSERT INTO booking_groups (group_number, guest_id, check_in_date, check_out_date, status, payment_status) \
         VALUES ($1, $2, '2031-05-10', '2031-05-12', 'active', 'unpaid') RETURNING id",
    )
    .bind(format!("G4-{tag}"))
    .bind(guest_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    let group_bk_id: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, booking_group_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, $4, '2031-05-10', '2031-05-12', 100.00, 100.00, 100.00, 'pending_payment', 'unpaid') RETURNING id",
    )
    .bind(format!("BKG4-{tag}"))
    .bind(guest_id)
    .bind(room_id)
    .bind(group_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    sqlx::query(
        "INSERT INTO payments (uuid, booking_id, amount, currency, payment_method, status) \
         VALUES (gen_random_uuid(), $1, 100.00, 'MYR', 'card', 'completed')",
    )
    .bind(group_bk_id)
    .execute(&pool)
    .await
    .unwrap();

    let group_err = bookings_service::cancel_pending_group_by_guest(&pool, user_id, group_id, None)
        .await
        .expect_err("completed payment on group member must block group pending cancel");
    assert!(
        matches!(group_err, ApiError::Conflict(_)),
        "expected 409 Conflict for group cancel, got {group_err:?}"
    );

    let _ = sqlx::query("DELETE FROM payments WHERE booking_id = ANY($1)")
        .bind([booking_id, group_bk_id])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM bookings WHERE id = ANY($1)")
        .bind([booking_id, group_bk_id])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM booking_groups WHERE id = $1")
        .bind(group_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM rooms WHERE id = $1")
        .bind(room_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM room_types WHERE id = $1")
        .bind(rt_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = $1")
        .bind(user_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM guests WHERE id = $1")
        .bind(guest_id)
        .execute(&pool)
        .await;
}

// ---------------------------------------------------------------------------
// H-05: Negative booking charges rejected in handler and by PostgreSQL CHECK constraints
// ---------------------------------------------------------------------------

#[tokio::test]
async fn h05_negative_booking_charges_rejected_by_handler_and_db_constraints() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();

    let actor_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'H05 Staff', 'staff', true, true) RETURNING id",
    )
    .bind(format!("h05_{tag}"))
    .bind(format!("h05-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let role_name = format!("h05_role_{tag}");
    grant_role_with_permissions(&pool, actor_id, &role_name, &["bookings:update"]).await;

    let guest_id: i64 =
        sqlx::query_scalar("INSERT INTO guests (nick_name, email) VALUES ($1, $2) RETURNING id")
            .bind(format!("H05 {tag}"))
            .bind(format!("h05-g-{tag}@example.test"))
            .fetch_one(&pool)
            .await
            .unwrap();
    let rt_id: i64 = sqlx::query_scalar(
        "INSERT INTO room_types (code, name, base_price, max_occupancy) VALUES ($1, $2, 120.00, 2) RETURNING id",
    )
    .bind(short_code("H5"))
    .bind(format!("H05 Type {tag}"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let room_id: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'available', true) RETURNING id",
    )
    .bind(short_code("R5"))
    .bind(rt_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    let booking_id: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, '2031-06-01', '2031-06-03', 120.00, 240.00, 240.00, 'confirmed', 'unpaid') RETURNING id",
    )
    .bind(format!("BK5-{tag}"))
    .bind(guest_id)
    .bind(room_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    for bad_input in [
        BookingUpdateInput {
            extra_bed_charge: Some(-10.0),
            ..Default::default()
        },
        BookingUpdateInput {
            room_rate_override: Some(-1.0),
            ..Default::default()
        },
        BookingUpdateInput {
            extra_bed_count: Some(-1),
            ..Default::default()
        },
    ] {
        let res = update_booking_handler(
            State(pool.clone()),
            Extension(actor_id),
            Path(booking_id),
            Json(bad_input),
        )
        .await;
        assert!(
            matches!(res, Err(ApiError::BadRequest(_))),
            "negative charge in update_booking_handler must return 400 BadRequest, got {res:?}"
        );
    }

    for (sql, constraint) in [
        (
            "UPDATE bookings SET room_rate = -1 WHERE id = $1",
            "bookings_room_rate_non_negative",
        ),
        (
            "UPDATE bookings SET subtotal = -1 WHERE id = $1",
            "bookings_subtotal_non_negative",
        ),
        (
            "UPDATE bookings SET total_amount = -1 WHERE id = $1",
            "bookings_total_amount_non_negative",
        ),
        (
            "UPDATE bookings SET extra_bed_charge = -1 WHERE id = $1",
            "bookings_extra_bed_charge_non_negative",
        ),
        (
            "UPDATE bookings SET extra_bed_count = -1 WHERE id = $1",
            "bookings_extra_bed_count_non_negative",
        ),
    ] {
        let err = sqlx::query(sql)
            .bind(booking_id)
            .execute(&pool)
            .await
            .expect_err("DB CHECK constraint must reject negative column");
        let msg = err.to_string();
        assert!(
            msg.contains(constraint),
            "expected constraint '{constraint}' in error: {msg}"
        );
    }

    let _ = sqlx::query("DELETE FROM bookings WHERE id = $1")
        .bind(booking_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM rooms WHERE id = $1")
        .bind(room_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM room_types WHERE id = $1")
        .bind(rt_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM guests WHERE id = $1")
        .bind(guest_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM user_roles WHERE user_id = $1")
        .bind(actor_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE name = $1)",
    )
    .bind(&role_name)
    .execute(&pool)
    .await;
    let _ = sqlx::query("DELETE FROM roles WHERE name = $1")
        .bind(&role_name)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = $1")
        .bind(actor_id)
        .execute(&pool)
        .await;
    hotel_app_be::core::rbac_cache::clear_all();
}

// ---------------------------------------------------------------------------
// H-06 / M-02 / L-01: data_transfer settings:manage gate, metadata-only preview, & export audit
// ---------------------------------------------------------------------------

#[tokio::test]
async fn h06_m02_l01_data_transfer_system_settings_and_export_audit() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();

    let uploader_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'DT Staff', 'staff', true, true) RETURNING id",
    )
    .bind(format!("dt_{tag}"))
    .bind(format!("dt-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let exporter_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'DT Exporter', 'staff', true, true) RETURNING id",
    )
    .bind(format!("dtexp_{tag}"))
    .bind(format!("dtexp-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();

    let role_import = format!("dt_imp_{tag}");
    grant_role_with_permissions(
        &pool,
        uploader_id,
        &role_import,
        &["data_transfer:import", "data_transfer:import_sensitive"],
    )
    .await;
    let role_exp = format!("dt_exp_{tag}");
    grant_role_with_permissions(&pool, exporter_id, &role_exp, &["data_transfer:export"]).await;

    let (jwt_imp, sid_imp) = mint_session(
        &pool,
        uploader_id,
        &format!("dt_{tag}"),
        vec!["staff".into()],
    )
    .await;
    let (jwt_exp, _) = mint_session(
        &pool,
        exporter_id,
        &format!("dtexp_{tag}"),
        vec!["staff".into()],
    )
    .await;
    let app = routes::create_router(pool.clone());

    let doc = serde_json::to_vec(&json!({
        "format": "hotel-backup",
        "version": 1,
        "kind": "business-data",
        "exportId": "31111111-2222-3333-4444-555555555555",
        "exportedAt": "2026-09-15T12:00:00Z",
        "applicationVersion": env!("CARGO_PKG_VERSION"),
        "source": {"environment": "development", "databaseProvider": "postgresql"},
        "manifest": {
            "entities": [{"name": "public.system_settings", "primaryKey": ["id"], "columns": ["id", "key", "value"]}],
            "exclusions": []
        },
        "tables": {
            "public.system_settings": [{"id": 1, "key": "hotel_name", "value": "Tampered"}]
        },
        "integrity": {
            "entities": 1,
            "rows": 1,
            "entityRows": {"public.system_settings": 1},
            "completedAt": "2026-09-15T12:00:01Z"
        }
    }))
    .unwrap();

    let (up_status, up_body) = http_call(
        &app,
        "POST",
        "/api/data-transfer/import/uploads",
        Some(&jwt_imp),
        doc,
        &[("content-type", "application/octet-stream")],
    )
    .await;
    assert_eq!(up_status, StatusCode::OK, "staging fixture must succeed");
    let up_json: Value = serde_json::from_slice(&up_body).unwrap();
    let upload_id = up_json["uploadId"].as_str().unwrap();

    // Preview without settings:manage -> metadata-only preview (existing == 0, requiresPermissions lists settings:manage)
    let (prev_status, prev_body) = http_call(
        &app,
        "POST",
        "/api/data-transfer/import/preview",
        Some(&jwt_imp),
        json!({"uploadId": upload_id}).to_string().into_bytes(),
        &[],
    )
    .await;
    assert_eq!(prev_status, StatusCode::OK);
    let prev_json: Value = serde_json::from_slice(&prev_body).unwrap();
    assert_eq!(prev_json["sensitive"], json!(true));
    let req_perms = prev_json["requiresPermissions"].as_array().unwrap();
    assert!(
        req_perms.iter().any(|p| p == "settings:manage"),
        "preview must report settings:manage requirement for public.system_settings: {prev_json}"
    );
    let entities = prev_json["entities"].as_array().unwrap();
    assert_eq!(
        entities[0]["existing"],
        json!(0),
        "unauthorized settings:manage preview must return metadata-only zero existing counts (no DB probe)"
    );
    // Keep clippy happy that ImportPreview type matches exported shape expectations.
    let _: fn() -> Option<ImportPreview> = || None;

    // Execute with step-up token but WITHOUT settings:manage -> 403 Forbidden
    let step_up =
        AuthService::issue_step_up_token(uploader_id, format!("dt_{tag}"), Some(sid_imp)).unwrap();
    let (exec_status, _) = http_call(
        &app,
        "POST",
        "/api/data-transfer/import/execute",
        Some(&jwt_imp),
        json!({"uploadId": upload_id, "mode": "merge", "onConflict": "skip", "confirm": true})
            .to_string()
            .into_bytes(),
        &[("x-step-up", &step_up)],
    )
    .await;
    assert_eq!(
        exec_status,
        StatusCode::FORBIDDEN,
        "system_settings import without settings:manage must fail with 403"
    );

    // Export writes data_export_started audit entry
    let (exp_status, _) = http_call(
        &app,
        "GET",
        "/api/data-transfer/export",
        Some(&jwt_exp),
        Vec::new(),
        &[],
    )
    .await;
    assert_eq!(exp_status, StatusCode::OK);
    let started_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE user_id = $1 AND action = 'data_export_started'",
    )
    .bind(exporter_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert!(
        started_count >= 1,
        "stream_export must persist data_export_started before streaming"
    );

    for uid in [uploader_id, exporter_id] {
        let _ = sqlx::query("DELETE FROM audit_logs WHERE user_id = $1")
            .bind(uid)
            .execute(&pool)
            .await;
        let _ = sqlx::query("DELETE FROM refresh_tokens WHERE user_id = $1")
            .bind(uid)
            .execute(&pool)
            .await;
        let _ = sqlx::query("DELETE FROM user_roles WHERE user_id = $1")
            .bind(uid)
            .execute(&pool)
            .await;
    }
    let _ = sqlx::query(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE name = ANY($1))",
    )
    .bind([&role_import, &role_exp])
    .execute(&pool)
    .await;
    let _ = sqlx::query("DELETE FROM roles WHERE name = ANY($1)")
        .bind([&role_import, &role_exp])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = ANY($1)")
        .bind([uploader_id, exporter_id])
        .execute(&pool)
        .await;
    hotel_app_be::core::rbac_cache::clear_all();
}

// ---------------------------------------------------------------------------
// M-03: Email HTML sanitization & template variable allowlist
// ---------------------------------------------------------------------------

#[tokio::test]
async fn m03_communications_sanitizes_html_and_enforces_variable_allowlist() {
    let dirty = r#"<p>Hello</p><script>alert(1)</script><img src="x" onerror="fetch('https://evil')"><a href="javascript:alert(2)">Click</a>"#;
    let clean = sanitize_email_html(dirty);
    assert!(clean.contains("<p>Hello</p>"), "safe HTML must survive");
    assert!(
        !clean.contains("<script"),
        "script tag must be stripped: {clean}"
    );
    assert!(
        !clean.contains("onerror"),
        "inline handler must be stripped: {clean}"
    );
    assert!(
        !clean.contains("javascript:"),
        "javascript: URI must be stripped: {clean}"
    );

    let bad = validate_template_input(TemplateInput {
        code: "sec_m03_bad".into(),
        name: "Bad Template".into(),
        subject: "Hi {{guest_name}}".into(),
        body_html: "<p>Secret {{internal_admin_token}}</p>".into(),
        body_text: None,
        variables: Some(vec!["internal_admin_token".into()]),
        is_active: Some(true),
    });
    assert!(
        matches!(bad, Err(ApiError::BadRequest(_))),
        "unknown template variable must be rejected, got {bad:?}"
    );

    let ok = validate_template_input(TemplateInput {
        code: "sec_m03_ok".into(),
        name: "Ok Template".into(),
        subject: "Hi {{guest_name}}".into(),
        body_html: "<p>Booking {{booking_number}} confirmed</p>".into(),
        body_text: None,
        variables: Some(vec!["guest_name".into(), "booking_number".into()]),
        is_active: Some(true),
    });
    assert!(ok.is_ok(), "allowlisted variables must validate cleanly");
}

// ---------------------------------------------------------------------------
// M-05 / M-06 / M-14: Origin/Sec-Fetch-Site CSRF, passwordless login mask & verification cooldown
// ---------------------------------------------------------------------------

#[tokio::test]
async fn m05_m06_m14_csrf_passwordless_login_mask_and_verification_cooldown() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();

    // M-05: Sec-Fetch-Site cross-site & Referer checks
    let mut bad_sfs = axum::http::HeaderMap::new();
    bad_sfs.insert("sec-fetch-site", "cross-site".parse().unwrap());
    assert!(matches!(
        validate_request_origin(&bad_sfs),
        Err(ApiError::Forbidden(_))
    ));

    let mut bad_ref = axum::http::HeaderMap::new();
    bad_ref.insert(
        header::REFERER,
        "https://evil.example/form".parse().unwrap(),
    );
    assert!(matches!(
        validate_request_origin(&bad_ref),
        Err(ApiError::Forbidden(_))
    ));

    let mut good_hdrs = axum::http::HeaderMap::new();
    good_hdrs.insert("sec-fetch-site", "same-origin".parse().unwrap());
    good_hdrs.insert(header::ORIGIN, "https://app.hotel.test".parse().unwrap());
    assert!(validate_request_origin(&good_hdrs).is_ok());

    // M-06: Passwordless Google-only account returns uniform "Invalid credentials"
    let pwless_user = format!("m06_{tag}");
    let pwless_email = format!("m06-{tag}@example.test");
    let google_sub = format!("google-sub-m06-{tag}");
    let pwless_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified, google_subject) \
         VALUES ($1, $2, NULL, 'Google Only', 'guest', true, true, $3) RETURNING id",
    )
    .bind(&pwless_user)
    .bind(&pwless_email)
    .bind(&google_sub)
    .fetch_one(&pool)
    .await
    .unwrap();

    let login_err = auth_service::login(
        &pool,
        LoginRequest {
            username: pwless_user.clone(),
            password: "WrongPassword123!".into(),
            totp_code: None,
        },
        Some("127.0.0.1"),
        Some("sec-audit"),
        None,
    )
    .await
    .expect_err("passwordless login must fail");
    assert!(
        matches!(login_err, ApiError::Unauthorized(ref m) if m == "Invalid credentials"),
        "passwordless login must return generic Invalid credentials, got {login_err:?}"
    );

    // M-14: Verification resend returns generic message and respects 60s cooldown
    let unv_email = format!("m14-{tag}@example.test");
    let unv_guest_id: i64 =
        sqlx::query_scalar("INSERT INTO guests (nick_name, email) VALUES ($1, $2) RETURNING id")
            .bind(format!("M14 {tag}"))
            .bind(&unv_email)
            .fetch_one(&pool)
            .await
            .unwrap();
    let unv_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, guest_id, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'M14 User', 'guest', $3, true, false) RETURNING id",
    )
    .bind(format!("m14_{tag}"))
    .bind(&unv_email)
    .bind(unv_guest_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    let r1 = auth_service::resend_verification(
        &pool,
        ResendVerificationRequest {
            email: unv_email.clone(),
        },
    )
    .await
    .unwrap();
    let sent_at_1: Option<chrono::DateTime<Utc>> =
        sqlx::query_scalar("SELECT email_token_expires_at FROM users WHERE id = $1")
            .bind(unv_id)
            .fetch_one(&pool)
            .await
            .unwrap();
    let queued_1: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM email_deliveries WHERE recipient_email = $1")
            .bind(&unv_email)
            .fetch_one(&pool)
            .await
            .unwrap();
    let r2 = auth_service::resend_verification(
        &pool,
        ResendVerificationRequest {
            email: unv_email.clone(),
        },
    )
    .await
    .unwrap();
    let sent_at_2: Option<chrono::DateTime<Utc>> =
        sqlx::query_scalar("SELECT email_token_expires_at FROM users WHERE id = $1")
            .bind(unv_id)
            .fetch_one(&pool)
            .await
            .unwrap();
    let queued_2: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM email_deliveries WHERE recipient_email = $1")
            .bind(&unv_email)
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(
        r1, r2,
        "resend_verification must return identical generic payloads"
    );
    assert!(
        sent_at_1.is_some(),
        "first resend must stamp email_token_expires_at"
    );
    assert_eq!(
        sent_at_1, sent_at_2,
        "immediate second resend must respect cooldown"
    );
    assert_eq!(queued_1, 1, "first resend must queue exactly one email");
    assert_eq!(
        queued_2, 1,
        "cooldown must prevent queueing duplicate verification email"
    );

    let _ = sqlx::query("DELETE FROM email_deliveries WHERE recipient_email = $1")
        .bind(&unv_email)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM audit_logs WHERE details->>'username' = ANY($1)")
        .bind([&pwless_user, &format!("m14_{tag}")])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = ANY($1)")
        .bind([pwless_id, unv_id])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM guests WHERE id = $1")
        .bind(unv_guest_id)
        .execute(&pool)
        .await;
}

// ---------------------------------------------------------------------------
// M-07 / M-08 / M-09: Closed business day payment block, ledger immutability & promo window
// ---------------------------------------------------------------------------

#[tokio::test]
async fn m07_m08_m09_business_date_ledger_void_and_promo_claim_window() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();

    let actor_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'M07 Staff', 'staff', true, true) RETURNING id",
    )
    .bind(format!("m07_{tag}"))
    .bind(format!("m07-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let guest_id: i64 =
        sqlx::query_scalar("INSERT INTO guests (nick_name, email) VALUES ($1, $2) RETURNING id")
            .bind(format!("M07 {tag}"))
            .bind(format!("m07-g-{tag}@example.test"))
            .fetch_one(&pool)
            .await
            .unwrap();
    let rt_id: i64 = sqlx::query_scalar(
        "INSERT INTO room_types (code, name, base_price, max_occupancy) VALUES ($1, $2, 100.00, 2) RETURNING id",
    )
    .bind(short_code("M7"))
    .bind(format!("M07 Type {tag}"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let room_id: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'available', true) RETURNING id",
    )
    .bind(short_code("RM7"))
    .bind(rt_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    let booking_id: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, '2031-07-01', '2031-07-02', 100.00, 100.00, 100.00, 'confirmed', 'unpaid') RETURNING id",
    )
    .bind(format!("BKM7-{tag}"))
    .bind(guest_id)
    .bind(room_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    // Ensure a closed night-audit date exists (use 2029-12-31 if not already closed, clean up afterwards if we inserted it).
    let closed_date = NaiveDate::from_ymd_opt(2029, 12, 31).unwrap();
    let audit_run_inserted: Option<i64> = {
        let already: bool = sqlx::query_scalar(
            "SELECT EXISTS(SELECT 1 FROM night_audit_runs WHERE status = 'completed' AND audit_date >= $1)",
        )
        .bind(closed_date)
        .fetch_one(&pool)
        .await
        .unwrap();
        if already {
            None
        } else {
            let run_id: i64 = sqlx::query_scalar(
                "INSERT INTO night_audit_runs (audit_date, run_at, run_by, status) \
                 VALUES ($1, CURRENT_TIMESTAMP, $2, 'completed') RETURNING id",
            )
            .bind(closed_date)
            .bind(actor_id)
            .fetch_one(&pool)
            .await
            .unwrap();
            Some(run_id)
        }
    };

    let pay_err = payments_service::record_payment(
        &pool,
        actor_id,
        RecordPaymentRequest {
            booking_id,
            amount: 50.0,
            payment_method: "cash".into(),
            payment_type: Some("booking".into()),
            transaction_reference: None,
            notes: None,
            payment_date: Some(closed_date.to_string()),
            idempotency_key: format!("idem-{tag}"),
        },
    )
    .await
    .expect_err("backdated payment on closed business day must fail");
    assert!(
        matches!(pay_err, ApiError::BadRequest(ref m) if m.contains("Night audit has already closed")),
        "expected closed business day error, got {pay_err:?}"
    );
    if let Some(run_id) = audit_run_inserted {
        let _ = sqlx::query("DELETE FROM night_audit_runs WHERE id = $1")
            .bind(run_id)
            .execute(&pool)
            .await;
    }

    // M-08: voided ledger update rejected & paid amount recomputes status
    let void_ledger = ledgers_service::create_customer_ledger(
        &pool,
        actor_id,
        CustomerLedgerCreateRequest {
            company_name: format!("M08 Void {tag}"),
            company_registration_number: None,
            contact_person: None,
            contact_email: None,
            contact_phone: None,
            billing_address_line1: None,
            billing_city: None,
            billing_state: None,
            billing_postal_code: None,
            billing_country: None,
            description: "initial".into(),
            expense_type: "corporate_billing".into(),
            amount: 100.0,
            currency: Some("MYR".into()),
            booking_id: None,
            guest_id: Some(guest_id),
            invoice_date: None,
            due_date: None,
            notes: None,
            internal_notes: None,
            folio_type: None,
            transaction_type: None,
            post_type: None,
            department_code: None,
            transaction_code: None,
            room_number: None,
            posting_date: None,
            transaction_date: None,
            reference_number: None,
            tax_amount: None,
            service_charge: None,
        },
    )
    .await
    .unwrap();
    sqlx::query(
        "UPDATE customer_ledgers SET status = 'void', void_at = CURRENT_TIMESTAMP WHERE id = $1",
    )
    .bind(void_ledger.id)
    .execute(&pool)
    .await
    .unwrap();

    let empty_update = || CustomerLedgerUpdateRequest {
        company_name: None,
        company_registration_number: None,
        contact_person: None,
        contact_email: None,
        contact_phone: None,
        billing_address_line1: None,
        billing_city: None,
        billing_state: None,
        billing_postal_code: None,
        billing_country: None,
        description: Some("tamper".into()),
        expense_type: None,
        amount: None,
        currency: None,
        status: None,
        invoice_date: None,
        due_date: None,
        notes: None,
        internal_notes: None,
        post_type: None,
        department_code: None,
        transaction_code: None,
        room_number: None,
        reference_number: None,
        tax_amount: None,
        service_charge: None,
    };
    let void_err =
        ledgers_service::update_customer_ledger(&pool, void_ledger.id, actor_id, empty_update())
            .await
            .expect_err("void ledger must be immutable");
    assert!(matches!(void_err, ApiError::BadRequest(_)));

    let paid_ledger = ledgers_service::create_customer_ledger(
        &pool,
        actor_id,
        CustomerLedgerCreateRequest {
            company_name: format!("M08 Paid {tag}"),
            company_registration_number: None,
            contact_person: None,
            contact_email: None,
            contact_phone: None,
            billing_address_line1: None,
            billing_city: None,
            billing_state: None,
            billing_postal_code: None,
            billing_country: None,
            description: "paid".into(),
            expense_type: "corporate_billing".into(),
            amount: 100.0,
            currency: Some("MYR".into()),
            booking_id: None,
            guest_id: Some(guest_id),
            invoice_date: None,
            due_date: None,
            notes: None,
            internal_notes: None,
            folio_type: None,
            transaction_type: None,
            post_type: None,
            department_code: None,
            transaction_code: None,
            room_number: None,
            posting_date: None,
            transaction_date: None,
            reference_number: None,
            tax_amount: None,
            service_charge: None,
        },
    )
    .await
    .unwrap();
    sqlx::query("UPDATE customer_ledgers SET paid_amount = 100.00, status = 'paid' WHERE id = $1")
        .bind(paid_ledger.id)
        .execute(&pool)
        .await
        .unwrap();
    let mut increase = empty_update();
    increase.amount = Some(150.0);
    let updated_paid =
        ledgers_service::update_customer_ledger(&pool, paid_ledger.id, actor_id, increase)
            .await
            .unwrap();
    assert_eq!(
        updated_paid.status, "partial",
        "increasing amount on paid ledger must recompute status to partial"
    );

    // M-09: reserve_claim_capacity enforces claim_starts_at & claim_ends_at
    let mut tx = pool.begin().await.unwrap();
    let future_promo: i64 = sqlx::query_scalar(
        "INSERT INTO promotions (slug, name, status, promotion_kind, discount_type, discount_value, claim_starts_at) \
         VALUES ($1, $2, 'published', 'voucher', 'fixed_amount', 10.00, CURRENT_TIMESTAMP + interval '2 days') RETURNING id",
    )
    .bind(format!("m09f-{tag}"))
    .bind(format!("Future Promo {tag}"))
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    let expired_promo: i64 = sqlx::query_scalar(
        "INSERT INTO promotions (slug, name, status, promotion_kind, discount_type, discount_value, claim_ends_at) \
         VALUES ($1, $2, 'published', 'voucher', 'fixed_amount', 10.00, CURRENT_TIMESTAMP - interval '2 days') RETURNING id",
    )
    .bind(format!("m09e-{tag}"))
    .bind(format!("Expired Promo {tag}"))
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    assert!(
        !PromotionRepository::reserve_claim_capacity(&mut tx, future_promo)
            .await
            .unwrap()
    );
    assert!(
        !PromotionRepository::reserve_claim_capacity(&mut tx, expired_promo)
            .await
            .unwrap()
    );
    tx.rollback().await.unwrap();

    let _ = sqlx::query("DELETE FROM customer_ledgers WHERE id = ANY($1)")
        .bind([void_ledger.id, paid_ledger.id])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM bookings WHERE id = $1")
        .bind(booking_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM rooms WHERE id = $1")
        .bind(room_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM room_types WHERE id = $1")
        .bind(rt_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM guests WHERE id = $1")
        .bind(guest_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM audit_logs WHERE user_id = $1")
        .bind(actor_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = $1")
        .bind(actor_id)
        .execute(&pool)
        .await;
}

// ---------------------------------------------------------------------------
// M-10: Anonymous booking client_request_id bounds & replay token guards
// ---------------------------------------------------------------------------

#[tokio::test]
async fn m10_anonymous_request_id_bounds_and_replay_guards() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();

    assert!(validate_anonymous_client_request_id("short-123").is_err());
    assert!(validate_anonymous_client_request_id(&"a".repeat(129)).is_err());
    let valid_req = validate_anonymous_client_request_id(&format!("valid_req_id_{tag}")).unwrap();

    let email = format!("m10-{tag}@example.test");
    let guest_id: i64 =
        sqlx::query_scalar("INSERT INTO guests (nick_name, email) VALUES ($1, $2) RETURNING id")
            .bind(format!("M10 {tag}"))
            .bind(&email)
            .fetch_one(&pool)
            .await
            .unwrap();
    let staff_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'M10 Staff', 'staff', true, true) RETURNING id",
    )
    .bind(format!("m10_{tag}"))
    .bind(format!("m10-s-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let rt_id: i64 = sqlx::query_scalar(
        "INSERT INTO room_types (code, name, base_price, max_occupancy) VALUES ($1, $2, 100.00, 2) RETURNING id",
    )
    .bind(short_code("M10"))
    .bind(format!("M10 Type {tag}"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let room_id: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'available', true) RETURNING id",
    )
    .bind(short_code("R10"))
    .bind(rt_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    // 1. Expired pre-checkin token must not replay
    let bk_expired: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, payment_status, portal_request_id, pre_checkin_token_expires_at) \
         VALUES ($1, $2, $3, '2031-08-01', '2031-08-02', 100.00, 100.00, 100.00, 'pending_payment', 'unpaid', $4, CURRENT_TIMESTAMP - interval '1 hour') RETURNING id",
    )
    .bind(format!("BKM10A-{tag}"))
    .bind(guest_id)
    .bind(room_id)
    .bind(&valid_req)
    .fetch_one(&pool)
    .await
    .unwrap();

    assert!(
        GuestBookingRepository::find_anonymous_by_request_id(&pool, &valid_req, &email)
            .await
            .unwrap()
            .is_none(),
        "expired hold must not be returned by anonymous replay lookup"
    );

    // 2. Authenticated created_by booking must not replay anonymously
    let valid_req2 = format!("valid_req_id2_{tag}");
    let bk_auth: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, payment_status, created_by, portal_request_id, pre_checkin_token_expires_at) \
         VALUES ($1, $2, $3, '2031-08-05', '2031-08-06', 100.00, 100.00, 100.00, 'pending_payment', 'unpaid', $4, $5, CURRENT_TIMESTAMP + interval '1 hour') RETURNING id",
    )
    .bind(format!("BKM10B-{tag}"))
    .bind(guest_id)
    .bind(room_id)
    .bind(staff_id)
    .bind(&valid_req2)
    .fetch_one(&pool)
    .await
    .unwrap();

    assert!(
        GuestBookingRepository::find_anonymous_by_request_id(&pool, &valid_req2, &email)
            .await
            .unwrap()
            .is_none(),
        "created_by IS NOT NULL booking must not be returned by anonymous replay lookup"
    );

    let _ = sqlx::query("DELETE FROM bookings WHERE id = ANY($1)")
        .bind([bk_expired, bk_auth])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM rooms WHERE id = $1")
        .bind(room_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM room_types WHERE id = $1")
        .bind(rt_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = $1")
        .bind(staff_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM guests WHERE id = $1")
        .bind(guest_id)
        .execute(&pool)
        .await;
}

// ---------------------------------------------------------------------------
// L-03 / L-04 / L-05 / L-09b: Audit LIKE escape, Group Receipt GET/POST split, gRPC guest guard, Team reactivation
// ---------------------------------------------------------------------------

#[tokio::test]
async fn l03_l04_l05_l09b_audit_like_group_receipt_grpc_and_team_reactivation() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();

    // L-03: LIKE metacharacters in AuditLogQuery.search are escaped literally
    let res_literal = format!("l03_lit_{tag}");
    let res_other = format!("l03_oth_{tag}");
    let a1: i64 = sqlx::query_scalar(
        "INSERT INTO audit_logs (action, resource_type) VALUES ($1, $2) RETURNING id",
    )
    .bind(format!("sec_{tag}_100%_exact"))
    .bind(&res_literal)
    .fetch_one(&pool)
    .await
    .unwrap();
    let a2: i64 = sqlx::query_scalar(
        "INSERT INTO audit_logs (action, resource_type) VALUES ($1, $2) RETURNING id",
    )
    .bind(format!("sec_{tag}_100abc_other"))
    .bind(&res_other)
    .fetch_one(&pool)
    .await
    .unwrap();

    let counts = AuditRepository::count_by_resource_type(
        &pool,
        &AuditLogQuery {
            search: Some(format!("sec_{tag}_100%")),
            ..Default::default()
        },
    )
    .await
    .unwrap();
    assert_eq!(
        counts.len(),
        1,
        "escaped LIKE search must match only literal 100%, got {counts:?}"
    );
    assert_eq!(counts[0].resource_type, res_literal);
    assert_eq!(counts[0].count, 1);

    // L-04: Group receipt GET (read-only 404, creates 0 invoices) vs POST (requires payments:create)
    let reader_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'Receipt Reader', 'staff', true, true) RETURNING id",
    )
    .bind(format!("l04_read_{tag}"))
    .bind(format!("l04r-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let reader_role = format!("l04_read_role_{tag}");
    grant_role_with_permissions(&pool, reader_id, &reader_role, &["payments:read"]).await;
    let (jwt_reader, _) = mint_session(
        &pool,
        reader_id,
        &format!("l04_read_{tag}"),
        vec!["staff".into()],
    )
    .await;

    let guest_id: i64 =
        sqlx::query_scalar("INSERT INTO guests (nick_name, email) VALUES ($1, $2) RETURNING id")
            .bind(format!("L04 {tag}"))
            .bind(format!("l04-g-{tag}@example.test"))
            .fetch_one(&pool)
            .await
            .unwrap();
    let rt_id: i64 = sqlx::query_scalar(
        "INSERT INTO room_types (code, name, base_price, max_occupancy) VALUES ($1, $2, 100.00, 2) RETURNING id",
    )
    .bind(short_code("L4"))
    .bind(format!("L04 Type {tag}"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let room_id: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'available', true) RETURNING id",
    )
    .bind(short_code("RL4"))
    .bind(rt_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    let group_id: i64 = sqlx::query_scalar(
        "INSERT INTO booking_groups (group_number, guest_id, check_in_date, check_out_date, status, payment_status) \
         VALUES ($1, $2, '2031-09-01', '2031-09-02', 'active', 'paid') RETURNING id",
    )
    .bind(format!("GL4-{tag}"))
    .bind(guest_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    let group_bk_id: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, booking_group_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, $4, '2031-09-01', '2031-09-02', 100.00, 100.00, 100.00, 'confirmed', 'paid') RETURNING id",
    )
    .bind(format!("BKL4-{tag}"))
    .bind(guest_id)
    .bind(room_id)
    .bind(group_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    let app = routes::create_router(pool.clone());
    let receipt_uri = format!("/api/booking-groups/{group_id}/receipt");
    let (get_status, _) = http_call(
        &app,
        "GET",
        &receipt_uri,
        Some(&jwt_reader),
        Vec::new(),
        &[],
    )
    .await;
    assert_eq!(
        get_status,
        StatusCode::NOT_FOUND,
        "GET group receipt before issuance must return 404"
    );

    let inv_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM invoices WHERE booking_id = $1")
        .bind(group_bk_id)
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(inv_count, 0, "GET group receipt must never create invoices");

    let (post_status, _) = http_call(
        &app,
        "POST",
        &receipt_uri,
        Some(&jwt_reader),
        Vec::new(),
        &[],
    )
    .await;
    assert_eq!(
        post_status,
        StatusCode::FORBIDDEN,
        "POST group receipt must require payments:create"
    );

    // L-05: gRPC room history RPCs and reject_guest_only reject guest users
    let guest_user_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, guest_id, is_active, is_verified) \
         VALUES ($1, $2, 'x', 'L05 GuestUser', 'guest', $3, true, true) RETURNING id",
    )
    .bind(format!("l05_g_{tag}"))
    .bind(format!("l05g-{tag}@example.test"))
    .bind(guest_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    sqlx::query(
        "INSERT INTO user_roles (user_id, role_id) \
         SELECT $1, id FROM roles WHERE name = 'guest' ON CONFLICT DO NOTHING",
    )
    .bind(guest_user_id)
    .execute(&pool)
    .await
    .unwrap();
    hotel_app_be::core::rbac_cache::clear_all();
    assert!(
        matches!(
            hotel_app_be::core::middleware::reject_guest_only(&pool, guest_user_id).await,
            Err(ApiError::Forbidden(_))
        ),
        "reject_guest_only must reject guest accounts"
    );
    let (jwt_guest, _) = mint_session(
        &pool,
        guest_user_id,
        &format!("l05_g_{tag}"),
        vec!["guest".into()],
    )
    .await;
    let guest_role = format!("l05_unused_{tag}");

    let grpc = RoomGrpc::new(pool.clone());
    let mut r1 = TonicRequest::new(rmpb::ListRoomStatusHistoryRequest {
        name: format!("rooms/{room_id}"),
        page_size: 10,
        page_token: String::new(),
    });
    r1.metadata_mut().insert(
        "authorization",
        format!("Bearer {jwt_guest}").parse().unwrap(),
    );
    let g1 = grpc
        .list_room_status_history(r1)
        .await
        .expect_err("guest gRPC status history must fail");
    assert_eq!(g1.code(), Code::PermissionDenied);

    let mut r2 = TonicRequest::new(rmpb::ListRoomChangeHistoryRequest {
        booking: String::new(),
        guest: String::new(),
        room: format!("rooms/{room_id}"),
        page_size: 10,
        page_token: String::new(),
    });
    r2.metadata_mut().insert(
        "authorization",
        format!("Bearer {jwt_guest}").parse().unwrap(),
    );
    let g2 = grpc
        .list_room_change_history(r2)
        .await
        .expect_err("guest gRPC change history must fail");
    assert_eq!(g2.code(), Code::PermissionDenied);

    // L-09b: Team reactivation checks actor role authority (high-priority role on team cannot be re-enabled by low-priority actor)
    let high_role = format!("l09_high_{tag}");
    let high_role_id: i64 = sqlx::query_scalar(
        "INSERT INTO roles (name, display_name, priority) VALUES ($1, $1, 900) RETURNING id",
    )
    .bind(&high_role)
    .fetch_one(&pool)
    .await
    .unwrap();
    let team_id: i64 = sqlx::query_scalar(
        "INSERT INTO teams (code, name, is_active) VALUES ($1, $2, false) RETURNING id",
    )
    .bind(format!("l09_{}", tag.chars().take(20).collect::<String>()))
    .bind(format!("L09 Team {tag}"))
    .fetch_one(&pool)
    .await
    .unwrap();
    sqlx::query("INSERT INTO team_roles (team_id, role_id) VALUES ($1, $2)")
        .bind(team_id)
        .bind(high_role_id)
        .execute(&pool)
        .await
        .unwrap();

    let reactivate_err = teams_service::update_team(
        &pool,
        reader_id,
        team_id,
        TeamUpdateInput {
            name: None,
            description: None,
            is_active: Some(true),
        },
    )
    .await
    .expect_err("low-privilege actor cannot reactivate team holding higher-priority role");
    assert!(
        matches!(reactivate_err, ApiError::Forbidden(_)),
        "expected Forbidden on team reactivation, got {reactivate_err:?}"
    );

    let _ = sqlx::query("DELETE FROM team_roles WHERE team_id = $1")
        .bind(team_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM teams WHERE id = $1")
        .bind(team_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM roles WHERE id = $1")
        .bind(high_role_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM bookings WHERE id = $1")
        .bind(group_bk_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM booking_groups WHERE id = $1")
        .bind(group_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM rooms WHERE id = $1")
        .bind(room_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM room_types WHERE id = $1")
        .bind(rt_id)
        .execute(&pool)
        .await;
    for uid in [reader_id, guest_user_id] {
        let _ = sqlx::query("DELETE FROM refresh_tokens WHERE user_id = $1")
            .bind(uid)
            .execute(&pool)
            .await;
        let _ = sqlx::query("DELETE FROM user_roles WHERE user_id = $1")
            .bind(uid)
            .execute(&pool)
            .await;
    }
    let _ = sqlx::query(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE name = ANY($1))",
    )
    .bind([&reader_role, &guest_role])
    .execute(&pool)
    .await;
    let _ = sqlx::query("DELETE FROM roles WHERE name = ANY($1)")
        .bind([&reader_role, &guest_role])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = ANY($1)")
        .bind([reader_id, guest_user_id])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM guests WHERE id = $1")
        .bind(guest_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM audit_logs WHERE id = ANY($1)")
        .bind([a1, a2])
        .execute(&pool)
        .await;
    hotel_app_be::core::rbac_cache::clear_all();
}

// ---------------------------------------------------------------------------
// Wave-2 Security Regressions (W2-01, W2-02, W2-05, W2-11, W2-12, W2-14)
// ---------------------------------------------------------------------------

#[tokio::test]
async fn w2_01_guest_portal_verify_requires_email_and_blocks_account_holders() {
    use hotel_app_be::modules::guest_portal::models::GuestPortalVerifyRequest;
    use hotel_app_be::modules::guest_portal::service as guest_portal_service;

    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();
    let today = hotel_app_be::core::db::hotel_today(&pool)
        .await
        .expect("hotel_today");
    let check_in = today + Duration::days(2);
    let check_out = today + Duration::days(4);

    let rt_id: i64 = sqlx::query_scalar(
        "INSERT INTO room_types (code, name, base_price, max_occupancy) VALUES ($1, $2, 100.00, 2) RETURNING id",
    )
    .bind(short_code("W1"))
    .bind(format!("W201 Type {tag}"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let room_id: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'available', true) RETURNING id",
    )
    .bind(short_code("RW1"))
    .bind(rt_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    let room2_id: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'available', true) RETURNING id",
    )
    .bind(short_code("RW2"))
    .bind(rt_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    let guest_email = format!("w2-01-g-{tag}@example.test");
    let guest_name = format!("W201 Guest {tag}");
    let g1: i64 =
        sqlx::query_scalar("INSERT INTO guests (nick_name, email) VALUES ($1, $2) RETURNING id")
            .bind(&guest_name)
            .bind(&guest_email)
            .fetch_one(&pool)
            .await
            .unwrap();
    let bk1_num = format!("BKW201A-{tag}");
    let bk1_id: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, $4, $5, 100.00, 200.00, 200.00, 'confirmed', 'unpaid') RETURNING id",
    )
    .bind(&bk1_num)
    .bind(g1)
    .bind(room_id)
    .bind(check_in)
    .bind(check_out)
    .fetch_one(&pool)
    .await
    .unwrap();

    // Missing email must fail generic 401 when guest has an email on file.
    let missing_email_err = guest_portal_service::verify_guest_booking(
        &pool,
        GuestPortalVerifyRequest {
            booking_number: bk1_num.clone(),
            name: guest_name.clone(),
            email: None,
        },
    )
    .await
    .expect_err("missing email must fail when guest email exists");
    assert!(matches!(missing_email_err, ApiError::Unauthorized(_)));

    // Wrong email must fail generic 401.
    let wrong_email_err = guest_portal_service::verify_guest_booking(
        &pool,
        GuestPortalVerifyRequest {
            booking_number: bk1_num.clone(),
            name: guest_name.clone(),
            email: Some("wrong@example.test".to_string()),
        },
    )
    .await
    .expect_err("wrong email must fail");
    assert!(matches!(wrong_email_err, ApiError::Unauthorized(_)));

    // Matching email (trimmed, case-insensitive) succeeds and issues a token.
    let issued = guest_portal_service::verify_guest_booking(
        &pool,
        GuestPortalVerifyRequest {
            booking_number: bk1_num.clone(),
            name: guest_name.clone(),
            email: Some(format!("  {}  ", guest_email.to_ascii_uppercase())),
        },
    )
    .await
    .expect("matching email must verify");

    // Once the guest holds an active account (legacy users.guest_id), both /verify and /auto-checkin fail 403.
    let u1: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, guest_id, is_active, is_verified) \
         VALUES ($1, $2, 'hash', 'Registered Guest', 'guest', $3, true, true) RETURNING id",
    )
    .bind(format!("w201_u1_{tag}"))
    .bind(&guest_email)
    .bind(g1)
    .fetch_one(&pool)
    .await
    .unwrap();

    let blocked_verify = guest_portal_service::verify_guest_booking(
        &pool,
        GuestPortalVerifyRequest {
            booking_number: bk1_num.clone(),
            name: guest_name.clone(),
            email: Some(guest_email.clone()),
        },
    )
    .await
    .expect_err("registered guest must be refused by verify_guest_booking");
    assert!(matches!(blocked_verify, ApiError::Forbidden(_)));

    let blocked_auto = guest_portal_service::auto_checkin_by_token(&pool, &issued.token)
        .await
        .expect_err("registered guest must be refused by auto_checkin_by_token");
    assert!(matches!(blocked_auto, ApiError::Forbidden(_)));

    // Multi-guest user_guests link also blocks anonymous portal verification.
    let g2_email = format!("w2-01-g2-{tag}@example.test");
    let g2_name = format!("W201 Guest2 {tag}");
    let g2: i64 =
        sqlx::query_scalar("INSERT INTO guests (nick_name, email) VALUES ($1, $2) RETURNING id")
            .bind(&g2_name)
            .bind(&g2_email)
            .fetch_one(&pool)
            .await
            .unwrap();
    let u2: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'hash', 'Linked Multi Guest', 'guest', true, true) RETURNING id",
    )
    .bind(format!("w201_u2_{tag}"))
    .bind(format!("w201-u2-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();
    sqlx::query("INSERT INTO user_guests (user_id, guest_id) VALUES ($1, $2)")
        .bind(u2)
        .bind(g2)
        .execute(&pool)
        .await
        .unwrap();
    let bk2_num = format!("BKW201B-{tag}");
    let bk2_id: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, payment_status) \
         VALUES ($1, $2, $3, $4, $5, 100.00, 200.00, 200.00, 'confirmed', 'unpaid') RETURNING id",
    )
    .bind(&bk2_num)
    .bind(g2)
    .bind(room2_id)
    .bind(check_in)
    .bind(check_out)
    .fetch_one(&pool)
    .await
    .unwrap();

    let blocked_multi = guest_portal_service::verify_guest_booking(
        &pool,
        GuestPortalVerifyRequest {
            booking_number: bk2_num,
            name: g2_name,
            email: Some(g2_email),
        },
    )
    .await
    .expect_err("user_guests-linked guest must be refused");
    assert!(matches!(blocked_multi, ApiError::Forbidden(_)));

    let _ = sqlx::query("DELETE FROM user_guests WHERE user_id = $1")
        .bind(u2)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = ANY($1)")
        .bind([u1, u2])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM bookings WHERE id = ANY($1)")
        .bind([bk1_id, bk2_id])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM rooms WHERE id = ANY($1)")
        .bind([room_id, room2_id])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM room_types WHERE id = $1")
        .bind(rt_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM guests WHERE id = ANY($1)")
        .bind([g1, g2])
        .execute(&pool)
        .await;
}

#[tokio::test]
async fn w2_02_update_complimentary_blocks_checked_in_and_cancelled_bookings() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();
    let staff_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'hash', 'W202 Staff', 'staff', true, true) RETURNING id",
    )
    .bind(format!("w202_staff_{tag}"))
    .bind(format!("w202-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let role_name = format!("w202_role_{tag}");
    grant_role_with_permissions(&pool, staff_id, &role_name, &["bookings:update"]).await;
    let (jwt_staff, _) = mint_session(
        &pool,
        staff_id,
        &format!("w202_staff_{tag}"),
        vec!["staff".into()],
    )
    .await;

    let guest_id: i64 =
        sqlx::query_scalar("INSERT INTO guests (nick_name, email) VALUES ($1, $2) RETURNING id")
            .bind(format!("W202 {tag}"))
            .bind(format!("w202-g-{tag}@example.test"))
            .fetch_one(&pool)
            .await
            .unwrap();
    let rt_id: i64 = sqlx::query_scalar(
        "INSERT INTO room_types (code, name, base_price, max_occupancy) VALUES ($1, $2, 100.00, 2) RETURNING id",
    )
    .bind(short_code("W2"))
    .bind(format!("W202 Type {tag}"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let r1_id: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'occupied', true) RETURNING id",
    )
    .bind(short_code("W2A"))
    .bind(rt_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    let r2_id: i64 = sqlx::query_scalar(
        "INSERT INTO rooms (room_number, room_type_id, status, is_active) VALUES ($1, $2, 'available', true) RETURNING id",
    )
    .bind(short_code("W2B"))
    .bind(rt_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    let bk_checked_in: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, is_complimentary, complimentary_reason, \
             complimentary_nights, payment_status) \
         VALUES ($1, $2, $3, '2031-10-01', '2031-10-04', 100.00, 200.00, 200.00, 'checked_in', true, 'Orig', 1, 'unpaid') RETURNING id",
    )
    .bind(format!("BKW202CI-{tag}"))
    .bind(guest_id)
    .bind(r1_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    let bk_cancelled: i64 = sqlx::query_scalar(
        "INSERT INTO bookings (booking_number, guest_id, room_id, check_in_date, check_out_date, \
             room_rate, subtotal, total_amount, status, is_complimentary, complimentary_reason, \
             complimentary_nights, payment_status) \
         VALUES ($1, $2, $3, '2031-10-01', '2031-10-04', 100.00, 200.00, 200.00, 'voided', true, 'Orig', 1, 'unpaid') RETURNING id",
    )
    .bind(format!("BKW202CA-{tag}"))
    .bind(guest_id)
    .bind(r2_id)
    .fetch_one(&pool)
    .await
    .unwrap();

    let app = routes::create_router(pool.clone());

    for (bk_id, payload) in [
        (
            bk_checked_in,
            json!({
                "complimentary_start_date": "2031-10-01",
                "complimentary_end_date": "2031-10-03",
                "complimentary_reason": "Tampered dates"
            }),
        ),
        (
            bk_checked_in,
            json!({ "complimentary_reason": "Tampered reason" }),
        ),
        (
            bk_cancelled,
            json!({ "complimentary_reason": "Cancelled mutation" }),
        ),
    ] {
        let (status, _) = http_call(
            &app,
            "PATCH",
            &format!("/api/bookings/{bk_id}/complimentary"),
            Some(&jwt_staff),
            payload.to_string().into_bytes(),
            &[("content-type", "application/json")],
        )
        .await;
        assert_eq!(
            status,
            StatusCode::BAD_REQUEST,
            "checked_in / cancelled complimentary updates must return 400"
        );
    }

    let persisted_reason: Option<String> =
        sqlx::query_scalar("SELECT complimentary_reason FROM bookings WHERE id = $1")
            .bind(bk_checked_in)
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(persisted_reason.as_deref(), Some("Orig"));

    let _ = sqlx::query("DELETE FROM bookings WHERE id = ANY($1)")
        .bind([bk_checked_in, bk_cancelled])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM rooms WHERE id = ANY($1)")
        .bind([r1_id, r2_id])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM room_types WHERE id = $1")
        .bind(rt_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM guests WHERE id = $1")
        .bind(guest_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM refresh_tokens WHERE user_id = $1")
        .bind(staff_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM user_roles WHERE user_id = $1")
        .bind(staff_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query(
        "DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE name = $1)",
    )
    .bind(&role_name)
    .execute(&pool)
    .await;
    let _ = sqlx::query("DELETE FROM roles WHERE name = $1")
        .bind(&role_name)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = $1")
        .bind(staff_id)
        .execute(&pool)
        .await;
    hotel_app_be::core::rbac_cache::clear_all();
}

#[tokio::test]
async fn w2_05_desktop_local_guard_blocks_rebinding_and_enforces_token() {
    let app = Router::new()
        .route("/health", axum::routing::get(|| async { "ok" }))
        .route("/uploads/test.png", axum::routing::get(|| async { "img" }))
        .route(
            "/api/private",
            axum::routing::get(|| async { "secret" }).options(|| async { "ok" }),
        )
        .layer(axum::middleware::from_fn(|req, next| {
            routes::enforce_desktop_local_guard_with(true, Some("desktop-secret-123"), req, next)
        }));

    let evil_req = Request::builder()
        .method("GET")
        .uri("/api/private")
        .header(header::HOST, "evil.example:3030")
        .header("x-desktop-client-token", "desktop-secret-123")
        .body(Body::empty())
        .unwrap();
    assert_eq!(
        app.clone().oneshot(evil_req).await.unwrap().status(),
        StatusCode::FORBIDDEN
    );

    let evil_health = Request::builder()
        .method("GET")
        .uri("/health")
        .header(header::HOST, "evil.example:3030")
        .body(Body::empty())
        .unwrap();
    assert_eq!(
        app.clone().oneshot(evil_health).await.unwrap().status(),
        StatusCode::FORBIDDEN
    );

    let missing_token = Request::builder()
        .method("GET")
        .uri("/api/private")
        .header(header::HOST, "127.0.0.1:3030")
        .body(Body::empty())
        .unwrap();
    assert_eq!(
        app.clone().oneshot(missing_token).await.unwrap().status(),
        StatusCode::UNAUTHORIZED
    );

    let wrong_token = Request::builder()
        .method("GET")
        .uri("/api/private")
        .header(header::HOST, "localhost:3030")
        .header("x-desktop-client-token", "wrong-secret")
        .body(Body::empty())
        .unwrap();
    assert_eq!(
        app.clone().oneshot(wrong_token).await.unwrap().status(),
        StatusCode::UNAUTHORIZED
    );

    let valid_req = Request::builder()
        .method("GET")
        .uri("/api/private")
        .header(header::HOST, "[::1]:3030")
        .header("x-desktop-client-token", "desktop-secret-123")
        .body(Body::empty())
        .unwrap();
    assert_eq!(
        app.clone().oneshot(valid_req).await.unwrap().status(),
        StatusCode::OK
    );

    let options_req = Request::builder()
        .method("OPTIONS")
        .uri("/api/private")
        .header(header::HOST, "evil.example")
        .body(Body::empty())
        .unwrap();
    assert_eq!(
        app.clone().oneshot(options_req).await.unwrap().status(),
        StatusCode::OK
    );

    let ok_health = Request::builder()
        .method("GET")
        .uri("/health")
        .header(header::HOST, "localhost:3030")
        .body(Body::empty())
        .unwrap();
    assert_eq!(
        app.clone().oneshot(ok_health).await.unwrap().status(),
        StatusCode::OK
    );

    let ok_uploads = Request::builder()
        .method("GET")
        .uri("/uploads/test.png")
        .header(header::HOST, "127.0.0.1:3030")
        .body(Body::empty())
        .unwrap();
    assert_eq!(
        app.clone().oneshot(ok_uploads).await.unwrap().status(),
        StatusCode::OK
    );

    let ok_ws = Request::builder()
        .method("GET")
        .uri("/api/private")
        .header(header::HOST, "127.0.0.1:3030")
        .header(header::UPGRADE, "WebSocket")
        .body(Body::empty())
        .unwrap();
    assert_eq!(app.oneshot(ok_ws).await.unwrap().status(), StatusCode::OK);
}

#[tokio::test]
async fn w2_11_guest_only_blocked_from_notifications_search_and_realtime() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();
    let guest_user_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'hash', 'W211 Guest Only', 'guest', true, true) RETURNING id",
    )
    .bind(format!("w211_guest_{tag}"))
    .bind(format!("w211-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();
    sqlx::query(
        "INSERT INTO user_roles (user_id, role_id) \
         SELECT $1, id FROM roles WHERE name = 'guest' \
         ON CONFLICT DO NOTHING",
    )
    .bind(guest_user_id)
    .execute(&pool)
    .await
    .unwrap();
    hotel_app_be::core::rbac_cache::clear_all();
    let (jwt_guest, _) = mint_session(
        &pool,
        guest_user_id,
        &format!("w211_guest_{tag}"),
        vec!["guest".into()],
    )
    .await;

    let app = routes::create_router(pool.clone());

    for (method, path) in [
        ("GET", "/api/system/notifications"),
        ("POST", "/api/system/notifications/1/read"),
        ("POST", "/api/system/notifications/read-all"),
        ("GET", "/api/search?q=room"),
    ] {
        let (status, _) = http_call(&app, method, path, Some(&jwt_guest), Vec::new(), &[]).await;
        assert_eq!(
            status,
            StatusCode::FORBIDDEN,
            "guest-only user must receive 403 on {method} {path}"
        );
    }

    {
        use tokio::io::{AsyncReadExt, AsyncWriteExt};

        let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
            .await
            .expect("bind loopback listener for ws check");
        let addr = listener.local_addr().unwrap();
        let ws_app = app.clone();
        let server_task = tokio::spawn(async move {
            let _ = axum::serve(
                listener,
                ws_app.into_make_service_with_connect_info::<std::net::SocketAddr>(),
            )
            .await;
        });

        let mut stream = tokio::net::TcpStream::connect(addr)
            .await
            .expect("connect ws test listener");
        let raw_req = format!(
            "GET /api/updates/socket HTTP/1.1\r\n\
             Host: {addr}\r\n\
             Connection: Upgrade\r\n\
             Upgrade: websocket\r\n\
             Sec-WebSocket-Key: AAAAAAAAAAAAAAAAAAAAAA==\r\n\
             Sec-WebSocket-Version: 13\r\n\
             Sec-WebSocket-Protocol: hotel-updates, {jwt_guest}\r\n\r\n"
        );
        stream.write_all(raw_req.as_bytes()).await.unwrap();
        let mut buf = [0_u8; 512];
        let n = stream.read(&mut buf).await.unwrap();
        let response_head = String::from_utf8_lossy(&buf[..n]);
        let status_line = response_head.lines().next().unwrap_or("");
        assert!(
            status_line.starts_with("HTTP/1.1 403"),
            "guest-only user must receive 403 on /api/updates/socket, got: {status_line}"
        );
        server_task.abort();
    }

    let _ = sqlx::query("DELETE FROM refresh_tokens WHERE user_id = $1")
        .bind(guest_user_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM user_roles WHERE user_id = $1")
        .bind(guest_user_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = $1")
        .bind(guest_user_id)
        .execute(&pool)
        .await;
    hotel_app_be::core::rbac_cache::clear_all();
}

#[tokio::test]
async fn w2_12_data_transfer_owner_isolation_and_export_cache_headers() {
    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();
    let user_a: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified, is_super_admin) \
         VALUES ($1, $2, 'hash', 'W212 Staff A', 'staff', true, true, false) RETURNING id",
    )
    .bind(format!("w212_a_{tag}"))
    .bind(format!("w212-a-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();
    let user_b: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified, is_super_admin) \
         VALUES ($1, $2, 'hash', 'W212 Staff B', 'staff', true, true, false) RETURNING id",
    )
    .bind(format!("w212_b_{tag}"))
    .bind(format!("w212-b-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();

    let role_name = format!("w212_role_{tag}");
    let perms = [
        "data_transfer:import",
        "data_transfer:export",
        "audit:export",
    ];
    grant_role_with_permissions(&pool, user_a, &role_name, &perms).await;
    grant_role_with_permissions(&pool, user_b, &role_name, &perms).await;
    let (jwt_a, _) = mint_session(
        &pool,
        user_a,
        &format!("w212_a_{tag}"),
        vec!["staff".into()],
    )
    .await;
    let (jwt_b, _) = mint_session(
        &pool,
        user_b,
        &format!("w212_b_{tag}"),
        vec!["staff".into()],
    )
    .await;

    let doc = serde_json::to_vec(&json!({
        "format": "hotel-backup",
        "version": 1,
        "kind": "business-data",
        "exportId": uuid::Uuid::new_v4().to_string(),
        "exportedAt": "2026-10-01T00:00:00Z",
        "applicationVersion": "0.3.0",
        "source": { "environment": "development", "databaseProvider": "postgresql" },
        "manifest": { "entities": [{ "name": "public.amenities", "primaryKey": ["id"], "columns": [] }], "exclusions": [] },
        "tables": { "public.amenities": [] },
        "integrity": { "entities": 1, "rows": 0, "entityRows": { "public.amenities": 0 }, "completedAt": "2026-10-01T00:00:01Z" }
    }))
    .unwrap();

    let app = routes::create_router(pool.clone());
    let (up_status, up_body) = http_call(
        &app,
        "POST",
        "/api/data-transfer/import/uploads",
        Some(&jwt_a),
        doc,
        &[("content-type", "application/octet-stream")],
    )
    .await;
    assert_eq!(up_status, StatusCode::OK);
    let up_json: Value = serde_json::from_slice(&up_body).unwrap();
    let upload_id = up_json["uploadId"].as_str().unwrap();

    let (b_prev, _) = http_call(
        &app,
        "POST",
        "/api/data-transfer/import/preview",
        Some(&jwt_b),
        json!({ "uploadId": upload_id }).to_string().into_bytes(),
        &[],
    )
    .await;
    assert_eq!(
        b_prev,
        StatusCode::FORBIDDEN,
        "staff B must not preview staff A's upload"
    );

    let (b_exec, _) = http_call(
        &app,
        "POST",
        "/api/data-transfer/import/execute",
        Some(&jwt_b),
        json!({ "uploadId": upload_id, "mode": "merge", "onConflict": "skip", "confirm": true })
            .to_string()
            .into_bytes(),
        &[],
    )
    .await;
    assert_eq!(
        b_exec,
        StatusCode::FORBIDDEN,
        "staff B must not execute staff A's upload"
    );

    let (b_del, _) = http_call(
        &app,
        "DELETE",
        &format!("/api/data-transfer/import/uploads/{upload_id}"),
        Some(&jwt_b),
        Vec::new(),
        &[],
    )
    .await;
    assert_eq!(
        b_del,
        StatusCode::FORBIDDEN,
        "staff B must not delete staff A's upload"
    );

    let (a_exec, a_exec_body) = http_call(
        &app,
        "POST",
        "/api/data-transfer/import/execute",
        Some(&jwt_a),
        json!({ "uploadId": upload_id, "mode": "merge", "onConflict": "skip", "confirm": true })
            .to_string()
            .into_bytes(),
        &[],
    )
    .await;
    assert_eq!(a_exec, StatusCode::ACCEPTED);
    let job_json: Value = serde_json::from_slice(&a_exec_body).unwrap();
    let job_id = job_json["jobId"].as_str().unwrap();

    let (b_job, _) = http_call(
        &app,
        "GET",
        &format!("/api/data-transfer/import/jobs/{job_id}"),
        Some(&jwt_b),
        Vec::new(),
        &[],
    )
    .await;
    assert_eq!(
        b_job,
        StatusCode::FORBIDDEN,
        "staff B must not read staff A's import job"
    );

    // Export endpoints must disable proxy/browser caching.
    for path in [
        "/api/data-transfer/export?scope=standard",
        "/api/audit-logs/export/csv",
        "/api/audit-logs/export/json",
    ] {
        let mut req = Request::builder()
            .method("GET")
            .uri(path)
            .header(header::AUTHORIZATION, format!("Bearer {jwt_a}"))
            .body(Body::empty())
            .unwrap();
        req.extensions_mut().insert(ConnectInfo(PEER_ADDR));
        let resp = app.clone().oneshot(req).await.unwrap();
        assert_eq!(
            resp.status(),
            StatusCode::OK,
            "export route {path} must succeed"
        );
        assert_eq!(
            resp.headers()
                .get(header::CACHE_CONTROL)
                .and_then(|v| v.to_str().ok()),
            Some("private, no-store"),
            "missing Cache-Control: private, no-store on {path}"
        );
        assert_eq!(
            resp.headers()
                .get(header::PRAGMA)
                .and_then(|v| v.to_str().ok()),
            Some("no-cache"),
            "missing Pragma: no-cache on {path}"
        );
    }

    let _ = sqlx::query("DELETE FROM refresh_tokens WHERE user_id = ANY($1)")
        .bind([user_a, user_b])
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM user_roles WHERE user_id = ANY($1)")
        .bind([user_a, user_b])
        .execute(&pool)
        .await;
    let _ = sqlx::query(
        "DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE name = $1)",
    )
    .bind(&role_name)
    .execute(&pool)
    .await;
    let _ = sqlx::query("DELETE FROM roles WHERE name = $1")
        .bind(&role_name)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = ANY($1)")
        .bind([user_a, user_b])
        .execute(&pool)
        .await;
    hotel_app_be::core::rbac_cache::clear_all();
}

#[tokio::test]
async fn w2_14_refresh_token_reuse_revokes_session_and_logs_audit() {
    use hotel_app_be::modules::auth::models::RefreshTokenRequest;

    let Some((pool, _guard)) = setup_pg().await else {
        return;
    };
    let tag = uniq_tag();
    let user_id: i64 = sqlx::query_scalar(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, is_active, is_verified) \
         VALUES ($1, $2, 'hash', 'W214 User', 'staff', true, true) RETURNING id",
    )
    .bind(format!("w214_{tag}"))
    .bind(format!("w214-{tag}@example.test"))
    .fetch_one(&pool)
    .await
    .unwrap();

    let t0 = AuthService::generate_refresh_token();
    let sid = AuthService::store_refresh_token(
        &pool,
        user_id,
        &t0,
        30,
        Some("127.0.0.1"),
        Some("w2-14-test"),
        None,
    )
    .await
    .expect("store initial refresh token");

    let rotated = auth_service::refresh_token(
        &pool,
        RefreshTokenRequest {
            refresh_token: t0.clone(),
        },
    )
    .await
    .expect("first refresh must rotate token");
    let t1 = rotated.refresh_token;
    assert_ne!(t1, t0);

    // Replaying t0 must fail and immediately revoke the entire session.
    let replay_err = auth_service::refresh_token(&pool, RefreshTokenRequest { refresh_token: t0 })
        .await
        .expect_err("reused prior refresh token must be rejected");
    assert!(matches!(replay_err, ApiError::Unauthorized(_)));

    // Subsequent use of t1 must now also fail because the session is revoked.
    let sibling_err = auth_service::refresh_token(&pool, RefreshTokenRequest { refresh_token: t1 })
        .await
        .expect_err("legitimate sibling refresh token must fail after reuse detection");
    assert!(matches!(sibling_err, ApiError::Unauthorized(_)));

    let is_revoked: bool =
        sqlx::query_scalar("SELECT is_revoked FROM refresh_tokens WHERE id = $1::uuid")
            .bind(&sid)
            .fetch_one(&pool)
            .await
            .unwrap();
    assert!(is_revoked, "session row must be revoked after reuse");

    let audit_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE user_id = $1 AND action = 'refresh_token_reused'",
    )
    .bind(user_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert!(
        audit_count >= 1,
        "refresh_token_reused audit row must be recorded"
    );

    let _ = sqlx::query("DELETE FROM refresh_tokens WHERE user_id = $1")
        .bind(user_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM audit_logs WHERE user_id = $1")
        .bind(user_id)
        .execute(&pool)
        .await;
    let _ = sqlx::query("DELETE FROM users WHERE id = $1")
        .bind(user_id)
        .execute(&pool)
        .await;
}
