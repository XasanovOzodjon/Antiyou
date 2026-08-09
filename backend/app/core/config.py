from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(Path(__file__).resolve().parents[2] / ".env"),
        extra="ignore",
    )

    database_url: str = "postgresql+asyncpg://familyguard:familyguard@localhost:5432/familyguard"
    database_url_sync: str = "postgresql://familyguard:familyguard@localhost:5432/familyguard"
    jwt_secret: str = "change-me"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 60
    refresh_token_expire_days: int = 30
    media_dir: str = "uploads"
    encryption_key: str = "dev-encryption-key-32bytes!!"
    cors_origins: str = "*"


@lru_cache
def get_settings() -> Settings:
    return Settings()
