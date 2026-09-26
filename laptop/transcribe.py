"""Audio file -> transcript text.

Providers (TRANSCRIBE_PROVIDER in .env):
- openrouter (default): GPT audio mini (or Gemini Flash) via OpenRouter; meetings get "Speaker N:" lines from the prompt.
- sarvam: short clips use the sync API (< 30 s); meetings use the batch API with
  speaker diarization, rendered as "Speaker 0: ..." lines so Claude can work out who owns what.
- openai: gpt-4o-transcribe, no diarization.
"""
import base64
import json
import os
import tempfile
from pathlib import Path

import httpx

from config import OPENROUTER_STT_MODEL, SARVAM_MODEL, SARVAM_MODE, TRANSCRIBE_MODEL, TRANSCRIBE_PROVIDER

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


_STT_PROMPT = """Transcribe this audio verbatim, in the language(s) actually spoken (Telugu, Hindi, English or a mix); keep code-switching as spoken. Output only the transcript, no commentary.{speakers}"""
_SPEAKERS = """
Separate speakers: start each turn on a new line as "Speaker 1:", "Speaker 2:" etc., keeping the same number for the same voice throughout."""


def _openrouter(
    path: Path, language: str | None, meeting: bool, names: list[str] | None = None, intro_name: str | None = None
) -> str:
    prompt = _STT_PROMPT.format(speakers=_SPEAKERS if meeting else "")
    if intro_name:
        prompt += (
            f"\nThe recording starts with {intro_name} briefly introducing themselves. Label every turn "
            f'in that voice "{intro_name}:" instead of a speaker number.'
        )
    if names:
        prompt += f"\nPeople who often come up (use these spellings if you hear them): {', '.join(names)}."
    if language:
        prompt += f"\nMain language hint: {language}."
    audio = base64.b64encode(path.read_bytes()).decode()
    fmt = path.suffix.lstrip(".").lower() or "m4a"
    r = httpx.post(
        "https://openrouter.ai/api/v1/chat/completions",
        headers={"Authorization": f"Bearer {os.environ['OPENROUTER_API_KEY']}"},
        json={
            "model": OPENROUTER_STT_MODEL,
            "messages": [{"role": "user", "content": [
                {"type": "text", "text": prompt},
                {"type": "input_audio", "input_audio": {"data": audio, "format": fmt}},
            ]}],
        },
        timeout=300,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"OpenRouter {r.status_code}: {r.text[:300]}")
    return r.json()["choices"][0]["message"]["content"].strip()


VIDEO = {".mp4", ".mov", ".mkv", ".webm", ".3gp", ".avi"}


def extract_audio(video: Path) -> Path:
    """Video file -> mono 16 kHz mp3 next to it (ffmpeg bundled via imageio-ffmpeg)."""
    import subprocess

    import imageio_ffmpeg

    out = video.with_suffix(".mp3")
    subprocess.run(
        [imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-i", str(video), "-vn", "-ac", "1", "-ar", "16000", "-b:a", "64k", str(out)],
        check=True,
        capture_output=True,
    )
    return out


def transcribe(
    path: Path,
    language: str | None = None,
    meeting: bool = False,
    speakers: int | None = None,
    names: list[str] | None = None,
    owner: dict | None = None,
) -> str:
    """language: "te" / "hi" / "en" hint, None = auto-detect. meeting=True -> long audio with speaker labels.
    Video files (e.g. a phone camera recording) are converted to audio first.
    owner: the app user's enrolled voice intro (voice.owner()); prepended to meetings so their voice gets their name."""
    if path.suffix.lower() in VIDEO:
        audio = extract_audio(path)
        try:
            return transcribe(audio, language, meeting, speakers, names, owner)
        finally:
            audio.unlink(missing_ok=True)
    if meeting and owner:
        import voice

        combined = voice.with_intro(path, owner["path"])
        try:
            if TRANSCRIBE_PROVIDER == "openrouter":
                return _openrouter(combined, language, meeting, names, intro_name=owner["name"])
            return transcribe(combined, language, meeting, speakers, names)
        finally:
            combined.unlink(missing_ok=True)
    if TRANSCRIBE_PROVIDER == "openrouter":
        return _openrouter(path, language, meeting, names)
    if TRANSCRIBE_PROVIDER == "openai":
        return _openai(path, language)
    if meeting:
        return _sarvam_meeting(path, language, speakers)
    return _sarvam_short(path, language)
