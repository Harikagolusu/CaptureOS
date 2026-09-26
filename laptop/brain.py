"""The senior: Claude reads a meeting transcript, decides what needs doing, and acts through tools."""
import json
import os
from datetime import date

import anthropic
from anthropic import beta_tool

import notion
import vivo_notes
from config import CLAUDE_MODEL, DRY_RUN, USE_OPENROUTER

if USE_OPENROUTER:
    client = anthropic.Anthropic(base_url="https://openrouter.ai/api", auth_token=os.environ["OPENROUTER_API_KEY"])
    # Server-side refusal fallbacks are a first-party Anthropic API feature.
    EXTRA = {}
else:
    client = anthropic.Anthropic()
    EXTRA = {"betas": ["server-side-fallback-2026-07-01"], "fallbacks": "default"}

SYSTEM = """You are the operations brain of CaptureOS. You receive a transcript of a meeting or a \
field visit (possibly Telugu, Hindi, English, or a mix) and turn it into real work items.
Lines may be labelled "Speaker 0", "Speaker 1"...; those labels are anonymous, so work out who \
is who from names people use when addressing each other, and use real names as owners.
People are whoever the transcript names; there is no fixed team list, and new names are normal. \
If a name matches someone from earlier meetings (listed in the context), reuse that exact spelling.

Real meetings are messy. Read the whole conversation before acting:
- Skip small talk, jokes, side chatter and thinking out loud; they are not tasks or decisions.
- A task exists only when work was actually agreed. A request someone declined ("no, I can't \
this week") is not assigned to them: follow the discussion to whoever took it on, and if nobody \
did, create it with owner "unassigned" and ask_user who should own it.
- When people revise something (new owner, new deadline, dropped idea), the final version wins; \
don't create tasks for superseded versions or dropped ideas.
- Tentative commitments ("I'll try", "maybe by Friday") still become tasks; mention the \
uncertainty in the title only if it matters, and ask_user if the owner is truly unclear.

Use the tools to:
- create_task for every agreed action item (who does what, by when). Resolve relative dates \
("Friday", "next week") against today's date. Priority is high, medium, or low.
- create_note exactly once, with a short English summary, the decisions, the attendees (everyone \
who spoke or was named as present) and which speaker label is which person where you can tell.
- ask_user when an action item has no clear owner or the transcript is ambiguous; still create \
everything you can.

Write all task titles, summaries and decisions in English. Don't invent tasks, owners, or dates \
that the transcript doesn't support. When done, reply with one sentence describing what you did."""


def run_brain(
    transcript: str, title: str | None = None, source: str = "cloud", speakers_hint: str | None = None
) -> dict:
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
    def create_note(
        title: str, summary: str, decisions: list[str], attendees: list[str], speaker_map: list[str]
    ) -> str:
        """Create the meeting note page in Notion with summary, decisions, attendees and the full transcript.

        Args:
            title: Meeting title in English.
            summary: 2-4 sentence English summary.
            decisions: Decisions that were agreed, one per item.
            attendees: Names of everyone who spoke or was named as present.
            speaker_map: One entry per identified speaker label, e.g. "Speaker 1 = Tej (manager)". Empty if unknown.
        """
        args = {
            "title": title,
            "summary": summary,
            "decisions": decisions,
            "attendees": attendees,
            "speaker_map": speaker_map,
        }
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
    known = [] if DRY_RUN else notion.known_people()
    if known:
        context += f"\nPeople from earlier meetings (spelling reference, not a limit): {', '.join(known)}"
    if title:
        context += f"\nMeeting title hint: {title}"
    if speakers_hint:
        context += f"\nWho is speaking (from the user): {speakers_hint}"

    runner = client.beta.messages.tool_runner(
        model=CLAUDE_MODEL,
        max_tokens=16000,
        thinking={"type": "adaptive"},
        system=SYSTEM,
        tools=[create_task, create_note, ask_user],
        messages=[{"role": "user", "content": f"{context}\n\n<transcript>\n{transcript}\n</transcript>"}],
        **EXTRA,
    )
    final = None
    for message in runner:
        final = message

    if final is not None and final.stop_reason == "refusal":
        summary = "Request declined by the model."
    else:
        summary = next((b.text for b in (final.content if final else []) if b.type == "text"), "")
    return {
        "summary": summary,
        "actions": actions,
        "attendees": next((a["args"].get("attendees", []) for a in actions if a["tool"] == "create_note"), []),
        "vivo_note": export_vivo_note(actions, transcript, title),
        "dry_run": DRY_RUN,
        "model": CLAUDE_MODEL,
    }


def export_vivo_note(actions: list[dict], transcript: str, title: str | None) -> dict:
    """Mirror the whole meeting into vivo Office Notes so it syncs to the phone."""
    if not vivo_notes.enabled():
        return {"status": "skipped", "reason": "VIVO_NOTES_TOKEN not set"}
    note = next((a["args"] for a in actions if a["tool"] == "create_note"), {})
    tasks = [a["args"] for a in actions if a["tool"] == "create_task"]
    questions = [a["args"]["question"] for a in actions if a["tool"] == "ask_user"]
    body = vivo_notes.meeting_html(
        note.get("summary", ""), note.get("decisions", []), tasks, questions, transcript, note.get("attendees")
    )
    try:
        note_id = vivo_notes.create_note(note.get("title") or title or "Meeting", body)
        return {"status": "done", "id": note_id}
    except Exception as e:
        return {"status": "error", "error": str(e)}
