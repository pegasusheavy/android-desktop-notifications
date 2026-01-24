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
