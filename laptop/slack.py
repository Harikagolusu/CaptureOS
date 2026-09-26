"""Slack: post each processed meeting to a channel through an Incoming Webhook (SLACK_WEBHOOK_URL)."""
import httpx

from config import SLACK_WEBHOOK_URL


def enabled() -> bool:
    return bool(SLACK_WEBHOOK_URL)


def _section(text: str) -> dict:
    return {"type": "section", "text": {"type": "mrkdwn", "text": text[:3000]}}


def meeting_blocks(
    title: str,
    summary: str,
    attendees: list[str],
    tasks: list[dict],
    questions: list[str],
    notion_url: str = "",
) -> list[dict]:
    blocks = [
        {"type": "header", "text": {"type": "plain_text", "text": f"📋 {title}"[:150]}},
        _section(summary or "_No summary_"),
    ]
    if attendees:
        blocks.append({"type": "context", "elements": [{"type": "mrkdwn", "text": "👥 " + ", ".join(attendees)}]})
    if tasks:
        lines = []
        for t in tasks:
            due = f" · due {t['due']}" if t.get("due") else ""
            lines.append(f"• *{t['title']}* → {t['owner']} ({t['priority']}{due})")
        blocks += [{"type": "divider"}, _section("*Action items*\n" + "\n".join(lines))]
    if questions:
        blocks.append(_section("*Needs an answer*\n" + "\n".join(f"❓ {q}" for q in questions)))
    if notion_url:
        blocks.append({
            "type": "actions",
            "elements": [{"type": "button", "text": {"type": "plain_text", "text": "Open in Notion"}, "url": notion_url}],
        })
    blocks.append({"type": "context", "elements": [{"type": "mrkdwn", "text": "_Captured by CaptureOS_"}]})
    return blocks


def post(blocks: list[dict], fallback: str) -> None:
    r = httpx.post(SLACK_WEBHOOK_URL, json={"text": fallback, "blocks": blocks}, timeout=15)
    if r.status_code != 200:
        raise RuntimeError(f"Slack {r.status_code}: {r.text[:200]}")
