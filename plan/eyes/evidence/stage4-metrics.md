# Stage 4 evidence — original Monochrome Blue eyes on the phone

Date: 2026-10-23 (times Israel). Device: Redmi 10, Android 13. App `app.mychatibot` 0.2.1 (versionCode 3), APK SHA-256 `f4671218b64c2f6ebb1a7ca01b7d5a656241c1c1ffa639ed99db68eb7ba51b9c`, installed by hand from Downloads (11:28). Engine and drawer: espp `expressive_eyes` v1.3.9 + Monochrome Blue, unmodified; LVGL `02814ddb`; canvas 480×360 RGB565 on a centered 4:3 SurfaceView.

Screenshots: [screens/stage4/](./screens/stage4/). Raw logs: [stage4-idle-raw.txt](./stage4-idle-raw.txt), [stage4-lifecycle-raw.txt](./stage4-lifecycle-raw.txt). Scripts: `tools/model-bench/idle_metrics.ps1`, `lifecycle_check.ps1`.

| Check | Result | Evidence |
|---|---|---|
| Launch | Cold start 760 ms; eyes on screen; no crash. Only `System.err` ENOENT comes from MIUI startup before our library loads | `01-launch.png`, logcat |
| 9 expressions | All distinct: NEUTRAL, HAPPY (cheek cut), SAD/ANGRY (brow cuts), SURPRISED (large), SLEEPY/BORED (narrow), WINK_LEFT/RIGHT (one eye a line) | `02…10-expr-*.png`, `sheet-expressions.png` |
| Gaze | Symmetric: left/right x ±56 px, up/down y −68/+64 px (phone pixels) | `11…14-look-*.png`, pixel bbox |
| Color / byte order | Eye pixel = (0,190,255) = upstream `0x00BFFF` after RGB565 → **byte order correct** | pixel sample |
| Reference comparison | Upstream README photo of the Monochrome Blue board ("happy"): blue domes with flat bottoms on black, matching our HAPPY. Low-resolution photo only; no pixel comparison possible | README asset `65f3afd4…` |
| Auto-blink | 41 s without input: 4 frames caught mid-blink (eye height 168–416 px vs 440 open), consistent with the 4 s average interval | `15-auto-blink-midframe.png` |
| Idle 10 min (11:32–11:42) | **59.9 fps** (36,078 frames / 602 s), ~30 engine redraws/s (upstream 0.033 s cap), RssAnon 33.6→33.9 MB (no leak), PSS 48.8 MB, CPU 47 °C flat, battery 34.4→34.5 °C, **no crash** | `stage4-idle-raw.txt` |
| CPU / frame work | ~29 % of one core (2:52 CPU in 10 min); frame work avg 1.5 ms early → 4.0 ms later (likely CPU down-clocking), max 17 ms (25 ms once, first frame after resume) | stats lines |
| Rotation ×10 | 20 activity recreations, same process, no crash, eyes centered 4:3 in landscape. Rotation forced with `wm user-rotation`; original state restored (auto-rotate on, `free`) | `17-landscape.png`, `18-after-rotation.png` |
| Background ×10 | 10/10 real home → return cycles (HOME intent), same process, 0 errors, no crash. **Render thread suspended in background: 0 CPU ticks / 5 s vs 157 in foreground** | `19-after-background.png` |

## Problems met

- 10:59 a stale 0.1.0 APK was reinstalled over 0.2.1 by mistake, killing the first idle run (not a crash). Fixed by reinstalling 0.2.1. The old APK was removed from Downloads.
- MIUI blocks shell key injection (`input keyevent`) and `settings put`. The first background attempt was invalid and was redone with HOME intents.

## Not verified / open

- Owner visual approval of the look (Stage 4 review gate).
- Long soak (> 10 min), sustained thermals, screen-off/locked behavior.
- Pixel-exact comparison with upstream hardware (no hardware available; the README photo only).
- Frame-work growth to ~4 ms: to profile later (LVGL NEON blend, smaller canvas) if needed.
