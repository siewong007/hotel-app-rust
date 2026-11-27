//! Durable email delivery worker.
//!
//! Follows the night-audit scheduler pattern: a spawned loop that polls the
//! `email_deliveries` outbox, claims due rows under a database lease, and
//! sends them through the configured [`Transport`]. Crash recovery is
//! structural: a row stuck in 'sending' past its lease expiry is reclaimed by
//! the next tick (on this or any other instance). Consent and suppression are
//! rechecked immediately before every send, so a late unsubscribe still
//! prevents delivery of queued mail.

use std::time::Duration;

use chrono::Utc;

use super::repository::CommunicationsRepository as Repo;
use super::transport::{EmailAttachment, OutgoingEmail, Transport};
use super::validation;
use crate::core::db::{DbPool, generate_uuid};
use crate::core::error::ApiError;

const DEFAULT_INTERVAL_SECS: u64 = 15;
const IDLE_HEARTBEAT_INTERVAL_SECS: u64 = 60;
const DEFAULT_BATCH: i64 = 10;
const MAX_BACKOFF_MINUTES: i64 = 60;

fn should_record_heartbeat(
    outcome: &Result<usize, ApiError>,
    last_recorded_at: Option<std::time::Instant>,
    now: std::time::Instant,
    idle_interval: Duration,
) -> bool {
    match outcome {
        Ok(0) => {
            last_recorded_at.is_none_or(|last| now.saturating_duration_since(last) >= idle_interval)
        }
        Ok(_) | Err(_) => true,
    }
}

fn env_u64(key: &str, default: u64) -> u64 {
    std::env::var(key)
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(default)
}

/// Exponential backoff: 2^attempts minutes, capped.
fn backoff_minutes(attempts: i32) -> i64 {
    let attempts = attempts.clamp(1, 30) as u32;
    (2_i64.saturating_pow(attempts)).min(MAX_BACKOFF_MINUTES)
}

/// The worker loop. Returns immediately when SMTP is unconfigured rather
/// than parking while holding the leader lock — an unconfigured winner would
/// otherwise block a configured replica's failover forever. The
/// `spawn_exclusive` wrapper re-acquires on a delay, so an unconfigured
/// replica costs one lock probe per cycle instead of the lock.
pub async fn run(pool: DbPool) {
    let transport = match Transport::from_env() {
        Ok(Some(t)) => t,
        Ok(None) => {
            log::info!(
                "Email delivery worker idle: SMTP not configured (set SMTP_HOST / SMTP_FROM_EMAIL)"
            );
            return;
        }
        Err(e) => {
            log::error!("Email delivery worker disabled: {e}");
            return;
        }
    };
    let interval =
        Duration::from_secs(env_u64("EMAIL_WORKER_INTERVAL_SECS", DEFAULT_INTERVAL_SECS));
    let batch = env_u64("EMAIL_WORKER_BATCH", DEFAULT_BATCH as u64) as i64;
    let worker_id = format!("worker-{}", generate_uuid());
    log::info!(
        "Email delivery worker started ({worker_id}, every {}s, batch {batch})",
        interval.as_secs()
    );
    let idle_heartbeat_interval = Duration::from_secs(IDLE_HEARTBEAT_INTERVAL_SECS);
    let mut last_heartbeat: Option<std::time::Instant> = None;
    loop {
        tokio::time::sleep(interval).await;
        let started = std::time::Instant::now();
        let outcome = tick(&pool, &transport, &worker_id, batch).await;
        let now = std::time::Instant::now();
        if should_record_heartbeat(&outcome, last_heartbeat, now, idle_heartbeat_interval) {
            crate::core::job_runs::record(
                &pool,
                "email_delivery_worker",
                outcome
                    .as_ref()
                    .ok()
                    .map(|n| serde_json::json!({ "claimed": n })),
                outcome.as_ref().err().map(|e| e.to_string()),
                started.elapsed(),
            )
            .await;
            last_heartbeat = Some(now);
        }
        if let Err(e) = &outcome {
            log::warn!("Email delivery worker tick failed: {e}");
        }
    }
}

/// One worker iteration. Also directly callable from tests with a fake
/// transport.
pub async fn tick(
    pool: &DbPool,
    transport: &Transport,
    worker_id: &str,
    batch: i64,
) -> Result<usize, ApiError> {
    let claimed = Repo::claim_due_deliveries(pool, worker_id, batch).await?;
    if claimed.is_empty() {
        return Ok(0);
    }
    let mut campaigns_touched: Vec<i64> = Vec::new();
    let count = claimed.len();
    for delivery in claimed {
        if let Some(campaign_id) = delivery.campaign_id
            && !campaigns_touched.contains(&campaign_id)
        {
            campaigns_touched.push(campaign_id);
        }
        if let Err(e) = process_delivery(pool, transport, &delivery, worker_id).await {
            // Persisting the outcome failed; the lease will expire and the
            // row will be reclaimed. Log without recipient details.
            log::warn!("Delivery {} outcome persistence failed: {e}", delivery.id);
        }
    }
    for campaign_id in campaigns_touched {
        // Don't finalize a campaign whose expansion is incomplete — a leader
        // that died mid-expansion leaves un-expanded audience with no
        // delivery rows, which the scheduler's stale-running resume picks up.
        if let Some(campaign) = Repo::get_campaign(pool, campaign_id).await? {
            let scope =
                crate::modules::segments::service::audience_scope_for(pool, campaign.segment_id)
                    .await?;
            let remaining =
                Repo::audience_batch(pool, &campaign.topic, campaign_id, &scope, 1).await?;
            if !remaining.is_empty() {
                continue;
            }
        }
        if Repo::complete_campaign_if_done(pool, campaign_id).await? {
            log::info!("Campaign {campaign_id} completed");
        }
    }
    Ok(count)
}

/// Files to send with a delivery: the PDF copy of a checkout receipt. A PDF
/// problem never fails or delays the email — the body already carries every
/// figure — so it is logged and the message goes out without the attachment.
async fn delivery_attachments(
    pool: &DbPool,
    delivery: &super::models::EmailDelivery,
) -> Vec<EmailAttachment> {
    if delivery.kind != "checkout_receipt" {
        return Vec::new();
    }
    let Some(invoice_number) = crate::modules::payments::service::invoice_from_checkout_receipt_key(
        &delivery.idempotency_key,
    ) else {
        log::warn!(
            "Delivery {}: checkout receipt key has no invoice; sending without PDF",
            delivery.id
        );
        return Vec::new();
    };
    match crate::modules::payments::service::checkout_receipt_pdf(pool, invoice_number).await {
        Ok(Some((filename, bytes))) => vec![EmailAttachment {
            filename,
            content_type: "application/pdf".to_string(),
            bytes,
        }],
        Ok(None) => {
            log::warn!(
                "Delivery {}: invoice {invoice_number} not found; sending receipt without PDF",
                delivery.id
            );
            Vec::new()
        }
        Err(e) => {
            log::warn!(
                "Delivery {}: receipt PDF failed ({e}); sending without it",
                delivery.id
            );
            Vec::new()
        }
    }
}

async fn process_delivery(
    pool: &DbPool,
    transport: &Transport,
    delivery: &super::models::EmailDelivery,
    worker_id: &str,
) -> Result<(), ApiError> {
    // Campaign cancelled after enqueue → drop the remaining sends.
    if let Some(campaign_id) = delivery.campaign_id
        && let Some(campaign) = Repo::get_campaign(pool, campaign_id).await?
        && campaign.status == "cancelled"
    {
        let mut tx = pool.begin().await.map_err(ApiError::from)?;
        Repo::mark_delivery_skipped_tx(
            &mut tx,
            delivery.id,
            worker_id,
            "cancelled",
            "campaign cancelled",
        )
        .await?;
        tx.commit().await.map_err(ApiError::from)?;
        return Ok(());
    }

    // Last-moment consent + suppression recheck. Transactional kinds are part
    // of the service: they need only an active guest (hard suppressions below
    // still apply), while marketing kinds additionally require a live
    // per-topic subscription.
    let suppressed = Repo::is_email_suppressed(pool, &delivery.recipient_email).await?;
    let transactional = !validation::requires_topic_subscription(&delivery.kind);
    let deliverable = if transactional {
        Repo::is_guest_active(pool, delivery.guest_id).await?
    } else {
        Repo::is_guest_deliverable(pool, delivery.guest_id, &delivery.topic).await?
    };
    if suppressed || !deliverable {
        let reason = if suppressed {
            "recipient suppressed"
        } else if transactional {
            "guest inactive"
        } else {
            "subscription revoked or guest inactive"
        };
        let mut tx = pool.begin().await.map_err(ApiError::from)?;
        Repo::mark_delivery_skipped_tx(&mut tx, delivery.id, worker_id, "suppressed", reason)
            .await?;
        tx.commit().await.map_err(ApiError::from)?;
        return Ok(());
    }

    let attachments = delivery_attachments(pool, delivery).await;
    let outcome = transport
        .send(&OutgoingEmail {
            to: delivery.recipient_email.clone(),
            subject: delivery.subject.clone(),
            body_html: delivery.body_html.clone(),
            body_text: delivery.body_text.clone(),
            attachments,
        })
        .await;

    let mut tx = pool.begin().await.map_err(ApiError::from)?;
    match outcome {
        Ok(provider_message_id) => {
            if Repo::mark_delivery_sent_tx(
                &mut tx,
                delivery.id,
                worker_id,
                provider_message_id.as_deref(),
            )
            .await?
                == 0
            {
                return Ok(()); // lease lost — the new owner persists the outcome
            }
            if let Some(campaign_id) = delivery.campaign_id {
                Repo::add_campaign_counts_tx(&mut tx, campaign_id, 1, 0).await?;
            }
        }
        Err(error) => {
            let error = error.chars().take(500).collect::<String>();
            if delivery.attempts >= delivery.max_attempts {
                if Repo::mark_delivery_failed_tx(&mut tx, delivery.id, worker_id, &error, None)
                    .await?
                    == 0
                {
                    return Ok(()); // lease lost — the new owner persists the outcome
                }
                if let Some(campaign_id) = delivery.campaign_id {
                    Repo::add_campaign_counts_tx(&mut tx, campaign_id, 0, 1).await?;
                }
                log::warn!(
                    "Delivery {} permanently failed after {} attempts",
                    delivery.id,
                    delivery.attempts
                );
            } else {
                let retry_at =
                    Utc::now() + chrono::Duration::minutes(backoff_minutes(delivery.attempts));
                Repo::mark_delivery_failed_tx(
                    &mut tx,
                    delivery.id,
                    worker_id,
                    &error,
                    Some(retry_at),
                )
                .await?;
            }
        }
    }
    tx.commit().await.map_err(ApiError::from)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use std::time::{Duration, Instant};

    use super::{backoff_minutes, should_record_heartbeat};
    use crate::core::error::ApiError;

    #[test]
    fn backoff_is_exponential_and_capped() {
        assert_eq!(backoff_minutes(1), 2);
        assert_eq!(backoff_minutes(2), 4);
        assert_eq!(backoff_minutes(4), 16);
        assert_eq!(backoff_minutes(10), 60);
        assert_eq!(backoff_minutes(30), 60);
    }

    #[test]
    fn heartbeat_throttles_idle_ticks_but_records_work_and_errors_immediately() {
        let interval = Duration::from_secs(60);
        let t0 = Instant::now();

        // First idle tick always records so startup health populates immediately.
        assert!(should_record_heartbeat(&Ok(0), None, t0, interval));
        // Subsequent idle ticks inside the 60s window are skipped.
        assert!(!should_record_heartbeat(
            &Ok(0),
            Some(t0),
            t0 + Duration::from_secs(15),
            interval,
        ));
        assert!(!should_record_heartbeat(
            &Ok(0),
            Some(t0),
            t0 + Duration::from_secs(59),
            interval,
        ));
        // Once the idle interval elapses, idle heartbeat resumes.
        assert!(should_record_heartbeat(
            &Ok(0),
            Some(t0),
            t0 + Duration::from_secs(60),
            interval,
        ));
        // Delivering emails or hitting an error always records immediately.
        assert!(should_record_heartbeat(
            &Ok(1),
            Some(t0),
            t0 + Duration::from_secs(15),
            interval,
        ));
        assert!(should_record_heartbeat(
            &Err(ApiError::Database("boom".into())),
            Some(t0),
            t0 + Duration::from_secs(15),
            interval,
        ));
    }
}
