# Notification Sync Design

Sync Android notifications to a Linux desktop over a local network.

## Requirements

- Sync all notifications from phone to desktop
- Auto-discovery via mDNS (no manual IP configuration)
- Display-only (no interaction/dismiss sync)
- WebSocket communication
- Native system notifications on desktop
- TLS encryption

## Architecture

Two applications:

1. **Android App (Kotlin)** - Notification listener service that captures all notifications and forwards them over WebSocket.

2. **Rust Desktop App** - System tray application that receives notifications and displays them natively.

### Data Flow

```
Phone Notification → NotificationListenerService → JSON → WebSocket/TLS → Rust Server → libnotify
```

### Discovery Flow

```
Rust app advertises "_notisync._tcp" via mDNS
Android app discovers service, connects via WebSocket
TLS handshake (trust-on-first-use)
Phone sends notifications in real-time
```

## Notification Data Model

```json
{
  "id": "unique-notification-id",
  "app_package": "com.whatsapp",
  "app_name": "WhatsApp",
  "title": "John Doe",
  "text": "Hey, are you free tonight?",
  "timestamp": 1706123456789,
  "icon": "base64-encoded-small-icon"
}
```

## Android App

### Components

- **NotificationListenerService** - System service receiving all notifications. Requires "Notification Access" permission.
- **ConnectionManager** - mDNS discovery via NsdManager, WebSocket connection management, auto-reconnection.
- **UI** - Single screen showing connection status and enable/disable toggle.

### Dependencies

- OkHttp (WebSocket client)
- Gson (JSON serialization)
- AndroidX (UI components)

## Rust Desktop App

### Components

- **mDNS Advertiser** - Broadcasts `_notisync._tcp.local` service.
- **WebSocket Server** - Async WebSocket over TLS using tokio-tungstenite.
- **TLS Layer** - Self-signed certificate generated on first run, stored in `~/.config/notisync/`.
- **Notification Display** - Uses notify-rust (libnotify wrapper).
- **System Tray** - Background app with tray icon showing connection status.

### Tray Menu

- Connection status ("Connected to: Device Name" or "Waiting for connection...")
- Quit

### Dependencies

```toml
tokio = "1"
tokio-tungstenite = "0.21"
mdns-sd = "0.10"
notify-rust = "4"
serde = "1"
serde_json = "1"
rcgen = "0.12"
rustls = "0.22"
tray-icon = "0.14"
winit = "0.29"
image = "0.24"
```

## Error Handling

### Android App

- Auto-reconnect every 5 seconds on disconnect
- Continue mDNS scanning if no service found
- Queue last 10 notifications during brief disconnections
- Clear connection state in UI

### Rust Desktop App

- Continue advertising after client disconnects
- Skip malformed JSON (log error)
- Log notification display failures without crashing
- Show "Phone connected" notification on new connection

## Project Structure

```
android-desktop-notifications/
├── android/                    # Android Kotlin app
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/.../
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── NotificationService.kt
│   │   │   │   └── ConnectionManager.kt
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── desktop/                    # Rust desktop app
│   ├── src/
│   │   ├── main.rs
│   │   ├── mdns.rs
│   │   ├── server.rs
│   │   ├── tls.rs
│   │   ├── notification.rs
│   │   └── tray.rs
│   └── Cargo.toml
└── docs/
    └── plans/
        └── 2026-01-24-notification-sync-design.md
```
