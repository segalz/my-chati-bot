"""Stage 6 phone check: send English sentences through DEBUG_SAY and record what the on-phone
model chose, what the controller applied, and the latency. Usage:
  python stage6_check.py --serial SERIAL --out-dir DIR [--testset model-testset-en.jsonl --set holdout]
"""

import argparse
import json
import shlex
import subprocess
import time
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")

DEMO = [
    ("I got the job I wanted!", True, ["HAPPY", "SURPRISED"], None),
    ("My dog is sick and I'm worried.", True, ["SAD"], None),
    ("Someone stole my bike, I'm furious!", True, ["ANGRY"], None),
    ("Whoa, a shooting star!", True, ["SURPRISED", "HAPPY"], None),
    ("I'm so sleepy, time for bed.", True, ["SLEEPY"], None),
    ("Look to the left.", True, ["NEUTRAL", "SURPRISED", "HAPPY"], "left"),
    ("Can you look up at the ceiling?", True, ["NEUTRAL", "SURPRISED", "HAPPY"], "up"),
    ("The meeting is at three o'clock.", False, None, None),
    ("Just between us, it's a secret.", True, ["WINK_LEFT", "WINK_RIGHT", "HAPPY"], None),
    ("This waiting room is so boring.", True, ["BORED"], None),
]


def adb(serial, *args):
    return subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True, encoding="utf-8").stdout


def say(serial, text):
    adb(serial, "shell", f"am broadcast -a app.mychatibot.DEBUG_SAY --es text {shlex.quote(text)}")


def wait_result(serial, text, timeout=15.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for line in reversed(adb(serial, "logcat", "-d", "-s", "ChatiEyes:I").splitlines()):
            if f"in: say: {text}" in line and "result:" in line:
                return line.split("ChatiEyes: ", 1)[-1]
        time.sleep(0.3)
    return ""


def judge(status, react, exprs, gaze):
    if "result:" not in status:
        return False
    result = status.split("result:", 1)[1]
    if not react:
        return "IGNORED_NO_REACTION" in result or "IGNORED_NO_CHANGE" in result
    if "APPLIED" not in result and "QUEUED" not in result:
        return False
    words = result.split()
    expr, look = words[1], words[2]
    return expr in exprs and (gaze is None or look == gaze)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--testset")
    ap.add_argument("--set", default="holdout")
    ap.add_argument("--shots", action="store_true")
    args = ap.parse_args()
    s, out = args.serial, Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)

    cases = DEMO
    if args.testset:
        cases = []
        for line in Path(args.testset).read_text(encoding="utf-8").splitlines():
            c = json.loads(line)
            if c["set"] == args.set:
                cases.append((c["text"], c["react"], c.get("expr"), c.get("gaze")))

    rows, passed, latencies = [], 0, []
    for i, (text, react, exprs, gaze) in enumerate(cases, 1):
        adb(s, "logcat", "-c")
        say(s, text)
        status = wait_result(s, text)
        ok = judge(status, react, exprs, gaze)
        passed += ok
        ms = None
        if " | model " in status:
            try:
                ms = int(status.split(" | model ", 1)[1].split(" ms", 1)[0])
                latencies.append(ms)
            except ValueError:
                pass
        rows.append(f"{i:2}. {'PASS' if ok else 'FAIL'} [{ms} ms] {text} -> {status.split('result: ', 1)[-1]}")
        print(rows[-1], flush=True)
        if args.shots:
            data = subprocess.run([ADB, "-s", s, "exec-out", "screencap", "-p"], capture_output=True).stdout
            (out / f"s6-{i:02d}.png").write_bytes(data)
        time.sleep(3.5)  # let the reaction show and return to idle before the next sentence

    latencies.sort()
    p50 = latencies[len(latencies) // 2] if latencies else None
    summary = f"passed {passed}/{len(cases)}; latency p50 {p50} ms, max {latencies[-1] if latencies else None} ms"
    stats = [l for l in adb(s, "logcat", "-d", "-s", "ChatiEyes:I").splitlines() if "stats " in l][-1:]
    text = "\n".join(rows + [summary] + stats)
    (out / ("stage6-" + (args.set if args.testset else "demo") + ".txt")).write_text(text, encoding="utf-8")
    print(summary, flush=True)


if __name__ == "__main__":
    main()
