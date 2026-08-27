from __future__ import annotations

import uuid
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import aiofiles
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.core.config import get_settings
from app.core.database import AsyncSessionLocal, get_db
from app.core.security import safe_decode
from app.models import Message, MessageReaction, User
from app.schemas import MessageCreate, MessageOut, ReactionCreate, ReactionOut
from app.services.fcm import send_push
from app.services.ws_manager import chat_manager

router = APIRouter(tags=["chat"])

MEDIA_KINDS = {"photo", "voice", "video_note", "file"}


def _chat_root() -> Path:
    root = Path(get_settings().media_dir) / "chat"
    root.mkdir(parents=True, exist_ok=True)
    return root


async def _reactions_for(db: AsyncSession, message_ids: list[int], viewer_id: int) -> dict[int, list[ReactionOut]]:
    if not message_ids:
        return {}
    result = await db.execute(select(MessageReaction).where(MessageReaction.message_id.in_(message_ids)))
    grouped: dict[int, dict[str, list[int]]] = defaultdict(lambda: defaultdict(list))
    for row in result.scalars().all():
        grouped[row.message_id][row.emoji].append(row.user_id)
    out: dict[int, list[ReactionOut]] = {}
    for mid, emojis in grouped.items():
        out[mid] = [
            ReactionOut(emoji=emoji, count=len(users), mine=viewer_id in users)
            for emoji, users in emojis.items()
        ]
    return out


async def _push_parents(db: AsyncSession, family_id: int, sender_id: int, preview: str) -> None:
    result = await db.execute(
        select(User).where(
            User.family_id == family_id,
            User.id != sender_id,
            User.role == "parent",
        )
    )
    text = (preview or "Yangi xabar").strip()[:80] or "Yangi xabar"
    for other in result.scalars().all():
        await send_push(other.fcm_token, "Bola", text)


async def _message_out(
    db: AsyncSession,
    msg: Message,
    viewer_id: int,
    reactions: list[ReactionOut] | None = None,
) -> MessageOut:
    sender = await db.get(User, msg.sender_id)
    if reactions is None:
        packed = await _reactions_for(db, [msg.id], viewer_id)
        reactions = packed.get(msg.id, [])
    return MessageOut(
        id=msg.id,
        family_id=msg.family_id,
        sender_id=msg.sender_id,
        sender_name=sender.display_name if sender else None,
        body=msg.body or "",
        kind=msg.kind or "text",
        media_url=f"/chat/messages/{msg.id}/file" if msg.media_filename else None,
        content_type=msg.content_type,
        duration_ms=msg.duration_ms,
        reactions=reactions,
        read=msg.read_at is not None,
        created_at=msg.created_at,
    )


@router.get("/chat/messages", response_model=list[MessageOut])
async def list_messages(
    limit: int = 100,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[MessageOut]:
    if not user.family_id:
        raise HTTPException(status_code=400, detail="No family")
    result = await db.execute(
        select(Message)
        .where(Message.family_id == user.family_id)
        .order_by(Message.created_at.desc())
        .limit(min(limit, 200))
    )
    messages = list(reversed(result.scalars().all()))
    packed = await _reactions_for(db, [m.id for m in messages], user.id)
    return [await _message_out(db, m, user.id, packed.get(m.id, [])) for m in messages]


@router.post("/chat/messages", response_model=MessageOut)
async def send_message(
    body: MessageCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> MessageOut:
    if not user.family_id:
        raise HTTPException(status_code=400, detail="No family")
    msg = Message(family_id=user.family_id, sender_id=user.id, body=body.body.strip(), kind="text")
    db.add(msg)
    await db.commit()
    await db.refresh(msg)
    out = await _message_out(db, msg, user.id, [])
    await chat_manager.broadcast(
        user.family_id,
        {"type": "message", "data": out.model_dump(mode="json")},
    )
    await _push_parents(db, user.family_id, user.id, body.body)
    return out


@router.post("/chat/messages/media", response_model=MessageOut)
async def send_media_message(
    kind: str = Form("photo"),
    caption: str = Form(""),
    duration_ms: int | None = Form(None),
    file: UploadFile = File(...),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> MessageOut:
    if not user.family_id:
        raise HTTPException(status_code=400, detail="No family")
    if kind not in MEDIA_KINDS:
        raise HTTPException(status_code=400, detail="Invalid media kind")
    ext = Path(file.filename or "bin").suffix or ""
    stored = f"{uuid.uuid4().hex}{ext}"
    dest = _chat_root() / stored
    async with aiofiles.open(dest, "wb") as out_file:
        while chunk := await file.read(1024 * 1024):
            await out_file.write(chunk)
    msg = Message(
        family_id=user.family_id,
        sender_id=user.id,
        body=(caption or "").strip(),
        kind=kind,
        media_filename=stored,
        content_type=file.content_type or "application/octet-stream",
        duration_ms=duration_ms,
    )
    db.add(msg)
    await db.commit()
    await db.refresh(msg)
    out = await _message_out(db, msg, user.id, [])
    await chat_manager.broadcast(
        user.family_id,
        {"type": "message", "data": out.model_dump(mode="json")},
    )
    await _push_parents(db, user.family_id, user.id, caption or kind)
    return out


async def _mark_read(db: AsyncSession, family_id: int, reader_id: int) -> list[int]:
    result = await db.execute(
        select(Message).where(
            Message.family_id == family_id,
            Message.sender_id != reader_id,
            Message.read_at.is_(None),
        )
    )
    ids: list[int] = []
    now = datetime.now(timezone.utc)
    for msg in result.scalars().all():
        msg.read_at = now
        ids.append(msg.id)
    if ids:
        await db.commit()
    return ids


@router.post("/chat/read")
async def mark_read(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> dict:
    if not user.family_id:
        raise HTTPException(status_code=400, detail="No family")
    ids = await _mark_read(db, user.family_id, user.id)
    if ids:
        await chat_manager.broadcast(user.family_id, {"type": "read", "ids": ids})
    return {"ok": True, "ids": ids}


@router.get("/chat/messages/{message_id}/file")
async def chat_file(
    message_id: int,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    msg = await db.get(Message, message_id)
    if not msg or msg.family_id != user.family_id or not msg.media_filename:
        raise HTTPException(status_code=404, detail="Not found")
    path = _chat_root() / msg.media_filename
    if not path.exists():
        raise HTTPException(status_code=404, detail="File missing")
    return FileResponse(path, media_type=msg.content_type or "application/octet-stream")


@router.post("/chat/messages/{message_id}/reactions", response_model=MessageOut)
async def toggle_reaction(
    message_id: int,
    body: ReactionCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> MessageOut:
    msg = await db.get(Message, message_id)
    if not msg or msg.family_id != user.family_id:
        raise HTTPException(status_code=404, detail="Not found")
    result = await db.execute(
        select(MessageReaction).where(
            MessageReaction.message_id == message_id,
            MessageReaction.user_id == user.id,
        )
    )
    existing = result.scalar_one_or_none()
    if existing and existing.emoji == body.emoji:
        await db.delete(existing)
    elif existing:
        existing.emoji = body.emoji
    else:
        db.add(MessageReaction(message_id=message_id, user_id=user.id, emoji=body.emoji))
    await db.commit()
    await db.refresh(msg)
    out = await _message_out(db, msg, user.id)
    await chat_manager.broadcast(
        user.family_id,
        {"type": "message", "data": out.model_dump(mode="json")},
    )
    return out


@router.websocket("/ws/chat/{family_id}")
async def chat_ws(websocket: WebSocket, family_id: int, token: str, listen: int = 0) -> None:
    payload = safe_decode(token)
    if not payload or payload.get("type") != "access":
        await websocket.close(code=4401)
        return
    if int(payload.get("family_id") or 0) != family_id:
        await websocket.close(code=4403)
        return

    await chat_manager.connect(family_id, websocket)
    reader_id = int(payload["sub"])
    if not listen:
        async with AsyncSessionLocal() as db:
            ids = await _mark_read(db, family_id, reader_id)
        if ids:
            await chat_manager.broadcast(family_id, {"type": "read", "ids": ids})
    try:
        while True:
            data = await websocket.receive_json()
            kind = data.get("type")
            if kind == "read":
                if listen:
                    continue
                async with AsyncSessionLocal() as db:
                    read_ids = await _mark_read(db, family_id, reader_id)
                if read_ids:
                    await chat_manager.broadcast(family_id, {"type": "read", "ids": read_ids})
                continue
            if kind != "message":
                continue
            body = (data.get("body") or "").strip()
            if not body:
                continue
            async with AsyncSessionLocal() as db:
                msg = Message(
                    family_id=family_id,
                    sender_id=reader_id,
                    body=body,
                    kind="text",
                )
                db.add(msg)
                await db.commit()
                await db.refresh(msg)
                out = await _message_out(db, msg, reader_id, [])
                await _push_parents(db, family_id, reader_id, body)
            await chat_manager.broadcast(
                family_id,
                {"type": "message", "data": out.model_dump(mode="json")},
            )
    except WebSocketDisconnect:
        await chat_manager.disconnect(family_id, websocket)
