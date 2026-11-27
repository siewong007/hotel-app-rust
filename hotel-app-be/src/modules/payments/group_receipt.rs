//! Combined receipt for a multi-room booking group.
//!
//! Each room booking keeps its own invoice (numbering, ledgers and refunds stay
//! per booking); this endpoint only gathers those invoices into one document
//! with group totals, for a group that was paid together.

use crate::core::db::DbPool;
use crate::core::error::ApiError;
use chrono::NaiveDate;
use rust_decimal::Decimal;
use serde::Serialize;

#[derive(Debug, Clone, Serialize, sqlx::FromRow)]
pub struct GroupReceiptRow {
    pub booking_id: i64,
    pub booking_number: Option<String>,
    pub invoice_number: String,
    pub room_number: Option<String>,
    pub room_type: Option<String>,
    pub check_in_date: NaiveDate,
    pub check_out_date: NaiveDate,
    pub nights: i32,
    pub room_total: Decimal,
    pub tourism_tax_amount: Decimal,
    pub extra_bed_count: i32,
    pub extra_bed_charge: Decimal,
    pub total: Decimal,
}

#[derive(sqlx::FromRow)]
struct GroupHeader {
    group_number: String,
    guest_id: i64,
    guest_name: Option<String>,
    check_in_date: NaiveDate,
    check_out_date: NaiveDate,
    currency: String,
    payment_status: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct GroupReceipt {
    pub booking_group_id: i64,
    pub group_number: String,
    pub guest_id: i64,
    pub guest_name: Option<String>,
    pub check_in_date: NaiveDate,
    pub check_out_date: NaiveDate,
    pub currency: String,
    pub payment_status: String,
    pub group_payment_ref: Option<String>,
    pub rooms: Vec<GroupReceiptRow>,
    pub room_total: Decimal,
    pub tourism_tax_amount: Decimal,
    pub extra_bed_charge: Decimal,
    pub grand_total: Decimal,
}

/// Read an already-issued combined receipt for a booking group without minting
/// any missing invoices (safe for `payments:read`).
pub async fn group_receipt_read_only(
    pool: &DbPool,
    group_id: i64,
) -> Result<GroupReceipt, ApiError> {
    let (group, booking_ids, invoice_by_booking) =
        load_group_receipt_inputs(pool, group_id).await?;
    if booking_ids
        .iter()
        .any(|id| !invoice_by_booking.contains_key(id))
    {
        return Err(ApiError::NotFound(
            "Group receipt has not been issued yet".to_string(),
        ));
    }
    assemble_group_receipt(pool, group_id, group, &booking_ids, &invoice_by_booking).await
}

/// Build (and, where missing, issue the per-room invoices for) a group receipt.
pub async fn group_receipt(
    pool: &DbPool,
    user_id: i64,
    group_id: i64,
) -> Result<GroupReceipt, ApiError> {
    let (group, booking_ids, mut invoice_by_booking) =
        load_group_receipt_inputs(pool, group_id).await?;

    for &booking_id in &booking_ids {
        if let std::collections::hash_map::Entry::Vacant(e) = invoice_by_booking.entry(booking_id) {
            let invoice_number =
                super::service::ensure_invoice_for_booking(pool, booking_id, user_id).await?;
            e.insert(invoice_number);
        }
    }

    assemble_group_receipt(pool, group_id, group, &booking_ids, &invoice_by_booking).await
}

async fn load_group_receipt_inputs(
    pool: &DbPool,
    group_id: i64,
) -> Result<
    (
        GroupHeader,
        Vec<i64>,
        std::collections::HashMap<i64, String>,
    ),
    ApiError,
> {
    let group: Option<GroupHeader> = sqlx::query_as(
        "SELECT bg.group_number, bg.guest_id, g.nick_name AS guest_name, bg.check_in_date, \
         bg.check_out_date, bg.currency, bg.payment_status \
         FROM booking_groups bg LEFT JOIN guests g ON g.id = bg.guest_id \
         WHERE bg.id = $1",
    )
    .bind(group_id)
    .fetch_optional(pool)
    .await
    .map_err(ApiError::from)?;
    let Some(group) = group else {
        return Err(ApiError::NotFound("Booking group not found".to_string()));
    };

    let booking_ids: Vec<i64> = sqlx::query_scalar(
        "SELECT id FROM bookings WHERE booking_group_id = $1 \
         AND COALESCE(status, '') <> 'voided' ORDER BY id",
    )
    .bind(group_id)
    .fetch_all(pool)
    .await
    .map_err(ApiError::from)?;
    if booking_ids.is_empty() {
        return Err(ApiError::NotFound(
            "This booking group has no active room bookings".to_string(),
        ));
    }

    let existing_rows: Vec<(i64, String)> = sqlx::query_as(
        "SELECT DISTINCT ON (booking_id) booking_id, invoice_number \
         FROM invoices \
         WHERE booking_id = ANY($1) \
           AND invoice_number IS NOT NULL \
           AND COALESCE(status, '') NOT IN ('void', 'voided', 'cancelled') \
         ORDER BY booking_id, created_at DESC, id DESC",
    )
    .bind(&booking_ids)
    .fetch_all(pool)
    .await
    .map_err(ApiError::from)?;
    let invoice_by_booking: std::collections::HashMap<i64, String> =
        existing_rows.into_iter().collect();

    Ok((group, booking_ids, invoice_by_booking))
}

async fn assemble_group_receipt(
    pool: &DbPool,
    group_id: i64,
    group: GroupHeader,
    booking_ids: &[i64],
    invoice_by_booking: &std::collections::HashMap<i64, String>,
) -> Result<GroupReceipt, ApiError> {
    let invoice_numbers: Vec<String> = booking_ids
        .iter()
        .map(|id| invoice_by_booking.get(id).cloned().unwrap_or_default())
        .collect();
    let rooms: Vec<GroupReceiptRow> = sqlx::query_as(
        "SELECT b.id AS booking_id, b.booking_number, u.invoice_number, \
         r.room_number, rt.name AS room_type, b.check_in_date, b.check_out_date, \
         (b.check_out_date - b.check_in_date)::int AS nights, \
         b.total_amount AS room_total, \
         COALESCE(b.tourism_tax_amount, 0) AS tourism_tax_amount, \
         COALESCE(b.extra_bed_count, 0)::int AS extra_bed_count, \
         COALESCE(b.extra_bed_charge, 0) AS extra_bed_charge, \
         b.total_amount + COALESCE(b.tourism_tax_amount, 0) \
           + COALESCE(b.extra_bed_charge, 0) AS total \
         FROM UNNEST($1::bigint[], $2::text[]) WITH ORDINALITY AS u(booking_id, invoice_number, ord) \
         JOIN bookings b ON b.id = u.booking_id \
         LEFT JOIN rooms r ON r.id = b.room_id \
         LEFT JOIN room_types rt ON rt.id = r.room_type_id \
         ORDER BY u.ord",
    )
    .bind(booking_ids)
    .bind(&invoice_numbers)
    .fetch_all(pool)
    .await
    .map_err(ApiError::from)?;

    let group_payment_ref: Option<String> = sqlx::query_scalar(
        "SELECT p.metadata->>'group_payment_ref' FROM payments p \
         JOIN bookings b ON b.id = p.booking_id \
         WHERE b.booking_group_id = $1 AND p.metadata ? 'group_payment_ref' \
         ORDER BY (p.status = 'completed') DESC, p.id DESC LIMIT 1",
    )
    .bind(group_id)
    .fetch_optional(pool)
    .await
    .map_err(ApiError::from)?
    .flatten();

    let sum = |f: fn(&GroupReceiptRow) -> Decimal| rooms.iter().map(f).sum::<Decimal>();
    Ok(GroupReceipt {
        booking_group_id: group_id,
        group_number: group.group_number,
        guest_id: group.guest_id,
        guest_name: group.guest_name,
        check_in_date: group.check_in_date,
        check_out_date: group.check_out_date,
        currency: group.currency,
        payment_status: group.payment_status,
        group_payment_ref,
        room_total: sum(|r| r.room_total),
        tourism_tax_amount: sum(|r| r.tourism_tax_amount),
        extra_bed_charge: sum(|r| r.extra_bed_charge),
        grand_total: sum(|r| r.total),
        rooms,
    })
}
