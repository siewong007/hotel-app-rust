//! Invoice number generation
//!
//! Generates monotonically-increasing invoice numbers in the format
//! `INV-YYYYMM-XXXX` (4-digit zero-padded sequence, scoped to the current
//! month). The sequence is shared across the `invoices` and `customer_ledgers`
//! tables so a number issued for a checkout invoice never collides with one
//! issued for a city-ledger entry in the same month.

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

    let mut inserted = 0usize;
    for (booking_id, yyyymm) in rows {
        let prefix = format!("INV-{}-", yyyymm);
        let pattern = format!("{}%", prefix);
        let lock_key = format!("invoice_number:{yyyymm}");

        let result: Result<bool, ApiError> = async {
            let mut tx = pool.begin().await.map_err(ApiError::from)?;
            sqlx::query("SELECT pg_advisory_xact_lock(hashtextextended($1, 0))")
                .bind(&lock_key)
                .execute(&mut *tx)
                .await
                .map_err(ApiError::from)?;
            let already_has_invoice: bool =
                sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM invoices WHERE booking_id = $1)")
                    .bind(booking_id)
                    .fetch_one(&mut *tx)
                    .await
                    .map_err(ApiError::from)?;
            if already_has_invoice {
                tx.commit().await.map_err(ApiError::from)?;
                return Ok(false);
            }
            let max_seq = repo::max_invoice_sequence(&mut *tx, &pattern).await?;
            let next = max_seq.unwrap_or(0) + 1;
            let invoice_number = format!("{}{:04}", prefix, next);
            sqlx::query(
                r#"
                INSERT INTO invoices (
                    invoice_number, booking_id, billing_name, billing_email,
                    subtotal, tax_amount, discount_amount, total_amount,
                    line_items, status, invoice_type
                )
                SELECT $1, b.id,
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
                       b.total_amount + COALESCE(b.tourism_tax_amount, 0)
                           + COALESCE(b.extra_bed_charge, 0),
                       '[]'::jsonb,
                       'issued',
                       'booking'
                FROM bookings b
                INNER JOIN guests g ON b.guest_id = g.id
                WHERE b.id = $2
                "#,
            )
            .bind(&invoice_number)
            .bind(booking_id)
            .execute(&mut *tx)
            .await
            .map_err(ApiError::from)?;
            tx.commit().await.map_err(ApiError::from)?;
            Ok(true)
        }
        .await;

        match result {
            Ok(true) => inserted += 1,
            Ok(false) => {}
            Err(e) => {
                log::warn!(
                    "Failed to backfill invoice for booking {}: {}",
                    booking_id,
                    e
                );
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
