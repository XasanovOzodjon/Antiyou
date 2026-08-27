from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user, require_child, require_parent
from app.core.database import get_db
from app.core.sms import WINDOW_SECONDS, address_key, collapse_sms, same_sms
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
    added = 0
    accepted: list[SmsItem] = []
    for item in body.items:
        if any(same_sms(item, prev) for prev in accepted):
            continue
        window_start = item.received_at - timedelta(seconds=WINDOW_SECONDS)
        window_end = item.received_at + timedelta(seconds=WINDOW_SECONDS)
        existing = await db.execute(
            select(SmsEvent).where(
                SmsEvent.device_id == body.device_id,
                SmsEvent.body == item.body,
                SmsEvent.direction == item.direction,
                SmsEvent.received_at >= window_start,
                SmsEvent.received_at <= window_end,
            )
        )
        if any(address_key(row.address) == address_key(item.address) for row in existing.scalars().all()):
            continue
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
        accepted.append(item)
        added += 1
    await db.commit()
    return {"ok": True, "count": added}


@router.get("/sms", response_model=list[SmsItem])
async def list_sms(
    limit: int = 200,
    user: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
) -> list[SmsItem]:
    cap = min(max(limit, 1), 200)
    result = await db.execute(
        select(SmsEvent)
        .where(SmsEvent.family_id == user.family_id)
        .order_by(SmsEvent.received_at.desc(), SmsEvent.id.desc())
        .limit(min(cap * 3, 600))
    )
    unique = collapse_sms(list(result.scalars().all()))[:cap]
    return [
        SmsItem(address=r.address, body=r.body, direction=r.direction, received_at=r.received_at)
        for r in unique
    ]


@router.post("/notifications/sync")
async def sync_notifications(
    body: NotificationSyncRequest,
    user: User = Depends(require_child),
    db: AsyncSession = Depends(get_db),
) -> dict:
    await _assert_device(db, user, body.device_id)
    added = 0
    seen: set[tuple] = set()
    for item in body.items:
        key = (item.package_name, item.title, item.text, item.posted_at)
        if key in seen:
            continue
        seen.add(key)
        existing = await db.execute(
            select(NotificationEvent).where(
                NotificationEvent.device_id == body.device_id,
                NotificationEvent.package_name == item.package_name,
                NotificationEvent.title == item.title,
                NotificationEvent.text == item.text,
                NotificationEvent.posted_at == item.posted_at,
            )
        )
        if existing.scalar_one_or_none():
            continue
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
        added += 1
    await db.commit()
    return {"ok": True, "count": added}


@router.get("/notifications", response_model=list[NotificationItem])
async def list_notifications(
    user: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
) -> list[NotificationItem]:
    result = await db.execute(
        select(NotificationEvent)
        .where(NotificationEvent.family_id == user.family_id)
        .order_by(NotificationEvent.posted_at.desc(), NotificationEvent.id.desc())
    )
    return [
        NotificationItem(
            id=r.id,
            package_name=r.package_name,
            title=r.title,
            text=r.text,
            posted_at=r.posted_at,
        )
        for r in result.scalars().all()
    ]


@router.delete("/notifications/{notification_id}")
async def delete_notification(
    notification_id: int,
    user: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
) -> dict:
    row = await db.get(NotificationEvent, notification_id)
    if not row or row.family_id != user.family_id:
        raise HTTPException(status_code=404, detail="Notification not found")
    await db.delete(row)
    await db.commit()
    return {"ok": True}


@router.delete("/notifications")
async def delete_all_notifications(
    user: User = Depends(require_parent),
    db: AsyncSession = Depends(get_db),
) -> dict:
    result = await db.execute(
        delete(NotificationEvent).where(NotificationEvent.family_id == user.family_id)
    )
    await db.commit()
    return {"ok": True, "count": result.rowcount}


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

    cutoff = datetime.now(timezone.utc) - timedelta(minutes=5)
    devices_result = await db.execute(select(Device).where(Device.family_id == family.id))
    devices = list(devices_result.scalars().all())
    for d in devices:
        seen = d.last_seen_at
        if seen is None:
            d.is_online = False
            continue
        if seen.tzinfo is None:
            seen = seen.replace(tzinfo=timezone.utc)
        else:
            seen = seen.astimezone(timezone.utc)
        d.is_online = seen >= cutoff
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
