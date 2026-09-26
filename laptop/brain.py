"""The senior: Claude reads a meeting transcript, decides what needs doing, and acts through tools."""
import json
from datetime import date

import anthropic
from anthropic import beta_tool

import notion
from config import CLAUDE_MODEL, DRY_RUN, TEAM_ROSTER

client = anthropic.Anthropic()

SYSTEM = """You are the operations brain of CaptureOS. You receive a transcript of a meeting or a \
field visit (possibly Telugu, Hindi, English, or a mix) and turn it into real work items.

Use the tools to:
- create_task for every concrete action item (who does what, by when). Resolve relative dates \
("Friday", "next week") against today's date. Priority is high, medium, or low.
- create_note exactly once, with a short English summary and the list of decisions.
- ask_user when an action item has no clear owner or the transcript is ambiguous; still create \
everything you can.

Write all task titles, summaries and decisions in English. Don't invent tasks, owners, or dates \
that the transcript doesn't support. When done, reply with one sentence describing what you did."""


def run_brain(transcript: str, title: str | None = None, source: str = "cloud") -> dict:
    actions: list[dict] = []

    def record(tool: str, args: dict, run) -> str:
        entry = {"tool": tool, "args": args, "status": "dry-run" if DRY_RUN else "done"}
        if not DRY_RUN and run:
            try:
                entry["url"] = run()
            except Exception as e:  # report to Claude and the app instead of crashing the run
                entry["status"] = "error"
                entry["error"] = str(e)
        actions.append(entry)
        return json.dumps({k: v for k, v in entry.items() if k != "args"})

    @beta_tool
    def create_task(title: str, owner: str, priority: str, due: str = "") -> str:
        """Create one action item in the team's Notion task board.

        Args:
            title: Short imperative task title in English.
            owner: Person responsible, exactly as named in the transcript or roster; "unassigned" if unknown.
            priority: One of high, medium, low.
            due: Due date as YYYY-MM-DD, or empty if none was mentioned.
        """
        args = {"title": title, "owner": owner, "priority": priority, "due": due or None}
        return record("create_task", args, lambda: notion.create_task(**args, source=source))

    @beta_tool
    def create_note(title: str, summary: str, decisions: list[str]) -> str:
        """Create the meeting note page in Notion with summary, decisions and the full transcript.

        Args:
            title: Meeting title in English.
            summary: 2-4 sentence English summary.
            decisions: Decisions that were agreed, one per item.
        """
        args = {"title": title, "summary": summary, "decisions": decisions}
        return record("create_note", args, lambda: notion.create_note(**args, transcript=transcript))

    @beta_tool
    def ask_user(question: str) -> str:
        """Queue a clarifying question for the user in the app. Continue with everything else.

        Args:
            question: One short question.
        """
        actions.append({"tool": "ask_user", "args": {"question": question}, "status": "pending"})
        return "Question queued for the user."

    context = f"Today's date: {date.today().isoformat()}"
    if TEAM_ROSTER:
        context += f"\nTeam roster (valid owners): {', '.join(TEAM_ROSTER)}"
    if title:
        context += f"\nMeeting title hint: {title}"

    runner = client.beta.messages.tool_runner(
        model=CLAUDE_MODEL,
        max_tokens=16000,
        thinking={"type": "adaptive"},
        system=SYSTEM,
        tools=[create_task, create_note, ask_user],
        messages=[{"role": "user", "content": f"{context}\n\n<transcript>\n{transcript}\n</transcript>"}],
        betas=["server-side-fallback-2026-07-01"],
        fallbacks="default",
    )
    final = None
    for message in runner:
        final = message

    if final is not None and final.stop_reason == "refusal":
        summary = "Request declined by the model."
    else:
        summary = next((b.text for b in (final.content if final else []) if b.type == "text"), "")
    return {"summary": summary, "actions": actions, "dry_run": DRY_RUN, "model": CLAUDE_MODEL}
