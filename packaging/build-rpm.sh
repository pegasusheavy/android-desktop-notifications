#!/bin/bash
# Build .rpm package for NotiSync
# Requires: rpm-build, cargo, rustc, and build dependencies

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
VERSION="0.1.0"

echo "Building NotiSync .rpm package..."

# Setup rpmbuild directories
mkdir -p ~/rpmbuild/{BUILD,RPMS,SOURCES,SPECS,SRPMS}

# Create source tarball
TARBALL_DIR=$(mktemp -d)
trap "rm -rf $TARBALL_DIR" EXIT

mkdir -p "$TARBALL_DIR/notisync-$VERSION"
cp -r "$PROJECT_ROOT"/* "$TARBALL_DIR/notisync-$VERSION/"
cd "$TARBALL_DIR"
tar czf ~/rpmbuild/SOURCES/notisync-$VERSION.tar.gz "notisync-$VERSION"

# Copy spec file
cp "$PROJECT_ROOT/packaging/rpm/notisync.spec" ~/rpmbuild/SPECS/

# Build the package
rpmbuild -bb ~/rpmbuild/SPECS/notisync.spec

# Copy the built package back
cp ~/rpmbuild/RPMS/*/*.rpm "$PROJECT_ROOT/"

echo ""
echo "Build complete! Package created:"
ls -la "$PROJECT_ROOT"/*.rpm
