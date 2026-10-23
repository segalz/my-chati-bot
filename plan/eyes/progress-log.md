# Eyes — running progress log

Times are local Israel time (HH:MM). Newest entries at the bottom. Details: [progress.md](./progress.md).

## Summary so far (2026-10-23)
- Stage 3 (Android skeleton) — DONE. App 0.1.0 built (Gradle 9.8.2, AGP 9.4.1, NDK r28c); Codex coder sandbox failed → Claude wrote it, AGY reviewed; MIUI blocks adb install → APK via Downloads, installed by hand; launched OK 10:10, black screen + native version, no crash.
- Stage 4.1 — DONE. LVGL 02814ddb + fmt 93e26fa5 added as git submodules (staged, not committed); espp base_component/logger/format copied from v1.3.9; original engine + Monochrome Blue drawer + LVGL compile for Android with 0 warnings, no upstream file modified.
- Stage 4.2–4.5 — native host written by Claude (render thread, AChoreographer, RGB565 canvas → ANativeWindow, JNI, debug broadcast). Codex review: approve with changes → both fixes applied.
- 0.2.1 APK pushed + hash verified; installed by owner 10:44.

## Log
10:49 Stage 4 phone checks started: 0.2.1 launched (cold 760 ms), original blue eyes visible, no crash.
10:50 Stats after 10 s: 58.7 fps, ~29 engine redraws/s (upstream 30 Hz cap), frame work avg 1.47 ms / max 6.9 ms.
10:50 Expression check: all 9 expressions + 4 gaze directions triggered via debug channel; 13 screenshots saved (reviewing now).
10:52 Expressions OK: all 9 visually distinct (brows SAD/ANGRY, cheeks HAPPY, wink lines), contact sheet saved.
10:53 Color/byte-order OK: eye pixel (0,190,255) = original 0x00BFFF in RGB565. Gaze symmetric: x ±56 px, y −68/+64 px.
10:53 Note: SURPRISED and look-left/right touch the 4:3 canvas edge (upstream geometry), to show owner.
10:54 Started blink check (rapid screenshots, no input).
10:54 Auto-blink OK: 41 s without input, 4 frames caught mid-blink (eye height 168–416 px vs 440 open); frame saved.
10:54 Started 10-minute idle soak (no input) with frame/memory/temperature metrics; ends ~11:05.
11:03 Idle soak interrupted: at 10:59:23 a reinstall of app.mychatibot started on the phone (installPackageLI) and killed the app — not a crash. MIUI installer/Play Protect screen is open now.
11:03 Partial idle data (10:49:52–10:59:23, ~9.5 min, no crash): ~59.9 fps, RSS 152 MB (anon 34 MB), CPU 47 °C stable, battery 34.5 °C.
11:04 NEED FROM YOU: was the APK tapped again? Please finish or cancel the installer screen on the phone, then tell me; I'll rerun the 10-min idle + rotation/background checks.
11:31 Confirmed on phone: 0.2.1 (versionCode 3) installed 11:28:18. Removed old chati-eyes-0.1.0-debug.apk from Downloads to avoid another mix-up.
11:32 App launched via adb (cold 761 ms), eyes on screen. Started 10-min idle soak again (no input), ends ~11:42.
11:34 Prepared rotation/background script (rotation forced temporarily via wm user-rotation; original auto-rotate=on restored at the end). Runs after the soak.
11:42 10-min idle soak PASSED (11:32–11:42, no input): 59.9 fps, ~30 redraws/s, RssAnon 33.6→33.9 MB (no leak), PSS 48.8 MB, CPU 47 °C flat, battery 34.4→34.5 °C, no crash. CPU ~29% of one core; frame work avg 1.5→4.1 ms (likely CPU down-clocking), max 17 ms.
11:43 Started rotation x10 + background x10 + background-suspension check.
11:44 Rotation PASSED: 10 landscape/portrait cycles = 20 activity recreations, same process, no crash, eyes centered 4:3 in landscape. Rotation setting restored (auto-rotate on, free).
11:45 Problem: MIUI blocks shell key injection (input keyevent) and settings writes; first background attempt invalid. Switched to HOME intent.
11:47 Background PASSED: 10/10 real home/return cycles, same process, 0 errors, no crash. Render thread fully suspended in background (0 CPU ticks/5 s vs 157 foreground). Worst frame 25 ms once (resume).
11:48 Next: compare with upstream reference images, write Stage 4 evidence; then owner look-approval.
11:52 Stage 4 evidence written (evidence/stage4-metrics.md), progress.md updated. All technical checks PASSED. NEED FROM YOU: look at the eyes on the phone and approve the look (Stage 4 gate). Nothing committed.
11:55 Stage 4 CLOSED: owner approved the look on the phone. All Stage 4 items done. Still nothing committed (branch eyes/stage3-android-skeleton; LVGL/fmt submodules staged). Next: Stage 5 (intent contract + debug channel) when you say go.
11:58 Committed b6d0d77 (Stages 3–4) and pushed branch eyes/stage3-android-skeleton to GitHub (public repo; phone serial removed from files first). No PR opened.
12:00 Stage 5 started: EyeIntent schema + validator, EyeController, debug intent channel, overlay. Claude writes the [HARD] parts (5.1, 5.2); Codex reviews.
12:02 5.1–5.2 written (EyeIntent parser, EyeController, controller thread host). Unit tests: 24/24 passed (13 parser, 11 controller).
12:04 5.3–5.4: Codex (model-picker coder) refused the wiring task (its global policy) → fallback Claude wrote it. Controller made process-wide so rotation can't strand a reaction.
12:04 Build 0.3.0 OK, unit tests 24/24. Sending Stage 5 code to Codex for review before pushing the APK.
12:07 Codex review of Stage 5: REJECT with 5 required changes (stale pending replay, org.json not strict, ticker race, listener lifecycle, overflow). All fixed; own strict JSON parser replaces org.json; MODEL may not set hold_ms. Tests 35/35.
12:09 Codex re-review: approve with 1 change (listener registration on handler thread) → fixed. Tests 35/35.
12:10 APK 0.3.0 READY TO INSTALL: Download/chati-eyes-0.3.0-debug.apk, SHA-256 d8dc6b61…13c7 (verified on phone). Older 0.2.1 APK removed from Downloads. NEED FROM YOU: install it, don't open it.
12:12 Tried to install 0.3.0 myself (open installer via adb intent + approve through scrcpy). Blocked by Claude Code's safety classifier ("Security Weaken") — not worked around. NEED FROM YOU: install Download/chati-eyes-0.3.0-debug.apk by hand, don't open it.
12:15 Second attempt with owner's chat approval also blocked by the safety classifier. Stopped; not worked around. Install must be done by hand or via 'Install via USB' / a Claude Code permission rule.
12:29 Checked: still 0.2.1 installed. Files app shows chati-eyes-0.3.0 selected (long-press), installer not opened. NEED FROM YOU: clear the selection, then a single short tap on the APK → Install.
12:30 Rechecked after 'installed now': phone still reports 0.2.1 (lastUpdate 11:28); Files app open on Download (landscape), no installer shown. Waiting for the install.
12:38 0.3.0 installed by owner (12:36:50) and open. Running Stage 5 phone check (11 intents/sentence + dwell + idle return).
12:41 Stage 5 phone check 13/13 PASS (valid applied, invalid rejected with reasons, NONE/neutral ignored, dwell+newest wins, idle return, DEBUG_SAY placeholder, no crash). Stage 5 COMPLETED. Not committed yet.
12:44 Demo requested: running a ~35 s choreographed show (blinks, gaze, all expressions, winks) through the debug channel, recording the phone screen.
12:46 BUG found while running the demo: 'am start' opened a 2nd MainActivity instance; the old one's surfaceDestroyed cleared the NEW surface (renderer has one window slot, no owner check) → eyes frozen (window=no). User confirmed 'it does nothing'. Fixing: surface owner token + launchMode singleTask; needs Codex review + a new APK.
12:45 Fix built (0.3.1): surface owner check + singleTask. Tests 35/35. Codex review: approve, no changes.
12:45 APK 0.3.1 READY TO INSTALL: Download/chati-eyes-0.3.1-debug.apk, SHA-256 363cdf93…d076 (verified on phone). 0.3.0 removed from Downloads. NEED FROM YOU: install it (short tap → Install), don't open it. Then: demo again, then Stage 6 (sentence → eyes).
12:45 Fix built (0.3.1): surface owner check + singleTask. Tests 35/35. Codex review: approve, no changes.
12:45 APK 0.3.1 READY TO INSTALL: Download/chati-eyes-0.3.1-debug.apk, SHA-256 363cdf93…d076 (verified on phone). 0.3.0 removed from Downloads. NEED FROM YOU: install it (short tap → Install), don't open it. Then: demo again, then Stage 6 (sentence → eyes).
12:53 0.3.1 installed by owner (12:52:21) and opened; renderer window=yes. Re-running the demo with screen recording.
12:54 Demo OK on 0.3.1: 35 s show recorded (1050 frames @30 fps, 4.4 MB, evidence/demo/eyes-demo-0.3.1.mp4), renderer stayed attached, no detach issues. Video sent. Next: Stage 6 (sentence → on-phone Gemma → eyes).
12:56 Stage 6 started: Gemma 3 1B Q4_K_M on the phone via llama.cpp v0.7.0 (620d222) inside the app; sentence (DEBUG_SAY) → model → validator (MODEL) → controller → eyes.
13:00 Stage 6 native brain written by Claude: llama.cpp v0.7.0 submodule (620d222) in the NDK build; Gemma prompt v4; system prompt cached in KV (swa_full); sentence tokenized without special tokens (anti-injection); two-phase grammar with fixed JSON fed in, never generated.
13:03 Found native code was built at -O0 in debug APKs (CMAKE_BUILD_TYPE=Debug) → forced Release (-O2/-O3) for llama.cpp and LVGL.
13:08 Model deployed to the phone app's private storage via run-as: files/models/gemma-3-1b-it-Q4_K_M.gguf, SHA-256 8ccc5cd1…a135 matches.
13:09 Codex review round 1: approve with 3 changes (cancellation/stale results, reject late results, don't generate closing "}) → fixed.
13:11 Codex round 2: reject (cancellation not request-specific; delivery check) → generation-based cancel + controller-thread freshness check.
13:12 Codex round 3: approve with 1 change (serialize submit) → fixed. Tests 35/35.
13:13 APK 0.4.0 READY TO INSTALL: Download/chati-eyes-0.4.0-debug.apk (18 MB), SHA-256 2f75b6a1…fe3d (verified on phone). 0.3.1 removed. NEED FROM YOU: install it (short tap → Install), don't open it.
13:19 0.4.0 installed by owner (13:18:21), running; model loaded (RssAnon ~600 MB). Eye frame work now 1.2 ms avg (was ~4 ms at -O0). Sending 10 demo sentences.
13:20 First sentence test on 0.4.0: the model ran and chose well (HAPPY, SAD, ANGRY, SURPRISED, SLEEPY, look left/up, BORED, no change for a schedule) — but ALL were rejected by the validator: model JSON had no schema_version (my integration bug; validator worked as intended). Latency 2.76 s (prompt 1.1 s, gen 1.66 s for 4 tokens).
13:21 Fixes: app adds the schema envelope to the model's v4 output (validator unchanged, new test); grammar applied to the full 262k vocab only when the top token is invalid (like llama.cpp common_sampler).
13:22 Codex review: approve with 1 change (wrap must not trim — size/whitespace bypass) → fixed + tests. Tests 36/36.
13:23 APK 0.4.1 READY TO INSTALL: Download/chati-eyes-0.4.1-debug.apk (10 MB), SHA-256 305c7f06…ccfdd (verified). 0.4.0 removed. NEED FROM YOU: install it (short tap → Install), don't open it.
13:27 0.4.1 installed by owner (13:26:00); model cold-loaded in 16.4 s (prefix 250 tokens). Sending 10 demo sentences.
13:29 SENTENCE → EYES WORKS: 9/10 demo sentences correct on the phone (only miss: 'secret' → SURPRISED instead of a wink). Latency p50 2.36 s, max 3.8 s (test harness polls logcat every 0.3 s + screenshots; to re-measure cleanly). Recording a talk demo video next.
13:30 Talk demo recorded (evidence/demo/talk-demo-0.4.1.mp4, 1175 frames/39 s): 7/7 sentences got the right reaction. Latency 2.5–3.0 s while screen-recording (recorder + harness load the CPU). Video sent. Open: latency vs 1.5 s target, full in-app benchmark (6.6), commit.
13:35 Latency work started (owner idea: a 'phrasebook' of cases→reactions + any idea; brainstorm with Codex/Grok/AGY). Step 1: clean latency measurement without harness polling.
13:37 Clean latency (no harness load), 10 sentences: median 2.09 s = prompt ~15 tok in 1.0 s (15 tok/s, Q4_K_M has no ARM repack) + gen ~3 tok + fixed JSON batch 1.13 s. CPU 47 °C. Sending the numbers to Codex, Grok and AGY in parallel for a brainstorm.
13:42 Brainstorm (Codex, Grok, AGY in parallel) done — strong agreement: (1) classify the expression straight from the prefill logits instead of generating (saves ~1.1 s), (2) decide look by keyword rules, (3) owner's phrasebook as a high-precision instant layer, (4) instant micro-reaction (blink) on input, (5) test 2 threads on the A75 cores, (6) Q4_0 may be fine once output is logit classification; endgame: tiny distilled classifier (~30 ms). Not helpful here: GPU/NNAPI, speculative decoding, shorter cached prompt.
13:47 Owner approved: implementing (1) early-stop expression classification from prefill logits, (2) look keyword rules, (3) editable phrasebook (exact matches), (4) instant blink on input — one build, then latency + 100-sentence accuracy on the phone.
13:50 Implemented: early-stop expression classification (no generation after the first unique token), LookRules (keyword gaze + negation), owner-editable assets/phrasebook.txt (exact matches, ~75 phrases), instant blink on every sentence, new IntentSource.PHRASEBOOK.
13:51 Codex review: approve with 3 changes (strict model-output shape, phrasebook hits must not reach the model queue, gaze rules too broad) → fixed; re-review: 3 small precision fixes (explicit reject, 'look up a…', linking-word off-by-one) → fixed. Tests 45/45.
13:53 APK 0.5.0 READY TO INSTALL: Download/chati-eyes-0.5.0-debug.apk (10 MB), SHA-256 dc21fd08…2707 (verified). 0.4.1 removed. NEED FROM YOU: install it (short tap → Install), don't open it. Then: latency + 100-sentence accuracy run.
14:01 0.5.0 installed by owner (14:00:37); phrasebook 74 phrases; model loaded in 18254 ms.
14:02 Running the 100-sentence English evaluation on the phone (dev 40 + holdout 60), one log read per sentence.
14:08 RESULT 0.5.0 (100 English sentences on the phone): accuracy 88/100 (dev 37/40, holdout 51/60) vs 89/100 before — kept. Latency model p50 1.07 s (was 2.09 s, −49%), p90 1.29 s, max 2.6 s; phrasebook hits ~1 ms. Generation now ~2 ms in most cases; the remaining floor is prompt processing (~1.0 s for ~15 tokens). Eyes kept 60 fps during inference (frame work avg 1.9 ms). Misses: boring→SAD, spider→ANGRY, secrets/jokes→SAD/ANGRY, 'turn your eyes right'→WINK_RIGHT.
14:12 Owner: speed first, then commit+push. Plan: 0.5.1 with debug-only runtime controls (threads, model file) → sweep threads × {Q4_K_M, QAT Q4_0, plain Q4_0} on the phone → 100-sentence accuracy for the best.
14:12 0.5.1 built (debug-only DEBUG_BRAIN: switch model file / thread counts at runtime, no reinstall per experiment). Tests 45/45.
14:13 Q4_0 models deployed to app private storage (QAT Q4_0 ef60e4e9…, plain Q4_0 27ee88e0…, hashes match).
14:13 APK 0.5.1 READY TO INSTALL: Download/chati-eyes-0.5.1-debug.apk, SHA-256 2f3578ba…e00e (verified). 0.5.0 removed. NEED FROM YOU: install it, don't open it. After that all speed experiments run without further installs.
14:17 0.5.1 installed by owner (14:16:05). Running speed sweep: 3 models × threads 2/4/6/8, 8 sentences each.
14:33 Speed sweep (8 sentences each, model time p50): Q4_K_M 2t 2.01 s / 4t 1.40 / 6t 1.05 / 8t 1.15; QAT Q4_0 1.09 / 0.76 / 0.53 / 0.60; plain Q4_0 1.17 / 0.78 / 0.50 / 0.55. 6 threads best for all (the '2 A75 threads' idea was wrong here). Q4_0 is ~2× faster. Now: 100-sentence accuracy for both Q4_0 @6 threads.
14:38 100-sentence accuracy @6 threads: plain Q4_0 84/100 (p50 0.47 s, p90 0.59 s); QAT Q4_0 82/100 (0.46 s); vs Q4_K_M 88/100 (1.07 s). Q4_0's extra misses are mostly over-reacting to neutral facts (Paris→HAPPY, laundry→BORED, volume down→SAD). Phone set back to Q4_K_M 6 threads.
14:38 NEED FROM YOU: choose default — Q4_K_M (accurate, ~1.1 s) or plain Q4_0 (~0.5 s, −4 sentences), or let me try calibrating Q4_0's 'no reaction' decision first.
