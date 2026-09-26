"""Office Kit watcher: files sent from the phone with vivo Office Kit's file transfer land in
~/Downloads/vivo Office Kit on the laptop. New photos / recordings there go into the smart inbox
automatically, as the configured user (INBOX_USER in .env, default: the team admin).
"""
import os
import threading
import time
from pathlib import Path

from sqlmodel import select

from db import User, session
from inbox import AUDIO, IMAGE, add_file, process_item

WATCH_DIR = Path(os.environ.get("OFFICEKIT_DIR", str(Path.home() / "Downloads" / "vivo Office Kit")))
POLL_SECONDS = 4


def _owner() -> User | None:
    name = os.environ.get("INBOX_USER", "").strip().lower()
    with session() as s:
        users = s.exec(select(User)).all()
    if name:
        return next((u for u in users if name in (n.lower() for n in u.names())), None)
    return next((u for u in users if u.role == "admin"), None)


def _loop(started: float) -> None:
    seen_size: dict[Path, int] = {}
    while True:
        try:
            owner = _owner()
            for f in WATCH_DIR.rglob("*") if owner and WATCH_DIR.exists() else []:
                if not f.is_file() or f.suffix.lower() not in IMAGE | AUDIO:
                    continue
                st = f.stat()
                if st.st_mtime < started:  # only files that arrive while the server runs
                    continue
                if seen_size.get(f) != st.st_size:  # wait until the transfer has finished writing
                    seen_size[f] = st.st_size
                    continue
                item_id = add_file(owner, f.name, f.read_bytes(), "officekit", f"officekit:{f.name}:{st.st_size}:{int(st.st_mtime)}")
                seen_size[f] = -1  # handled
                if item_id:
                    print(f"[officekit] {f.name} -> inbox item {item_id}")
                    threading.Thread(target=process_item, args=(item_id,), daemon=True).start()
        except Exception as e:  # never let the watcher die
            print(f"[officekit] {e}")
        time.sleep(POLL_SECONDS)


def start() -> bool:
    if os.environ.get("OFFICEKIT_WATCH", "1") == "0":
        return False
    threading.Thread(target=_loop, args=(time.time(),), daemon=True).start()
    print(f"[officekit] watching {WATCH_DIR}")
    return True
