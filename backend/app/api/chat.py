from fastapi import APIRouter, Depends, HTTPException, WebSocket, WebSocketDisconnect
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.core.database import AsyncSessionLocal, get_db
from app.core.security import safe_decode
from app.models import Message, User
from app.schemas import MessageCreate, MessageOut
from app.services.fcm import send_push
from app.services.ws_manager import chat_manager

router = APIRouter(tags=["chat"])


async def _message_out(db: AsyncSession, msg: Message) -> MessageOut:
    sender = await db.get(User, msg.sender_id)
    return MessageOut(
        id=msg.id,
        family_id=msg.family_id,
        sender_id=msg.sender_id,
        sender_name=sender.display_name if sender else None,
        body=msg.body,
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
    return [await _message_out(db, m) for m in messages]


@router.post("/chat/messages", response_model=MessageOut)
async def send_message(
    body: MessageCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> MessageOut:
    if not user.family_id:
        raise HTTPException(status_code=400, detail="No family")
    msg = Message(family_id=user.family_id, sender_id=user.id, body=body.body.strip())
    db.add(msg)
    await db.commit()
    await db.refresh(msg)
    out = await _message_out(db, msg)
    await chat_manager.broadcast(
        user.family_id,
        {"type": "message", "data": out.model_dump(mode="json")},
    )
    # Notify other family members via FCM stub
    others = await db.execute(select(User).where(User.family_id == user.family_id, User.id != user.id))
    for other in others.scalars().all():
        await send_push(other.fcm_token, "Yangi xabar", body.body[:80])
    return out


@router.websocket("/ws/chat/{family_id}")
async def chat_ws(websocket: WebSocket, family_id: int, token: str) -> None:
    payload = safe_decode(token)
    if not payload or payload.get("type") != "access":
        await websocket.close(code=4401)
        return
    if int(payload.get("family_id") or 0) != family_id:
        await websocket.close(code=4403)
        return

    await chat_manager.connect(family_id, websocket)
    try:
        while True:
            data = await websocket.receive_json()
            if data.get("type") != "message":
                continue
            body = (data.get("body") or "").strip()
            if not body:
                continue
            async with AsyncSessionLocal() as db:
                msg = Message(
                    family_id=family_id,
                    sender_id=int(payload["sub"]),
                    body=body,
                )
                db.add(msg)
                await db.commit()
                await db.refresh(msg)
                out = await _message_out(db, msg)
            await chat_manager.broadcast(
                family_id,
                {"type": "message", "data": out.model_dump(mode="json")},
            )
    except WebSocketDisconnect:
        await chat_manager.disconnect(family_id, websocket)
