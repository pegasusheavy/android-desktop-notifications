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
