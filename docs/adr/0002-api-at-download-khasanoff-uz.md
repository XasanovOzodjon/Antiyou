# API lives at download.khasanoff.uz

Phones need a stable HTTPS origin instead of a changing ngrok URL. Production serves the Family Guard API at `https://download.khasanoff.uz` (Caddy + Let's Encrypt in front of uvicorn). Local debug still uses Postgres + uvicorn + ngrok; production sets `START_NGROK=0`.
