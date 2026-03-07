#!/usr/bin/env bash
# Thin wrapper — delegates to the locally installed Gradle 8.7
# (gradle-wrapper.jar is not required with this approach)
set -euo pipefail

GRADLE_BIN="${GRADLE_BIN:-$HOME/.local/gradle-8.7/bin/gradle}"

if [[ ! -x "$GRADLE_BIN" ]]; then
    echo "ERROR: Gradle not found at $GRADLE_BIN"
    echo "Run: bash setup-android-build.sh"
    exit 1
fi

exec "$GRADLE_BIN" "$@"
