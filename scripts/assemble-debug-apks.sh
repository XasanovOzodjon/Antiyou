#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
URL="${1:-${API_BASE_URL:-}}"
if [[ -z "$URL" && -f "$ROOT/.scratch/api-base-url.txt" ]]; then
  URL="$(tr -d '[:space:]' < "$ROOT/.scratch/api-base-url.txt")"
fi
if [[ -z "$URL" ]]; then
  echo "Usage: $0 <https://xxxx.ngrok-free.app>" >&2
  echo "Yoki avval scripts/run-debug.sh ni ishlatib URL ni oling." >&2
  exit 1
fi
echo "API_BASE_URL=$URL"
cd "$ROOT/mobile/child"
./gradlew assembleDebug -PAPI_BASE_URL="$URL"
cd "$ROOT/mobile/parent"
./gradlew assembleDebug -PAPI_BASE_URL="$URL"
echo
echo "Child:  $ROOT/mobile/child/app/build/outputs/apk/debug/app-debug.apk"
echo "Parent: $ROOT/mobile/parent/app/build/outputs/apk/debug/app-debug.apk"
