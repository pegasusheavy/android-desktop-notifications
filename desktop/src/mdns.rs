use mdns_sd::{ServiceDaemon, ServiceInfo};
use std::collections::HashMap;

pub const SERVICE_TYPE: &str = "_notisync._tcp.local.";
pub const SERVICE_NAME: &str = "notisync";

/// Get the hostname for mDNS advertisement
pub fn get_hostname() -> String {
    gethostname::gethostname()
        .into_string()
        .unwrap_or_else(|_| "desktop".to_string())
}

/// Format the service hostname for mDNS
pub fn format_service_hostname(hostname: &str) -> String {
    format!("{}.local.", hostname)
}

/// Format the instance name for mDNS
pub fn format_instance_name(hostname: &str) -> String {
    format!("{}@{}", SERVICE_NAME, hostname)
}

pub struct MdnsAdvertiser {
    daemon: ServiceDaemon,
    fullname: String,
}

impl MdnsAdvertiser {
    pub fn new(port: u16) -> Result<Self, Box<dyn std::error::Error>> {
        let daemon = ServiceDaemon::new()?;

        let hostname = get_hostname();
        let service_hostname = format_service_hostname(&hostname);
        let instance_name = format_instance_name(&hostname);

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

    /// Get the full service name being advertised
    pub fn fullname(&self) -> &str {
        &self.fullname
    }

    pub fn shutdown(self) {
        let _ = self.daemon.unregister(&self.fullname);
        let _ = self.daemon.shutdown();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_service_type_format() {
        assert_eq!(SERVICE_TYPE, "_notisync._tcp.local.");
        assert!(SERVICE_TYPE.starts_with("_"));
        assert!(SERVICE_TYPE.ends_with(".local."));
    }

    #[test]
    fn test_service_name() {
        assert_eq!(SERVICE_NAME, "notisync");
    }

    #[test]
    fn test_get_hostname_returns_string() {
        let hostname = get_hostname();
        assert!(!hostname.is_empty());
    }

    #[test]
    fn test_format_service_hostname() {
        let hostname = "mycomputer";
        let service_hostname = format_service_hostname(hostname);

        assert_eq!(service_hostname, "mycomputer.local.");
        assert!(service_hostname.ends_with(".local."));
    }

    #[test]
    fn test_format_instance_name() {
        let hostname = "mycomputer";
        let instance_name = format_instance_name(hostname);

        assert_eq!(instance_name, "notisync@mycomputer");
        assert!(instance_name.starts_with("notisync@"));
    }

    #[test]
    fn test_format_instance_name_with_special_chars() {
        let hostname = "my-computer_01";
        let instance_name = format_instance_name(hostname);

        assert_eq!(instance_name, "notisync@my-computer_01");
    }

    #[test]
    fn test_mdns_advertiser_creation() {
        // This test requires network access but validates the struct can be created
        let result = MdnsAdvertiser::new(19876);

        // May fail in CI without network, so we just check it doesn't panic unexpectedly
        if let Ok(advertiser) = result {
            assert!(advertiser.fullname().contains("notisync"));
            assert!(advertiser.fullname().contains(SERVICE_TYPE.trim_end_matches('.')));
            advertiser.shutdown();
        }
    }

    #[test]
    fn test_different_ports() {
        let hostname = get_hostname();
        let service_hostname = format_service_hostname(&hostname);
        let instance_name = format_instance_name(&hostname);

        // Verify formatting works for any port
        for port in [80u16, 443, 8080, 9876, 65535] {
            let properties: HashMap<String, String> = HashMap::new();
            let result = ServiceInfo::new(
                SERVICE_TYPE,
                &instance_name,
                &service_hostname,
                (),
                port,
                properties,
            );

            assert!(result.is_ok(), "Failed to create ServiceInfo for port {}", port);
        }
    }
}
