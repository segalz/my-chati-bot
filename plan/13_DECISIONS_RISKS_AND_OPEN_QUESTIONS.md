# Decisions, Risks, and Open Questions

[Index](INDEX.md)

## Confirmed owner intent

| ID | Decision / requirement |
|---|---|
| D01 | Repurpose an old Android phone as a charming desk companion with expressive blue eyes |
| D02 | Target original `espp/expressive_eyes` v1.3.9 C++; prefer NDK/JNI port and original blue renderer/dependencies over reimplementation |
| D03 | Local on-device small AI selects expressions and autonomous eye intentions; deterministic animation renders frames |
| D04 | Central configurable AI handles conversation; avoid central LLM token costs for ordinary eye control |
| D05 | Local wake word, sleep/wake commands, speech input/output, optional visual awareness and contextual sight |
| D06 | Front and rear camera support with clear controls; no capture without permission |
| D07 | Kotlin/native Android preferred; physical phone over USB ADB for Codex/Claude development; emulator supplementary; scrcpy optional |
| D08 | Hardware inventory before stack/model commitments; API provider and optional Ollama backend remain configurable |
| D09 | English planning documents in multiple named Markdown files with an index; no implementation in this task |

## Proposed engineering decisions — not settled product choices

Foreground desk mode first; narrow JNI; event-driven model inference; bounded intent contract; deterministic fallback; camera off initially; transient media by default; separate upload authorization; half-duplex voice first if needed. Each proposal can change with evidence and owner feedback while preserving confirmed intent.

## Risk register

| Risk | Impact | Validation / response |
|---|---|---|
| Exact v1.3.9 cannot be retrieved | Blocks verified original-source port | **Closed 2026-10-22**: exact v1.3.9 obtained and hashed |
| Blue drawer dependencies are nonportable or differently licensed | Scope/cost/legal obligations change | **Closed 2026-10-23**: ported unmodified (LVGL, fmt); licenses in `third_party/LICENSES` |
| Phone has insufficient RAM/ABI/OS support | Local model or toolkit unavailable | Inventory and smallest-model benchmarks; report gap. **2026-10-23: OPEN, watch.** Eyes + eye model + voice use 1.69 GB RSS (voice adds ~0.7 GB, more than expected); speech input will need room |
| Sustained inference harms animation or overheats device | Poor desk experience | Isolated scheduling, reduced cadence, soak measurements. **2026-10-23: validated** — 60 fps and no heating over 30 min with a sentence every 15 s ([stage7-smoke](eyes/evidence/stage7-smoke.md)) |
| Small model misreads mood in longer text | Wrong or jumpy reactions in stories | **2026-10-23: partly mitigated** — deterministic mood rules (linger/carry/dwell) give 55/70; giving the 1B model the earlier sentences made it worse (48/70), so it stays off |
| Background mic/camera restrictions | Always-on modes may be unsupported | Actual OS/target lifecycle matrix; foreground MVP |
| Wake-word accuracy or TTS feedback | Repeated false wakes | Noise/voice tests and audio coordination |
| Approximate gaze unreliable | Twitching or misleading attention | Confidence gates, smoothing, head-direction fallback |
| Provider lacks assumed API features | Voice/image path fails | Capability-specific adapter tests |
| Old Android security/transport limitations | Online operation may be unsuitable | Verify TLS/Keystore and endpoint support; minimize data |
| Privacy state races with inference/network work | Unwanted capture/upload | Central policy checks, cancellation, stale-session rejection |

## TBD / Ask owner

| Question | Needed by | Blocks planning now? |
|---|---|---|
| Actual phone model/RAM/OS and authorized USB connection | P0 inventory | No; discover directly first |
| Wake phrase, sleep/wake command wording, spoken languages | P3 | No. Languages answered 2026-10-23: English voice first, Hebrew later; eye-model input English only (R12) |
| Must it work while locked/screen-off, or is visible desk mode enough initially? | Final lifecycle scope | No |
| Does sleep retain local wake listening, or fully mute? | Voice UX finalization | No; keep explicit modes |
| Personal use only or distribution to others? | Credentials/release design | No. 2026-10-23: owner accepts GPL-3.0 (espeak-ng in the voice) — a distributed app must then ship its source |
| Conversation provider/model, budget, and credentials | Live online tests | No |
| Ollama host and desired role, if any | Optional LAN tests | No |
| Camera awareness schedule and rear-camera purpose | P4/P5 | No |
| One-time/per-query cloud-image approval preference | Cloud vision UX | No |
| Personality, eye layout, orientation, brightness, sounds | Visual tuning | No |
| History/memory retention and deletion expectations | Persistence features | No |
| Acceptable alternative if v1.3.9 or original renderer is unavailable | Only after evidence of blocker | No |

No essential unresolved product decision prevented this document set. Ask only the questions needed for the next implementation gate; do not invent answers or ask the owner for hardware facts that can be safely discovered.

## Decision log (eyes and voice, 2026-10-22/23)

Owner = zvi segal. Evidence lives under `plan/eyes/evidence/`.

| Date | Decision | Status | Evidence / alternatives |
|---|---|---|---|
| 2026-10-22 | Eye model input is English only; translation happens at the source | Accepted (owner) | R12 in [eyes/plan.md](eyes/plan.md); Hebrew dropped from evaluation |
| 2026-10-22 | Eye model: Gemma 3 1B it Q4_K_M in-app via llama.cpp, 6 threads | Accepted (owner) | [model-benchmark](eyes/evidence/model-benchmark.md); Qwen/LFM/Q4_0 measured |
| 2026-10-23 | Q4_0 (0.47 s) not adopted: 84–86/100 < the agreed 87 bar; Q4_K_M stays (88/100, ~1 s) | Validated | same; calibration chosen on dev only |
| 2026-10-23 | Stories: deterministic mood rules in the controller (linger 1.5 s, carry 6 s, dwell 1.5 s); model context off | Validated + accepted (owner: pacing good) | [stage6c](eyes/evidence/stage6c/); context A/B 48/70 vs 55/70 |
| 2026-10-23 | Voice: Piper libritts_r-medium via sherpa-onnx v1.13.8, on the phone, 2 threads; emotion = speed per expression (`voice.txt`) | Accepted (owner left the choice to Claude; owner: "really good") | [voice-benchmark](eyes/evidence/voice-benchmark.md); Kokoro (RTF 6), Kitten, Supertonic measured |
| 2026-10-23 | GPL-3.0 component (espeak-ng) accepted in the app | Accepted (owner) | [voice-shortlist](eyes/evidence/voice-shortlist.md) |
| 2026-10-23 | Hebrew voice: the phone's own Google TTS (offline Hebrew voice), speed+pitch per expression; eyes get the English version per sentence (R12) | Accepted (owner: "this is the solution") | [hebrew-voice-research](eyes/evidence/hebrew-voice-research.md); Gemini TTS sounded best but its free tier is 10 requests/day and the owner will not pay — code kept, off |

## Decision log procedure

For each future change add date, decision owner, evidence, chosen option, alternatives, and affected documents. Distinguish “requested,” “proposed,” “validated,” and “accepted.” Update this register and linked technical plan together so a later agent does not mistake an option for a commitment.
