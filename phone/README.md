# CaptureOS phone app (Flutter)

Talks to the backend in `../laptop` (see `../docs/API.md`).

## Run on the iQOO
1. Start the backend on the laptop: `cd laptop && .venv/Scripts/python server.py` — it prints `http://<IP>:8000`.
2. Phone and laptop on the same Wi-Fi / hotspot.
3. `flutter pub get` then `flutter run` (USB debugging on), or `flutter build apk --release` and install
   `build/app/outputs/flutter-apk/app-release.apk`.
4. First screen: type the server address, then **Create team** (admin) or **Join a team** with the code.

## Tabs
| Tab | What it does | Backend |
|---|---|---|
| Tasks | My to-dos from meetings, photos, voice memos, site visits; tick to finish (mirrors to Notion) | `/me/tasks`, `PATCH /tasks/{id}` |
| Meetings | Record (or upload) a meeting → summary, who said what, updates, tasks; "Just me" view; Save to Notes | `/meetings` |
| Inbox | "Check today's photos" reads the gallery; AI sorts into albums and pulls out to-dos. Office Kit transfers land here too | `/inbox` |
| Field | Site visits: photo + voice observations → report (Word + Notion) | `/field/...` |
| Team | Join code, members, my type (employee/student/manager/field), voice intro, log out | `/team`, `/me`, `/voice/enroll` |

## Code map
- `lib/core/api_client.dart` — server URL, login token, JSON + multipart calls
- `lib/models/api_models.dart` — backend JSON models
- `lib/screens/` — `onboarding`, `main_shell` (tabs), `tasks`, `meetings`/`record_meeting`/`meeting_detail`,
  `inbox`, `field_visits`/`field_visit`, `team`; the original field-capture screens (`capture_screen` is reused
  for Field observations)
- `lib/services/camera_service.dart`, `voice_service.dart` — real camera (image_picker) and mic (record);
  `Fake*` versions for tests
