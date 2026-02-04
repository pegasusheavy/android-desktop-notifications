#!/bin/bash
set -e

APP_NAME="NotiSync"
VERSION="${1:-1.0.0}"
ARCH="${2:-x86_64}"
DMG_NAME="notisync-${VERSION}-macos-${ARCH}.dmg"

# Create app bundle structure
mkdir -p "${APP_NAME}.app/Contents/MacOS"
mkdir -p "${APP_NAME}.app/Contents/Resources"

# Copy binary
cp notisync "${APP_NAME}.app/Contents/MacOS/"

# Copy Info.plist and update version
cp packaging/macos/Info.plist "${APP_NAME}.app/Contents/Info.plist"
sed -i '' "s/1.0.0/${VERSION}/g" "${APP_NAME}.app/Contents/Info.plist"

# Copy icon if exists
if [ -f "packaging/macos/AppIcon.icns" ]; then
    cp packaging/macos/AppIcon.icns "${APP_NAME}.app/Contents/Resources/"
fi

# Create DMG
mkdir -p dmg_contents
cp -r "${APP_NAME}.app" dmg_contents/
ln -s /Applications dmg_contents/Applications

hdiutil create -volname "${APP_NAME}" \
    -srcfolder dmg_contents \
    -ov -format UDZO \
    "${DMG_NAME}"

# Cleanup
rm -rf dmg_contents "${APP_NAME}.app"

echo "Created ${DMG_NAME}"
