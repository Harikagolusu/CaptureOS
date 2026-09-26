"""Audio file -> transcript text.

Providers (TRANSCRIBE_PROVIDER in .env):
- sarvam (default): short clips use the sync API (< 30 s); meetings use the batch API with
  speaker diarization, rendered as "Speaker 0: ..." lines so Claude can work out who owns what.
- openai: gpt-4o-transcribe, no diarization.
"""
import json
import os
import tempfile
from pathlib import Path

from config import SARVAM_MODEL, SARVAM_MODE, TRANSCRIBE_MODEL, TRANSCRIBE_PROVIDER

_LANG = {"te": "te-IN", "hi": "hi-IN", "en": "en-IN", "ta": "ta-IN", "kn": "kn-IN"}


def _sarvam_lang(language: str | None) -> str:
    if not language:
        return "unknown"
    return _LANG.get(language, language)


def _sarvam_client():
    from sarvamai import SarvamAI

    return SarvamAI(api_subscription_key=os.environ["SARVAM_API_KEY"])


def _sarvam_short(path: Path, language: str | None) -> str:
    with open(path, "rb") as f:
        r = _sarvam_client().speech_to_text.transcribe(
            file=f, model=SARVAM_MODEL, mode=SARVAM_MODE, language_code=_sarvam_lang(language)
        )
    return r.transcript


def _sarvam_meeting(path: Path, language: str | None, speakers: int | None) -> str:
    job = _sarvam_client().speech_to_text_job.create_job(
        model=SARVAM_MODEL,
        mode=SARVAM_MODE,
        language_code=_sarvam_lang(language),
        with_diarization=True,
        num_speakers=speakers,
    )
    job.upload_files(file_paths=[str(path)])
    job.start()
    job.wait_until_complete()
    if not job.is_successful():
        raise RuntimeError(f"Sarvam batch job {job.job_id} failed: {job.get_file_results()}")
    with tempfile.TemporaryDirectory() as out:
        job.download_outputs(output_dir=out)
        result = json.loads(next(Path(out).glob("*.json")).read_text(encoding="utf-8"))
    entries = (result.get("diarized_transcript") or {}).get("entries") or []
    if not entries:
        return result.get("transcript", "")
    return "\n".join(f"Speaker {e['speaker_id']}: {e['transcript']}" for e in entries)


def _openai(path: Path, language: str | None) -> str:
    from openai import OpenAI

    with open(path, "rb") as f:
        kwargs = {"model": TRANSCRIBE_MODEL, "file": f}
        if language:
            kwargs["language"] = language
        return OpenAI().audio.transcriptions.create(**kwargs).text


def transcribe(path: Path, language: str | None = None, meeting: bool = False, speakers: int | None = None) -> str:
    """language: "te" / "hi" / "en" hint, None = auto-detect. meeting=True -> long audio with speaker labels."""
    if TRANSCRIBE_PROVIDER == "openai":
        return _openai(path, language)
    if meeting:
        return _sarvam_meeting(path, language, speakers)
    return _sarvam_short(path, language)
