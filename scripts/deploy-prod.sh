#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${DEPLOY_HOST:-ubuntu@download.khasanoff.uz}"
KEY="${DEPLOY_KEY:-$HOME/.ssh/Ozodjon.pem}"
REMOTE_DIR="${DEPLOY_DIR:-/home/ubuntu/antiyou}"

ssh_cmd=(ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -i "$KEY" "$HOST")
rsync -az \
  --exclude '.git/' \
  --exclude '.venv/' \
  --exclude '.agents/' \
  --exclude '.scratch/' \
  --exclude '.env' \
  --exclude '**/__pycache__/' \
  --exclude '**/.gradle/' \
  --exclude '**/build/' \
  -e "ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -i $KEY" \
  "$ROOT/" "$HOST:$REMOTE_DIR/"

"${ssh_cmd[@]}" bash -s -- "$REMOTE_DIR" <<'REMOTE'
set -euo pipefail
DIR="$1"
cd "$DIR"
if [[ ! -f .env ]]; then
  umask 077
  cat > .env <<EOF
POSTGRES_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -hex 48)
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=60
REFRESH_TOKEN_EXPIRE_DAYS=30
ENCRYPTION_KEY=$(openssl rand -hex 16)
CORS_ORIGINS=*
START_NGROK=0
MEDIA_DIR=/app/uploads
TLS_EMAIL=admin@khasanoff.uz
EOF
  echo "Created $DIR/.env with new secrets"
fi
sudo docker compose -f docker-compose.prod.yml pull db caddy
sudo docker compose -f docker-compose.prod.yml up -d --build
sudo docker compose -f docker-compose.prod.yml ps
REMOTE

echo
echo "Health: https://download.khasanoff.uz/health"
curl -fsS --retry 8 --retry-delay 3 --retry-all-errors https://download.khasanoff.uz/health
echo
