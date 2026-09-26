"""Loads laptop/.env into the environment and exposes settings."""
import os
from pathlib import Path

BASE = Path(__file__).parent
DATA = BASE / "data"
DATA.mkdir(exist_ok=True)

_env = BASE / ".env"
if _env.exists():
    for line in _env.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip())

CLAUDE_MODEL = os.environ.get("CLAUDE_MODEL", "claude-opus-5")
TRANSCRIBE_MODEL = os.environ.get("TRANSCRIBE_MODEL", "gpt-4o-transcribe")
NOTION_TOKEN = os.environ.get("NOTION_TOKEN", "")
NOTION_TASKS_DB = os.environ.get("NOTION_TASKS_DB", "")
NOTION_NOTES_PAGE = os.environ.get("NOTION_NOTES_PAGE", "")
TEAM_ROSTER = [n.strip() for n in os.environ.get("TEAM_ROSTER", "").split(",") if n.strip()]

# No Notion credentials -> tools record what they would do instead of calling Notion.
DRY_RUN = not (NOTION_TOKEN and NOTION_TASKS_DB)
