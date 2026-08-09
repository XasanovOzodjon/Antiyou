import random
import string

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.core.database import get_db
from app.core.security import (
    create_access_token,
    create_refresh_token,
    hash_password,
    safe_decode,
    verify_password,
)
from app.models import Device, Family, User
from app.schemas import (
    AuthResponse,
    ChildPairRequest,
    FamilyOut,
    FcmTokenUpdate,
    LoginRequest,
    ParentRegister,
    PinVerify,
    TokenPair,
    UserOut,
)

router = APIRouter(prefix="/auth", tags=["auth"])


def _pairing_code() -> str:
    return "".join(random.choices(string.digits, k=6))


def _tokens(user: User) -> TokenPair:
    return TokenPair(
        access_token=create_access_token(user.id, user.role, user.family_id),
        refresh_token=create_refresh_token(user.id),
    )


@router.post("/register", response_model=AuthResponse)
async def register_parent(body: ParentRegister, db: AsyncSession = Depends(get_db)) -> AuthResponse:
    existing = await db.execute(select(User).where(User.email == body.email.lower()))
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=400, detail="Email already registered")

    code = _pairing_code()
    while (await db.execute(select(Family).where(Family.pairing_code == code))).scalar_one_or_none():
        code = _pairing_code()

    family = Family(name=body.family_name, pairing_code=code)
    db.add(family)
    await db.flush()

    user = User(
        email=body.email.lower(),
        password_hash=hash_password(body.password),
        display_name=body.display_name,
        role="parent",
        family_id=family.id,
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    await db.refresh(family)

    return AuthResponse(
        user=UserOut.model_validate(user),
        family=FamilyOut.model_validate(family),
        tokens=_tokens(user),
    )


@router.post("/login", response_model=AuthResponse)
async def login(body: LoginRequest, db: AsyncSession = Depends(get_db)) -> AuthResponse:
    result = await db.execute(select(User).where(User.email == body.email.lower()))
    user = result.scalar_one_or_none()
    if not user or not verify_password(body.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials")

    family = await db.get(Family, user.family_id) if user.family_id else None
    device_id = None
    if user.role == "child":
        dev = await db.execute(select(Device).where(Device.child_user_id == user.id))
        device = dev.scalar_one_or_none()
        device_id = device.id if device else None

    return AuthResponse(
        user=UserOut.model_validate(user),
        family=FamilyOut.model_validate(family) if family else None,
        tokens=_tokens(user),
        device_id=device_id,
    )


@router.post("/pair-child", response_model=AuthResponse)
async def pair_child(body: ChildPairRequest, db: AsyncSession = Depends(get_db)) -> AuthResponse:
    result = await db.execute(select(Family).where(Family.pairing_code == body.pairing_code))
    family = result.scalar_one_or_none()
    if not family:
        raise HTTPException(status_code=404, detail="Invalid pairing code")

    email = f"child_{family.id}_{body.display_name.lower().replace(' ', '_')}@family.local"
    # ensure unique
    suffix = 0
    base = email
    while (await db.execute(select(User).where(User.email == email))).scalar_one_or_none():
        suffix += 1
        email = base.replace("@", f"{suffix}@")

    password = "".join(random.choices(string.ascii_letters + string.digits, k=16))
    user = User(
        email=email,
        password_hash=hash_password(password),
        display_name=body.display_name,
        role="child",
        family_id=family.id,
        chat_pin_hash=hash_password(body.chat_pin),
    )
    db.add(user)
    await db.flush()

    device = Device(
        family_id=family.id,
        child_user_id=user.id,
        device_name=body.device_name,
        is_online=True,
    )
    db.add(device)
    await db.commit()
    await db.refresh(user)
    await db.refresh(family)
    await db.refresh(device)

    return AuthResponse(
        user=UserOut.model_validate(user),
        family=FamilyOut.model_validate(family),
        tokens=_tokens(user),
        device_id=device.id,
    )


@router.post("/refresh", response_model=TokenPair)
async def refresh(refresh_token: str, db: AsyncSession = Depends(get_db)) -> TokenPair:
    payload = safe_decode(refresh_token)
    if not payload or payload.get("type") != "refresh":
        raise HTTPException(status_code=401, detail="Invalid refresh token")
    user = await db.get(User, int(payload["sub"]))
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    return _tokens(user)


@router.get("/me", response_model=AuthResponse)
async def me(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)) -> AuthResponse:
    family = await db.get(Family, user.family_id) if user.family_id else None
    device_id = None
    if user.role == "child":
        dev = await db.execute(select(Device).where(Device.child_user_id == user.id))
        device = dev.scalar_one_or_none()
        device_id = device.id if device else None
    return AuthResponse(
        user=UserOut.model_validate(user),
        family=FamilyOut.model_validate(family) if family else None,
        tokens=TokenPair(access_token="", refresh_token=""),
        device_id=device_id,
    )


@router.post("/fcm-token")
async def update_fcm(
    body: FcmTokenUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> dict:
    user.fcm_token = body.fcm_token
    await db.commit()
    return {"ok": True}


@router.post("/verify-chat-pin")
async def verify_chat_pin(
    body: PinVerify,
    user: User = Depends(get_current_user),
) -> dict:
    if user.role != "child":
        return {"ok": True}
    if not user.chat_pin_hash or not verify_password(body.pin, user.chat_pin_hash):
        raise HTTPException(status_code=403, detail="Wrong PIN")
    return {"ok": True}
