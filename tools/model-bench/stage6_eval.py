"""Low-overhead on-phone evaluation of sentence → eyes (one logcat read per sentence, no polling
while the model runs). Reports accuracy per set and latency per source (model / phrasebook).
Usage: python stage6_eval.py --serial SERIAL --testset FILE --out FILE.txt [--wait 2.5]
"""

import argparse
import json
import re
import shlex
import statistics
import subprocess
import time
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")


def adb(serial, *args):
    return subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True, encoding="utf-8").stdout


def status_for(serial, text):
    for line in reversed(adb(serial, "logcat", "-d", "-s", "ChatiEyes:I").splitlines()):
        if f"in: say: {text}" in line and "result:" in line:
            return line.split("result: ", 1)[1]
    return ""


def judge(result, react, exprs, gaze):
    words = result.split()
    if not words:
        return False
    if not react:
        return words[0] in ("IGNORED_NO_REACTION", "IGNORED_NO_CHANGE")
    if words[0] not in ("APPLIED", "QUEUED"):
        return False
    return words[1] in exprs and (gaze is None or words[2] == gaze)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--testset", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--wait", type=float, default=2.5)
    args = ap.parse_args()
    s = args.serial
    cases = [json.loads(l) for l in Path(args.testset).read_text(encoding="utf-8").splitlines() if l.strip()]

    rows, by_set, lat = [], {}, {"model": [], "phrasebook": []}
    for c in cases:
        adb(s, "logcat", "-c")
        adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_SAY --es text {shlex.quote(c['text'])}")
        time.sleep(args.wait)
        result = status_for(s, c["text"])
        if not result:
            time.sleep(2.0)
            result = status_for(s, c["text"])
        ok = judge(result, c["react"], c.get("expr"), c.get("gaze"))
        source = "phrasebook" if "| phrasebook" in result else "model" if "| model" in result else "?"
        m = re.search(r"\| (?:model|phrasebook) (\d+) ms", result)
        if m:
            lat[source].append(int(m.group(1)))
        by_set.setdefault(c["set"], [0, 0, 0])
        by_set[c["set"]][0] += ok
        by_set[c["set"]][1] += 1
        by_set[c["set"]][2] += source == "phrasebook"
        rows.append(f"{'PASS' if ok else 'FAIL'} {c['id']:5} [{source}] {c['text']} -> {result.split(' applied:')[0]}")
        print(rows[-1], flush=True)
        time.sleep(0.8)  # let the eyes settle a little before the next sentence

    summary = []
    for name, (ok, n, pb) in by_set.items():
        summary.append(f"{name}: {ok}/{n} correct ({pb} answered by the phrasebook)")
    for src, xs in lat.items():
        if xs:
            xs.sort()
            summary.append(f"latency {src}: n={len(xs)} p50={statistics.median(xs)} ms "
                           f"p90={xs[int(0.9 * (len(xs) - 1))]} ms max={xs[-1]} ms")
    stats = [l for l in adb(s, "logcat", "-d", "-s", "ChatiEyes:I").splitlines() if "stats " in l][-1:]
    text = "\n".join(rows + [""] + summary + stats)
    Path(args.out).write_text(text, encoding="utf-8")
    print("\n".join(summary), flush=True)


if __name__ == "__main__":
    main()
