#!/usr/bin/env bash
# build-apk.sh - Security checks + assemble debug APK
# Usage:  ./build-apk.sh [--release]
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLEW="$SCRIPT_DIR/gradlew"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BOLD='\033[1m'; NC='\033[0m'
info()  { echo -e "${GREEN}[build]${NC} $*"; }
warn()  { echo -e "${YELLOW}[warn]${NC}  $*"; }
fail()  { echo -e "${RED}[FAIL]${NC}  $*"; SECURITY_FAIL=1; }

BUILD_TYPE="debug"
if [[ "${1:-}" == "--release" ]]; then
    BUILD_TYPE="release"
fi

# ── Prerequisites ─────────────────────────────────────────────────────────────
if [[ ! -f "$GRADLEW" ]]; then
    echo -e "${RED}Gradle wrapper not found. Run ./setup-android-build.sh first.${NC}"
    exit 1
fi
if ! java -version 2>&1 | grep -q 'version "17'; then
    export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
fi
export ANDROID_HOME
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# ─────────────────────────────────────────────────────────────────────────────
# SECURITY CHECKS
# ─────────────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}── Security Checks ──────────────────────────────────────────${NC}"
SECURITY_FAIL=0

SRC="$SCRIPT_DIR/app/src"

# 1. Hardcoded secrets (API keys / tokens) in Kotlin source
#    Allow: variable names like getApiKey(), X-API-Key (HTTP header), DataStore references
info "Checking for hardcoded secrets in source..."
SECRETS=$(grep -rn \
    --include="*.kt" \
    -E '(apiKey|api_key|API_KEY|secret|token|bearer|password)\s*=\s*"[A-Za-z0-9+/=_\-]{8,}"' \
    "$SRC/main/java/" 2>/dev/null \
    | grep -iv '//.*' \
    | grep -iv 'getString\|runBlocking\|prefs\.\|DataStore\|preference\|Preference' \
    || true)
if [[ -n "$SECRETS" ]]; then
    fail "Possible hardcoded credential(s) found:"
    echo "$SECRETS" | while IFS= read -r line; do echo "       $line"; done
else
    echo -e "  ${GREEN}✓${NC} No hardcoded secrets"
fi

# 2. Hardcoded non-localhost IP addresses in source (not in comments or config)
info "Checking for hardcoded IP addresses..."
HARDCODED_IPS=$(grep -rn \
    --include="*.kt" \
    -E '"https?://([0-9]{1,3}\.){3}[0-9]{1,3}' \
    "$SRC/main/java/" 2>/dev/null \
    | grep -v '^\s*//' \
    | grep -v 'localhost\|127\.0\.0\.1\|placeholder\|10\.0\.2\.2' \
    || true)
if [[ -n "$HARDCODED_IPS" ]]; then
    warn "Hardcoded IP(s) found (acceptable for dev, verify before release):"
    echo "$HARDCODED_IPS" | while IFS= read -r line; do echo "       $line"; done
else
    echo -e "  ${GREEN}✓${NC} No hardcoded IPs"
fi

# 3. Cleartext traffic flag in manifest
info "Checking cleartext traffic setting..."
if grep -q 'usesCleartextTraffic="true"' "$SRC/main/AndroidManifest.xml" 2>/dev/null; then
    if [[ "$BUILD_TYPE" == "release" ]]; then
        fail "android:usesCleartextTraffic=\"true\" should not be set in a release build"
    else
        warn "android:usesCleartextTraffic=\"true\" (OK for local dev, disable before publishing)"
    fi
else
    echo -e "  ${GREEN}✓${NC} Cleartext traffic disabled"
fi

# 4. Logging interceptor in release
info "Checking HTTP logging interceptor..."
if grep -rn 'Level.BODY\|BODY\|HttpLoggingInterceptor' "$SRC/main/java/" --include="*.kt" 2>/dev/null | grep -v '^\s*//' | grep -q '.'; then
    if [[ "$BUILD_TYPE" == "release" ]]; then
        warn "HttpLoggingInterceptor with BODY logging found — strips only via ProGuard; consider guarding with BuildConfig.DEBUG"
    else
        echo -e "  ${GREEN}✓${NC} Logging interceptor present (debug build — expected)"
    fi
fi

# 5. TODO / FIXME security notes
info "Checking for security TODOs..."
SEC_TODOS=$(grep -rn --include="*.kt" -iE 'TODO.*secur|FIXME.*secur|HACK.*auth|TODO.*auth|TODO.*crypt' \
    "$SRC/main/java/" 2>/dev/null || true)
if [[ -n "$SEC_TODOS" ]]; then
    warn "Security-related TODOs found:"
    echo "$SEC_TODOS" | while IFS= read -r line; do echo "       $line"; done
else
    echo -e "  ${GREEN}✓${NC} No security TODOs"
fi

# 6. Block release build on hard failures
if [[ $SECURITY_FAIL -eq 1 ]]; then
    echo ""
    echo -e "${RED}✗ Security check FAILED — fix the issues above before building.${NC}"
    exit 1
fi
echo ""
echo -e "${GREEN}✓ All security checks passed.${NC}"

# ─────────────────────────────────────────────────────────────────────────────
# BUILD
# ─────────────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}── Building ($BUILD_TYPE) ────────────────────────────────────${NC}"

GRADLE_TASK="assemble$(tr '[:lower:]' '[:upper:]' <<< "${BUILD_TYPE:0:1}")${BUILD_TYPE:1}"
info "Running: ./gradlew $GRADLE_TASK"

(cd "$SCRIPT_DIR" && "$GRADLEW" "$GRADLE_TASK" --stacktrace 2>&1)

# ─────────────────────────────────────────────────────────────────────────────
# REPORT
# ─────────────────────────────────────────────────────────────────────────────
APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/$BUILD_TYPE"
APK_FILE=$(find "$APK_DIR" -name "*.apk" 2>/dev/null | head -1)

echo ""
if [[ -n "$APK_FILE" ]]; then
    APK_SIZE=$(du -sh "$APK_FILE" | cut -f1)
    echo -e "${GREEN}${BOLD}✓ Build successful!${NC}"
    echo -e "  APK:  ${BOLD}$APK_FILE${NC}"
    echo -e "  Size: $APK_SIZE"
    echo ""
    # Windows-accessible path for easy copy
    WSL_WIN_PATH=$(wslpath -w "$APK_FILE" 2>/dev/null || true)
    if [[ -n "$WSL_WIN_PATH" ]]; then
        echo -e "  Windows path: ${YELLOW}$WSL_WIN_PATH${NC}"
        echo -e "  (Copy this to your phone or use:  adb install \"$APK_FILE\")"
    fi
else
    echo -e "${RED}Build succeeded but APK not found in $APK_DIR${NC}"
    exit 1
fi
