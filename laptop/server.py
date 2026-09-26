"""CaptureOS laptop bridge: audio -> transcript -> Claude brain -> Notion."""
import json
import socket
import tempfile
from datetime import datetime
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel

import api
import db
import notion
import slack
import vivo_notes
import voice
from brain import run_brain
from config import CLAUDE_MODEL, DATA, DRY_RUN, TRANSCRIBE_PROVIDER
from transcribe import transcribe

app = FastAPI(title="CaptureOS Bridge")
db.init()
app.include_router(api.router)


def lan_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def save(kind: str, payload: dict) -> str:
    run_id = datetime.now().strftime("%Y%m%d-%H%M%S")
    (DATA / f"{kind}-{run_id}.json").write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    return run_id


async def transcribe_upload(audio: UploadFile, language: str | None, meeting: bool = False, speakers: int | None = None) -> str:
    suffix = Path(audio.filename or "audio.m4a").suffix or ".m4a"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(await audio.read())
        path = Path(tmp.name)
    try:
        names = [] if DRY_RUN else await run_in_threadpool(notion.known_people)
        owner = voice.owner() if meeting else None
        return await run_in_threadpool(transcribe, path, language or None, meeting, speakers, names, owner)
    except Exception as e:
        raise HTTPException(502, f"Transcription failed: {e}")
    finally:
        path.unlink(missing_ok=True)


class ProcessRequest(BaseModel):
    transcript: str
    title: str | None = None


@app.get("/health")
def health():
    return {"ok": True, "ip": lan_ip(), "claude": CLAUDE_MODEL, "transcribe": TRANSCRIBE_PROVIDER, "notion_dry_run": DRY_RUN,
            "vivo_notes": {"token": vivo_notes.enabled(), "service": vivo_notes.health()}, "slack": slack.enabled()}


@app.post("/transcribe")
async def transcribe_endpoint(audio: UploadFile = File(...), language: str = Form("")):
    text = await transcribe_upload(audio, language)
    return {"transcript": text}


@app.post("/process")
async def process(req: ProcessRequest):
    result = await run_in_threadpool(run_brain, req.transcript, req.title)
    result["id"] = save("process", {"request": req.model_dump(), "result": result})
    return result


@app.post("/meeting")
async def meeting(
    audio: UploadFile = File(...),
    language: str = Form(""),
    title: str = Form(""),
    speakers: int | None = Form(None),
    who: str = Form(""),
):
    """Full pipeline in one call: recorded meeting audio in, transcript + actions out."""
    text = await transcribe_upload(audio, language, meeting=True, speakers=speakers)
    hint = voice.speakers_hint(voice.owner(), who or None)
    result = await run_in_threadpool(run_brain, text, title or None, "cloud", hint)
    result["transcript"] = text
    result["id"] = save("meeting", result)
    return result



@app.post("/voice/enroll")
async def voice_enroll(audio: UploadFile = File(...), name: str = Form(...), role: str = Form("")):
    """One-time setup: the app user records ~10 s ("Hi, I'm Tej, I'm the manager")."""
    info = voice.save_owner(await audio.read(), Path(audio.filename or "").suffix, name, role)
    return {"enrolled": True, "name": info["name"], "role": info["role"]}


@app.get("/voice")
def voice_status():
    info = voice.owner()
    return {"enrolled": bool(info), "name": info and info["name"], "role": info and info["role"]}

if __name__ == "__main__":
    import uvicorn

    print(f"\n  CaptureOS bridge -> http://{lan_ip()}:8000  (phone uses this IP)\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
