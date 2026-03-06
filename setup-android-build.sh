#!/usr/bin/env bash
# setup-android-build.sh - One-time setup: JDK 17 + Android SDK + Gradle wrapper
# Run once on WSL2, then use build-apk.sh for every build.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
GRADLE_VERSION="8.7"
# AGP 8.5.2 requires Gradle 8.7+.
# Find the latest cmdline-tools URL at:
#   https://developer.android.com/studio  → "Command line tools only"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()    { echo -e "${GREEN}[setup]${NC} $*"; }
warn()    { echo -e "${YELLOW}[warn]${NC}  $*"; }
error()   { echo -e "${RED}[error]${NC} $*"; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── 1. JDK 17 ────────────────────────────────────────────────────────────────
if java -version 2>&1 | grep -q 'version "17'; then
    info "JDK 17 already installed: $(java -version 2>&1 | head -1)"
else
    info "Installing OpenJDK 17..."
    sudo apt-get update -qq
    sudo apt-get install -y openjdk-17-jdk-headless unzip wget
    # Point JAVA_HOME at 17 even if other versions are installed
    sudo update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java 2>/dev/null || true
fi
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
info "JAVA_HOME=$JAVA_HOME"

# ── 2. Android SDK cmdline-tools ─────────────────────────────────────────────
TOOLS_DIR="$ANDROID_HOME/cmdline-tools/latest"
if [[ -f "$TOOLS_DIR/bin/sdkmanager" ]]; then
    info "Android cmdline-tools already present."
else
    info "Downloading Android cmdline-tools..."
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    TMP_ZIP="$(mktemp /tmp/cmdtools.XXXXXX.zip)"
    wget -q --show-progress -O "$TMP_ZIP" "$CMDLINE_TOOLS_URL"
    unzip -q "$TMP_ZIP" -d "$ANDROID_HOME/cmdline-tools"
    # Google zips it as "cmdline-tools/"; rename to "latest"
    if [[ -d "$ANDROID_HOME/cmdline-tools/cmdline-tools" ]]; then
        mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$TOOLS_DIR"
    fi
    rm -f "$TMP_ZIP"
    info "cmdline-tools installed at $TOOLS_DIR"
fi

export PATH="$TOOLS_DIR/bin:$ANDROID_HOME/platform-tools:$PATH"

# ── 3. SDK packages ───────────────────────────────────────────────────────────
info "Accepting SDK licenses..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true

PACKAGES=("platform-tools" "platforms;android-35" "build-tools;35.0.0")
for pkg in "${PACKAGES[@]}"; do
    if sdkmanager --list_installed 2>/dev/null | grep -q "$(echo "$pkg" | tr ';' ' ')"; then
        info "Already installed: $pkg"
    else
        info "Installing $pkg..."
        sdkmanager "$pkg"
    fi
done

# ── 4. Gradle wrapper ─────────────────────────────────────────────────────────
WRAPPER_PROPS="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.properties"
GRADLEW="$SCRIPT_DIR/gradlew"

if [[ -f "$GRADLEW" ]]; then
    info "Gradle wrapper already present."
else
    info "Generating Gradle $GRADLE_VERSION wrapper..."

    GRADLE_DIST_DIR="$HOME/.gradle/dists/gradle-${GRADLE_VERSION}-bin"
    GRADLE_BIN="$HOME/.local/gradle-${GRADLE_VERSION}/bin/gradle"

    if [[ ! -f "$GRADLE_BIN" ]]; then
        GRADLE_ZIP_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
        info "Downloading Gradle $GRADLE_VERSION..."
        mkdir -p "$HOME/.local"
        TMP_GZIP="$(mktemp /tmp/gradle.XXXXXX.zip)"
        wget -q --show-progress -O "$TMP_GZIP" "$GRADLE_ZIP_URL"
        unzip -q "$TMP_GZIP" -d "$HOME/.local"
        rm -f "$TMP_GZIP"
    fi

    mkdir -p "$SCRIPT_DIR/gradle/wrapper"
    (cd "$SCRIPT_DIR" && "$HOME/.local/gradle-${GRADLE_VERSION}/bin/gradle" wrapper \
        --gradle-version "$GRADLE_VERSION" \
        --distribution-type bin \
        --quiet)
    chmod +x "$GRADLEW"
    info "Gradle wrapper generated."
fi

# ── 5. Persist env vars in ~/.bashrc ──────────────────────────────────────────
MARKER="# digest.it android sdk"
if ! grep -q "$MARKER" ~/.bashrc 2>/dev/null; then
    info "Adding SDK env vars to ~/.bashrc..."
    cat >> ~/.bashrc <<BASHRC

$MARKER
export ANDROID_HOME="$ANDROID_HOME"
export JAVA_HOME="$JAVA_HOME"
export PATH="\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
BASHRC
    info "Reload your shell or run:  source ~/.bashrc"
fi

# ── 6. Smoke-test ─────────────────────────────────────────────────────────────
info "Verifying setup..."
ANDROID_HOME="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" \
    "$GRADLEW" --version --project-dir "$SCRIPT_DIR" --quiet 2>&1 | head -4

echo ""
echo -e "${GREEN}✓ Setup complete!${NC}"
echo -e "  Run  ${YELLOW}./build-apk.sh${NC}  to compile and get the APK."
