# Kubuntu’da Family Guard — to‘liq ishga tushirish

Bu qo‘llanma: Android Studio o‘rnatish, emulator, backend va ikkala Android ilovani ishga tushirish.

---

## 0. Talablar

- Kubuntu (yoki boshqa Ubuntu asosidagi tizim)
- Internet (SDK/emulator bir necha GB)
- RAM kamida 8 GB (16 GB tavsiya)
- Diskda ~15–20 GB bo‘sh joy
- Repo: https://github.com/XasanovOzodjon/Antiyou

---

## 1. Loyihani clone qilish

```bash
cd ~
git clone https://github.com/XasanovOzodjon/Antiyou.git
cd Antiyou
ls
```

Ko‘rinishi kerak: `backend`, `mobile`, `docker-compose.yml`, `README.md`, `KUBUNTU_RUN.md`.

---

## 2. Backend: Docker + Python

### 2.1 Docker o‘rnatish

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2 python3-venv python3-pip curl
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

**Muhim:** shundan keyin tizimdan chiqib qayta kiring (yoki restart). Aks holda Docker ruxsat bermasligi mumkin.

Tekshiruv:

```bash
docker ps
```

### 2.2 Postgres va API

```bash
cd ~/Antiyou
docker compose up -d

cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp -n .env.example .env

uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Bu terminalni **yopmang** — API shu yerda ishlashi kerak.

Tekshiruv: brauzerda http://127.0.0.1:8000/health

Kutilgan javob:

```json
{"status":"ok"}
```

API hujjatlar: http://127.0.0.1:8000/docs

> Postgres Docker’da **5433** portda ochiladi (`.env` dagi `DATABASE_URL` shunga mos).

### 2.3 Keyingi safar backendni qisqa yoqish

```bash
cd ~/Antiyou
docker compose up -d
cd backend
source .venv/bin/activate
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Yoki:

```bash
~/Antiyou/scripts/run-backend.sh
```

---

## 3. Android Studio o‘rnatish

### Variant A — rasmiy (tavsiya)

1. https://developer.android.com/studio dan Linux `.tar.gz` yuklab oling.
2. Terminalda:

```bash
cd ~/Downloads
tar -xzf android-studio-*-linux.tar.gz
sudo mv android-studio /opt/
/opt/android-studio/bin/studio.sh
```

Menyuga qo‘shish: Android Studio ichida **Tools → Create Desktop Entry**.

### Variant B — Snap

```bash
sudo snap install android-studio --classic
android-studio
```

---

## 4. Android Studio birinchi sozlash (Setup Wizard)

1. **Standard** installation tanlang.
2. Theme: Light / Dark.
3. SDK yuklanishini kuting (Android SDK, Platform-Tools, Emulator).
4. Finish.

Agar Wizard o‘tib ketgan bo‘lsa:

**Settings → Languages & Frameworks → Android SDK**

**SDK Platforms** tab:
- Android SDK Platform **34** (yoki 35)

**SDK Tools** tab:
- Android SDK Build-Tools
- Android Emulator
- Android SDK Platform-Tools

Apply → OK.

SDK yo‘li odatda:

```text
/home/SIZNING_USER/Android/Sdk
```

### Gradle JDK

**Settings → Build, Execution, Deployment → Build Tools → Gradle**

- **Gradle JDK** → **JDK 17** (yoki Studio bundled JDK 17)

---

## 5. Emulator (AVD) yaratish

1. Bosh ekran: **More Actions → Virtual Device Manager**  
   yoki loyiha ochiq bo‘lsa: **Device Manager**.
2. **Create Device**.
3. Masalan **Pixel 6** → Next.
4. System Image: **API 34** (yoki 33). Yonida **Download** bo‘lsa — yuklab oling → Next.
5. AVD Name: `Pixel6_API34` → Finish.
6. ▶ Play bilan emulatorni yoqing (birinchi marta 1–3 daqiqa olishi mumkin).

### Emulator sekin bo‘lsa (KVM)

```bash
egrep -c '(vmx|svm)' /proc/cpuinfo
sudo apt install -y qemu-kvm libvirt-daemon-system
sudo usermod -aG kvm $USER
```

Qayta login qiling.

Ikkita ilova uchun ikkinchi AVD ham yarating (masalan `Pixel6_API34_Child`), yoki bitta emulator + bitta haqiqiy telefon.

---

## 6. Loyihani Android Studio’da ochish

Loyihada **2 ta alohida** Android modul bor:

| Papka | Ilova |
|-------|--------|
| `mobile/parent` | Ota-ona (Family Guard) |
| `mobile/child` | Bolalar (Oila Nazorati) |

### Parent

1. **File → Open**
2. `~/Antiyou/mobile/parent` ni tanlang
3. Trust / OK
4. **Gradle Sync** tugaguncha kuting

### Child

1. **File → Open** (yangi oynada ochish mumkin)
2. `~/Antiyou/mobile/child`

Agar `local.properties` so‘ralsa / SDK topilmasa, papkada yarating:

```properties
sdk.dir=/home/SIZNING_USER/Android/Sdk
```

Namuna: `local.properties.example`.

---

## 7. Emulatorga Run

1. Backend (`uvicorn`) ishlayotgan bo‘lsin.
2. Emulator yoqilgan bo‘lsin.
3. Yuqorida configuration: **app**, qurilma: emulator.
4. Yashil **▶ Run**.

### Emulator uchun API URL

Hozir `app/build.gradle.kts` da:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000\"")
```

`10.0.2.2` — emulatordan host (Kubuntu) dagi `localhost:8000` ga yo‘l. **Emulator uchun o‘zgartirmang.**

### Parent sinovi

1. Ro‘yxatdan o‘ting (email + parol).
2. Asosiy ekranda **juftlash kodi** (6 xona) chiqadi — yozib qo‘ying.
3. Tablar: Chat, Vaqt, SMS, Bildirish, Galereya.

### Child sinovi

1. Child ilovani Run qiling (boshqa emulator yoki telefon).
2. Juftlash kodi + ism + Chat PIN.
3. Ob-havo ekrani ochiladi.
4. Haroratga **uzoq bosib turing** → PIN → ota-ona chat.
5. **Ruxsatlarni sozlash**: SMS, galereya, joylashuv, Usage Access, Notification Listener.

---

## 8. Haqiqiy telefonga ulash

1. Telefonda: **Sozlamalar → Telefon haqida → Build number** ga 7 marta bosing.
2. **Dasturchilar uchun** menyuda **USB debugging** yoqing.
3. USB bilan ulang → **Allow**.
4. Android Studio qurilmalar ro‘yxatida telefon chiqadi → Run.

### Telefon uchun API URL (muhim)

Telefon va Kubuntu **bir xil Wi‑Fi**da bo‘lsin.

Kompyuter IP:

```bash
hostname -I
```

Masalan `192.168.1.10`. Ikkala faylda o‘zgartiring:

- `mobile/parent/app/build.gradle.kts`
- `mobile/child/app/build.gradle.kts`

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.10:8000\"")
```

**File → Sync Project with Gradle Files** → qayta Run.

Firewall:

```bash
sudo ufw allow 8000/tcp
```

| Qayerda ishlatasiz | `API_BASE_URL` |
|--------------------|----------------|
| Emulator | `http://10.0.2.2:8000` |
| Haqiqiy telefon | `http://LAN_IP:8000` |

---

## 9. Kunlik ish tartibi

**Terminal 1 — backend:**

```bash
cd ~/Antiyou
docker compose up -d
cd backend && source .venv/bin/activate
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

**Android Studio:**

1. Emulator ▶
2. Parent → Run
3. Child → Run

---

## 10. Muammolar va yechimlar

| Muammo | Yechim |
|--------|--------|
| `Docker permission denied` | `sudo usermod -aG docker $USER` + qayta login |
| Gradle Sync fail | Internet, JDK 17, Sync qayta |
| Emulator ochilmaydi | KVM, AVD qayta yaratish, boshqa system image |
| Ilova serverga ulanmaydi | `uvicorn` ishlayaptimi? Emulator: `10.0.2.2`, telefon: LAN IP |
| `/health` ochilmaydi | Port 8000, firewall, Docker Postgres |
| SDK topilmaydi | Android SDK path + `local.properties` |
| `role "familyguard" does not exist` | `docker compose up -d`, `.env` da port **5433** |

---

## 11. Arxitektura (qisqa)

```text
Kubuntu
 ├── Docker Postgres (:5433)
 ├── FastAPI uvicorn (:8000)
 └── Android Studio
      ├── Emulator A → Parent ilova
      └── Emulator B / telefon → Child ilova
              │
              └── HTTP → 10.0.2.2:8000 (emulator)
```

---

## 12. Foydali havolalar

- API docs: http://127.0.0.1:8000/docs
- GitHub: https://github.com/XasanovOzodjon/Antiyou
- Asosiy README: [README.md](README.md)
