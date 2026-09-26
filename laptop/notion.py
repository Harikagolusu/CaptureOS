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


def known_people(limit: int = 100) -> list[str]:
    """Distinct task owners already in the tasks DB, most recent first. Never raises."""
    try:
        data = _post(
            f"/databases/{NOTION_TASKS_DB}/query",
            {"page_size": limit, "sorts": [{"timestamp": "created_time", "direction": "descending"}]},
        )
    except Exception:
        return []
    seen: dict[str, None] = {}
    for page in data.get("results", []):
        owner = "".join(t["plain_text"] for t in page["properties"].get("Owner", {}).get("rich_text", [])).strip()
        if owner and owner.lower() != "unassigned":
            seen.setdefault(owner)
    return list(seen)


def create_task(title: str, owner: str, due: str | None, priority: str, source: str) -> str:
    props = {
        "Name": {"title": _text(title)},
        "Owner": {"rich_text": _text(owner)},
        "Priority": {"select": {"name": priority}},
        "Status": {"select": {"name": "To do"}},
        "Source": {"select": {"name": source}},
    }
    if due:
        props["Due"] = {"date": {"start": due}}
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
