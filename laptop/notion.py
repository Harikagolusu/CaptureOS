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


def create_note(title: str, summary: str, decisions: list[str], transcript: str) -> str:
    blocks = [
        {"object": "block", "type": "heading_2", "heading_2": {"rich_text": _text("Summary")}},
        {"object": "block", "type": "paragraph", "paragraph": {"rich_text": _text(summary)}},
        {"object": "block", "type": "heading_2", "heading_2": {"rich_text": _text("Decisions")}},
        *[
            {"object": "block", "type": "bulleted_list_item", "bulleted_list_item": {"rich_text": _text(d)}}
            for d in decisions
        ],
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
