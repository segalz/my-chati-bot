# MVP and Implementation Phases

[Index](INDEX.md)

## Scope and sequencing

The first convincing MVP is a working companion on the actual old phone: original blue eyes, validated local expression selection, local wake detection and sleep/wake, one conversational voice path, and strict privacy controls. Front-camera awareness follows as a bounded visual milestone. Rear-camera contextual capture is a separate addition. The complete vision includes both cameras; early milestones must state omissions explicitly.

No phase below authorizes implementation, installation, paid calls, or device changes during this documentation task. Durations remain TBD until hardware and port risks are measured.

| Phase | Work package | Exit evidence / dependency |
|---|---|---|
| P0 — Feasibility | Inventory actual phone/toolchain; obtain exact v1.3.9; source, renderer, dependency and license audit; sensor lifecycle research | Sanitized hardware report; immutable source manifest; supported-mode matrix; explicit blockers |
| P1 — Original eyes | Kotlin shell, NDK/JNI port, original blue drawer/dependency adaptation, deterministic idle/blink/gaze, touch privacy controls | Original-source provenance; APK running on physical phone; visual comparison; frame/memory baseline |
| P2 — Local behavior | Benchmark candidate model(s), validate intent schema, event/cooldown policy, deterministic fallback | Genuine on-phone model output changes eyes offline without cloud calls; sustained responsiveness |
| P3 — Voice companion | Local wake detection; sleep/wake/mute; bounded STT/TTS; configurable conversation adapter | Real voice round trip; local commands; cancellation; provider failure/offline tests; documented costs |
| P4 — Local visual awareness | Opt-in front camera; face/smile/head/gaze cues; smoothing; sensor/resource arbitration | Permitted visual reaction; denied/revoked permission means zero capture; acceptable thermal load |
| P5 — Contextual sight | Rear-camera selection; purpose-bound still capture; optional image-capable API path | Correct camera and consent boundary; no silent storage/upload; unsupported capability handled |
| P6 — Reliability | Long desk sessions, lifecycle recovery, locked/background research, privacy review, reproducible packaging | Device evidence and known-limits report; no unsupported always-on claims |

## Milestone gates

Do not hide an unavailable v1.3.9 behind `main`, replace the original renderer silently, or claim a local AI when only rules ran. If the phone cannot meet a core requirement, present the measured failure and revised choices to the owner before changing the scope.

The foreground desk MVP can proceed while unsupported locked-screen/background modes remain explicitly deferred. This is a proposed sequencing choice, not confirmation that background sensing is unnecessary to the final product.

## MVP demonstration script

1. Launch the exact installed artifact on the selected phone; show original blue eyes and provenance.
2. Disconnect network; show smooth idle, a verified locally inferred expression, and supported local sleep/wake.
3. Mute the microphone; verify capture stops and touch wake remains available.
4. Reconnect the configured endpoint; perform one speech conversation and show that eyes never freeze while waiting.
5. Disable/deny camera; verify no capture. If P4 is complete, enable a permitted awareness session and demonstrate one visual reaction, then stop it.
6. Show resource measurements and a concise list of unsupported or untested modes.

## Deferred possibilities

Advanced barge-in, rich long-term memory, precise gaze estimation, multiple people, simultaneous cameras, custom voice/personality, store publication, reboot automation, and background operation beyond validated capabilities are not assumed MVP commitments. Select them only through explicit decisions and measurements.
