#!/bin/bash
# Build .deb package for NotiSync
# Requires: debhelper, devscripts, cargo, rustc, and build dependencies

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "Building NotiSync .deb package..."

# Create temporary build directory
BUILD_DIR=$(mktemp -d)
trap "rm -rf $BUILD_DIR" EXIT

# Copy source to build directory
cp -r "$PROJECT_ROOT" "$BUILD_DIR/notisync-0.1.0"
cd "$BUILD_DIR/notisync-0.1.0"

# Copy debian directory to project root
cp -r packaging/debian .

# Make rules executable
chmod +x debian/rules

# Build the package
dpkg-buildpackage -us -uc -b

# Copy the built package back
cp "$BUILD_DIR"/*.deb "$PROJECT_ROOT/"

echo ""
echo "Build complete! Package created:"
ls -la "$PROJECT_ROOT"/*.deb
