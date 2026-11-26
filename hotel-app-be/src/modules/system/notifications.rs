//! Actionable staff notification metadata derivation, deduped producers,
//! realtime WebSocket fan-out, and background Support SLA sweeping.

use serde_json::json;
use sqlx::Row;

use crate::core::db::{DbPool, DbTransaction};
use crate::core::error::ApiError;
use crate::core::job_runs;

/// Normalized payload to insert a row into `staff_notifications`.
pub struct StaffNotificationDraft<'a> {
    pub audience_permission: &'a str,
    pub kind: &'a str,
    pub subject: Option<&'a str>,
    pub title: &'a str,
    pub body: Option<&'a str>,
}

/// Derived UX & routing metadata computed without changing the DB schema.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct NotificationMeta {
    pub priority: &'static str,
    pub category: &'static str,
    pub action_url: Option<String>,
    pub action_label_key: Option<&'static str>,
    pub requires_action: bool,
}

fn encode_query_value(raw: &str) -> String {
    let mut out = String::with_capacity(raw.len());
    for byte in raw.as_bytes() {
        if byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_' | b'.' | b'~') {
            out.push(*byte as char);
        } else {
            out.push('%');
            out.push_str(&format!("{byte:02X}"));
        }
    }
    out
}

fn category_for_permission(audience_permission: &str) -> &'static str {
    match audience_permission.split(':').next().unwrap_or("") {
        "payments" => "payments",
        "support" => "support",
        "bookings" => "bookings",
        "ekyc" => "ekyc",
        "maintenance" => "maintenance",
        "housekeeping" => "housekeeping",
        _ => "system",
    }
}

/// Derive priority, category, deep-link URL, and CTA label key for a persisted
/// `staff_notifications` row.
pub fn derive_meta(
    audience_permission: &str,
    kind: &str,
    subject: Option<&str>,
    title: &str,
) -> NotificationMeta {
    let clean_subject = subject.map(str::trim).filter(|s| !s.is_empty());
    match kind {
        "bank_transfer_pending" => NotificationMeta {
            priority: "urgent",
            category: "payments",
            action_url: Some("/payment-approvals".to_string()),
            action_label_key: Some("reviewPayment"),
            requires_action: true,
        },
        "payment_receipt_uploaded" => NotificationMeta {
            priority: "urgent",
            category: "payments",
            action_url: Some("/payment-approvals".to_string()),
            action_label_key: Some("reviewReceipt"),
            requires_action: true,
        },
        "guest_support_conversation" => {
            let is_cancellation = title.trim().eq_ignore_ascii_case("Cancellation request");
            let action_url = match clean_subject {
                Some(conv) => Some(format!("/support?search={}", encode_query_value(conv))),
                None => Some("/support".to_string()),
            };
            NotificationMeta {
                priority: if is_cancellation { "urgent" } else { "high" },
                category: "support",
                action_url,
                action_label_key: Some(if is_cancellation {
                    "reviewCancellation"
                } else {
                    "replyGuest"
                }),
                requires_action: true,
            }
        }
        "guest_support_message" => {
            let conv = clean_subject
                .and_then(|s| s.split(':').next())
                .map(str::trim)
                .filter(|s| !s.is_empty());
            let action_url = match conv {
                Some(number) => Some(format!("/support?search={}", encode_query_value(number))),
                None => Some("/support".to_string()),
            };
            NotificationMeta {
                priority: "high",
                category: "support",
                action_url,
                action_label_key: Some("replyGuest"),
                requires_action: true,
            }
        }
        "support_sla_breached" => {
            let conv = clean_subject
                .and_then(|s| s.split(':').next())
                .map(str::trim)
                .filter(|s| !s.is_empty());
            let action_url = match conv {
                Some(number) => Some(format!(
                    "/support?queue=at_risk&search={}",
                    encode_query_value(number)
                )),
                None => Some("/support?queue=at_risk".to_string()),
            };
            NotificationMeta {
                priority: "urgent",
                category: "support",
                action_url,
                action_label_key: Some("handleSlaBreach"),
                requires_action: true,
            }
        }
        "support_sla_at_risk" => {
            let conv = clean_subject
                .and_then(|s| s.split(':').next())
                .map(str::trim)
                .filter(|s| !s.is_empty());
            let action_url = match conv {
                Some(number) => Some(format!(
                    "/support?queue=at_risk&search={}",
                    encode_query_value(number)
                )),
                None => Some("/support?queue=at_risk".to_string()),
            };
            NotificationMeta {
                priority: "high",
                category: "support",
                action_url,
                action_label_key: Some("replyBeforeSla"),
                requires_action: true,
            }
        }
        "ekyc_pending_review" => {
            let action_url = match clean_subject {
                Some(id) if id.bytes().all(|b| b.is_ascii_digit()) => {
                    Some(format!("/ekyc-admin?application_id={id}"))
                }
                _ => Some("/ekyc-admin".to_string()),
            };
            NotificationMeta {
                priority: "high",
                category: "ekyc",
                action_url,
                action_label_key: Some("reviewEkyc"),
                requires_action: true,
            }
        }
        "maintenance_critical" => NotificationMeta {
            priority: "urgent",
            category: "maintenance",
            action_url: Some("/housekeeping?tab=maintenance".to_string()),
            action_label_key: Some("openMaintenance"),
            requires_action: true,
        },
        "maintenance_high" => NotificationMeta {
            priority: "high",
            category: "maintenance",
            action_url: Some("/housekeeping?tab=maintenance".to_string()),
            action_label_key: Some("openMaintenance"),
            requires_action: true,
        },
        "online_booking_created" => {
            let action_url = match clean_subject {
                Some(booking_number) => Some(format!(
                    "/bookings?search={}",
                    encode_query_value(booking_number)
                )),
                None => Some("/bookings".to_string()),
            };
            NotificationMeta {
                priority: "normal",
                category: "bookings",
                action_url,
                action_label_key: Some("viewBooking"),
                requires_action: false,
            }
        }
        "job_failure" => NotificationMeta {
            priority: "urgent",
            category: "system",
            action_url: Some("/jobs".to_string()),
            action_label_key: Some("inspectJobs"),
            requires_action: true,
        },
        _ => NotificationMeta {
            priority: "normal",
            category: category_for_permission(audience_permission),
            action_url: None,
            action_label_key: None,
            requires_action: false,
        },
    }
}

/// Broadcast a domain change over local WebSockets and cross-replica LISTEN/NOTIFY.
/// Safe no-op in tests where no WebSocket hub has been registered.
pub fn publish_domain_changed(pool: &DbPool, domain: &'static str) {
    if let Some(hub) = crate::modules::realtime::hub::DataChangeHub::for_fanout(pool.clone()) {
        hub.publish_data_changed(domain);
    }
}

/// Notify connected staff clients that the notification feed changed.
pub fn publish_notifications_changed(pool: &DbPool) {
    publish_domain_changed(pool, "notifications");
}

/// Insert a staff notification inside an open transaction, deduplicating
/// recent identical `(kind, subject)` rows within a 6-hour window.
pub async fn notify_staff_tx(
    tx: &mut DbTransaction<'_>,
    draft: StaffNotificationDraft<'_>,
) -> Result<bool, ApiError> {
    let rows_affected = if let Some(subject) = draft.subject {
        sqlx::query(
            "INSERT INTO staff_notifications
                (audience_permission, kind, subject, title, body)
             SELECT $1, $2, $3, $4, $5
             WHERE NOT EXISTS (
                SELECT 1 FROM staff_notifications
                WHERE kind = $2 AND subject = $3
                  AND created_at > CURRENT_TIMESTAMP - INTERVAL '6 hours'
             )",
        )
        .bind(draft.audience_permission)
        .bind(draft.kind)
        .bind(subject)
        .bind(draft.title)
        .bind(draft.body)
        .execute(&mut **tx)
        .await
        .map_err(ApiError::from)?
        .rows_affected()
    } else {
        sqlx::query(
            "INSERT INTO staff_notifications
                (audience_permission, kind, subject, title, body)
             VALUES ($1, $2, NULL, $3, $4)",
        )
        .bind(draft.audience_permission)
        .bind(draft.kind)
        .bind(draft.title)
        .bind(draft.body)
        .execute(&mut **tx)
        .await
        .map_err(ApiError::from)?
        .rows_affected()
    };
    Ok(rows_affected > 0)
}

/// Best-effort post-commit notification writer. Safe on pre-patch databases
/// lacking `staff_notifications`, deduplicates recent `(kind, subject)` rows,
/// and broadcasts `"notifications"` immediately if a row was inserted.
pub async fn notify_staff(pool: &DbPool, draft: StaffNotificationDraft<'_>) -> bool {
    if !job_runs::table_present(pool).await {
        return false;
    }
    let result = if let Some(subject) = draft.subject {
        sqlx::query(
            "INSERT INTO staff_notifications
                (audience_permission, kind, subject, title, body)
             SELECT $1, $2, $3, $4, $5
             WHERE NOT EXISTS (
                SELECT 1 FROM staff_notifications
                WHERE kind = $2 AND subject = $3
                  AND created_at > CURRENT_TIMESTAMP - INTERVAL '6 hours'
             )",
        )
        .bind(draft.audience_permission)
        .bind(draft.kind)
        .bind(subject)
        .bind(draft.title)
        .bind(draft.body)
        .execute(pool)
        .await
    } else {
        sqlx::query(
            "INSERT INTO staff_notifications
                (audience_permission, kind, subject, title, body)
             VALUES ($1, $2, NULL, $3, $4)",
        )
        .bind(draft.audience_permission)
        .bind(draft.kind)
        .bind(draft.title)
        .bind(draft.body)
        .execute(pool)
        .await
    };

    match result {
        Ok(done) => {
            if done.rows_affected() > 0 {
                publish_notifications_changed(pool);
                true
            } else {
                false
            }
        }
        Err(error) => {
            log::warn!(
                "Failed to write staff notification (kind={}): {error}",
                draft.kind
            );
            false
        }
    }
}

/// Sweep open support conversations whose active response/resolution SLA is
/// breached or within 30 minutes of breach, creating deduplicated alerts for
/// `support:read` holders.
pub async fn sweep_support_sla_notifications(pool: &DbPool) -> Result<u64, ApiError> {
    if !job_runs::table_present(pool).await {
        return Ok(0);
    }

    let rows = sqlx::query(
        r#"
        SELECT
            c.conversation_number,
            COALESCE(g.nick_name, trim(g.first_name || ' ' || g.last_name), 'Guest') AS guest_name,
            (active_due <= CURRENT_TIMESTAMP) AS is_breached
        FROM (
            SELECT
                id,
                conversation_number,
                guest_id,
                CASE
                    WHEN first_response_at IS NULL AND status = 'waiting_for_staff'
                        THEN first_response_due_at
                    WHEN resolved_at IS NULL AND status NOT IN ('waiting_for_guest', 'resolved', 'closed')
                        THEN resolution_due_at
                    ELSE NULL
                END AS active_due
            FROM support_conversations
        ) c
        JOIN guests g ON g.id = c.guest_id
        WHERE c.active_due IS NOT NULL
          AND c.active_due <= CURRENT_TIMESTAMP + INTERVAL '30 minutes'
        ORDER BY c.active_due ASC
        LIMIT 100
        "#,
    )
    .fetch_all(pool)
    .await
    .map_err(ApiError::from)?;

    let mut inserted: u64 = 0;
    for row in rows {
        let number: String = row.get("conversation_number");
        let guest_name: String = row.get("guest_name");
        let is_breached: bool = row.get("is_breached");
        let (kind, subject, title, body) = if is_breached {
            (
                "support_sla_breached",
                format!("{number}:breached"),
                "Support SLA breached",
                format!("Conversation {number} ({guest_name}) has breached its response SLA."),
            )
        } else {
            (
                "support_sla_at_risk",
                format!("{number}:at_risk"),
                "Support SLA at risk",
                format!("Conversation {number} ({guest_name}) is due within 30 minutes."),
            )
        };

        let rows_affected = sqlx::query(
            "INSERT INTO staff_notifications
                (audience_permission, kind, subject, title, body)
             SELECT 'support:read', $1, $2, $3, $4
             WHERE NOT EXISTS (
                SELECT 1 FROM staff_notifications
                WHERE kind = $1 AND subject = $2
             )",
        )
        .bind(kind)
        .bind(&subject)
        .bind(title)
        .bind(&body)
        .execute(pool)
        .await
        .map_err(ApiError::from)?
        .rows_affected();

        inserted += rows_affected;
    }

    if inserted > 0 {
        publish_notifications_changed(pool);
        publish_domain_changed(pool, "support");
        log::info!(
            "Support SLA sweep generated {inserted} staff notification(s): {}",
            json!({ "inserted": inserted })
        );
    }

    Ok(inserted)
}

#[cfg(test)]
mod tests {
    use super::derive_meta;

    #[test]
    fn derive_meta_routes_payment_and_receipt_alerts() {
        let bank = derive_meta(
            "payments:read",
            "bank_transfer_pending",
            Some("BK-101"),
            "Payment pending approval",
        );
        assert_eq!(bank.priority, "urgent");
        assert_eq!(bank.category, "payments");
        assert_eq!(bank.action_url.as_deref(), Some("/payment-approvals"));
        assert_eq!(bank.action_label_key, Some("reviewPayment"));
        assert!(bank.requires_action);

        let receipt = derive_meta(
            "payments:read",
            "payment_receipt_uploaded",
            Some("BK-102"),
            "Payment receipt uploaded",
        );
        assert_eq!(receipt.priority, "urgent");
        assert_eq!(receipt.action_label_key, Some("reviewReceipt"));
    }

    #[test]
    fn derive_meta_distinguishes_cancellation_and_splits_message_subjects() {
        let cancel = derive_meta(
            "support:read",
            "guest_support_conversation",
            Some("SUP-2026-001"),
            "Cancellation request",
        );
        assert_eq!(cancel.priority, "urgent");
        assert_eq!(cancel.action_label_key, Some("reviewCancellation"));
        assert_eq!(
            cancel.action_url.as_deref(),
            Some("/support?search=SUP-2026-001")
        );

        let msg = derive_meta(
            "support:read",
            "guest_support_message",
            Some("SUP-2026-002:client msg&1"),
            "New guest support message",
        );
        assert_eq!(msg.priority, "high");
        assert_eq!(
            msg.action_url.as_deref(),
            Some("/support?search=SUP-2026-002")
        );

        let breach = derive_meta(
            "support:read",
            "support_sla_breached",
            Some("SUP-2026-003:breached"),
            "Support SLA breached",
        );
        assert_eq!(breach.priority, "urgent");
        assert_eq!(
            breach.action_url.as_deref(),
            Some("/support?queue=at_risk&search=SUP-2026-003")
        );
        assert_eq!(breach.action_label_key, Some("handleSlaBreach"));
    }

    #[test]
    fn derive_meta_handles_ekyc_maintenance_bookings_and_jobs() {
        let ekyc = derive_meta(
            "ekyc:read",
            "ekyc_pending_review",
            Some("42"),
            "eKYC awaiting review",
        );
        assert_eq!(ekyc.priority, "high");
        assert_eq!(ekyc.category, "ekyc");
        assert_eq!(
            ekyc.action_url.as_deref(),
            Some("/ekyc-admin?application_id=42")
        );

        let maint = derive_meta(
            "maintenance:read",
            "maintenance_critical",
            Some("MT-202611-0005"),
            "Critical maintenance ticket",
        );
        assert_eq!(maint.priority, "urgent");
        assert_eq!(
            maint.action_url.as_deref(),
            Some("/housekeeping?tab=maintenance")
        );

        let booking = derive_meta(
            "bookings:read",
            "online_booking_created",
            Some("BK 99/1"),
            "New online booking",
        );
        assert_eq!(booking.priority, "normal");
        assert!(!booking.requires_action);
        assert_eq!(
            booking.action_url.as_deref(),
            Some("/bookings?search=BK%2099%2F1")
        );

        let fallback = derive_meta("housekeeping:read", "custom_notice", None, "Custom");
        assert_eq!(fallback.category, "housekeeping");
        assert_eq!(fallback.priority, "normal");
        assert_eq!(fallback.action_url, None);
    }
}
