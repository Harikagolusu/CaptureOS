"""Smart inbox: a productive Google Photos. Photos and audio from the phone (app upload or Office Kit
transfer) are sorted by Claude into self-named albums; only items that matter for the user's type
(employee / student / manager / field worker) get their text read and to-dos extracted.

Audio: transcribed, then classified. Meetings go through the full meeting pipeline; lectures become
study notes; voice memos become to-dos; anything personal is just filed.
"""
import hashlib
import traceback
from datetime import date
from pathlib import Path

from fastapi import APIRouter, BackgroundTasks, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from sqlmodel import func, select

import notion
from api import current_user
from brain import CLAUDE_MODEL, EXTRA, client
from config import DATA, DRY_RUN
from db import InboxItem, Meeting, Task, User, session
from field import _jpeg_b64
from pipeline import match_user, process_meeting
from transcribe import VIDEO, transcribe

router = APIRouter(prefix="/inbox")
INBOX_DIR = DATA / "inbox"
IMAGE = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
AUDIO = {".m4a", ".mp3", ".wav", ".aac", ".ogg", ".opus", ".flac", ".amr", ".3ga"} | VIDEO

PROFILES = {
    "employee": "whiteboards, meeting boards, documents, invoices/receipts, visiting cards, screenshots of tasks or chats",
    "student": "lecture slides, class notes, textbook pages, exam or assignment notices, timetables, notice boards",
    "manager": "whiteboards, team boards and plans, documents, invoices, screenshots of tasks; items may name teammates",
    "field": "site or equipment problems, stock and inventory, delivery proofs, meter readings, signed documents",
}

_TODOS = {
    "type": "array",
    "description": (
        "Only action items that are written in the item or explicitly asked for (one task each). "
        "A photo of a problem with no written instruction is reference material: no task. Never add follow-ups."
    ),
    "items": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "title": {"type": "string", "description": "Short imperative English task."},
            "owner": {"type": "string", "description": "Person named for this item, or empty (then it's the user's)."},
            "due": {"type": "string", "description": "YYYY-MM-DD if a date or weekday is given, else empty."},
            "priority": {"type": "string", "enum": ["high", "medium", "low"]},
        },
        "required": ["title", "owner", "due", "priority"],
    },
}

PHOTO_TOOL = {
    "name": "file_photo",
    "description": "File one phone photo into a smart album and extract what matters.",
    "input_schema": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "album": {"type": "string", "description": "Short album name (2-5 words). Reuse an existing album when it fits."},
            "category": {"type": "string", "description": "One or two words, e.g. whiteboard, lecture slide, receipt, selfie, food, travel."},
            "people": {"type": "boolean", "description": "True if people are the main subject."},
            "actionable": {"type": "boolean", "description": "True only if it matches the user's important criteria."},
            "title": {"type": "string", "description": "3-8 word English title."},
            "text": {"type": "string", "description": "If actionable: all readable text, verbatim, line breaks kept. Otherwise empty."},
            "summary": {"type": "string", "description": "One short English sentence."},
            "todos": _TODOS,
        },
        "required": ["album", "category", "people", "actionable", "title", "text", "summary", "todos"],
    },
}

AUDIO_TOOL = {
    "name": "file_audio",
    "description": "Classify one phone recording and extract what matters.",
    "input_schema": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "kind": {"type": "string", "enum": ["meeting", "lecture", "voice_memo", "call", "personal", "other"],
                     "description": "meeting = several people discussing work; voice_memo = one person noting things for themselves."},
            "album": {"type": "string", "description": "Short album name, e.g. 'Voice memos', 'DBMS lectures'. Reuse existing when it fits."},
            "title": {"type": "string", "description": "3-8 word English title."},
            "summary": {"type": "string", "description": "For lectures: key points as short lines. Otherwise one or two sentences."},
            "todos": _TODOS,
        },
        "required": ["kind", "album", "title", "summary", "todos"],
    },
}


def _call(tool: dict, content: list[dict]) -> dict:
    msg = client.beta.messages.create(
        model=CLAUDE_MODEL, max_tokens=6000, tools=[tool], messages=[{"role": "user", "content": content}], **EXTRA
    )
    block = next((b for b in msg.content if b.type == "tool_use" and b.name == tool["name"]), None)
    if block is None:
        raise RuntimeError(f"Claude didn't call {tool['name']} (stop_reason={msg.stop_reason})")
    return block.input


def _context(user: User, albums: list[str], taken_at: str) -> str:
    return (
        f"User: {user.name}, profile '{user.profile}'. Important for them: {PROFILES.get(user.profile, PROFILES['employee'])}.\n"
        f"Today: {date.today():%A %Y-%m-%d} (a weekday name means its next occurrence). Taken: {taken_at or 'unknown'}.\n"
        f"Existing albums: {', '.join(albums) or 'none yet'}."
    )


def _add_tasks(s, item: InboxItem, todos: list[dict], owner_default: User, users: list[User]) -> None:
    ids = []
    for todo in todos:
        user = match_user(todo["owner"], users) if todo["owner"] else owner_default
        owner = user.name if user else todo["owner"]
        url = "" if DRY_RUN else notion.create_task(
            title=todo["title"], owner=owner, due=todo["due"] or None, priority=todo["priority"], source="cloud"
        )
        t = Task(team_id=item.team_id, owner_name=owner, owner_user_id=user and user.id, title=todo["title"],
                 due=todo["due"] or None, priority=todo["priority"], notion_url=url)
        s.add(t)
        s.flush()
        ids.append(str(t.id))
    item.task_ids = ",".join(ids)


def process_item(item_id: int) -> None:
    with session() as s:
        item = s.get(InboxItem, item_id)
        user = s.get(User, item.user_id)
        users = list(s.exec(select(User).where(User.team_id == item.team_id)))
        albums = list(s.exec(select(InboxItem.album).where(InboxItem.user_id == user.id, InboxItem.album != "").distinct()))
        meeting_id = None
        try:
            ctx = _context(user, albums, item.taken_at)
            if item.kind == "photo":
                r = _call(PHOTO_TOOL, [
                    {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": _jpeg_b64(Path(item.path))}},
                    {"type": "text", "text": ctx + "\n\nFile this photo. Call file_photo once."},
                ])
                item.album, item.category, item.people, item.actionable = r["album"], r["category"], r["people"], r["actionable"]
                item.title, item.text, item.summary = r["title"], r["text"], r["summary"]
                if r["actionable"]:
                    _add_tasks(s, item, r["todos"], user, users)
            else:
                item.text = transcribe(Path(item.path), meeting=True, names=[n for u in users for n in u.names()])
                r = _call(AUDIO_TOOL, [{"type": "text", "text": ctx + f"\n\nTranscript:\n{item.text}\n\nCall file_audio once."}])
                item.album, item.category, item.title, item.summary = r["album"], r["kind"], r["title"], r["summary"]
                item.actionable = r["kind"] in ("meeting", "lecture", "voice_memo")
                if r["kind"] == "meeting":
                    m = Meeting(team_id=item.team_id, host_id=user.id, title=r["title"], audio_path=item.path, transcript=item.text)
                    s.add(m)
                    s.flush()
                    item.meeting_id = meeting_id = m.id
                elif r["kind"] == "voice_memo":
                    _add_tasks(s, item, r["todos"], user, users)
            item.status = "done"
        except Exception as e:
            traceback.print_exc()
            item.status, item.error = "error", str(e)[:500]
        s.add(item)
        s.commit()
    if meeting_id:
        process_meeting(meeting_id)  # full meeting flow: speakers, tasks, Notion, Slack


def add_file(user: User, name: str, data: bytes, source: str, client_id: str = "", taken_at: str = "") -> int | None:
    """Store one file in the inbox. Returns the new item id, or None if skipped (duplicate / unsupported)."""
    suffix = Path(name).suffix.lower()
    kind = "photo" if suffix in IMAGE else "audio" if suffix in AUDIO else None
    if not kind:
        return None
    digest = hashlib.sha1(data).hexdigest()
    with session() as s:
        dup = select(InboxItem).where(InboxItem.user_id == user.id, InboxItem.content_hash == digest)
        if client_id:
            dup = select(InboxItem).where(
                InboxItem.user_id == user.id, (InboxItem.client_id == client_id) | (InboxItem.content_hash == digest)
            )
        if s.exec(dup).first():
            return None
        item = InboxItem(team_id=user.team_id, user_id=user.id, kind=kind, source=source, client_id=client_id,
                         content_hash=digest, taken_at=taken_at)
        s.add(item)
        s.commit()
        s.refresh(item)
        INBOX_DIR.mkdir(parents=True, exist_ok=True)
        path = INBOX_DIR / f"{item.id}{suffix}"
        path.write_bytes(data)
        item.path = str(path)
        s.add(item)
        s.commit()
        return item.id


def _out(i: InboxItem) -> dict:
    out = i.model_dump(exclude={"path", "team_id", "task_ids"})
    out["task_ids"] = [int(x) for x in i.task_ids.split(",") if x]
    out["file_url"] = f"/inbox/{i.id}/file"
    return out


@router.post("")
async def upload(
    background: BackgroundTasks,
    files: list[UploadFile] = File(...),
    client_ids: str = Form(""),  # comma-separated, same order as files (gallery ids or hashes)
    taken_at: str = Form(""),  # comma-separated ISO timestamps, same order
    user: User = Depends(current_user),
):
    """Photos and/or audio from the phone's gallery or recorder. Re-sent client_ids are skipped."""
    ids = [x.strip() for x in client_ids.split(",")] if client_ids else []
    times = [x.strip() for x in taken_at.split(",")] if taken_at else []
    accepted, skipped = [], []
    for n, f in enumerate(files):
        cid = ids[n] if n < len(ids) else ""
        item_id = add_file(user, f.filename or "", await f.read(), "app", cid, times[n] if n < len(times) else "")
        if item_id:
            accepted.append(item_id)
            background.add_task(process_item, item_id)
        else:
            skipped.append(cid or f.filename)
    return {"accepted": accepted, "skipped": skipped}


@router.get("")
def list_items(kind: str = "", album: str = "", actionable: bool | None = None, user: User = Depends(current_user)):
    with session() as s:
        q = select(InboxItem).where(InboxItem.user_id == user.id)
        if kind:
            q = q.where(InboxItem.kind == kind)
        if album:
            q = q.where(InboxItem.album == album)
        if actionable is not None:
            q = q.where(InboxItem.actionable == actionable)
        return [_out(i) for i in s.exec(q.order_by(InboxItem.created_at.desc()))]


@router.get("/albums")
def albums(user: User = Depends(current_user)):
    """Smart albums with counts and the newest item id (for a cover thumbnail)."""
    with session() as s:
        rows = s.exec(
            select(InboxItem.album, func.count(InboxItem.id), func.max(InboxItem.id))
            .where(InboxItem.user_id == user.id, InboxItem.album != "")
            .group_by(InboxItem.album)
        ).all()
        return [{"album": a, "count": c, "cover_url": f"/inbox/{cover}/file"} for a, c, cover in rows]


@router.get("/{item_id}/file")
def item_file(item_id: int, user: User = Depends(current_user)):
    with session() as s:
        i = s.get(InboxItem, item_id)
        if not i or i.user_id != user.id:
            raise HTTPException(404, "Not found")
        return FileResponse(i.path)
