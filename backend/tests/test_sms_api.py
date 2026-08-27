from datetime import datetime, timedelta, timezone

import pytest
from httpx import AsyncClient


def _auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def _family(client: AsyncClient) -> dict:
    res = await client.post(
        "/auth/register",
        json={
            "email": "sms-parent@example.com",
            "password": "secret12",
            "display_name": "Ota",
            "family_name": "Uy",
        },
    )
    assert res.status_code == 200, res.text
    return res.json()


async def _pair(client: AsyncClient, pairing_code: str) -> dict:
    res = await client.post(
        "/auth/pair-child",
        json={
            "pairing_code": pairing_code,
            "display_name": "Bola",
            "device_name": "Redmi 14C",
            "chat_pin": "1234",
        },
    )
    assert res.status_code == 200, res.text
    return res.json()


async def _sync(client: AsyncClient, child: dict, items: list[dict]) -> None:
    res = await client.post(
        "/sms/sync",
        headers=_auth(child["tokens"]["access_token"]),
        json={"device_id": child["device_id"], "items": items},
    )
    assert res.status_code == 200, res.text


@pytest.mark.asyncio
async def test_duplicate_inbox_sms_is_stored_once(client: AsyncClient) -> None:
    parent = await _family(client)
    child = await _pair(client, parent["family"]["pairing_code"])
    received = datetime(2026, 8, 27, 12, 0, tzinfo=timezone.utc).isoformat()
    payload = {
        "address": "+998901112233",
        "body": "Qayerdasan?",
        "direction": "inbox",
        "received_at": received,
    }
    await _sync(client, child, [payload, payload])
    await _sync(client, child, [payload])
    listed = await client.get("/sms", headers=_auth(parent["tokens"]["access_token"]))
    assert listed.status_code == 200, listed.text
    rows = listed.json()
    assert len(rows) == 1
    assert rows[0]["address"] == "+998901112233"
    assert rows[0]["body"] == "Qayerdasan?"
    assert rows[0]["direction"] == "inbox"


@pytest.mark.asyncio
async def test_broadcast_and_inbox_copy_collapse_when_times_differ_slightly(
    client: AsyncClient,
) -> None:
    parent = await _family(client)
    child = await _pair(client, parent["family"]["pairing_code"])
    first = datetime(2026, 8, 27, 12, 0, 0, tzinfo=timezone.utc)
    await _sync(
        client,
        child,
        [
            {
                "address": "998901112233",
                "body": "Keldim",
                "direction": "inbox",
                "received_at": first.isoformat(),
            },
            {
                "address": "+998901112233",
                "body": "Keldim",
                "direction": "inbox",
                "received_at": (first + timedelta(seconds=1)).isoformat(),
            },
        ],
    )
    listed = await client.get("/sms", headers=_auth(parent["tokens"]["access_token"]))
    assert listed.status_code == 200, listed.text
    assert len(listed.json()) == 1
    row = listed.json()[0]
    assert row["body"] == "Keldim"
    assert row["direction"] == "inbox"


@pytest.mark.asyncio
async def test_parent_sees_child_sent_sms_alongside_inbox(client: AsyncClient) -> None:
    parent = await _family(client)
    child = await _pair(client, parent["family"]["pairing_code"])
    when = datetime(2026, 8, 27, 15, 30, tzinfo=timezone.utc)
    await _sync(
        client,
        child,
        [
            {
                "address": "+998909998877",
                "body": "Ok, ketyapman",
                "direction": "sent",
                "received_at": when.isoformat(),
            },
            {
                "address": "+998909998877",
                "body": "Qayerdasan?",
                "direction": "inbox",
                "received_at": (when - timedelta(minutes=2)).isoformat(),
            },
        ],
    )
    listed = await client.get("/sms", headers=_auth(parent["tokens"]["access_token"]))
    assert listed.status_code == 200, listed.text
    rows = listed.json()
    assert [(r["direction"], r["body"]) for r in rows] == [
        ("sent", "Ok, ketyapman"),
        ("inbox", "Qayerdasan?"),
    ]


@pytest.mark.asyncio
async def test_same_text_kept_when_one_is_sent_and_one_is_inbox(client: AsyncClient) -> None:
    parent = await _family(client)
    child = await _pair(client, parent["family"]["pairing_code"])
    when = datetime(2026, 8, 27, 16, 0, tzinfo=timezone.utc)
    await _sync(
        client,
        child,
        [
            {
                "address": "+998901110000",
                "body": "Ok",
                "direction": "sent",
                "received_at": when.isoformat(),
            },
            {
                "address": "+998901110000",
                "body": "Ok",
                "direction": "inbox",
                "received_at": when.isoformat(),
            },
        ],
    )
    listed = await client.get("/sms", headers=_auth(parent["tokens"]["access_token"]))
    rows = listed.json()
    assert {(r["direction"], r["body"]) for r in rows} == {("sent", "Ok"), ("inbox", "Ok")}
