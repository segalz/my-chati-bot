# Stage 7.2 evidence record: eyes P0 (original eyes + on-phone eye model)

Per the [evidence record template](../../12_ENGINEERING_AND_TEST_PLAN.md). Date: 2026-10-23.

## Build and revisions
- Device: Redmi 10, Android 13 / API 33, MIUI 14, arm64-v8a (serial not recorded; see [device-inventory.md](./device-inventory.md)).
- App: `app.mychatibot` 0.5.2 (versionCode 10), debug build with native code optimized (Release). APK SHA-256 `9508bec4e30e8d4c1d231d10c6826a157aa0ae0126254ef487286cef37a1b7e3`. Installed by the owner from Downloads (MIUI blocks `adb install`).
- Source: branch `eyes/stage3-android-skeleton`. The renderer is espp/expressive_eyes **v1.3.9** Monochrome Blue, unmodified (archive and hashes in `third_party/`). LVGL `02814dd`, fmt `93e26fa`, llama.cpp `620d222` (v0.7.0). NDK r28c, CMake 3.31.6, AGP 9.4.1.
- Model: Gemma 3 1B it Q4_K_M GGUF (SHA-256 verified, [model-shortlist.md](./model-shortlist.md)), in app-private storage, 6 threads.
- Modes: debug broadcast channel (ADB, dev/test only); input is English only (R12); no INTERNET permission.

## Results
| Area | Steps | Observed | Status |
|---|---|---|---|
| Original eyes render | Stage 3–4 checks, owner visual approval | Monochrome Blue at 60 fps; rotation/background/lifecycle handled | PASS ([stage4-metrics.md](./stage4-metrics.md)) |
| Intent contract | Unit tests + DEBUG_INTENT on phone | Strict schema v1, newest-valid-wins controller; 45/45 unit tests | PASS |
| Sentence → eyes accuracy | 100 English sentences (40 dev + 60 holdout) | 88/100 (dev 37, holdout 51) | PASS |
| Offline | Same set in airplane mode | 88/100, identical | PASS ([stage6/eval-0.5.2-airplane.txt](./stage6/eval-0.5.2-airplane.txt)) |
| Latency | Same set | model p50 ~0.93–1.07 s; phrasebook ~1 ms; instant blink on arrival | PASS (target 0.5 s not reached at this accuracy; [model-benchmark.md](./model-benchmark.md)) |
| Animation during inference | Smoke + demos | 60 fps held while the model runs | PASS |
| 30-min stability | [stage7-smoke.md](./stage7-smoke.md) | 117 sentences, flat memory, no crash, no heating | PASS |
| Demo video | [demo/talk-demo-0.4.1.mp4](./demo/) | 7/7 sentences reacted correctly | PASS |

## Untested / open
- Fallback to `fallback` mode when MIUI kills the model under real memory pressure: release is wired to `onTrimMemory`, but a real low-memory kill was never forced.
- Battery drain (all runs were on USB power) and runs longer than 30 min.
- Hebrew input: out of scope by decision (R12; translation happens upstream).
- Real upstream sender (Claude/voice): sentences have only come through the ADB debug channel so far.
- Release build (non-debug) signing and install.
- Owner live demo sign-off (Stage 7 review).

## Addendum 2026-10-23: stories and voice (APK 0.6.x–0.7.0)
- App 0.7.0 (versionCode 14), APK SHA-256 `7b06bc97136fe7d124aab7f164f6f86cc9d77791162d82f3db34950d0b052851`. Adds sherpa-onnx v1.13.8 JNI (`libsherpa-onnx-jni.so`, from the release asset with SHA-256 `7583ca38…9e6f`). The voice model Piper `en_US-libritts_r-medium` (`.onnx` SHA-256 `8cce84ce…17f4`) sits in app-private `files/voice/`.

| Area | Steps | Observed | Status |
|---|---|---|---|
| Stories, pacing | 10 stories (5 dev / 5 holdout) via DEBUG_STORY; rules tuned on dev | dev 30/35, holdout 25/35; owner watched s02: "good for now" | PASS ([stage6c/](./stage6c/), [demo/story-demo-0.6.1.mp4](./demo/)) |
| Story context in the model | Same stories, context on vs off | worse with context (48/70 vs 55/70), so it stays off | Not adopted |
| Voice | DEBUG_SPEAK story s02 | 7/7 sentences spoken; eyes 3–6 ms after each audio start; first audio 1.54 s; no gaps (2 voice threads) | PASS (owner: "really good") |
| Memory | `/proc/<pid>/status` after speaking | VmRSS 1.69 GB (eyes + eye model + voice) | Recorded, watch |

Untested (in addition to the list above): video with sound (screenrecord has no audio), voice under memory pressure (release wired, not forced), long voice sessions and battery, and a Hebrew voice (later stage). The default of 2 voice threads is in the code but takes effect from the next APK; 0.7.0 runs with it set over the debug channel.
