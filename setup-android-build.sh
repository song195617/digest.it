#!/usr/bin/env bash
# setup-android-build.sh - One-time setup: JDK 17 + Android SDK + Gradle wrapper
# Run once on WSL2, then use build-apk.sh for every build.
set -euo pipefail

if grep -qi microsoft /proc/version 2>/dev/null; then
    LINUX_HOME="$(getent passwd "$(id -un)" | cut -d: -f6)"
    if [[ -n "$LINUX_HOME" && "${HOME:-}" != "$LINUX_HOME" ]]; then
        export HOME="$LINUX_HOME"
    fi
fi

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
GRADLE_VERSION="8.13"
# AGP 8.13 requires Gradle 8.13+.
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
    if [[ -d "$ANDROID_HOME/cmdline-tools/cmdline-tools" ]]; then
        mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$TOOLS_DIR"
    fi
    rm -f "$TMP_ZIP"
    info "cmdline-tools installed at $TOOLS_DIR"
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT
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

# ── 4. Gradle binary + thin gradlew ──────────────────────────────────────────
GRADLE_BIN="$HOME/.local/gradle-${GRADLE_VERSION}/bin/gradle"
GRADLEW="$SCRIPT_DIR/gradlew"

if [[ ! -f "$GRADLE_BIN" ]]; then
    info "Downloading Gradle $GRADLE_VERSION..."
    mkdir -p "$HOME/.local"
    TMP_GZIP="$(mktemp /tmp/gradle.XXXXXX.zip)"
    wget -q --show-progress -O "$TMP_GZIP" \
        "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    unzip -q "$TMP_GZIP" -d "$HOME/.local"
    rm -f "$TMP_GZIP"
    info "Gradle $GRADLE_VERSION installed at $GRADLE_BIN"
else
    info "Gradle $GRADLE_VERSION already present."
fi

if [[ ! -f "$GRADLEW" ]]; then
    mkdir -p "$SCRIPT_DIR/gradle/wrapper"
    cat > "$GRADLEW" <<GRADLEW_SCRIPT
#!/usr/bin/env bash
if grep -qi microsoft /proc/version 2>/dev/null; then
    LINUX_HOME="4(getent passwd "4(id -un)" | cut -d: -f6)"
    if [[ -n "4LINUX_HOME" && "4{HOME:-}" != "4LINUX_HOME" ]]; then
        export HOME="4LINUX_HOME"
    fi
fi
GRADLE_BIN="4{GRADLE_BIN:-4HOME/.local/gradle-${GRADLE_VERSION}/bin/gradle}"
if [[ ! -x "4GRADLE_BIN" ]]; then
    echo "ERROR: Gradle not found at 4GRADLE_BIN. Run: bash setup-android-build.sh"
    exit 1
fi
exec "4GRADLE_BIN" "4@"
GRADLEW_SCRIPT
    chmod +x "$GRADLEW"
    cat > "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.properties" <<PROPS
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
PROPS
    info "gradlew created (delegates to local Gradle $GRADLE_VERSION)."
else
    info "gradlew already present."
fi

MARKER="# digest.it android sdk"
if ! grep -q "$MARKER" ~/.bashrc 2>/dev/null; then
    info "Adding SDK env vars to ~/.bashrc..."
    cat >> ~/.bashrc <<BASHRC

$MARKER
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export JAVA_HOME="$JAVA_HOME"
export PATH="\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
BASHRC
    info "Reload your shell or run:  source ~/.bashrc"
fi

info "Verifying setup..."
ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" JAVA_HOME="$JAVA_HOME" \
    "$GRADLEW" --version --project-dir "$SCRIPT_DIR" --quiet 2>&1 | head -4

echo ""
echo -e "${GREEN}✓ Setup complete!${NC}"
echo -e "  Run  ${YELLOW}./build-apk.sh${NC}  to compile and get the APK."