"""Background processing of an uploaded meeting: audio -> transcript -> Claude -> DB rows (+ Notion)."""
import re
import traceback
from pathlib import Path

from sqlmodel import select

import notion
import voice
from brain import run_brain
from config import DRY_RUN
from db import Attendee, Meeting, Question, Segment, Task, User, session
from transcribe import transcribe

_LINE = re.compile(r"^\s*([^:\n]{1,40}):\s*(.+)$")


def _clean(name: str) -> str:
    return re.sub(r"\s*\(.*?\)\s*", "", name).strip()


def match_user(name: str, users: list[User]) -> User | None:
    """Name or nickname (case-insensitive, also first word) -> team member."""
    n = _clean(name).lower()
    if not n or n in ("unassigned", "meeting lead"):
        return None
    for u in users:
        if n in (x.lower() for x in u.names()):
            return u
    first = n.split()[0]
    for u in users:
        if first in (x.lower().split()[0] for x in u.names()):
            return u
    return None


def team_context(host: User, users: list[User]) -> str:
    members = "; ".join(
        f"{u.name}" + (f" (also called {', '.join(u.names()[1:])})" if len(u.names()) > 1 else "") + (" [admin]" if u.role == "admin" else "")
        for u in users
    )
    return (
        f"This meeting was recorded by {host.name}, who usually runs it. "
        f"Team members: {members}. Use each member's main name as owner even if a nickname was used; "
        "people who aren't team members are fine too."
    )


def _speaker_map(entries: list[str]) -> dict[str, str]:
    out = {}
    for e in entries:
        if "=" in e:
            label, name = e.split("=", 1)
            out[label.strip().lower()] = _clean(name)
    return out


def process_meeting(meeting_id: int) -> None:
    with session() as s:
        m = s.get(Meeting, meeting_id)
        users = list(s.exec(select(User).where(User.team_id == m.team_id)))
        host = s.get(User, m.host_id)
        try:
            m.status = "transcribing"
            s.add(m)
            s.commit()

            owner = voice.owner()
            if m.transcript:  # already transcribed (e.g. audio that arrived through the inbox)
                text = m.transcript
            else:
                names = sorted({n for u in users for n in u.names()} | set([] if DRY_RUN else notion.known_people()))
                text = transcribe(Path(m.audio_path), m.language or None, meeting=True, names=names, owner=owner)
                m.transcript = text
            m.status = "thinking"
            s.add(m)
            s.commit()

            hint = voice.speakers_hint(owner, team_context(host, users))
            result = run_brain(text, title=m.title or None, speakers_hint=hint)
            note = next((a for a in result["actions"] if a["tool"] == "create_note"), None)
            args = note["args"] if note else {}

            m.summary = args.get("summary") or result["summary"]
            m.title = m.title or args.get("title", "Meeting")
            m.notion_url = (note or {}).get("url", "")

            labels = _speaker_map(args.get("speaker_map", []))
            updates = {u.split(":", 1)[0].strip().lower(): u.split(":", 1)[1].strip() for u in result.get("person_updates", []) if ":" in u}

            for i, line in enumerate(l for l in text.splitlines() if l.strip()):
                hit = _LINE.match(line)
                speaker, said = (hit.group(1).strip(), hit.group(2).strip()) if hit else ("", line.strip())
                name = labels.get(speaker.lower(), speaker if not speaker.lower().startswith("speaker") else "")
                user = match_user(name, users) if name else None
                s.add(Segment(meeting_id=m.id, idx=i, speaker=speaker, name=user.name if user else name, user_id=user and user.id, text=said))

            label_of = {v.lower(): k for k, v in labels.items()}
            for name in result.get("attendees", []):
                user = match_user(name, users)
                s.add(
                    Attendee(
                        meeting_id=m.id,
                        user_id=user and user.id,
                        name=user.name if user else _clean(name),
                        speaker_label=label_of.get(_clean(name).lower(), ""),
                        update=updates.get(_clean(name).lower(), ""),
                    )
                )

            for a in result["actions"]:
                if a["tool"] == "create_task":
                    t = a["args"]
                    user = match_user(t["owner"], users)
                    s.add(
                        Task(
                            team_id=m.team_id,
                            meeting_id=m.id,
                            owner_name=user.name if user else t["owner"],
                            owner_user_id=user and user.id,
                            title=t["title"],
                            due=t.get("due"),
                            priority=t.get("priority", "medium"),
                            notion_url=a.get("url", ""),
                        )
                    )
                elif a["tool"] == "ask_user":
                    s.add(Question(meeting_id=m.id, text=a["args"]["question"]))

            m.status = "done"
            s.add(m)
            s.commit()
        except Exception as e:
            traceback.print_exc()
            s.rollback()
            m = s.get(Meeting, meeting_id)
            m.status, m.error = "error", str(e)[:500]
            s.add(m)
            s.commit()
