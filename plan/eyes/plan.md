# Plan: Eyes — Stage 1 (original blue eyes driven by an on-phone model)

[Project index](../INDEX.md)

## Goal

Get the first living piece of My Chati Bot onto the physical old phone: the **original Monochrome Blue Eyes** renderer and C++ animation engine from `espp/expressive_eyes` **v1.3.9**, ported to Android through NDK/JNI, and a **small language model running on the phone itself** that receives an English or Hebrew sentence (sent by Claude over ADB during development) and turns it into a validated eye intent (expression, gaze, hold time) that the engine then animates. This proves the core of decisions D02 and D03 end to end before voice, camera, or conversation work starts.

## Scope

- In scope:
  - Windows toolchain (Android command-line tools, platform-tools, NDK, CMake) and connected-phone inventory.
  - Exact-version source, dependency, license, and portability audit of `expressive_eyes` v1.3.9 and its Monochrome Blue Eyes example drawer.
  - Kotlin app shell + NDK/JNI port of the original engine and blue renderer to an Android surface.
  - Bounded intent contract, validator, expiry, and deterministic idle fallback.
  - On-device small model (runtime chosen from the audit/inventory, llama.cpp is the leading candidate) that maps a sentence to an intent.
  - Development input channel: a debug-only ADB broadcast that delivers a sentence (or a raw intent) to the app.
  - Verification on the physical phone at the end of every stage.
- Out of scope:
  - The full-featured "realistic eyes" renderer.
  - Wake word, STT/TTS, conversation API, Ollama/LAN backends, cameras, background/locked-screen operation.
  - Store release, signing for distribution, long soak tests (beyond a short smoke session).

## Binding requirements (owner, 2026-10-22)

- R1 — The eyes are the original **Monochrome Blue Eyes** renderer from `espp/expressive_eyes`, exactly **v1.3.9** (not the full-featured realistic renderer). Port the original C++ engine and renderer through NDK/JNI; verify source identity, dependencies, licenses, and portability before implementation.
- R2 — The on-device model selects **bounded expression and gaze intents only**; it never generates frames.
- R3 — Deterministic animation runs independently of the model at the device's supported frame rate.
- R4 — Local behavior must eventually respond **autonomously to permitted context**, not only to sentences sent through ADB.
- R5 — ADB commands are a **development/test interface**, not the final user interaction.
- R6 — The on-phone requirement applies to **eye intelligence**. Conversation may later use a separately configured API; Ollama is optional and not needed for this stage.
- R7 — Model candidates are selected **only after measuring the phone's hardware**. Before downloading, present the recommended model, size, license, and expected requirements to the owner.
- R8 — Development agents are selected with model-picker **without bypassing** the owner's global Cline-first code-intelligence policy.
- R9 — Every stage is validated appropriately: tool installation and source/license analysis need their own evidence; APK installation applies once an app exists; executable stages are tested on the physical phone with a real action and an observed result.
- R10 — No erasing data, uninstalling apps, or changing unrelated phone/PC settings.
- R11 — plan.md and progress.md stay current and clearly separate **completed**, **failed**, and **untested** work.
- R12 — **English-only model input** (owner decision 2026-10-22). The eye model's prompt, input, and output are English. Hebrew is translated **at the source**, not by an extra translator on the eyes path: speech via Whisper's Hebrew→English translate task (P3), conversation replies via an English affect hint from the conversation API, and development sentences sent in English over ADB. Hebrew support is therefore not a model-selection criterion. ~~A small Hebrew/imperfect-translation subset stays in tests only as a robustness check.~~ Updated 2026-10-23 (owner): translation quality is the sending model's responsibility, so eye-model tests use **clean English only**. Hebrew and rough-translation sentences are dropped from evaluation as noise.

## Assumptions & constraints

- **Version pin is hard.** Only v1.3.9. If it cannot be obtained or does not contain the blue drawer, stop and ask the owner. Never substitute `main` or another version silently.
- **No re-creation.** The original engine and blue renderer are ported; a lookalike rewrite is not acceptable. A minimal drawing-callback adapter is allowed only with a written decision record (see Stage 2).
- **Model on the phone.** Owner chose on-device inference for this stage. If the phone cannot run any suitable model, report measurements and ask before changing scope. A deterministic fallback keeps the eyes alive but is never reported as the local AI working.
- **Toolchain:** the owner approved installing Android command-line tools only (SDK cmdline-tools, platform-tools, NDK, CMake) under `%LOCALAPPDATA%\Android\Sdk`. No Android Studio. Java 21 (Microsoft OpenJDK) is already installed. Ollama exists on the PC but is not used here.
- **Phone (Stage 1 result, 2026-10-22):** Redmi 10 (MT6769H, 2×A75 + 6×A55 with dot-product), Android 13 / API 33, arm64-v8a, ~5.6 GiB RAM, 1080×2400 at 60/90 Hz, GLES 3.2 / Vulkan 1.1. Toolchain: SDK platform 36, build-tools 36.1.0, NDK r28c (28.2.13676358), CMake 3.31.6. Details: [evidence/device-inventory.md](./evidence/device-inventory.md).
- **The workspace is not a Git repository yet.** Stage 3 initializes one for the Android project (a real implementation need, not a routing workaround).
- Builds, installs, and phone runs are authorized by the owner for the per-stage phone checks in this plan.
- **Install path (2026-10-23):** MIUI blocks `adb install` (`INSTALL_FAILED_USER_RESTRICTED`, "Install via USB" off). Working method: push the APK to `/sdcard/Download/`, verify its SHA-256 on the phone, and the owner taps it and approves the Android prompt. Launch, screenshots, and logcat then run over ADB. Each new APK needs that owner tap until "Install via USB" is enabled. scrcpy for viewing: `C:\Users\segal\AppData\Local\Programs\scrcpy\scrcpy-win64-v5.0.1\scrcpy.exe -s <PHONE_SERIAL> --window-title "Redmi 10 (Chati)"` (set `ADB` to the SDK adb). Model weight downloads need owner approval of the exact model, license, and size first.
- Every `[HARD]` micro-step is written by Claude itself and then reviewed by Codex. Other coding steps are delegated as described below.

## Orchestration (how every micro-step is executed)

Claude is the orchestrator and owns decisions, synthesis, and final verification. Model selection uses the owner's model-picker tool, `C:\Develop\mcp-model-picker` (MCP server `model-picker`: `pick`, `log_run`, `backend_status`), following its [SUPERVISOR.md](../../../mcp-model-picker/docs/SUPERVISOR.md) routing rules. Check `backend_status` at the start of each stage; use `prefer=thrifty` for a backend whose quota is low (e.g. AGY weekly was 13 % on 2026-10-22).

1. **Coder selection** — for each non-`[HARD]` coding micro-step call `model-picker.pick` with `category` (coding / testing / ui / research), `size` (S/M/L), `needs_write=true`, `prefer=balanced` (`quality` for native/JNI work). Call the returned `primary.mcp_tool` with the returned model/effort, with a concise English prompt that names the exact files it may change and forbids git operations.
2. **`[HARD]` steps** — Claude writes the code directly. Hard parts: the native drawing adapter, render thread and surface lifecycle, JNI bridge, intent validation/arbitration, llama.cpp integration, and constrained output.
3. **Review** — after every coding micro-step, pick a reviewer with `model-picker.pick(category="review", exclude=[<coder backend>])`. For `[HARD]` steps the reviewer is always **Codex** (`codex_ask`, read-only sandbox, prompt opens with an explicit owner-authorization block naming Codex the reviewer). Findings are fixed and re-reviewed until clean.
4. **Logging** — every delegated call, success or failure, is followed by `model-picker.log_run`. On failure use the returned fallback.
5. **Analysis** — read-only code discovery follows the owner's policy: Cline workspace first once the project is a trusted Git repository; AGY/Grok fallback per failed scope.
6. **Stage gate** — every stage ends with a check on the connected phone: build, verify the APK hash, `adb install -r`, launch, perform a real action, observe (`adb exec-out screencap`, logcat by PID, `dumpsys gfxinfo/meminfo`). Results go to [progress.md](./progress.md) with evidence paths under `plan/eyes/evidence/`.

## Stages

### Stage 1: Toolchain and connected phone — ✅ completed 2026-10-22 (see progress.md)
Install the Android command-line toolchain and inventory the real phone.

- [x] 1.1 Install Android cmdline-tools, platform-tools, and SDK platform/build-tools under `%LOCALAPPDATA%\Android\Sdk`; set `ANDROID_HOME` for the user.
  - Check: `adb version` and `sdkmanager --list_installed` print the installed packages.
- [x] 1.2 Owner connects the phone with USB debugging enabled and approves the RSA prompt.
  - Check: `adb devices -l` shows exactly one physical device in state `device`; its serial is recorded privately (not in shared evidence).
- [x] 1.3 Run the read-only inventory from [10_CONNECTED_PHONE_DEVELOPMENT.md](../10_CONNECTED_PHONE_DEVELOPMENT.md) section 2 (manufacturer, model, Android release/SDK, ABI list, MemTotal/MemAvailable, `/data` free space, screen size/density, battery/thermal).
  - Check: `plan/eyes/evidence/device-inventory.md` exists, sanitized, with every field filled or explicitly marked unavailable.
- [x] 1.4 Choose and install NDK + CMake versions compatible with the phone's API level and ABI; record the versions.
  - Check: `sdkmanager --list_installed` shows the chosen NDK and CMake; versions written in progress.md.
- [x] 1.5 Classify the phone into a capability profile (Constrained / Balanced / Higher) and set a provisional model memory budget (MemAvailable minus reserve for rendering and OS).
  - Check: profile and budget recorded in `device-inventory.md` with the numbers used.

**Stage 1 review:** ADB talks to the right phone, the toolchain exists, and the device facts that drive Stages 2–6 (ABI, API level, RAM budget) are written down. Phone check: `adb shell getprop ro.product.cpu.abilist` matches the inventory.

### Stage 2: Exact source, dependency, license, and portability audit — ✅ completed 2026-10-22 (conditional GO, see progress.md)
Prove what v1.3.9 is and whether the blue renderer can run on Android before writing app code.

- [x] 2.1 Download the v1.3.9 component archive from the Espressif registry into `third_party/expressive_eyes-1.3.9/` (unchanged); record URL, date, SHA-256, and component manifest.
  - Check: `third_party/expressive_eyes-1.3.9/SOURCE.md` lists URL, date, hash; recomputing the hash matches.
- [x] 2.2 Map the archive to an immutable upstream `esp-cpp/espp` commit/tag (diff the archive against the candidate commit).
  - Check: SOURCE.md names the commit and states the diff result (identical / listed differences). If no match: Status blocked, ask owner.
- [x] 2.3 Locate the Monochrome Blue Eyes example drawer and its assets in that exact revision.
  - Check: exact file paths recorded; if the drawer is not in the archive, the matching repository revision that contains it is recorded and owner is told.
- [x] 2.4 Delegated read-only audit (research, quality): engine API (expressions, gaze, blink, update/timing, randomness, drawing callbacks), blue drawer dependencies (LVGL or other), ESP-IDF/FreeRTOS couplings, build requirements.
  - Check: `plan/eyes/evidence/port-audit.md` has `## Result / ## Evidence / ## Metrics` with exact file and identifier names found in the source; no invented API names.
- [x] 2.5 Dependency and license inventory: name, exact version, license, notices, transitive deps, Android/NDK compatibility for the core, the blue drawer, assets, and every rendering dependency.
  - Check: table in `port-audit.md`; every row has a license and a source of truth; license texts copied to `third_party/LICENSES/`.
- [x] 2.6 Portability decision: what compiles as-is, what needs a platform adapter (time, random, logging, tasks, display flush), and the chosen Android drawing backend (e.g. the dependency's own software framebuffer blitted to `ANativeWindow`).
  - Check: decision record in `port-audit.md` reviewed by Codex; any deviation from "original renderer" is explicit. Owner is asked if a blocker exists.
- [x] 2.7 Record the canonical v1.3.9 expression identifiers and gaze/blink API that the intent contract (Stage 5) will map to.
  - Check: list in `port-audit.md` with file:line references.

**Stage 2 review:** immutable provenance, complete license inventory, and a go / no-go portability verdict exist. Nothing is substituted. Phone check: none (no app yet). Gate: the owner sees a short summary before Stage 3.

### Stage 2B: Early on-phone model comparison (CLI, before the app) — ✅ completed 2026-10-22 (see evidence/model-benchmark.md; model choice pending owner)
Owner asked to test which local model is best. The phone is already measured (Stage 1), so candidates can be compared on the real phone with a command-line runtime now, in parallel with Stages 3–5, instead of waiting for Stage 6. This replaces the research half of 6.1 and the bulk of 6.6; Stage 6 then confirms the winner inside the app.

- [x] 2B.1 Shortlist 3 small instruction models that fit the ≤ 1.0 GiB budget (exact repo, revision, license, quantization, file size, Hebrew + English ability). Present the shortlist and download sizes to the owner (R7).
  - Check: `evidence/model-shortlist.md`; owner approves the downloads.
- [x] 2B.2 Build `llama.cpp` (pinned tag, MIT) command-line tools with NDK r28c for arm64-v8a (dot-product on), push binaries and approved GGUF files to `/data/local/tmp/chati-bench/`.
  - Check: `llama-cli --version` runs on the phone; file hashes recorded.
- [x] 2B.3 Fixed test set (R12): ~40 English sentences plus a small robustness subset (imperfect "machine-translated" English and a few raw Hebrew lines, scored separately), labeled with the expected reaction, including **no-reaction** cases (neutral statements that should leave the eyes alone) and gaze cases. Same prompt and same JSON grammar (EyeIntent fields incl. `react`) for all models.
  - Check: test set saved in `evidence/model-testset.jsonl`.
- [x] 2B.4 Run every model on the phone: cold load, warm p50/p95 latency, peak RSS, tokens/s, valid-JSON rate, expression accuracy, react/no-react accuracy, temperature after the run.
  - Check: `evidence/model-benchmark.md` with a comparison table; recommendation (model, size, license, requirements) presented to the owner. Benchmark files are removed from the phone afterwards.

### Stage 3: Android project skeleton with native build — ✅ completed 2026-10-23 (see progress.md)
Create a minimal Kotlin + NDK app that runs on the phone.

- [ ] 3.1 `git init` the workspace root with a `.gitignore` for Gradle/Android/NDK outputs and model files; `third_party/` committed unchanged.
  - Check: `git status` is clean after the first commit, which the owner approves.
- [x] 3.2 Create Gradle project `android/` (Kotlin, single `app` module, Views + `SurfaceView`, minSdk = phone API or lower, ABI filter = phone ABI, pinned AGP/Gradle/NDK/CMake versions).
  - Check: `.\gradlew.bat :app:assembleDebug` succeeds; versions listed in progress.md.
- [x] 3.3 Add `app/src/main/cpp/CMakeLists.txt` building a `chati_eyes` shared library with a single JNI `nativeVersion()` function.
  - Check: APK contains `lib/<abi>/libchati_eyes.so` (`unzip -l`); build log has no warnings promoted to errors.
- [x] 3.4 Fullscreen black activity with keep-screen-on while visible, showing the native version string in a debug overlay.
  - Check: unit/instrumentation build passes.

**Stage 3 review:** phone check — install the debug APK, launch, screenshot shows the black screen and the native version string; logcat shows no crash.

### Stage 4: Port the original engine and Monochrome Blue Eyes renderer — ✅ completed 2026-10-23 (owner approved the look)
Run the original animation on the Android surface.

- [x] 4.1 Add the v1.3.9 engine sources and the blue drawer (and its rendering dependency) to the CMake build from `third_party/` without editing them; platform shims live in `app/src/main/cpp/platform/`.
  - Check: native build compiles all upstream files for the phone ABI; `PATCHES.md` lists every shim and states "no upstream file modified" (or lists each patch).
- [x] 4.2 `[HARD]` Platform adapter per [port-audit.md](./evidence/port-audit.md): our own Android CMake targets (upstream files use `idf_component_register`), empty `esp_heap_caps.h` shim, `ESP_PLATFORM` undefined, `logger.cpp` compiled, C++20, LVGL at pin `02814ddb` with a minimal `lv_conf.h` (core + SW renderer only).
  - Check: the engine RNG is not seedable (upstream statics), so the native test disables auto-blink and runs a scripted `set_expression` / `look_at` / `blink` / `update(dt)` sequence. The resulting expression and state transitions (not blink timings) are identical across two runs.
- [x] 4.3 `[HARD]` Drawing adapter: RGB565 LVGL canvas → row-by-row copy into an `ANativeWindow` buffer (`WINDOW_FORMAT_RGB_565`, respecting `stride`), scaled/letterboxed to 1080×2400.
  - Check: (a) `sizeof(lv_color_t)`, canvas stride, and byte order are recorded at the pin; (b) a known-color test pixel/area reads back correctly on the phone screenshot; (c) a 320×240 upstream-resolution reference frame and the phone frame are compared side by side (color, shape). The proposed canvas resolution is decided here.
- [x] 4.4 `[HARD]` Render thread and surface lifecycle: dedicated native render thread, display-paced updates, safe create/resize/destroy/recreate on rotation and background/foreground.
  - Check: 20 rotate + background/foreground cycles with no crash or stale surface (logcat clean, PID unchanged).
- [x] 4.5 `[HARD]` Narrow JNI bridge: `init`, `surfaceCreated/Changed/Destroyed`, `setExpression(id, intensity)`, `lookAt(x, y)`, `blink()`, `release()`; thread-safe command queue into the render thread.
  - Check: Codex review clean; calling each JNI method from a debug button changes the eyes as expected.
- [x] 4.6 Deterministic idle: engine's own blink and subtle gaze behavior running with no external input.
  - Check: 60 s recording/screens show blinking and idle gaze; no freezes.
- [x] 4.7 Baseline metrics: frame time p50/p95, long stalls, memory, CPU temperature over 10 min.
  - Check: numbers saved to `evidence/stage4-metrics.md` from `dumpsys gfxinfo/meminfo` and battery temperature.

**Stage 4 review:** phone check — every v1.3.9 expression is shown once via the debug channel and screenshot-compared with upstream; idle runs 10 min smoothly; rotation and backgrounding survive. Owner looks at the phone and approves the look.

### Stage 5: Intent contract and debug input channel — ✅ completed 2026-10-23 (see progress.md)
A safe, typed path from "something wants an expression" to the engine.

- [x] 5.1 `[HARD]` Kotlin `EyeIntent` schema (schema_version, `react` boolean — the model may decide **not** to react, which leaves the current animation untouched — expression enum mapped to the 2.7 identifiers `NEUTRAL, HAPPY, SAD, ANGRY, SURPRISED, SLEEPY, BORED, WINK_LEFT, WINK_RIGHT`, intensity 0–1, gaze x/y in [-1, 1], hold_ms bounded, session_id, expires_at_monotonic_ms) and a strict parser/validator (unknown fields/values rejected, NaN/out-of-range rejected or clamped per contract).
  - Check: JVM unit tests for valid, invalid enum, NaN, out-of-range, expired, oversized input — all pass.
- [x] 5.2 `[HARD]` `EyeController`: newest-valid-intent wins, minimum dwell, hold expiry back to idle, deterministic fallback when no intent; single owner thread feeding JNI.
  - Check: unit tests with a fake clock cover expiry, dwell, and fallback; Codex review clean.
- [x] 5.3 Debug-only broadcast receiver (`debug` build type only, not exported in release): action `…DEBUG_INTENT` with a JSON extra, and action `…DEBUG_SAY` with a `text` extra (consumed in Stage 6).
  - Check: release manifest (merged) has no such receiver; debug build receives `adb shell am broadcast -a … --es json '{…}'`.
- [x] 5.4 On-screen debug overlay (toggle) showing last input, validation result, applied intent, and mode (`model` / `fallback`).
  - Check: overlay shows the rejection reason for a malformed JSON broadcast.

**Stage 5 review:** phone check — Claude sends 10 raw intents (valid and invalid) over ADB; valid ones change the eyes, invalid ones are rejected and visible in the overlay and logcat; eyes return to idle after hold time.

### Stage 6: On-phone small model: sentence → intent — COMPLETED 2026-10-23
The local model reads a sentence and chooses the eyes' reaction, fully offline.

- [x] 6.1 (Largely done in Stage 2B: recommended model **Gemma 3 1B it Q4_K_M**, single model; fallback Gemma QAT Q4_0 for speed. Confirm the owner's choice and license note here.) Research and shortlist 2–3 small instruction models that fit the Stage 1 memory budget and the chosen runtime (Qwen / SmolLM / Gemma small variants in GGUF are leads); record exact model card, revision, license, quantization, file size, language coverage (English + Hebrew).
  - Check: shortlist table in `evidence/model-shortlist.md`; owner approves which weights to download.
- [x] 6.2 Add the inference runtime (llama.cpp at a pinned tag, unless the audit says otherwise) to the NDK build for the phone ABI, with its license recorded.
  - Check: native build succeeds; APK size delta recorded.
- [x] 6.3 `[HARD]` Inference wrapper on its own thread: load model from app-private storage, single-request queue with cancellation, timeout, and memory-pressure release; never on the render or UI thread.
  - Check: loading a model pushed with `adb push` succeeds; cold-load time and resident memory logged.
- [x] 6.4 `[HARD]` Constrained output: prompt v4 (`{"expression":…,"look":…}`, `NONE` = no reaction; `NEUTRAL`+`center` = no change) with its grammar; output always goes through the 5.1 validator. **Speed:** keep the system-prompt state cached; feed grammar-forced tokens (answer prefix and the `","look":"` / `"}` literals) as batched input instead of generating them; threads `-t 6 -tb 6`. Target ≤ 1.5 s p50 on the phone (Stage 2B measured 1.76 s with prefix prefill only; estimate ~1.2–1.4 s).
  - Check: 50 scripted sentences produce ≥ 95 % schema-valid outputs (target to confirm with owner after measuring); every output is validated, none bypass it.
- [x] 6.5 Wire `DEBUG_SAY` → inference → validator → `EyeController`; overlay shows sentence, raw model output, latency, and whether the applied intent came from the model or fallback.
  - Check: one sentence over ADB changes the eyes on the phone; overlay says `model`.
- [x] 6.6 Benchmark the shortlisted models on the phone with the same sentence set: cold load, warm p50/p95 latency, peak memory, valid-schema rate, expression accuracy against an expected-label set, frame-time impact during inference, temperature.
  - Check: `evidence/model-benchmark.md` with numbers per model; smallest model meeting the targets is selected and the choice recorded in [13_DECISIONS_RISKS_AND_OPEN_QUESTIONS.md](../13_DECISIONS_RISKS_AND_OPEN_QUESTIONS.md).

**Stage 6 review:** phone check in airplane mode — Claude sends a set of English and Hebrew sentences (happy, sad, surprised, sleepy, angry, neutral, "look left", etc.); the eyes react correctly, the animation never freezes during inference, and the overlay shows `model` as the source. Killing the model (memory pressure) visibly falls back to `fallback` mode.

### Stage 7: Acceptance and documentation
Close the stage with evidence and updated plans.

- [ ] 7.1 Short smoke session (30 min) with periodic sentences: frame timing, memory, temperature, no crashes.
  - Check: `evidence/stage7-smoke.md` with metrics and logcat summary.
- [ ] 7.2 Evidence record per [12_ENGINEERING_AND_TEST_PLAN.md](../12_ENGINEERING_AND_TEST_PLAN.md): device (sanitized), APK hash, source/model/runtime revisions, steps, observed results, pass/fail/untested.
  - Check: record complete; untested items listed explicitly.
- [ ] 7.3 Update [13_DECISIONS_RISKS_AND_OPEN_QUESTIONS.md](../13_DECISIONS_RISKS_AND_OPEN_QUESTIONS.md) and [11_MVP_AND_IMPLEMENTATION_PHASES.md](../11_MVP_AND_IMPLEMENTATION_PHASES.md) with what P0/P1/P2 now prove and what remains.
  - Check: decisions marked validated/accepted with evidence links.

**Stage 7 review:** owner watches a live demo on the phone: original blue eyes idle, Claude sends sentences, the on-phone model changes the eyes offline. Owner signs off before voice work starts.
