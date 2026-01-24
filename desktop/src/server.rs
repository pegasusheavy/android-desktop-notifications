use crate::notification::PhoneNotification;
use crate::tls::create_tls_acceptor;
use futures_util::StreamExt;
use std::net::SocketAddr;
use tokio::net::TcpListener;
use tokio::sync::mpsc;
use tokio_tungstenite::accept_async;

/// Events emitted by the WebSocket server
#[derive(Debug, Clone, PartialEq)]
pub enum ServerEvent {
    ClientConnected(String),
    ClientDisconnected(String),
    NotificationReceived(PhoneNotification),
}

impl ServerEvent {
    /// Check if this is a connection event
    pub fn is_connection(&self) -> bool {
        matches!(self, ServerEvent::ClientConnected(_))
    }

    /// Check if this is a disconnection event
    pub fn is_disconnection(&self) -> bool {
        matches!(self, ServerEvent::ClientDisconnected(_))
    }

    /// Check if this is a notification event
    pub fn is_notification(&self) -> bool {
        matches!(self, ServerEvent::NotificationReceived(_))
    }

    /// Get the client ID if this is a connection or disconnection event
    pub fn client_id(&self) -> Option<&str> {
        match self {
            ServerEvent::ClientConnected(id) | ServerEvent::ClientDisconnected(id) => Some(id),
            ServerEvent::NotificationReceived(_) => None,
        }
    }

    /// Get the notification if this is a notification event
    pub fn notification(&self) -> Option<&PhoneNotification> {
        match self {
            ServerEvent::NotificationReceived(notif) => Some(notif),
            _ => None,
        }
    }
}

/// WebSocket server configuration and state
pub struct Server {
    port: u16,
}

impl Server {
    pub fn new(port: u16) -> Self {
        Self { port }
    }

    /// Get the configured port
    pub fn port(&self) -> u16 {
        self.port
    }

    /// Get the socket address the server will bind to
    pub fn socket_addr(&self) -> SocketAddr {
        SocketAddr::from(([0, 0, 0, 0], self.port))
    }

    pub async fn run(
        &self,
        event_tx: mpsc::Sender<ServerEvent>,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let addr = self.socket_addr();
        let listener = TcpListener::bind(addr).await?;
        let tls_acceptor = create_tls_acceptor();

        log::info!("WebSocket server listening on {}", addr);

        loop {
            let (stream, peer_addr) = listener.accept().await?;
            let tls_acceptor = tls_acceptor.clone();
            let event_tx = event_tx.clone();

            tokio::spawn(async move {
                if let Err(e) =
                    handle_connection(stream, peer_addr, tls_acceptor, event_tx).await
                {
                    log::error!("Connection error from {}: {}", peer_addr, e);
                }
            });
        }
    }
}

/// Parse a JSON message into a PhoneNotification
pub fn parse_notification(json: &str) -> Result<PhoneNotification, serde_json::Error> {
    serde_json::from_str(json)
}

async fn handle_connection(
    stream: tokio::net::TcpStream,
    peer_addr: SocketAddr,
    tls_acceptor: tokio_rustls::TlsAcceptor,
    event_tx: mpsc::Sender<ServerEvent>,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    log::info!("New connection from {}", peer_addr);

    let tls_stream = tls_acceptor.accept(stream).await?;
    let ws_stream = accept_async(tls_stream).await?;

    let client_id = peer_addr.to_string();
    event_tx
        .send(ServerEvent::ClientConnected(client_id.clone()))
        .await?;

    let (_, mut read) = ws_stream.split();

    while let Some(msg) = read.next().await {
        match msg {
            Ok(tokio_tungstenite::tungstenite::Message::Text(text)) => {
                match parse_notification(&text) {
                    Ok(notif) => {
                        log::debug!("Received notification: {:?}", notif);
                        event_tx
                            .send(ServerEvent::NotificationReceived(notif))
                            .await?;
                    }
                    Err(e) => {
                        log::warn!("Failed to parse notification: {}", e);
                    }
                }
            }
            Ok(tokio_tungstenite::tungstenite::Message::Close(_)) => {
                break;
            }
            Err(e) => {
                log::error!("WebSocket error: {}", e);
                break;
            }
            _ => {}
        }
    }

    event_tx
        .send(ServerEvent::ClientDisconnected(client_id))
        .await?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_test_notification() -> PhoneNotification {
        PhoneNotification {
            id: "test-id".to_string(),
            app_package: "com.test".to_string(),
            app_name: "Test".to_string(),
            title: Some("Title".to_string()),
            text: Some("Text".to_string()),
            timestamp: 12345,
            icon: None,
        }
    }

    #[test]
    fn test_server_new() {
        let server = Server::new(8080);
        assert_eq!(server.port(), 8080);
    }

    #[test]
    fn test_server_socket_addr() {
        let server = Server::new(9876);
        let addr = server.socket_addr();

        assert_eq!(addr.port(), 9876);
        assert!(addr.ip().is_unspecified());
    }

    #[test]
    fn test_server_event_client_connected() {
        let event = ServerEvent::ClientConnected("192.168.1.1:12345".to_string());

        assert!(event.is_connection());
        assert!(!event.is_disconnection());
        assert!(!event.is_notification());
        assert_eq!(event.client_id(), Some("192.168.1.1:12345"));
        assert!(event.notification().is_none());
    }

    #[test]
    fn test_server_event_client_disconnected() {
        let event = ServerEvent::ClientDisconnected("192.168.1.1:12345".to_string());

        assert!(!event.is_connection());
        assert!(event.is_disconnection());
        assert!(!event.is_notification());
        assert_eq!(event.client_id(), Some("192.168.1.1:12345"));
        assert!(event.notification().is_none());
    }

    #[test]
    fn test_server_event_notification_received() {
        let notif = create_test_notification();
        let event = ServerEvent::NotificationReceived(notif.clone());

        assert!(!event.is_connection());
        assert!(!event.is_disconnection());
        assert!(event.is_notification());
        assert!(event.client_id().is_none());
        assert_eq!(event.notification(), Some(&notif));
    }

    #[test]
    fn test_server_event_clone() {
        let event = ServerEvent::ClientConnected("test".to_string());
        let cloned = event.clone();

        assert_eq!(event, cloned);
    }

    #[test]
    fn test_server_event_debug() {
        let event = ServerEvent::ClientConnected("test".to_string());
        let debug_str = format!("{:?}", event);

        assert!(debug_str.contains("ClientConnected"));
        assert!(debug_str.contains("test"));
    }

    #[test]
    fn test_parse_notification_valid() {
        let json = r#"{
            "id": "notif-1",
            "app_package": "com.example",
            "app_name": "Example",
            "title": "Hello",
            "text": "World",
            "timestamp": 1234567890
        }"#;

        let result = parse_notification(json);
        assert!(result.is_ok());

        let notif = result.unwrap();
        assert_eq!(notif.id, "notif-1");
        assert_eq!(notif.app_name, "Example");
    }

    #[test]
    fn test_parse_notification_invalid_json() {
        let json = "not valid json";
        let result = parse_notification(json);

        assert!(result.is_err());
    }

    #[test]
    fn test_parse_notification_missing_required_fields() {
        let json = r#"{"id": "test"}"#;
        let result = parse_notification(json);

        assert!(result.is_err());
    }

    #[test]
    fn test_parse_notification_with_optional_fields() {
        let json = r#"{
            "id": "notif-2",
            "app_package": "com.test",
            "app_name": "Test",
            "title": null,
            "text": null,
            "timestamp": 0
        }"#;

        let result = parse_notification(json);
        assert!(result.is_ok());

        let notif = result.unwrap();
        assert!(notif.title.is_none());
        assert!(notif.text.is_none());
        assert!(notif.icon.is_none());
    }

    #[tokio::test]
    async fn test_server_event_channel() {
        let (tx, mut rx) = mpsc::channel(10);

        tx.send(ServerEvent::ClientConnected("test".to_string()))
            .await
            .unwrap();
        tx.send(ServerEvent::NotificationReceived(create_test_notification()))
            .await
            .unwrap();
        tx.send(ServerEvent::ClientDisconnected("test".to_string()))
            .await
            .unwrap();

        let event1 = rx.recv().await.unwrap();
        assert!(event1.is_connection());

        let event2 = rx.recv().await.unwrap();
        assert!(event2.is_notification());

        let event3 = rx.recv().await.unwrap();
        assert!(event3.is_disconnection());
    }
}
