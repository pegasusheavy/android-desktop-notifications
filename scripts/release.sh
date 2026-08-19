#!/usr/bin/env bash
#
# Release script for NotiSync
# Automatically bumps versions in all project files, commits, tags, and pushes
#
# Usage: ./scripts/release.sh [major|minor|patch]
#
# Copyright (c) 2026 Joseph R. Quinn
# Contact: quinn.josephr@protonmail.com

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

# Check for required tools
check_dependencies() {
    local missing=()
    
    for cmd in git cargo sed date; do
        if ! command -v "$cmd" &> /dev/null; then
            missing+=("$cmd")
        fi
    done
    
    if [[ ${#missing[@]} -gt 0 ]]; then
        log_error "Missing required commands: ${missing[*]}"
    fi
}

# Get current version from Cargo.toml
get_current_version() {
    grep -E '^version = "' "$PROJECT_ROOT/desktop/Cargo.toml" | head -1 | sed 's/version = "\(.*\)"/\1/'
}

# Get current Android versionCode
get_android_version_code() {
    grep -E 'versionCode = ' "$PROJECT_ROOT/android/app/build.gradle.kts" | sed 's/.*versionCode = \([0-9]*\).*/\1/'
}

# Calculate new version based on bump type
calculate_new_version() {
    local current_version="$1"
    local bump_type="$2"
    
    IFS='.' read -r major minor patch <<< "$current_version"
    
    case "$bump_type" in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch)
            patch=$((patch + 1))
            ;;
        *)
            log_error "Invalid bump type: $bump_type. Use: major, minor, or patch"
            ;;
    esac
    
    echo "$major.$minor.$patch"
}

# Update version in Cargo.toml
update_cargo_version() {
    local new_version="$1"
    local cargo_file="$PROJECT_ROOT/desktop/Cargo.toml"
    
    log_info "Updating Cargo.toml to version $new_version"
    sed -i "s/^version = \".*\"/version = \"$new_version\"/" "$cargo_file"
}

# Update version in build.gradle.kts
update_gradle_version() {
    local new_version="$1"
    local new_version_code="$2"
    local gradle_file="$PROJECT_ROOT/android/app/build.gradle.kts"
    
    log_info "Updating build.gradle.kts to version $new_version (code: $new_version_code)"
    sed -i "s/versionCode = [0-9]*/versionCode = $new_version_code/" "$gradle_file"
    sed -i "s/versionName = \".*\"/versionName = \"$new_version\"/" "$gradle_file"
}

# Update debian changelog
update_debian_changelog() {
    local new_version="$1"
    local changelog_file="$PROJECT_ROOT/packaging/debian/changelog"
    local date_str
    date_str=$(date -R)
    
    log_info "Updating debian/changelog to version $new_version"
    
    # Create new changelog entry at the top
    local new_entry="notisync ($new_version-1) unstable; urgency=medium

  * Release v$new_version

 -- Joseph Quinn <quinn.josephr@protonmail.com>  $date_str
"
    
    # Prepend new entry to changelog
    echo -e "$new_entry\n$(cat "$changelog_file")" > "$changelog_file"
}

# Update Cargo.lock
update_cargo_lock() {
    log_info "Updating Cargo.lock"
    (cd "$PROJECT_ROOT/desktop" && cargo update -p notisync --precise "$1" 2>/dev/null || cargo check --quiet)
}

# Verify working directory is clean
check_working_directory() {
    if ! git -C "$PROJECT_ROOT" diff --quiet || ! git -C "$PROJECT_ROOT" diff --staged --quiet; then
        log_error "Working directory is not clean. Please commit or stash your changes first."
    fi
}

# Verify we're on main branch
check_branch() {
    local current_branch
    current_branch=$(git -C "$PROJECT_ROOT" branch --show-current)
    
    if [[ "$current_branch" != "main" && "$current_branch" != "master" ]]; then
        log_warn "You are on branch '$current_branch', not 'main'"
        read -p "Continue anyway? [y/N] " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# Verify remote is up to date
check_remote_sync() {
    log_info "Fetching from remote..."
    git -C "$PROJECT_ROOT" fetch origin --quiet
    
    local local_commit
    local remote_commit
    local_commit=$(git -C "$PROJECT_ROOT" rev-parse HEAD)
    remote_commit=$(git -C "$PROJECT_ROOT" rev-parse origin/main 2>/dev/null || git -C "$PROJECT_ROOT" rev-parse origin/master 2>/dev/null)
    
    if [[ "$local_commit" != "$remote_commit" ]]; then
        log_warn "Local branch is not in sync with remote"
        read -p "Continue anyway? [y/N] " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# Commit version bump
commit_version_bump() {
    local new_version="$1"
    
    log_info "Committing version bump"
    git -C "$PROJECT_ROOT" add -A
    git -C "$PROJECT_ROOT" commit -m "chore: bump version to $new_version"
}

# Create and push tag
create_and_push_tag() {
    local new_version="$1"
    local tag="v$new_version"
    
    # Check if tag already exists
    if git -C "$PROJECT_ROOT" rev-parse "$tag" &>/dev/null; then
        log_error "Tag $tag already exists. Delete it first with: git tag -d $tag && git push --delete origin $tag"
    fi
    
    log_info "Creating tag $tag"
    git -C "$PROJECT_ROOT" tag -a "$tag" -m "Release $tag"
    
    log_info "Pushing changes and tag to remote"
    git -C "$PROJECT_ROOT" push origin HEAD
    git -C "$PROJECT_ROOT" push origin "$tag"
}

# Print release summary
print_summary() {
    local old_version="$1"
    local new_version="$2"
    
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                    Release Complete!                      ║${NC}"
    echo -e "${GREEN}╠══════════════════════════════════════════════════════════╣${NC}"
    echo -e "${GREEN}║${NC} Version: ${YELLOW}$old_version${NC} → ${GREEN}$new_version${NC}"
    echo -e "${GREEN}║${NC} Tag: ${BLUE}v$new_version${NC}"
    echo -e "${GREEN}║${NC}"
    echo -e "${GREEN}║${NC} GitHub Actions will now build and create the release."
    echo -e "${GREEN}║${NC} Monitor at: ${BLUE}https://github.com/quinnjr/android-desktop-notifications/actions${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# Main function
main() {
    local bump_type="${1:-}"
    
    # Validate arguments
    if [[ -z "$bump_type" ]]; then
        echo "Usage: $0 [major|minor|patch]"
        echo ""
        echo "Examples:"
        echo "  $0 patch   # 0.2.0 -> 0.2.1"
        echo "  $0 minor   # 0.2.0 -> 0.3.0"
        echo "  $0 major   # 0.2.0 -> 1.0.0"
        exit 1
    fi
    
    if [[ ! "$bump_type" =~ ^(major|minor|patch)$ ]]; then
        log_error "Invalid bump type: $bump_type. Use: major, minor, or patch"
    fi
    
    # Run checks
    check_dependencies
    check_working_directory
    check_branch
    check_remote_sync
    
    # Get versions
    local current_version
    current_version=$(get_current_version)
    
    local current_version_code
    current_version_code=$(get_android_version_code)
    
    local new_version
    new_version=$(calculate_new_version "$current_version" "$bump_type")
    
    local new_version_code=$((current_version_code + 1))
    
    # Confirm with user
    echo ""
    log_info "Current version: $current_version (Android code: $current_version_code)"
    log_info "New version:     $new_version (Android code: $new_version_code)"
    echo ""
    read -p "Proceed with release? [y/N] " -n 1 -r
    echo
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Release cancelled"
        exit 0
    fi
    
    # Update all version files
    update_cargo_version "$new_version"
    update_gradle_version "$new_version" "$new_version_code"
    update_debian_changelog "$new_version"
    update_cargo_lock "$new_version"
    
    # Commit and tag
    commit_version_bump "$new_version"
    create_and_push_tag "$new_version"
    
    # Print summary
    print_summary "$current_version" "$new_version"
}

main "$@"
