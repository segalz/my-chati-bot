# Sources and Planning Provenance

[Index](INDEX.md)

Planning date: 2026-10-22. This is a requirements and engineering plan, not a completed feasibility study.

## Conversation evidence

Owner's referenced conversation: `6ad9b87d-e494-83ed-8513-c13535c4a81d`, titled `חיבור AI למחשב`. Retrieved available turns describe on-phone models, Android development with a USB-connected physical phone using Codex/Claude rather than relying only on emulators, and multiple English planning files with an index in the chosen folder.

Some prior assistant content is represented only by unresolved content-reference markers. Those missing passages were not reconstructed or treated as evidence. The owner's detailed current request supplies the explicit eyes-library/version, architecture, privacy, API, and documentation requirements. Follow-up requests reiterate the eyes project, connected-phone instructions, and multiple documents.

The planning folder was empty when inspected; parent contained only `plan` and was not a Git repository. No source code was present to analyze. No code implementation, device inventory, APK build/install, model execution, provider test, or renderer audit was performed.

## Original eyes sources

| Source | What is established in this planning run |
|---|---|
| [Requested v1.3.9 README](https://components.espressif.com/components/espp/expressive_eyes/versions/1.3.9/readme) | Owner's exact target; browser retrieval unsuccessful. Contents/version availability remain unverified |
| [Upstream component path](https://github.com/esp-cpp/espp/tree/main/components/expressive_eyes) | Owner-supplied upstream location; direct browser retrieval unsuccessful; main is not an immutable v1.3.9 pin |
| [Repository landing page](https://github.com/esp-cpp/espp) | Accessible; presents the project as ESP C++ components and labels the repository MIT. Does not establish every renderer/dependency license |
| [v1.3.7 registry README](https://components.espressif.com/components/espp/expressive_eyes/versions/1.3.7/readme?language=en) | Accessible older documentation describes callback drawing, expression animation, and an example monochrome blue drawer. Context only, not an authorized version replacement |

The older README supports investigating the blue drawer, not asserting the concrete Android dependency graph. Required next evidence is the exact target archive/commit and associated source/license audit described in 04.

## Official platform references

- [Android ADB](https://developer.android.com/tools/adb): computer/device communication, install/debug commands, device selection. Connected-phone guide uses illustrative commands, not execution results.
- [Run apps on a hardware device](https://developer.android.com/studio/run/device): USB debugging and real-device development.
- [Android JNI tips](https://developer.android.com/ndk/guides/jni-tips): managed/native interoperability and keeping the JNI boundary small.
- [Foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types): microphone/camera permissions and while-in-use restrictions. Actual phone/API/target behavior still needs testing.
- [OpenAI billing separation](https://help.openai.com/en/articles/9039756-managing-billing-for-chatgpt-and-the-api-platform): API billing is separate from ChatGPT subscriptions. No provider was selected.

## Candidate technology references

- [llama.cpp](https://github.com/ggml-org/llama.cpp): candidate native inference foundation; no compatible model/phone combination established.
- [ONNX Runtime Mobile](https://onnxruntime.ai/docs/get-started/with-mobile.html): candidate mobile inference runtime.
- [LiteRT](https://developers.google.com/edge/litert): candidate on-device ML runtime.
- [Google AI Edge](https://ai.google.dev/edge): further mobile-model/runtime research.
- [scrcpy](https://github.com/Genymobile/scrcpy): optional phone display/control reference, not a requirement or verified installed tool.
- [Ollama documentation](https://docs.ollama.com/): optional future LAN/backend research; API capabilities and deployment not verified in this task.

Source contents and SDK/model versions can change. Pin exact source/model/runtime versions during implementation and record verification dates. Treat all candidate support, license, and performance assertions as research until validated for the actual selected components and phone.
