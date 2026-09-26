"""Thin Notion API client: one tasks database row, one notes page."""
import httpx

from config import NOTION_NOTES_PAGE, NOTION_TASKS_DB, NOTION_TOKEN

API = "https://api.notion.com/v1"
HEADERS = {
    "Authorization": f"Bearer {NOTION_TOKEN}",
    "Notion-Version": "2022-06-28",
    "Content-Type": "application/json",
}


def _text(s: str) -> list[dict]:
    return [{"type": "text", "text": {"content": s[:2000]}}]


def _post(path: str, body: dict) -> dict:
    r = httpx.post(f"{API}{path}", headers=HEADERS, json=body, timeout=20)
    if r.status_code >= 400:
        raise RuntimeError(f"Notion {r.status_code}: {r.text[:300]}")
    return r.json()


def _recent_tasks(limit: int = 100) -> list[dict]:
    data = _post(
        f"/databases/{NOTION_TASKS_DB}/query",
        {"page_size": limit, "sorts": [{"timestamp": "created_time", "direction": "descending"}]},
    )
    return data.get("results", [])


def _owner(page: dict) -> str:
    return "".join(t["plain_text"] for t in page["properties"].get("Owner", {}).get("rich_text", [])).strip()


def known_people(limit: int = 100) -> list[str]:
    """Distinct task owners already in the tasks DB, most recent first. Never raises."""
    try:
        pages = _recent_tasks(limit)
    except Exception:
        return []
    seen: dict[str, None] = {}
    for page in pages:
        owner = _owner(page)
        if owner and owner.lower() != "unassigned":
            seen.setdefault(owner)
    return list(seen)


def learned_assignees(limit: int = 100) -> dict[str, str]:
    """Owner name (lowercase) -> Notion user id, learned from tasks where someone set Assignee.

    Personal access tokens can't list workspace users, so a teammate claims one of their tasks
    once (sets Assignee = themselves) and every later task for that name is assigned automatically.
    """
    try:
        pages = _recent_tasks(limit)
    except Exception:
        return {}
    learned: dict[str, str] = {}
    for page in pages:
        people = page["properties"].get("Assignee", {}).get("people", [])
        owner = _owner(page).lower()
        if len(people) == 1 and owner and owner != "unassigned":
            learned.setdefault(owner, people[0]["id"])
    return learned


def ensure_assignee_column() -> None:
    """Add the Assignee (Notion person) column to the tasks DB if it's missing."""
    r = httpx.patch(
        f"{API}/databases/{NOTION_TASKS_DB}", headers=HEADERS, json={"properties": {"Assignee": {"people": {}}}}, timeout=20
    )
    if r.status_code >= 400:
        raise RuntimeError(f"Notion {r.status_code}: {r.text[:300]}")


def create_task(
    title: str, owner: str, due: str | None, priority: str, source: str, assignee_id: str | None = None
) -> str:
    props = {
        "Name": {"title": _text(title)},
        "Owner": {"rich_text": _text(owner)},
        "Priority": {"select": {"name": priority}},
        "Status": {"select": {"name": "To do"}},
        "Source": {"select": {"name": source}},
    }
    if due:
        props["Due"] = {"date": {"start": due}}
    if assignee_id:
        props["Assignee"] = {"people": [{"id": assignee_id}]}
    page = _post("/pages", {"parent": {"database_id": NOTION_TASKS_DB}, "properties": props})
    return page["url"]


def _bullets(items: list[str]) -> list[dict]:
    return [
        {"object": "block", "type": "bulleted_list_item", "bulleted_list_item": {"rich_text": _text(i)}}
        for i in items
    ]


def create_note(
    title: str,
    summary: str,
    decisions: list[str],
    transcript: str,
    attendees: list[str] | None = None,
    speaker_map: list[str] | None = None,
    person_updates: list[str] | None = None,
) -> str:
    blocks = [
        {"object": "block", "type": "paragraph", "paragraph": {"rich_text": _text("Attendees: " + ", ".join(attendees or ["unknown"]))}},
        *_bullets(speaker_map or []),
        {"object": "block", "type": "heading_2", "heading_2": {"rich_text": _text("Summary")}},
        {"object": "block", "type": "paragraph", "paragraph": {"rich_text": _text(summary)}},
        *(
            [{"object": "block", "type": "heading_2", "heading_2": {"rich_text": _text("Updates by person")}}, *_bullets(person_updates)]
            if person_updates
            else []
        ),
        {"object": "block", "type": "heading_2", "heading_2": {"rich_text": _text("Decisions")}},
        *_bullets(decisions),
        {"object": "block", "type": "heading_2", "heading_2": {"rich_text": _text("Transcript")}},
        # Notion caps a rich_text item at 2000 chars; split long transcripts into paragraphs.
        *[
            {"object": "block", "type": "paragraph", "paragraph": {"rich_text": _text(transcript[i : i + 2000])}}
            for i in range(0, min(len(transcript), 80_000), 2000)
        ],
    ]
    parent = {"page_id": NOTION_NOTES_PAGE} if NOTION_NOTES_PAGE else {"database_id": NOTION_TASKS_DB}
    props = {"title": {"title": _text(title)}} if NOTION_NOTES_PAGE else {"Name": {"title": _text(title)}}
    page = _post("/pages", {"parent": parent, "properties": props, "children": blocks[:100]})
    return page["url"]


_STATUS = {"todo": "To do", "doing": "Doing", "done": "Done"}


def set_status(page_url: str, status: str) -> None:
    """Mirror an app status change onto the Notion task page (page id = last 32 hex chars of its URL)."""
    page_id = page_url.rstrip("/").split("-")[-1].split("/")[-1][-32:]
    r = httpx.patch(
        f"{API}/pages/{page_id}",
        headers=HEADERS,
        json={"properties": {"Status": {"select": {"name": _STATUS[status]}}}},
        timeout=20,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"Notion {r.status_code}: {r.text[:300]}")
