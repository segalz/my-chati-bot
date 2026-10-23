"""Stage 7.1 smoke session: sentences at a steady pace for N minutes while the eyes run, with a
snapshot (renderer counters, memory, CPU, temperatures) every few minutes. Writes the raw record
and prints a short summary. No phone settings are changed.
Usage: python stage7_smoke.py --serial SERIAL --testset FILE --out FILE.txt [--minutes 30]
"""

import argparse
import json
import re
import shlex
import statistics
import time
from pathlib import Path

from stage6_eval import adb, judge, status_for

STATS = re.compile(r"stats frames=(\d+) redraws=(\d+) presents=(\d+) work_avg_us=(\d+) work_max_us=(\d+)")


def snapshot(s, label):
    pid = adb(s, "shell", "pidof app.mychatibot").strip()
    lines = [f"=== {label} {adb(s, 'shell', 'date +%H:%M:%S').strip()} pid={pid or 'NONE'}"]
    stats = None
    if pid:
        adb(s, "shell", "am broadcast -a app.mychatibot.DEBUG_EYES --ez stats true")
        time.sleep(0.5)
        found = [l for l in adb(s, "logcat", "-d", "-s", "ChatiEyes:I").splitlines() if "stats " in l]
        if found:
            lines.append(found[-1])
            m = STATS.search(found[-1])
            stats = tuple(map(int, m.groups())) if m else None
        lines.append(adb(s, "shell", f"grep -E 'VmRSS|VmHWM|Threads' /proc/{pid}/status").strip())
        lines.append(adb(s, "shell", f"top -b -n 1 -p {pid} | tail -1").strip())
    lines.append(adb(s, "shell", "dumpsys thermalservice | grep -m1 'mName=CPU'").strip())
    lines.append(adb(s, "shell", "dumpsys battery | grep -E 'temperature|level'").strip())
    return pid, stats, "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--testset", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--minutes", type=float, default=30)
    ap.add_argument("--every", type=float, default=15, help="seconds between sentences")
    ap.add_argument("--snapshot", type=float, default=300, help="seconds between snapshots")
    args = ap.parse_args()
    s = args.serial
    cases = [json.loads(l) for l in Path(args.testset).read_text(encoding="utf-8").splitlines() if l.strip()]
    adb(s, "logcat", "-b", "crash", "-c")

    record, lat, ok_n, sent, missing, pids = [], [], 0, 0, 0, set()
    pid, first_stats, text = snapshot(s, "start")
    pids.add(pid)
    record.append(text)
    print(text, flush=True)
    start = time.time()
    next_snap = start + args.snapshot
    i = 0
    while time.time() - start < args.minutes * 60:
        c = cases[i % len(cases)]
        i += 1
        adb(s, "logcat", "-c")
        adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_SAY --es text {shlex.quote(c['text'])}")
        time.sleep(2.5)
        result = status_for(s, c["text"]) or (time.sleep(2.0) or status_for(s, c["text"]))
        sent += 1
        missing += not result
        ok = judge(result, c["react"], c.get("expr"), c.get("gaze"))
        ok_n += ok
        m = re.search(r"\| (?:model|phrasebook) (\d+) ms", result)
        if m:
            lat.append(int(m.group(1)))
        record.append(f"{time.strftime('%H:%M:%S')} {'PASS' if ok else 'FAIL'} {c['id']} -> {result.split(' applied:')[0]}")
        if time.time() >= next_snap:
            pid, _, text = snapshot(s, f"t+{int((time.time() - start) / 60)}m")
            pids.add(pid)
            record.append(text)
            print(text, flush=True)
            next_snap += args.snapshot
        time.sleep(max(0.0, args.every - 2.5))

    elapsed = time.time() - start
    pid, last_stats, text = snapshot(s, "end")
    pids.add(pid)
    record.append(text)
    crash = adb(s, "logcat", "-d", "-b", "crash").strip()
    record.append("=== crash buffer\n" + (crash or "(empty)"))

    summary = [f"duration {args.minutes:g} min, sentences {sent}, correct {ok_n}, no result {missing}",
               f"app pids seen: {sorted(pids)} ({'no restart' if len(pids) == 1 and '' not in pids else 'RESTART/MISSING'})",
               f"crash buffer: {'empty' if not crash else 'NOT EMPTY'}"]
    if lat:
        lat.sort()
        summary.append(f"latency p50={statistics.median(lat)} ms p90={lat[int(0.9 * (len(lat) - 1))]} ms max={lat[-1]} ms")
    if first_stats and last_stats:
        frames = last_stats[0] - first_stats[0]
        secs = elapsed
        summary.append(f"renderer: {frames} frames (~{frames / secs:.1f}/s), work_avg_us={last_stats[3]}, "
                       f"lifetime work_max_us={last_stats[4]}")
    Path(args.out).write_text("\n".join(record + [""] + summary) + "\n", encoding="utf-8")
    print("\n".join(summary), flush=True)


if __name__ == "__main__":
    main()
