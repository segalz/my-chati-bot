# Testing and gates

Every stage must pass these before the owner is asked to decide. Everything is measured on the physical phone (Redmi 10) with the stage's APK; tools are in `tools/model-bench/`. Revised 2026-10-24 after Codex's plan review (8 findings).

## Principle: no regression, measured the same way every time
The owner's rule is "never lower accuracy, never slow the timing". So gates compare against a **frozen baseline**, never against a fixed allowance:
- **Frozen baseline**: E0 records the approved build (APK file kept, full SHA-256, git commit, model file hash, prompt version, settings) and its measurements. Every later stage is compared with the stage before it **and** with this baseline.
- **Accuracy is deterministic** (greedy decoding, temperature 0): the same build gives the same answers. Gate: **no sentence that was right becomes wrong** in any existing set (dev and holdout checked **separately**), unless the owner explicitly accepts a listed trade.
- **Timing has run-to-run noise**, so it is compared like this:
  - one **run** = one pass of a timing set; it yields p50, p90 and p99 (nearest-rank) of its samples, in milliseconds;
  - the **baseline** = 3 runs; for each metric its **band** is [min, max] of the 3 run-level values;
  - a **stage** = 3 runs; for each metric the stage value is the **median of its 3 run-level values**;
  - **pass** when every stage value ≤ the band's max, compared with the frozen E0 baseline **and** with the previous stage. Any relaxation is an explicit owner decision, written in progress.md.
  - **Start-up** (3 runs each, from `am force-stop` + launch, timestamps from logcat): **launch → ready** (both log lines "warm-up done" and "Hebrew voice warm-up done") and **launch → first audible output** for a line sent right at launch (`speech: 1/n start`).
  - **First request after readiness**: launch, wait for both warm-up lines, then send the set's first item; 3 runs. **Warm** samples: the same items after one throw-away item.
  - Start-up, first-request and warm values are each compared with E0 and with the previous stage by the rule above.
  - **Audible output**: `speech: 1/n start` is logged when the AudioTrack **playback head** reaches the sentence's first sample (both voices play through the same `AudioOut`), a software proxy. E0 adds an `AudioTrack.getTimestamp()` probe that logs the presented frame time, and measures the proxy-to-presentation offset for Piper (English) and the phone's Google TTS (Hebrew); the offset is recorded in the baseline and re-checked each stage (gate: within the baseline band), so buffering added downstream cannot hide.

## Measurements

| Area | How | Gate |
|---|---|---|
| Single sentences | 100 English sentences (`model-testset-en.jsonl`, 40 dev + 60 holdout), `stage6_eval.py` | dev and holdout each ≥ baseline (88 = 37 + 51), no right→wrong flips |
| Stories | 10 stories (`story-testset.jsonl`), `stage6c_story_eval.py` — also after app-level/controller changes | dev and holdout each ≥ baseline (55 = 30 + 25); total expression changes ≤ baseline; shortest display ≥ baseline − 200 ms (timer granularity) |
| New labels (E2, E5–E7) | per label, every item with its expected label and its set: 10 sentences (5 dev / 5 holdout), 10 near-misses incl. **negation, sarcasm and keyword traps** ("I don't love it", "I just love Mondays") (5 dev / 5 holdout), 2 stories (1 dev / 1 holdout) labeled per sentence; all earlier new labels' sets re-run (cumulative) | label: dev ≥ 4/5 and holdout ≥ 4/5; near-misses: dev ≥ 4/5 and holdout ≥ 4/5 (≥ 9/10 together); stories: per story ≥ its sentence count − 1 shown correctly mid-sentence; cumulative sets: no right→wrong flips; a **confusion matrix** of all labels shows no new confusions on existing sets |
| Eye-model latency | the 100 sentences ×3, warm; plus the first sentence after a cold start | p50/p90/p99 within baseline spread; cold first sentence not slower |
| Voice, English (Piper) | spoken 3- and 7-sentence lines ×3, cold and warm | first audio, gaps between sentences, eyes-to-audio offset (≤ 10 ms), cancellation by a newer line — all within baseline |
| Voice, Hebrew (phone Google TTS, default) | same, cold (after restart) and warm, plus language switch EN↔HE | same |
| Frame rate | 30-min smoke with speech (`stage7_smoke.py` + `DEBUG_SPEAK`), plus a 5-min **worst-case** run (all overlays/morphs + voice + inference at once) | ≥ 59 fps average; **frame-time p99 ≤ 25 ms and no frame > 50 ms** (renderer stats, E0.4); avg work ≤ baseline band max; battery temperature at the end (`dumpsys battery`, phone on USB power, same room) ≤ baseline + 1.0 °C |
| Memory | VmRSS sampled every 5 min during the smoke (`/proc/<pid>/status`), peak = VmHWM; recovery after `onTrimMemory` (release, then speak again) | **absolute ceiling 1.8 GB** for VmHWM (baseline ≈ 1.69 GB); minute-30 RSS − minute-5 RSS ≤ 20 MB; after release and reload the next line works |
| Unit tests | `gradlew :app:testDebugUnitTest` | all pass; new pure code has tests |

Before any new label: tokenizer checks — every label's first token is unique or the classifier can still separate it (multi-token labels decoded), the grammar reaches every label, and `first_tokens`/`first_leads_to_none` are recomputed and logged. Logit margins (#13) are recalibrated whenever the label set changes (they are a ranking signal, not a probability).

## Per-stage protocol
1. **Unit tests** for every new pure piece.
2. **Two independent reviews, never by the author** (see plan.md › Orchestration), on each change **and on the final integrated diff** of the stage; blocking findings closed before step 3.
3. **Regression run** on the phone: the whole table (accuracy rows run every stage — they are cheap and catch controller/story side effects).
4. **Visual check**: screenshots/video per new look, color sampling (`color_check.py`).
5. **Owner check** on the phone: keep / change / drop.
6. **Commit + tag** `emotions-E<n>` only after the owner's yes; the tag marks the accepted state (commit + configuration manifest); the APK file is archived (`plan/emotions/evidence/apk/`, git-ignored).

For a **batch APK** (several features shipped off): before installing, two reviews of the whole batch diff; after installing, (a) all new features off = the previous accepted state on every row above, (b) each feature on alone with its own checks, (c) all accepted features on together.

## Rollback
- **Runtime switches** (from E0): `effects.txt` in the app's **private files** (editable over the debug channel and persisted across restarts; the packaged asset is only the default), so a feature can be turned off without a rebuild. Switch scope: per feature; off = the previous behaviour exactly (checked by re-running the stage's tests with everything off).
- **Configuration**: every activated set (bundle, colors, switches, voice) has a manifest (APK SHA-256, bundle version, each file's SHA-256, each switch's state) archived under `plan/emotions/evidence/config/`; rolling back = restoring a whole manifest atomically (never single files), then a restart and the stage's checks. Invalid or partial sets are rejected and the last-known-good set stays active (plan.md › Few installs).
- **Model prompt bundles**: each prompt version is a complete bundle (system prompt, grammar, label list, first-token map, voice/color/shape mappings) with a version number; the previous bundles stay in the app and are selectable, the KV cache is rebuilt on a switch, and pending intents are dropped.
- **Code**: `git revert` of the stage's commit, or rebuild from the previous tag. The archived APK of every stage can be reinstalled directly (same signing key: the debug key is kept). App data stays compatible (new settings files have defaults; the model files do not change).
- **Rehearsal**: E0 rehearses one rollback (install the previous APK, verify the baseline numbers) so the path is known to work.
