# Family Guard

Ochiq ota-ona nazorati tizimi: FastAPI backend + bolalar Android ilovasi + ota-ona Android ilovasi.

## Tarkib

| Papka | Vazifa |
|-------|--------|
| `backend/` | FastAPI API (auth, chat, usage, SMS, notifications, media, heartbeat) |
| `mobile/child/` | Bolalar ilovasi — ob-havo, PIN-chat, fon agent |
| `mobile/parent/` | Ota-ona paneli — dashboard, chat, SMS, galereya, screen time |
| `docker-compose.yml` | PostgreSQL (host port **5433**) |

## Backend ishga tushirish

```bash
# 1) Postgres
docker compose up -d

# 2) Python
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # kerak bo‘lsa

# 3) API
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Health: http://127.0.0.1:8000/health  
Docs: http://127.0.0.1:8000/docs

> Mac’da lokal Postgres 5432 band bo‘lsa, Docker **5433** portda ishlaydi (`.env` da shu port).

## Android

1. Android Studio’da `mobile/child` yoki `mobile/parent` ni oching.
2. `local.properties` yarating:

```properties
sdk.dir=/Users/YOU/Library/Android/sdk
```

3. Emulator uchun API URL allaqachon `http://10.0.2.2:8000`.
   Haqiqiy telefonda kompyuter LAN IP qo‘ying (`app/build.gradle.kts` ichidagi `API_BASE_URL`).

4. Run.

### Ota-ona ilovasi

- Ro‘yxatdan o‘ting → **juftlash kodi** chiqadi.
- Tablar: Asosiy, Chat, Vaqt, SMS, Bildirish, Galereya.

### Bolalar ilovasi

- Juftlash kodi + ism + chat PIN.
- Asosiy ekran: **ob-havo** (Open-Meteo).
- Haroratga **uzoq bosib turing** → PIN → ota-ona chat.
- «Ruxsatlarni sozlash»: SMS, galereya, joylashuv, Usage Access, Notification Listener.
- Foreground service: «Oila himoyasi faol».

## API qisqacha

- `POST /auth/register` — ota-ona
- `POST /auth/pair-child` — bola juftlash
- `GET/POST /chat/messages`, `WS /ws/chat/{family_id}?token=...`
- `POST /usage/sync`, `GET /usage`
- `POST /sms/sync`, `GET /sms`
- `POST /notifications/sync`, `GET /notifications`
- `POST /media/upload`, `GET /media`
- `POST /devices/heartbeat`
- `GET /dashboard/summary`

## FCM

`POST /auth/fcm-token` token saqlaydi. Push yuborish hozircha stub (`app/services/fcm.py`). Firebase Admin credentials qo‘shilganda yoqiladi.

## MVP cheklovlari

- Faqat Android
- Device Owner (o‘chirib bo‘lmaslik) — keyingi versiya
- Real-time ekran stream — yo‘q
- Sideload / oilaviy APK uchun mo‘ljallangan
