"""Audio file -> transcript text. Provider: OpenAI (gpt-4o-transcribe). AI4Bharat slot comes next."""
from pathlib import Path

from openai import OpenAI

from config import TRANSCRIBE_MODEL

_client: OpenAI | None = None


def transcribe(path: Path, language: str | None = None) -> str:
    """language: ISO-639-1 hint like "te", "hi", "en"; None lets the model detect it."""
    global _client
    _client = _client or OpenAI()
    with open(path, "rb") as f:
        kwargs = {"model": TRANSCRIBE_MODEL, "file": f}
        if language:
            kwargs["language"] = language
        result = _client.audio.transcriptions.create(**kwargs)
    return result.text
