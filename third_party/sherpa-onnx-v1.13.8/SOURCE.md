# sherpa-onnx v1.13.8 (TTS runtime for Stage 8)

- Source: https://github.com/k2-fsa/sherpa-onnx, tag `v1.13.8` (2026-09-10), Apache-2.0 (`../LICENSES/sherpa-onnx-v1.13.8-LICENSE.txt`).
- `jniLibs/arm64-v8a/libsherpa-onnx-jni.so`: from the release asset `sherpa-onnx-v1.13.8-android-static-link-onnxruntime.tar.bz2`
  (SHA-256 `7583ca385ae7d981e65468455c2ea2c9f2da383921dccfc5658d0dc19d309e6f`, equal to the digest GitHub publishes).
  onnxruntime and libc++ are linked in statically; it needs only system libraries. It also contains espeak-ng
  (GPL-3.0, `../LICENSES/espeak-ng-COPYING.txt`) — owner accepted GPL-3.0 on 2026-10-23.
  Not committed (24 MB); fetch it with `tools/fetch_sherpa_onnx.ps1`, which checks the hash.
- `android/app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt`: `sherpa-onnx/kotlin-api/Tts.kt` at the same tag, unchanged
  (the JNI code reads its class and field names).
- Voice model (not in the APK): `vits-piper-en_US-libritts_r-medium` from the `tts-models` release
  (SHA-256 `10dc268f3e371696d721486123e2705a9fc1faa113491979fde4d88dba1f1b1c`), copied to the app's private
  `files/voice/` like the eye model. Dataset LibriTTS-R, CC BY 4.0.
