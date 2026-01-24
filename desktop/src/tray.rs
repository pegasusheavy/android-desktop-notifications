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
