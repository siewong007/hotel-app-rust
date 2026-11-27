//! Invoice number generation
//!
//! Generates monotonically-increasing invoice numbers in the format
//! `INV-YYYYMM-XXXX` (4-digit zero-padded sequence, scoped to the current
//! month). The sequence is shared across the `invoices` and `customer_ledgers`
//! tables so a number issued for a checkout invoice never collides with one
//! issued for a city-ledger entry in the same month.

use std::collections::BTreeMap;

use crate::core::db::{DbPool, DbTransaction};
use crate::core::error::ApiError;
use crate::repositories::invoice_numbers as repo;

/// Allocate the next invoice number inside an active transaction, holding a
/// per-month PostgreSQL advisory xact lock so concurrent invoice generations
/// or city-ledger posts cannot collide on `MAX + 1`.
pub async fn next_invoice_number_tx(tx: &mut DbTransaction<'_>) -> Result<String, ApiError> {
    sqlx::query("SELECT pg_advisory_xact_lock(hashtextextended('invoice_number:' || TO_CHAR(CURRENT_DATE, 'YYYYMM'), 0))")
        .execute(&mut **tx)
        .await
        .map_err(ApiError::from)?;
    next_invoice_number(&mut **tx).await
}

/// Compute the next invoice number for the current month.
///
/// Format: `INV-YYYYMM-XXXX` (e.g. `INV-202604-0001`). `YYYYMM` is the hotel
/// business month — `CURRENT_DATE` under the per-connection session timezone —
/// computed in the same SQL statement as the sequence scan so the single-use
/// executor is consumed exactly once.
pub async fn next_invoice_number<'e, E>(executor: E) -> Result<String, ApiError>
where
    E: sqlx::Executor<'e, Database = crate::core::db::DbDatabase>,
{
    let (yyyymm, max_seq) = repo::current_month_max_invoice_sequence(executor).await?;
    let next = max_seq.unwrap_or(0) + 1;
    Ok(format!("INV-{}-{:04}", yyyymm, next))
}

/// Backfill invoice rows for any eligible booking that doesn't yet have one.
///
/// Each backfilled invoice gets a number in the new `INV-YYYYMM-XXXX` format,
/// where `YYYYMM` is derived from the booking's `created_at`. Sequence numbers
/// continue from whatever already exists for that month, so this is safe to
/// run repeatedly — it only inserts where no invoice row exists. Takes the
/// per-month advisory lock per month to stay race-free against concurrent
/// checkout/city-ledger allocations.
///
/// Returns the number of invoices created.
pub async fn backfill_missing_booking_invoices(pool: &DbPool) -> Result<usize, ApiError> {
    let rows = repo::bookings_missing_invoices(pool).await?;
    if rows.is_empty() {
        return Ok(0);
    }

    // Group ordered booking IDs by YYYYMM so each month executes one locked
    // set-based INSERT instead of opening thousands of serial transactions at boot.
    let mut by_month: BTreeMap<String, Vec<i64>> = BTreeMap::new();
    for (booking_id, yyyymm) in rows {
        by_month.entry(yyyymm).or_default().push(booking_id);
    }

    let mut inserted = 0usize;
    for (yyyymm, booking_ids) in by_month {
        let prefix = format!("INV-{}-", yyyymm);
        let pattern = format!("{}%", prefix);
        let lock_key = format!("invoice_number:{yyyymm}");

        let result: Result<u64, ApiError> = async {
            let mut tx = pool.begin().await.map_err(ApiError::from)?;
            sqlx::query("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))")
                .bind(&lock_key)
                .execute(&mut *tx)
                .await
                .map_err(ApiError::from)?;
            let max_seq = repo::max_invoice_sequence(&mut *tx, &pattern)
                .await?
                .unwrap_or(0);
            let res = sqlx::query(
                r#"
                WITH target AS (
                    SELECT u.booking_id, u.ord
                    FROM UNNEST($1::bigint[]) WITH ORDINALITY AS u(booking_id, ord)
                    INNER JOIN bookings b ON b.id = u.booking_id
                    WHERE NOT EXISTS (SELECT 1 FROM invoices i WHERE i.booking_id = b.id)
                ),
                numbered AS (
                    SELECT t.booking_id,
                           $2 || CASE
                               WHEN ($3 + ROW_NUMBER() OVER (ORDER BY t.ord)) < 10000
                               THEN LPAD(($3 + ROW_NUMBER() OVER (ORDER BY t.ord))::text, 4, '0')
                               ELSE ($3 + ROW_NUMBER() OVER (ORDER BY t.ord))::text
                           END AS invoice_number
                    FROM target t
                ),
                booking_paid AS (
                    SELECT b.id AS booking_id,
                           b.total_amount + COALESCE(b.tourism_tax_amount, 0) + COALESCE(b.extra_bed_charge, 0) AS invoice_total,
                           COALESCE(
                               bk_charge.paid_amount,
                               GREATEST(
                                   COALESCE(SUM(p.amount) FILTER (
                                       WHERE p.status = 'completed'
                                         AND COALESCE(p.payment_type, 'booking') NOT IN ('refund', 'deposit', 'deposit_forfeited', 'service', 'damage')
                                   ), 0)
                                   - COALESCE(SUM(p.amount) FILTER (
                                       WHERE p.status <> 'void'
                                         AND COALESCE(p.payment_type, 'booking') = 'refund'
                                         AND COALESCE(p.notes, '') LIKE 'Room charge refunded%'
                                   ), 0),
                                   0
                               )
                           ) AS completed_paid
                    FROM bookings b
                    INNER JOIN numbered n ON n.booking_id = b.id
                    LEFT JOIN LATERAL (
                        SELECT cl.paid_amount
                        FROM customer_ledgers cl
                        WHERE cl.booking_id = b.id
                          AND cl.post_type = 'room_charge'
                          AND cl.status <> 'void'
                          AND cl.void_at IS NULL
                          AND COALESCE(cl.is_reversal, FALSE) = FALSE
                        ORDER BY cl.created_at DESC
                        LIMIT 1
                    ) bk_charge ON TRUE
                    LEFT JOIN payments p ON p.booking_id = b.id
                    GROUP BY b.id, b.total_amount, b.tourism_tax_amount, b.extra_bed_charge, bk_charge.paid_amount
                )
                INSERT INTO invoices (
                    invoice_number, booking_id, bill_to_guest_id, billing_name, billing_email,
                    subtotal, tax_amount, discount_amount, total_amount, paid_amount,
                    line_items, status, invoice_type
                )
                SELECT n.invoice_number,
                       b.id,
                       b.guest_id,
                       CASE
                           WHEN NULLIF(BTRIM(g.first_name), '') IS NOT NULL
                            AND NULLIF(BTRIM(g.last_name), '') IS NOT NULL
                           THEN BTRIM(g.first_name) || ' ' || BTRIM(g.last_name)
                           ELSE COALESCE(BTRIM(g.nick_name), '')
                       END,
                       g.email,
                       COALESCE(b.subtotal, b.total_amount + COALESCE(b.discount_amount, 0))
                           + COALESCE(b.extra_bed_charge, 0),
                       COALESCE(b.tourism_tax_amount, 0),
                       COALESCE(b.discount_amount, 0),
                       bp.invoice_total,
                       LEAST(GREATEST(bp.completed_paid, 0), GREATEST(bp.invoice_total, 0)),
                       '[]'::jsonb,
                       CASE
                           WHEN bp.invoice_total <= 0 OR bp.completed_paid >= bp.invoice_total
                           THEN 'paid'
                           ELSE 'issued'
                       END,
                       'booking'
                FROM numbered n
                INNER JOIN bookings b ON b.id = n.booking_id
                INNER JOIN guests g ON b.guest_id = g.id
                INNER JOIN booking_paid bp ON bp.booking_id = b.id
                ORDER BY n.invoice_number
                "#,
            )
            .bind(&booking_ids)
            .bind(&prefix)
            .bind(max_seq)
            .execute(&mut *tx)
            .await
            .map_err(ApiError::from)?;
            tx.commit().await.map_err(ApiError::from)?;
            Ok(res.rows_affected())
        }
        .await;

        match result {
            Ok(count) => inserted += count as usize,
            Err(e) => {
                log::warn!("Failed to backfill invoices for month {}: {}", yyyymm, e);
            }
        }
    }

    Ok(inserted)
}

/// Backfill `customer_ledgers.due_date` for any rows where it's NULL.
///
/// Uses the linked company's `payment_terms_days` (falling back to
/// `default_payment_terms_days`) and adds it to the row's
/// `posting_date`/`invoice_date`/`created_at` (in that order of preference).
/// Idempotent — only touches rows where `due_date IS NULL`.
pub async fn backfill_missing_ledger_due_dates(pool: &DbPool) -> Result<usize, ApiError> {
    repo::backfill_ledger_due_dates(pool).await
}
