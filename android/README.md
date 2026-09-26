# CaptureOS on-device app (Android, Kotlin)

Built on [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) (Apache-2.0, see `LICENSE`),
commit `3ec1f34`. Everything runs **on the phone** with Gemma (LiteRT-LM) — no server, no API keys.

## What we added
`app/src/main/java/com/google/ai/edge/gallery/customtasks/captureos/`

| File | Does |
|---|---|
| `CaptureTask.kt` | The "CaptureOS" tile; uses Gemma-4-E2B-it (or Gemma 4 E4B / 3n) with audio input |
| `MeetingRecorder.kt` | 16 kHz mic recording, split into 28 s WAV clips (Gemma audio limit is 30 s) |
| `CapturePipeline.kt` | On-device: transcribe each clip → read transcript with the `createTask` tool → summary |
| `CaptureTools.kt` | `createTask(title, owner, dueDate, dueTime, priority)` tool Gemma calls |
| `TaskStore.kt` | Tasks saved on the phone |
| `ReminderScheduler.kt` | Notification at the due time (or 9:00 on the due date), via WorkManager |
| `CaptureScreen.kt` | Record button, typed notes, results, Tasks tab |

Other changes: app name "CaptureOS", `applicationId = com.buildx.captureos` (installs next to Gallery),
`versionName = 1.0.19` so the Gemma 4 model list loads.

## Build
Needs JDK 17+ and Android SDK 37.
```
echo sdk.dir=D:/tools/android-sdk > local.properties
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```
First launch: open the CaptureOS tile, download Gemma-4-E2B-it (2.6 GB, once, on Wi-Fi). After that it works in airplane mode.
