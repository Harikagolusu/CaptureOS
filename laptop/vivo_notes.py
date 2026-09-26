"""vivo Office Kit Notes: local third-party HTTP API (127.0.0.1, token from Office Kit).

Notes created here sync to the iQOO phone's Notes app through Office Kit.
"""
import html

import httpx

from config import VIVO_NOTES_PORT, VIVO_NOTES_TOKEN

BASE = f"http://127.0.0.1:{VIVO_NOTES_PORT}"


def enabled() -> bool:
    return bool(VIVO_NOTES_TOKEN)


def health() -> bool:
    try:
        return httpx.get(f"{BASE}/health", timeout=3).status_code == 200
    except httpx.HTTPError:
        return False


def meeting_html(
    summary: str,
    decisions: list[str],
    tasks: list[dict],
    questions: list[str],
    transcript: str,
    attendees: list[str] | None = None,
) -> str:
    e = html.escape
    parts = [f"<p><b>Attendees:</b> {e(', '.join(attendees or ['unknown']))}</p>", f"<p><b>Summary</b><br>{e(summary)}</p>"]
    if tasks:
        rows = []
        for t in tasks:
            due = f" · due {e(t['due'])}" if t.get("due") else ""
            rows.append(f"☐ {e(t['title'])} → <b>{e(t['owner'])}</b> ({e(t['priority'])}{due})")
        parts.append("<p><b>Action items</b><br>" + "<br>".join(rows) + "</p>")
    if decisions:
        parts.append("<p><b>Decisions</b><br>" + "<br>".join(f"• {e(d)}" for d in decisions) + "</p>")
    if questions:
        parts.append("<p><b>Open questions</b><br>" + "<br>".join(f"? {e(q)}" for q in questions) + "</p>")
    parts.append("<p><b>Transcript</b><br>" + e(transcript).replace("\n", "<br>") + "</p>")
    parts.append("<p><i>Captured by CaptureOS</i></p>")
    return "".join(parts)


def create_note(title: str, content_html: str) -> str:
    r = httpx.post(
        f"{BASE}/third-party/notes",
        headers={"Authorization": f"Bearer {VIVO_NOTES_TOKEN}"},
        json={"title": title, "content": content_html},
        timeout=30,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"vivo Notes {r.status_code}: {r.text[:300]}")
    return r.json()["data"]["id"]
