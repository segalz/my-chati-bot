# Voice and Vision Pipelines

[Index](INDEX.md)

## Voice

```text
Granted mic permission + enabled listening mode
 -> audio input -> local wake-word detector
 -> wake acknowledgement -> bounded utterance with silence/timeout rules
 -> selected STT -> transcript -> local command router or conversation adapter
 -> response text -> selected TTS -> speaker
```

Sleep/wake and stop commands should be handled locally where the selected recognition path permits. Local wake-word detection is required; a cloud listener is not a substitute. Wake-word engine, phrase, language coverage, licensing, false positives/negatives, and old-device CPU cost require evaluation. Candidate families include small keyword-spotting models or an embedded speech toolkit such as sherpa-onnx; no engine is selected.

STT candidates include Android-provided recognition where availability and processing location can be verified, or a bundled offline model/runtime. TTS may use an installed offline Android voice if suitable. Some engines/languages need downloads or network access; test actual offline behavior instead of labeling the whole platform service offline. Online STT/TTS is optional and must be disclosed and explicitly enabled.

Coordinate audio focus, interruptions, silence detection, endpointing, recording limits, and cancellation. Prevent the app's own TTS from repeatedly waking it. Begin with half-duplex speech if necessary; barge-in is a later measured enhancement. Never block the eyes while waiting for a transcript or speech synthesis.

## Vision

```text
Permission + active supported state + enabled camera mode + purpose
 -> selected front OR rear camera
 -> bounded in-memory frames -> local detector/tracker
 -> face presence/position, smile cue, approximate head/gaze direction
 -> smoothed events -> local eye policy
```

Face detection and head orientation are not reliable knowledge of identity or precise eye gaze. Smile scores do not prove emotions. Define quality/confidence thresholds, lighting limitations, lost-target behavior, rotation, mirror correction, and multi-face selection. Proposed default is the most stable nearby face without identifying it; final selection policy is **TBD / Ask owner**.

CameraX/Camera2 and local detector toolkits are candidates. Audit their model/runtime licenses and device support before adoption. Start with downsampled, rate-limited analysis, bounded buffers, and dropped old frames. Release frames promptly to avoid camera stalls and memory accumulation.

## Context-sensitive still capture

An LLM request or local trigger can propose capture; only the policy layer can approve it. Check OS permission, app camera toggle, selected camera, current session/purpose, foreground eligibility, cooldown, and upload allowance at the point of use. Avoid autonomous rear-camera capture until its purpose is agreed.

Local awareness and cloud vision are separate modes. A locally processed preview frame must not automatically become a cloud image. Before sending a still, show the applicable consent/status and minimize resolution/content. No silent video stream, continuous cloud vision, automatic storage, or capture while disabled.

## Degradation

Without a camera, idle and voice still work. Without microphone permission, touch/text can exercise the companion. Without STT/TTS availability, show the limitation. Offline use retains only verified local capabilities; the eyes always have a deterministic fallback. Sensor permissions can be revoked mid-session and must be honored immediately.
