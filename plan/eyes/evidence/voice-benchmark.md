# Stage 8.2 voice benchmark on the phone (CLI)

Date: 2026-10-23. Phone: Redmi 10 ([device-inventory.md](./device-inventory.md)), with the eye app running (eye model loaded). Runtime: sherpa-onnx **v1.13.8** `android-aarch64-termux-static` CLI (`sherpa-onnx-offline-tts`), plus `libc++_shared.so` from NDK r28c because the Termux build expects it. Everything ran from `/data/local/tmp/voice`, which was deleted afterwards. All downloads matched the SHA-256 digests published on the GitHub release assets (see [voice-shortlist.md](./voice-shortlist.md)). Owner approved both download rounds.

## Speed: "I got the job at the bakery!"
"Generate" is the synthesis time printed by the CLI; RTF = generate / audio length (below 1 = faster than speech).

| Engine (file) | Size | License | Threads | Generate | Audio | RTF |
|---|---|---|---|---|---|---|
| Kokoro-82M int8 (`kokoro-int8-en-v0_19`) | 98 MB | Apache-2.0 | 2 / 4 / 6 | 11.3 / 10.4 / 10.3 s | 1.72 s | 6.6 / 6.0 / 6.0 |
| Kitten nano int8 (`kitten-nano-en-v0_8-int8`) | 29 MB | Apache-2.0 | 2 / 4 / 6 | 3.22 / 3.01 / 2.88 s | 2.0 s | 1.61 / 1.50 / 1.43 |
| Kitten nano fp32 (`kitten-nano-en-v0_8-fp32`) | 60 MB | Apache-2.0 | 4 / 6 | 1.53 / 1.45 s | 2.0 s | 0.76 / 0.73 |
| Supertonic int8 (`sherpa-onnx-supertonic-tts-int8-2026-03-06`) | 80 MB | OpenRAIL | 4 / 6 | 1.12 / 1.09 s | 2.28 s | 0.49 / 0.48 |
| **Piper libritts_r-medium** (`vits-piper-en_US-libritts_r-medium`) | 78 MB | CC BY 4.0 (dataset), MIT (Piper) | 2 / 4 / 6 | 0.59 / 0.44 / **0.37 s** | 1.44 s | 0.39 / 0.30 / **0.25** |

The int8 models are slow on this CPU (Kitten int8 is about 2× slower than fp32), and Kokoro is out of reach even before that.

## Emotion samples
The same 4 two-sentence lines (happy ×1.10 speed, sad ×0.85, angry ×1.05, surprised ×1.15), voice 0, 6 threads. Time per line: Piper 0.70–0.95 s, Supertonic 1.6–1.9 s, Kitten fp32 2.1–3.6 s. Files: [voice-samples/](./voice-samples/) (`*-all-emotions.wav`, single lines in `samples/`). None of these engines acts out emotion by itself: the difference comes from speed and punctuation only.

## Decision
**Piper libritts_r-medium** (owner left the choice to Claude, 2026-10-23). Reasons: by far the fastest (speech can start well under a second after a line arrives), the cleanest license, and 904 speakers to pick per emotion without another download. Claude cannot listen to audio, so the choice is based on speed and license. Owner listening and the speaker choice per emotion come in 8.4.

## Not measured / open
- Peak memory and temperature of the TTS process (the Android `time -v` output was not captured); measure in the app (8.3).
- Time to first audio inside the app (sentence-by-sentence synthesis) — 8.3/8.5.
- License: Piper and Kitten/Kokoro all use the espeak-ng phonemizer (GPL-3.0) through sherpa-onnx. That matters only if the app is distributed closed-source (owner decision before 8.3).
