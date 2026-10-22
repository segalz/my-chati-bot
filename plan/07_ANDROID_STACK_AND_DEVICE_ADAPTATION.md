# Android Stack and Device Adaptation

[Index](INDEX.md)

## Preferred stack

- Kotlin for lifecycle, settings, permissions, voice/camera coordination, and provider adapters.
- Original C++ eyes through Android NDK, CMake, and a small JNI interface.
- Android rendering surface adapted to verified upstream rendering needs. Do not select a graphics dependency before the audit.
- Coroutines or equivalent structured asynchronous work for coordination; keep inference/network off the UI/render path.
- CameraX or Camera2 as candidates, chosen by actual device/API coverage and control requirements.
- Replaceable local inference, STT, TTS, wake-word, and conversation interfaces.

Compose versus Android Views, minimum/target SDK, NDK version, C++ standard, app ID, and supported ABIs are **TBD**. Pin a reproducible toolchain after the phone and upstream requirements are known. Do not assume an old device is 64-bit or has a useful GPU/NPU backend.

## Hardware inventory first

Use [the connected-phone guide](10_CONNECTED_PHONE_DEVELOPMENT.md). Record model, manufacturer, OS/API, ABI list, total/free RAM, storage, resolution/refresh rate, camera capabilities, microphone/speaker operation, graphics limits, and charging/thermal observations. Capture only relevant diagnostics; sanitize serial numbers and unrelated device data.

Proposed capability profiles:

| Profile | Adaptation |
|---|---|
| Constrained | Lower render resolution/frame target, smallest local policy, reduced camera duty cycle, no concurrent heavy pipelines |
| Balanced | Smooth eyes plus validated local model, local wake detection, sampled local vision |
| Higher capacity | Additional speech/vision features only after sustained measurements |

These profiles have no invented RAM cutoffs. Assign them from measurements, not marketing specifications.

## Lifecycle and always-on risks

Begin with a visible, user-started desk app and a managed keep-screen-on setting. Screen-off/locked operation and reboot behavior are separate experiments. Check each actual OS/API/target combination.

Android documents microphone/camera foreground-service types, permissions, and while-in-use start restrictions. A foreground service is not a general bypass for sensor restrictions. See [official foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types). Background microphone/camera access is a high-priority validation risk.

Release audio/camera resources on mute, permission loss, unsupported background transitions, and app exit. Recreate rendering resources safely after rotation/surface loss. Do not ask agents to root the phone, disable system protections, or manipulate device-owner status to make a demo pass.

## Sustained desk operation

Measure temperature, charging behavior, frame pacing, model memory, and battery drain over a long session. Offer dimming and reduced activity; avoid unnecessary wake locks. Brightness, charging practice, display burn-in mitigation, and whether screen position should shift are proposed design questions. Do not claim the old phone can run safely and reliably all day until observed.
