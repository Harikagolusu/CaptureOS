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
            v = v.split(" #", 1)[0].strip()
            if v:  # empty values would shadow real env vars / confuse SDK credential lookup
                os.environ.setdefault(k.strip(), v)

# Claude access: direct Anthropic key preferred; otherwise OpenRouter's Anthropic-compatible endpoint.
USE_OPENROUTER = not os.environ.get("ANTHROPIC_API_KEY") and bool(os.environ.get("OPENROUTER_API_KEY"))
CLAUDE_MODEL = os.environ.get("CLAUDE_MODEL", "anthropic/claude-opus-5" if USE_OPENROUTER else "claude-opus-5")
TRANSCRIBE_PROVIDER = os.environ.get("TRANSCRIBE_PROVIDER", "openrouter")
OPENROUTER_STT_MODEL = os.environ.get("OPENROUTER_STT_MODEL", "openai/gpt-audio-mini")
TRANSCRIBE_MODEL = os.environ.get("TRANSCRIBE_MODEL", "gpt-4o-transcribe")
SARVAM_MODEL = os.environ.get("SARVAM_MODEL", "saaras:v3")
SARVAM_MODE = os.environ.get("SARVAM_MODE", "transcribe")
NOTION_TOKEN = os.environ.get("NOTION_TOKEN", "")
NOTION_TASKS_DB = os.environ.get("NOTION_TASKS_DB", "")
NOTION_NOTES_PAGE = os.environ.get("NOTION_NOTES_PAGE", "")
SLACK_WEBHOOK_URL = os.environ.get("SLACK_WEBHOOK_URL", "")
VIVO_NOTES_TOKEN = os.environ.get("VIVO_NOTES_TOKEN", "")
VIVO_NOTES_PORT = os.environ.get("VIVO_NOTES_PORT", "9200")

# No Notion credentials -> tools record what they would do instead of calling Notion.
DRY_RUN = not (NOTION_TOKEN and NOTION_TASKS_DB)
