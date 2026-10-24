# Emotional eyes — index

Goal (owner, 2026-10-24): the app is no longer "the original blue eyes" but an **expressive-eyes app that is the base for an emotional conversation with an AI**. This folder plans the expressive work in small, reversible stages.

| File | What it holds |
|---|---|
| [plan.md](plan.md) | Stages E0–E9: what each delivers, its micro-steps, who codes and reviews, its gate and how to roll it back |
| [catalog.md](catalog.md) | Every idea (#1–#36) with what it looks like, what triggers it, its cost/risk, and its stage |
| [testing.md](testing.md) | The measurements every stage must pass (accuracy, timing, frame rate, memory) and how to run them |
| [progress.md](progress.md) | Status per stage and micro-step: done / failed / untested |
| [report.md](report.md) | Claude's running report: timestamped lines for every step, measurement, review, problem, or need from the owner |

Related: the finished eyes and voice work is in [../eyes/](../eyes/) (plan, progress, evidence). Decisions are logged in [../13_DECISIONS_RISKS_AND_OPEN_QUESTIONS.md](../13_DECISIONS_RISKS_AND_OPEN_QUESTIONS.md).

## Working rules (owner, 2026-10-24)
1. One feature at a time, one stage at a time: every feature is **measured → checked by the owner → decided**; a stage ends with **commit + tag** of the accepted state, and only then does the next stage start. Code may arrive early in a shared APK (switched off); acceptance stays sequential.
2. **Never lower accuracy, never slow the timing.** The baseline numbers in [testing.md](testing.md) are hard gates.
3. **Always reversible:** every stage is one tagged commit on top of the previous one; every new behaviour can also be switched off at runtime.
4. Nothing is committed without the owner's go-ahead.
5. Coding is delegated through model-picker to another model where it fits; Claude plans, integrates and verifies.
6. Every change gets two independent reviews by reviewers who wrote none of it (the reviewer table in plan.md › Orchestration is authoritative).
7. **Few installs**: behaviour lives in files pushed to the phone; drawings come in batches switched off and are turned on one at a time — about 7–8 APKs for the whole plan (see plan.md › Few installs).
