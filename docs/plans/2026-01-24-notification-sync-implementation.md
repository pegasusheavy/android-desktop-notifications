# Notification Sync Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an Android app and Rust desktop app that sync phone notifications to the desktop over a local network.

**Architecture:** Android NotificationListenerService captures notifications, serializes to JSON, sends via WebSocket/TLS. Rust system tray app advertises via mDNS, accepts connections, displays notifications via libnotify.

**Tech Stack:** Kotlin/Android (OkHttp, Gson, NsdManager), Rust (tokio, tokio-tungstenite, mdns-sd, notify-rust, tray-icon, rustls)

---

## Phase 1: Rust Desktop App

### Task 1: Initialize Rust Project

**Files:**
- Create: `desktop/Cargo.toml`
- Create: `desktop/src/main.rs`

**Step 1: Create project structure**

```bash
mkdir -p desktop/src
```

**Step 2: Create Cargo.toml**

Create `desktop/Cargo.toml`:

```toml
[package]
name = "notisync"
version = "0.1.0"
edition = "2021"

[dependencies]
tokio = { version = "1", features = ["full"] }
tokio-tungstenite = { version = "0.24", features = ["rustls-tls-native-roots"] }
tokio-rustls = "0.26"
rustls = "0.23"
rustls-pemfile = "2"
rcgen = "0.13"
mdns-sd = "0.11"
notify-rust = "4"
serde = { version = "1", features = ["derive"] }
serde_json = "1"
tray-icon = "0.19"
image = "0.25"
directories = "5"
log = "0.4"
env_logger = "0.11"
```

**Step 3: Create minimal main.rs**

Create `desktop/src/main.rs`:

```rust
fn main() {
    println!("notisync starting...");
}
```

**Step 4: Verify project builds**

Run: `cd desktop && cargo build`
Expected: Build succeeds

**Step 5: Commit**

```bash
git add desktop/
git commit -m "feat(desktop): initialize Rust project with dependencies"
```

---

### Task 2: Notification Data Model

**Files:**
- Create: `desktop/src/notification.rs`
- Modify: `desktop/src/main.rs`

**Step 1: Create notification module**

Create `desktop/src/notification.rs`:

```rust
use serde::Deserialize;

#[derive(Debug, Deserialize)]
pub struct PhoneNotification {
    pub id: String,
    pub app_package: String,
    pub app_name: String,
    pub title: Option<String>,
    pub text: Option<String>,
    pub timestamp: u64,
    #[serde(default)]
    pub icon: Option<String>,
}

impl PhoneNotification {
    pub fn display(&self) -> Result<(), Box<dyn std::error::Error>> {
        let title = format!("{}", self.app_name);
        let body = match (&self.title, &self.text) {
            (Some(t), Some(txt)) => format!("{}: {}", t, txt),
            (Some(t), None) => t.clone(),
            (None, Some(txt)) => txt.clone(),
            (None, None) => "New notification".to_string(),
        };

        notify_rust::Notification::new()
            .summary(&title)
            .body(&body)
            .timeout(notify_rust::Timeout::Milliseconds(5000))
            .show()?;

        Ok(())
    }
}
```

**Step 2: Update main.rs to use module**

Replace `desktop/src/main.rs`:

```rust
mod notification;

use notification::PhoneNotification;

fn main() {
    // Test notification parsing
    let json = r#"{
        "id": "test-1",
        "app_package": "com.test",
        "app_name": "Test App",
        "title": "Hello",
        "text": "World",
        "timestamp": 1706123456789
    }"#;

    let notif: PhoneNotification = serde_json::from_str(json).unwrap();
    println!("Parsed: {:?}", notif);
    notif.display().unwrap();
}
```

**Step 3: Verify it builds and shows a test notification**

Run: `cd desktop && cargo run`
Expected: Shows a desktop notification "Test App: Hello: World"

**Step 4: Commit**

```bash
git add desktop/src/
git commit -m "feat(desktop): add notification data model and display"
```

---

### Task 3: TLS Certificate Generation

**Files:**
- Create: `desktop/src/tls.rs`
- Modify: `desktop/src/main.rs`

**Step 1: Create TLS module**

Create `desktop/src/tls.rs`:

```rust
use rcgen::{CertifiedKey, generate_simple_self_signed};
use rustls::pki_types::{CertificateDer, PrivateKeyDer};
use std::fs;
use std::path::PathBuf;
use std::sync::Arc;
use tokio_rustls::TlsAcceptor;

pub fn get_config_dir() -> PathBuf {
    let proj_dirs = directories::ProjectDirs::from("", "", "notisync")
        .expect("Could not determine config directory");
    let config_dir = proj_dirs.config_dir().to_path_buf();
    fs::create_dir_all(&config_dir).expect("Could not create config directory");
    config_dir
}

pub fn get_or_create_cert() -> (Vec<CertificateDer<'static>>, PrivateKeyDer<'static>) {
    let config_dir = get_config_dir();
    let cert_path = config_dir.join("cert.pem");
    let key_path = config_dir.join("key.pem");

    if cert_path.exists() && key_path.exists() {
        log::info!("Loading existing certificate from {:?}", config_dir);
        let cert_pem = fs::read_to_string(&cert_path).expect("Could not read cert");
        let key_pem = fs::read_to_string(&key_path).expect("Could not read key");

        let cert = rustls_pemfile::certs(&mut cert_pem.as_bytes())
            .map(|r| r.expect("Could not parse cert"))
            .collect();

        let key = rustls_pemfile::private_key(&mut key_pem.as_bytes())
            .expect("Could not parse key")
            .expect("No key found");

        (cert, key)
    } else {
        log::info!("Generating new self-signed certificate");
        let subject_alt_names = vec!["localhost".to_string(), "notisync.local".to_string()];
        let CertifiedKey { cert, key_pair } = generate_simple_self_signed(subject_alt_names)
            .expect("Could not generate certificate");

        let cert_pem = cert.pem();
        let key_pem = key_pair.serialize_pem();

        fs::write(&cert_path, &cert_pem).expect("Could not write cert");
        fs::write(&key_path, &key_pem).expect("Could not write key");

        let cert_der = rustls_pemfile::certs(&mut cert_pem.as_bytes())
            .map(|r| r.expect("Could not parse cert"))
            .collect();

        let key_der = rustls_pemfile::private_key(&mut key_pem.as_bytes())
            .expect("Could not parse key")
            .expect("No key found");

        (cert_der, key_der)
    }
}

pub fn create_tls_acceptor() -> TlsAcceptor {
    let (certs, key) = get_or_create_cert();

    let config = rustls::ServerConfig::builder()
        .with_no_client_auth()
        .with_single_cert(certs, key)
        .expect("Could not create TLS config");

    TlsAcceptor::from(Arc::new(config))
}
```

**Step 2: Update main.rs to test TLS**

Replace `desktop/src/main.rs`:

```rust
mod notification;
mod tls;

fn main() {
    env_logger::init();

    println!("Testing TLS certificate generation...");
    let _acceptor = tls::create_tls_acceptor();
    println!("TLS acceptor created successfully");
    println!("Config dir: {:?}", tls::get_config_dir());
}
```

**Step 3: Verify it creates certificates**

Run: `cd desktop && RUST_LOG=info cargo run`
Expected: Creates certificates in ~/.config/notisync/ (or equivalent)

**Step 4: Commit**

```bash
git add desktop/src/
git commit -m "feat(desktop): add TLS certificate generation"
```

---

### Task 4: mDNS Service Advertisement

**Files:**
- Create: `desktop/src/mdns.rs`
- Modify: `desktop/src/main.rs`

**Step 1: Create mDNS module**

Create `desktop/src/mdns.rs`:

```rust
use mdns_sd::{ServiceDaemon, ServiceInfo};
use std::collections::HashMap;

const SERVICE_TYPE: &str = "_notisync._tcp.local.";
const SERVICE_NAME: &str = "notisync";

pub struct MdnsAdvertiser {
    daemon: ServiceDaemon,
    fullname: String,
}

impl MdnsAdvertiser {
    pub fn new(port: u16) -> Result<Self, Box<dyn std::error::Error>> {
        let daemon = ServiceDaemon::new()?;

        let hostname = gethostname::gethostname()
            .into_string()
            .unwrap_or_else(|_| "desktop".to_string());

        let service_hostname = format!("{}.local.", hostname);
        let instance_name = format!("{}@{}", SERVICE_NAME, hostname);

        let properties: HashMap<String, String> = HashMap::new();

        let service_info = ServiceInfo::new(
            SERVICE_TYPE,
            &instance_name,
            &service_hostname,
            (),
            port,
            properties,
        )?;

        let fullname = service_info.get_fullname().to_string();
        daemon.register(service_info)?;

        log::info!("mDNS: Advertising {} on port {}", fullname, port);

        Ok(Self { daemon, fullname })
    }

    pub fn shutdown(self) {
        let _ = self.daemon.unregister(&self.fullname);
        let _ = self.daemon.shutdown();
    }
}
```

**Step 2: Add gethostname dependency**

Update `desktop/Cargo.toml` dependencies section, add:

```toml
gethostname = "0.5"
```

**Step 3: Update main.rs to test mDNS**

Replace `desktop/src/main.rs`:

```rust
mod mdns;
mod notification;
mod tls;

fn main() {
    env_logger::init();

    let port = 9876;
    println!("Starting mDNS advertisement on port {}...", port);

    let advertiser = mdns::MdnsAdvertiser::new(port).expect("Could not start mDNS");

    println!("Press Ctrl+C to stop");
    std::thread::sleep(std::time::Duration::from_secs(30));

    advertiser.shutdown();
}
```

**Step 4: Verify mDNS is advertising**

Run: `cd desktop && RUST_LOG=info cargo run`

In another terminal, verify with: `avahi-browse -r _notisync._tcp`
Expected: Shows the notisync service

**Step 5: Commit**

```bash
git add desktop/
git commit -m "feat(desktop): add mDNS service advertisement"
```

---

### Task 5: WebSocket Server

**Files:**
- Create: `desktop/src/server.rs`
- Modify: `desktop/src/main.rs`

**Step 1: Create server module**

Create `desktop/src/server.rs`:

```rust
use crate::notification::PhoneNotification;
use crate::tls::create_tls_acceptor;
use futures_util::StreamExt;
use std::net::SocketAddr;
use std::sync::Arc;
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
    let ws_stream = accept_async(tokio_tungstenite::MaybeTlsStream::Rustls(tls_stream)).await?;

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
```

**Step 2: Add futures-util dependency**

Update `desktop/Cargo.toml` dependencies section, add:

```toml
futures-util = "0.3"
```

**Step 3: Update main.rs with async runtime**

Replace `desktop/src/main.rs`:

```rust
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
```

**Step 4: Verify it compiles**

Run: `cd desktop && cargo build`
Expected: Build succeeds

**Step 5: Commit**

```bash
git add desktop/
git commit -m "feat(desktop): add WebSocket server with TLS"
```

---

### Task 6: System Tray Integration

**Files:**
- Create: `desktop/src/tray.rs`
- Create: `desktop/assets/icon.png`
- Modify: `desktop/src/main.rs`

**Step 1: Create tray icon asset**

Create a simple 32x32 PNG icon. For now, we'll generate one programmatically.

```bash
mkdir -p desktop/assets
```

**Step 2: Create tray module**

Create `desktop/src/tray.rs`:

```rust
use std::sync::mpsc as std_mpsc;
use tray_icon::{
    menu::{Menu, MenuEvent, MenuItem},
    TrayIcon, TrayIconBuilder,
};

pub enum TrayCommand {
    Quit,
}

pub struct Tray {
    _tray_icon: TrayIcon,
    menu_rx: std_mpsc::Receiver<TrayCommand>,
}

impl Tray {
    pub fn new(status: &str) -> Result<Self, Box<dyn std::error::Error>> {
        let (menu_tx, menu_rx) = std_mpsc::channel();

        let menu = Menu::new();
        let status_item = MenuItem::new(status, false, None);
        let quit_item = MenuItem::new("Quit", true, None);

        let quit_id = quit_item.id().clone();

        menu.append(&status_item)?;
        menu.append(&quit_item)?;

        // Create a simple colored icon
        let icon = create_icon()?;

        let tray_icon = TrayIconBuilder::new()
            .with_menu(Box::new(menu))
            .with_tooltip("NotiSync")
            .with_icon(icon)
            .build()?;

        // Handle menu events in a thread
        std::thread::spawn(move || {
            loop {
                if let Ok(event) = MenuEvent::receiver().recv() {
                    if event.id == quit_id {
                        let _ = menu_tx.send(TrayCommand::Quit);
                    }
                }
            }
        });

        Ok(Self {
            _tray_icon: tray_icon,
            menu_rx,
        })
    }

    pub fn try_recv_command(&self) -> Option<TrayCommand> {
        self.menu_rx.try_recv().ok()
    }
}

fn create_icon() -> Result<tray_icon::Icon, Box<dyn std::error::Error>> {
    // Create a simple 32x32 blue icon
    let size = 32u32;
    let mut rgba = Vec::with_capacity((size * size * 4) as usize);

    for y in 0..size {
        for x in 0..size {
            // Create a simple circle
            let dx = x as f32 - size as f32 / 2.0;
            let dy = y as f32 - size as f32 / 2.0;
            let dist = (dx * dx + dy * dy).sqrt();

            if dist < size as f32 / 2.0 - 2.0 {
                // Blue color
                rgba.extend_from_slice(&[66, 133, 244, 255]);
            } else {
                // Transparent
                rgba.extend_from_slice(&[0, 0, 0, 0]);
            }
        }
    }

    Ok(tray_icon::Icon::from_rgba(rgba, size, size)?)
}
```

**Step 3: Update main.rs for tray integration**

Replace `desktop/src/main.rs`:

```rust
mod mdns;
mod notification;
mod server;
mod tls;
mod tray;

use server::{Server, ServerEvent};
use tray::{Tray, TrayCommand};
use tokio::sync::mpsc;
use std::time::Duration;

#[tokio::main]
async fn main() {
    env_logger::init();

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
        // Check for tray commands
        if let Some(TrayCommand::Quit) = tray.try_recv_command() {
            log::info!("Quit requested");
            break;
        }

        // Check for server events (non-blocking)
        match tokio::time::timeout(Duration::from_millis(100), event_rx.recv()).await {
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
```

**Step 4: Verify tray app works**

Run: `cd desktop && RUST_LOG=info cargo run`
Expected: System tray icon appears, right-click shows menu with "Quit"

**Step 5: Commit**

```bash
git add desktop/
git commit -m "feat(desktop): add system tray integration"
```

---

## Phase 2: Android App

### Task 7: Initialize Android Project

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`

**Step 1: Create Android project structure**

```bash
mkdir -p android/app/src/main/java/com/notisync
mkdir -p android/app/src/main/res/layout
mkdir -p android/app/src/main/res/values
mkdir -p android/app/src/main/res/drawable
mkdir -p android/gradle/wrapper
```

**Step 2: Create settings.gradle.kts**

Create `android/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NotiSync"
include(":app")
```

**Step 3: Create root build.gradle.kts**

Create `android/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
```

**Step 4: Create gradle.properties**

Create `android/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

**Step 5: Create app/build.gradle.kts**

Create `android/app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.notisync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.notisync"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // OkHttp for WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson for JSON
    implementation("com.google.code.gson:gson:2.10.1")
}
```

**Step 6: Create AndroidManifest.xml**

Create `android/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.NotiSync"
        android:networkSecurityConfig="@xml/network_security_config">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".NotificationService"
            android:exported="true"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

**Step 7: Create resource files**

Create `android/app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">NotiSync</string>
</resources>
```

Create `android/app/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.NotiSync" parent="Theme.Material3.Light.NoActionBar">
        <item name="colorPrimary">@color/blue_500</item>
        <item name="colorPrimaryContainer">@color/blue_100</item>
    </style>
</resources>
```

Create `android/app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="blue_500">#4285F4</color>
    <color name="blue_100">#D2E3FC</color>
    <color name="green_500">#34A853</color>
    <color name="red_500">#EA4335</color>
</resources>
```

Create `android/app/src/main/res/xml/network_security_config.xml`:

```bash
mkdir -p android/app/src/main/res/xml
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

**Step 8: Create Gradle wrapper**

```bash
cd android && gradle wrapper --gradle-version 8.4
```

**Step 9: Commit**

```bash
git add android/
git commit -m "feat(android): initialize Android project structure"
```

---

### Task 8: Create Main Activity UI

**Files:**
- Create: `android/app/src/main/res/layout/activity_main.xml`
- Create: `android/app/src/main/java/com/notisync/MainActivity.kt`

**Step 1: Create layout**

Create `android/app/src/main/res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="24dp">

    <TextView
        android:id="@+id/titleText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="NotiSync"
        android:textSize="32sp"
        android:textStyle="bold"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="48dp" />

    <View
        android:id="@+id/statusIndicator"
        android:layout_width="16dp"
        android:layout_height="16dp"
        android:background="@drawable/circle_red"
        app:layout_constraintTop_toBottomOf="@id/titleText"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="32dp" />

    <TextView
        android:id="@+id/statusText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Disconnected"
        android:textSize="18sp"
        app:layout_constraintTop_toBottomOf="@id/statusIndicator"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="8dp" />

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/enableSwitch"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Enable Notification Sync"
        android:textSize="16sp"
        app:layout_constraintTop_toBottomOf="@id/statusText"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="48dp" />

    <Button
        android:id="@+id/permissionButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Grant Notification Access"
        app:layout_constraintTop_toBottomOf="@id/enableSwitch"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="24dp" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

**Step 2: Create drawable resources**

Create `android/app/src/main/res/drawable/circle_red.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/red_500" />
</shape>
```

Create `android/app/src/main/res/drawable/circle_green.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/green_500" />
</shape>
```

**Step 3: Create MainActivity**

Create `android/app/src/main/java/com/notisync/MainActivity.kt`:

```kotlin
package com.notisync

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var statusIndicator: View
    private lateinit var statusText: TextView
    private lateinit var enableSwitch: SwitchMaterial
    private lateinit var permissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusIndicator = findViewById(R.id.statusIndicator)
        statusText = findViewById(R.id.statusText)
        enableSwitch = findViewById(R.id.enableSwitch)
        permissionButton = findViewById(R.id.permissionButton)

        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("notisync", MODE_PRIVATE)
            prefs.edit().putBoolean("enabled", isChecked).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val hasPermission = isNotificationListenerEnabled()
        permissionButton.visibility = if (hasPermission) View.GONE else View.VISIBLE
        enableSwitch.isEnabled = hasPermission

        val prefs = getSharedPreferences("notisync", MODE_PRIVATE)
        enableSwitch.isChecked = prefs.getBoolean("enabled", false)

        // Update status based on connection state
        val isConnected = prefs.getBoolean("connected", false)
        if (isConnected) {
            statusIndicator.setBackgroundResource(R.drawable.circle_green)
            statusText.text = "Connected"
        } else {
            statusIndicator.setBackgroundResource(R.drawable.circle_red)
            statusText.text = if (hasPermission) "Searching..." else "Disconnected"
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(this, NotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(componentName.flattenToString()) == true
    }
}
```

**Step 4: Create placeholder NotificationService**

Create `android/app/src/main/java/com/notisync/NotificationService.kt`:

```kotlin
package com.notisync

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Will be implemented in next task
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed for display-only
    }
}
```

**Step 5: Create launcher icons (placeholder)**

```bash
mkdir -p android/app/src/main/res/mipmap-hdpi
mkdir -p android/app/src/main/res/mipmap-mdpi
mkdir -p android/app/src/main/res/mipmap-xhdpi
mkdir -p android/app/src/main/res/mipmap-xxhdpi
mkdir -p android/app/src/main/res/mipmap-xxxhdpi
```

Create `android/app/src/main/res/mipmap-hdpi/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/blue_100"/>
    <foreground android:drawable="@color/blue_500"/>
</adaptive-icon>
```

Copy to other densities:
```bash
cp android/app/src/main/res/mipmap-hdpi/ic_launcher.xml android/app/src/main/res/mipmap-mdpi/
cp android/app/src/main/res/mipmap-hdpi/ic_launcher.xml android/app/src/main/res/mipmap-xhdpi/
cp android/app/src/main/res/mipmap-hdpi/ic_launcher.xml android/app/src/main/res/mipmap-xxhdpi/
cp android/app/src/main/res/mipmap-hdpi/ic_launcher.xml android/app/src/main/res/mipmap-xxxhdpi/
cp android/app/src/main/res/mipmap-hdpi/ic_launcher.xml android/app/src/main/res/mipmap-hdpi/ic_launcher_round.xml
cp android/app/src/main/res/mipmap-hdpi/ic_launcher.xml android/app/src/main/res/mipmap-mdpi/ic_launcher_round.xml
cp android/app/src/main/res/mipmap-hdpi/ic_launcher.xml android/app/src/main/res/mipmap-xhdpi/ic_launcher_round.xml
cp android/app/src/main/res/mipmap-hdpi/ic_launcher.xml android/app/src/main/res/mipmap-xxhdpi/ic_launcher_round.xml
cp android/app/src/main/res/mipmap-hdpi/ic_launcher.xml android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.xml
```

**Step 6: Commit**

```bash
git add android/
git commit -m "feat(android): add main activity with UI"
```

---

### Task 9: Implement Connection Manager

**Files:**
- Create: `android/app/src/main/java/com/notisync/ConnectionManager.kt`
- Create: `android/app/src/main/java/com/notisync/PhoneNotification.kt`

**Step 1: Create notification data class**

Create `android/app/src/main/java/com/notisync/PhoneNotification.kt`:

```kotlin
package com.notisync

data class PhoneNotification(
    val id: String,
    val app_package: String,
    val app_name: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val icon: String? = null
)
```

**Step 2: Create ConnectionManager**

Create `android/app/src/main/java/com/notisync/ConnectionManager.kt`:

```kotlin
package com.notisync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.*

class ConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "ConnectionManager"
        private const val SERVICE_TYPE = "_notisync._tcp."
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_QUEUED_NOTIFICATIONS = 10
    }

    private val gson = Gson()
    private var nsdManager: NsdManager? = null
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isSearching = AtomicBoolean(false)
    private val notificationQueue = ConcurrentLinkedQueue<PhoneNotification>()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    interface ConnectionListener {
        fun onConnected(hostName: String)
        fun onDisconnected()
        fun onSearching()
    }

    var listener: ConnectionListener? = null

    private val okHttpClient: OkHttpClient by lazy {
        // Trust all certificates (for self-signed cert)
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    fun start() {
        if (isSearching.get()) return

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        startDiscovery()
    }

    fun stop() {
        stopDiscovery()
        webSocket?.close(1000, "App stopped")
        webSocket = null
        isConnected.set(false)
    }

    fun sendNotification(notification: PhoneNotification) {
        if (isConnected.get()) {
            val json = gson.toJson(notification)
            webSocket?.send(json)
            Log.d(TAG, "Sent notification: ${notification.app_name}")
        } else {
            // Queue notification for later
            notificationQueue.offer(notification)
            while (notificationQueue.size > MAX_QUEUED_NOTIFICATIONS) {
                notificationQueue.poll()
            }
            Log.d(TAG, "Queued notification: ${notification.app_name}")
        }
    }

    private fun startDiscovery() {
        isSearching.set(true)
        listener?.onSearching()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceName.startsWith("notisync")) {
                    nsdManager?.resolveService(serviceInfo, createResolveListener())
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
                isSearching.set(false)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                isSearching.set(false)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery", e)
            }
        }
        discoveryListener = null
        isSearching.set(false)
    }

    private fun createResolveListener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service resolved: ${serviceInfo.host}:${serviceInfo.port}")
                connectToServer(serviceInfo.host.hostAddress!!, serviceInfo.port)
            }
        }
    }

    private fun connectToServer(host: String, port: Int) {
        if (isConnected.get()) return

        val url = "wss://$host:$port"
        Log.d(TAG, "Connecting to $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected.set(true)
                stopDiscovery()
                listener?.onConnected(host)

                // Send queued notifications
                while (notificationQueue.isNotEmpty()) {
                    notificationQueue.poll()?.let {
                        sendNotification(it)
                    }
                }

                updateConnectionState(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                handleDisconnection()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                handleDisconnection()
            }
        })
    }

    private fun handleDisconnection() {
        isConnected.set(false)
        webSocket = null
        listener?.onDisconnected()
        updateConnectionState(false)

        // Retry connection after delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isConnected.get() && !isSearching.get()) {
                startDiscovery()
            }
        }, RECONNECT_DELAY_MS)
    }

    private fun updateConnectionState(connected: Boolean) {
        context.getSharedPreferences("notisync", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("connected", connected)
            .apply()
    }
}
```

**Step 3: Commit**

```bash
git add android/
git commit -m "feat(android): add connection manager with mDNS discovery"
```

---

### Task 10: Implement Notification Listener Service

**Files:**
- Modify: `android/app/src/main/java/com/notisync/NotificationService.kt`

**Step 1: Implement the notification service**

Replace `android/app/src/main/java/com/notisync/NotificationService.kt`:

```kotlin
package com.notisync

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationService"
        var instance: NotificationService? = null
            private set
    }

    private var connectionManager: ConnectionManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        connectionManager = ConnectionManager(this)

        connectionManager?.listener = object : ConnectionManager.ConnectionListener {
            override fun onConnected(hostName: String) {
                Log.d(TAG, "Connected to $hostName")
            }

            override fun onDisconnected() {
                Log.d(TAG, "Disconnected")
            }

            override fun onSearching() {
                Log.d(TAG, "Searching for desktop...")
            }
        }

        Log.d(TAG, "NotificationService created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")

        if (isEnabled()) {
            connectionManager?.start()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        connectionManager?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionManager?.stop()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isEnabled()) return

        val notification = sbn.notification ?: return
        val extras = notification.extras

        // Skip our own notifications
        if (sbn.packageName == packageName) return

        // Skip ongoing notifications (media players, etc.)
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val appName = getAppName(sbn.packageName)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // Skip if no content
        if (title == null && text == null) return

        val phoneNotification = PhoneNotification(
            id = sbn.key,
            app_package = sbn.packageName,
            app_name = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        Log.d(TAG, "Notification from $appName: $title")
        connectionManager?.sendNotification(phoneNotification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed for display-only mode
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun isEnabled(): Boolean {
        return getSharedPreferences("notisync", MODE_PRIVATE)
            .getBoolean("enabled", false)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            connectionManager?.start()
        } else {
            connectionManager?.stop()
        }
    }
}
```

**Step 2: Update MainActivity to interact with service**

Update the enableSwitch listener in `android/app/src/main/java/com/notisync/MainActivity.kt`:

Replace the `enableSwitch.setOnCheckedChangeListener` block:

```kotlin
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("notisync", MODE_PRIVATE)
            prefs.edit().putBoolean("enabled", isChecked).apply()
            NotificationService.instance?.setEnabled(isChecked)
        }
```

**Step 3: Commit**

```bash
git add android/
git commit -m "feat(android): implement notification listener service"
```

---

### Task 11: Final Testing and Polish

**Files:**
- Various cleanup and testing

**Step 1: Build and test desktop app**

```bash
cd desktop && cargo build --release
```

Run: `./target/release/notisync`
Expected: Tray icon appears, mDNS advertising starts

**Step 2: Build Android app**

```bash
cd android && ./gradlew assembleDebug
```

Install on phone: `adb install app/build/outputs/apk/debug/app-debug.apk`

**Step 3: Test end-to-end**

1. Start desktop app
2. Open Android app
3. Grant notification access permission
4. Enable the switch
5. Send a test notification on phone
6. Verify it appears on desktop

**Step 4: Final commit**

```bash
git add -A
git commit -m "chore: final testing and polish"
```

---

## Summary

- **Phase 1 (Tasks 1-6)**: Rust desktop app with mDNS, WebSocket/TLS server, notifications, and system tray
- **Phase 2 (Tasks 7-10)**: Android app with notification listener, mDNS discovery, and WebSocket client
- **Task 11**: Integration testing

Total: 11 tasks
