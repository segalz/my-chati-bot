# Model shortlist — Stage 2B.1 (eye-intent model on the phone)

Date: 2026-10-22. Status: **proposed, awaiting owner approval to download** (R7). Nothing has been downloaded.

## Selection basis (from Stage 1 measurements)

Phone: Redmi 10, 2 × A75 + 6 × A55 with dot-product, ~5.6 GiB RAM, ~2.5 GiB available at idle. Provisional budget for the eye model: **≤ 1.0 GiB resident** (weights + KV cache + runtime). The job is small: read one sentence, output a short JSON intent (`react`, expression, intensity, gaze, hold). We need fast, reliable structured output and Hebrew + English understanding, not long-form chat.

Runtime for the comparison: **llama.cpp** (MIT), latest release `v0.7.0` (2026-10-15), built with NDK r28c for arm64-v8a, using a GBNF JSON grammar so every model emits the same constrained schema.

## Shortlist (all GGUF Q4_K_M)

| # | Model | Params | Q4_K_M file | Est. RAM in use* | License | Hebrew | Role in the test |
|---|---|---|---|---|---|---|---|
| A | **Qwen3.5-0.8B** (`Qwen/Qwen3.5-0.8B` @ `2fc06364`; GGUF `unsloth/Qwen3.5-0.8B-GGUF` @ `6ab46149`) | 0.87 B | **508 MB** | ~0.6–0.7 GB | **Apache-2.0** (no restrictions) | Card claims 201 languages; Hebrew not benchmarked by the vendor | **Primary candidate** |
| B | **Gemma 3 1B it** (`google/gemma-3-1b-it` @ `dcc83ea8`; GGUF `ggml-org/gemma-3-1b-it-GGUF` @ `f9c28bcd`) | 1.0 B | **769 MB** | ~0.9–1.0 GB | **Gemma Terms of Use** (use-restriction policy; the official repo is gated, the ggml-org GGUF is not, but the same terms apply) | Family claim "140+ languages"; not confirmed for the 1B size | Quality comparison at the top of the budget |
| C | **LFM2.5-350M** (`LiquidAI/LFM2.5-350M` @ `9e6c6ccf`; GGUF `LiquidAI/LFM2.5-350M-GGUF` @ `657e078c`) | 0.35 B | **219 MB** | ~0.3 GB | **LFM Open License v1.0** (free; commercial use limited to companies under a revenue threshold) | **Not listed** (EN, AR, ZH, FR, DE, JA, KO, PT, ES) | Speed floor: is a tiny model good enough? |

\* Estimate = file size + short-context KV cache (~512 tokens) + runtime overhead. **To be measured** on the phone (peak RSS) in 2B.4.

**Total download if approved: ~1.5 GB** (A + B + C), stored only in `/data/local/tmp/chati-bench/` on the phone during the test, then removed.

## Considered and not shortlisted

| Model | Reason |
|---|---|
| Gemma 4 E2B it (Apache-2.0, multimodal) | Even the smallest mobile GGUF (`UD-Q2_K_XL`) is 2.09 GB → over budget |
| Qwen3.5-2B (Apache-2.0) | Q4_K_M 1.22 GB → over the 1.0 GiB budget. Possible "quality ceiling" test only if the owner wants it |
| LFM2.5-1.2B-Instruct | 697 MB, but no Hebrew listed and restricted license; C already covers the LFM family |
| Llama-3.2-1B-Instruct | 770 MB, Hebrew not in supported languages, Llama 3.2 community license |
| SmolLM3-3B | Too large for the budget |

## Expected requirements and caveats

- Qwen3.5 has a "thinking" mode. For this task it must be disabled (no-think chat template), otherwise latency explodes.
- Hebrew quality is the biggest unknown for every candidate. The test set (2B.3) has a Hebrew half and **no-reaction** cases, so this is measured, not assumed.
- Model choice is not final until the in-app confirmation (Stage 6).

## Sources

Hugging Face model API and model cards (revisions above, fetched 2026-10-22); llama.cpp GitHub releases API. Secondary context: [HF blog — local models 2026](https://huggingface.co/blog/daya-shankar/open-source-llm-models-to-run-locally), [Best Small Language Models for Phones in 2026](https://www.llm-hub.app/blog/best-small-language-models-for-phones-2026).
