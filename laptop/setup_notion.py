"""One-time Notion setup: creates the tasks database and the meeting-notes page under a page you
shared with your integration, then writes their IDs into laptop/.env.

    .venv/Scripts/python setup_notion.py <notion page URL or id>
"""
import re
import sys

import httpx

import config
from notion import API, HEADERS


def page_id(url_or_id: str) -> str:
    # Notion URLs end in "<title-slug>-<32 hex id>"; dashes may also appear inside the id.
    runs = [r for r in re.findall(r"[0-9a-f]+", url_or_id.split("?")[0].replace("-", "").lower()) if len(r) >= 32]
    if not runs:
        sys.exit(f"Couldn't find a Notion page id in: {url_or_id}")
    return runs[-1][-32:]


def post(path: str, body: dict) -> dict:
    r = httpx.post(f"{API}{path}", headers=HEADERS, json=body, timeout=20)
    if r.status_code >= 400:
        sys.exit(f"Notion {r.status_code}: {r.text[:400]}\n(Is the page shared with your integration? ••• → Connections)")
    return r.json()


def options(*names: str) -> dict:
    return {"select": {"options": [{"name": n} for n in names]}}


def set_env(key: str, value: str) -> None:
    env = config.BASE / ".env"
    lines = env.read_text(encoding="utf-8").splitlines() if env.exists() else []
    lines = [l for l in lines if not l.startswith(f"{key}=")] + [f"{key}={value}"]
    env.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    if not config.NOTION_TOKEN:
        sys.exit("Put NOTION_TOKEN=... in laptop/.env first.")
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    parent = {"type": "page_id", "page_id": page_id(sys.argv[1])}

    db = post("/databases", {
        "parent": parent,
        "title": [{"type": "text", "text": {"content": "CaptureOS Tasks"}}],
        "properties": {
            "Name": {"title": {}},
            "Owner": {"rich_text": {}},
            "Assignee": {"people": {}},
            "Due": {"date": {}},
            "Priority": options("high", "medium", "low"),
            "Status": options("To do", "Doing", "Done"),
            "Source": options("cloud", "on-device"),
        },
    })
    notes = post("/pages", {
        "parent": parent,
        "properties": {"title": {"title": [{"type": "text", "text": {"content": "CaptureOS Meeting Notes"}}]}},
    })
    set_env("NOTION_TASKS_DB", db["id"])
    set_env("NOTION_NOTES_PAGE", notes["id"])
    print(f"Tasks DB:    {db['url']}\nNotes page:  {notes['url']}\nSaved both IDs to laptop/.env")


if __name__ == "__main__":
    main()
