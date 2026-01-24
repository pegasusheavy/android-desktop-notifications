# Maintainer: Joseph Quinn <quinn.josephr@protonmail.com>
pkgname=notisync
pkgver=0.1.0
pkgrel=1
pkgdesc="Sync Android notifications to your Linux desktop"
arch=('x86_64')
url="https://github.com/PegasusHeavyIndustries/android-desktop-notifications"
license=('MIT')
depends=(
    'gtk3'
    'libnotify'
    'xdotool'
    'libayatana-appindicator'
)
makedepends=(
    'rust'
    'cargo'
    'pkg-config'
)
install=notisync.install
source=()
sha256sums=()

build() {
    cd "$srcdir/../desktop"
    cargo build --release --locked
}

package() {
    cd "$srcdir/../desktop"

    # Install binary
    install -Dm755 "target/release/notisync" "$pkgdir/usr/bin/notisync"

    # Install desktop file
    install -Dm644 "$srcdir/../notisync.desktop" "$pkgdir/usr/share/applications/notisync.desktop"

    # Install icon
    install -Dm644 "$srcdir/../notisync.svg" "$pkgdir/usr/share/icons/hicolor/scalable/apps/notisync.svg"

    # Install systemd user unit
    install -Dm644 "$srcdir/../notisync.service" "$pkgdir/usr/lib/systemd/user/notisync.service"
}
