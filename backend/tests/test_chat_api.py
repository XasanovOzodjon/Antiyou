from pathlib import Path

import pytest
from httpx import AsyncClient


def _auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def _join(client: AsyncClient, role: str, device_name: str = "Redmi 14C") -> dict:
    res = await client.post("/auth/auto-join", json={"role": role, "device_name": device_name})
    assert res.status_code == 200, res.text
    return res.json()


@pytest.mark.asyncio
async def test_auto_join_connects_one_parent_and_one_child(client: AsyncClient) -> None:
    parent = await _join(client, "parent")
    child = await _join(client, "child")
    assert parent["user"]["role"] == "parent"
    assert child["user"]["role"] == "child"
    assert parent["family"]["id"] == child["family"]["id"]
    assert child["device_id"]
    again = await _join(client, "child", "Redmi 14C again")
    assert again["user"]["id"] == child["user"]["id"]
    assert again["device_id"] == child["device_id"]


@pytest.mark.asyncio
async def test_chat_photo_is_visible_to_the_other_side(client: AsyncClient, tmp_path: Path) -> None:
    parent = await _join(client, "parent")
    child = await _join(client, "child")
    photo = tmp_path / "hi.jpg"
    photo.write_bytes(b"\xff\xd8\xff fake-jpeg")
    sent = await client.post(
        "/chat/messages/media",
        headers=_auth(parent["tokens"]["access_token"]),
        data={"kind": "photo", "caption": "salom"},
        files={"file": ("hi.jpg", photo.read_bytes(), "image/jpeg")},
    )
    assert sent.status_code == 200, sent.text
    body = sent.json()
    assert body["kind"] == "photo"
    assert body["body"] == "salom"
    assert body["media_url"]
    listed = await client.get("/chat/messages", headers=_auth(child["tokens"]["access_token"]))
    assert listed.status_code == 200, listed.text
    row = listed.json()[-1]
    assert row["kind"] == "photo"
    assert row["id"] == body["id"]
    file_res = await client.get(
        row["media_url"],
        headers=_auth(child["tokens"]["access_token"]),
    )
    assert file_res.status_code == 200
    assert file_res.content.startswith(b"\xff\xd8\xff")


@pytest.mark.asyncio
async def test_chat_reaction_toggles_on_a_message(client: AsyncClient) -> None:
    parent = await _join(client, "parent")
    child = await _join(client, "child")
    msg = await client.post(
        "/chat/messages",
        headers=_auth(parent["tokens"]["access_token"]),
        json={"body": "ping"},
    )
    assert msg.status_code == 200, msg.text
    mid = msg.json()["id"]
    add = await client.post(
        f"/chat/messages/{mid}/reactions",
        headers=_auth(child["tokens"]["access_token"]),
        json={"emoji": "❤️"},
    )
    assert add.status_code == 200, add.text
    listed = await client.get("/chat/messages", headers=_auth(parent["tokens"]["access_token"]))
    reactions = listed.json()[-1]["reactions"]
    assert reactions == [{"emoji": "❤️", "count": 1, "mine": False}]
    child_view = await client.get("/chat/messages", headers=_auth(child["tokens"]["access_token"]))
    assert child_view.json()[-1]["reactions"][0]["mine"] is True
    await client.post(
        f"/chat/messages/{mid}/reactions",
        headers=_auth(child["tokens"]["access_token"]),
        json={"emoji": "❤️"},
    )
    gone = await client.get("/chat/messages", headers=_auth(parent["tokens"]["access_token"]))
    assert gone.json()[-1]["reactions"] == []


@pytest.mark.asyncio
async def test_child_message_pushes_parent_not_child(client: AsyncClient, monkeypatch: pytest.MonkeyPatch) -> None:
    pushed: list[tuple[str | None, str, str]] = []

    async def fake_push(token: str | None, title: str, body: str, data: dict | None = None) -> bool:
        pushed.append((token, title, body))
        return True

    monkeypatch.setattr("app.api.chat.send_push", fake_push)
    parent = await _join(client, "parent")
    child = await _join(client, "child")
    await client.post(
        "/auth/fcm-token",
        headers=_auth(parent["tokens"]["access_token"]),
        json={"fcm_token": "parent-fcm-token"},
    )
    await client.post(
        "/auth/fcm-token",
        headers=_auth(child["tokens"]["access_token"]),
        json={"fcm_token": "child-fcm-token"},
    )
    sent = await client.post(
        "/chat/messages",
        headers=_auth(child["tokens"]["access_token"]),
        json={"body": "salom ota"},
    )
    assert sent.status_code == 200, sent.text
    assert pushed == [("parent-fcm-token", "Bola", "salom ota")]
    pushed.clear()
    from_parent = await client.post(
        "/chat/messages",
        headers=_auth(parent["tokens"]["access_token"]),
        json={"body": "qayerdasan"},
    )
    assert from_parent.status_code == 200, from_parent.text
    assert pushed == []


@pytest.mark.asyncio
async def test_child_media_pushes_parent_not_child(
    client: AsyncClient, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    pushed: list[str | None] = []

    async def fake_push(token: str | None, title: str, body: str, data: dict | None = None) -> bool:
        pushed.append(token)
        return True

    monkeypatch.setattr("app.api.chat.send_push", fake_push)
    parent = await _join(client, "parent")
    child = await _join(client, "child")
    await client.post(
        "/auth/fcm-token",
        headers=_auth(parent["tokens"]["access_token"]),
        json={"fcm_token": "parent-fcm-token"},
    )
    await client.post(
        "/auth/fcm-token",
        headers=_auth(child["tokens"]["access_token"]),
        json={"fcm_token": "child-fcm-token"},
    )
    photo = tmp_path / "hi.jpg"
    photo.write_bytes(b"\xff\xd8\xff fake-jpeg")
    sent = await client.post(
        "/chat/messages/media",
        headers=_auth(child["tokens"]["access_token"]),
        data={"kind": "photo", "caption": "rasm"},
        files={"file": ("hi.jpg", photo.read_bytes(), "image/jpeg")},
    )
    assert sent.status_code == 200, sent.text
    assert pushed == ["parent-fcm-token"]


@pytest.mark.asyncio
async def test_message_is_star_until_the_other_side_reads_it(client: AsyncClient) -> None:
    parent = await _join(client, "parent")
    child = await _join(client, "child")
    sent = await client.post(
        "/chat/messages",
        headers=_auth(parent["tokens"]["access_token"]),
        json={"body": "salom"},
    )
    assert sent.status_code == 200, sent.text
    assert sent.json()["read"] is False
    ack = await client.post("/chat/read", headers=_auth(child["tokens"]["access_token"]))
    assert ack.status_code == 200, ack.text
    assert sent.json()["id"] in ack.json()["ids"]
    listed = await client.get("/chat/messages", headers=_auth(parent["tokens"]["access_token"]))
    assert listed.json()[-1]["read"] is True
