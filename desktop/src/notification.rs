use serde::Deserialize;

#[derive(Debug, Clone, Deserialize, PartialEq)]
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
    /// Format the notification body based on available fields
    pub fn format_body(&self) -> String {
        match (&self.title, &self.text) {
            (Some(t), Some(txt)) => format!("{}: {}", t, txt),
            (Some(t), None) => t.clone(),
            (None, Some(txt)) => txt.clone(),
            (None, None) => "New notification".to_string(),
        }
    }

    pub fn display(&self) -> Result<(), Box<dyn std::error::Error>> {
        let title = format!("{}", self.app_name);
        let body = self.format_body();

        notify_rust::Notification::new()
            .summary(&title)
            .body(&body)
            .timeout(notify_rust::Timeout::Milliseconds(5000))
            .show()?;

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_test_notification(
        title: Option<&str>,
        text: Option<&str>,
    ) -> PhoneNotification {
        PhoneNotification {
            id: "test-id".to_string(),
            app_package: "com.test.app".to_string(),
            app_name: "Test App".to_string(),
            title: title.map(String::from),
            text: text.map(String::from),
            timestamp: 1234567890,
            icon: None,
        }
    }

    #[test]
    fn test_format_body_with_title_and_text() {
        let notif = create_test_notification(Some("Hello"), Some("World"));
        assert_eq!(notif.format_body(), "Hello: World");
    }

    #[test]
    fn test_format_body_with_title_only() {
        let notif = create_test_notification(Some("Hello"), None);
        assert_eq!(notif.format_body(), "Hello");
    }

    #[test]
    fn test_format_body_with_text_only() {
        let notif = create_test_notification(None, Some("World"));
        assert_eq!(notif.format_body(), "World");
    }

    #[test]
    fn test_format_body_with_neither() {
        let notif = create_test_notification(None, None);
        assert_eq!(notif.format_body(), "New notification");
    }

    #[test]
    fn test_deserialize_full_notification() {
        let json = r#"{
            "id": "notif-123",
            "app_package": "com.example.app",
            "app_name": "Example App",
            "title": "New Message",
            "text": "Hello there!",
            "timestamp": 1706123456789,
            "icon": "base64data"
        }"#;

        let notif: PhoneNotification = serde_json::from_str(json).unwrap();

        assert_eq!(notif.id, "notif-123");
        assert_eq!(notif.app_package, "com.example.app");
        assert_eq!(notif.app_name, "Example App");
        assert_eq!(notif.title, Some("New Message".to_string()));
        assert_eq!(notif.text, Some("Hello there!".to_string()));
        assert_eq!(notif.timestamp, 1706123456789);
        assert_eq!(notif.icon, Some("base64data".to_string()));
    }

    #[test]
    fn test_deserialize_minimal_notification() {
        let json = r#"{
            "id": "notif-456",
            "app_package": "com.minimal.app",
            "app_name": "Minimal",
            "title": null,
            "text": null,
            "timestamp": 1234567890
        }"#;

        let notif: PhoneNotification = serde_json::from_str(json).unwrap();

        assert_eq!(notif.id, "notif-456");
        assert_eq!(notif.title, None);
        assert_eq!(notif.text, None);
        assert_eq!(notif.icon, None); // default value
    }

    #[test]
    fn test_deserialize_without_optional_icon() {
        let json = r#"{
            "id": "notif-789",
            "app_package": "com.test",
            "app_name": "Test",
            "title": "Title",
            "text": "Text",
            "timestamp": 0
        }"#;

        let notif: PhoneNotification = serde_json::from_str(json).unwrap();
        assert_eq!(notif.icon, None);
    }

    #[test]
    fn test_notification_clone() {
        let notif = create_test_notification(Some("Title"), Some("Text"));
        let cloned = notif.clone();

        assert_eq!(notif, cloned);
    }

    #[test]
    fn test_notification_debug() {
        let notif = create_test_notification(Some("Title"), None);
        let debug_str = format!("{:?}", notif);

        assert!(debug_str.contains("PhoneNotification"));
        assert!(debug_str.contains("test-id"));
        assert!(debug_str.contains("Title"));
    }
}
