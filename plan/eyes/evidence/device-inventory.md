# Device and toolchain inventory — Stage 1

Date: 2026-10-22. Collected read-only over USB ADB (`getprop`, `/proc`, `dumpsys`, `pm list features`). Serial withheld from shared evidence (ADB reports exactly one physical device, already authorized, state `device`).

## Phone

| Field | Value | Source |
|---|---|---|
| Manufacturer / brand | Xiaomi / Redmi | `ro.product.manufacturer`, `ro.product.brand` |
| Model | Redmi 10 (`21061119AG`, device `selene`) | `ro.product.marketname`, `ro.product.model` |
| OS | Android 13, API 33, MIUI 14 (V14.0.7.0.TKUMIXM), security patch 2024-04-01, `user` build | `ro.build.*`, `ro.miui.ui.version.name` |
| First API level | 30 | `ro.product.first_api_level` |
| SoC | MediaTek MT6769H (Helio G88 family), platform mt6768 | `ro.soc.*`, `ro.board.platform` |
| CPU | 8 cores: 2 × Cortex-A75 (0xd0a) @ 2.0 GHz + 6 × Cortex-A55 (0xd05) @ 1.8 GHz | `/proc/cpuinfo`, `cpuinfo_max_freq` |
| CPU features | `fp asimd aes pmull sha1 sha2 crc32 atomics fphp asimdhp asimdrdm lrcpc dcpop asimddp` — ARMv8.2 with dot-product (`asimddp`); no i8mm/SVE | `/proc/cpuinfo` |
| ABIs | `arm64-v8a` (primary), `armeabi-v7a`, `armeabi` | `ro.product.cpu.abilist` |
| Kernel page size | 4096 | `getconf PAGESIZE` |
| RAM | MemTotal 5,856,236 kB (~5.6 GiB); MemAvailable 2,645,856 kB (~2.5 GiB) at idle with screen on | `/proc/meminfo` |
| Swap (zram) | 4,194,300 kB total, 2,990,084 kB free | `/proc/meminfo` |
| Storage `/data` | 105 G, 80 G available | `df -h` |
| Display | 1080 × 2400, 440 dpi, modes 60 Hz (default) and 90 Hz; top cutout 103 px (centered camera hole, x 505–575); rounded corners r=104 | `wm`, `dumpsys display` |
| Graphics | OpenGL ES 3.2 (`ro.opengles.version` 196610) with AEP; Vulkan 1.1 (`vulkan.version` 4198400), level 1, compute | `getprop`, `pm list features` |
| GPU name | Not captured (SurfaceFlinger dump truncated). Public SoC spec says Mali-G52 MC2 — **unverified on device** | — |
| Sensors present | camera (front + rear), microphone | `pm list features` |
| Battery / thermal at inventory | USB powered, 100 %, 27.7 °C battery, CPU 47 °C, thermal status 0 (none) | `dumpsys battery`, `dumpsys thermalservice` |

## Capability profile and provisional model budget

- **Profile: Balanced** (provisional). 64-bit, ~5.6 GiB RAM, GLES 3.2/Vulkan 1.1, dot-product CPU. Sustained thermal behavior is **untested**.
- **Provisional model memory budget: ≤ 1.0 GiB resident** for the eye-intent model. Derivation: idle MemAvailable ≈ 2.5 GiB − rendering/app (~0.2 GiB) − OS and foreground headroom (~1.0 GiB) − margin for later STT/wake word (~0.3 GiB). MIUI kills background apps aggressively, so the real figure must be measured in Stage 6.
- **Implication for Stage 6 (to be confirmed by benchmark, not selected yet):** sub-1B-parameter instruction models in 4-bit GGUF fit comfortably; ~1–1.5B Q4 may fit the budget but latency on 2 × A75 is the open question. No model is selected or downloaded (requirement R7).
- **Frame-rate target for Stage 4:** 60 Hz default mode (90 Hz available); final target after measurement.

## Windows toolchain installed (Stage 1)

Location `%LOCALAPPDATA%\Android\Sdk` (`ANDROID_HOME` set for the user; user PATH extended with `platform-tools` and `cmdline-tools\latest\bin`). Android SDK license accepted with owner authorization on 2026-10-22 for these packages only.

| Package | Version | Evidence |
|---|---|---|
| cmdline-tools (latest) | archive `commandlinetools-win-16111833_latest.zip`; reports `1.0.16625370 (Android CLI)` | SHA-1 `57d04f2d75eb8e8fffc5000a987e5de4b5a63e9d` matched Google `repository2-3.xml`; SHA-256 `E5885E2E59038C0778A85FC19F01DE7CE7C567C105553F7617B936B1E0E87B2B` |
| platform-tools | 37.0.1 (adb 1.0.41, 37.0.1-15733141) | `adb version` |
| platforms;android-36 | 2.0.0 | `sdkmanager --list_installed` |
| build-tools | 36.1.0 | `sdkmanager --list_installed` |
| NDK | 28.2.13676358 (r28c) | `source.properties` |
| CMake | 3.31.6 (ninja 1.12.1) | `cmake --version` |
| Java (pre-existing) | Microsoft OpenJDK 21.0.12 | `java -version` |

Version rationale: NDK r28c is a current stable release supporting API 33/arm64. CMake 3.31.x chosen over 4.x because CMake 4 removed compatibility with `cmake_minimum_required` < 3.5, which ESP-IDF-era component files may use; revisit after the Stage 2 audit. Notes: `sdkmanager` now prints a deprecation notice pointing to the new `android sdk` CLI and expects `/` package separators (`platforms/android-36`) when called from PowerShell.

## Toolchain-to-device smoke test

A C++20 program was compiled with NDK r28c clang (`--target=aarch64-linux-android33`, static libc++), pushed to `/data/local/tmp`, executed, and deleted:

```
chati ndk smoke ok, cores=8, cxx=202002
exit=0
ls: /data/local/tmp/chati_ndk_smoke: No such file or directory   # cleanup confirmed
```

## Not collected / untested

GPU renderer string; camera capabilities; audio routes; real app permissions; sustained thermal/frame behavior; MIUI background-kill behavior. These belong to later stages.
