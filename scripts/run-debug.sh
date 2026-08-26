#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

docker compose up -d

cd "$ROOT/backend"
if [[ ! -d .venv ]]; then
  python3 -m venv .venv
  .venv/bin/pip install -r requirements.txt
fi

.venv/bin/uvicorn app.main:app --reload --host 0.0.0.0 --port 8000 &
UVICORN_PID=$!
cleanup() {
  kill "$UVICORN_PID" "${NGROK_PID:-}" 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 40); do
  if curl -sf http://127.0.0.1:8000/health >/dev/null; then
    break
  fi
  sleep 0.5
done
curl -sf http://127.0.0.1:8000/health >/dev/null

ngrok http 8000 --log=stdout >/tmp/familyguard-ngrok.log 2>&1 &
NGROK_PID=$!

URL=""
for _ in $(seq 1 40); do
  URL="$(curl -sf http://127.0.0.1:4040/api/tunnels | python3 -c "
import json, sys
data = json.load(sys.stdin)
tunnels = data.get('tunnels') or []
https = next((t['public_url'] for t in tunnels if str(t.get('public_url','')).startswith('https://')), '')
print(https or (tunnels[0]['public_url'] if tunnels else ''))
" 2>/dev/null || true)"
  if [[ -n "$URL" ]]; then
    break
  fi
  sleep 0.5
done

if [[ -z "$URL" ]]; then
  echo "ngrok public URL chiqmadi. /tmp/familyguard-ngrok.log ni ko‘ring." >&2
  exit 1
fi

mkdir -p "$ROOT/.scratch"
echo "$URL" | tee "$ROOT/.scratch/api-base-url.txt"
echo
echo "Debug API: $URL"
echo "APK (ikkala ilova):"
echo "  cd mobile/child && ./gradlew assembleDebug -PAPI_BASE_URL=$URL"
echo "  cd mobile/parent && ./gradlew assembleDebug -PAPI_BASE_URL=$URL"
echo
echo "Ctrl+C to‘xtatadi (API + ngrok)."
wait
