Name:           notisync
Version:        0.1.0
Release:        1%{?dist}
Summary:        Sync Android notifications to your Linux desktop

License:        MIT
URL:            https://github.com/quinnjr/android-desktop-notifications
Source0:        %{name}-%{version}.tar.gz

BuildRequires:  rust >= 1.70
BuildRequires:  cargo
BuildRequires:  pkg-config
BuildRequires:  gtk3-devel
BuildRequires:  libappindicator-gtk3-devel
BuildRequires:  libxdo-devel

Requires:       gtk3
Requires:       libappindicator-gtk3
Requires:       libxdo
Requires:       libnotify

%description
NotiSync allows you to receive notifications from your Android phone
on your Linux desktop. It uses mDNS for automatic discovery and
WebSocket over TLS for secure communication.

Features:
- Automatic phone discovery via mDNS
- Secure TLS encrypted connection
- System tray integration
- Native desktop notifications

%prep
%autosetup

%build
cd desktop
cargo build --release

%install
rm -rf %{buildroot}

# Install binary
install -Dm755 desktop/target/release/notisync %{buildroot}%{_bindir}/notisync

# Install desktop file
install -Dm644 notisync.desktop %{buildroot}%{_datadir}/applications/notisync.desktop

# Install icon
install -Dm644 notisync.svg %{buildroot}%{_datadir}/icons/hicolor/scalable/apps/notisync.svg

# Install systemd user unit
install -Dm644 notisync.service %{buildroot}%{_userunitdir}/notisync.service

%files
%license packaging/debian/copyright
%{_bindir}/notisync
%{_datadir}/applications/notisync.desktop
%{_datadir}/icons/hicolor/scalable/apps/notisync.svg
%{_userunitdir}/notisync.service

%post
echo ""
echo "NotiSync installed successfully!"
echo ""
echo "To start NotiSync now:"
echo "  systemctl --user start notisync.service"
echo ""
echo "To enable NotiSync on login:"
echo "  systemctl --user enable notisync.service"
echo ""

%preun
if [ $1 -eq 0 ]; then
    # Package removal, not upgrade
    systemctl --user stop notisync.service 2>/dev/null || true
    systemctl --user disable notisync.service 2>/dev/null || true
fi

%changelog
* Fri Jan 24 2026 Joseph Quinn <quinn.josephr@protonmail.com> - 0.1.0-1
- Initial release
- Android notification forwarding to Linux desktop
- mDNS auto-discovery
- TLS encrypted WebSocket communication
- System tray integration
- Systemd user service
