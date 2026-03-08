#!/usr/bin/env bash
# Thin wrapper — delegates to the locally installed Gradle 8.13
# (gradle-wrapper.jar is not required with this approach)
set -euo pipefail

if grep -qi microsoft /proc/version 2>/dev/null; then
    LINUX_HOME="$(getent passwd "$(id -un)" | cut -d: -f6)"
    if [[ -n "$LINUX_HOME" && "${HOME:-}" != "$LINUX_HOME" ]]; then
        export HOME="$LINUX_HOME"
    fi
fi

GRADLE_BIN="${GRADLE_BIN:-$HOME/.local/gradle-8.13/bin/gradle}"

if [[ ! -x "$GRADLE_BIN" ]]; then
    echo "ERROR: Gradle not found at $GRADLE_BIN"
    echo "Run: bash setup-android-build.sh"
    exit 1
fi

exec "$GRADLE_BIN" "$@"