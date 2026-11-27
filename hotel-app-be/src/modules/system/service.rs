//! Aggregates DB liveness, in-process metrics, the email queue, and
//! `job_runs` heartbeats into the System Health response.

use std::sync::LazyLock;
use std::time::Instant;

use crate::core::db::DbPool;
use crate::core::error::ApiError;
use crate::core::metrics;
use crate::modules::system::models::{
    JobRunRow, StaffNotificationItem, StaffNotificationsResponse, SystemHealthResponse,
};
use crate::modules::system::repository;

/// Process start, initialized on first read — close enough to process birth
/// for an uptime display, and impossible to get wrong at startup.
static PROCESS_STARTED: LazyLock<Instant> = LazyLock::new(Instant::now);

pub fn mark_process_started() {
    let _ = *PROCESS_STARTED;
}

const RECENT_FAILURES_LIMIT: i64 = 25;

pub async fn system_health(pool: &DbPool) -> Result<SystemHealthResponse, ApiError> {
    // The fact that this query answers at all is the liveness check — a pool
    // that cannot execute SELECT 1 fails the request with a 5xx, which is the
    // honest "down" signal.
    sqlx::query("SELECT 1")
        .execute(pool)
        .await
        .map_err(ApiError::from)?;

    let job_runs_enabled = repository::table_present(pool).await;
    let jobs = if job_runs_enabled {
        repository::latest_per_job(pool).await?
    } else {
        Vec::new()
    };

    Ok(SystemHealthResponse {
        database: "ok",
        uptime_seconds: PROCESS_STARTED.elapsed().as_secs(),
        metrics: metrics::snapshot(),
        email_queue: repository::email_queue_health(pool).await?,
        jobs,
        job_runs_enabled,
    })
}

/// Recent failed iterations across all loops.
pub async fn recent_job_failures(pool: &DbPool) -> Result<Vec<JobRunRow>, ApiError> {
    if !repository::table_present(pool).await {
        return Ok(Vec::new());
    }
    repository::recent_failures(pool, RECENT_FAILURES_LIMIT).await
}

const NOTIFICATIONS_LIMIT: i64 = 100;

/// The caller's notification feed. Visibility is audience-based: a
/// notification addressed to `settings:manage` reaches exactly the users whose
/// *effective* permission names contain it — the same set `check_permission`
/// enforces, so a team-conferred grant counts here too.
pub async fn my_notifications(
    pool: &DbPool,
    user_id: i64,
) -> Result<StaffNotificationsResponse, ApiError> {
    if !repository::table_present(pool).await {
        return Ok(StaffNotificationsResponse {
            unread: 0,
            urgent_unread: 0,
            action_pending_count: 0,
            items: Vec::new(),
        });
    }
    let permissions = crate::core::rbac_cache::effective_permissions(pool, user_id).await?;
    let names: Vec<String> = permissions.iter().cloned().collect();
    let items = repository::list_notifications(pool, user_id, &names, NOTIFICATIONS_LIMIT).await?;
    let unread = items
        .iter()
        .filter(|n: &&StaffNotificationItem| n.read_at.is_none() && n.action_pending)
        .count() as i64;
    let urgent_unread = items
        .iter()
        .filter(|n: &&StaffNotificationItem| {
            n.read_at.is_none() && n.action_pending && n.priority == "urgent"
        })
        .count() as i64;
    let action_pending_count = items
        .iter()
        .filter(|n: &&StaffNotificationItem| n.action_pending && n.requires_action)
        .count() as i64;
    Ok(StaffNotificationsResponse {
        unread,
        urgent_unread,
        action_pending_count,
        items,
    })
}

pub async fn mark_notification_read(
    pool: &DbPool,
    user_id: i64,
    notification_id: i64,
) -> Result<(), ApiError> {
    if !repository::table_present(pool).await {
        return Ok(());
    }
    let permissions = crate::core::rbac_cache::effective_permissions(pool, user_id).await?;
    let names: Vec<String> = permissions.iter().cloned().collect();
    repository::mark_notification_read(pool, notification_id, user_id, &names).await?;
    Ok(())
}

pub async fn mark_all_notifications_read(pool: &DbPool, user_id: i64) -> Result<(), ApiError> {
    if !repository::table_present(pool).await {
        return Ok(());
    }
    let permissions = crate::core::rbac_cache::effective_permissions(pool, user_id).await?;
    let names: Vec<String> = permissions.iter().cloned().collect();
    repository::mark_all_notifications_read(pool, user_id, &names).await?;
    Ok(())
}
