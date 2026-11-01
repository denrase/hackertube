#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 1 ]]; then echo "Usage: $0 TV_IP:DEBUG_PORT (already paired/authorized)" >&2; exit 2; fi
target="$1"
apk="$(cd "$(dirname "$0")/.." && pwd)/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk" ]] || { echo "Build first: ./gradlew assembleDebug" >&2; exit 1; }
export ADB_MDNS="${ADB_MDNS:-0}"
adb connect "$target"
adb -s "$target" get-state
adb -s "$target" install -r "$apk"
adb -s "$target" shell am start -n app.hackertube.tv/.MainActivity
