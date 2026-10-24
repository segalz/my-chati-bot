# Plan: emotional eyes (stages E0–E9)

Owner decisions 2026-10-24: requirement R1 changes from "the original Monochrome Blue eyes" to **an expressive-eyes app, the base for an emotional conversation with an AI**; all ideas in [catalog.md](catalog.md) are accepted; Claude orders them. Each stage follows the protocol and gates in [testing.md](testing.md) and the working rules in [INDEX.md](INDEX.md).

## Orchestration
- **Coder**: chosen per micro-step with model-picker (`pick`, category `coding`/`ui`, `needs_write`). Current pick (2026-10-24): **Codex** (`codex_ask`, `sandbox="workspace-write"`, enforced OS sandbox), fallback **AGY** (`antigravity_ask`, write mode; unsandboxed, so only on a work branch, never `main`, with the exact files it may touch). Codex needs the owner-authorization block naming it the implementer.
- **Claude** writes the brief (files, interfaces, tests to add, what not to touch), integrates, and keeps the `[HARD]` parts (native renderer threading, model prompt/grammar, timing) when delegation is not a good fit.
- **Reviewers (owner, 2026-10-24): two independent reviews for every change, neither by the author.**

  | Author | Reviewer 1 | Reviewer 2 |
  |---|---|---|
  | Codex | Claude | opencode |
  | AGY | Claude | opencode |
  | Claude | Codex (read-only) | opencode |
  | Claude + Codex (mixed) | opencode | Cline (read-only review prompt) |

  - **A reviewer must have written none of the reviewed scope.** If a listed reviewer is unavailable, the next one from this order that wrote none of it takes its place: opencode → Cline → Codex (read-only) → AGY (read-only prompt) → Grok via `xai` (read-only), or Claude when Claude wrote none of it. If two such reviewers cannot be found, the stage waits.
  - opencode = `opencodeHelper.analyze_code` (read-only; `list_free_models` with `probe=true` first, then a responsive free model). The owner's global instructions state: "OpenCode Helper (`opencodeHelper` MCP) is allowed for read-only code analysis/review", so this is within policy; **Cline** (`ask_cline_workspace`, read-only, disposable worktree) stays the first route for code search/analysis (writing briefs, tracing a finding).
  - Each change and the **final integrated diff of the stage** get both reviews. A blocking finding is fixed (and re-reviewed) or explicitly answered before the owner check; the report lists reviewers, findings and fixes.
  - Before a delegated write: check the backend with model-picker `backend_status`; the delegate gets the exact files it may change and works in the current work branch only (AGY never on `main`); Claude checks `git diff` afterwards and never reports a delegate's claim as verified. Every delegated run is logged with `log_run`.
- **Verification**: Claude runs the builds, unit tests and the phone measurements itself; a delegate's claims are never reported as verified.
- **Version control** stays with Claude and the owner: delegates never commit, push, switch or reset.

## Few installs (owner, 2026-10-24)
MIUI blocks `adb install`, so every APK is installed by hand by the owner. The plan keeps that to about **7–8 installs in total** (not one per emotion):
- **Behaviour in files, not code.** Colors, effect switches, voice styles and the eye model's **prompt bundles** (labels, prompt, grammar, mappings) are read from the app's private files at start (the packaged assets are only defaults). A new emotion's prompt, a color or a switch is tried by pushing a file over the debug channel (`run-as`, as with the models) and restarting the app — no install.
- **Drawings built in batches, switched off.** Shapes, effects and motions need code (one APK), so each APK carries a batch of them, all **off** by default. They are then switched on **one at a time**, measured and shown to the owner, so the rule "one change at a time" holds for testing while the APK count stays small.
- **A rejected item stays off** (or is removed in the next APK of the plan); it never forces an extra install by itself.
- **Delivery vs acceptance.** An APK may carry code for several features (or two stages, as in install 4), but features are **accepted one after another**: each is switched on, measured, checked by the owner and recorded; a stage is tagged only when all its features are accepted or explicitly dropped. A tag marks an accepted **state** = a commit plus a configuration manifest (below), so "the previous stage" always exists even when its code shipped early.
- **Every batch APK before installation**: two independent reviews of the whole batch diff (reviewer table above), then on the phone an **all-new-features-off regression** that must equal the previous accepted state, then each feature's enabled-path tests, then the **cumulative combination** of all accepted features (functional checks, not only rendering).
- **Configuration safety** (applies to every pushed file):
  - debug builds only: files reach the private storage only through `run-as` and are applied over the debug receiver, which exists only in debuggable builds;
  - a change is **staged** in `files/config/staging/`, the whole set is validated together (schema version, minimum app version, sizes: each file ≤ 64 KB, ≤ 32 labels, prompt ≤ 2 000 tokens, every label mapped to a color/voice/shape, grammar reaches every label), then **activated atomically** (one pointer file swapped) — a partial or invalid set is never used;
  - the previous set is kept as **last-known-good**; any load error at start falls back to it automatically and logs why;
  - tests cover malformed, oversized, partial, unknown-version and incompatible sets.
- **Configuration manifest**: every activation writes a manifest (APK SHA-256, bundle version, every file's SHA-256, every switch's state); it is archived in `plan/emotions/evidence/config/` and restored as a whole; the rollback rehearsal in E0 includes restoring a manifest after a restart.

| Install | APK carries |
|---|---|
| 1 | E0: switches, regression hooks, instrumentation, prompt bundles from files, shape framework, decided color experiment |
| 2 | E1: colors.txt, intensity, thinking, sleeping, × × |
| 3 | E2: heart drawer + pulse (LOVE itself arrives as a prompt-bundle file) |
| 4 | E3 + E4: every effect and motion, off |
| 5–6 | E5–E7: every new emotion's drawing (arcs, tears, cheeks, stars, spiral, dollar, …), off; their labels arrive as prompt-bundle files, one at a time |
| 7 | E8: halo and shape morph |

One or two more are expected if a measurement or review finds a code bug.

## E0 — Baseline and safety net
Make every later step measurable and reversible before adding anything.
- [ ] E0.1 Decide the eye-color experiment (APK 0.9.4, uncommitted): owner keeps it → commit it as the base; or revert. The baseline is recorded **after** this decision and **before** any renderer framework change.
- [ ] E0.2 Runtime switches: `effects.txt` in the app's private files (packaged asset = defaults), changed and persisted over the debug channel (`DEBUG_LOOK --es effects name=on|off`), read by a small `Effects` object; unknown names rejected; tests.
- [ ] E0.3 One regression command: `tools/model-bench/regression.py` runs every row of [testing.md](testing.md) (accuracy per set, latency ×3, voice EN/HE cold+warm, smoke with speech, memory, frame-time tail) and prints the gate table against the stored baseline.
- [ ] E0.4 Instrumentation: frame-time tail and memory peak in the renderer stats (p99, max frame, frames > 50 ms); an `AudioTrack.getTimestamp()` probe for the real audio presentation time.
- [ ] E0.5 Record the frozen baseline (3 runs for timing), archive the APK and its manifest; rehearse one rollback (reinstall the previous APK, re-measure).
- [ ] E0.6 `[HARD]` Shape framework in the renderer: our own drawers beside the original (same `EyeDrawer` interface, same engine state), selected between frames like the color swap; the original drawer stays the default. A `SetShape{name}` command. Measured against the baseline (must be identical with no shape in use).
- [ ] E0.7 Prompt-bundle mechanism (versioned prompt + grammar + labels + first-token map + voice/color/shape mappings; previous bundles selectable). Bundles are **files** in the app's private storage (`files/bundles/v<N>/`, packaged v4 as the default), validated on load (unknown labels, grammar that does not reach every label, missing mappings → rejected, the previous bundle stays), so a new label is tried without an install. A switch is a **new request generation**: in-flight inference is cancelled, queued and playing audio stopped, pending and active reactions/effects/shapes cleared back to idle, the KV cache rebuilt; then the new bundle answers. Tested by switching **during** a spoken story: nothing from the old bundle (label, voice style, color, effect) appears afterwards. Bundle v4 = today's prompt, so nothing changes yet.
- Gate: every baseline number unchanged; owner check: nothing looks different. Tag `emotions-E0`.

## E1 — App-level life (no model change): #15, #13, #9, #10, #17
- [ ] E1.1 #15 `assets/colors.txt` (state + emotion → color, tint amount, fade); parser with unit tests; replaces the constants of the color experiment.
- [ ] E1.2 #13 Intensity: the native side already returns the label's logit margin; map it to a 0..1 strength (a ranking signal, not a probability); stronger = stronger tint (and later bigger expression). Unit tests; thresholds chosen on dev sentences only and **recalibrated whenever the label set changes**.
- [ ] E1.3 #9 Thinking: when the eye model is busy for more than 300 ms, the eyes look up to one side (a glance, never a reaction, never delays one).
- [ ] E1.4 #10 Sleeping: after 2 minutes of quiet the eyes close to lines (SLEEPY + a "closed" shape) with floating "Zzz"; any sentence/line wakes them at once (a blink, then the reaction).
- [ ] E1.5 #17 × × eyes for a second on a voice/model/cloud failure.
- Gate: accuracy unchanged (no model change), timing unchanged (thinking/sleep never delay a reaction), fps/memory within limits; owner check of each look.

## E2 — First new emotion: #1 Love (+ #35 pulse)
- [ ] E2.1 Test sentences: 10 love sentences (5 dev / 5 holdout), 10 near-misses (thankful/happy/excited that must stay HAPPY; negation "I don't love it"; sarcasm "I just love Mondays"; keyword traps), 2 love stories.
- [ ] E2.2 Tokenizer and grammar checks for LOVE (first token vs the existing labels, multi-token decoding, grammar reachability); cold and warm latency of the new bundle.
- [ ] E2.3 `[HARD]` Prompt bundle v5 (a file) with the single new label LOVE; bundle v4 stays selectable.
- [ ] E2.4 Heart drawer (red hearts, follows blink/look/size from the engine) + #35 pulse; LOVE → heart shape + red; voice style for LOVE in `voice.txt`.
- [ ] E2.5 Measure every row of [testing.md](testing.md), including the confusion matrix and the cumulative sets.
- Gate: no right→wrong flip on the existing sets, LOVE and near-miss floors met, timing within baseline spread; owner check. If the existing sets drop: tune on dev only, or make LOVE rule-based (keywords) instead of a model label — owner decides.

## E3 — Effects layer (no model change): #25 sweat, #26 anger mark, #27 sparkles, #28 ?/!, #29 notes, #30 rain cloud
- [ ] E3.1 `[HARD]` Overlay layer drawn after the eyes on the same canvas (render thread), simple shapes, each with its own lifetime/animation.
- [ ] E3.2 Each effect bound to an existing label/state + strength (#13), each switchable in `effects.txt`. Effects meant for labels that do not exist yet (sweat for SHY, ? for CONFUSED) get a **temporary trigger** from existing labels (sweat: SURPRISED after SAD; ?: SURPRISED with low strength) until their label arrives in E5/E6, then move to it.
- Gate: the worst-case frame run (all effects + voice + inference) meets the frame-time tail gate; timing within baseline; owner check per effect.

## E4 — Motions (no model change): #31 eye roll, #32 shifty glance, #33 double blink, #34 tremble, #12 strong anger, #24 wink
- [ ] E4.1 Motion scripts as short sequences of look/blink/offset commands on the controller thread (deterministic, cancelled by any reaction).
- [ ] E4.2 Bindings to labels/strength/rules; switches.
- Gate: as E3.

## E5 — New emotions batch 1: #2 laugh, #3 cry, #5 shy
- [ ] E5.1 One APK with the drawings for LAUGH, CRY and SHY (off). Then LAUGH alone (sets, tokenizer checks, bundle file v6, switch on its drawing, measurements, owner check), then CRY, then SHY — **one label per bundle**, each passing the E2 gates before the next is added.
- Gate: as E2 for each label; if one label hurts accuracy it is dropped or made rule-based, not forced.

## E6 — New emotions batch 2: #4 fear, #6 excited (stars), #7 confused (+ #19 spiral), #8 pleading
- Same method as E5: drawings in one APK (off), then one label per bundle file, cumulative sets, confusion matrix.

## E7 — Shapes and nuance: #18 dollar, #20 skeptical (+ #21 squint), #22 huge eyes, #23 one eye open
- #22/#23 are app-level (strength / pre-sleep); #18 starts rule-based (money words); #20 is a model label (same gates).

## E8 — Polish: #14 halo, #16 shape morph
- Halo around the eyes (render cost measured), morph transition between any two shapes.

## E9 — Later, blocked by other work
- #11 listening (needs speech input, P3), #36 follow the user (needs the front camera, P4).

## Risks
- **Label crowding**: each new label can confuse a 1B model. Mitigation: 2–4 labels per stage, dev/holdout gates, rule-based fallback, owner decides.
- **Prompt length → latency**: the system prompt is cached, but a cold start and every bundle switch re-decode it, and more labels can need extra decode steps (shared first tokens) — cold and warm latency measured each stage.
- **Render cost**: custom drawers/effects on the CPU canvas — frame-time tail, allocations and temperature measured each stage, worst case included.
- **Memory**: already 1.69 GB with both models and the voice; absolute ceiling 1.8 GB in [testing.md](testing.md).
