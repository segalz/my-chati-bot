# User Interactions

[Index](INDEX.md)

## First launch

Show the companion and a short setup path: privacy controls, optional microphone/camera permissions, voice setup, and optional conversation endpoint. Allow an eyes-only preview without permissions or credentials. Explain where processing occurs before enabling an online speech or vision option.

The app should show capability status rather than silently pretending everything is supported. Example: “Eyes available; voice permission needed; conversation not configured.” Final wording and interface language: **TBD / Ask owner**.

## Typical desk session

| Situation | Intended behavior |
|---|---|
| No interaction | Local idle gaze, occasional blinks, restrained expression changes |
| Wake word heard | Acknowledge locally; enter listening if microphone access and policy allow |
| Owner speaks | Capture a bounded utterance; show listening eyes |
| Waiting for API | Thinking expression; keep animation responsive; allow cancel |
| Reply arrives | Speak through selected TTS; coordinate eyes with speech state |
| Owner says sleep | Stop conversation capture, cancel active requests where possible, show resting eyes |
| Owner says wake | Exit companion sleep only if local wake listening was explicitly enabled |
| Person appears in permitted camera mode | Smoothly attend toward estimated face position |
| Smile detected | Optional brief friendly reaction; do not claim to know the person's feelings |
| Camera unavailable | Return to nonvisual behavior; show status without repeated permission prompts |
| Network unavailable | Keep local eyes and supported local commands; indicate conversation is offline |

## Sleep and mute are separate

Proposed: **companion sleep** may retain an explicitly enabled local wake detector, but stops normal speech recording and camera sessions. **Microphone mute** stops all microphone use, including wake detection. Waking from mute requires touch or a visible control. Never promise voice wake while the microphone is fully off.

Whether companion sleep dims the display or closes the eyes is **TBD / Ask owner**. Final wake word, sleep/wake phrases, supported spoken languages, and false-trigger tolerance are also open.

## Visual interaction

The front camera faces the owner when the phone is mounted normally; the rear camera may face the room or an object. Mount orientation and intended rear-camera use need confirmation. Select one camera at a time initially. Do not assume simultaneous front/rear availability.

Capture requires a supported app state, OS permission, an enabled app camera mode, and a valid purpose. A request such as “What do you see?” may propose a still capture. Cloud transmission requires a separate allowed upload decision. Local autonomous reactions may use an explicitly enabled local awareness session; they may not turn a disabled camera back on.

## Owner controls

Provide discoverable touch controls for mute, camera off, sleep/wake, cancel speech/request, provider settings, and delete history. Exact gestures, screen layout, brightness, sounds, and personality intensity are **TBD / Ask owner**. Privacy controls must remain accessible even during full-screen eyes.
