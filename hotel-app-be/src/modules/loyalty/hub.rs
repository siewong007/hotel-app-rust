use axum::extract::ws::{Message, WebSocket};
use serde::Serialize;
use tokio::sync::broadcast;

#[derive(Debug, Clone, Serialize)]
pub struct LoyaltyMemberUpdatedEvent {
    pub event_type: &'static str,
    pub member_id: i64,
    pub guest_id: i64,
}

#[derive(Debug, Clone)]
pub struct LoyaltyHub {
    sender: broadcast::Sender<LoyaltyMemberUpdatedEvent>,
}

impl Default for LoyaltyHub {
    fn default() -> Self {
        let (sender, _) = broadcast::channel(256);
        Self { sender }
    }
}

impl LoyaltyHub {
    pub fn publish_member_updated(&self, member_id: i64, guest_id: i64) {
        let _ = self.sender.send(LoyaltyMemberUpdatedEvent {
            event_type: "loyalty_member_updated",
            member_id,
            guest_id,
        });
    }

    pub fn subscribe(&self) -> broadcast::Receiver<LoyaltyMemberUpdatedEvent> {
        self.sender.subscribe()
    }
}

pub async fn serve_guest_socket(
    mut socket: WebSocket,
    hub: LoyaltyHub,
    pool: crate::core::db::DbPool,
    guest_id: i64,
    token: String,
) {
    let mut events = hub.subscribe();
    let mut session_check = tokio::time::interval(std::time::Duration::from_secs(30));
    session_check.tick().await;
    loop {
        tokio::select! {
            _ = session_check.tick() => {
                match crate::modules::guest_portal::service::is_guest_session_token_active(&token, &pool).await {
                    Ok(true) => {}
                    Ok(false) => {
                        let _ = socket.send(Message::Close(None)).await;
                        break;
                    }
                    Err(error) => {
                        log::warn!("guest loyalty websocket session check failed: {error}");
                    }
                }
            }
            event = events.recv() => match event {
                Ok(event) => {
                    if event.guest_id != guest_id {
                        continue;
                    }
                    let Ok(payload) = serde_json::to_string(&event) else {
                        continue;
                    };
                    if socket.send(Message::Text(payload.into())).await.is_err() {
                        break;
                    }
                }
                Err(broadcast::error::RecvError::Lagged(skipped)) => {
                    log::warn!("guest loyalty websocket lagged; skipped {skipped} events");
                    continue;
                }
                Err(broadcast::error::RecvError::Closed) => break,
            },
            message = socket.recv() => match message {
                Some(Ok(Message::Ping(payload)))
                    if socket.send(Message::Pong(payload.clone())).await.is_err() => break,
                Some(Ok(Message::Close(_))) | None | Some(Err(_)) => break,
                _ => {}
            }
        }
    }
}

pub async fn serve_socket(
    mut socket: WebSocket,
    hub: LoyaltyHub,
    pool: crate::core::db::DbPool,
    user_id: i64,
    session_id: String,
) {
    let mut events = hub.subscribe();
    let mut session_check = tokio::time::interval(std::time::Duration::from_secs(30));
    session_check.tick().await;
    loop {
        tokio::select! {
            _ = session_check.tick() => {
                match crate::core::auth::AuthService::is_session_active(&pool, user_id, &session_id).await {
                    Ok(true) => {
                        if crate::core::middleware::check_any_permission(
                            &pool,
                            user_id,
                            &["loyalty:read", "loyalty:manage", "analytics:read"],
                        )
                        .await
                        .is_err()
                        {
                            let _ = socket.send(Message::Close(None)).await;
                            break;
                        }
                    }
                    Ok(false) => {
                        let _ = socket.send(Message::Close(None)).await;
                        break;
                    }
                    Err(error) => {
                        log::warn!("loyalty websocket session check failed: {error}");
                    }
                }
            }
            event = events.recv() => match event {
                Ok(event) => {
                    let Ok(payload) = serde_json::to_string(&event) else { continue; };
                    if socket.send(Message::Text(payload.into())).await.is_err() { break; }
                }
                Err(broadcast::error::RecvError::Lagged(skipped)) => {
                    log::warn!("loyalty websocket lagged; skipped {skipped} events");
                    continue;
                }
                Err(broadcast::error::RecvError::Closed) => break,
            },
            message = socket.recv() => match message {
                Some(Ok(Message::Ping(payload))) if socket.send(Message::Pong(payload.clone())).await.is_err() => break,
                Some(Ok(Message::Close(_))) | None | Some(Err(_)) => break,
                _ => {}
            }
        }
    }
}
