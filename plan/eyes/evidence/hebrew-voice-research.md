# Stage 9 research: Hebrew voice on the phone (2026-10-23)

Read with `gh`, the Hugging Face API, and `adb` (read-only); nothing was downloaded.

| Option | What | License | Size | Fit |
|---|---|---|---|---|
| sherpa-onnx `tts-models` | no Hebrew voice in the release (searched he_IL / hebrew) | — | — | none |
| `phonikud/phonikud-tts` voices (Piper-style, SASPEECH) | Hebrew neural voices | **CC BY-NC 4.0 + "academic research and educational use only"** | ~60 MB ONNX each | not allowed for a personal robot |
| `phonikud/phonikud` (G2P code) | Hebrew text → IPA | CC BY 4.0 | code | usable |
| `Phonikud/phonikud-onnx` (diacritizer) | adds nikud | MIT | **294 MB** (int8 too) | heavy next to the eye model |
| `renikud/renikud` (G2P, IEEE SLT 2026) | unvocalized Hebrew → IPA, ONNX | CC BY 4.0 | ~20 MB | usable front end for an own voice |
| Google TTS on the phone | `com.google.android.tts` 20260914, default engine, updated 2026-10-19 | system service | voice data managed by Google TTS | ready; offline only if the Hebrew voice data is installed; rate/pitch only |
| Cloud (ElevenLabs v3, Gemini, OpenAI) | expressive voices; Hebrew quality untested | paid API | — | best emotion; needs internet, key, cost |

Conclusion: today the only ready on-phone Hebrew voice without a license problem is Android's own Google TTS. An own open Hebrew neural voice would mean training one (ReNikud + a permissively licensed Hebrew speech dataset).
