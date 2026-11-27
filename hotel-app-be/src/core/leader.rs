//! Single-runner gating for spawned background loops via Postgres advisory
//! locks.
//!
//! Each guarded task pins one pooled connection and holds a session-scoped
//! `pg_advisory_lock` on it. Only the winner's `run` future is driven; losers
//! retry until the lock frees. Session death releases the lock automatically,
//! so a crashed leader hands off to the next replica without any lease
//! bookkeeping. A watchdog ping on the pinned connection detects lock loss
//! (failover, network partition) and re-enters the acquisition loop.

use std::future::Future;
use std::pin::Pin;
use std::str::FromStr;
use std::time::Duration;

use sqlx::Connection;
use tokio::task::JoinHandle;
use tokio::time::{sleep, timeout};

use super::db::DbPool;

/// How long a loser waits before retrying `pg_try_advisory_lock`, and how
/// long a leader that lost its connection waits before re-acquiring.
const RETRY_DELAY: Duration = Duration::from_secs(30);
/// How often the leader pings the lock-holding connection.
const WATCHDOG_INTERVAL: Duration = Duration::from_secs(15);
/// Client-side bound on the lock attempt and each watchdog ping. Without it
/// a half-open connection hangs until the OS gives up on TCP retransmits —
/// far longer than the overlap the idempotency contract below guarantees.
const QUERY_TIMEOUT: Duration = Duration::from_secs(5);

/// Advisory-lock keys. One namespace (820_xxx) keeps every guarded task
/// distinct by convention — session locks share PostgreSQL's single keyspace
/// with the `pg_advisory_xact_lock(hashtextextended(..))` callers in the
/// repositories, so the range is a social contract, not an enforced one.
pub const LOCK_NIGHT_AUDIT: i64 = 820_001;
pub const LOCK_RECEIPTS: i64 = 820_002;
pub const LOCK_UNPAID_HOLD: i64 = 820_003;
pub const LOCK_COMMS_WORKER: i64 = 820_004;
pub const LOCK_COMMS_SCHED: i64 = 820_005;
/// Housekeeping loop: rate-limit bucket prune (every 10 min) and the hourly
/// session retention sweep. Shares one lock and one pinned connection.
pub const LOCK_MAINTENANCE: i64 = 820_006;

/// Release every session-level advisory lock the connection holds before
/// returning it to the pool. Returns `true` when unlocking completed within
/// `QUERY_TIMEOUT`; callers must `conn.close().await` when `false` so a
/// half-open or still-locked connection never returns to the pool.
async fn release_locks(conn: &mut sqlx::postgres::PgConnection) -> bool {
    matches!(
        timeout(
            QUERY_TIMEOUT,
            sqlx::query("SELECT pg_advisory_unlock_all()").execute(&mut *conn),
        )
        .await,
        Ok(Ok(_))
    )
}

async fn finish_lock_conn(mut conn: sqlx::pool::PoolConnection<crate::core::db::DbDatabase>) {
    if release_locks(&mut conn).await {
        drop(conn);
    } else {
        let _ = conn.close().await;
    }
}

/// Spawn `run` so that exactly one process in the replica set drives it at a
/// time. `run` receives the pool and is expected to never return (a
/// scheduler loop); if it does, or the lock connection dies, the acquisition
/// loop starts over.
///
/// **Contract: `run` must be idempotent under a bounded overlap (~
/// `WATCHDOG_INTERVAL` + `QUERY_TIMEOUT`, ≤ ~20s) with a successor leader.**
/// When the lock-holding connection dies, PostgreSQL releases the advisory
/// lock immediately and another replica can win it while this replica's
/// `run` is still executing. Guard with DB constraints, row leases, or
/// idempotency keys (as the current schedulers do: `night_audit_runs`
/// audit-date unique key, delivery leases, campaign idempotency keys); an
/// in-memory "already ran" flag does not survive the overlap. `run` executes
/// as a child task: a panic is logged and the lock re-acquired rather than
/// killing the acquisition loop and leaving the lock pooled.
pub fn spawn_exclusive<F, Fut>(name: &'static str, lock_key: i64, pool: DbPool, run: F)
where
    F: Fn(DbPool) -> Fut + Send + Sync + 'static,
    Fut: Future<Output = ()> + Send + 'static,
{
    tokio::spawn(async move {
        loop {
            let Ok(mut conn) = pool.acquire().await else {
                sleep(RETRY_DELAY).await;
                continue;
            };
            let won = timeout(
                QUERY_TIMEOUT,
                sqlx::query_scalar::<_, bool>("SELECT pg_try_advisory_lock($1)")
                    .bind(lock_key)
                    .fetch_one(&mut *conn),
            )
            .await
            .ok()
            .and_then(|result| result.ok())
            .unwrap_or(false);
            if !won {
                // The attempt may have acquired the lock server-side before
                // its response was lost — release before returning the conn, or
                // close on timeout so no locked/dead connection returns to pool.
                finish_lock_conn(conn).await;
                sleep(RETRY_DELAY).await;
                continue;
            }
            log::info!("scheduler '{name}' holds leader lock {lock_key}");

            // Drive `run` as a child task so a panic surfaces as a JoinError
            // here instead of unwinding through this task — which would drop
            // the pooled connection while it still holds the lock and leave
            // every replica leaderless until the connection is reaped.
            let mut run_handle = tokio::spawn(run(pool.clone()));
            let mut watchdog = tokio::time::interval(WATCHDOG_INTERVAL);
            loop {
                tokio::select! {
                    outcome = &mut run_handle => {
                        match outcome {
                            Ok(()) => log::warn!(
                                "scheduler '{name}' exited; re-acquiring lock"
                            ),
                            Err(error) => log::error!(
                                "scheduler '{name}' task failed: {error}; re-acquiring lock"
                            ),
                        }
                        break;
                    }
                    _ = watchdog.tick() => {
                        let alive = timeout(
                            QUERY_TIMEOUT,
                            sqlx::query("SELECT 1").execute(&mut *conn),
                        )
                        .await
                        .map(|result| result.is_ok())
                        .unwrap_or(false);
                        if !alive {
                            log::warn!(
                                "scheduler '{name}' lost lock connection; re-acquiring"
                            );
                            break;
                        }
                    }
                }
            }
            run_handle.abort();
            finish_lock_conn(conn).await;
            sleep(RETRY_DELAY).await;
        }
    });
}

pub type LeaderRunFn =
    Box<dyn Fn(DbPool) -> Pin<Box<dyn Future<Output = ()> + Send>> + Send + Sync>;

/// One scheduler task managed by [`spawn_exclusive_multiplexed`].
pub struct LeaderTask {
    pub name: &'static str,
    pub lock_key: i64,
    pub run: LeaderRunFn,
}

impl LeaderTask {
    pub fn new<F, Fut>(name: &'static str, lock_key: i64, run: F) -> Self
    where
        F: Fn(DbPool) -> Fut + Send + Sync + 'static,
        Fut: Future<Output = ()> + Send + 'static,
    {
        Self {
            name,
            lock_key,
            run: Box::new(move |pool| Box::pin(run(pool))),
        }
    }
}

/// Drive multiple independent leader-locked background loops over ONE dedicated
/// non-pooled PostgreSQL connection, preserving request-pool capacity.
///
/// Session-level advisory locks in PostgreSQL are re-entrant on the same
/// session, so we track per-task running handles and only call
/// `pg_try_advisory_lock(task.lock_key)` when a slot is currently `None`.
pub fn spawn_exclusive_multiplexed(database_url: String, pool: DbPool, tasks: Vec<LeaderTask>) {
    tokio::spawn(async move {
        let mut handles: Vec<Option<JoinHandle<()>>> = (0..tasks.len()).map(|_| None).collect();

        loop {
            let Ok(connect_opts) = sqlx::postgres::PgConnectOptions::from_str(&database_url) else {
                log::error!("multiplexed leader: invalid DATABASE_URL");
                return;
            };
            let connect_opts = connect_opts.application_name("hotel-app-be-leader");

            let Ok(Ok(mut conn)) = timeout(
                QUERY_TIMEOUT,
                sqlx::postgres::PgConnection::connect_with(&connect_opts),
            )
            .await
            else {
                sleep(RETRY_DELAY).await;
                continue;
            };

            let mut retry_tick = tokio::time::interval(RETRY_DELAY);
            retry_tick.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
            let mut watchdog = tokio::time::interval(WATCHDOG_INTERVAL);
            watchdog.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);

            'session: loop {
                // Reap any child tasks that exited or panicked before retrying
                // unheld locks. Unlock once so the session cleanly releases the
                // slot before re-acquisition.
                for (idx, task) in tasks.iter().enumerate() {
                    if let Some(handle) = handles[idx].as_ref()
                        && handle.is_finished()
                    {
                        if let Some(finished) = handles[idx].take() {
                            match finished.await {
                                Ok(()) => log::warn!(
                                    "scheduler '{}' exited; will re-acquire lock",
                                    task.name
                                ),
                                Err(error) => log::error!(
                                    "scheduler '{}' task failed: {}; will re-acquire lock",
                                    task.name,
                                    error
                                ),
                            }
                        }
                        let _ = timeout(
                            QUERY_TIMEOUT,
                            sqlx::query("SELECT pg_advisory_unlock($1)")
                                .bind(task.lock_key)
                                .execute(&mut conn),
                        )
                        .await;
                    }

                    if handles[idx].is_none() {
                        match timeout(
                            QUERY_TIMEOUT,
                            sqlx::query_scalar::<_, bool>("SELECT pg_try_advisory_lock($1)")
                                .bind(task.lock_key)
                                .fetch_one(&mut conn),
                        )
                        .await
                        {
                            Ok(Ok(true)) => {
                                log::info!(
                                    "scheduler '{}' holds leader lock {} (multiplexed)",
                                    task.name,
                                    task.lock_key
                                );
                                handles[idx] = Some(tokio::spawn((task.run)(pool.clone())));
                            }
                            Ok(Ok(false)) => {}
                            Ok(Err(error)) => {
                                log::warn!(
                                    "multiplexed leader lock error for '{}': {error}",
                                    task.name
                                );
                                break 'session;
                            }
                            Err(_) => {
                                log::warn!("multiplexed leader lock timeout for '{}'", task.name);
                                break 'session;
                            }
                        }
                    }
                }

                tokio::select! {
                    _ = retry_tick.tick() => {}
                    _ = watchdog.tick() => {
                        let alive = timeout(
                            QUERY_TIMEOUT,
                            sqlx::query("SELECT 1").execute(&mut conn),
                        )
                        .await
                        .map(|r| r.is_ok())
                        .unwrap_or(false);
                        if !alive {
                            log::warn!("multiplexed leader lost connection; aborting active runners and reconnecting");
                            break 'session;
                        }
                    }
                }
            }

            for slot in &mut handles {
                if let Some(handle) = slot.take() {
                    handle.abort();
                }
            }
            if !release_locks(&mut conn).await {
                let _ = conn.close().await;
            }
            sleep(RETRY_DELAY).await;
        }
    });
}
