from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

WINDOW_SECONDS = 2.0


def address_key(address: str) -> str:
    digits = "".join(ch for ch in (address or "") if ch.isdigit())
    if len(digits) >= 9:
        return digits[-9:]
    return digits or (address or "").strip().lower()


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def same_sms(left: Any, right: Any, window_seconds: float = WINDOW_SECONDS) -> bool:
    if address_key(getattr(left, "address", "")) != address_key(getattr(right, "address", "")):
        return False
    if (getattr(left, "body", "") or "") != (getattr(right, "body", "") or ""):
        return False
    if (getattr(left, "direction", "inbox") or "inbox") != (getattr(right, "direction", "inbox") or "inbox"):
        return False
    delta = abs((_as_utc(left.received_at) - _as_utc(right.received_at)).total_seconds())
    return delta <= window_seconds


def collapse_sms(rows: list[Any], window_seconds: float = WINDOW_SECONDS) -> list[Any]:
    kept: list[Any] = []
    for row in rows:
        if any(same_sms(row, existing, window_seconds) for existing in kept):
            continue
        kept.append(row)
    return kept
