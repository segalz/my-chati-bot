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
