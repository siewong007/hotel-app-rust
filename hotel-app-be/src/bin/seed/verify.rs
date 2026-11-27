//! Post-seed relational, financial, status, currency, and sequence verifier.
//! Runs inside the seed transaction before COMMIT (unless `--skip-verify`) so
//! broken fixtures or drifting triggers fail fast instead of silently persisting.

use crate::engine::{RESYNC_SEQUENCES, Tx};

/// Verify core seeded invariants across the 800000..899999 band and all
/// resynced identity sequences.
pub async fn verify_invariants(tx: &mut Tx<'_>) -> Result<(), anyhow::Error> {
    let orphan_payments: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM public.payments p \
         WHERE p.id BETWEEN 800000 AND 899999 \
           AND NOT EXISTS (SELECT 1 FROM public.bookings b WHERE b.id = p.booking_id)",
    )
    .fetch_one(&mut **tx)
    .await?;
    anyhow::ensure!(
        orphan_payments == 0,
        "post-seed check failed: {orphan_payments} orphan seeded payment(s)"
    );

    let orphan_invoices: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM public.invoices i \
         WHERE i.id BETWEEN 800000 AND 899999 \
           AND i.booking_id IS NOT NULL \
           AND NOT EXISTS (SELECT 1 FROM public.bookings b WHERE b.id = i.booking_id)",
    )
    .fetch_one(&mut **tx)
    .await?;
    anyhow::ensure!(
        orphan_invoices == 0,
        "post-seed check failed: {orphan_invoices} orphan seeded invoice(s)"
    );

    for table in [
        "bookings",
        "payments",
        "invoices",
        "promotions",
        "booking_groups",
    ] {
        let usd_rows: i64 = sqlx::query_scalar(sqlx::AssertSqlSafe(format!(
            "SELECT COUNT(*) FROM public.{table} WHERE id BETWEEN 800000 AND 899999 AND currency = 'USD'"
        )))
        .fetch_one(&mut **tx)
        .await?;
        anyhow::ensure!(
            usd_rows == 0,
            "post-seed check failed: public.{table} has {usd_rows} 'USD' row(s) in band"
        );
    }

    let mismatched_groups: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM public.booking_groups bg \
         WHERE bg.id BETWEEN 800000 AND 899999 \
           AND ( \
             bg.room_count <> (SELECT COUNT(*) FROM public.bookings b WHERE b.booking_group_id = bg.id) \
             OR bg.total_amount <> (SELECT COALESCE(SUM(b.total_amount), 0) FROM public.bookings b WHERE b.booking_group_id = bg.id) \
           )",
    )
    .fetch_one(&mut **tx)
    .await?;
    anyhow::ensure!(
        mismatched_groups == 0,
        "post-seed check failed: {mismatched_groups} booking_groups header(s) disagree with child bookings"
    );

    let invalid_paid_invoices: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM public.invoices i \
         WHERE i.id BETWEEN 800000 AND 899999 \
           AND i.status = 'paid' \
           AND (i.paid_at IS NULL OR COALESCE(i.paid_amount, 0) < COALESCE(i.total_amount, 0))",
    )
    .fetch_one(&mut **tx)
    .await?;
    anyhow::ensure!(
        invalid_paid_invoices == 0,
        "post-seed check failed: {invalid_paid_invoices} paid invoice(s) missing paid_at or full paid_amount"
    );

    let invalid_active_bookings: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM public.bookings b \
         WHERE b.id BETWEEN 800000 AND 899999 \
           AND b.status NOT IN ('voided', 'comp_void', 'no_show') \
           AND (b.check_out_date < b.check_in_date OR b.total_amount < 0)",
    )
    .fetch_one(&mut **tx)
    .await?;
    anyhow::ensure!(
        invalid_active_bookings == 0,
        "post-seed check failed: {invalid_active_bookings} active booking(s) have invalid date range or negative total"
    );

    let scale_collision_user_guests: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM public.user_guests WHERE id >= 850000")
            .fetch_one(&mut **tx)
            .await?;
    anyhow::ensure!(
        scale_collision_user_guests == 0,
        "post-seed check failed: {scale_collision_user_guests} user_guests row(s) encroach on 850000..899999 scale band"
    );

    for (table, seq) in RESYNC_SEQUENCES {
        let max_id: i64 = sqlx::query_scalar(sqlx::AssertSqlSafe(format!(
            "SELECT COALESCE(MAX(id), 0)::bigint FROM public.{table}"
        )))
        .fetch_one(&mut **tx)
        .await?;
        let (last_value, is_called): (i64, bool) = sqlx::query_as(sqlx::AssertSqlSafe(format!(
            "SELECT last_value, is_called FROM public.{seq}"
        )))
        .fetch_one(&mut **tx)
        .await?;
        let next_val = if is_called {
            last_value + 1
        } else {
            last_value
        };
        anyhow::ensure!(
            next_val > max_id,
            "post-seed check failed: sequence public.{seq} next_val ({next_val}) <= MAX({table}.id) ({max_id})"
        );
    }

    Ok(())
}
