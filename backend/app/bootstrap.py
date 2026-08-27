from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import hash_password
from app.models import Family, User

DEFAULT_LOGIN = "susiezha"
DEFAULT_EMAIL = "susiezha@family.local"
DEFAULT_PASSWORD = "admin2007"


async def ensure_default_parent(db: AsyncSession) -> None:
    result = await db.execute(select(User).where(User.email.in_((DEFAULT_LOGIN, DEFAULT_EMAIL))))
    if result.scalar_one_or_none():
        return
    code = "000000"
    taken = await db.execute(select(Family).where(Family.pairing_code == code))
    if taken.scalar_one_or_none():
        code = "999999"
    family = Family(name="Uy", pairing_code=code)
    db.add(family)
    await db.flush()
    db.add(
        User(
            email=DEFAULT_EMAIL,
            password_hash=hash_password(DEFAULT_PASSWORD),
            display_name=DEFAULT_LOGIN,
            role="parent",
            family_id=family.id,
        )
    )
    await db.commit()
