#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not installed. Open android/ in Android Studio or install Gradle 8.9." >&2
  exit 2
fi
cd "$ROOT/android"
gradle --no-daemon assembleDebug
printf '\nAPK: %s\n' "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
