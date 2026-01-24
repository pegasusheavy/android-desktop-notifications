mod notification;
mod tls;

fn main() {
    env_logger::init();

    println!("Testing TLS certificate generation...");
    let _acceptor = tls::create_tls_acceptor();
    println!("TLS acceptor created successfully");
    println!("Config dir: {:?}", tls::get_config_dir());
}
