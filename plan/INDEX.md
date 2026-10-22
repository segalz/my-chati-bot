# My Chati Bot — Project Planning Index

Status: planning only. Updated: 2026-10-22. Language: English.

My Chati Bot repurposes an old Android phone as an expressive desk companion. Its signature is animated blue eyes based on the original `espp/expressive_eyes` C++ component, targeting **v1.3.9**. A small on-device AI chooses behavior; deterministic animation draws frames; a separately configured conversational AI handles dialogue.

## Read the plan

| Document | Purpose |
|---|---|
| [01_PRODUCT_VISION.md](01_PRODUCT_VISION.md) | Product idea, boundaries, and success criteria |
| [02_USER_INTERACTIONS.md](02_USER_INTERACTIONS.md) | Daily use, voice, sleep, wake, and visual reactions |
| [03_ARCHITECTURE_AND_DATA_FLOW.md](03_ARCHITECTURE_AND_DATA_FLOW.md) | Modules, ownership, scheduling, and data boundaries |
| [04_ORIGINAL_EYES_PORT_AND_LICENSING.md](04_ORIGINAL_EYES_PORT_AND_LICENSING.md) | Exact upstream target, blue renderer, NDK port, dependency audit |
| [05_EXPRESSIONS_AND_STATE_MACHINE.md](05_EXPRESSIONS_AND_STATE_MACHINE.md) | Behavior states, emotion, gaze, arbitration, and small schema |
| [06_LOCAL_AI_CANDIDATES_AND_BENCHMARKS.md](06_LOCAL_AI_CANDIDATES_AND_BENCHMARKS.md) | Candidate runtimes/models and hardware-based selection |
| [07_ANDROID_STACK_AND_DEVICE_ADAPTATION.md](07_ANDROID_STACK_AND_DEVICE_ADAPTATION.md) | Kotlin/native stack, lifecycle, compatibility, and thermal design |
| [08_VOICE_AND_VISION_PIPELINES.md](08_VOICE_AND_VISION_PIPELINES.md) | Wake word, speech, cameras, face/smile/gaze, and capture rules |
| [09_PRIVACY_SECURITY_AND_CONNECTIVITY.md](09_PRIVACY_SECURITY_AND_CONNECTIVITY.md) | Permissions, retention, API keys, offline/online operation |
| [10_CONNECTED_PHONE_DEVELOPMENT.md](10_CONNECTED_PHONE_DEVELOPMENT.md) | Practical Windows USB/ADB workflow for Codex and Claude Code |
| [11_MVP_AND_IMPLEMENTATION_PHASES.md](11_MVP_AND_IMPLEMENTATION_PHASES.md) | Milestones, dependencies, evidence, and release gates |
| [12_ENGINEERING_AND_TEST_PLAN.md](12_ENGINEERING_AND_TEST_PLAN.md) | Device tests, acceptance criteria, and evidence requirements |
| [13_DECISIONS_RISKS_AND_OPEN_QUESTIONS.md](13_DECISIONS_RISKS_AND_OPEN_QUESTIONS.md) | Confirmed intent, proposals, risks, and owner questions |
| [14_AGENT_DEVELOPMENT_INSTRUCTIONS.md](14_AGENT_DEVELOPMENT_INSTRUCTIONS.md) | Handoff and rules for future development agents |
| [15_SOURCES_AND_PLANNING_PROVENANCE.md](15_SOURCES_AND_PLANNING_PROVENANCE.md) | Original links, source verification status, and limits |

## How to interpret this set

- **Confirmed intent** comes from the owner's current request and available conversation text.
- **Proposed** means an engineering recommendation, not an owner-approved selection.
- **Validation required** means no compatibility or runtime result has been established.
- **TBD / Ask owner** identifies a product decision that must not be invented.

Start with 01, 13, and 10. Before implementation, complete hardware inventory and the exact-source/license audit. Then follow 11. No APK, source implementation, model download, API call, or phone test was performed in this documentation task.

## Existing-work preservation

The target `C:\Develop\my-chati-bot\plan` was inspected before writing and contained no files. The parent contained only this directory and was not a Git repository at inspection. No existing content was overwritten. Future agents must recheck the actual state rather than assuming this remains true.
