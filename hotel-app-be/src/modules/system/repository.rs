//! Read queries for the operational-health surfaces. All tolerant of a
//! `job_runs` table that does not exist yet (pre-patch databases): callers
//! gate on [`table_present`].

use sqlx::{FromRow, Row};

use crate::core::db::DbPool;
use crate::core::error::ApiError;
use crate::core::job_runs;
use crate::modules::system::models::{
    EmailQueueHealth, JobHealth, JobRunRow, StaffNotificationItem,
};
use crate::modules::system::notifications;

/// Same probe the writer uses — false until `job_runs` exists on this
/// database. Re-exported so the service shares one source of truth.
pub async fn table_present(pool: &DbPool) -> bool {
    job_runs::table_present(pool).await
}

/// Latest run per job plus 24-hour counters.
pub async fn latest_per_job(pool: &DbPool) -> Result<Vec<JobHealth>, ApiError> {
    let rows = sqlx::query(
        "SELECT DISTINCT ON (r.job_name)
                r.job_name, r.status, r.detail, r.error, r.duration_ms, r.created_at,
                (SELECT COUNT(*) FROM job_runs j
                  WHERE j.job_name = r.job_name
                    AND j.created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours') AS runs_24h,
                (SELECT COUNT(*) FROM job_runs j
                  WHERE j.job_name = r.job_name AND j.status = 'error'
                    AND j.created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours') AS failures_24h
         FROM job_runs r
         ORDER BY r.job_name, r.created_at DESC",
    )
    .fetch_all(pool)
    .await
    .map_err(ApiError::from)?;

    Ok(rows
        .into_iter()
        .map(|row| JobHealth {
            job_name: row.get("job_name"),
            last_status: row.get("status"),
            last_detail: row.get("detail"),
            last_error: row.get("error"),
            last_duration_ms: row.get("duration_ms"),
            last_run_at: row.get("created_at"),
            runs_24h: row.get("runs_24h"),
            failures_24h: row.get("failures_24h"),
        })
        .collect())
}

/// Most recent failed iterations, newest first — the Jobs page's alert feed.
pub async fn recent_failures(pool: &DbPool, limit: i64) -> Result<Vec<JobRunRow>, ApiError> {
    sqlx::query_as::<_, JobRunRow>(
        "SELECT id, job_name, status, detail, error, duration_ms, created_at
         FROM job_runs
         WHERE status = 'error'
         ORDER BY created_at DESC
         LIMIT $1",
    )
    .bind(limit)
    .fetch_all(pool)
    .await
    .map_err(ApiError::from)
}

/// `email_deliveries` backlog and throughput. This table predates `job_runs`,
/// so it is queried unconditionally.
pub async fn email_queue_health(pool: &DbPool) -> Result<EmailQueueHealth, ApiError> {
    let row = sqlx::query(
        "SELECT
            COUNT(*) FILTER (WHERE status = 'queued') AS queued,
            COUNT(*) FILTER (WHERE status = 'sending') AS sending,
            COUNT(*) FILTER (WHERE status = 'failed') AS failed,
            COUNT(*) FILTER (WHERE status = 'sent'
                              AND sent_at > CURRENT_TIMESTAMP - INTERVAL '24 hours') AS sent_24h
         FROM email_deliveries",
    )
    .fetch_one(pool)
    .await
    .map_err(ApiError::from)?;

    Ok(EmailQueueHealth {
        queued: row.get("queued"),
        sending: row.get("sending"),
        failed: row.get("failed"),
        sent_24h: row.get("sent_24h"),
    })
}

#[derive(FromRow)]
struct StaffNotificationRow {
    id: i64,
    audience_permission: String,
    kind: String,
    subject: Option<String>,
    title: String,
    body: Option<String>,
    created_at: chrono::DateTime<chrono::Utc>,
    read_at: Option<chrono::DateTime<chrono::Utc>>,
    action_pending: bool,
}

fn priority_rank(priority: &str) -> u8 {
    match priority {
        "urgent" => 0,
        "high" => 1,
        _ => 2,
    }
}

/// Notifications addressed to any of `permissions` (the caller's effective
/// permission names), enriched with priority/routing/live `action_pending`,
/// ordered with unread actionable and urgent items first, then newest first.
/// A `<resource>:manage` grant also matches audiences on that resource, the
/// same implication as `has_permission`. `staff_notifications` shares its
/// install with `job_runs`, so callers gate on [`table_present`] the same way.
pub async fn list_notifications(
    pool: &DbPool,
    user_id: i64,
    permissions: &[String],
    limit: i64,
) -> Result<Vec<StaffNotificationItem>, ApiError> {
    let rows = sqlx::query_as::<_, StaffNotificationRow>(
        r#"
        WITH candidate_notifications AS (
            SELECT n.id, n.audience_permission, n.kind, n.subject, n.title, n.body, n.created_at
            FROM staff_notifications n
            WHERE n.audience_permission = ANY($2)
               OR (split_part(n.audience_permission, ':', 1) || ':manage') = ANY($2)
            ORDER BY n.created_at DESC
            LIMIT 200
        )
        SELECT
            n.id,
            n.audience_permission,
            n.kind,
            n.subject,
            n.title,
            n.body,
            n.created_at,
            r.read_at,
            CASE
                WHEN n.kind IN ('bank_transfer_pending', 'payment_receipt_uploaded') AND n.subject IS NOT NULL THEN
                    EXISTS (
                        SELECT 1
                        FROM payments p
                        JOIN bookings b ON b.id = p.booking_id
                        LEFT JOIN booking_groups bg ON bg.id = b.booking_group_id
                        WHERE p.status = 'pending'
                          AND p.payment_method = 'bank_transfer'
                          AND (
                              b.booking_number = n.subject
                              OR bg.group_number = n.subject
                              OR ('PAY-' || p.id::text) = n.subject
                          )
                    )
                WHEN n.kind IN ('guest_support_conversation', 'guest_support_message') AND n.subject IS NOT NULL THEN
                    EXISTS (
                        SELECT 1
                        FROM support_conversations c
                        WHERE c.conversation_number = split_part(n.subject, ':', 1)
                          AND c.status = 'waiting_for_staff'
                    )
                WHEN n.kind IN ('support_sla_at_risk', 'support_sla_breached') AND n.subject IS NOT NULL THEN
                    EXISTS (
                        SELECT 1
                        FROM support_conversations c
                        WHERE c.conversation_number = split_part(n.subject, ':', 1)
                          AND c.resolved_at IS NULL
                          AND c.status NOT IN ('waiting_for_guest', 'resolved', 'closed')
                    )
                WHEN n.kind = 'ekyc_pending_review' AND n.subject IS NOT NULL THEN
                    EXISTS (
                        SELECT 1
                        FROM ekyc_verifications e
                        WHERE e.id = (CASE WHEN n.subject ~ '^[0-9]{1,18}$' THEN n.subject::bigint ELSE NULL END)
                          AND e.status IN (
                              'submitted',
                              'automated_review',
                              'pending_manual_review',
                              'in_review',
                              'escalated',
                              'on_hold'
                          )
                    )
                WHEN n.kind IN ('maintenance_critical', 'maintenance_high') AND n.subject IS NOT NULL THEN
                    EXISTS (
                        SELECT 1
                        FROM maintenance_tickets m
                        WHERE m.ticket_number = n.subject
                          AND m.status NOT IN ('resolved', 'closed')
                    )
                ELSE (r.read_at IS NULL)
            END AS action_pending
        FROM candidate_notifications n
        LEFT JOIN staff_notification_reads r
          ON r.notification_id = n.id AND r.user_id = $1
        "#,
    )
    .bind(user_id)
    .bind(permissions)
    .fetch_all(pool)
    .await
    .map_err(ApiError::from)?;

    let mut items: Vec<StaffNotificationItem> = rows
        .into_iter()
        .map(|row| {
            let meta = notifications::derive_meta(
                &row.audience_permission,
                &row.kind,
                row.subject.as_deref(),
                &row.title,
            );
            StaffNotificationItem {
                id: row.id,
                audience_permission: row.audience_permission,
                kind: row.kind,
                subject: row.subject,
                title: row.title,
                body: row.body,
                created_at: row.created_at,
                read_at: row.read_at,
                priority: meta.priority.to_string(),
                category: meta.category.to_string(),
                action_url: meta.action_url,
                action_label_key: meta.action_label_key.map(str::to_string),
                requires_action: meta.requires_action,
                action_pending: row.action_pending,
            }
        })
        .collect();

    items.sort_by(|a, b| {
        let a_unread_pending = a.read_at.is_none() && a.action_pending;
        let b_unread_pending = b.read_at.is_none() && b.action_pending;
        b_unread_pending
            .cmp(&a_unread_pending)
            .then_with(|| b.action_pending.cmp(&a.action_pending))
            .then_with(|| priority_rank(&a.priority).cmp(&priority_rank(&b.priority)))
            .then_with(|| b.created_at.cmp(&a.created_at))
    });

    let max_items = usize::try_from(limit.max(0)).unwrap_or(100);
    items.truncate(max_items);
    Ok(items)
}

/// Mark one notification read for `user_id` — a no-op when the notification is
/// not addressed to a permission the user holds, so callers cannot mark
/// notifications they cannot see (and no existence check leaks anything).
pub async fn mark_notification_read(
    pool: &DbPool,
    notification_id: i64,
    user_id: i64,
    permissions: &[String],
) -> Result<(), ApiError> {
    sqlx::query(
        "INSERT INTO staff_notification_reads (notification_id, user_id)
         SELECT n.id, $2 FROM staff_notifications n
         WHERE n.id = $1
           AND (
             n.audience_permission = ANY($3)
             OR (split_part(n.audience_permission, ':', 1) || ':manage') = ANY($3)
           )
         ON CONFLICT DO NOTHING",
    )
    .bind(notification_id)
    .bind(user_id)
    .bind(permissions)
    .execute(pool)
    .await
    .map_err(ApiError::from)?;
    Ok(())
}

/// Mark every notification visible to the user as read.
pub async fn mark_all_notifications_read(
    pool: &DbPool,
    user_id: i64,
    permissions: &[String],
) -> Result<(), ApiError> {
    sqlx::query(
        "INSERT INTO staff_notification_reads (notification_id, user_id)
         SELECT n.id, $1 FROM staff_notifications n
         WHERE n.audience_permission = ANY($2)
            OR (split_part(n.audience_permission, ':', 1) || ':manage') = ANY($2)
         ON CONFLICT DO NOTHING",
    )
    .bind(user_id)
    .bind(permissions)
    .execute(pool)
    .await
    .map_err(ApiError::from)?;
    Ok(())
}
