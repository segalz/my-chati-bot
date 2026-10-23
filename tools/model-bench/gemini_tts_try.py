"""Stage 9 try-out: Gemini TTS in Hebrew from the PC. Reads GEMINI_API_KEY from the repo's .env
(never printed), writes one WAV per line and prints the request time.
Usage: python gemini_tts_try.py --out DIR [--model gemini-3.8-flash-tts] [--voice Kore]
"""

import argparse
import base64
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
LINES = [
    ("happy", "warm, excited and happy", "קיבלתי את העבודה במאפייה! אני מתחיל ביום שני!"),
    ("sad", "sad, quiet and slow, close to tears", "הווטרינר אמר שהוא לא ישרוד. אני עדיין מתגעגע אליו כל יום."),
    ("angry", "angry and frustrated", "והם מסרבים להחזיר לי את הכסף! זה פשוט מגוחך."),
    ("surprised", "amazed and surprised", "פתאום כולם קפצו וצעקו הפתעה! לא האמנתי!"),
]


def api_key():
    env = Path(__file__).resolve().parents[2] / ".env"
    for line in env.read_text(encoding="utf-8").splitlines():
        if line.startswith("GEMINI_API_KEY="):
            return line.split("=", 1)[1].strip().strip('"')
    raise SystemExit("GEMINI_API_KEY not found in .env")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--model", default="gemini-3.8-flash-tts")
    ap.add_argument("--voice", default="Kore")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    key = api_key()
    for name, style, text in LINES:
        body = {
            "model": args.model,
            "input": [{"type": "user_input", "content": [
                {"type": "text", "text": text, "annotations": [{"type": "speech_metadata", "style": style}]},
            ]}],
            "generation_config": {"speech_config": [{"voice": args.voice}]},
            "response_format": {"type": "audio"},
        }
        req = urllib.request.Request(URL, data=json.dumps(body).encode(), method="POST",
                                     headers={"x-goog-api-key": key, "Content-Type": "application/json"})
        started = time.time()
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.load(r)
        except urllib.error.HTTPError as e:
            print(f"{name}: HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:600]}")
            return
        took = time.time() - started
        audio = None
        for step in data.get("steps", []):
            for c in step.get("content", []) or []:
                if c.get("data"):
                    audio = base64.b64decode(c["data"])
        if audio is None:
            print(f"{name}: no audio in response keys={list(data)[:8]}")
            return
        (out / f"gemini-{args.voice}-{name}.wav").write_bytes(audio)
        print(f"{name}: {took:.2f} s, {len(audio)} bytes")


if __name__ == "__main__":
    main()
