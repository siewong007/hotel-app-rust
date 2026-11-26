use axum::extract::ws::{Message, WebSocket};
use serde::Serialize;
use tokio::sync::broadcast;

#[derive(Debug, Clone, Serialize)]
pub struct SupportEvent {
    pub event_id: String,
    pub event_type: &'static str,
    #[serde(skip)]
    pub guest_id: i64,
    pub conversation_id: i64,
}

impl SupportEvent {
    pub fn conversation_changed(guest_id: i64, conversation_id: i64) -> Self {
        Self {
            event_id: uuid::Uuid::new_v4().to_string(),
            event_type: "conversation_changed",
            guest_id,
            conversation_id,
        }
    }
}

/// Fan-out hub for guest support updates. A single broadcast channel is
/// shared by every connected guest socket; `serve_socket` filters events down
/// to the connecting guest's own `guest_id` so one guest's socket never
/// observes another guest's conversation activity.
#[derive(Debug, Clone)]
pub struct SupportHub {
    sender: broadcast::Sender<SupportEvent>,
}

impl Default for SupportHub {
    fn default() -> Self {
        let (sender, _) = broadcast::channel(256);
        Self { sender }
    }
}

impl SupportHub {
    pub fn subscribe(&self) -> broadcast::Receiver<SupportEvent> {
        self.sender.subscribe()
    }

    pub fn publish(&self, event: SupportEvent) {
        let _ = self.sender.send(event);
    }
}

pub async fn serve_socket(
    mut socket: WebSocket,
    hub: SupportHub,
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
                        log::warn!("guest support websocket session check failed: {error}");
                    }
                }
            }
            event = events.recv() => {
                match event {
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
                        log::warn!("guest support websocket lagged; skipped {skipped} events");
                        continue;
                    }
                    Err(broadcast::error::RecvError::Closed) => break,
                }
            }
            message = socket.recv() => {
                match message {
                    Some(Ok(Message::Ping(payload)))
                        if socket.send(Message::Pong(payload.clone())).await.is_err() => break,
                    Some(Ok(Message::Close(_))) | None | Some(Err(_)) => break,
                    _ => {}
                }
            }
        }
    }
}
