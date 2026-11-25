//! Signed-in multi-room checkout with a voucher, end to end against
//! PostgreSQL. A group redeems one voucher across rooms that share the same
//! nights, so the per-night allocation rows must be keyed by room as well
//! (patch 0029). Also covers group dates following their rooms and the
//! "room too small" quote error. Opt-in through `DATABASE_URL`; every fixture
//! is deleted afterwards.
use chrono::{Duration, NaiveDate};
use hotel_app_be::core::error::ApiError;
use hotel_app_be::modules::booking_groups::repository::BookingGroupRepository;
use hotel_app_be::modules::consent::models::{ConsentAcceptance, ConsentDocument};
use hotel_app_be::modules::guest_booking::availability::AvailabilityHub;
use hotel_app_be::modules::guest_booking::group::{
    self, GroupDiscountRequest, GroupQuoteRequest, GroupRoomRequest, GuestGroupBookingRequest,
};
use rust_decimal::Decimal;
use sqlx::{PgPool, postgres::PgPoolOptions};

fn d(value: &str) -> Decimal {
    value.parse().unwrap()
}

async fn pool() -> Option<PgPool> {
    let url = std::env::var("DATABASE_URL").ok()?;
    if std::env::var("JWT_SECRET").is_err() {
        // SAFETY: set before any config read in this binary.
        unsafe { std::env::set_var("JWT_SECRET", "test-secret-test-secret-test-secret") };
    }
    let _ = hotel_app_be::core::config::init_from_env();
    Some(
        PgPoolOptions::new()
            .max_connections(4)
            .after_connect(|conn, _| {
                Box::pin(async move {
                    sqlx::query("SET app.allow_audit_mutation = 'on'")
                        .execute(conn)
                        .await
                        .map(|_| ())
                })
            })
            .connect(&url)
            .await
            .expect("connect"),
    )
}

struct Fx {
    guest_id: i64,
    /// Cheap type (100/night, 3 rooms) and dear type (200/night, 2 rooms).
    cheap: i64,
    dear: i64,
    promotion_ids: Vec<i64>,
    check_in: NaiveDate,
    check_out: NaiveDate,
}

fn tag() -> String {
    static SEQ: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
    static BASE: std::sync::LazyLock<u64> = std::sync::LazyLock::new(|| {
        chrono::Utc::now()
            .timestamp_nanos_opt()
            .unwrap_or_default()
            .unsigned_abs()
    });
    format!(
        "{:x}",
        *BASE + SEQ.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
    )
}

async fn hotel_currency(pool: &PgPool) -> String {
    let value: Option<String> =
        sqlx::query_scalar("SELECT value FROM system_settings WHERE key = 'currency'")
            .fetch_optional(pool)
            .await
            .unwrap();
    value
        .map(|v| v.trim().to_ascii_uppercase())
        .filter(|v| v.len() == 3)
        .unwrap_or_else(|| "MYR".to_string())
}

async fn fixture(pool: &PgPool, offset_days: i64) -> Fx {
    let tag = tag();
    let guest_id: i64 = sqlx::query_scalar(
        "INSERT INTO guests (first_name, last_name, nick_name, email, phone, ic_number, tourism_type) \
         VALUES ('Voucher', 'Group', $1, $2, '+60123456789', 'A1234567', 'local') RETURNING id",
    )
    .bind(format!("VG {tag}"))
    .bind(format!("vg-{tag}@example.com"))
    .fetch_one(pool)
    .await
    .unwrap();
    sqlx::query(
        "INSERT INTO users (username, email, password_hash, full_name, user_type, guest_id, \
             is_active, is_verified) \
         VALUES ($1, $2, 'x', 'Voucher Group', 'guest', $3, true, true)",
    )
    .bind(format!("vg{tag}"))
    .bind(format!("vg-user-{tag}@example.com"))
    .bind(guest_id)
    .execute(pool)
    .await
    .unwrap();
    let mut types = Vec::new();
    for (code, price, rooms) in [("C", "100.00", 3), ("D", "200.00", 2)] {
        let room_type_id: i64 = sqlx::query_scalar(
            "INSERT INTO room_types (code, name, base_price, weekday_rate, weekend_rate, max_occupancy) \
             VALUES ($1, $2, $3::numeric, $3::numeric, $3::numeric, 2) RETURNING id",
        )
        .bind(format!("VG{code}{tag}"))
        .bind(format!("VG Type {code} {tag}"))
        .bind(price)
        .fetch_one(pool)
        .await
        .unwrap();
        for n in 0..rooms {
            sqlx::query(
                "INSERT INTO rooms (room_number, room_type_id, status, is_active) \
                 VALUES ($1, $2, 'available', true)",
            )
            .bind(format!("VG{code}{n}{tag}"))
            .bind(room_type_id)
            .execute(pool)
            .await
            .unwrap();
        }
        types.push(room_type_id);
    }
    let today: NaiveDate = sqlx::query_scalar("SELECT CURRENT_DATE")
        .fetch_one(pool)
        .await
        .unwrap();
    Fx {
        guest_id,
        cheap: types[0],
        dear: types[1],
        promotion_ids: Vec::new(),
        check_in: today + Duration::days(offset_days),
        check_out: today + Duration::days(offset_days + 2),
    }
}

async fn voucher(pool: &PgPool, f: &mut Fx, kind: &str, value: &str) -> i64 {
    let tag = tag();
    let currency = hotel_currency(pool).await;
    let promotion_id: i64 = sqlx::query_scalar(
        "INSERT INTO promotions (slug, name, discount_type, discount_value, currency, status, \
             promotion_kind) \
         VALUES ($1, $2, $3, $4::numeric, $5, 'published', 'voucher') RETURNING id",
    )
    .bind(format!("vg-{tag}"))
    .bind(format!("VG {tag}"))
    .bind(kind)
    .bind(value)
    .bind(currency)
    .fetch_one(pool)
    .await
    .unwrap();
    f.promotion_ids.push(promotion_id);
    sqlx::query_scalar(
        "INSERT INTO vouchers (promotion_id, guest_id, code, source) \
         VALUES ($1, $2, $3, 'admin_issue') RETURNING id",
    )
    .bind(promotion_id)
    .bind(f.guest_id)
    .bind(format!("VG{tag}"))
    .fetch_one(pool)
    .await
    .unwrap()
}

async fn cleanup(pool: &PgPool, f: &Fx) {
    let ids: Vec<i64> = sqlx::query_scalar("SELECT id FROM bookings WHERE guest_id = $1")
        .bind(f.guest_id)
        .fetch_all(pool)
        .await
        .unwrap_or_default();
    for sql in [
        "DELETE FROM voucher_redemption_allocations WHERE booking_id = ANY($1)",
        "DELETE FROM voucher_redemptions WHERE booking_id = ANY($1)",
        "DELETE FROM email_deliveries WHERE booking_id = ANY($1)",
        "DELETE FROM payments WHERE booking_id = ANY($1)",
        "DELETE FROM consent_records WHERE booking_id = ANY($1)",
        "DELETE FROM booking_history WHERE booking_id = ANY($1)",
        "DELETE FROM booking_modifications WHERE booking_id = ANY($1)",
        "DELETE FROM bookings WHERE id = ANY($1)",
    ] {
        let _ = sqlx::query(sqlx::AssertSqlSafe(sql))
            .bind(&ids)
            .execute(pool)
            .await;
    }
    for sql in [
        "DELETE FROM voucher_redemptions WHERE guest_id = $1",
        "DELETE FROM consent_records WHERE guest_id = $1",
        "DELETE FROM booking_groups WHERE guest_id = $1",
        "DELETE FROM vouchers WHERE guest_id = $1",
        "DELETE FROM guest_complimentary_credits WHERE guest_id = $1",
        "DELETE FROM users WHERE guest_id = $1",
        "DELETE FROM guests WHERE id = $1",
    ] {
        let _ = sqlx::query(sqlx::AssertSqlSafe(sql))
            .bind(f.guest_id)
            .execute(pool)
            .await;
    }
    for id in &f.promotion_ids {
        let _ = sqlx::query("DELETE FROM promotions WHERE id = $1")
            .bind(id)
            .execute(pool)
            .await;
    }
    for room_type_id in [f.cheap, f.dear] {
        let _ = sqlx::query("DELETE FROM rooms WHERE room_type_id = $1")
            .bind(room_type_id)
            .execute(pool)
            .await;
        let _ = sqlx::query("DELETE FROM room_types WHERE id = $1")
            .bind(room_type_id)
            .execute(pool)
            .await;
    }
}

/// Run `body`, always clean up, then re-raise any failure.
macro_rules! scenario {
    ($pool:ident, $f:ident, $body:block) => {{
        let result = async { $body }.await;
        cleanup(&$pool, &$f).await;
        result
    }};
}

fn room(room_type_id: i64) -> GroupRoomRequest {
    GroupRoomRequest {
        room_type_id,
        adults: Some(1),
        children: Some(0),
        extra_beds: None,
    }
}

fn granted(document: ConsentDocument) -> ConsentAcceptance {
    ConsentAcceptance {
        document,
        version: document.current_version().to_string(),
        granted: true,
        locale: "en".to_string(),
    }
}

/// Quote then check out the cart as the signed-in guest.
async fn checkout(
    pool: &PgPool,
    f: &Fx,
    rooms: Vec<GroupRoomRequest>,
    discounts: GroupDiscountRequest,
    request_id: &str,
) -> (group::GroupQuote, group::GroupBookingConfirmation) {
    let quote = group::quote_group(
        pool,
        Some(f.guest_id),
        &GroupQuoteRequest {
            check_in_date: f.check_in.to_string(),
            check_out_date: f.check_out.to_string(),
            rooms: rooms.clone(),
            tourism_type: Some("local".to_string()),
            discounts: discounts.clone(),
        },
    )
    .await
    .expect("group quote");
    let confirmation = group::create_for_guest(
        pool,
        &AvailabilityHub::default(),
        f.guest_id,
        GuestGroupBookingRequest {
            client_request_id: request_id.to_string(),
            check_in_date: f.check_in.to_string(),
            check_out_date: f.check_out.to_string(),
            rooms,
            expected_total: quote.total_amount,
            special_requests: None,
            cleaning_preference: None,
            smoking_preference: None,
            consents: vec![
                granted(ConsentDocument::TermsOfService),
                granted(ConsentDocument::PrivacyNotice),
            ],
            discounts,
        },
        None,
        None,
    )
    .await
    .expect("multi-room checkout with a voucher must succeed");
    (quote, confirmation)
}

async fn allocation_rows(pool: &PgPool, group_id: i64) -> Vec<(i64, NaiveDate, Decimal)> {
    sqlx::query_as(
        "SELECT a.booking_id, a.stay_date, a.discount_amount \
         FROM voucher_redemption_allocations a JOIN bookings b ON b.id = a.booking_id \
         WHERE b.booking_group_id = $1 ORDER BY a.booking_id, a.stay_date",
    )
    .bind(group_id)
    .fetch_all(pool)
    .await
    .unwrap()
}

/// 3 rooms (100, 200, 100 a night × 2 nights) with a 10% voucher: every room
/// is discounted on its own (20, 40, 20), one allocation row per room-night,
/// and the group total is the sum of its rooms.
#[tokio::test]
async fn postgres_multi_room_checkout_with_percentage_voucher() {
    let Some(pool) = pool().await else { return };
    let mut f = fixture(&pool, 40).await;
    let voucher_id = voucher(&pool, &mut f, "percentage", "10.00").await;
    scenario!(pool, f, {
        let (quote, confirmation) = checkout(
            &pool,
            &f,
            vec![room(f.cheap), room(f.dear), room(f.cheap)],
            GroupDiscountRequest {
                voucher_id: Some(voucher_id),
                ..Default::default()
            },
            &format!("vg-pct-{}", f.guest_id),
        )
        .await;
        assert_eq!(confirmation.room_count, 3);
        assert_eq!(confirmation.total_amount, quote.total_amount);
        assert_eq!(quote.voucher_discount, d("80.00"));
        let mut discounts: Vec<Decimal> = confirmation
            .rooms
            .iter()
            .map(|room| room.discount_amount)
            .collect();
        discounts.sort();
        assert_eq!(discounts, vec![d("20.00"), d("20.00"), d("40.00")]);
        let rows = allocation_rows(&pool, confirmation.group_id).await;
        assert_eq!(rows.len(), 6, "one allocation per room-night");
        let allocated: Decimal = rows.iter().map(|(_, _, discount)| *discount).sum();
        assert_eq!(allocated, d("80.00"));
        let (group_total, members_total, voucher_status): (Decimal, Decimal, String) =
            sqlx::query_as(
                "SELECT g.total_amount, \
                     (SELECT SUM(total_amount) FROM bookings WHERE booking_group_id = g.id), \
                     (SELECT status FROM vouchers WHERE id = $2) \
                 FROM booking_groups g WHERE g.id = $1",
            )
            .bind(confirmation.group_id)
            .bind(voucher_id)
            .fetch_one(&pool)
            .await
            .unwrap();
        assert_eq!(group_total, members_total);
        assert_eq!(group_total, d("720.00"));
        assert_eq!(voucher_status, "redeemed");
    })
}

/// 3 rooms (200, 100, 100 a night × 2 nights) with a fixed 50 voucher and one free night: the credit settles a
/// cheapest room-night (100), the 50 is applied once and split by what each
/// room still owes, and the checkout commits.
#[tokio::test]
async fn postgres_multi_room_checkout_with_fixed_voucher_and_free_night() {
    let Some(pool) = pool().await else { return };
    let mut f = fixture(&pool, 50).await;
    let voucher_id = voucher(&pool, &mut f, "fixed_amount", "50.00").await;
    sqlx::query(
        "INSERT INTO guest_complimentary_credits (guest_id, room_type_id, nights_available) \
         VALUES ($1, $2, 1)",
    )
    .bind(f.guest_id)
    .bind(f.cheap)
    .execute(&pool)
    .await
    .unwrap();
    scenario!(pool, f, {
        let (quote, confirmation) = checkout(
            &pool,
            &f,
            vec![room(f.dear), room(f.cheap), room(f.cheap)],
            GroupDiscountRequest {
                voucher_id: Some(voucher_id),
                use_credits: true,
                complimentary: None,
            },
            &format!("vg-fix-{}", f.guest_id),
        )
        .await;
        assert_eq!(quote.complimentary_nights, 1);
        assert_eq!(quote.complimentary_discount, d("100.00"));
        assert_eq!(quote.voucher_discount, d("50.00"));
        // 800 list (2 nights × 200 + 100 + 100) − 100 free night − 50 voucher.
        assert_eq!(confirmation.total_amount, d("650.00"));
        let shares: Vec<Decimal> = sqlx::query_scalar(
            "SELECT group_voucher_share FROM bookings WHERE booking_group_id = $1",
        )
        .bind(confirmation.group_id)
        .fetch_all(&pool)
        .await
        .unwrap();
        assert_eq!(shares.iter().copied().sum::<Decimal>(), d("50.00"));
        let credited: (i64, Option<i32>) = sqlx::query_as(
            "SELECT COUNT(*), MAX(b.complimentary_nights) FROM bookings b \
             JOIN rooms r ON r.id = b.room_id \
             WHERE b.booking_group_id = $1 AND r.room_type_id = $2 \
               AND COALESCE(b.complimentary_nights, 0) > 0",
        )
        .bind(confirmation.group_id)
        .bind(f.cheap)
        .fetch_one(&pool)
        .await
        .unwrap();
        assert_eq!(
            credited,
            (1, Some(1)),
            "the free night sits on a cheap room"
        );
        let left: i32 = sqlx::query_scalar(
            "SELECT nights_available FROM guest_complimentary_credits WHERE guest_id = $1",
        )
        .bind(f.guest_id)
        .fetch_one(&pool)
        .await
        .unwrap();
        assert_eq!(left, 0);
        let rows = allocation_rows(&pool, confirmation.group_id).await;
        assert_eq!(rows.len(), 6, "one allocation per room-night");
        let allocated: Decimal = rows.iter().map(|(_, _, discount)| *discount).sum();
        assert_eq!(allocated, d("150.00"));
    })
}

/// When a room's stay moves, the group's dates follow its rooms (earliest
/// check-in, latest check-out).
#[tokio::test]
async fn postgres_group_dates_follow_member_rooms() {
    let Some(pool) = pool().await else { return };
    let f = fixture(&pool, 60).await;
    scenario!(pool, f, {
        let (_, confirmation) = checkout(
            &pool,
            &f,
            vec![room(f.cheap), room(f.cheap)],
            GroupDiscountRequest::default(),
            &format!("vg-dates-{}", f.guest_id),
        )
        .await;
        let moved_in = f.check_in + Duration::days(30);
        let moved_out = f.check_out + Duration::days(30);
        let mut tx = pool.begin().await.unwrap();
        sqlx::query(
            "UPDATE bookings SET check_in_date = $2, check_out_date = $3 \
             WHERE booking_group_id = $1",
        )
        .bind(confirmation.group_id)
        .bind(moved_in)
        .bind(moved_out)
        .execute(&mut *tx)
        .await
        .unwrap();
        let first: i64 =
            sqlx::query_scalar("SELECT MIN(id) FROM bookings WHERE booking_group_id = $1")
                .bind(confirmation.group_id)
                .fetch_one(&mut *tx)
                .await
                .unwrap();
        BookingGroupRepository::refresh_for_booking_tx(&mut tx, first)
            .await
            .unwrap();
        tx.commit().await.unwrap();
        let dates: (NaiveDate, NaiveDate) = sqlx::query_as(
            "SELECT check_in_date, check_out_date FROM booking_groups WHERE id = $1",
        )
        .bind(confirmation.group_id)
        .fetch_one(&pool)
        .await
        .unwrap();
        assert_eq!(dates, (moved_in, moved_out));
    })
}

/// A party larger than the room fits is told the room is too small, not that
/// it is unavailable.
#[tokio::test]
async fn postgres_quote_for_too_many_guests_says_room_too_small() {
    let Some(pool) = pool().await else { return };
    let f = fixture(&pool, 70).await;
    scenario!(pool, f, {
        let result = group::quote_group(
            &pool,
            Some(f.guest_id),
            &GroupQuoteRequest {
                check_in_date: f.check_in.to_string(),
                check_out_date: f.check_out.to_string(),
                rooms: vec![GroupRoomRequest {
                    room_type_id: f.cheap,
                    adults: Some(3),
                    children: Some(1),
                    extra_beds: None,
                }],
                tourism_type: Some("local".to_string()),
                discounts: Default::default(),
            },
        )
        .await;
        match result {
            Err(ApiError::BadRequest(message)) => {
                assert!(message.contains("too small for 4 guests"), "{message}");
            }
            other => panic!(
                "expected a too-small error, got {:?}",
                other.map(|q| q.total_amount)
            ),
        }
    })
}
