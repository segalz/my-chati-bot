# On-phone model comparison — Stage 2B.4

Date: 2026-10-22. Phone: Redmi 10 (see [device-inventory.md](./device-inventory.md)). Runtime: llama.cpp `v0.7.0` (commit `620d222`), NDK r28c, arm64 `armv8.2-a+dotprod+fp16`, static, `llama-server` with `-c 1024 -t 4 --reasoning off --swa-full`, temperature 0. Models: Q4_K_M GGUF, SHA-256 verified against Hugging Face ([model-shortlist.md](./model-shortlist.md)). Test set: [model-testset.jsonl](./model-testset.jsonl). The main set has 40 English sentences (28 react incl. 5 gaze, 12 no-reaction); the robustness sets have 8 rough-translation English and 6 raw Hebrew sentences. Tools: `tools/model-bench/run_bench.py`, `summarize.py`. Raw results: `bench/qwen3.5-0.8b.json`, `bench/gemma-lfm.json` (prompt v1), `bench/v2-all.json` (prompt v2).

## Prompt versions

- **v1:** JSON schema with `react` first, numeric gaze, intensity, and hold time. All models were weak: `react` was decided before the emotion, numeric gaze signs were confused, and the output was long.
- **v2 (adopted):** expression decided first, `react`, gaze as a word (`center/left/right/up/down`), and 6 few-shot examples (not from the test set). A whitespace-free GBNF grammar keeps the output to ~15 tokens. Intensity and hold time move to the app controller (R2 still holds: bounded intents only).

## Results — prompt v2, main set (40)

| Model | Fully correct | React/no-react | Expression (28) | Gaze (5) | p50 latency | Gen speed | Peak RSS† | Load |
|---|---|---|---|---|---|---|---|---|
| **Gemma 3 1B it** | **33/40** | 35/40 | **25/28** | 4/5 | 2.8 s | 7.4 tok/s | 1373 MB | 4.8 s |
| **Qwen3.5-0.8B** | 32/40 | **38/40** | 20/28 | **5/5** | **2.4 s** | 9.3 tok/s | 1255 MB | 5.6 s |
| LFM2.5-350M | 14/40 | 29/40 | 13/28 | 5/5 | 4.9 s measured / ~1.1 s est.‡ | 27.4 tok/s | **517 MB** | 2.5 s |

Robustness (v2): Gemma robust 7/8 react, 6/6 expr; Hebrew 6/6 react, 5/5 expr. Qwen robust 8/8 react, 4/6 expr; Hebrew 6/6 react, 4/5 expr. LFM robust 6/8, 2/6; Hebrew 5/6, 2/5.

Prompt v1, fully correct on the main set: Qwen 21/40, Gemma 15/40, LFM 14/40. Prompt design mattered more than model choice for Qwen.

† Peak RSS (`VmHWM`) **includes the memory-mapped model file** (Gemma 806 MB, Qwen 533 MB, LFM 229 MB). That part is file-backed and reclaimable, so anonymous memory is roughly 570 MB (Gemma), 720 MB (Qwen), and 290 MB (LFM). Exact PSS/anon split was not captured. **Untested.**

‡ Prompt-prefix caching worked for Qwen and Gemma (median `cache_n` 297 tokens), so their latency is mostly generation. For LFM (hybrid conv architecture) llama-server could not reuse the cache (`cache_n` 0): ~4.3 s goes to re-reading the 336-token prompt every time. In our app the system-prompt state can be saved once and restored per sentence, giving an estimated ~1.1 s. That estimate is **unverified**.

Temperature: CPU 47 °C before and after each run (thermal status 0). Runs were short (~3–25 min per model).

## Error patterns (v2)

- **Gemma:** 4 of its 5 misses are `react:true` with `NEUTRAL` on no-reaction sentences. The eyes would barely change, so this is nearly harmless. Others: "boring lecture" → SAD; "look down at the floor" → no reaction; "hid the cookies" → SLEEPY.
- **Qwen:** "look left" → WINK_LEFT with the correct gaze (debatable); "huge spider behind you" → no reaction; "nobody came to my party" → NEUTRAL; "wow, did not expect" → HAPPY.
- **LFM:** strong SAD bias; not usable for this task at this size.

## Recommendation (for owner decision)

1. **Gemma 3 1B it — recommended.** It is the most accurate on emotions (25/28) and handled raw Hebrew best. Its only weak spot is a light over-reaction with `NEUTRAL`, which is visually harmless. Costs: slower (2.8 s), the most memory, and the **Gemma Terms of Use** license (use restrictions; fine for a personal project, needs review for distribution).
2. **Qwen3.5-0.8B — close alternative.** Best at the "react or not" decision (38/40) and gaze; faster, smaller, **Apache-2.0**. Weaker on expression nuance.
3. **LFM2.5-350M — rejected** for accuracy, despite its speed and size.

Both finalists are at **~2.5–3 s per sentence** on this phone. That is acceptable for reacting to a finished sentence, but not instant. Possible speedups to test in Stage 6: Q4_0 with ARM weight repacking, `-t 2` on the A75 cores, a shorter system prompt, and state save/restore.

## Speed work and model combination (2026-10-23, owner request)

The owner asked to work on speed first and not to rule out using both models together.

**Thread sweep** (`llama-bench -p 32 -n 16`, [bench/threads-sweep.md](./bench/threads-sweep.md)). Prompt processing scales to 8 threads (Qwen 41 → 63 tok/s, Gemma 19 → 29 tok/s from `-t 4` to `-t 8`); generation peaks at 6–8 threads (Qwen 12.1, Gemma 11.2 tok/s). Adopted: `-t 6 -tb 8`.

**Output formats** (all with `-t 6 -tb 8`):

| Variant | Qwen fully correct / p50 | Gemma fully correct / p50 |
|---|---|---|
| v2 JSON (`expression`,`react`,`look`) | 32/40 · 2.04 s | 33/40 · 2.31 s |
| v3 two words (`HAPPY center`) | **collapsed** (HAPPY for 52/54) · 0.95 s | 18/28 expr, react 28/40 · 1.18 s |
| **v4 JSON (`expression` incl. `NONE`, `look`)** | **32/40 · 1.61 s** | **33/40 · 1.90 s** (p95 3.9 s) |

The JSON keys act as scaffolding for these small models: a bare two-word answer is fast but inaccurate. **v4 is adopted**, saving ~20–30 % with no accuracy loss. Robustness on v4: Qwen 6/8 rough, 5/6 Hebrew; Gemma 6/8, 6/6.

**Combination: Qwen decides react + look, Gemma decides the expression** ([bench/combo-parallel.json](./bench/combo-parallel.json), `tools/model-bench/run_combo.py`). Both servers ran at the same time, with `-t 3 -tb 4` each:

| Setup | Main fully correct | Robust | Hebrew | p50 latency | Anonymous memory |
|---|---|---|---|---|---|
| Qwen alone (v4) | 32/40 | 6/8 | 5/6 | **1.6 s** | ~740 MB |
| Gemma alone (v4) | 33/40 | 6/8 | 6/6 | 1.9 s | **~560 MB** |
| **Combined, parallel** | **36/40** | **8/8** | **6/6** | 3.0 s | ~1.3 GB total (MemAvailable stayed 1.7 GB) |

Measured memory (`RssAnon`): Qwen 741 MB, Gemma 560 MB. Earlier peak-RSS numbers included the mapped model file. Both single models fit the ≤ 1.0 GiB budget; the combination exceeds it but left 1.7 GB available. Parallel running is CPU-bound: two models sharing 8 cores take ~3.0 s, versus a theoretical 1.9 s. **Caveat:** the combination rule was chosen after seeing this test set, so its 36/40 must be confirmed on new sentences before it is trusted.

**Not yet tried (next speed options):** Q4_0 weights with ARM online repacking (needs new downloads → owner approval); state save/restore of the system prompt inside the app (Stage 6); a cascade that runs Gemma only when Qwen says "react" (estimated ~3.5 s when reacting, ~1.6 s otherwise).

## Decision round — English only, Q4_0, one vs two models (2026-10-23)

Owner direction: the sending model translates, so the eye model receives **clean English only**; Hebrew and rough-translation sets are dropped as noise. Test Q4_0, decide one vs two models, and treat accuracy and speed as critical.

**Test set** [model-testset-en.jsonl](./model-testset-en.jsonl): **dev** = the 40 earlier English sentences; **holdout** = 60 new, unseen sentences (33 react, 7 gaze, 20 no-reaction). Rules are chosen on dev and confirmed on holdout. All runs use prompt v4 and `-t 6`, `-tb 8` (Qwen) / `-tb 6` (Gemma).

**Scoring rule "effective"** (adopted product semantics): `NEUTRAL` + `center` changes nothing on screen, so the app treats it as no reaction. This mostly helps Gemma, whose "react" errors are almost all `NEUTRAL center`.

**Raw speed** ([bench/quant-speed.md](./bench/quant-speed.md), `llama-bench`, 6 threads): Gemma QAT Q4_0 processes the prompt **2.7× faster** than Q4_K_M (69 vs 25 tok/s; generation 12.7 vs 10.6) thanks to ARM repacking. Qwen Q4_0 gains only ~5–10 %.

| Setup | dev (40) | holdout (60) | **Total /100** | p50 latency | Anon memory |
|---|---|---|---|---|---|
| **Gemma 3 1B Q4_K_M** | 38 | 51 | **89** | 2.06 s | **560 MB** |
| Gemma 3 1B Q4_K_M + answer-prefix prefill | — | — | 87 (94/100 identical decisions) | **1.76 s** | 560 MB |
| Gemma 3 1B Q4_0 (plain) | 34 | 47 | 81 | 1.49 s | 765 MB |
| Gemma 3 1B QAT Q4_0 | 33 | 45 | 78 | **1.36 s** | 778 MB |
| Qwen3.5-0.8B Q4_K_M | 32 | 48 | 80 | 1.63 s | 742 MB |
| Qwen3.5-0.8B Q4_0 | 34 | 45 | 79 | 1.45 s | 712 MB |
| Combo Q4_K_M (Qwen react+look, Gemma expr) | 36 | 52 | 88 | 3.72 s seq. / ~3.0 s parallel | 1302 MB |
| Combo Q4_0 (Qwen Q4_0 + Gemma QAT) | 32 | 49 | 81 | 2.83 s seq. | 1490 MB |

Q4_0 variants use more anonymous memory because repacked weights are copied out of the mapped file.

### Conclusions

1. **One model, not two.** On unseen sentences the best combination (88/100) does not beat Gemma Q4_K_M alone (89/100), and it costs ~1.8× the latency and ~2.3× the memory. The earlier 36/40 combination result was partly fitted to that set, and the holdout confirms there is no real gain. Combination is **rejected**.
2. **Model: Gemma 3 1B it Q4_K_M** — the most accurate (89/100) with the lowest memory (560 MB anon).
3. **Q4_0 is rejected for now.** It is 30–35 % faster but loses 8–11 points of accuracy (it over-reacts and confuses winks and sleepiness). It stays as a fallback if speed must win.
4. **Speed path for the app:**
   - Measured now: 2.06 s → **1.76 s** by feeding the answer prefix as prompt (prefill).
   - Remaining time: ~0.83 s to process the sentence and ~0.85 s to generate 8 tokens, of which ~5 are still grammar-forced JSON (`","look":"`, `"}`).
   - In our own app the forced tokens can be fed in batches instead of generated one by one. The estimate is **~1.2–1.4 s**, **unverified until Stage 6**.
   - The system-prompt state is already reused (prompt cache).
5. **Product rules adopted:** v4 prompt; `NONE` = no reaction; `NEUTRAL` + `center` = no change; the app sets intensity and hold time.

Remaining visible errors of Gemma Q4_K_M are mostly "secret / wink" sentences read as SAD or SURPRISED and a few neutral facts answered HAPPY. These can be improved with more few-shot examples, but must be re-validated on new sentences to avoid fitting the test set.

## Limits of this test

Test set of 100 English sentences labeled by one person (Claude), so the labels themselves may be debatable; a single run per model; short sessions (no sustained-thermal data); RSS includes mapped files; run with `llama-server`, not yet inside the app. Server logs of the first rounds were lost during phone cleanup (the 2026-10-23 logs are in `bench/server-logs/`) (a Git Bash path-rewrite bug in the pull command), but per-request timings are preserved in the JSON results. Phone cleanup confirmed: `/data/local/tmp/chati-bench` removed, no server running, the benchmark port forward removed. The models remain on the PC in `models/bench/` (~1.57 GB).
