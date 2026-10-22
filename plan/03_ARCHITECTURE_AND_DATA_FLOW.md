# Architecture and Data Flow

[Index](INDEX.md)

## Proposed components

| Component | Responsibility | Must not do |
|---|---|---|
| Kotlin app shell | Lifecycle, settings, permissions, status, owner controls | Treat model output as permission |
| Event/state controller | Current activity, cancellation, behavior priorities | Block on rendering or network |
| Local eye policy | Infer bounded expression/gaze intents from local context | Generate frames, make paid calls, open camera directly |
| Native eye engine | Original C++ animation semantics and renderer adaptation | Depend on conversational API availability |
| Voice coordinator | Wake word, utterance boundaries, STT, TTS, audio focus | Upload ambient audio by default |
| Vision coordinator | Authorized camera sessions and local tracking | Save/upload frames without policy approval |
| Conversation adapter | Provider requests, streaming, limits, cancellation | Control sensors or native memory directly |
| Policy/security layer | Enforce permissions, retention, endpoints, upload limits | Delegate authority to model text |

## Logical flow

```text
Microphone permission + enabled mode -> local wake detector -> bounded utterance
                                                        -> STT -> text
Text + selected conversation context -> configured API or optional Ollama backend
                                     -> reply -> TTS -> speech lifecycle events

Camera permission + enabled mode + capture policy -> selected camera
       -> local face/smile/head-direction observations -> normalized local events
       -> optional approved still -> vision-capable conversation endpoint

Local events + activity state -> small on-device eye policy -> validated intent
                                                      -> state arbitration
                                                      -> original C++ engine
                                                      -> Android surface
```

The central AI can supply optional conversational affect hints. The local controller decides how to use them. A cloud hint is not required for idle eyes and cannot override mute, sleep, permissions, or thermal limits.

## Scheduling and ownership

Use a dedicated render owner and a monotonic clock with display-paced updates. Adapt time stepping to verified original-library semantics; do not assume a delta-time API exists. JNI should carry state changes, configuration, and surface lifecycle, rather than repeated model text or unnecessary full-frame copies.

Inference, vision, audio, and networking run outside the render/UI critical path. Use bounded queues and newest-valid intent semantics. Include event timestamps, request/session IDs, and expiration so late API replies or model results cannot wake a sleeping companion. Cancel pending work on privacy changes and release resources on lifecycle transitions.

The policy gets compact observations such as `face_present`, normalized face position, smile confidence, activity, elapsed idle time, and speech start/end. Default to transient context, not raw images or ambient audio. Rate-limit inference by events and cooldowns; exact cadence is a benchmark outcome.

## Failure behavior

- Slow/crashed local model: deterministic neutral/idle policy keeps the eyes alive; record degraded mode.
- Network timeout: end thinking state, indicate unavailable conversation, no unbounded automatic retries.
- Malformed model intent: reject it, retain safe prior state until expiry, then idle.
- Sensor loss: clear observations and return to nonvisual/nonlistening behavior.
- Thermal pressure: reduce vision/inference load before compromising permission enforcement; reduce frame target if needed.

Deterministic fallback is necessary resilience, but it must not be reported as proof of a functioning local AI.
