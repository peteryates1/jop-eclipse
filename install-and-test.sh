#!/bin/bash
#
# Build, install, and launch JOP Eclipse plugins for testing.
#
# Usage:
#   ./install-and-test.sh          # build + install + launch
#   ./install-and-test.sh --skip-build   # install + launch (reuse last build)
#   ./install-and-test.sh --install-only # build + install, no launch
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ECLIPSE_HOME="/opt/eclipse/java-latest-released/eclipse"
P2_POOL="/opt/.p2/pool/plugins"
BUNDLES_INFO="${ECLIPSE_HOME}/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
TEST_WORKSPACE="/tmp/jop-eclipse-test-workspace"
SITE_PLUGINS="${SCRIPT_DIR}/com.jopdesign.site/target/repository/plugins"

SKIP_BUILD=false
INSTALL_ONLY=false

for arg in "$@"; do
    case "$arg" in
        --skip-build)  SKIP_BUILD=true ;;
        --install-only) INSTALL_ONLY=true ;;
        --help|-h)
            echo "Usage: $0 [--skip-build] [--install-only]"
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

# --- Remove old JOP plugins ---
echo "=== Removing old JOP plugins ==="
rm -f "${P2_POOL}"/com.jopdesign.*.jar
# Remove old entries from bundles.info
grep -v "^com\.jopdesign\." "$BUNDLES_INFO" > "${BUNDLES_INFO}.tmp"
mv "${BUNDLES_INFO}.tmp" "$BUNDLES_INFO"

# --- Install new plugins ---
echo "=== Installing plugins ==="
for jar in "${SITE_PLUGINS}"/com.jopdesign.*.jar; do
    filename="$(basename "$jar")"
    # Extract bundle symbolic name and version from filename
    # e.g. com.jopdesign.core_1.0.0.202602181703.jar
    bundle_name="${filename%%_*}"
    version="${filename#*_}"
    version="${version%.jar}"

    cp "$jar" "${P2_POOL}/"
    echo "${bundle_name},${version},../../../.p2/pool/plugins/${filename},4,false" >> "$BUNDLES_INFO"
    echo "    Installed ${bundle_name} ${version}"
done

echo "=== Installation complete ==="

# --- Launch ---
if [ "$INSTALL_ONLY" = false ]; then
    echo "=== Launching Eclipse (workspace: ${TEST_WORKSPACE}) ==="
    mkdir -p "$TEST_WORKSPACE"
    "${ECLIPSE_HOME}/eclipse" -data "$TEST_WORKSPACE" -clean &
    echo "    Eclipse PID: $!"
fi
