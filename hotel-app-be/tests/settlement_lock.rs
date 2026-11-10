//! Settlement lock for online bookings (patch 0023,
//! `modules/payments/settlement_lock.rs`): an OTA / direct-website booking's
//! room charge is only recorded under its channel's tender, deposits and
//! extras stay open, and a locked booking's deposit is refunded to its
//! original tender.
//!
//! Requires `DATABASE_URL` (PostgreSQL); skips gracefully without it.
//! Fixture IDs live in the 971_0xx block (grepped: unused elsewhere).

use hotel_app_be::core::error::ApiError;
use hotel_app_be::models::{RecordPaymentRequest, UpdatePaymentRequest};
use hotel_app_be::modules::bookings::queries::{GET_BOOKING_BY_ID_QUERY, GET_BOOKINGS_BASE_QUERY};
use hotel_app_be::modules::bookings::repository::BookingRepository;
use hotel_app_be::modules::bookings::{
    OnlineCheckinAutopost, record_online_checkin_payment_tx, split_online_checkin_autopost,
};
use hotel_app_be::modules::payments::service as payments;
use rust_decimal::Decimal;
use sqlx::{PgPool, Row, postgres::PgPoolOptions};
use std::str::FromStr;

const ACTOR: i64 = 971_001;
const ROOM_TYPE: i64 = 971_002;
const ROOM: i64 = 971_003;
const GUEST: i64 = 971_004;

fn pg_serial_lock() -> std::sync::Arc<tokio::sync::Mutex<()>> {
    static LOCK: std::sync::OnceLock<std::sync::Arc<tokio::sync::Mutex<()>>> =
        std::sync::OnceLock::new();
    LOCK.get_or_init(|| std::sync::Arc::new(tokio::sync::Mutex::new(())))
        .clone()
}

async fn setup() -> Option<(PgPool, tokio::sync::OwnedMutexGuard<()>)> {
    let Ok(database_url) = std::env::var("DATABASE_URL") else {
        eprintln!("Skipping settlement-lock test because DATABASE_URL is not set");
        return None;
    };
    let guard = pg_serial_lock().lock_owned().await;
    let pool = PgPoolOptions::new()
        .max_connections(5)
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
    seed_shared(&pool).await;
    Some((pool, guard))
}

async fn exec(pool: &PgPool, sql: &str) {
    sqlx::query(sqlx::AssertSqlSafe(sql))
        .execute(pool)
        .await
        .unwrap();
}

async fn seed_shared(pool: &PgPool) {
    exec(pool, &format!(
        "INSERT INTO users (id, username, email, full_name, user_type, is_active, is_verified) \
         OVERRIDING SYSTEM VALUE VALUES ({ACTOR}, 'settle_lock_actor', 'settle-lock@hotel.local', 'Settle Lock', 'staff', true, true) \
         ON CONFLICT (id) DO NOTHING"
    )).await;
    exec(pool, &format!(
        "INSERT INTO room_types (id, code, name, base_price, max_occupancy, keycard_deposit_amount, service_charge_percentage) \
         OVERRIDING SYSTEM VALUE VALUES ({ROOM_TYPE}, 'SLRT', 'Settle Lock RT', 100, 2, 0, 0) ON CONFLICT (id) DO NOTHING"
    )).await;
    exec(pool, &format!(
        "INSERT INTO rooms (id, room_number, room_type_id, status) \
         OVERRIDING SYSTEM VALUE VALUES ({ROOM}, 'SL971', {ROOM_TYPE}, 'available') ON CONFLICT (id) DO NOTHING"
    )).await;
    exec(pool, &format!(
        "INSERT INTO guests (id, nick_name, first_name, last_name, email, tourism_type) \
         OVERRIDING SYSTEM VALUE VALUES ({GUEST}, 'Settle Lock Guest', 'Settle', 'Lock', 'settle-lock-guest@hotel.local', 'local') \
         ON CONFLICT (id) DO NOTHING"
    )).await;
}

/// Seed booking `id` with `source` and the channel named `channel` (or none).
async fn seed_booking(
    pool: &PgPool,
    id: i64,
    source: &str,
    channel: Option<&str>,
    payment_method: Option<&str>,
) {
    exec(pool, &format!("DELETE FROM bookings WHERE id = {id}")).await;
    sqlx::query(
        "INSERT INTO bookings (
            id, booking_number, guest_id, guest_name, guest_email, room_id,
            check_in_date, check_out_date, adults, children,
            room_rate, subtotal, total_amount, status, payment_status, created_by,
            tourism_tax_amount, extra_bed_charge, source, booking_channel_id, payment_method
         )
         OVERRIDING SYSTEM VALUE VALUES ($1, $2, $3, 'Settle Lock Guest', 'settle-lock-guest@hotel.local', $4,
                 DATE '2031-01-01' + ($1 - 971000)::int, DATE '2031-01-02' + ($1 - 971000)::int, 1, 0, 100, 100, 100, 'confirmed', 'unpaid', $5, 0, 0,
                 $6, (SELECT id FROM booking_channels WHERE name = $7), $8)",
    )
    .bind(id)
    .bind(format!("BK-SL-{id}"))
    .bind(GUEST)
    .bind(ROOM)
    .bind(ACTOR)
    .bind(source)
    .bind(channel)
    .bind(payment_method)
    .execute(pool)
    .await
    .unwrap();
}

async fn cleanup(pool: &PgPool, ids: &[i64]) {
    for id in ids {
        exec(pool, &format!("DELETE FROM audit_logs WHERE resource_type = 'payment' AND (details->>'booking_id')::bigint = {id}")).await;
        exec(pool, &format!("DELETE FROM bookings WHERE id = {id}")).await;
    }
}

fn pay(
    booking_id: i64,
    method: &str,
    payment_type: &str,
    amount: f64,
    key: &str,
) -> RecordPaymentRequest {
    RecordPaymentRequest {
        booking_id,
        amount,
        payment_method: method.to_string(),
        payment_type: Some(payment_type.to_string()),
        transaction_reference: None,
        notes: None,
        payment_date: None,
        idempotency_key: format!("settle-lock-{booking_id}-{key}"),
    }
}

fn assert_bad_request(result: Result<serde_json::Value, ApiError>, needle: &str) {
    match result {
        Err(ApiError::BadRequest(msg)) => {
            assert!(msg.contains(needle), "unexpected message: {msg}")
        }
        other => panic!("expected BadRequest containing {needle:?}, got {other:?}"),
    }
}

#[tokio::test]
async fn ota_room_charge_only_accepts_channel_method() {
    let Some((pool, _guard)) = setup().await else {
        return;
    };
    let id = 971_010;
    seed_booking(&pool, id, "online", Some("Booking.com"), Some("Cash")).await;

    assert_bad_request(
        payments::record_payment(&pool, ACTOR, pay(id, "Cash", "booking", 50.0, "cash")).await,
        "settled via Booking.com",
    );
    assert_bad_request(
        payments::record_payment(&pool, ACTOR, pay(id, "Agoda.com", "booking", 50.0, "agoda"))
            .await,
        "Booking.com",
    );
    assert_bad_request(
        payments::record_payment(&pool, ACTOR, pay(id, "Visa Card", "booking", 50.0, "visa")).await,
        "Booking.com",
    );

    // Legacy spelling still matches.
    payments::record_payment(&pool, ACTOR, pay(id, "Booking.Com", "booking", 50.0, "bc"))
        .await
        .expect("channel method allowed");
    // Cash security deposit and folio extras stay open.
    payments::record_payment(&pool, ACTOR, pay(id, "Cash", "deposit", 50.0, "dep"))
        .await
        .expect("cash deposit allowed");
    payments::record_payment(&pool, ACTOR, pay(id, "Cash", "service", 10.0, "svc"))
        .await
        .expect("cash extra allowed");

    // Deposit refund must go back to the deposit's tender.
    let refund = |method: &str| serde_json::json!({"payment_method": method, "amount": 50.0, "note": "Normal deposit refund"});
    match payments::refund_deposit(&pool, ACTOR, id, refund("Booking.com")).await {
        Err(ApiError::BadRequest(msg)) => assert!(msg.contains("original payment method"), "{msg}"),
        other => panic!("refund to another method must be refused, got {other:?}"),
    }
    payments::refund_deposit(&pool, ACTOR, id, refund("cash"))
        .await
        .expect("refund to original tender");

    cleanup(&pool, &[id]).await;
}

#[tokio::test]
async fn pending_room_payment_cannot_be_retendered() {
    let Some((pool, _guard)) = setup().await else {
        return;
    };
    let id = 971_020;
    seed_booking(&pool, id, "online", Some("Agoda"), None).await;
    let payment_id: i64 = sqlx::query_scalar(
        "INSERT INTO payments (uuid, booking_id, amount, payment_method, payment_type, status, created_by) \
         VALUES (gen_uuidv7(), $1, 100, 'Agoda.com', 'booking', 'pending', $2) RETURNING id",
    )
    .bind(id)
    .bind(ACTOR)
    .fetch_one(&pool)
    .await
    .unwrap();
    let update = |method: &str| UpdatePaymentRequest {
        amount: None,
        payment_method: Some(method.to_string()),
        transaction_reference: None,
        notes: None,
        payment_date: None,
    };
    assert_bad_request(
        payments::update_payment(&pool, ACTOR, payment_id, update("Cash")).await,
        "Agoda.com",
    );
    payments::update_payment(&pool, ACTOR, payment_id, update("Agoda.Com"))
        .await
        .expect("same tender, other spelling");
    cleanup(&pool, &[id]).await;
}

#[tokio::test]
async fn website_accepts_bank_transfer_and_paypal_only() {
    let Some((pool, _guard)) = setup().await else {
        return;
    };
    let id = 971_030;
    seed_booking(&pool, id, "website", Some("Direct Website"), None).await;
    assert_bad_request(
        payments::record_payment(&pool, ACTOR, pay(id, "Cash", "booking", 40.0, "cash")).await,
        "Bank Transfer or PayPal",
    );
    payments::record_payment(
        &pool,
        ACTOR,
        pay(id, "bank_transfer", "booking", 40.0, "bt"),
    )
    .await
    .expect("bank transfer allowed");
    payments::record_payment(&pool, ACTOR, pay(id, "Paypal", "booking", 40.0, "pp"))
        .await
        .expect("paypal allowed");
    cleanup(&pool, &[id]).await;
}

#[tokio::test]
async fn online_booking_without_channel_is_refused_and_walk_in_is_open() {
    let Some((pool, _guard)) = setup().await else {
        return;
    };
    let (online, walk_in) = (971_040, 971_041);
    seed_booking(&pool, online, "online", None, None).await;
    seed_booking(&pool, walk_in, "walk_in", None, None).await;
    assert_bad_request(
        payments::record_payment(
            &pool,
            ACTOR,
            pay(online, "Booking.com", "booking", 40.0, "x"),
        )
        .await,
        "no booking channel",
    );
    payments::record_payment(&pool, ACTOR, pay(online, "Cash", "deposit", 50.0, "dep"))
        .await
        .expect("deposit still allowed");
    payments::record_payment(&pool, ACTOR, pay(walk_in, "Cash", "booking", 40.0, "cash"))
        .await
        .expect("walk-in unlocked");
    cleanup(&pool, &[online, walk_in]).await;
}

#[tokio::test]
async fn online_checkin_auto_post_uses_channel_method() {
    let Some((pool, _guard)) = setup().await else {
        return;
    };
    let id = 971_050;
    // The booking row carries a desk tender ("Cash"); the auto-posted room
    // charge must still be recorded under the channel's method.
    seed_booking(&pool, id, "online", Some("Traveloka"), Some("Cash")).await;
    let booking = BookingRepository::find_by_id(&pool, id)
        .await
        .unwrap()
        .unwrap();
    let mut tx = pool.begin().await.unwrap();
    assert!(
        record_online_checkin_payment_tx(&mut tx, &booking, ACTOR)
            .await
            .unwrap()
    );
    tx.commit().await.unwrap();
    let (method, amount): (String, Decimal) = sqlx::query_as(
        "SELECT payment_method, amount FROM payments WHERE booking_id = $1 AND payment_type = 'booking'",
    )
    .bind(id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(method, "Traveloka");
    assert_eq!(amount, Decimal::from_str("100").unwrap());

    // Without a channel the auto-post is refused while money is owed.
    let missing = 971_051;
    seed_booking(&pool, missing, "online", None, Some("Cash")).await;
    let booking = BookingRepository::find_by_id(&pool, missing)
        .await
        .unwrap()
        .unwrap();
    let mut tx = pool.begin().await.unwrap();
    let err = record_online_checkin_payment_tx(&mut tx, &booking, ACTOR)
        .await
        .unwrap_err();
    assert!(
        matches!(err, ApiError::BadRequest(ref m) if m.contains("no booking channel")),
        "{err:?}"
    );
    drop(tx);
    cleanup(&pool, &[id, missing]).await;
}

async fn payout_flags(pool: &PgPool, id: i64) -> (bool, bool) {
    let by_id: bool = sqlx::query(GET_BOOKING_BY_ID_QUERY)
        .bind(id)
        .fetch_one(pool)
        .await
        .unwrap()
        .get("channel_payout_recorded");
    let list_sql = format!("{GET_BOOKINGS_BASE_QUERY} WHERE b.id = $1");
    let listed: bool = sqlx::query(sqlx::AssertSqlSafe(list_sql))
        .bind(id)
        .fetch_one(pool)
        .await
        .unwrap()
        .get("channel_payout_recorded");
    (by_id, listed)
}

/// A guest paid the OTA for one room type and upgraded at the front desk:
/// after the channel payout is recorded, the desk collects the difference with
/// its own tenders, capped at the outstanding balance.
#[tokio::test]
async fn desk_collects_upgrade_difference_after_channel_payout() {
    let Some((pool, _guard)) = setup().await else {
        return;
    };
    let id = 971_060;
    cleanup(&pool, &[id]).await;
    seed_booking(&pool, id, "online", Some("Agoda"), Some("Agoda.com")).await;

    // Before the payout the lock is unchanged: no desk tender for the room.
    assert_bad_request(
        payments::record_payment(&pool, ACTOR, pay(id, "Cash", "booking", 20.0, "early")).await,
        "settled via Agoda",
    );
    assert_eq!(payout_flags(&pool, id).await, (false, false));

    // Agoda pays the original room in full.
    payments::record_payment(&pool, ACTOR, pay(id, "Agoda.Com", "booking", 100.0, "ota"))
        .await
        .expect("channel payout");
    assert_eq!(payout_flags(&pool, id).await, (true, true));

    // Upgrade at the desk raises the total to 130.
    exec(
        &pool,
        &format!("UPDATE bookings SET total_amount = 130, subtotal = 130, room_rate = 130 WHERE id = {id}"),
    )
    .await;

    assert_bad_request(
        payments::record_payment(&pool, ACTOR, pay(id, "Cash", "booking", 40.0, "over")).await,
        "the desk can only collect the remaining balance of 30",
    );
    assert_bad_request(
        payments::record_payment(&pool, ACTOR, pay(id, "Booking.com", "booking", 30.0, "bc")).await,
        "not as Booking.com",
    );
    payments::record_payment(&pool, ACTOR, pay(id, "Cash", "booking", 10.0, "cash"))
        .await
        .expect("cash remainder allowed");
    payments::record_payment(&pool, ACTOR, pay(id, "Visa Card", "booking", 20.0, "card"))
        .await
        .expect("card remainder allowed");
    // Fully settled: nothing more at the desk.
    assert_bad_request(
        payments::record_payment(&pool, ACTOR, pay(id, "Cash", "booking", 1.0, "extra")).await,
        "remaining balance of 0",
    );

    let audited: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE action = 'payment_recorded' \
         AND (details->>'booking_id')::bigint = $1 AND details->>'settlement' = 'desk_remainder' \
         AND details->>'channel' = 'Agoda'",
    )
    .bind(id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(audited, 2);

    cleanup(&pool, &[id]).await;
}

fn dec(v: &str) -> Decimal {
    Decimal::from_str(v).unwrap()
}

#[test]
fn checkin_autopost_holds_back_the_increase_since_booking() {
    // Upgrade before check-in: channel booked 100, total now 130.
    assert_eq!(
        split_online_checkin_autopost(dec("130"), dec("130"), Some(dec("100"))),
        Some(OnlineCheckinAutopost {
            post: dec("100"),
            held_back: dec("30")
        })
    );
    // Tourism tax still rides with the channel amount, as before.
    assert_eq!(
        split_online_checkin_autopost(dec("140"), dec("130"), Some(dec("100"))),
        Some(OnlineCheckinAutopost {
            post: dec("110"),
            held_back: dec("30")
        })
    );
    // Unchanged or lowered total: the whole remainder, as before.
    assert_eq!(
        split_online_checkin_autopost(dec("100"), dec("100"), None),
        Some(OnlineCheckinAutopost {
            post: dec("100"),
            held_back: dec("0")
        })
    );
    assert_eq!(
        split_online_checkin_autopost(dec("80"), dec("80"), Some(dec("150"))),
        Some(OnlineCheckinAutopost {
            post: dec("80"),
            held_back: dec("0")
        })
    );
    // Only the upgrade difference is still owed: nothing for the channel.
    assert_eq!(
        split_online_checkin_autopost(dec("30"), dec("130"), Some(dec("100"))),
        None
    );
    assert_eq!(
        split_online_checkin_autopost(dec("0"), dec("100"), None),
        None
    );
}

async fn autopost(pool: &PgPool, id: i64) -> Option<(String, Decimal, String)> {
    let booking = BookingRepository::find_by_id(pool, id)
        .await
        .unwrap()
        .unwrap();
    let mut tx = pool.begin().await.unwrap();
    let recorded = record_online_checkin_payment_tx(&mut tx, &booking, ACTOR)
        .await
        .unwrap();
    tx.commit().await.unwrap();
    if !recorded {
        return None;
    }
    Some(
        sqlx::query_as(
            "SELECT payment_method, amount, COALESCE(notes, '') FROM payments \
             WHERE booking_id = $1 AND payment_type = 'booking' ORDER BY id DESC LIMIT 1",
        )
        .bind(id)
        .fetch_one(pool)
        .await
        .unwrap(),
    )
}

async fn set_total(pool: &PgPool, id: i64, total: &str) {
    exec(
        pool,
        &format!("UPDATE bookings SET total_amount = {total}, subtotal = {total}, room_rate = {total} WHERE id = {id}"),
    )
    .await;
}

/// An OTA guest upgraded before check-in: the auto-post records only what
/// the channel booked; the difference stays for the desk.
#[tokio::test]
async fn online_checkin_autopost_records_only_the_channel_amount() {
    let Some((pool, _guard)) = setup().await else {
        return;
    };
    let (created, edited, untouched, lowered) = (971_070, 971_071, 971_072, 971_073);
    cleanup(&pool, &[created, edited, untouched, lowered]).await;
    for id in [created, edited, untouched, lowered] {
        seed_booking(&pool, id, "online", Some("Agoda"), Some("Agoda.com")).await;
    }

    // 1. "Booking created" snapshot 100, upgraded to 130 at the desk.
    exec(&pool, &format!(
        "INSERT INTO booking_history (booking_id, previous_status, new_status, changed_by, change_reason, metadata) \
         VALUES ({created}, NULL, 'confirmed', {ACTOR}, 'Booking created', '{{\"total_amount\": \"100.00\"}}')"
    )).await;
    set_total(&pool, created, "130").await;
    let (method, amount, notes) = autopost(&pool, created)
        .await
        .expect("channel amount posted");
    assert_eq!(method, "Agoda.com");
    assert_eq!(amount, dec("100"));
    assert!(
        notes.contains("30.00 added after booking left for the desk"),
        "{notes}"
    );
    // The 30 stays owed and the desk collects it via the desk-remainder path.
    assert_bad_request(
        payments::record_payment(&pool, ACTOR, pay(created, "Cash", "booking", 31.0, "over")).await,
        "remaining balance of 30",
    );
    payments::record_payment(&pool, ACTOR, pay(created, "Cash", "booking", 30.0, "cash"))
        .await
        .expect("desk collects the upgrade difference");
    // Re-running the auto-post is a no-op: nothing is owed.
    assert!(autopost(&pool, created).await.is_none());

    // 2. No creation snapshot, but an edit recorded 100 -> 130.
    exec(&pool, &format!(
        "INSERT INTO booking_modifications (booking_id, modification_type, old_value, new_value, price_adjustment, modified_by) \
         VALUES ({edited}, 'rate_change', '{{\"total_amount\": \"100.00\"}}', '{{\"total_amount\": \"130.00\"}}', 30, {ACTOR})"
    )).await;
    set_total(&pool, edited, "130").await;
    assert_eq!(autopost(&pool, edited).await.unwrap().1, dec("100"));

    // 3. No snapshot and no total-changing edit: whole remainder, as before.
    exec(&pool, &format!(
        "INSERT INTO booking_modifications (booking_id, modification_type, old_value, new_value, price_adjustment, modified_by) \
         VALUES ({untouched}, 'general_update', '{{\"total_amount\": \"100.00\"}}', '{{\"total_amount\": \"100.00\"}}', 0, {ACTOR})"
    )).await;
    let (_, amount, notes) = autopost(&pool, untouched).await.unwrap();
    assert_eq!(amount, dec("100"));
    assert_eq!(notes, "Auto-recorded at check-in for online reservation");

    // 4. Total lowered after booking: the (lower) remainder, as before.
    exec(&pool, &format!(
        "INSERT INTO booking_history (booking_id, previous_status, new_status, changed_by, change_reason, metadata) \
         VALUES ({lowered}, NULL, 'confirmed', {ACTOR}, 'Booking created', '{{\"total_amount\": \"150.00\"}}')"
    )).await;
    assert_eq!(autopost(&pool, lowered).await.unwrap().1, dec("100"));

    cleanup(&pool, &[created, edited, untouched, lowered]).await;
}
