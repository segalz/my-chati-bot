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
