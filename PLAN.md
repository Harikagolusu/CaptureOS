# CaptureOS: Battle Plan (Team BuildX, 3 people, Flutter)

> Rule assumed: **PC dev allowed until 13:00. After 13:00 = phone-only (Red Light).**
> So by 13:00 the APK + models must be ON the phones and the app must be tunable
> without a PC. Green Light (laptop back) = sync + dispatch demo.
> Confirm exact rules with organisers at kickoff; adjust times below.

---

## 1. Sharpened pitch (what changed vs deck)

**One line:** "Speak in your language, snap the site. Get an English report and tickets. Offline."

| Deck said | Now |
|---|---|
| Two equal modes | **Field Mode = hero** (less competition, visual demo). Meeting Mode = second act, 30s. |
| 3 models (ASR + LLM + VLM) | **2 engines**: on-device speech + **Gemma 3n** (one multimodal model does text + image). Fewer moving parts. |
| "Runs on Snapdragon NPU" | "Runs fully on-device, hardware-accelerated". Don't claim NPU unless we prove it. |
| Telugu-first (unproven) | **"Speak Telugu/Hindi/English → structured English output."** Prove with one live Telugu line. |
| Laptop needed for output | **Phone already makes the PDF report + tasks alone** (Red Light proof). Laptop = live dashboard + Slack/Notion dispatch. |
| Just outputs | **Evidence-linked output**: every task / finding links back to its photo + audio clip. Trust feature for auditors. |
| Auto-send | **Review-before-send** screen: user edits/approves AI output. Answers "what if AI is wrong?" |

---

## 2. Weak points → fixes

1. **"Otter/Fireflies already exist"**: they're cloud, English, meetings-only. We are offline, vernacular, field + meeting, privacy-safe. Lead with Field Mode.
2. **Vernacular ASR risk**: Plan A: Android on-device speech recognizer via `speech_to_text` (te-IN / hi-IN offline packs; download on phone before 13:00). Plan B: Whisper via `sherpa_onnx` (English/Hindi). Gemma translates + structures to English.
3. **NPU integration too hard**: skip QNN. `flutter_gemma` (MediaPipe) runs on GPU. Honest claim.
4. **Slow VLM**: one photo ≈ a few seconds on Gemma 3n E2B. Show a progress animation; process in background queue while user takes the next photo.
5. **LLM gives broken JSON**: strict prompt + few-shot example + JSON repair/validation + retry once + fallback to raw text entry. Never crash on stage.
6. **RAM**: one model instance, loaded at app start (warm-up), reused for both modes.
7. **After 13:00 can't code**: in-app **Lab screen**: edit prompts, language, laptop IP, mock-mode toggle. All fixes after 13:00 happen here.
8. **Venue Wi-Fi fails**: own hotspot for phone ↔ laptop. Phone keeps an offline queue and syncs when laptop appears (itself a demo moment).
9. **Live demo fails**: mock-mode toggle (pre-canned result) + backup screen recording made on the phone after 13:00.

---

## 2b. Hybrid AI: local for small, cloud for heavy

**Pitch framing: "Local-first, cloud when it matters."** Never "cloud app with local fallback".

**Router (on phone, rules tunable in Lab screen):**
| Goes LOCAL (Gemma 3n) | Goes CLOUD |
|---|---|
| One field entry (1 photo + short voice note) | Full field report across all entries (summary, recommendations) |
| Meeting < ~5 min transcript → tasks | Long meeting transcript (> ~2k tokens) |
| Classification, severity, PII redaction | Local output failed JSON check / missing fields ("unsure") |
| **Private Mode ON** or **no network**: everything | Telugu / code-switched audio if local ASR is weak (optional) |

**Rules:**
- No network or Private Mode → always local. The demo still works in airplane mode.
- Before any cloud call: **redact locally first** (names, phones, amounts → placeholders); send text only by default, never raw audio/photos unless the user allows.
- The API key never goes in the APK. Phone → small **cloud proxy** (serverless function) → LLM. P3 deploys it before 13:00, since after 13:00 there is no laptop.
- Every result shows a badge: `on-device` or `cloud`. Judges see the routing live.
- Cloud timeout (~8s) → fall back to the local result. Never block the UI.

**Cloud model:** whichever you have free credits/keys for (Gemini, Claude, etc.). Use the same JSON schema for both engines so the rest of the app doesn't care.

**Demo beat:** airplane mode ON → field entry done locally (badge `on-device`). Airplane mode OFF → tap "Compile report" → cloud writes the polished report (badge `cloud`). Shows both.

---

## 2c. v1 CORE: voice → transcript → "Jarvis" brain → actions in Notion

> Slack dropped for now. Notion is the only destination in v1.

**Flow**
```
[hold mic] → speech-to-text (on-device) → transcript
   → BRAIN (Gemma local / cloud if long) → action plan JSON
   → action cards on phone (approve / edit / reject)
   → executor → Notion (tasks DB + notes page)
   → result shown ("2 tasks created for Ravi, Priya") + Notion link
```

**Brain output (one schema for local + cloud):**
```json
{
  "summary": "Site review: replace Block B tiles, send quotation",
  "actions": [
    {"tool": "create_task", "args": {"title": "Send revised quotation", "owner": "Ravi", "due": "2026-09-27", "priority": "high"}, "confidence": 0.92},
    {"tool": "create_note", "args": {"title": "Site review sync", "decisions": ["Replace Block B tiles"], "transcript": "..."}, "confidence": 0.95},
    {"tool": "ask_user",   "args": {"question": "Who owns 'book contractor'?"}, "confidence": 0.4}
  ]
}
```

**Tools v1 (keep it small):**
- `create_task`: row in the Notion "CaptureOS Tasks" DB (Name, Owner, Due, Priority, Status=To do, Source=on-device/cloud)
- `create_note`: Notion page with summary, decisions, transcript
- `ask_user`: brain missing info (owner/date) → app asks, then re-plans

**Tools v2 (only if v1 is solid), the "Jarvis" wow:**
- `update_task` ("mark quotation done")
- `query_tasks` ("what's pending for Ravi?") → reads Notion, speaks the answer

**Decision rules:**
- Prompt gets: today's date (resolves "Friday"), team roster (valid owners), tool list, 2 few-shot examples.
- Confidence ≥ 0.8 AND create-only → auto-run (shows an Undo = archive page). Else → approval card.
- Invalid JSON → repair → retry once → fall back to one `create_note` with the raw transcript. Never lose the capture.
- Short command / meeting < ~5 min → Gemma on phone. Long transcript → cloud (per 2b).

**Where Notion calls run:** the Notion secret is NOT in the APK. Phone → POST `/actions` → laptop server (later: cloud proxy) → Notion API. Offline / no laptop → the action plan sits in the phone queue and flushes when the bridge is reachable.

**Build order**
1. **P3:** Notion integration + tasks DB + `/actions` endpoint on the laptop server. Test with curl + a sample plan JSON.
2. **P2:** brain prompt iterated on the PC FIRST (run Gemma via Ollama on the laptop, same prompt), using 10 sample transcripts (English, Hinglish, Telugu mix). Lock the prompt, then move it to the phone.
3. **P1:** Flutter: mic screen → live transcript → action cards → approve → POST `/actions` → success view.
4. Integrate on the phone → then add routing to cloud for long transcripts.

**Notion setup (P3, 10 min)**
1. notion.so/profile/integrations → New internal integration → copy the secret.
2. Create DB "CaptureOS Tasks": `Name` (title), `Owner` (text), `Due` (date), `Priority` (select), `Status` (select), `Source` (select).
3. DB `•••` → Connections → add the integration. Copy the DB id from its URL.
4. `laptop/.env`: `NOTION_TOKEN=...`, `NOTION_TASKS_DB=...`, `NOTION_NOTES_PAGE=...` (parent page for notes).
5. Check the current Notion API version header + the database vs data-source parent format in the docs before coding.

---

## 2d. BRAIN v2: cloud plans, Gemma helps in parallel, Gemma executes (runs on PC first)

```
transcript
   │
   ▼
CLOUD PLANNER ── splits the job, tags each subtask HARD / EASY
   │
   ├── HARD → cloud solves        (summary, decisions, who-owns-what, report)   ┐ parallel
   └── EASY → Gemma local solves  (dates, priority, names/entities, formatting) ┘
   │
   ▼
MERGE → one action plan (same schema as 2c)
   │
   ▼
GEMMA EXECUTOR (local) → validates, fills gaps, emits tool calls
   │
   ▼
executor code → Notion task / Notion note / ask_user
```

- **Runs on the PC now:** orchestrator in the laptop FastAPI; Gemma via Ollama on the laptop; cloud via API. Same code path moves to the phone later.
- **Planner output:** `{"subtasks":[{"id":"s1","kind":"hard|easy","goal":"...","input":"..."}]}`. Easy ones fire to Gemma immediately (async), hard ones run on the cloud in the same `asyncio.gather`.
- **Gemma executor does NOT call Notion itself.** It emits JSON tool calls; plain code validates + runs them. The LLM decides, code acts, so there are no hallucinated API calls.
- **Failsafes:** no network → Gemma runs the whole plan alone. Cloud > 8s → use Gemma's answer. Private Mode → local only. Redact names/numbers before anything goes to the cloud. Bad JSON → repair → retry → save as note.
- **Latency tip:** Gemma can start easy extraction on the raw transcript *while* the cloud plans (speculative); the planner just reuses it. Try it if the demo feels slow.

---

## 3. Architecture

```
PHONE (Flutter)                                        LAPTOP (Green Light)
┌─────────────────────────────────────────┐           ┌──────────────────────────┐
│ Capture: mic (record / speech_to_text)  │           │ FastAPI server           │
│          camera (image_picker/camera)   │           │  POST /entries           │
│              │                          │           │  WS  /live → dashboard   │
│ Speech → text (on-device, te/hi/en)     │  HTTP     │ Dispatch:                │
│              │                          │  JSON +   │  Meeting → Slack webhook │
│ Gemma 3n (flutter_gemma, GPU)           │  photos   │            (+ Notion)    │
│   text+image → strict JSON              │ ───────►  │  Field   → DOCX report   │
│              │                          │ (hotspot) │ Web dashboard (live feed)│
│ Review screen → local DB queue (sqflite)│           └──────────────────────────┘
│ Phone-only outputs: PDF report, share   │
│ Lab screen: prompts, lang, IP, mock     │
└─────────────────────────────────────────┘
```

**JSON contract (freeze in hour 1):**
```json
// meeting
{"type":"meeting","id":"...","title":"...","lang":"te",
 "decisions":["..."],
 "tasks":[{"title":"...","owner":"Ravi","due":"2026-09-27","priority":"high","evidence":{"audio_ms":[12000,19000]}}],
 "transcript":"..."}
// field
{"type":"field","id":"...","site":"...","lang":"hi",
 "entries":[{"photo":"img_01.jpg","observation":"...","issue":"crack in tile","severity":"medium","action":"...","voice_text":"..."}],
 "summary":"..."}
```

**Flutter packages (verify in hour 1):** `flutter_gemma`, `speech_to_text`, `record`, `image_picker` or `camera`, `sqflite`, `http`, `pdf` + `printing`/`share_plus`, `permission_handler`.
**Laptop:** Python FastAPI + uvicorn, `python-docx`, Slack incoming webhook, single-file HTML dashboard.

---

## 4. Team split

- **P1: Flutter app lead.** Screens (Home, Meeting, Field, Review, Lab), capture, sqflite queue, sync client, PDF on phone.
- **P2: On-device AI.** flutter_gemma setup + model on phone, speech recognition (te/hi/en), prompts, JSON parse/repair, warm-up. Owns "does it run on the iQOO".
- **P3: Laptop + pitch.** FastAPI server, live dashboard, Slack/Notion, DOCX. Then demo script, deck updates, backup video, judge Q&A.

---

## 5. Timeline (adjust to real kickoff)

**Before 13:00 (PC allowed): everything that needs a compiler**
- **K+0:00–0:45 Spikes (all parallel):** P2 runs Gemma 3n on the phone (flutter_gemma example app) + tests Telugu offline speech. P1 creates the Flutter skeleton + builds an APK to the phone. P3 FastAPI hello + Slack webhook. **Freeze JSON contract.**
- **Checkpoint 1:** Gemma runs on the phone? Yes → go. No → Gemma 3 1B text-only + photo caption skipped/manual tag (fallback).
- **K+0:45–2:30 Build:** P1 capture + review + queue. P2 ai_service (text→JSON, image+text→JSON). P3 dashboard + DOCX + Notion (optional).
- **K+2:30 → 12:15 Integrate:** end-to-end on the phone. Lab screen. Mock mode. Phone PDF export.
- **12:15–12:45 Freeze:** release APK installed on ALL 3 phones, models pushed, language packs downloaded, laptop server tested over the hotspot.
- **12:45–13:00 Buffer.** Nothing new.

**After 13:00 (phone-only)**
- Tune prompts in the Lab screen with real recordings (Telugu + English).
- Collect demo props + photos, rehearse the Field flow 5×.
- Record the backup demo video (screen record).
- P3 finalises the pitch + judge Q&A on the phone.

**Green Light:** start the laptop server → phone flushes its queue → dashboard lights up → Slack ping → DOCX opens. Rehearse this 3×.

**Cut list if behind (in order):** Notion → evidence audio timestamps → Meeting Mode live streaming (use record-then-transcribe) → DOCX on laptop (phone PDF is enough).

---

## 6. Demo script (3 min)

1. **Hook (15s):** "Every site visit ends with 2 hours of report writing. Watch it take 20 seconds, no internet."
2. **Airplane mode ON.** Show it.
3. **Field Mode (75s):** snap a prop (cracked tile / damaged box), say a voice note **in Telugu**. Entry appears in English: issue, severity, action. Second photo. Tap "Report" → PDF on the phone.
4. **Meeting Mode (40s):** 20s scripted mini-meeting with names + deadlines → tasks with owners/dates → review → approve.
5. **Airplane OFF / hotspot → laptop (30s):** queue syncs, dashboard fills live, Slack ping, DOCX report.
6. **Close (20s):** privacy, vernacular, offline. "Your phone becomes the eyes, ears, and hands of your workday."

## 7. Judge Q&A prep
- Accuracy? → review screen + evidence links; human stays in control.
- Why on-device? → privacy (client sites, finance), zero signal on sites, zero API cost.
- Latency / battery? → measure real numbers on the day, and say them.
- vs Otter? → cloud, English, meetings-only, no field reports.
- Business? → per-seat for field teams (insurance surveyors, construction QA, FMCG sales audits).
