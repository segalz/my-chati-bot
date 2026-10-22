# Local AI Candidates and Benchmarks

[Index](INDEX.md)

## Requirement and selection boundary

A small model must execute on the Android phone to select expressions and coordinate eye intentions. It should consume compact local features and produce bounded intent, not frames. No central LLM token spending is needed for local idle/eye control. Do not pick a large conversational model just because it is available.

No model, quantization, runtime, minimum RAM, or supported Android version is fixed. Hardware inventory precedes selection. Model weights and runtime software have separate licenses and compatibility requirements.

## Candidate approaches — not selections

| Approach | Candidate tooling | Questions to validate |
|---|---|---|
| Small classifier/policy network over structured features | [LiteRT](https://developers.google.com/edge/litert), [ONNX Runtime Mobile](https://onnxruntime.ai/docs/get-started/with-mobile.html) | Are suitable weights/training data available? Does it express the desired autonomous behavior? |
| Compact quantized instruction model producing constrained intents | [llama.cpp](https://github.com/ggml-org/llama.cpp) | Does the exact model fit available RAM and ABI, respect schema, and stay responsive under heat? |
| Mobile-oriented small model through an appropriate edge runtime | [Google AI Edge](https://ai.google.dev/edge) ecosystem | Is that specific runtime/model combination supported on this old phone and OS? |
| Deterministic policy | Native/Kotlin logic | Essential baseline/fallback, but does not satisfy the local-model acceptance gate |

Candidate weight families to investigate include small Qwen, SmolLM, and Gemma variants, if their then-current licenses, language coverage, formats, and device requirements fit. These are research leads, not claims that any specific release will run. Start with the smallest task-suitable option; record exact model card and revision before downloading or adopting weights.

## Evaluation protocol

1. Inventory total/free RAM, available storage, CPU/ABI, OS, graphics support, and thermal behavior.
2. Reserve resources for animation, wake detection, speech, and optional vision before assigning a model memory budget.
3. Run a no-model deterministic baseline, then each candidate against the same recorded local-feature scenarios.
4. Measure cold load, resident/peak memory, warm p50/p95 intent latency, valid-schema rate, event accuracy, frame pacing impact, energy/temperature, and recovery after memory pressure.
5. Include long idle sessions, simultaneous TTS/vision, malformed/noisy context, permission loss, sleep during inference, and offline launch.
6. Choose the smallest option meeting agreed behavior and sustained-device budgets. Record rejected candidates and reasons.

Proposed inference is event-driven with cooldowns and short context. Exact cadence and latency thresholds remain TBD after the baseline. Constrain output where supported and always validate it independently. Avoid a free-running agent loop.

## LAN option

Ollama on another machine may optionally handle conversation. It is neither on-device inference nor proof the local eye model works. If the phone cannot run a suitable model, report that capability gap and ask about a revised product scope; retain deterministic eyes as an explicitly degraded mode.
