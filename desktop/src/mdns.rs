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
