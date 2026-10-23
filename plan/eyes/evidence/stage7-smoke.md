# Stage 7.1 smoke session

Date: 2026-10-23, 15:20–15:50 (Israel time). Phone: Redmi 10 (see [device-inventory.md](./device-inventory.md)), on USB power, network on. App 0.5.2 (debug APK, SHA-256 `9508bec4e30e8d4c1d231d10c6826a157aa0ae0126254ef487286cef37a1b7e3`), model Gemma 3 1B it Q4_K_M, 6 threads, NONE threshold off.
Method: `tools/model-bench/stage7_smoke.py` sent one English sentence every 15 s through `DEBUG_SAY`, cycling through [model-testset-en.jsonl](./model-testset-en.jsonl). It took a snapshot every 5 min. Raw record: [stage7-smoke-raw.txt](./stage7-smoke-raw.txt).

| Check | Result |
|---|---|
| Duration / sentences | 30 min / 117 sentences, every one answered (0 missing) |
| Correct reactions | 103/117 (88 %), the same rate as the full set |
| Latency sentence → eyes | p50 945 ms, p90 1143 ms, max 1505 ms |
| Frame rate | 108,155 frames in 30 min = **60.0 fps**; average frame work 2.3 ms; no new worst frame during the run (lifetime max 26.7 ms, set earlier) |
| Memory (RSS) | 986–994 MB the whole run, flat, so no leak. Peak (VmHWM) 1.65 GB comes from earlier model switching in this process, not from this run |
| Threads | 43 → 42 |
| Process CPU (sampled) | 18–31 % |
| Temperature | CPU sensor 47 °C, unchanged; battery 30.0–31.4 °C |
| Crashes / restarts | crash buffer empty; same process (no restart) for the whole run |

**Result: PASS.** The run was stable: steady 60 fps, flat memory, no heating, no crashes.
Limitations: the phone was on USB power (no battery-drain figure). The CPU sensor reading did not move, so battery temperature is the more useful heat signal. This was 30 minutes, not an all-day run.
