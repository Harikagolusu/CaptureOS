# CaptureOS API (for the Flutter app)

Base URL: `http://<laptop IP>:8000` for now (the server prints it on start; `/health` shows it). Cloud URL later.
Interactive docs with "Try it out": open `http://<laptop IP>:8000/docs` in a browser.

**Auth:** after `/team` or `/join`, store `user.token` on the phone and send it on every call:
`Authorization: Bearer <token>`. `401` = token missing or wrong (send the user back to the join screen).

## Screens → calls

### 1. First launch
**Admin: "Create team"**
```
POST /team   {"team_name": "BuildX", "admin_name": "Tej", "aliases": ["Teja"], "profile": "manager"}
→ {"team": {"id": 1, "name": "BuildX", "code": "0KVE4Y"}, "user": {"id": 1, "name": "Tej", "role": "admin", "token": "..."}}
```
Show the **code** big on screen; teammates type it in.

**Member: "Join team"**
```
POST /join   {"code": "0KVE4Y", "name": "Harika", "profile": "employee"}
→ {"team": {...}, "user": {"id": 2, "name": "Harika", "role": "member", "token": "..."}}
```
`404` wrong code. Name matching ignores case and accepts nicknames the admin added.
`profile` = what the smart inbox treats as important: `employee` | `student` | `manager` | `field`. Change later with `PATCH /me {"profile": "student"}`.

### 2. Admin: team screen
```
GET  /team                         → {"name", "code" (admin only), "members": [{"id","name","aliases","role","joined"}]}
POST /team/members  {"name": "Hrithik", "aliases": ["Hritik"]}     (admin only)
```
`joined: false` = added by the admin but hasn't opened the app yet.

### 3. Record a meeting (usually the admin)
Record in the background (foreground-service notification), then upload the file (audio **or** video):
```
POST /meetings   multipart: audio=<file>, title (optional), language (optional: te | hi | en)
→ {"id": 7, "status": "uploaded"}
```
Returns at once. Then **poll every 3 s**:
```
GET /meetings/7   → "status": uploaded → transcribing → thinking → done   (or "error" + "error" message)
```
Takes ~30–60 s for a 1–2 minute meeting.

### 4. Meeting detail (everyone in the team)
```
GET /meetings/7 →
{
  "id": 7, "title": "Daily Standup - 26 Sep 2026", "status": "done", "summary": "...", "notion_url": "...",
  "attendees": [{"name": "Harika", "user_id": 2, "speaker_label": "speaker 2", "update": "done deployment; next fix bugs ..."}],
  "tasks":     [{"id": 3, "title": "Fix bugs ...", "owner": "Harika", "owner_user_id": 2, "due": "2026-09-27", "priority": "high", "status": "todo"}],
  "questions": [{"id": 1, "text": "Who owns the API docs?", "answer": "", "status": "open"}],
  "segments":  [{"speaker": "Speaker 2", "name": "Harika", "user_id": 2, "text": "Today I completed ..."}]
}
GET /meetings   → list: [{"id", "title", "status", "created_at", "summary"}]  (newest first)
```

### 5. My view (member home)
```
GET /me                      → who am I (name, role)
GET /me/tasks?status=todo    → my tasks (status filter optional: todo | doing | done)
GET /me/meetings/7           → {"summary", "my_update", "my_tasks": [...], "i_said": ["...", "..."]}
PATCH /tasks/3  {"status": "done"}       → updates the app DB and the Notion page
```
Members can only change their own tasks; the admin can change any.

### 6. Admin answers Claude's questions
```
POST /questions/1/answer  {"answer": "Kiran owns the docs"}      (admin only)
```

### Optional: admin voice intro (once)
```
POST /voice/enroll   multipart: audio=<~10 s "Hi, I'm Tej, I'm the manager">, name, role
GET  /voice          → {"enrolled", "name", "role"}
```
Makes the admin's voice come out as their name in every meeting.

### 7. Field Mode (photos)
One **visit** = one site walk; each **observation** = one photo + optional voice note / typed note.
```
POST /field/visits  {"site": "Block B handover"}                       → {"id": 3, "site": "..."}
POST /field/visits/3/observations   multipart: photo=<jpg>, voice=<audio, optional>, note=<text, optional>
                                                                        → {"id": 9, "status": "processing"}   (returns at once; keep shooting)
GET  /field/visits/3   → {
  "site", "report_status": none | building | done | error,
  "summary", "key_issues": [...], "recommendations": [...], "report_url": "/field/visits/3/report.docx", "notion_url",
  "observations": [{"id": 9, "status": "processing|done|error", "title": "Cracked floor tile near lift",
                    "observation": "...", "category": "finishing", "severity": "none|low|medium|high",
                    "action": "Replace the cracked tiles before handover", "owner": "Ravi",
                    "voice_text": "...", "note": "...", "photo_url": "/field/photos/9", "task_id": 12}]
}
GET  /field/visits     → list of visits
GET  /field/photos/9   → the photo (send the auth header on the image request too)
POST /field/visits/3/report       → {"report_status": "building"}  then poll GET /field/visits/3 until "done" (~20-30 s)
GET  /field/visits/3/report.docx  → Word report with photos (download, then share / open)
```
Compiling the report also creates tasks for medium/high problems (they show up in `/me/tasks` for their owner), a Notion page, and a Slack post.

### 8. Smart inbox (productive Google Photos + recordings)
The phone sends new gallery photos and recordings (on demand: "check today's photos", or a background
watcher). Or the user just sends them to the laptop with **Office Kit file transfer**, and the laptop picks them up
automatically. Claude files each into a self-named album; only items that matter for the user's profile get
their text read and to-dos extracted. People/personal photos are filed, never turned into tasks.
```
POST /inbox   multipart: files=<one or many photos/audio/video>, client_ids="IMG_101,IMG_102" (gallery ids, optional),
              taken_at="2026-09-26T10:05,2026-09-26T12:30" (optional)       → {"accepted": [ids], "skipped": [...]}
GET  /inbox?kind=photo|audio&album=...&actionable=true   → [{
        "id", "kind": "photo|audio", "source": "app|officekit", "status": "queued|done|error",
        "album": "DBMS Lecture Slides", "category": "lecture slide", "people": false, "actionable": true,
        "title", "summary", "text" (text read from the photo / audio transcript),
        "task_ids": [7], "meeting_id": null (set when a recording was a meeting), "file_url": "/inbox/7/file"}]
GET  /inbox/albums      → [{"album": "Sprint Boards", "count": 3, "cover_url": "/inbox/9/file"}]
GET  /inbox/{id}/file   → the photo / audio (send the auth header)
```
Audio is classified as `meeting` (runs the full meeting flow → shows in `/meetings`), `lecture` (study notes in
`summary`), `voice_memo` (to-dos → `/me/tasks`), `call`, `personal`, or `other`.
Duplicates (same `client_id`, or the same file arriving from the app and Office Kit) are skipped.

### Save to vivo Notes (no API)
On meeting detail, a **Save to Notes** button shares `summary + my tasks` as text via `share_plus`; the user picks vivo Notes.

## Errors
`401` no/bad token · `403` not allowed (member doing admin things, or someone else's task) · `404` wrong code / not in your team · `422` bad input.
