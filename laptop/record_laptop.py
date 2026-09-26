"""Record a Zoom / Google Meet / Teams call running on the laptop: the computer's own sound (the other
people) plus the laptop microphone (you), mixed into one mp3 in the Office Kit folder. The server's
Office Kit watcher picks it up and runs the full meeting flow (transcript, tasks, Notion, Slack).

    .venv/Scripts/python record_laptop.py            # press Enter to stop
"""
import threading
import time
import wave
from datetime import datetime

import numpy as np
import soundcard as sc

from officekit import WATCH_DIR
from transcribe import extract_audio

RATE = 16000
BLOCK = 1600  # 0.1 s


def _capture(device, chunks: list, stop: threading.Event) -> None:
    with device.recorder(samplerate=RATE, channels=1, blocksize=BLOCK) as rec:
        while not stop.is_set():
            chunks.append(rec.record(numframes=BLOCK)[:, 0])


def main() -> None:
    speaker = sc.default_speaker()
    loopback = sc.get_microphone(id=str(speaker.name), include_loopback=True)
    mic = sc.default_microphone()
    print(f"Recording the call audio ({speaker.name}) + your mic ({mic.name}).")
    print("Press Enter to stop...\n")

    stop = threading.Event()
    call, you = [], []
    threads = [threading.Thread(target=_capture, args=(d, buf, stop), daemon=True) for d, buf in ((loopback, call), (mic, you))]
    for t in threads:
        t.start()
    started = time.time()
    input()
    stop.set()
    for t in threads:
        t.join(timeout=2)

    a = np.concatenate(call) if call else np.zeros(0, dtype=np.float32)
    b = np.concatenate(you) if you else np.zeros(0, dtype=np.float32)
    rms = lambda x: float(np.sqrt(np.mean(x**2))) if len(x) else 0.0  # noqa: E731
    if rms(a) < 1e-4:
        print("Warning: no call audio was captured - is the laptop muted? (mute silences the capture too)")
    if max(rms(a), rms(b)) < 0.006:  # only background noise: transcribers invent text from it
        print("Nothing but background noise was heard - not saved.")
        return
    n = max(len(a), len(b))
    mixed = np.pad(a, (0, n - len(a))) + np.pad(b, (0, n - len(b)))
    peak = float(np.max(np.abs(mixed))) if n else 0.0
    if peak > 0:
        mixed = mixed / peak * 0.9  # normalise so quiet calls are still clear
    pcm = (mixed * 32767).astype(np.int16)

    WATCH_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    wav_path = WATCH_DIR.parent / f"_laptop_call_{stamp}.wav"  # temp, outside the watched folder
    with wave.open(str(wav_path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    mp3 = extract_audio(wav_path)
    final = WATCH_DIR / f"laptop_call_{stamp}.mp3"
    mp3.replace(final)
    wav_path.unlink(missing_ok=True)
    print(f"Saved {time.time() - started:.0f} s to {final}")
    print("The CaptureOS server will pick it up and process the meeting.")


if __name__ == "__main__":
    main()
