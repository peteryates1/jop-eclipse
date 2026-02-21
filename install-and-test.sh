#!/bin/bash
#
# Build, install, and launch JOP Eclipse plugins for testing.
#
# Uses a local overlay approach: the base Eclipse installation and p2 pool
# remain read-only. A writable overlay at ~/eclipse-jop/ holds:
#   - configuration/  (full copy from base — OSGi needs write access)
#   - plugins/         (JOP plugin jars)
#
# Eclipse is launched with -configuration pointing to the local overlay,
# so the base install is never modified.
#
# Usage:
#   ./install-and-test.sh               # build + install + launch
#   ./install-and-test.sh --skip-build  # install + launch (reuse last build)
#   ./install-and-test.sh --install-only # build + install, no launch
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ECLIPSE_HOME="/opt/eclipse/java-latest-released/eclipse"
BASE_CONFIG="${ECLIPSE_HOME}/configuration"
BASE_BUNDLES="${BASE_CONFIG}/org.eclipse.equinox.simpleconfigurator/bundles.info"
OVERLAY="${HOME}/eclipse-jop"
OVERLAY_CONFIG="${OVERLAY}/configuration"
OVERLAY_BUNDLES="${OVERLAY_CONFIG}/org.eclipse.equinox.simpleconfigurator/bundles.info"
OVERLAY_PLUGINS="${OVERLAY}/plugins"
TEST_WORKSPACE="/tmp/jop-eclipse-test-workspace"
SITE_PLUGINS="${SCRIPT_DIR}/com.jopdesign.site/target/repository/plugins"

SKIP_BUILD=false
INSTALL_ONLY=false

for arg in "$@"; do
    case "$arg" in
        --skip-build)   SKIP_BUILD=true ;;
        --install-only) INSTALL_ONLY=true ;;
        --help|-h)
            echo "Usage: $0 [--skip-build] [--install-only]"
            echo ""
            echo "Overlay directory: ${OVERLAY}"
            echo "  plugins/        JOP plugin jars"
            echo "  configuration/  Writable Eclipse config (bundles.info)"
            exit 0
            ;;
    esac
done

# --- Build ---
if [ "$SKIP_BUILD" = false ]; then
    echo "=== Building plugins ==="
    mvn -f "${SCRIPT_DIR}/pom.xml" clean verify -q
    echo "    Build successful"
else
    echo "=== Skipping build ==="
    if [ ! -d "$SITE_PLUGINS" ]; then
        echo "ERROR: No build output found. Run without --skip-build first."
        exit 1
    fi
fi

# --- Kill running Eclipse ---
if pgrep -f "eclipse.*product" > /dev/null 2>&1; then
    echo "=== Stopping running Eclipse ==="
    pkill -f "eclipse.*product" 2>/dev/null || true
    sleep 2
fi

# --- Set up overlay directory ---
echo "=== Setting up overlay at ${OVERLAY} ==="
mkdir -p "${OVERLAY_PLUGINS}"

# Copy the entire configuration directory from base.
# OSGi needs write access to org.eclipse.osgi/ for bundle caching,
# lock files, etc. A full copy (~10MB) is simpler and safer than
# selectively symlinking.
if [ ! -d "${OVERLAY_CONFIG}" ]; then
    echo "    Copying base configuration (first time setup)..."
    cp -r "${BASE_CONFIG}" "${OVERLAY_CONFIG}"
    # Remove any stale symlinks from previous overlay attempts
    find "${OVERLAY_CONFIG}" -type l -delete 2>/dev/null || true
elif [ ! -d "${OVERLAY_CONFIG}/org.eclipse.osgi" ]; then
    # Previous overlay had symlinks — replace with real copy
    echo "    Replacing symlinked configuration with full copy..."
    rm -rf "${OVERLAY_CONFIG}"
    cp -r "${BASE_CONFIG}" "${OVERLAY_CONFIG}"
fi

# --- Remove old JOP plugins from overlay ---
echo "=== Removing old JOP plugins ==="
rm -f "${OVERLAY_PLUGINS}"/com.jopdesign.*.jar

# --- Rebuild bundles.info ---
# Start from base, strip any old JOP entries (from previous direct installs)
grep -v "^com\.jopdesign\." "$BASE_BUNDLES" > "$OVERLAY_BUNDLES"

# --- Install new JOP plugins ---
echo "=== Installing plugins ==="
for jar in "${SITE_PLUGINS}"/com.jopdesign.*.jar; do
    filename="$(basename "$jar")"
    bundle_name="${filename%%_*}"
    version="${filename#*_}"
    version="${version%.jar}"

    cp "$jar" "${OVERLAY_PLUGINS}/"
    echo "${bundle_name},${version},${OVERLAY_PLUGINS}/${filename},4,false" >> "$OVERLAY_BUNDLES"
    echo "    Installed ${bundle_name} ${version}"
done

echo "=== Installation complete ==="
echo "    Overlay: ${OVERLAY}"
echo "    Plugins: $(ls "${OVERLAY_PLUGINS}"/com.jopdesign.*.jar 2>/dev/null | wc -l) JOP bundles"

# --- Launch ---
if [ "$INSTALL_ONLY" = false ]; then
    echo "=== Launching Eclipse (workspace: ${TEST_WORKSPACE}) ==="
    mkdir -p "$TEST_WORKSPACE"
    "${ECLIPSE_HOME}/eclipse" \
        -configuration "${OVERLAY_CONFIG}" \
        -data "$TEST_WORKSPACE" \
        -clean &
    echo "    Eclipse PID: $!"
fi
