"""FCM push hooks — wire Google service account JSON via FCM_CREDENTIALS later."""

from __future__ import annotations

import logging

logger = logging.getLogger(__name__)


async def send_push(fcm_token: str | None, title: str, body: str, data: dict | None = None) -> bool:
    if not fcm_token:
        return False
    # Placeholder: log only until Firebase Admin SDK credentials are configured.
    logger.info("FCM stub -> token=%s… title=%s body=%s data=%s", fcm_token[:12], title, body, data)
    return True
