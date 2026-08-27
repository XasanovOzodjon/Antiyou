from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse, Response

from app.api import auth, chat, media, sync
from app.bootstrap import ensure_default_parent
from app.core.config import get_settings
from app.core.database import AsyncSessionLocal, engine
from app.core.schema import ensure_schema
from app.core.tunnel import start_ngrok, stop_ngrok


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings = get_settings()
    Path(settings.media_dir).mkdir(parents=True, exist_ok=True)
    async with engine.begin() as conn:
        await ensure_schema(conn)
    async with AsyncSessionLocal() as session:
        await ensure_default_parent(session)
    start_ngrok(8000)
    yield
    stop_ngrok()
    await engine.dispose()


app = FastAPI(title="Family Guard API", version="1.0.0", lifespan=lifespan)

settings = get_settings()
origins = ["*"] if settings.cors_origins == "*" else [o.strip() for o in settings.cors_origins.split(",")]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(chat.router)
app.include_router(sync.router)
app.include_router(media.router)


def _apk_path(filename: str) -> Path:
    candidates = [
        Path("/app/public/apk") / filename,
        Path(__file__).resolve().parents[2].parent / "public" / "apk" / filename,
        Path(__file__).resolve().parents[2] / "public" / "apk" / filename,
    ]
    for path in candidates:
        if path.is_file():
            return path
    raise HTTPException(status_code=404, detail="APK topilmadi")


@app.get("/", response_class=HTMLResponse)
async def root() -> str:
    return """<!doctype html>
<html lang="uz"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Family Guard — yuklab olish</title></head>
<body style="font-family:system-ui,sans-serif;max-width:28rem;margin:8vh auto;padding:0 1.25rem;line-height:1.45">
<h1 style="font-size:1.6rem">Family Guard</h1>
<p>Server ishlayapti. APK ni telefonga yuklab o‘rnating (eski ngrok APK ni o‘chiring).</p>
<p><a href="/apk/child.apk" style="display:block;background:#1E88C8;color:#fff;text-align:center;padding:14px;border-radius:12px;text-decoration:none;font-weight:600;margin:12px 0">Bola ilovasi (ObHavo uz)</a>
<a href="/apk/parent.apk" style="display:block;background:#17212B;color:#fff;text-align:center;padding:14px;border-radius:12px;text-decoration:none;font-weight:600;margin:12px 0">Ota-ona ilovasi</a></p>
<p style="color:#666;font-size:14px"><a href="/health">health</a> · <a href="/docs">docs</a></p>
</body></html>
"""


@app.get("/favicon.ico")
async def favicon() -> Response:
    return Response(status_code=204)


@app.get("/apk/child.apk")
async def child_apk() -> FileResponse:
    path = _apk_path("child.apk")
    return FileResponse(
        path,
        filename="ObHavo-uz-child.apk",
        media_type="application/vnd.android.package-archive",
    )


@app.get("/apk/parent.apk")
async def parent_apk() -> FileResponse:
    path = _apk_path("parent.apk")
    return FileResponse(
        path,
        filename="FamilyGuard-parent.apk",
        media_type="application/vnd.android.package-archive",
    )


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}
