//! Rate plan business logic.

use super::repository::RateRepository;
use crate::core::db::DbPool;
use crate::core::error::ApiError;
use crate::models::AuditEvent;
use crate::models::{
    ApplicableRateQuery, BulkRoomRateInput, RatePlan, RatePlanCreateValues, RatePlanInput,
    RatePlanUpdateInput, RatePlanUpdateValues, RatePlanWithRates, RoomRate, RoomRateBulkValues,
    RoomRateCreateValues, RoomRateInput, RoomRateUpdateInput, RoomRateUpdateValues,
    RoomRateWithDetails, RoomType,
};
use crate::services::audit::AuditLog;
use chrono::{Datelike, NaiveDate};
use rust_decimal::Decimal;
use serde_json::json;

pub async fn create_rate_plan(
    pool: &DbPool,
    user_id: i64,
    input: RatePlanInput,
) -> Result<RatePlan, ApiError> {
    let values = rate_plan_create_values(input)?;
    let rate_plan = RateRepository::create_rate_plan(pool, user_id, &values).await?;
    log_rate_plan_event(pool, user_id, "rate_plan_created", &rate_plan).await;
    Ok(rate_plan)
}

pub async fn list_rate_plans(pool: &DbPool) -> Result<Vec<RatePlan>, ApiError> {
    RateRepository::list_rate_plans(pool).await
}

pub async fn get_rate_plan(pool: &DbPool, rate_plan_id: i64) -> Result<RatePlan, ApiError> {
    RateRepository::find_rate_plan(pool, rate_plan_id).await
}

pub async fn get_rate_plan_with_rates(
    pool: &DbPool,
    rate_plan_id: i64,
) -> Result<RatePlanWithRates, ApiError> {
    let rate_plan = RateRepository::find_rate_plan(pool, rate_plan_id).await?;
    let rates = RateRepository::room_rates_by_plan(pool, rate_plan_id).await?;
    Ok(RatePlanWithRates { rate_plan, rates })
}

pub async fn update_rate_plan(
    pool: &DbPool,
    user_id: i64,
    rate_plan_id: i64,
    input: RatePlanUpdateInput,
) -> Result<RatePlan, ApiError> {
    let values = rate_plan_update_values(input)?;
    if !has_rate_plan_updates(&values) {
        return Err(ApiError::BadRequest(
            "No valid fields to update".to_string(),
        ));
    }

    let existing = RateRepository::find_rate_plan(pool, rate_plan_id).await?;
    let merged_from = values.valid_from.or(existing.valid_from);
    let merged_to = values.valid_to.or(existing.valid_to);
    validate_optional_date_window(merged_from, merged_to, "valid_from", "valid_to")?;

    let rate_plan = RateRepository::update_rate_plan(pool, rate_plan_id, &values).await?;
    log_rate_plan_event(pool, user_id, "rate_plan_updated", &rate_plan).await;
    Ok(rate_plan)
}

pub async fn delete_rate_plan(
    pool: &DbPool,
    user_id: i64,
    rate_plan_id: i64,
) -> Result<(), ApiError> {
    let deleted = RateRepository::delete_rate_plan(pool, rate_plan_id).await?;
    log_rate_plan_event(pool, user_id, "rate_plan_deleted", &deleted).await;
    Ok(())
}

pub async fn create_room_rate(
    pool: &DbPool,
    user_id: i64,
    input: RoomRateInput,
) -> Result<RoomRate, ApiError> {
    let values = room_rate_create_values(input)?;
    let room_rate = RateRepository::create_room_rate(pool, &values).await?;
    log_room_rate_event(pool, user_id, "room_rate_created", &room_rate).await;
    Ok(room_rate)
}

pub async fn list_room_rates(pool: &DbPool) -> Result<Vec<RoomRateWithDetails>, ApiError> {
    RateRepository::list_room_rates(pool).await
}

pub async fn room_rates_by_plan(
    pool: &DbPool,
    rate_plan_id: i64,
) -> Result<Vec<RoomRateWithDetails>, ApiError> {
    RateRepository::room_rates_by_plan(pool, rate_plan_id).await
}

pub async fn get_room_rate(pool: &DbPool, rate_id: i64) -> Result<RoomRateWithDetails, ApiError> {
    RateRepository::find_room_rate(pool, rate_id).await
}

pub async fn update_room_rate(
    pool: &DbPool,
    user_id: i64,
    rate_id: i64,
    input: RoomRateUpdateInput,
) -> Result<RoomRate, ApiError> {
    let values = room_rate_update_values(input)?;
    if !has_room_rate_updates(&values) {
        return Err(ApiError::BadRequest(
            "No valid fields to update".to_string(),
        ));
    }

    let existing = RateRepository::find_room_rate(pool, rate_id).await?;
    let merged_from = values.effective_from.unwrap_or(existing.effective_from);
    let merged_to = values.effective_to.or(existing.effective_to);
    validate_optional_date_window(
        Some(merged_from),
        merged_to,
        "effective_from",
        "effective_to",
    )?;

    let room_rate = RateRepository::update_room_rate(pool, rate_id, &values).await?;
    log_room_rate_event(pool, user_id, "room_rate_updated", &room_rate).await;
    Ok(room_rate)
}

pub async fn delete_room_rate(pool: &DbPool, user_id: i64, rate_id: i64) -> Result<(), ApiError> {
    let deleted = RateRepository::delete_room_rate(pool, rate_id).await?;
    log_room_rate_event(pool, user_id, "room_rate_deleted", &deleted).await;
    Ok(())
}

pub async fn room_types_for_rates(pool: &DbPool) -> Result<Vec<RoomType>, ApiError> {
    RateRepository::active_room_types(pool).await
}

/// Bulk band upsert behind the rate calendar's bulk editor: one band per
/// room type, exact-bounds match repriced in place, otherwise inserted —
/// all inside one transaction with a single audit event.
pub async fn bulk_upsert_room_rates(
    pool: &DbPool,
    user_id: i64,
    input: BulkRoomRateInput,
) -> Result<Vec<RoomRate>, ApiError> {
    let values = room_rate_bulk_values(input)?;
    RateRepository::find_rate_plan(pool, values.rate_plan_id).await?;

    // Snapshot current band prices for the audit diff before the upsert
    // rewrites them — enrichment only, never fail the write for it.
    let prior_bands = RateRepository::room_rate_band_prices(
        pool,
        values.rate_plan_id,
        &values.room_type_ids,
        values.effective_from,
        Some(values.effective_to),
    )
    .await
    .unwrap_or_default();

    let mut tx = pool.begin().await.map_err(ApiError::from)?;
    let mut upserted = Vec::with_capacity(values.room_type_ids.len());
    for room_type_id in &values.room_type_ids {
        let band = RateRepository::upsert_room_rate_band_tx(
            &mut tx,
            &RoomRateCreateValues {
                rate_plan_id: values.rate_plan_id,
                room_type_id: *room_type_id,
                price: values.price,
                effective_from: values.effective_from,
                effective_to: Some(values.effective_to),
            },
        )
        .await?;
        upserted.push(band);
    }
    tx.commit().await.map_err(ApiError::from)?;

    // Per-band diff the audit UI renders as field changes: before carries the
    // prior price only where a band existed; after carries every written band.
    let new_price = values.price.to_string();
    let mut before = serde_json::Map::new();
    let mut after = serde_json::Map::new();
    for (_room_type_id, room_type_name, prior_price) in &prior_bands {
        let key = format!("{room_type_name} price");
        if let Some(price) = prior_price {
            before.insert(key.clone(), json!(price.to_string()));
        }
        after.insert(key, json!(new_price));
    }

    let _ = AuditLog::log_event(
        pool,
        AuditEvent {
            user_id: Some(user_id),
            action: "room_rates_bulk_upsert",
            resource_type: "rate_plan",
            resource_id: Some(values.rate_plan_id),
            details: Some(json!({
                "room_type_ids": values.room_type_ids,
                "effective_from": values.effective_from,
                "effective_to": values.effective_to,
                "price": values.price.to_string(),
                "bands_written": upserted.len(),
                "before": before,
                "after": after,
            })),
            ..Default::default()
        },
    )
    .await;
    Ok(upserted)
}

pub async fn applicable_rate(
    pool: &DbPool,
    query: ApplicableRateQuery,
) -> Result<serde_json::Value, ApiError> {
    let date = NaiveDate::parse_from_str(&query.date, "%Y-%m-%d")
        .map_err(|_| ApiError::BadRequest("Invalid date format. Use YYYY-MM-DD".to_string()))?;
    let day_of_week = date.weekday().num_days_from_monday() as i32;

    if let Some(rate) =
        RateRepository::applicable_rate(pool, query.room_type_id, date, day_of_week).await?
    {
        return Ok(json!(rate));
    }

    let room_type = RateRepository::find_room_type(pool, query.room_type_id).await?;
    Ok(json!({
        "rate_plan_code": "BASE",
        "rate_plan_name": "Base Rate",
        "room_type_id": room_type.id,
        "room_type_name": room_type.name,
        "room_type_code": room_type.code,
        "price": room_type.base_price,
        "is_base_rate": true
    }))
}

fn parse_opt_rate_date(value: Option<&String>, field: &str) -> Result<Option<NaiveDate>, ApiError> {
    match value.map(|s| s.trim()).filter(|s| !s.is_empty()) {
        Some(raw) => NaiveDate::parse_from_str(raw, "%Y-%m-%d")
            .map(Some)
            .map_err(|_| {
                ApiError::BadRequest(format!("Invalid {field} date format. Use YYYY-MM-DD"))
            }),
        None => Ok(None),
    }
}

fn validate_optional_date_window(
    from: Option<NaiveDate>,
    to: Option<NaiveDate>,
    from_label: &str,
    to_label: &str,
) -> Result<(), ApiError> {
    if let (Some(start), Some(end)) = (from, to)
        && start > end
    {
        return Err(ApiError::BadRequest(format!(
            "{from_label} must not be after {to_label}"
        )));
    }
    Ok(())
}

fn rate_plan_create_values(input: RatePlanInput) -> Result<RatePlanCreateValues, ApiError> {
    let valid_from = parse_opt_rate_date(input.valid_from.as_ref(), "valid_from")?;
    let valid_to = parse_opt_rate_date(input.valid_to.as_ref(), "valid_to")?;
    validate_optional_date_window(valid_from, valid_to, "valid_from", "valid_to")?;
    let adjustment_value = input
        .adjustment_value
        .map(|v| {
            Decimal::from_f64_retain(v)
                .ok_or_else(|| ApiError::BadRequest("Invalid adjustment_value".to_string()))
        })
        .transpose()?;

    Ok(RatePlanCreateValues {
        name: input.name,
        code: input.code,
        description: input.description,
        plan_type: input.plan_type,
        adjustment_type: input.adjustment_type,
        adjustment_value,
        valid_from,
        valid_to,
        applies_monday: input.applies_monday.unwrap_or(true),
        applies_tuesday: input.applies_tuesday.unwrap_or(true),
        applies_wednesday: input.applies_wednesday.unwrap_or(true),
        applies_thursday: input.applies_thursday.unwrap_or(true),
        applies_friday: input.applies_friday.unwrap_or(true),
        applies_saturday: input.applies_saturday.unwrap_or(true),
        applies_sunday: input.applies_sunday.unwrap_or(true),
        min_nights: input.min_nights.unwrap_or(1),
        max_nights: input.max_nights,
        min_advance_booking: input.min_advance_booking.unwrap_or(0),
        max_advance_booking: input.max_advance_booking,
        blackout_dates: input.blackout_dates,
        is_active: input.is_active.unwrap_or(true),
        priority: input.priority.unwrap_or(0),
    })
}

fn rate_plan_update_values(input: RatePlanUpdateInput) -> Result<RatePlanUpdateValues, ApiError> {
    let valid_from = parse_opt_rate_date(input.valid_from.as_ref(), "valid_from")?;
    let valid_to = parse_opt_rate_date(input.valid_to.as_ref(), "valid_to")?;
    validate_optional_date_window(valid_from, valid_to, "valid_from", "valid_to")?;
    let adjustment_value = input
        .adjustment_value
        .map(|v| {
            Decimal::from_f64_retain(v)
                .ok_or_else(|| ApiError::BadRequest("Invalid adjustment_value".to_string()))
        })
        .transpose()?;

    Ok(RatePlanUpdateValues {
        name: input.name,
        code: input.code,
        description: input.description,
        plan_type: input.plan_type,
        adjustment_type: input.adjustment_type,
        adjustment_value,
        valid_from,
        valid_to,
        applies_monday: input.applies_monday,
        applies_tuesday: input.applies_tuesday,
        applies_wednesday: input.applies_wednesday,
        applies_thursday: input.applies_thursday,
        applies_friday: input.applies_friday,
        applies_saturday: input.applies_saturday,
        applies_sunday: input.applies_sunday,
        min_nights: input.min_nights,
        max_nights: input.max_nights,
        min_advance_booking: input.min_advance_booking,
        max_advance_booking: input.max_advance_booking,
        is_active: input.is_active,
        priority: input.priority,
    })
}

fn has_rate_plan_updates(values: &RatePlanUpdateValues) -> bool {
    values.name.is_some()
        || values.code.is_some()
        || values.description.is_some()
        || values.plan_type.is_some()
        || values.adjustment_type.is_some()
        || values.adjustment_value.is_some()
        || values.valid_from.is_some()
        || values.valid_to.is_some()
        || values.applies_monday.is_some()
        || values.applies_tuesday.is_some()
        || values.applies_wednesday.is_some()
        || values.applies_thursday.is_some()
        || values.applies_friday.is_some()
        || values.applies_saturday.is_some()
        || values.applies_sunday.is_some()
        || values.min_nights.is_some()
        || values.max_nights.is_some()
        || values.min_advance_booking.is_some()
        || values.max_advance_booking.is_some()
        || values.is_active.is_some()
        || values.priority.is_some()
}

fn room_rate_create_values(input: RoomRateInput) -> Result<RoomRateCreateValues, ApiError> {
    let effective_from =
        NaiveDate::parse_from_str(&input.effective_from, "%Y-%m-%d").map_err(|_| {
            ApiError::BadRequest("Invalid effective_from date format. Use YYYY-MM-DD".to_string())
        })?;

    let effective_to = parse_opt_rate_date(input.effective_to.as_ref(), "effective_to")?;
    validate_optional_date_window(
        Some(effective_from),
        effective_to,
        "effective_from",
        "effective_to",
    )?;

    let price = Decimal::from_f64_retain(input.price)
        .filter(|p| p.is_sign_positive() && !p.is_zero())
        .ok_or_else(|| ApiError::BadRequest("price must be a positive number".to_string()))?;

    Ok(RoomRateCreateValues {
        rate_plan_id: input.rate_plan_id,
        room_type_id: input.room_type_id,
        price,
        effective_from,
        effective_to,
    })
}

fn room_rate_bulk_values(input: BulkRoomRateInput) -> Result<RoomRateBulkValues, ApiError> {
    let parse = |value: &str, label: &str| {
        NaiveDate::parse_from_str(value, "%Y-%m-%d")
            .map_err(|_| ApiError::BadRequest(format!("Invalid {label} date. Use YYYY-MM-DD")))
    };
    let (effective_from, effective_to) = (
        parse(&input.effective_from, "effective_from")?,
        parse(&input.effective_to, "effective_to")?,
    );
    if input.room_type_ids.is_empty() {
        return Err(ApiError::BadRequest(
            "room_type_ids must not be empty".to_string(),
        ));
    }
    if effective_from > effective_to {
        return Err(ApiError::BadRequest(
            "effective_from must not be after effective_to".to_string(),
        ));
    }
    let price = Decimal::from_f64_retain(input.price)
        .filter(|p| p.is_sign_positive() && !p.is_zero())
        .ok_or_else(|| ApiError::BadRequest("price must be a positive number".to_string()))?;
    Ok(RoomRateBulkValues {
        rate_plan_id: input.rate_plan_id,
        room_type_ids: input.room_type_ids,
        effective_from,
        effective_to,
        price,
    })
}

fn room_rate_update_values(input: RoomRateUpdateInput) -> Result<RoomRateUpdateValues, ApiError> {
    let price = input
        .price
        .map(|p| {
            Decimal::from_f64_retain(p)
                .filter(|d| d.is_sign_positive() && !d.is_zero())
                .ok_or_else(|| ApiError::BadRequest("price must be a positive number".to_string()))
        })
        .transpose()?;
    let effective_from = parse_opt_rate_date(input.effective_from.as_ref(), "effective_from")?;
    let effective_to = parse_opt_rate_date(input.effective_to.as_ref(), "effective_to")?;
    validate_optional_date_window(
        effective_from,
        effective_to,
        "effective_from",
        "effective_to",
    )?;

    Ok(RoomRateUpdateValues {
        price,
        effective_from,
        effective_to,
    })
}

fn has_room_rate_updates(values: &RoomRateUpdateValues) -> bool {
    values.price.is_some() || values.effective_from.is_some() || values.effective_to.is_some()
}

fn rate_plan_audit_details(rate_plan: &RatePlan) -> serde_json::Value {
    json!({
        "code": rate_plan.code,
        "name": rate_plan.name,
        "plan_type": rate_plan.plan_type,
        "adjustment_type": rate_plan.adjustment_type,
        "adjustment_value": rate_plan.adjustment_value.map(|value| value.to_string()),
        "valid_from": rate_plan.valid_from,
        "valid_to": rate_plan.valid_to,
        "is_active": rate_plan.is_active,
        "priority": rate_plan.priority,
    })
}

fn room_rate_audit_details(room_rate: &RoomRate) -> serde_json::Value {
    json!({
        "rate_plan_id": room_rate.rate_plan_id,
        "room_type_id": room_rate.room_type_id,
        "price": room_rate.price.to_string(),
        "effective_from": room_rate.effective_from,
        "effective_to": room_rate.effective_to,
    })
}

async fn log_rate_plan_event(pool: &DbPool, user_id: i64, action: &str, rate_plan: &RatePlan) {
    let _ = AuditLog::log_event(
        pool,
        AuditEvent {
            user_id: Some(user_id),
            action,
            resource_type: "rate_plan",
            resource_id: Some(rate_plan.id),
            details: Some(rate_plan_audit_details(rate_plan)),
            ..Default::default()
        },
    )
    .await;
}

async fn log_room_rate_event(pool: &DbPool, user_id: i64, action: &str, room_rate: &RoomRate) {
    let _ = AuditLog::log_event(
        pool,
        AuditEvent {
            user_id: Some(user_id),
            action,
            resource_type: "room_rate",
            resource_id: Some(room_rate.id),
            details: Some(room_rate_audit_details(room_rate)),
            ..Default::default()
        },
    )
    .await;
}

#[cfg(test)]
mod rate_validation_tests {
    use super::*;

    #[test]
    fn room_rate_create_rejects_bad_price_dates_and_window() {
        let bad_price = RoomRateInput {
            rate_plan_id: 1,
            room_type_id: 1,
            price: 0.0,
            effective_from: "2026-11-01".to_string(),
            effective_to: Some("2026-11-05".to_string()),
        };
        assert!(room_rate_create_values(bad_price).is_err());

        let bad_date = RoomRateInput {
            rate_plan_id: 1,
            room_type_id: 1,
            price: 150.0,
            effective_from: "2026-11-01".to_string(),
            effective_to: Some("not-a-date".to_string()),
        };
        assert!(room_rate_create_values(bad_date).is_err());

        let inverted = RoomRateInput {
            rate_plan_id: 1,
            room_type_id: 1,
            price: 150.0,
            effective_from: "2026-11-10".to_string(),
            effective_to: Some("2026-11-05".to_string()),
        };
        assert!(room_rate_create_values(inverted).is_err());
    }

    #[test]
    fn rate_plan_create_rejects_inverted_window_and_bad_dates() {
        let base = RatePlanInput {
            name: "Promo".to_string(),
            code: "PROMO".to_string(),
            description: None,
            plan_type: Some("seasonal".to_string()),
            adjustment_type: None,
            adjustment_value: None,
            valid_from: Some("2026-12-20".to_string()),
            valid_to: Some("2026-12-01".to_string()),
            applies_monday: None,
            applies_tuesday: None,
            applies_wednesday: None,
            applies_thursday: None,
            applies_friday: None,
            applies_saturday: None,
            applies_sunday: None,
            min_nights: None,
            max_nights: None,
            min_advance_booking: None,
            max_advance_booking: None,
            blackout_dates: None,
            is_active: None,
            priority: None,
        };
        assert!(rate_plan_create_values(base).is_err());
    }
}
