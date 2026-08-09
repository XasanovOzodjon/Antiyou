import uuid
from datetime import datetime
from pathlib import Path

import aiofiles
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user, require_child, require_parent
from app.core.config import get_settings
from app.core.database import get_db
from app.models import Device, MediaItem, User
from app.schemas import MediaOut

router = APIRouter(tags=["media"])


def _media_root() -> Path:
    root = Path(get_settings().media_dir)
    root.mkdir(parents=True, exist_ok=True)
    return root


@router.post("/media/upload", response_model=MediaOut)
async def upload_media(
    device_id: int = Form(...),
    local_uri: str = Form(...),
    taken_at: str | None = Form(None),
    file: UploadFile = File(...),
    user: User = Depends(require_child),
    db: AsyncSession = Depends(get_db),
) -> MediaOut:
    device = await db.get(Device, device_id)
    if not device or device.child_user_id != user.id:
        raise HTTPException(status_code=404, detail="Device not found")

    ext = Path(file.filename or "img.jpg").suffix or ".jpg"
    stored = f"{uuid.uuid4().hex}{ext}"
    dest = _media_root() / stored
    async with aiofiles.open(dest, "wb") as out:
        while chunk := await file.read(1024 * 1024):
            await out.write(chunk)

    taken = datetime.fromisoformat(taken_at) if taken_at else None
    item = MediaItem(
        family_id=user.family_id,
        device_id=device_id,
        local_uri=local_uri,
        filename=stored,
        content_type=file.content_type or "image/jpeg",
        taken_at=taken,
    )
    db.add(item)
    await db.commit()
    await db.refresh(item)
    return MediaOut(
        id=item.id,
        filename=item.filename,
        content_type=item.content_type,
        url=f"/media/file/{item.id}",
        taken_at=item.taken_at,
        created_at=item.created_at,
    )


@router.get("/media", response_model=list[MediaOut])
async def list_media(
    limit: int = 50,
    user: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
) -> list[MediaOut]:
    result = await db.execute(
        select(MediaItem)
        .where(MediaItem.family_id == user.family_id)
        .order_by(MediaItem.created_at.desc())
        .limit(min(limit, 100))
    )
    return [
        MediaOut(
            id=r.id,
            filename=r.filename,
            content_type=r.content_type,
            url=f"/media/file/{r.id}",
            taken_at=r.taken_at,
            created_at=r.created_at,
        )
        for r in result.scalars().all()
    ]


@router.get("/media/file/{media_id}")
async def get_media_file(
    media_id: int,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    item = await db.get(MediaItem, media_id)
    if not item or item.family_id != user.family_id:
        raise HTTPException(status_code=404, detail="Not found")
    path = _media_root() / item.filename
    if not path.exists():
        raise HTTPException(status_code=404, detail="File missing")
    return FileResponse(path, media_type=item.content_type)
