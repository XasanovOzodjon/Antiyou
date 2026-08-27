from sqlalchemy import inspect, text
from sqlalchemy.ext.asyncio import AsyncConnection

from app.models import Base


_MESSAGE_COLUMNS = {
    "kind": "kind VARCHAR(20) DEFAULT 'text'",
    "media_filename": "media_filename VARCHAR(255)",
    "content_type": "content_type VARCHAR(120)",
    "duration_ms": "duration_ms INTEGER",
    "read_at": "read_at TIMESTAMPTZ",
}


def _sync_schema(connection) -> None:
    inspector = inspect(connection)
    tables = set(inspector.get_table_names())
    if "messages" in tables:
        cols = {c["name"] for c in inspector.get_columns("messages")}
        for name, ddl in _MESSAGE_COLUMNS.items():
            if name not in cols:
                connection.execute(text(f"ALTER TABLE messages ADD COLUMN {ddl}"))
    Base.metadata.create_all(connection)


async def ensure_schema(conn: AsyncConnection) -> None:
    await conn.run_sync(_sync_schema)
