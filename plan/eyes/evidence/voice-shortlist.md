# Stage 8.1 voice shortlist (English, on the phone)

Date: 2026-10-23. Sources: GitHub release assets of `k2-fsa/sherpa-onnx` (`tts-models` and `v1.13.8`) and repository license metadata, read with `gh`. Nothing has been downloaded yet.

## Runtime
| Item | Version | Size (download) | License |
|---|---|---|---|
| sherpa-onnx (on-device speech toolkit; onnxruntime inside) | **v1.13.8** (2026-09-10). v2.0.0 came out today (2026-10-23), too new to pin | CLI for the benchmark: `sherpa-onnx-v1.13.8-android-aarch64-termux-static.tar.bz2` **149 MB** (static, should run from `adb shell`). App later: built from source or the 33 MB `android-static-link-onnxruntime` JNI package | Apache-2.0 |
| espeak-ng (turns English text into phonemes; sherpa-onnx compiles it in for Kokoro/Kitten/Piper) | pinned by sherpa-onnx (`csukuangfj/espeak-ng@ed530aa`) | inside the runtime + `espeak-ng-data/` in each model archive | **GPL-3.0** — see note |

## Voice models
| Model | File | Size | License | Why |
|---|---|---|---|---|
| **Kokoro-82M, English, int8** (main candidate) | `kokoro-int8-en-v0_19.tar.bz2` | **98 MB** | Apache-2.0 (weights, hexgrad/Kokoro-82M) | Very natural English, 11 voices; int8 for speed on a mid-range phone |
| **Kitten TTS nano v0.8, int8** (fallback) | `kitten-nano-en-v0_8-int8.tar.bz2` | **29 MB** | Apache-2.0 (KittenML/KittenTTS) | Tiny and fast if Kokoro is too slow next to the eye model |
| (not now) Kokoro-82M multi-lang v1.0 int8 | `kokoro-int8-multi-lang-v1_0.tar.bz2` | 126 MB | Apache-2.0 | More voices, but adds Chinese we don't need |
| (not now) Kokoro full precision | `kokoro-en-v0_19.tar.bz2` | 304 MB | Apache-2.0 | Too big/slow for this phone |

Benchmark download in total: about **276 MB** on the PC (149 + 98 + 29). On the phone everything goes to `/data/local/tmp` and is deleted after the benchmark, like Stage 2B. The phone has ~2.5 GiB free RAM when idle, and the eye model already uses ~1 GB.

## License note (owner decision needed before 8.3, not before the benchmark)
Kokoro/Kitten weights and sherpa-onnx are Apache-2.0, but the English phonemizer espeak-ng is **GPL-3.0**. Shipping it inside the app means the app, if distributed, must be distributed under GPL-3.0-compatible terms (source available). For a private robot or an open-source project that is fine. For a closed/commercial app it is not, and we would need a different phonemizer (e.g. an Apache-licensed English lexicon + rules) or a cloud voice. The benchmark itself (CLI on your own phone, nothing distributed) is not affected.

## What the benchmark measures (8.2)
Time to first audio for a short and a long sentence, real-time factor, peak memory with the eye model loaded, temperature, and recorded samples of the same line in each emotion setting (speed/voice), for the owner to hear.
