from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user, require_child, require_parent
from app.core.database import get_db
from app.models import AppUsageDaily, Device, Family, NotificationEvent, SmsEvent, User
from app.schemas import (
    DashboardSummary,
    DeviceOut,
    FamilyOut,
    HeartbeatRequest,
    NotificationItem,
    NotificationSyncRequest,
    SmsItem,
    SmsSyncRequest,
    UsageItem,
    UsageSyncRequest,
)

router = APIRouter(tags=["sync"])


async def _assert_device(db: AsyncSession, user: User, device_id: int) -> Device:
    device = await db.get(Device, device_id)
    if not device or device.family_id != user.family_id:
        raise HTTPException(status_code=404, detail="Device not found")
    if user.role == "child" and device.child_user_id != user.id:
        raise HTTPException(status_code=403, detail="Not your device")
    return device


@router.post("/usage/sync")
async def sync_usage(
    body: UsageSyncRequest,
    user: User = Depends(require_child),
    db: AsyncSession = Depends(get_db),
) -> dict:
    await _assert_device(db, user, body.device_id)
    for item in body.items:
        result = await db.execute(
            select(AppUsageDaily).where(
                AppUsageDaily.device_id == body.device_id,
                AppUsageDaily.day == item.day,
                AppUsageDaily.package_name == item.package_name,
            )
        )
        row = result.scalar_one_or_none()
        if row:
            row.total_ms = item.total_ms
            row.app_label = item.app_label
        else:
            db.add(
                AppUsageDaily(
                    family_id=user.family_id,
                    device_id=body.device_id,
                    day=item.day,
                    package_name=item.package_name,
                    app_label=item.app_label,
                    total_ms=item.total_ms,
                )
            )
    await db.commit()
    return {"ok": True, "count": len(body.items)}


@router.get("/usage", response_model=list[UsageItem])
async def get_usage(
    day: str | None = None,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[UsageItem]:
    if not user.family_id:
        return []
    from datetime import date as date_cls

    target = date_cls.fromisoformat(day) if day else datetime.now(timezone.utc).date()
    result = await db.execute(
        select(AppUsageDaily)
        .where(AppUsageDaily.family_id == user.family_id, AppUsageDaily.day == target)
        .order_by(AppUsageDaily.total_ms.desc())
    )
    rows = result.scalars().all()
    return [
        UsageItem(
            package_name=r.package_name,
            app_label=r.app_label,
            total_ms=r.total_ms,
            day=r.day,
        )
        for r in rows
    ]


@router.post("/sms/sync")
async def sync_sms(
    body: SmsSyncRequest,
    user: User = Depends(require_child),
    db: AsyncSession = Depends(get_db),
) -> dict:
    await _assert_device(db, user, body.device_id)
    for item in body.items:
        db.add(
            SmsEvent(
                family_id=user.family_id,
                device_id=body.device_id,
                address=item.address,
                body=item.body,
                direction=item.direction,
                received_at=item.received_at,
            )
        )
    await db.commit()
    return {"ok": True, "count": len(body.items)}


@router.get("/sms", response_model=list[SmsItem])
async def list_sms(
    limit: int = 100,
    user: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
) -> list[SmsItem]:
    result = await db.execute(
        select(SmsEvent)
        .where(SmsEvent.family_id == user.family_id)
        .order_by(SmsEvent.received_at.desc())
        .limit(min(limit, 200))
    )
    return [
        SmsItem(address=r.address, body=r.body, direction=r.direction, received_at=r.received_at)
        for r in result.scalars().all()
    ]


@router.post("/notifications/sync")
async def sync_notifications(
    body: NotificationSyncRequest,
    user: User = Depends(require_child),
    db: AsyncSession = Depends(get_db),
) -> dict:
    await _assert_device(db, user, body.device_id)
    for item in body.items:
        db.add(
            NotificationEvent(
                family_id=user.family_id,
                device_id=body.device_id,
                package_name=item.package_name,
                title=item.title,
                text=item.text,
                posted_at=item.posted_at,
            )
        )
    await db.commit()
    return {"ok": True, "count": len(body.items)}


@router.get("/notifications", response_model=list[NotificationItem])
async def list_notifications(
    limit: int = 100,
    user: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
) -> list[NotificationItem]:
    result = await db.execute(
        select(NotificationEvent)
        .where(NotificationEvent.family_id == user.family_id)
        .order_by(NotificationEvent.posted_at.desc())
        .limit(min(limit, 200))
    )
    return [
        NotificationItem(
            package_name=r.package_name,
            title=r.title,
            text=r.text,
            posted_at=r.posted_at,
        )
        for r in result.scalars().all()
    ]


@router.post("/devices/heartbeat")
async def heartbeat(
    body: HeartbeatRequest,
    user: User = Depends(require_child),
    db: AsyncSession = Depends(get_db),
) -> dict:
    device = await _assert_device(db, user, body.device_id)
    device.last_seen_at = datetime.now(timezone.utc)
    device.is_online = True
    device.wifi_ssid = body.wifi_ssid
    await db.commit()
    return {"ok": True}


@router.get("/dashboard/summary", response_model=DashboardSummary)
async def dashboard(
    user: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
) -> DashboardSummary:
    family = await db.get(Family, user.family_id)
    if not family:
        raise HTTPException(status_code=404, detail="Family not found")

    # mark offline if no heartbeat in 2 minutes
    cutoff = datetime.now(timezone.utc) - timedelta(minutes=2)
    devices_result = await db.execute(select(Device).where(Device.family_id == family.id))
    devices = list(devices_result.scalars().all())
    for d in devices:
        if d.last_seen_at and d.last_seen_at.replace(tzinfo=timezone.utc) < cutoff:
            d.is_online = False
    await db.commit()

    today = datetime.now(timezone.utc).date()
    usage_result = await db.execute(
        select(AppUsageDaily)
        .where(AppUsageDaily.family_id == family.id, AppUsageDaily.day == today)
        .order_by(AppUsageDaily.total_ms.desc())
        .limit(10)
    )
    top = [
        UsageItem(
            package_name=r.package_name,
            app_label=r.app_label,
            total_ms=r.total_ms,
            day=r.day,
        )
        for r in usage_result.scalars().all()
    ]

    return DashboardSummary(
        family=FamilyOut.model_validate(family),
        devices=[DeviceOut.model_validate(d) for d in devices],
        top_apps_today=top,
    )
