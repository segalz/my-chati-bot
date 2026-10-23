# Port audit — expressive_eyes v1.3.9 + Monochrome Blue drawer → Android NDK

Date: 2026-10-22. Provenance: [third_party/expressive_eyes-1.3.9/SOURCE.md](../../../third_party/expressive_eyes-1.3.9/SOURCE.md).

Analysis route (owner's policy): Cline `ask_cline_workspace` on the upstream clone at tag v1.3.9 **failed** (tool-call error: it passed a JSON array as one file path, then timed out after 2 attempts). Fallback chosen by model-picker: **Grok `grok-4.7` high via `xai`, read-only** (run `eyes-s2-audit-grok-1`). Claude spot-verified the load-bearing claims with `grep` (marked ✔).

## Result

**Verdict: conditional GO** (final after the Stage 4 Android build/render proof; see Codex amendments at the end). The engine and the Monochrome Blue drawer can compile **unmodified** for Android arm64 (C++20), provided:

1. an empty shim header `esp_heap_caps.h` is on the include path (included but never called by the engine and the drawer) ✔;
2. `ESP_PLATFORM` stays undefined, so the logger uses `std::chrono::steady_clock` instead of `esp_log_timestamp()` ✔;
3. LVGL (the pinned commit) is built for Android with its software renderer, and libfmt is header-only via `format.hpp`.

The upstream **example `app_main` is not portable**: board Kconfig, LCD init, SPIRAM `heap_caps_malloc`, `espp::Task`. It is replaced by an Android host that mirrors its wiring (same canvas size and format, same eye geometry, same mutex discipline). That host is our code, not a re-creation of the renderer.

## Evidence

### Engine (`components/expressive_eyes`)
- Class `espp::ExpressiveEyes` (`include/expressive_eyes.hpp:30`). `Config`: `screen_width` 320, `screen_height` 240, `eye_spacing` 100, `eye_width` 60, `eye_height` 80, `blink_duration` 0.12 s, `blink_interval` 4.0 s, `enable_auto_blink` true, `enable_pupil_physics` true, `on_draw`, `log_level` (`hpp:115-127`).
- **Expression enum (canonical IDs for the intent contract)** ✔ `hpp:100-110`: `NEUTRAL, HAPPY, SAD, ANGRY, SURPRISED, SLEEPY, BORED, WINK_LEFT, WINK_RIGHT`.
- `update(float dt)`: caller supplies seconds; the engine has no clock ✔ (`hpp:139`). `look_at(float x, float y)` is clamped to [-1, 1] ✔ (`hpp:146`, `cpp:304-307`). `blink()` ✔ (`hpp:157`). `set_expression` / `get_expression` / `get_preset_expression` (`hpp:152-170`).
- Draw callback: `std::function<void(const EyeState& left, const EyeState& right)>` (`hpp:95`). `EyeState` holds `x, y, width, height` plus `ExpressionState` (scales, rotation, curves, offsets, pupil, eyebrow, cheek) (`hpp:66-88`).
- **Redraw is throttled inside the engine**: `MIN_REDRAW_INTERVAL = 0.033f` (~30 fps max) ✔ (`cpp:36`).
- **RNG**: function-local `static std::random_device` + `static std::mt19937` for blink timing, not seedable from outside ✔ (`cpp:21-22`, `cpp:64-65`).
- ESP surface: only `#include <esp_heap_caps.h>` (`cpp:3`), with no `heap_caps_*` call ✔. No FreeRTOS, no Kconfig.

### Monochrome Blue drawer (`example/main/monochrome_blue_drawer.hpp`, with `eye_drawer.hpp`)
- Constructor takes `lv_obj_t* canvas`, `lv_color_t* canvas_buffer`, `std::recursive_mutex&`, and color, default `lv_color_hex(0x00BFFF)` (`:19-26`).
- LVGL calls used ✔: `lv_canvas_fill_bg`, `lv_canvas_init_layer` / `lv_canvas_finish_layer`, `lv_draw_rect` + `lv_draw_rect_dsc_init` (`LV_RADIUS_CIRCLE`), `lv_draw_line` + `lv_draw_line_dsc_init`, `lv_color_black`, `lv_color_hex`. No styles or widgets beyond the canvas.
- Black background, electric-blue (`0x00BFFF`) eye ellipses, black brows and cheeks (cut-outs). Pupils, curves, and `eye_rotation` are not drawn by this drawer.
- Locks the shared `recursive_mutex` per draw (`:48-59`). Unused `#include <esp_heap_caps.h>` ✔ (`:7`). `canvas_buffer_` is stored but never read. No allocation.
- Scales brow and cheek from `screen_width*0.35` and `screen_height*0.55` (`:40-41`).

### Example wiring (to mirror in the Android host)
- Canvas buffer `width*height*sizeof(lv_color_t)`, format `LV_COLOR_FORMAT_RGB565` (`expressive_eyes_example.cpp:71-80`).
- Eye geometry: width `0.35*W`, height `0.55*H`, spacing `0.55*W` (`:110-126`).
- `lv_task_handler()` every 16 ms under `lvgl_mutex` (`:128-142`). `eyes.update(dt)` uses `steady_clock` dt and a 16 ms sleep (`:198-256`). This file has no `lv_tick_inc`; the board layer handles the tick, so the host must supply it.

### Support components
- `base_component.hpp`: std + Logger only. Its C++20 designated initializers make **C++20 the minimum** (`:61-63`).
- `logger.hpp`: ESP includes and `esp_log_timestamp()` only under `#if defined(ESP_PLATFORM)` ✔ (`:9-16`, `:474-478`); steady_clock fallback (`:480-485`). `logger_formatters.hpp` has no includes ✔. `CONFIG_ESPP_LOGGER_LOG_LEVEL` defaults when undefined (`:63-64`).
- `format.hpp`: header-only libfmt (`FMT_HEADER_ONLY`) (`:5-22`).

## Dependency and license inventory (2.5)

| Part | Exact version | License | Used for | Android/NDK notes |
|---|---|---|---|---|
| expressive_eyes engine | espp v1.3.9 (`792c56e1`) | MIT (espp `LICENSE`, © 2022 esp-cpp) | Animation and state | Compiles with `esp_heap_caps.h` shim |
| Monochrome Blue drawer + `eye_drawer.hpp` | same | MIT (same repo; the example files carry no separate header) | Rendering | Needs LVGL 9 canvas and draw APIs |
| base_component, logger, format | espp v1.3.9 | MIT | Logging | `ESP_PLATFORM` undefined |
| libfmt | `93e26fa5` (12.1.1-dev) | MIT-style (fmt LICENSE) | Formatting, header-only | Standard C++ |
| LVGL | `02814ddb` (9.6.0-dev) | MIT (LVGL Kft) | Canvas + software draw | Build `src/` with a custom `lv_conf.h`. Only core + SW renderer; bundled third-party libs (image decoders, fonts beyond default, etc.) **disabled**, and each one enabled later needs its own license check |
| ESP-IDF | ≥ 5.0 in manifests | — | **Not used** on Android | Replaced by shims and the host |

License texts: `third_party/LICENSES/` (espp, LVGL, fmt). Gap: the registry's `license.txt` for 1.3.9 is missing (NoSuchKey); the repository LICENSE at the tag is used instead.

## Portability decision record (2.6)

| Topic | Decision | Rationale / deviation |
|---|---|---|
| Upstream files | Compile engine + blue drawer + base_component/logger/format unmodified from the v1.3.9 tag | R1 |
| ESP shim | `platform/esp_heap_caps.h` (empty) | Only ESP include in those translation units |
| LVGL | Build the exact submodule pin `02814ddb`, not a release tag | Fidelity to v1.3.9. **Risk:** dev snapshot. Owner may later choose v9.6.0. No silent swap. |
| Canvas | `W×H` RGB565 LVGL canvas, buffer from `new`/`malloc` instead of SPIRAM `heap_caps_malloc` | Same format as the example. Allocation call differs only in host code. |
| Display path | LVGL display with no physical driver; after each `lv_task_handler` the canvas pixels are copied to an `ANativeWindow` buffer set to `WINDOW_FORMAT_RGB_565` (no conversion), scaled/letterboxed to 1080×2400 | Replaces the board LCD flush. Simplest exact-pixel path. |
| Canvas resolution | Proposed 320×240 upstream default scaled ×N with nearest-neighbor, or a native-resolution canvas (e.g. 1080×810 band) | **Open — decide in Stage 4** by visual comparison and frame time. Both use the same drawer code. |
| Timing | Host render thread computes `dt` from `steady_clock`, calls `eyes.update(dt)`, `lv_tick_inc`, and `lv_task_handler` under the shared `recursive_mutex`, paced by display vsync (Choreographer/60 Hz) | R3. Note: the engine itself caps redraws at ~30 fps (upstream behavior); the surface still updates at display rate. Changing 0.033 would mean patching upstream, so it is kept. |
| Determinism tests | The engine RNG is not seedable, so tests disable `enable_auto_blink` and drive scripted `update(dt)` / `blink()` / `set_expression` | Plan 4.2 check adjusted (no upstream patch) |
| C++ standard | C++20 (`-std=c++20`, NDK r28c) | `base_component.hpp:61-63` |
| Example `app_main` | Not ported; the Android host mirrors its wiring | Board/Kconfig-specific |

## Metrics

Files analyzed: 9 complete (`expressive_eyes.hpp/.cpp`, `eye_drawer.hpp`, `monochrome_blue_drawer.hpp`, `expressive_eyes_example.cpp`, `base_component.hpp`, `logger.hpp/.cpp`, `format.hpp`) plus `logger_formatters.hpp` (includes only). Not read: `task.hpp` (example only), LVGL internals, `full_featured_drawer.hpp` (out of scope). Grok usage: 188,947 total tokens.

## Review — Codex (`codex/gpt-6-astra/max`, read-only, run `eyes-s2-review-codex-1`)

**Verdict: approve with changes.** Codex confirmed provenance (HEAD `792c56e1` = tag v1.3.9; fmt and LVGL submodule pins), the core API and enum claims, the ESP include locations, the logger guards, and C++20. It found no hidden unconditional ESP/FreeRTOS use and no Xtensa-only constructs. The required changes are accepted and applied as the **binding amendments** below; they override the record above where they differ.

1. **GO is conditional.** It becomes final only after Stage 4 proves an Android compile, link, and render. The fmt and LVGL sources at the pins were not present in the review checkout (Status: blocked for their internals). The upstream `CMakeLists.txt` files use `idf_component_register`, so **we write our own Android CMake targets**. `logger.cpp` must be compiled: it defines `Logger::start_time_`.
2. **Canvas and pixel path must be specified and measured in Stage 4.3 before acceptance:** `sizeof(lv_color_t)` at the pin; canvas stride and alignment (`lv_draw_buf` stride); RGB565 byte order vs `WINDOW_FORMAT_RGB_565` (both expected native little-endian 16-bit, to be confirmed with a known-color test pixel); row-by-row copy respecting `ANativeWindow_Buffer.stride`; whether a canvas without a physical flush driver needs an `lv_display`/`lv_tick` (to be determined from LVGL source at the pin).
3. **Single engine owner.** Engine setters (`set_expression`, `look_at`, `blink`) and `update` have no lock, and the RNGs are shared statics. All engine calls, drawing, and buffer copy run on **one render thread**; other threads post commands through a queue. **Redraw quantization:** the accumulator resets to zero (`cpp:30-42`), so at 60 Hz updates the eyes redraw every 2nd frame (~30 Hz), and at `dt=0.016` every 3rd frame (~20.8 Hz). This is upstream behavior and is kept. **RNG** runs at `init` and after every blink even with auto-blink off, so deterministic tests compare state transitions, not exact blink timings.
4. **Wording corrections.** Eyes are drawn as **rounded rectangles with `LV_RADIUS_CIRCLE`** (not an ellipse primitive). Eyebrow `height`/`color` are also ignored by this drawer. "Unmodified original" means **unmodified upstream source files**: a different canvas resolution keeps the code original but does not reproduce scaled pixels exactly, so Stage 4 compares against an upstream-resolution (320×240) reference render. The logger's `CONFIG_ESPP_LOGGER_ENABLE_CURSOR_COMMANDS` defaults off safely (`logger.hpp:72-79`).

**Stage 2 conclusion:** conditional **GO** for porting the original engine and Monochrome Blue drawer; no owner-blocking issue found. Open item for the owner: LVGL exact pin (9.6.0-dev snapshot, chosen for fidelity) vs release v9.6.0. The default is the exact pin.
