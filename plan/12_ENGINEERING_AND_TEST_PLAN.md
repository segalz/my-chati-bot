# Engineering and Test Plan

[Index](INDEX.md)

## Evidence principle

Test the physical old phone early. Emulator tests supplement permission, lifecycle, layout, and mocked adapter checks but cannot establish sustained thermal behavior, camera quality, microphone acoustics, or model speed on that phone. Discovery, a successful build, and a working ADB shell each prove only that narrow capability.

## Acceptance matrix

| Area | Test | Required observation |
|---|---|---|
| Provenance | Verify exact archive/commit and renderer source | v1.3.9 identified; adapted files and licenses traceable |
| Rendering | Idle, blink, gaze, expression transitions during inference/API delay | Original visual behavior retained; no blocking waits in frame path |
| Native lifecycle | Rotate, background/foreground, destroy/recreate surface | No crash, stale surface, or resource leak |
| Local AI | Offline model inference from fixed scenarios | Actual model output validated and reflected in eyes; fallback separately labeled |
| Intent safety | Invalid enum, NaN/out-of-range values, expired session | Rejected/clamped per contract; privacy state cannot change |
| Voice | Wake, sleep, wake again, mute, silence timeout | Local wake detection; no wake-by-voice while fully muted |
| Audio | TTS playback, interruption, noisy room | No recurring self-trigger loop; bounded recordings |
| API | Valid call, bad key, timeout, malformed stream, cancel | Clear state, bounded retry, no duplicate speech or late wake |
| Camera | Denied permission, app toggle off, runtime revocation | No unauthorized frames acquired or sent |
| Vision | Face enters/leaves, smile cue, rotation, low light, multiple faces | Stable confidence-aware reaction; graceful loss of target |
| Upload | Local-only mode and separately allowed still query | No network image transfer in local-only mode |
| Offline | Airplane/no route, unavailable LAN host | Local eyes continue; unsupported conversation clearly identified |
| Resources | Long render + model + wake/vision session | Measured memory, frame timing, temperature, and failure recovery |
| Privacy | Inspect redacted logs/storage/network events | No secrets or raw ambient media retained by default |

## Test levels

- Focused unit tests for state arbitration, stale-result rejection, intent validation, permission/capture gating, and cancellation.
- Native deterministic scenarios with seeded randomness and visual reference comparisons from the verified upstream renderer.
- Integration tests for JNI/surface ownership, model loading, voice/vision adapters, and mocked provider responses.
- Instrumented physical-device tests and manual real-sensor trials for acoustics, optics, lifecycle, and privacy indicators.
- A small emulator matrix chosen from the final supported API range, rather than an invented range today.

## Proposed budgets, pending baseline

Aim to sustain the selected 30 or 60 fps profile without visible freezes; choose the final target from device measurements. Report p50/p95 frame time and long stalls, not only average fps. Measure model warm/cold latency, valid-intent rate, peak memory, wake-word false accepts/rejects, speech latency, camera duty cycle, and temperature trend. Thresholds for release are **TBD after P0/P1**; these targets are not measured results.

Run a short smoke session during each relevant change and a proposed two-hour combined-load soak before claiming desk readiness. Longer testing is required before calling the product “all-day.” Record ambient conditions, charging, brightness, device OS, and enabled features.

## Evidence record template

For each meaningful test record: date; device/API/ABI (sanitized); app version and APK hash; exact source/model/runtime revisions; enabled modes; steps; expected/observed result; metrics; redacted logs or permitted visual evidence; pass/fail/untested; limitations. Never fabricate a screenshot or model result to close a milestone.

When verification output requires code investigation, use the owner's required delegated route. Retest the affected behavior after edits; avoid broad repetition without a reason.
