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
