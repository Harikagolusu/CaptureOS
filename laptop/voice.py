"""The app user's voice intro ("Hi, I'm Tej, I'm the manager").

Only the person using the app enrolls. For every meeting, their intro clip is placed in front of the
recording, so the transcriber hears that voice introduce itself and labels it with their name for the
rest of the meeting. Everyone else is identified from context (names used in the conversation).

    .venv/Scripts/python voice.py enroll <audio file> "Tej" "manager"
"""
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

import imageio_ffmpeg

from config import DATA

DIR = DATA / "voice"


def save_owner(audio: bytes, suffix: str, name: str, role: str = "") -> dict:
    DIR.mkdir(parents=True, exist_ok=True)
    for old in DIR.glob("owner.*"):
        old.unlink()
    path = DIR / f"owner{suffix or '.m4a'}"
    path.write_bytes(audio)
    info = {"name": name.strip(), "role": role.strip(), "file": path.name}
    (DIR / "owner.json").write_text(json.dumps(info), encoding="utf-8")
    return info


def owner() -> dict | None:
    meta = DIR / "owner.json"
    if not meta.exists():
        return None
    info = json.loads(meta.read_text(encoding="utf-8"))
    path = DIR / info["file"]
    return {**info, "path": path} if path.exists() else None


def with_intro(meeting: Path, intro: Path) -> Path:
    """intro + 1 s silence + meeting, as one mono 16 kHz mp3 in a temp file (caller deletes it)."""
    fd, name = tempfile.mkstemp(suffix=".mp3")
    os.close(fd)  # Windows keeps the file locked while this handle is open
    out = Path(name)
    subprocess.run(
        [
            imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-i", str(intro), "-i", str(meeting),
            "-filter_complex",
            "[0:a]aresample=16000,aformat=channel_layouts=mono,apad=pad_dur=1[a0];"
            "[1:a]aresample=16000,aformat=channel_layouts=mono[a1];[a0][a1]concat=n=2:v=0:a=1[a]",
            "-map", "[a]", "-b:a", "64k", str(out),
        ],
        check=True,
        capture_output=True,
    )
    return out


def speakers_hint(info: dict | None, extra: str | None = None) -> str | None:
    parts = []
    if info:
        role = f", {info['role']}" if info.get("role") else ""
        parts.append(
            f"The app user is {info['name']}{role}. The transcript opens with their short voice "
            f"introduction; that voice is {info['name']} throughout. The intro itself is not meeting content."
        )
    if extra:
        parts.append(extra)
    return " ".join(parts) or None


if __name__ == "__main__":
    if len(sys.argv) < 4 or sys.argv[1] != "enroll":
        sys.exit(__doc__)
    src = Path(sys.argv[2])
    print(save_owner(src.read_bytes(), src.suffix, sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else ""))
