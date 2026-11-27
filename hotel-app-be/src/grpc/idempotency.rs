//! Client `request_id` idempotency for mutating RPCs.
//!
//! The contract requires every mutating RPC to honor a client-supplied
//! `request_id`: retrying the same `(user_id, method, request_id)` with an
//! identical payload replays the original response instead of executing the
//! mutation twice. Calling with the same key and different request parameters
//! fails with `INVALID_ARGUMENT`. REST has no equivalent — its mutations are
//! at-most-once per HTTP request — so this is strictly additive and changes no
//! existing behavior.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use prost::Message;
use sha2::{Digest, Sha256};
use tokio::sync::Notify;
use tonic::{Response, Status};

const TTL: Duration = Duration::from_secs(60 * 60);
const MAX_ENTRIES: usize = 10_000;
const MAX_REQUEST_ID_BYTES: usize = 128;

type CacheKey = (i64, String, [u8; 32]);
type Fingerprint = [u8; 32];

struct CacheEntry {
    bytes: Vec<u8>,
    fingerprint: Fingerprint,
    created_at: Instant,
}

#[derive(Default)]
struct CacheState {
    entries: HashMap<CacheKey, CacheEntry>,
    inflight: HashMap<CacheKey, Arc<Notify>>,
}

struct InFlightGuard<'a> {
    state: &'a Mutex<CacheState>,
    key: Option<CacheKey>,
    notify: Arc<Notify>,
}

impl<'a> InFlightGuard<'a> {
    fn complete(mut self, bytes: Vec<u8>, fingerprint: Fingerprint) {
        if let Some(key) = self.key.take()
            && let Ok(mut state) = self.state.lock()
        {
            state.inflight.remove(&key);
            IdempotencyCache::insert_entry(&mut state.entries, key, bytes, fingerprint);
        }
        self.notify.notify_waiters();
    }
}

impl Drop for InFlightGuard<'_> {
    fn drop(&mut self) {
        if let Some(key) = self.key.take() {
            if let Ok(mut state) = self.state.lock() {
                state.inflight.remove(&key);
            }
            self.notify.notify_waiters();
        }
    }
}

#[derive(Default)]
pub struct IdempotencyCache {
    state: Mutex<CacheState>,
}

impl IdempotencyCache {
    /// Runs `f` once per `(user_id, method, request_id)`; a repeat within the
    /// TTL replays the cached response if the serialized request matches, or
    /// returns `INVALID_ARGUMENT` if parameters differ. Concurrent duplicate
    /// calls wait for the in-flight execution instead of double-mutating.
    /// `request_id == ""` bypasses the cache.
    pub async fn run<Req, Res, F, Fut>(
        &self,
        user_id: i64,
        method: &str,
        request_id: &str,
        req: &Req,
        f: F,
    ) -> Result<Response<Res>, Status>
    where
        Req: Message,
        Res: Message + Default,
        F: FnOnce() -> Fut,
        Fut: std::future::Future<Output = Result<Response<Res>, Status>>,
    {
        if request_id.is_empty() {
            return f().await;
        }
        if request_id.len() > MAX_REQUEST_ID_BYTES {
            return Err(Status::invalid_argument(format!(
                "request_id must not exceed {MAX_REQUEST_ID_BYTES} bytes"
            )));
        }

        let request_id_hash: [u8; 32] = Sha256::digest(request_id.as_bytes()).into();
        let key = (user_id, method.to_string(), request_id_hash);
        let fingerprint: Fingerprint = Sha256::digest(req.encode_to_vec()).into();

        let guard = loop {
            let waiter = {
                let mut state = self
                    .state
                    .lock()
                    .map_err(|_| Status::internal("idempotency cache lock poisoned"))?;

                if let Some(entry) = state.entries.get(&key) {
                    if entry.created_at.elapsed() < TTL {
                        if entry.fingerprint != fingerprint {
                            return Err(Status::invalid_argument(
                                "request_id already used with different parameters",
                            ));
                        }
                        let res = Res::decode(entry.bytes.as_slice()).map_err(|_| {
                            Status::internal("cached idempotent response failed to decode")
                        })?;
                        return Ok(Response::new(res));
                    }
                    state.entries.remove(&key);
                }

                if let Some(notify) = state.inflight.get(&key) {
                    Err(Arc::clone(notify))
                } else {
                    let notify = Arc::new(Notify::new());
                    state.inflight.insert(key.clone(), Arc::clone(&notify));
                    Ok(InFlightGuard {
                        state: &self.state,
                        key: Some(key.clone()),
                        notify,
                    })
                }
            };

            match waiter {
                Ok(guard) => break guard,
                Err(notify) => {
                    notify.notified().await;
                }
            }
        };

        let resp = f().await?;
        let bytes = resp.get_ref().encode_to_vec();
        guard.complete(bytes, fingerprint);
        Ok(resp)
    }

    fn insert_entry(
        entries: &mut HashMap<CacheKey, CacheEntry>,
        key: CacheKey,
        bytes: Vec<u8>,
        fingerprint: Fingerprint,
    ) {
        if entries.len() >= MAX_ENTRIES {
            entries.retain(|_, entry| entry.created_at.elapsed() < TTL);
            if entries.len() >= MAX_ENTRIES
                && let Some(cutoff) = Instant::now().checked_sub(TTL / 2)
            {
                entries.retain(|_, entry| entry.created_at > cutoff);
            }
            if entries.len() >= MAX_ENTRIES {
                let mut by_age: Vec<(CacheKey, Instant)> = entries
                    .iter()
                    .map(|(k, v)| (k.clone(), v.created_at))
                    .collect();
                by_age.sort_by_key(|(_, at)| *at);
                let target_remove = entries.len().saturating_sub((MAX_ENTRIES * 3) / 4).max(1);
                for (old_key, _) in by_age.into_iter().take(target_remove) {
                    entries.remove(&old_key);
                }
            }
        }
        entries.insert(
            key,
            CacheEntry {
                bytes,
                fingerprint,
                created_at: Instant::now(),
            },
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};

    #[derive(Clone, PartialEq, Message)]
    struct TestReq {
        #[prost(string, tag = "1")]
        value: String,
    }

    #[derive(Clone, PartialEq, Message)]
    struct TestRes {
        #[prost(int64, tag = "1")]
        count: i64,
    }

    #[tokio::test]
    async fn replays_for_same_user_and_isolates_different_users() {
        let cache = IdempotencyCache::default();
        let calls = Arc::new(AtomicUsize::new(0));
        let req = TestReq {
            value: "same".into(),
        };

        let r1 = cache
            .run(1, "Op", "k1", &req, || async {
                let n = calls.fetch_add(1, Ordering::SeqCst) + 1;
                Ok(Response::new(TestRes { count: n as i64 }))
            })
            .await
            .unwrap();
        let r2 = cache
            .run(1, "Op", "k1", &req, || async {
                let n = calls.fetch_add(1, Ordering::SeqCst) + 1;
                Ok(Response::new(TestRes { count: n as i64 }))
            })
            .await
            .unwrap();
        let r3 = cache
            .run(2, "Op", "k1", &req, || async {
                let n = calls.fetch_add(1, Ordering::SeqCst) + 1;
                Ok(Response::new(TestRes { count: n as i64 }))
            })
            .await
            .unwrap();

        assert_eq!(r1.into_inner().count, 1);
        assert_eq!(r2.into_inner().count, 1);
        assert_eq!(r3.into_inner().count, 2);
        assert_eq!(calls.load(Ordering::SeqCst), 2);
    }

    #[tokio::test]
    async fn rejects_reused_request_id_with_different_payload() {
        let cache = IdempotencyCache::default();
        let req1 = TestReq { value: "a".into() };
        let req2 = TestReq { value: "b".into() };

        cache
            .run(1, "Op", "k2", &req1, || async {
                Ok(Response::new(TestRes { count: 1 }))
            })
            .await
            .unwrap();

        let err = cache
            .run(1, "Op", "k2", &req2, || async {
                Ok(Response::new(TestRes { count: 2 }))
            })
            .await
            .unwrap_err();
        assert_eq!(err.code(), tonic::Code::InvalidArgument);
    }

    #[tokio::test]
    async fn deduplicates_concurrent_retries_single_flight() {
        let cache = Arc::new(IdempotencyCache::default());
        let calls = Arc::new(AtomicUsize::new(0));
        let req = TestReq {
            value: "race".into(),
        };

        let c1 = Arc::clone(&cache);
        let q1 = Arc::clone(&calls);
        let r1_req = req.clone();
        let h1 = tokio::spawn(async move {
            c1.run(1, "Op", "k3", &r1_req, || async {
                tokio::time::sleep(Duration::from_millis(40)).await;
                let n = q1.fetch_add(1, Ordering::SeqCst) + 1;
                Ok(Response::new(TestRes { count: n as i64 }))
            })
            .await
        });

        tokio::time::sleep(Duration::from_millis(10)).await;
        let r2 = cache
            .run(1, "Op", "k3", &req, || async {
                let n = calls.fetch_add(1, Ordering::SeqCst) + 1;
                Ok(Response::new(TestRes { count: n as i64 }))
            })
            .await
            .unwrap();
        let r1 = h1.await.unwrap().unwrap();

        assert_eq!(r1.into_inner().count, 1);
        assert_eq!(r2.into_inner().count, 1);
        assert_eq!(calls.load(Ordering::SeqCst), 1);
    }

    #[test]
    fn capacity_eviction_does_not_underflow_and_bounds_size() {
        let mut entries = HashMap::new();
        for i in 0..=(MAX_ENTRIES + 5) {
            let key_hash: [u8; 32] = Sha256::digest(format!("req-{i}").as_bytes()).into();
            IdempotencyCache::insert_entry(
                &mut entries,
                (1, "Op".to_string(), key_hash),
                vec![1],
                [0u8; 32],
            );
        }
        assert!(entries.len() <= MAX_ENTRIES);
    }

    #[tokio::test]
    async fn rejects_overlong_request_id() {
        let cache = IdempotencyCache::default();
        let req = TestReq { value: "x".into() };
        let long_id = "a".repeat(MAX_REQUEST_ID_BYTES + 1);
        let err = cache
            .run(1, "Op", &long_id, &req, || async {
                Ok(Response::new(TestRes { count: 1 }))
            })
            .await
            .unwrap_err();
        assert_eq!(err.code(), tonic::Code::InvalidArgument);
    }
}
