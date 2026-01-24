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
