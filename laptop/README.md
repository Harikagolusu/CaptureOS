# laptop/: CaptureOS bridge (senior brain)

Recorded meeting audio → transcript (Sarvam, speaker-labelled; OpenAI optional) → Claude agent → Notion tasks + note.

## Run
```bash
cd laptop
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt     # Windows (macOS/Linux: .venv/bin/pip)
cp .env.example .env                               # fill in the keys
.venv/Scripts/python server.py                     # prints the IP the phone should use
```
Without Notion keys the server runs in **dry-run**: Claude still decides, and actions come back in the response but aren't written to Notion.

## Endpoints
| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/health` | none | ip, models, dry-run flag |
| POST | `/transcribe` | multipart `audio` (≤ 30 s voice note), optional `language` (`te`/`hi`/`en`) | `{transcript}` |
| POST | `/process` | JSON `{transcript, title?}` | `{summary, actions[], dry_run, id}` |
| POST | `/meeting` | multipart `audio`, `language?`, `title?`, `speakers?` | speaker-labelled transcript + process result |
| POST | `/voice/enroll` | multipart `audio` (~10 s "Hi, I'm Tej, I'm the manager"), `name`, `role?` | one-time setup of the app user's voice |
| GET | `/voice` | none | `{enrolled, name, role}` |

## Test
```bash
curl -X POST localhost:8000/process -H "Content-Type: application/json" \
  -d "{\"transcript\": \"$(cat samples/meeting_mixed.txt | tr '\n' ' ')\"}"
curl -X POST localhost:8000/meeting -F audio=@my_recording.m4a -F language=te
```

## Action shape (what the app renders)
```json
{"tool": "create_task", "args": {"title": "...", "owner": "Ravi", "priority": "high", "due": "2026-10-02"},
 "status": "done | dry-run | error | pending", "url": "https://notion.so/..."}
```
`ask_user` actions have `status: "pending"` and `args.question`; the app shows them as questions.

## Destinations
**Notion** (tasks DB + meeting notes page)
1. notion.so/profile/integrations → New internal integration → copy the secret into `.env` as `NOTION_TOKEN=`.
2. Create an empty page (e.g. "CaptureOS"), then ••• → Connections → add the integration.
3. `.venv/Scripts/python setup_notion.py <that page's URL>`: creates the "CaptureOS Tasks" DB + "CaptureOS Meeting Notes" page and saves their IDs to `.env`. Dry-run turns off automatically.

**vivo Office Kit Notes** (syncs to the iQOO phone's Notes app)
- Uses Office Kit's local third-party Notes API (`127.0.0.1:9200/third-party`, shipped as the `office-suite-notes` skill in the Office Kit install).
- Copy the token from the Office Kit app into `.env` as `VIVO_NOTES_TOKEN=`. `/health` shows whether the service is up and the token is set.
- Every processed meeting becomes one note: summary, action items, decisions, open questions, transcript.

## Who is who
- **App user:** enrolls once (`/voice/enroll`). Their intro is placed in front of every meeting, so the transcript labels their voice with their name.
- **Everyone else:** identified from the conversation: "Kiran, what's the update?" → the next voice that answers is Kiran. No setup for teammates.
- Names already in Notion are passed as spelling hints, so the same person keeps the same spelling.
- The meeting note shows attendees, which voice is who, and **Updates by person** (done / next / blocked) for the manager.

## Cloud (Railway), when you need the app without the laptop
```bash
cd laptop
railway init -n captureos            # new project
railway up --detach                  # builds the Dockerfile, deploys
railway volume add --mount-path /data
railway variables --set OPENROUTER_API_KEY=... --set NOTION_TOKEN=... --set NOTION_TASKS_DB=... --set NOTION_NOTES_PAGE=...
railway domain                       # public https URL -> put it in the app's Server address
```
Data (SQLite DB, recordings, photos) lives on the `/data` volume. The Office Kit watcher and
`record_laptop.py` only run on the laptop.

## Record a call running on the laptop
`.venv/Scripts/python record_laptop.py` records the computer's sound (the other people) + your mic,
press Enter to stop; the mp3 lands in the Office Kit folder and the running server processes it.
The laptop must not be muted (mute silences the capture); headphones are fine.
