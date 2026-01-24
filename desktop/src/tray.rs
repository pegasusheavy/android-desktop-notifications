use std::sync::mpsc as std_mpsc;
use tray_icon::{
    menu::{Menu, MenuEvent, MenuItem},
    TrayIcon, TrayIconBuilder,
};

/// Commands that can be received from the tray menu
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TrayCommand {
    Quit,
}

/// Generate RGBA data for a circular icon
pub fn generate_circle_icon(size: u32, r: u8, g: u8, b: u8) -> Vec<u8> {
    let mut rgba = Vec::with_capacity((size * size * 4) as usize);
    let center = size as f32 / 2.0;
    let radius = center - 2.0;

    for y in 0..size {
        for x in 0..size {
            let dx = x as f32 - center;
            let dy = y as f32 - center;
            let dist = (dx * dx + dy * dy).sqrt();

            if dist < radius {
                rgba.extend_from_slice(&[r, g, b, 255]);
            } else {
                rgba.extend_from_slice(&[0, 0, 0, 0]);
            }
        }
    }

    rgba
}

/// Create the default blue icon for the tray
pub fn create_icon_rgba() -> (Vec<u8>, u32, u32) {
    let size = 32u32;
    let rgba = generate_circle_icon(size, 66, 133, 244);
    (rgba, size, size)
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
    let (rgba, width, height) = create_icon_rgba();
    Ok(tray_icon::Icon::from_rgba(rgba, width, height)?)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_tray_command_quit() {
        let cmd = TrayCommand::Quit;
        assert_eq!(cmd, TrayCommand::Quit);
    }

    #[test]
    fn test_tray_command_clone() {
        let cmd = TrayCommand::Quit;
        let cloned = cmd.clone();
        assert_eq!(cmd, cloned);
    }

    #[test]
    fn test_tray_command_copy() {
        let cmd = TrayCommand::Quit;
        let copied: TrayCommand = cmd;
        assert_eq!(cmd, copied);
    }

    #[test]
    fn test_tray_command_debug() {
        let cmd = TrayCommand::Quit;
        let debug_str = format!("{:?}", cmd);
        assert_eq!(debug_str, "Quit");
    }

    #[test]
    fn test_generate_circle_icon_size() {
        let size = 32u32;
        let rgba = generate_circle_icon(size, 255, 0, 0);

        // Should have size * size * 4 bytes (RGBA)
        assert_eq!(rgba.len(), (size * size * 4) as usize);
    }

    #[test]
    fn test_generate_circle_icon_different_sizes() {
        for size in [16u32, 32, 48, 64, 128] {
            let rgba = generate_circle_icon(size, 0, 255, 0);
            assert_eq!(rgba.len(), (size * size * 4) as usize);
        }
    }

    #[test]
    fn test_generate_circle_icon_center_pixel_colored() {
        let size = 32u32;
        let rgba = generate_circle_icon(size, 100, 150, 200);

        // Center pixel should be colored
        let center = (size / 2) as usize;
        let idx = (center * size as usize + center) * 4;

        assert_eq!(rgba[idx], 100);     // R
        assert_eq!(rgba[idx + 1], 150); // G
        assert_eq!(rgba[idx + 2], 200); // B
        assert_eq!(rgba[idx + 3], 255); // A (opaque)
    }

    #[test]
    fn test_generate_circle_icon_corner_transparent() {
        let size = 32u32;
        let rgba = generate_circle_icon(size, 255, 255, 255);

        // Top-left corner should be transparent
        assert_eq!(rgba[0], 0);   // R
        assert_eq!(rgba[1], 0);   // G
        assert_eq!(rgba[2], 0);   // B
        assert_eq!(rgba[3], 0);   // A (transparent)
    }

    #[test]
    fn test_create_icon_rgba_dimensions() {
        let (rgba, width, height) = create_icon_rgba();

        assert_eq!(width, 32);
        assert_eq!(height, 32);
        assert_eq!(rgba.len(), (width * height * 4) as usize);
    }

    #[test]
    fn test_create_icon_rgba_blue_color() {
        let (rgba, width, _) = create_icon_rgba();

        // Check center pixel is blue (66, 133, 244)
        let center = (width / 2) as usize;
        let idx = (center * width as usize + center) * 4;

        assert_eq!(rgba[idx], 66);      // R
        assert_eq!(rgba[idx + 1], 133); // G
        assert_eq!(rgba[idx + 2], 244); // B
        assert_eq!(rgba[idx + 3], 255); // A
    }

    #[test]
    fn test_channel_communication() {
        let (tx, rx) = std_mpsc::channel();

        tx.send(TrayCommand::Quit).unwrap();

        let received = rx.try_recv().unwrap();
        assert_eq!(received, TrayCommand::Quit);
    }

    #[test]
    fn test_channel_empty() {
        let (_tx, rx) = std_mpsc::channel::<TrayCommand>();

        let result = rx.try_recv();
        assert!(result.is_err());
    }
}
