from datetime import date, datetime, timezone

import pytest
from httpx import AsyncClient


async def _family(client: AsyncClient, email: str = "parent@example.com") -> dict:
    res = await client.post(
        "/auth/register",
        json={
            "email": email,
            "password": "secret12",
            "display_name": "Ota",
            "family_name": "Uy",
        },
    )
    assert res.status_code == 200, res.text
    return res.json()


async def _pair(client: AsyncClient, pairing_code: str, name: str = "Bola") -> dict:
    res = await client.post(
        "/auth/pair-child",
        json={
            "pairing_code": pairing_code,
            "display_name": name,
            "device_name": "Redmi 14C",
            "chat_pin": "1234",
        },
    )
    assert res.status_code == 200, res.text
    return res.json()


def _auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


@pytest.mark.asyncio
async def test_parent_lists_every_captured_notification(client: AsyncClient) -> None:
    parent = await _family(client)
    child = await _pair(client, parent["family"]["pairing_code"])
    posted = datetime(2026, 1, 15, 10, 0, tzinfo=timezone.utc)
    items = [
        {
            "package_name": f"com.app.{i}",
            "title": f"Title {i}",
            "text": f"Body {i}",
            "posted_at": posted.isoformat(),
        }
        for i in range(201)
    ]
    sync = await client.post(
        "/notifications/sync",
        headers=_auth(child["tokens"]["access_token"]),
        json={"device_id": child["device_id"], "items": items},
    )
    assert sync.status_code == 200, sync.text
    listed = await client.get(
        "/notifications",
        headers=_auth(parent["tokens"]["access_token"]),
    )
    assert listed.status_code == 200, listed.text
    body = listed.json()
    assert len(body) == 201
    names = {row["package_name"] for row in body}
    assert names == {f"com.app.{i}" for i in range(201)}


@pytest.mark.asyncio
async def test_duplicate_captured_notification_at_same_time_is_ignored(client: AsyncClient) -> None:
    parent = await _family(client)
    child = await _pair(client, parent["family"]["pairing_code"])
    posted = datetime(2026, 1, 15, 11, 0, tzinfo=timezone.utc).isoformat()
    payload = {
        "package_name": "com.youtube.app",
        "title": "Video",
        "text": "Kids song",
        "posted_at": posted,
    }
    headers = _auth(child["tokens"]["access_token"])
    body = {"device_id": child["device_id"], "items": [payload, payload]}
    first = await client.post("/notifications/sync", headers=headers, json=body)
    assert first.status_code == 200, first.text
    second = await client.post("/notifications/sync", headers=headers, json=body)
    assert second.status_code == 200, second.text
    listed = await client.get("/notifications", headers=_auth(parent["tokens"]["access_token"]))
    assert listed.status_code == 200
    assert len(listed.json()) == 1


@pytest.mark.asyncio
async def test_parent_deletes_one_captured_notification(client: AsyncClient) -> None:
    parent = await _family(client)
    child = await _pair(client, parent["family"]["pairing_code"])
    headers_child = _auth(child["tokens"]["access_token"])
    headers_parent = _auth(parent["tokens"]["access_token"])
    await client.post(
        "/notifications/sync",
        headers=headers_child,
        json={
            "device_id": child["device_id"],
            "items": [
                {
                    "package_name": "com.a",
                    "title": "A",
                    "text": "one",
                    "posted_at": datetime(2026, 1, 15, 12, 0, tzinfo=timezone.utc).isoformat(),
                },
                {
                    "package_name": "com.b",
                    "title": "B",
                    "text": "two",
                    "posted_at": datetime(2026, 1, 15, 12, 1, tzinfo=timezone.utc).isoformat(),
                },
            ],
        },
    )
    listed = await client.get("/notifications", headers=headers_parent)
    rows = listed.json()
    assert len(rows) == 2
    target_id = rows[0]["id"]
    gone = await client.delete(f"/notifications/{target_id}", headers=headers_parent)
    assert gone.status_code == 200, gone.text
    remaining = await client.get("/notifications", headers=headers_parent)
    left = remaining.json()
    assert len(left) == 1
    assert left[0]["id"] != target_id


@pytest.mark.asyncio
async def test_parent_clears_all_captured_notifications(client: AsyncClient) -> None:
    parent = await _family(client)
    child = await _pair(client, parent["family"]["pairing_code"])
    await client.post(
        "/notifications/sync",
        headers=_auth(child["tokens"]["access_token"]),
        json={
            "device_id": child["device_id"],
            "items": [
                {
                    "package_name": "com.a",
                    "title": "A",
                    "text": None,
                    "posted_at": datetime(2026, 1, 15, 13, 0, tzinfo=timezone.utc).isoformat(),
                },
                {
                    "package_name": "com.b",
                    "title": "B",
                    "text": "x",
                    "posted_at": datetime(2026, 1, 15, 13, 1, tzinfo=timezone.utc).isoformat(),
                },
            ],
        },
    )
    cleared = await client.delete("/notifications", headers=_auth(parent["tokens"]["access_token"]))
    assert cleared.status_code == 200, cleared.text
    listed = await client.get("/notifications", headers=_auth(parent["tokens"]["access_token"]))
    assert listed.json() == []


@pytest.mark.asyncio
async def test_child_cannot_delete_captured_notifications(client: AsyncClient) -> None:
    parent = await _family(client)
    child = await _pair(client, parent["family"]["pairing_code"])
    await client.post(
        "/notifications/sync",
        headers=_auth(child["tokens"]["access_token"]),
        json={
            "device_id": child["device_id"],
            "items": [
                {
                    "package_name": "com.a",
                    "title": "A",
                    "text": "x",
                    "posted_at": datetime(2026, 1, 15, 14, 0, tzinfo=timezone.utc).isoformat(),
                }
            ],
        },
    )
    listed = await client.get("/notifications", headers=_auth(parent["tokens"]["access_token"]))
    nid = listed.json()[0]["id"]
    one = await client.delete(
        f"/notifications/{nid}",
        headers=_auth(child["tokens"]["access_token"]),
    )
    all_rows = await client.delete(
        "/notifications",
        headers=_auth(child["tokens"]["access_token"]),
    )
    assert one.status_code == 403
    assert all_rows.status_code == 403
    still = await client.get("/notifications", headers=_auth(parent["tokens"]["access_token"]))
    assert len(still.json()) == 1


@pytest.mark.asyncio
async def test_clear_all_does_not_touch_another_family(client: AsyncClient) -> None:
    a = await _family(client, email="a@example.com")
    b = await _family(client, email="b@example.com")
    child_a = await _pair(client, a["family"]["pairing_code"], name="Aa")
    child_b = await _pair(client, b["family"]["pairing_code"], name="Bb")
    posted = datetime(2026, 1, 15, 15, 0, tzinfo=timezone.utc).isoformat()
    for child in (child_a, child_b):
        await client.post(
            "/notifications/sync",
            headers=_auth(child["tokens"]["access_token"]),
            json={
                "device_id": child["device_id"],
                "items": [
                    {
                        "package_name": "com.x",
                        "title": "Hi",
                        "text": "n",
                        "posted_at": posted,
                    }
                ],
            },
        )
    await client.delete("/notifications", headers=_auth(a["tokens"]["access_token"]))
    left_a = await client.get("/notifications", headers=_auth(a["tokens"]["access_token"]))
    left_b = await client.get("/notifications", headers=_auth(b["tokens"]["access_token"]))
    assert left_a.json() == []
    assert len(left_b.json()) == 1


@pytest.mark.asyncio
async def test_usage_includes_installed_app_with_zero_time(client: AsyncClient) -> None:
    parent = await _family(client)
    child = await _pair(client, parent["family"]["pairing_code"])
    today = date(2026, 1, 15)
    sync = await client.post(
        "/usage/sync",
        headers=_auth(child["tokens"]["access_token"]),
        json={
            "device_id": child["device_id"],
            "items": [
                {
                    "package_name": "com.never.opened",
                    "app_label": "Never",
                    "total_ms": 0,
                    "day": today.isoformat(),
                },
                {
                    "package_name": "com.used.app",
                    "app_label": "Used",
                    "total_ms": 60_000,
                    "day": today.isoformat(),
                },
            ],
        },
    )
    assert sync.status_code == 200, sync.text
    listed = await client.get(
        "/usage",
        params={"day": today.isoformat()},
        headers=_auth(parent["tokens"]["access_token"]),
    )
    assert listed.status_code == 200, listed.text
    by_pkg = {row["package_name"]: row for row in listed.json()}
    assert by_pkg["com.never.opened"]["total_ms"] == 0
    assert by_pkg["com.never.opened"]["app_label"] == "Never"
    assert by_pkg["com.used.app"]["total_ms"] == 60_000
