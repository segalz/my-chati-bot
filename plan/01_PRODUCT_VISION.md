# Product Vision

[Index](INDEX.md)

## The idea

Give an unused Android phone a second life as a charming, always-present companion on a desk. The display becomes a pair of expressive blue eyes. It looks around, blinks, rests, reacts to conversation, and responds to permitted visual context. It should feel alive while the owner is working, without requiring continuous paid cloud requests.

The visual foundation is the **original open-source `espp/expressive_eyes` v1.3.9 C++ library**, including its monochrome blue renderer where feasible. Preserve the original behavior and appearance through an Android NDK/JNI port before considering any substitute. A visually similar animation rewritten from scratch does not meet this source-reuse intent.

## Three distinct responsibilities

1. **Animation:** deterministic, smooth frame updates on the device. Blink curves, gaze movement, interpolation, and drawing do not wait for inference.
2. **Local intelligence:** a small model on the phone selects expressions and coordinates autonomous eye behavior using bounded context. It does not generate images or video frames.
3. **Conversation:** a configurable API-based AI handles dialogue. OpenAI-compatible services and other provider adapters are possible. An optional Ollama LAN backend may serve conversation; it does not replace the requirement to validate local eye intelligence on the phone.

## Experience goals

- Recognizable blue eyes, smooth idle motion, natural blinking, gaze shifts, and emotional expression.
- Local wake-word detection; spoken sleep and wake commands; speech input and output.
- Front-facing camera for optional person awareness, plus rear-facing camera support for contextual views when deliberately enabled.
- Face presence, smile cues, and approximate gaze/head direction influencing eye behavior locally.
- Clear microphone, camera, network, and sleep status. Camera permission alone must not imply unrestricted capture or upload.
- Useful offline presence even when conversation is unavailable.

## Boundaries

This is an Android app, preferably Kotlin plus original native C++, not new ESP hardware. It is not a frame-generating AI demo or a face-identification system. Background operation, locked-screen listening, reboot recovery, and model performance are validation risks, not promised capabilities.

“Always-on” describes the desired desk experience. Whether that means a visible foreground app, a dim display, or locked-screen operation remains subject to device tests and owner decisions. Start with a user-launched visible desk mode.

## Success

A convincing demo uses the actual old phone: original-source blue eyes keep moving during inference and network waits; a locally produced expression changes behavior; wake/sleep works in the supported mode; one conversation round trip works through the configured provider; and denied camera permission produces zero camera capture. Measured evidence matters more than an emulator recording or a successful build.

No release date, phone specification, provider, exact wake phrase, language set, or final model has been selected.
