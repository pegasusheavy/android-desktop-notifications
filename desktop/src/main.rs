mod mdns;
mod notification;
mod server;
mod tls;
mod tray;

use server::{Server, ServerEvent};
use tray::{Tray, TrayCommand};
use tokio::sync::mpsc;
use std::time::Duration;

/// Initialize platform-specific UI toolkit
fn init_platform() {
    #[cfg(target_os = "linux")]
    {
        gtk::init().expect("Failed to initialize GTK");
    }
}

/// Process platform-specific events
fn process_platform_events() {
    #[cfg(target_os = "linux")]
    {
        while gtk::events_pending() {
            gtk::main_iteration();
        }
    }
    // On Windows and macOS, tray-icon handles event processing internally
}

#[tokio::main]
async fn main() {
    env_logger::init();

    // Initialize platform-specific UI
    init_platform();

    let port = 9876;

    // Start mDNS
    let advertiser = mdns::MdnsAdvertiser::new(port).expect("Could not start mDNS");

    // Start server
    let (event_tx, mut event_rx) = mpsc::channel(100);
    let server = Server::new(port);

    tokio::spawn(async move {
        if let Err(e) = server.run(event_tx).await {
            log::error!("Server error: {}", e);
        }
    });

    // Create tray icon
    let tray = Tray::new("Waiting for connection...").expect("Could not create tray icon");

    log::info!("NotiSync running on port {}", port);

    // Main event loop
    loop {
        // Process platform events (required for tray icon)
        process_platform_events();

        // Check for tray commands
        if let Some(TrayCommand::Quit) = tray.try_recv_command() {
            log::info!("Quit requested");
            break;
        }

        // Check for server events (non-blocking)
        match tokio::time::timeout(Duration::from_millis(50), event_rx.recv()).await {
            Ok(Some(event)) => match event {
                ServerEvent::ClientConnected(id) => {
                    log::info!("Phone connected: {}", id);
                    // Show connection notification
                    let _ = notify_rust::Notification::new()
                        .summary("NotiSync")
                        .body(&format!("Phone connected: {}", id))
                        .timeout(notify_rust::Timeout::Milliseconds(3000))
                        .show();
                }
                ServerEvent::ClientDisconnected(id) => {
                    log::info!("Phone disconnected: {}", id);
                }
                ServerEvent::NotificationReceived(notif) => {
                    log::debug!("Notification from {}", notif.app_name);
                    if let Err(e) = notif.display() {
                        log::error!("Failed to display notification: {}", e);
                    }
                }
            },
            Ok(None) => {
                // Channel closed
                break;
            }
            Err(_) => {
                // Timeout, continue loop
            }
        }
    }

    advertiser.shutdown();
}
