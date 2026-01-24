use crate::notification::PhoneNotification;
use crate::tls::create_tls_acceptor;
use futures_util::StreamExt;
use std::net::SocketAddr;
use tokio::net::TcpListener;
use tokio::sync::mpsc;
use tokio_tungstenite::accept_async;

#[derive(Debug, Clone)]
pub enum ServerEvent {
    ClientConnected(String),
    ClientDisconnected(String),
    NotificationReceived(PhoneNotification),
}

pub struct Server {
    port: u16,
}

impl Server {
    pub fn new(port: u16) -> Self {
        Self { port }
    }

    pub async fn run(
        &self,
        event_tx: mpsc::Sender<ServerEvent>,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let addr = SocketAddr::from(([0, 0, 0, 0], self.port));
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
                match serde_json::from_str::<PhoneNotification>(&text) {
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
