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
| Exact v1.3.9 cannot be retrieved | Blocks verified original-source port | Obtain immutable archive/commit; ask before substitution |
| Blue drawer dependencies are nonportable or differently licensed | Scope/cost/legal obligations change | Concrete renderer/dependency audit and minimal port spike |
| Phone has insufficient RAM/ABI/OS support | Local model or toolkit unavailable | Inventory and smallest-model benchmarks; report gap |
| Sustained inference harms animation or overheats device | Poor desk experience | Isolated scheduling, reduced cadence, soak measurements |
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
| Wake phrase, sleep/wake command wording, spoken languages | P3 | No |
| Must it work while locked/screen-off, or is visible desk mode enough initially? | Final lifecycle scope | No |
| Does sleep retain local wake listening, or fully mute? | Voice UX finalization | No; keep explicit modes |
| Personal use only or distribution to others? | Credentials/release design | No |
| Conversation provider/model, budget, and credentials | Live online tests | No |
| Ollama host and desired role, if any | Optional LAN tests | No |
| Camera awareness schedule and rear-camera purpose | P4/P5 | No |
| One-time/per-query cloud-image approval preference | Cloud vision UX | No |
| Personality, eye layout, orientation, brightness, sounds | Visual tuning | No |
| History/memory retention and deletion expectations | Persistence features | No |
| Acceptable alternative if v1.3.9 or original renderer is unavailable | Only after evidence of blocker | No |

No essential unresolved product decision prevented this document set. Ask only the questions needed for the next implementation gate; do not invent answers or ask the owner for hardware facts that can be safely discovered.

## Decision log procedure

For each future change add date, decision owner, evidence, chosen option, alternatives, and affected documents. Distinguish “requested,” “proposed,” “validated,” and “accepted.” Update this register and linked technical plan together so a later agent does not mistake an option for a commitment.
