# Progress: emotional eyes

Status: **E0 accepted 2026-10-24 (tag emotions-E0)** (plan approved by the owner 2026-10-24; reviewed by Codex). Legend: [x] done · [~] partly · [ ] not started · ✗ failed. Each finished stage lists its APK (file archived, SHA-256), commit, tag and measurements.

## E0 — Baseline and safety net — ACCEPTED 2026-10-24 (APK 0.10.0, tag emotions-E0)
- [x] E0.1 Decide the color experiment — owner keeps it as the base (APK 0.9.4, SHA-256 a3289db4…a44f; measured: waiting #00BDFE, happy #5AE36B, sad #0FB5A3, angry #FE2728); committed with the plan
- [x] E0.2 Runtime switches (`effects.txt` in private files, debug channel) — code done (Codex), reviewed (opencode, Claude, Cline), tests 99/99; phone check with the E0 APK
- [x] E0.3 Regression command (all rows of testing.md) — `tools/model-bench/regression.py` by Codex, opencode review fixed, tool tests 14/14; first device run with the E0 APK
- [x] E0.4 Frame-time tail + audio presentation probe — by Claude, reviewed by Codex + opencode (fixed), builds; phone check with the E0 APK
- [x] E0.5 Frozen baseline (timing ×3) — evidence/baseline-E0/ (88/100, 55/70, p50 915 ms, 59.9 fps, VmHWM 1.74 GB, all rows complete); APK 0.10.0 archived (SHA-256 406484b0…e176); configuration rollback rehearsed across a restart. APK-reinstall rehearsal skipped by the owner
- [x] E0.6 Shape framework in the renderer — by Claude, reviewed (Codex, opencode), builds; phone check with APK 0.10.0
- [x] E0.7 Prompt bundles as files in private storage (bundle v4 = today, byte-identical, unit-tested) — by Claude, reviewed (Codex ×5 rounds, opencode ×2), builds; phone check with APK 0.10.0

## E1 — App-level life — DONE (APK 0.11.0, install 2; tag emotions-E1)
- [x] E1.1 #15 colors.txt — phone check PASS
- [x] E1.2 #13 intensity (dev-only calibration gap 10.3..20.1) — phone check PASS
- [x] E1.3 #9 thinking glance — phone check PASS
- [x] E1.4 #10 sleeping + wake — phone check PASS
- [x] E1.5 #17 × × on failures — drawing PASS; trigger by review only
- [x] Regression all-off = E0 (evidence/E1-all-off/, timing re-run), all-on (evidence/E1-all-on/)
- [x] Owner approved → commit bbea3df + tag emotions-E1

## E2 — Love — STOPPED by the owner (the 1B model cannot learn LOVE without flips; keyword option declined)
- [ ] E2.1 Test sets (love, near-misses incl. negation/sarcasm/keywords, 2 stories)
- [ ] E2.2 Tokenizer/grammar checks, cold+warm latency
- [ ] E2.3 Prompt bundle v5 (LOVE only)
- [ ] E2.4 Heart drawer + pulse, voice style
- [ ] E2.5 Full measurements incl. confusion matrix

## E3 — Effects layer — DONE (APK 0.13.0, install 4; tag emotions-E3; all effects off by default, sweat visibility fix pending)
- [ ] E3.1 Overlay layer on the render thread
- [ ] E3.2 #25 sweat · #26 anger mark · #27 sparkles · #28 ?/! · #29 notes · #30 rain cloud (temporary triggers where the label does not exist yet)

## E4 — Motions — NOT STARTED
- [ ] E4.1 Motion scripts (cancelled by any reaction)
- [ ] E4.2 #31 eye roll · #32 shifty glance · #33 double blink · #34 tremble · #12 strong anger · #24 wink

## E5 — Emotions batch 1, one label at a time — NOT STARTED
- [ ] LAUGH (#2) · [ ] CRY (#3) · [ ] SHY (#5)

## E6 — Emotions batch 2, one label at a time — NOT STARTED
- [ ] FEAR (#4) · [ ] EXCITED (#6) · [ ] CONFUSED (#7, #19) · [ ] PLEADING (#8)

## E7 — Shapes and nuance — NOT STARTED
- [ ] #18 dollar (rule) · [ ] #20 SKEPTICAL (+ #21) · [ ] #22 huge eyes · [ ] #23 one eye open

## E8 — Polish — NOT STARTED
- [ ] #14 halo · [ ] #16 shape morph

## E9 — Later — BLOCKED
- [ ] #11 listening (needs speech input) · [ ] #36 follow the user (needs the front camera)
