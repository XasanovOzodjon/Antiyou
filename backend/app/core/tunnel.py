from __future__ import annotations

import json
import os
import shutil
import subprocess
import time
import urllib.request
from pathlib import Path

_proc: subprocess.Popen[bytes] | None = None


def _public_url(expected_port: int | None = None) -> str | None:
    try:
        with urllib.request.urlopen("http://127.0.0.1:4040/api/tunnels", timeout=1) as resp:
            data = json.load(resp)
        tunnels = data.get("tunnels") or []
        for t in tunnels:
            addr = str((t.get("config") or {}).get("addr") or "")
            if expected_port is not None and not addr.endswith(f":{expected_port}"):
                continue
            url = t.get("public_url") or ""
            if str(url).startswith("https://"):
                return str(url)
        if expected_port is None and tunnels and tunnels[0].get("public_url"):
            return str(tunnels[0]["public_url"])
    except Exception:
        return None
    return None


def _save(url: str) -> None:
    root = Path(__file__).resolve().parents[3]
    scratch = root / ".scratch"
    scratch.mkdir(parents=True, exist_ok=True)
    (scratch / "api-base-url.txt").write_text(url + "\n", encoding="utf-8")


def start_ngrok(port: int = 8000) -> str | None:
    global _proc
    if os.environ.get("START_NGROK", "1").lower() in {"0", "false", "no"}:
        return None
    existing = _public_url(port)
    if existing:
        _save(existing)
        print(f"ngrok: {existing}", flush=True)
        return existing
    binary = shutil.which("ngrok")
    if not binary:
        print("ngrok topilmadi PATH da — tunnel ochilmadi", flush=True)
        return None
    _proc = subprocess.Popen(
        [binary, "http", str(port), "--log=stdout"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    for _ in range(50):
        url = _public_url(port)
        if url:
            _save(url)
            print(f"ngrok: {url}", flush=True)
            return url
        if _proc.poll() is not None:
            break
        time.sleep(0.2)
    print("ngrok URL chiqmadi", flush=True)
    return None


def stop_ngrok() -> None:
    global _proc
    if _proc is None:
        return
    if _proc.poll() is None:
        _proc.terminate()
        try:
            _proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
            _proc.kill()
    _proc = None
