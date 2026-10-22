# Expressions and State Machine

[Index](INDEX.md)

## Separate activity from affect

Activity drives operational truth: `Idle`, `Listening`, `Thinking`, `Speaking`, `Sleeping`, `Unavailable`. Affect is an independent bounded expression choice. Permissions, connectivity, and thermal status are orthogonal facts, not emotions. The app must not appear to listen when microphone capture is stopped.

Proposed affect vocabulary: neutral, happy, sad, surprised, sleepy, bored, angry, left wink, right wink. These are planning names, based in part on the older upstream README; map to actual v1.3.9 identifiers after inspection. Do not invent native API names. “Thinking” and “listening” are app behaviors that may combine existing expressions and gaze.

## Transitions

| Current state | Event | Next state / action |
|---|---|---|
| Idle | Valid wake or touch-to-talk | Listening, if authorized; otherwise show permission status |
| Listening | End of utterance | Thinking, if a conversation route exists |
| Listening | Cancel/timeout | Idle; discard transient recording |
| Thinking | Valid reply | Speaking, when TTS is enabled |
| Thinking | Reply with TTS disabled | Present reply through chosen accessible UI; return Idle |
| Speaking | Completion/cancel | Idle |
| Any active state | Sleep command | Sleeping; invalidate pending sessions and stop camera |
| Sleeping | Permitted local wake/touch | Idle or Listening according to configured action |
| Any | Microphone mute | Stop all microphone consumers; retain nonaudio state |
| Any | Camera off/revocation | Close camera and clear observations; no automatic reopen |
| Any | Service unavailable | Nonblocking status and local fallback behavior |

## Arbitration

Privacy and user stop commands have highest authority, followed by lifecycle/safety constraints, explicit user interaction, active conversation activity, recent visual cues, local-model affect, and idle variation. This is a proposed priority order to test. Clamp gaze and intensity, apply transition easing, set minimum dwell times, and use hysteresis so noisy observations do not cause twitching.

Use a stable local random seed in tests. Blink/gaze scheduling belongs to deterministic animation/control, even when the model selects the next intention. Never require an LLM completion for every blink.

## Illustrative intent contract

```json
{
  "schema_version": 1,
  "expression": "happy",
  "intensity": 0.6,
  "gaze": {"x": 0.2, "y": -0.1},
  "hold_ms": 1800,
  "session_id": "local-session-12",
  "expires_at_monotonic_ms": 123456
}
```

This is a planning example, not an upstream API or final schema. Proposed coordinates are normalized to [-1, 1]; origin/sign mapping must account for display rotation and mirrored front-camera coordinates. Validate enum, finite numbers, bounds, duration, session, and expiry. Reject unknown actions. Intents cannot contain arbitrary code, URLs, permission grants, or camera commands.

## Idle and attention

Use subtle gaze excursions, occasional blinks, and smooth returns to center. Locally tracked face position can become a gaze target with smoothing and a lost-target timeout. Smile cues may trigger brief positive affect. The owner should be able to reduce autonomous activity. Final timing/intensity defaults require physical-phone observation and owner feedback.
