# CaptureOS

30-hour hackathon project — offline-first field capture.

## Vision (interim, until PLAN.md lands)
PHONE Field Mode (hero) + Meeting Mode (lightweight) → offline queue → Office Kit / local → LAPTOP FastAPI dashboard.

Field Mode pipeline:
Photo capture → Voice note → Speech-to-text → On-device, hardware-accelerated AI (Gemma 3n) → Structured observation → Human review → Evidence links → Phone-generated PDF.

Principles:
- Offline-first, no cloud dependency for core Field Mode
- Human approval before final export
- Every observation retains links to source photo + audio
- Say "on-device, hardware-accelerated" — do not claim NPU unless verified
- Phone PDF generation mandatory, laptop secondary

## Repo layout (proposed, not yet scaffolded)
- `phone/` — Flutter app (Field Mode hero)
- `laptop/` — FastAPI stub (secondary)
- `docs/` — architecture
- `design/philosophy.md` — to be added
- `PLAN.md` — to be added (source of truth)

## Team workflow
- Default branch: `main`
- Create feature branches: `field/*`, `meeting/*`, `laptop/*`, `docs/*`
- PRs required, 1 review minimum for hackathon speed
- No direct commits to `main` after bootstrap

## Status
Bootstrap commit only — no implementation yet.
