import asyncio
from collections import defaultdict

from fastapi import WebSocket


class ConnectionManager:
    def __init__(self) -> None:
        self._rooms: dict[int, set[WebSocket]] = defaultdict(set)
        self._lock = asyncio.Lock()

    async def connect(self, family_id: int, websocket: WebSocket) -> None:
        await websocket.accept()
        async with self._lock:
            self._rooms[family_id].add(websocket)

    async def disconnect(self, family_id: int, websocket: WebSocket) -> None:
        async with self._lock:
            self._rooms[family_id].discard(websocket)

    async def broadcast(self, family_id: int, message: dict) -> None:
        async with self._lock:
            sockets = list(self._rooms.get(family_id, set()))
        dead: list[WebSocket] = []
        for ws in sockets:
            try:
                await ws.send_json(message)
            except Exception:
                dead.append(ws)
        for ws in dead:
            await self.disconnect(family_id, ws)


chat_manager = ConnectionManager()
