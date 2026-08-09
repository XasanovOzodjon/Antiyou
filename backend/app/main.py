from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import auth, chat, media, sync
from app.core.config import get_settings
from app.core.database import Base, engine


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings = get_settings()
    Path(settings.media_dir).mkdir(parents=True, exist_ok=True)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
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


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}
