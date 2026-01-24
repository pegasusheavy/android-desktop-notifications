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

/// Generate a new self-signed certificate
pub fn generate_cert() -> (String, String) {
    let subject_alt_names = vec!["localhost".to_string(), "notisync.local".to_string()];
    let CertifiedKey { cert, key_pair } = generate_simple_self_signed(subject_alt_names)
        .expect("Could not generate certificate");

    (cert.pem(), key_pair.serialize_pem())
}

/// Parse PEM certificate and key into DER format
pub fn parse_pem_cert(cert_pem: &str, key_pem: &str) -> (Vec<CertificateDer<'static>>, PrivateKeyDer<'static>) {
    let cert = rustls_pemfile::certs(&mut cert_pem.as_bytes())
        .map(|r| r.expect("Could not parse cert"))
        .collect();

    let key = rustls_pemfile::private_key(&mut key_pem.as_bytes())
        .expect("Could not parse key")
        .expect("No key found");

    (cert, key)
}

/// Get or create certificate from a specific directory
pub fn get_or_create_cert_in_dir(config_dir: &PathBuf) -> (Vec<CertificateDer<'static>>, PrivateKeyDer<'static>) {
    let cert_path = config_dir.join("cert.pem");
    let key_path = config_dir.join("key.pem");

    if cert_path.exists() && key_path.exists() {
        log::info!("Loading existing certificate from {:?}", config_dir);
        let cert_pem = fs::read_to_string(&cert_path).expect("Could not read cert");
        let key_pem = fs::read_to_string(&key_path).expect("Could not read key");

        parse_pem_cert(&cert_pem, &key_pem)
    } else {
        log::info!("Generating new self-signed certificate");
        let (cert_pem, key_pem) = generate_cert();

        fs::write(&cert_path, &cert_pem).expect("Could not write cert");
        fs::write(&key_path, &key_pem).expect("Could not write key");

        parse_pem_cert(&cert_pem, &key_pem)
    }
}

pub fn get_or_create_cert() -> (Vec<CertificateDer<'static>>, PrivateKeyDer<'static>) {
    let config_dir = get_config_dir();
    get_or_create_cert_in_dir(&config_dir)
}

/// Create TLS acceptor from certificate and key
pub fn create_tls_acceptor_from_cert(
    certs: Vec<CertificateDer<'static>>,
    key: PrivateKeyDer<'static>,
) -> TlsAcceptor {
    let config = rustls::ServerConfig::builder()
        .with_no_client_auth()
        .with_single_cert(certs, key)
        .expect("Could not create TLS config");

    TlsAcceptor::from(Arc::new(config))
}

pub fn create_tls_acceptor() -> TlsAcceptor {
    let (certs, key) = get_or_create_cert();
    create_tls_acceptor_from_cert(certs, key)
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_generate_cert_creates_valid_pem() {
        let (cert_pem, key_pem) = generate_cert();

        assert!(cert_pem.starts_with("-----BEGIN CERTIFICATE-----"));
        assert!(cert_pem.ends_with("-----END CERTIFICATE-----\n"));
        assert!(key_pem.starts_with("-----BEGIN PRIVATE KEY-----"));
        assert!(key_pem.ends_with("-----END PRIVATE KEY-----\n"));
    }

    #[test]
    fn test_parse_pem_cert_success() {
        let (cert_pem, key_pem) = generate_cert();
        let (certs, _key) = parse_pem_cert(&cert_pem, &key_pem);

        assert!(!certs.is_empty());
        assert_eq!(certs.len(), 1);
    }

    #[test]
    fn test_get_or_create_cert_creates_new() {
        let temp_dir = TempDir::new().unwrap();
        let config_dir = temp_dir.path().to_path_buf();

        let cert_path = config_dir.join("cert.pem");
        let key_path = config_dir.join("key.pem");

        assert!(!cert_path.exists());
        assert!(!key_path.exists());

        let (certs, _key) = get_or_create_cert_in_dir(&config_dir);

        assert!(cert_path.exists());
        assert!(key_path.exists());
        assert!(!certs.is_empty());
    }

    #[test]
    fn test_get_or_create_cert_loads_existing() {
        let temp_dir = TempDir::new().unwrap();
        let config_dir = temp_dir.path().to_path_buf();

        // Create initial cert
        let (certs1, _) = get_or_create_cert_in_dir(&config_dir);

        // Load existing cert
        let (certs2, _) = get_or_create_cert_in_dir(&config_dir);

        // Should be the same certificate
        assert_eq!(certs1.len(), certs2.len());
        assert_eq!(certs1[0].as_ref(), certs2[0].as_ref());
    }

    #[test]
    fn test_create_tls_acceptor_from_cert() {
        let (cert_pem, key_pem) = generate_cert();
        let (certs, key) = parse_pem_cert(&cert_pem, &key_pem);

        // Should not panic
        let _acceptor = create_tls_acceptor_from_cert(certs, key);
    }

    #[test]
    fn test_get_config_dir_creates_directory() {
        let config_dir = get_config_dir();

        assert!(config_dir.exists());
        assert!(config_dir.is_dir());
        assert!(config_dir.to_string_lossy().contains("notisync"));
    }

    #[test]
    fn test_cert_files_have_correct_permissions() {
        let temp_dir = TempDir::new().unwrap();
        let config_dir = temp_dir.path().to_path_buf();

        get_or_create_cert_in_dir(&config_dir);

        let cert_path = config_dir.join("cert.pem");
        let key_path = config_dir.join("key.pem");

        // Files should be readable
        let cert_content = fs::read_to_string(&cert_path).unwrap();
        let key_content = fs::read_to_string(&key_path).unwrap();

        assert!(!cert_content.is_empty());
        assert!(!key_content.is_empty());
    }
}
