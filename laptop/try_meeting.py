"""Run a recorded meeting through the full pipeline and print what happened.

    .venv/Scripts/python try_meeting.py                 # newest recording/video (Sound Recordings, Camera Roll)
    .venv/Scripts/python try_meeting.py path/to/a.m4a [te|hi|en]   # audio or video file
"""
import sys
import time
from pathlib import Path

from brain import run_brain
from transcribe import VIDEO, transcribe

MEDIA = {".m4a", ".mp3", ".wav", ".aac", ".ogg", ".flac"} | VIDEO


def newest_media() -> Path:
    home = Path.home()
    dirs = [
        home / "Documents" / "Sound Recordings",
        home / "Pictures" / "Camera Roll",
        home / "OneDrive" / "Documents" / "Sound Recordings",
        home / "OneDrive" / "Pictures" / "Camera Roll",
    ]
    files = [f for d in dirs if d.exists() for f in d.iterdir() if f.suffix.lower() in MEDIA]
    if not files:
        sys.exit("No recordings found in Sound Recordings or Camera Roll. Pass a file path instead.")
    return max(files, key=lambda f: f.stat().st_mtime)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else newest_media()
    language = sys.argv[2] if len(sys.argv) > 2 else None
    print(f"Audio: {path.name}\n")

    t = time.time()
    text = transcribe(path, language, meeting=True)
    print(f"--- Transcript ({time.time() - t:.1f}s)\n{text}\n")

    t = time.time()
    result = run_brain(text, title=path.stem)
    print(f"--- Claude ({time.time() - t:.1f}s): {result['summary']}\n")
    for a in result["actions"]:
        args = a["args"]
        if a["tool"] == "create_task":
            line = f"TASK  {args['title']} -> {args['owner']} ({args['priority']}, due {args.get('due') or '-'})"
        elif a["tool"] == "create_note":
            line = f"NOTE  {args['title']}"
        else:
            line = f"ASK   {args['question']}"
        print(f"[{a['status']}] {line}" + (f"\n        {a['url']}" if a.get("url") else "") + (f"\n        ERROR {a['error']}" if a.get("error") else ""))


if __name__ == "__main__":
    main()
