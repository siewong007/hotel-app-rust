//! Live-PostgreSQL regression coverage for Step 3/4 database, decode,
//! search, financial, and concurrency fixes. Skips gracefully when
//! `DATABASE_URL` is unset (matching the other integration suites).

mod postgres_tests {
    use axum::Json;
    use axum::extract::{Extension, Path, Query, State};
    use chrono::NaiveDate;
    use hotel_app_be::core::db::hotel_today;
    use hotel_app_be::core::error::ApiError;
    use hotel_app_be::models::{BookingPaginationParams, BookingUpdateInput};
    use hotel_app_be::modules::bookings::handlers::{get_bookings_handler, update_booking_handler};
    use hotel_app_be::modules::insights::service as insights_service;
    use hotel_app_be::modules::ledgers::repository as ledger_repo;
    use hotel_app_be::modules::promotions::repository::PromotionRepository;
    use hotel_app_be::modules::promotions::validation::PromotionDraft;
    use hotel_app_be::modules::revenue::repository::RevenueRepository;
    use hotel_app_be::modules::revenue::validation::RevenueRange;
    use hotel_app_be::modules::support::repository::SupportRepository;
    use rust_decimal::Decimal;
    use sqlx::{PgPool, postgres::PgPoolOptions};
    use std::str::FromStr;

    fn d(value: &str) -> Decimal {
        Decimal::from_str(value).expect("valid decimal")
    }

    fn pg_serial_lock() -> std::sync::Arc<tokio::sync::Mutex<()>> {
        static LOCK: std::sync::OnceLock<std::sync::Arc<tokio::sync::Mutex<()>>> =
            std::sync::OnceLock::new();
        LOCK.get_or_init(|| std::sync::Arc::new(tokio::sync::Mutex::new(())))
            .clone()
    }

    async fn setup_pg_pool() -> Option<(PgPool, tokio::sync::OwnedMutexGuard<()>)> {
        let database_url = match std::env::var("DATABASE_URL") {
            Ok(url) => url,
            Err(_) => {
                eprintln!("Skipping db_perf_integrity_regressions because DATABASE_URL is not set");
                return None;
            }
        };
        let guard = pg_serial_lock().lock_owned().await;
        if std::env::var("JWT_SECRET").is_err() {
            // SAFETY: initialized once under serial mutex in test process.
            unsafe { std::env::set_var("JWT_SECRET", "test-secret-test-secret-test-secret") };
        }
        let _ = hotel_app_be::core::config::init_from_env();
        let pool = PgPoolOptions::new()
            .max_connections(4)
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
            .expect("connect to test postgres");
        Some((pool, guard))
    }

    async fn cleanup_band(pool: &PgPool, start: i64, end: i64) {
        for stmt in [
            "DELETE FROM voucher_redemption_allocations WHERE booking_id BETWEEN $1 AND $2",
            "DELETE FROM voucher_redemptions WHERE id BETWEEN $1 AND $2 OR booking_id BETWEEN $1 AND $2",
            "DELETE FROM invoices WHERE id BETWEEN $1 AND $2 OR booking_id BETWEEN $1 AND $2",
            "DELETE FROM payments WHERE id BETWEEN $1 AND $2 OR booking_id BETWEEN $1 AND $2",
            "DELETE FROM bookings WHERE id BETWEEN $1 AND $2",
            "DELETE FROM booking_groups WHERE id BETWEEN $1 AND $2",
            "DELETE FROM vouchers WHERE id BETWEEN $1 AND $2 OR promotion_id BETWEEN $1 AND $2",
            "DELETE FROM promotion_room_types WHERE promotion_id BETWEEN $1 AND $2 OR room_type_id BETWEEN $1 AND $2",
            "DELETE FROM promotions WHERE id BETWEEN $1 AND $2",
            "DELETE FROM support_messages WHERE id BETWEEN $1 AND $2 OR conversation_id BETWEEN $1 AND $2",
            "DELETE FROM support_events WHERE id BETWEEN $1 AND $2 OR conversation_id BETWEEN $1 AND $2",
            "DELETE FROM support_conversations WHERE id BETWEEN $1 AND $2",
            "DELETE FROM room_status_change_log WHERE room_id BETWEEN $1 AND $2",
            "DELETE FROM rooms WHERE id BETWEEN $1 AND $2",
            "DELETE FROM room_types WHERE id BETWEEN $1 AND $2",
            "DELETE FROM guests WHERE id BETWEEN $1 AND $2",
            "DELETE FROM user_roles WHERE user_id BETWEEN $1 AND $2",
            "DELETE FROM users WHERE id BETWEEN $1 AND $2",
        ] {
            sqlx::query(stmt)
                .bind(start)
                .bind(end)
                .execute(pool)
                .await
                .unwrap();
        }
    }

    #[tokio::test]
    async fn promotions_claim_counts_decode_as_bigint_and_unnest_targets() {
        let Some((pool, _guard)) = setup_pg_pool().await else {
            return;
        };
        let base = 997_100;
        let actor_id = base + 1;
        let rt1 = base + 2;
        let rt2 = base + 3;
        cleanup_band(&pool, base, base + 99).await;
        sqlx::query("DELETE FROM promotions WHERE slug = 'reg-promo-997100'")
            .execute(&pool)
            .await
            .unwrap();

        sqlx::query(
            "INSERT INTO users (id, username, email, full_name, user_type, is_active, is_verified) \
             OVERRIDING SYSTEM VALUE VALUES ($1, 'reg_997101', 'reg997101@hotel.test', 'Reg User', 'staff', true, true)",
        )
        .bind(actor_id)
        .execute(&pool)
        .await
        .unwrap();
        for (id, code, name) in [
            (rt1, "REG997A", "Reg Room A"),
            (rt2, "REG997B", "Reg Room B"),
        ] {
            sqlx::query(
                "INSERT INTO room_types (id, code, name, base_price, max_occupancy) \
                 OVERRIDING SYSTEM VALUE VALUES ($1, $2, $3, 120.00, 2)",
            )
            .bind(id)
            .bind(code)
            .bind(name)
            .execute(&pool)
            .await
            .unwrap();
        }

        let draft = PromotionDraft {
            slug: "reg-promo-997100".to_string(),
            name: "Regression Bigint Promo".to_string(),
            description: Some("Verifies bigint claim_limit and unnest targets".to_string()),
            terms: None,
            promotion_kind: "voucher".to_string(),
            discount_type: "percentage".to_string(),
            discount_value: d("10.00"),
            max_discount_amount: None,
            currency: "MYR".to_string(),
            claim_starts_at: None,
            claim_ends_at: None,
            stay_starts_on: None,
            stay_ends_on: None,
            min_nights: Some(1),
            max_nights: None,
            min_subtotal: Some(d("0.00")),
            claim_limit: Some(100),
            per_guest_limit: 1,
            is_public: false,
            is_cancellable: true,
            room_type_ids: vec![rt1, rt2],
            internal_code: Some("REG100".to_string()),
            objective: Some("retention".to_string()),
            booking_channel_ids: vec![],
            loyalty_tier_ids: vec![],
        };

        let mut tx = pool.begin().await.unwrap();
        let promo_id = PromotionRepository::insert_promotion(&mut tx, &draft, actor_id)
            .await
            .expect("insert promotion");
        PromotionRepository::replace_room_type_targets(&mut tx, promo_id, &[rt1, rt2, rt1])
            .await
            .expect("unnest room type targets deduplicating duplicates");
        let updated =
            PromotionRepository::update_promotion(&mut tx, promo_id, Some(1), &draft, actor_id)
                .await
                .expect("update promotion with bigint version check");
        assert_eq!(updated, Some(promo_id));
        tx.commit().await.unwrap();

        let fetched = PromotionRepository::find_by_id(&pool, promo_id)
            .await
            .expect("find_by_id must decode bigint fields")
            .expect("promotion must exist");
        assert_eq!(fetched.claim_limit, Some(100));
        assert_eq!(fetched.claimed_count, 0);
        assert_eq!(fetched.room_type_ids.len(), 2);

        sqlx::query("DELETE FROM promotion_room_types WHERE promotion_id = $1")
            .bind(promo_id)
            .execute(&pool)
            .await
            .unwrap();
        sqlx::query("DELETE FROM promotions WHERE id = $1")
            .bind(promo_id)
            .execute(&pool)
            .await
            .unwrap();
        cleanup_band(&pool, base, base + 99).await;
    }

    #[tokio::test]
    async fn support_last_message_by_user_handles_null_author() {
        let Some((pool, _guard)) = setup_pg_pool().await else {
            return;
        };
        let base = 997_200;
        let guest_id = base + 1;
        let staff_id = base + 2;
        let conv_id = base + 3;
        let msg_id = base + 4;
        cleanup_band(&pool, base, base + 99).await;

        sqlx::query(
            "INSERT INTO guests (id, nick_name, email) OVERRIDING SYSTEM VALUE VALUES ($1, 'Support Null Author', 'reg997201@hotel.test')",
        )
        .bind(guest_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO users (id, username, email, full_name, user_type, is_active, is_verified) \
             OVERRIDING SYSTEM VALUE VALUES ($1, 'reg_997202', 'reg997202@hotel.test', 'Staff User', 'staff', true, true)",
        )
        .bind(staff_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO support_conversations (id, conversation_number, guest_id, subject, category, status) \
             OVERRIDING SYSTEM VALUE VALUES ($1, 'SUP-REG-997203', $2, 'Regression NULL author', 'booking', 'waiting_for_staff')",
        )
        .bind(conv_id)
        .bind(guest_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO support_messages (id, conversation_id, author_type, author_guest_id, author_user_id, body, client_message_id) \
             OVERRIDING SYSTEM VALUE VALUES ($1, $2, 'staff', NULL, NULL, 'Deleted staff message body', 'reg-cm-997204')",
        )
        .bind(msg_id)
        .bind(conv_id)
        .execute(&pool)
        .await
        .unwrap();

        let belongs = SupportRepository::staff_client_message_belongs_to_actor(
            &pool,
            conv_id,
            staff_id,
            "reg-cm-997204",
        )
        .await
        .expect("COALESCE(author_user_id = $2, FALSE) must decode Some(false) when author_user_id IS NULL");
        assert_eq!(belongs, Some(false));

        cleanup_band(&pool, base, base + 99).await;
    }

    #[tokio::test]
    async fn bookings_hourly_stay_matches_date_and_month_search() {
        let Some((pool, _guard)) = setup_pg_pool().await else {
            return;
        };
        let base = 997_300;
        let guest_id = base + 1;
        let rt_id = base + 2;
        let room_id = base + 3;
        let booking_id = base + 4;
        cleanup_band(&pool, base, base + 99).await;

        sqlx::query(
            "INSERT INTO guests (id, nick_name, email) OVERRIDING SYSTEM VALUE VALUES ($1, 'Hourly Guest 997', 'reg997301@hotel.test')",
        )
        .bind(guest_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO room_types (id, code, name, base_price, max_occupancy) OVERRIDING SYSTEM VALUE VALUES ($1, 'REG997H', 'Hourly Type', 100.00, 2)",
        )
        .bind(rt_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO rooms (id, room_number, room_type_id, status) OVERRIDING SYSTEM VALUE VALUES ($1, '997H1', $2, 'available')",
        )
        .bind(room_id)
        .bind(rt_id)
        .execute(&pool)
        .await
        .unwrap();

        let hourly_day = NaiveDate::from_ymd_opt(2032, 7, 15).unwrap();
        sqlx::query(
            "INSERT INTO bookings (id, booking_number, guest_id, room_id, check_in_date, check_out_date, room_rate, subtotal, total_amount, status, payment_status) \
             OVERRIDING SYSTEM VALUE VALUES ($1, 'B-REG-997304', $2, $3, $4, $4, 60.00, 60.00, 60.00, 'confirmed', 'unpaid')",
        )
        .bind(booking_id)
        .bind(guest_id)
        .bind(room_id)
        .bind(hourly_day)
        .execute(&pool)
        .await
        .unwrap();

        let by_date = get_bookings_handler(
            State(pool.clone()),
            Query(BookingPaginationParams {
                date_search: Some(hourly_day),
                search: Some("B-REG-997304".to_string()),
                ..Default::default()
            }),
        )
        .await
        .expect("date_search must succeed");
        assert!(
            by_date.data.iter().any(|b| b.id == booking_id),
            "hourly same-day booking must match date_search"
        );

        let by_month = get_bookings_handler(
            State(pool.clone()),
            Query(BookingPaginationParams {
                month_search: Some(NaiveDate::from_ymd_opt(2032, 7, 1).unwrap()),
                search: Some("B-REG-997304".to_string()),
                ..Default::default()
            }),
        )
        .await
        .expect("month_search must succeed");
        assert!(
            by_month.data.iter().any(|b| b.id == booking_id),
            "hourly same-day booking must match month_search"
        );

        cleanup_band(&pool, base, base + 99).await;
    }

    #[tokio::test]
    async fn bookings_group_void_resplits_voucher_under_group_lock() {
        let Some((pool, _guard)) = setup_pg_pool().await else {
            return;
        };
        let base = 997_400;
        let actor_id = base + 1;
        let guest_id = base + 2;
        let rt_id = base + 3;
        let room_a = base + 4;
        let room_b = base + 5;
        let promo_id = base + 6;
        let voucher_id = base + 7;
        let group_id = base + 8;
        let booking_a = base + 9;
        let booking_b = base + 10;
        let red_a = base + 11;
        cleanup_band(&pool, base, base + 99).await;

        sqlx::query(
            "INSERT INTO users (id, username, email, full_name, user_type, is_active, is_verified) \
             OVERRIDING SYSTEM VALUE VALUES ($1, 'reg_997401', 'reg997401@hotel.test', 'Admin Reg', 'staff', true, true)",
        )
        .bind(actor_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO user_roles (user_id, role_id) SELECT $1, id FROM roles WHERE name = 'admin' ON CONFLICT DO NOTHING",
        )
        .bind(actor_id)
        .execute(&pool)
        .await
        .unwrap();
        hotel_app_be::core::rbac_cache::clear_all();

        sqlx::query(
            "INSERT INTO guests (id, nick_name, email, tourism_type) \
             OVERRIDING SYSTEM VALUE VALUES ($1, 'Group Void Guest', 'reg997402@hotel.test', 'local')",
        )
        .bind(guest_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO room_types (id, code, name, base_price, max_occupancy) \
             OVERRIDING SYSTEM VALUE VALUES ($1, 'REG997G', 'Group Void Type', 100.00, 2)",
        )
        .bind(rt_id)
        .execute(&pool)
        .await
        .unwrap();
        for (room_id, num) in [(room_a, "997G1"), (room_b, "997G2")] {
            sqlx::query(
                "INSERT INTO rooms (id, room_number, room_type_id, status) OVERRIDING SYSTEM VALUE VALUES ($1, $2, $3, 'available')",
            )
            .bind(room_id)
            .bind(num)
            .bind(rt_id)
            .execute(&pool)
            .await
            .unwrap();
        }
        sqlx::query(
            "INSERT INTO promotions (id, slug, name, status, promotion_kind, discount_type, discount_value, currency, per_guest_limit, is_public, is_cancellable, created_by, updated_by) \
             OVERRIDING SYSTEM VALUE VALUES ($1, 'reg-grp-997406', 'Group Void Promo', 'published', 'voucher', 'fixed_amount', 60.00, 'MYR', 1, false, true, $2, $2)",
        )
        .bind(promo_id)
        .bind(actor_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO vouchers (id, promotion_id, guest_id, code, status, source) \
             OVERRIDING SYSTEM VALUE VALUES ($1, $2, $3, 'VCHREG997407', 'redeemed', 'admin_issue')",
        )
        .bind(voucher_id)
        .bind(promo_id)
        .bind(guest_id)
        .execute(&pool)
        .await
        .unwrap();

        let ci = NaiveDate::from_ymd_opt(2032, 8, 10).unwrap();
        let co = NaiveDate::from_ymd_opt(2032, 8, 12).unwrap();
        sqlx::query(
            "INSERT INTO booking_groups (id, group_number, guest_id, check_in_date, check_out_date, status, payment_status, room_count, subtotal, discount_amount, tax_amount, tourism_tax_amount, total_amount, currency, voucher_id, created_by) \
             OVERRIDING SYSTEM VALUE VALUES ($1, 'GRP-REG-997408', $2, $3, $4, 'active', 'unpaid', 2, 400.00, 60.00, 0.00, 0.00, 340.00, 'MYR', $5, $6)",
        )
        .bind(group_id)
        .bind(guest_id)
        .bind(ci)
        .bind(co)
        .bind(voucher_id)
        .bind(actor_id)
        .execute(&pool)
        .await
        .unwrap();

        for (bid, bnum, rid) in [
            (booking_a, "B-REG-997409", room_a),
            (booking_b, "B-REG-997410", room_b),
        ] {
            sqlx::query(
                "INSERT INTO bookings (id, booking_number, guest_id, room_id, booking_group_id, check_in_date, check_out_date, room_rate, subtotal, discount_amount, tax_amount, tourism_tax_amount, total_amount, currency, status, payment_status) \
                 OVERRIDING SYSTEM VALUE VALUES ($1, $2, $3, $4, $5, $6, $7, 100.00, 200.00, 30.00, 0.00, 0.00, 170.00, 'MYR', 'confirmed', 'unpaid')",
            )
            .bind(bid)
            .bind(bnum)
            .bind(guest_id)
            .bind(rid)
            .bind(group_id)
            .bind(ci)
            .bind(co)
            .execute(&pool)
            .await
            .unwrap();
        }
        sqlx::query(
            "INSERT INTO voucher_redemptions (id, voucher_id, promotion_id, booking_id, guest_id, status, gross_subtotal, discount_type, discount_value, discount_amount, net_total, applied_by) \
             OVERRIDING SYSTEM VALUE VALUES ($1, $2, $3, $4, $5, 'applied', 400.00, 'fixed_amount', 60.00, 60.00, 340.00, $6)",
        )
        .bind(red_a)
        .bind(voucher_id)
        .bind(promo_id)
        .bind(booking_a)
        .bind(guest_id)
        .bind(actor_id)
        .execute(&pool)
        .await
        .unwrap();
        for bid in [booking_a, booking_b] {
            for day_offset in 0..2 {
                let d_stay = ci + chrono::Duration::days(day_offset);
                sqlx::query(
                    "INSERT INTO voucher_redemption_allocations (redemption_id, booking_id, stay_date, gross_amount, discount_amount, net_amount) \
                     VALUES ($1, $2, $3, 100.00, 15.00, 85.00)",
                )
                .bind(red_a)
                .bind(bid)
                .bind(d_stay)
                .execute(&pool)
                .await
                .unwrap();
            }
        }

        let res = update_booking_handler(
            State(pool.clone()),
            Extension(actor_id),
            Path(booking_a),
            Json(BookingUpdateInput {
                status: Some("voided".to_string()),
                ..Default::default()
            }),
        )
        .await
        .expect("voiding one room in voucher group must succeed cleanly");
        assert_eq!(res.status, "voided");

        let voucher_status: String =
            sqlx::query_scalar("SELECT status FROM vouchers WHERE id = $1")
                .bind(voucher_id)
                .fetch_one(&pool)
                .await
                .unwrap();
        assert_eq!(voucher_status, "redeemed");

        let remaining_status: String =
            sqlx::query_scalar("SELECT status FROM bookings WHERE id = $1")
                .bind(booking_b)
                .fetch_one(&pool)
                .await
                .unwrap();
        assert_eq!(remaining_status, "confirmed");

        cleanup_band(&pool, base, base + 99).await;
    }

    #[tokio::test]
    async fn ledgers_missing_id_returns_not_found() {
        let Some((pool, _guard)) = setup_pg_pool().await else {
            return;
        };
        let res = ledger_repo::get_ledger_payments(&pool, 997_999).await;
        assert!(
            matches!(res, Err(ApiError::NotFound(_))),
            "missing ledger id must return NotFound, got {res:?}"
        );
    }

    #[tokio::test]
    async fn revenue_pipeline_excludes_keycard_deposit_refunds_and_paid_invoices() {
        let Some((pool, _guard)) = setup_pg_pool().await else {
            return;
        };
        let base = 997_600;
        let guest_id = base + 1;
        let rt_id = base + 2;
        let room_id = base + 3;
        let booking_id = base + 4;
        cleanup_band(&pool, base, base + 99).await;

        let window_day = NaiveDate::from_ymd_opt(2033, 4, 15).unwrap();
        let range = RevenueRange {
            from: window_day,
            to: window_day,
        };
        let today = hotel_today(&pool).await.unwrap();
        let baseline = RevenueRepository::pipeline(&pool, &range, today)
            .await
            .unwrap();

        sqlx::query(
            "INSERT INTO guests (id, nick_name, email, tourism_type) OVERRIDING SYSTEM VALUE VALUES ($1, 'Rev Pipeline Guest', 'reg997601@hotel.test', 'local')",
        )
        .bind(guest_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO room_types (id, code, name, base_price, max_occupancy) OVERRIDING SYSTEM VALUE VALUES ($1, 'REG997P', 'Rev Type', 500.00, 2)",
        )
        .bind(rt_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO rooms (id, room_number, room_type_id, status) OVERRIDING SYSTEM VALUE VALUES ($1, '997P1', $2, 'available')",
        )
        .bind(room_id)
        .bind(rt_id)
        .execute(&pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO bookings (id, booking_number, guest_id, room_id, check_in_date, check_out_date, room_rate, subtotal, total_amount, status, payment_status) \
             OVERRIDING SYSTEM VALUE VALUES ($1, 'B-REG-997604', $2, $3, $4, $5, 500.00, 1000.00, 1000.00, 'confirmed', 'partial')",
        )
        .bind(booking_id)
        .bind(guest_id)
        .bind(room_id)
        .bind(today + chrono::Duration::days(10))
        .bind(today + chrono::Duration::days(12))
        .execute(&pool)
        .await
        .unwrap();

        // Completed booking payment (counted), deposit + keycard deposit refund + void payment (all excluded).
        sqlx::query(
            "INSERT INTO payments (id, booking_id, amount, currency, payment_method, payment_type, status, notes, refunded_at, created_at) \
             OVERRIDING SYSTEM VALUE VALUES \
             ($1, $5, 1000.00, 'MYR', 'online_payment', 'booking', 'completed', 'Stay payment', NULL, ($6::date::timestamp AT TIME ZONE current_setting('TimeZone')) + interval '10 hours'), \
             ($2, $5, 50.00, 'MYR', 'cash', 'deposit', 'completed', 'Keycard deposit', NULL, ($6::date::timestamp AT TIME ZONE current_setting('TimeZone')) + interval '11 hours'), \
             ($3, $5, 50.00, 'MYR', 'cash', 'refund', 'refunded', 'Keycard deposit refund — returned', ($6::date::timestamp AT TIME ZONE current_setting('TimeZone')) + interval '12 hours', ($6::date::timestamp AT TIME ZONE current_setting('TimeZone')) + interval '12 hours'), \
             ($4, $5, 200.00, 'MYR', 'credit_card', 'booking', 'void', 'Voided retry', NULL, ($6::date::timestamp AT TIME ZONE current_setting('TimeZone')) + interval '13 hours')",
        )
        .bind(base + 11)
        .bind(base + 12)
        .bind(base + 13)
        .bind(base + 14)
        .bind(booking_id)
        .bind(window_day)
        .execute(&pool)
        .await
        .unwrap();

        // Paid invoice (excluded) and partially paid issued invoice (300.00 balance due).
        sqlx::query(
            "INSERT INTO invoices (id, invoice_number, booking_id, bill_to_guest_id, billing_name, issue_date, subtotal, total_amount, paid_amount, currency, line_items, status) \
             OVERRIDING SYSTEM VALUE VALUES \
             ($1, 'INV-REG-997621', $3, $4, 'Rev Pipeline Guest', $5, 1000.00, 1000.00, 1000.00, 'MYR', '[]'::jsonb, 'paid'), \
             ($2, 'INV-REG-997622', $3, $4, 'Rev Pipeline Guest', $5, 500.00, 500.00, 200.00, 'MYR', '[]'::jsonb, 'issued')",
        )
        .bind(base + 21)
        .bind(base + 22)
        .bind(booking_id)
        .bind(guest_id)
        .bind(window_day)
        .execute(&pool)
        .await
        .unwrap();

        let after = RevenueRepository::pipeline(&pool, &range, today)
            .await
            .unwrap();
        assert_eq!(after.collected - baseline.collected, d("1000.00"));
        assert_eq!(after.outstanding - baseline.outstanding, d("300.00"));

        cleanup_band(&pool, base, base + 99).await;
    }

    #[tokio::test]
    async fn insights_overview_reconciles_revenue_today_and_7day_series() {
        let Some((pool, _guard)) = setup_pg_pool().await else {
            return;
        };
        let overview = insights_service::overview(&pool)
            .await
            .expect("insights overview must succeed");
        assert_eq!(overview.revenue_last_7_days.len(), 7);
        let last = overview
            .revenue_last_7_days
            .last()
            .expect("7-day slice is non-empty");
        assert_eq!(last.date, overview.business_date);
        assert!(
            (last.revenue - overview.revenue_today).abs() < 1e-6,
            "revenue_today ({}) must match last 7-day bucket ({})",
            overview.revenue_today,
            last.revenue
        );
    }
}
