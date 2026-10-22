# Progress: Eyes — Stage 1 (original blue eyes driven by an on-phone model)

Status legend: `[x]` completed (with evidence) · `[!]` failed (reason recorded) · `[~]` untested / deferred (reason recorded) · `[ ]` not started.

Tracks execution of [plan.md](./plan.md). Check off each item as it's completed, and fill in Result with what the check actually showed — not just "done," but what confirmed it (e.g. "adb devices shows 1 device", "screenshot evidence/stage4-happy.png matches upstream"). For delegated steps also record coder unit, reviewer unit, and the `log_run` run_id.

## Stage 1: Toolchain and connected phone — COMPLETED 2026-10-22
Evidence: [evidence/device-inventory.md](./evidence/device-inventory.md). Delegation: none (installation/inventory done by Claude directly; no code analysis involved).

- [x] 1.1 Install Android cmdline-tools, platform-tools, SDK platform/build-tools; set ANDROID_HOME
  - Result: cmdline-tools archive SHA-1 matched Google's repository manifest; `sdkmanager --list_installed` shows platform-tools 37.0.1, platforms/android-36 2.0.0, build-tools 36.1.0; `adb version` = 37.0.1. ANDROID_HOME + user PATH set. SDK license accepted with owner authorization. Note: first install attempt failed for `platforms;android-36`/`build-tools;36.1.0` because cmd splits on `;` — fixed by using `/` separators.
- [x] 1.2 Owner connects phone, USB debugging approved
  - Result: phone was already connected and authorized; `adb devices -l` shows exactly one device, state `device`, Redmi 10 (`21061119AG`, `selene`). No on-phone action was needed.
- [x] 1.3 Read-only hardware inventory → evidence/device-inventory.md
  - Result: Android 13 / API 33, MT6769H, 2×A75 + 6×A55 with dot-product, arm64-v8a, ~5.6 GiB RAM (2.5 GiB available at idle), 80 GB free, 1080×2400 60/90 Hz, GLES 3.2, Vulkan 1.1. GPU renderer string not captured (marked unverified).
- [x] 1.4 Install NDK + CMake matching phone API/ABI
  - Result: NDK 28.2.13676358 (r28c), CMake 3.31.6 + ninja 1.12.1. Proven by an NDK-built arm64 C++20 binary that ran on the phone (`exit=0`) and was removed.
- [x] 1.5 Capability profile and model memory budget
  - Result: provisional profile Balanced; provisional eye-model budget ≤ 1.0 GiB resident (derivation in evidence). No model selected (R7).
- [x] **Stage 1 review** (phone check: ABI matches inventory)
  - Result: `ro.product.cpu.abilist` = `arm64-v8a,armeabi-v7a,armeabi`, matching the inventory; real action = NDK binary executed on the phone with observed output. Untested at this stage: sustained thermal behavior, GPU name, cameras/audio (later stages).

## Stage 2: Exact source, dependency, license, and portability audit
- [ ] 2.1 Download v1.3.9 archive to third_party/, record URL/date/SHA-256
  - Result: 
- [ ] 2.2 Map archive to immutable upstream commit
  - Result: 
- [ ] 2.3 Locate Monochrome Blue Eyes drawer and assets
  - Result: 
- [ ] 2.4 Delegated read-only engine/drawer audit → evidence/port-audit.md
  - Result: 
- [ ] 2.5 Dependency and license inventory
  - Result: 
- [ ] 2.6 Portability decision record (Codex-reviewed)
  - Result: 
- [ ] 2.7 Canonical expression identifiers and gaze/blink API
  - Result: 
- [ ] **Stage 2 review** (owner summary gate)
  - Result: 

## Stage 3: Android project skeleton with native build
- [ ] 3.1 git init + .gitignore + first commit (owner-approved)
  - Result: 
- [ ] 3.2 Gradle project android/ with pinned versions
  - Result: 
- [ ] 3.3 CMake chati_eyes library with nativeVersion()
  - Result: 
- [ ] 3.4 Fullscreen activity with native version overlay
  - Result: 
- [ ] **Stage 3 review** (phone check: app launches, version visible)
  - Result: 

## Stage 4: Port the original engine and Monochrome Blue Eyes renderer
- [ ] 4.1 Upstream sources + blue drawer in CMake, unmodified; PATCHES.md
  - Result: 
- [ ] 4.2 [HARD] Platform adapter (clock, random, logging, ESP/FreeRTOS shims)
  - Result: 
- [ ] 4.3 [HARD] Drawing adapter to ANativeWindow
  - Result: 
- [ ] 4.4 [HARD] Render thread and surface lifecycle
  - Result: 
- [ ] 4.5 [HARD] Narrow JNI bridge
  - Result: 
- [ ] 4.6 Deterministic idle (blink, gaze)
  - Result: 
- [ ] 4.7 Baseline metrics → evidence/stage4-metrics.md
  - Result: 
- [ ] **Stage 4 review** (phone check: all expressions vs upstream, 10 min idle, rotation; owner approves look)
  - Result: 

## Stage 5: Intent contract and debug input channel
- [ ] 5.1 [HARD] EyeIntent schema + strict validator + unit tests
  - Result: 
- [ ] 5.2 [HARD] EyeController (newest-valid, dwell, expiry, fallback)
  - Result: 
- [ ] 5.3 Debug-only broadcast receiver (DEBUG_INTENT, DEBUG_SAY)
  - Result: 
- [ ] 5.4 Debug overlay (input, validation, applied intent, mode)
  - Result: 
- [ ] **Stage 5 review** (phone check: 10 raw intents over ADB)
  - Result: 

## Stage 6: On-phone small model: sentence → intent
- [ ] 6.1 Model shortlist (owner approves weights download)
  - Result: 
- [ ] 6.2 Inference runtime in NDK build (pinned, licensed)
  - Result: 
- [ ] 6.3 [HARD] Inference wrapper thread (load, queue, cancel, timeout, memory release)
  - Result: 
- [ ] 6.4 [HARD] Constrained JSON output + validation (50 sentences)
  - Result: 
- [ ] 6.5 DEBUG_SAY → model → validator → controller
  - Result: 
- [ ] 6.6 On-phone benchmark and model selection → evidence/model-benchmark.md
  - Result: 
- [ ] **Stage 6 review** (phone check: airplane mode, English + Hebrew sentences, source = model)
  - Result: 

## Stage 7: Acceptance and documentation
- [ ] 7.1 30-min smoke session → evidence/stage7-smoke.md
  - Result: 
- [ ] 7.2 Evidence record per test plan
  - Result: 
- [ ] 7.3 Update decisions (13) and phases (11)
  - Result: 
- [ ] **Stage 7 review** (owner live demo and sign-off)
  - Result: 
