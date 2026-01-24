mod mdns;
mod notification;
mod server;
mod tls;

use server::{Server, ServerEvent};
use tokio::sync::mpsc;

#[tokio::main]
async fn main() {
    env_logger::init();

    let port = 9876;

    // Start mDNS
    let _advertiser = mdns::MdnsAdvertiser::new(port).expect("Could not start mDNS");

    // Start server
    let (event_tx, mut event_rx) = mpsc::channel(100);
    let server = Server::new(port);

    tokio::spawn(async move {
        if let Err(e) = server.run(event_tx).await {
            log::error!("Server error: {}", e);
        }
    });

    println!("NotiSync running on port {}", port);
    println!("Waiting for phone to connect...");

    // Handle events
    while let Some(event) = event_rx.recv().await {
        match event {
            ServerEvent::ClientConnected(id) => {
                println!("Phone connected: {}", id);
            }
            ServerEvent::ClientDisconnected(id) => {
                println!("Phone disconnected: {}", id);
            }
            ServerEvent::NotificationReceived(notif) => {
                println!("Notification from {}: {:?}", notif.app_name, notif.title);
                if let Err(e) = notif.display() {
                    log::error!("Failed to display notification: {}", e);
                }
            }
        }
    }
}
