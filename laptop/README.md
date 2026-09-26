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
