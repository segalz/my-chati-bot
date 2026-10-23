"""A short choreographed demo of the eyes, driven through the debug channel while the phone
screen is recorded. Usage: python eyes_demo.py --serial SERIAL --out demo.mp4
"""

import argparse
import json
import shlex
import subprocess
import time
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
REMOTE = "/data/local/tmp/chati-demo.mp4"

# (seconds to wait after the step, kind, value)
SCRIPT = [
    (1.5, "raw", "--ez blink true"),                 # waking up
    (1.2, "raw", "--ez blink true"),
    (1.6, "intent", ("NEUTRAL", "left", 1400)),      # looking around
    (1.6, "intent", ("NEUTRAL", "right", 1400)),
    (1.6, "intent", ("NEUTRAL", "up", 1400)),
    (1.6, "intent", ("NEUTRAL", "down", 1400)),
    (2.6, "intent", ("HAPPY", "center", 2400)),      # "hello!"
    (2.4, "intent", ("SURPRISED", "center", 2200)),  # "oh!"
    (2.6, "intent", ("SAD", "down", 2400)),
    (2.6, "intent", ("ANGRY", "center", 2400)),
    (2.6, "intent", ("SLEEPY", "center", 2400)),
    (2.6, "intent", ("BORED", "up", 2400)),
    (1.6, "intent", ("WINK_LEFT", "center", 1400)),  # a wink for each side
    (1.6, "intent", ("WINK_RIGHT", "center", 1400)),
    (2.8, "intent", ("HAPPY", "center", 2600)),      # goodbye smile
    (2.0, "raw", "--ez blink true"),
]


def adb(serial, *args):
    return subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True, encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    s = args.serial

    adb(s, "shell", "am start -n app.mychatibot/.MainActivity")
    time.sleep(1.5)
    adb(s, "shell", "am broadcast -a app.mychatibot.DEBUG_EYES --ez overlay false")
    duration = int(sum(step[0] for step in SCRIPT)) + 3
    recorder = subprocess.Popen([ADB, "-s", s, "shell",
                                 f"screenrecord --verbose --time-limit {duration} --bit-rate 6000000 {REMOTE}"],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    time.sleep(1.5)
    for wait, kind, value in SCRIPT:
        if kind == "raw":
            adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_EYES {value}")
            print("raw", value, flush=True)
        else:
            expr, look, hold = value
            payload = json.dumps({"schema_version": 1, "expression": expr, "look": look, "hold_ms": hold},
                                 separators=(",", ":"))
            adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_INTENT --es json {shlex.quote(payload)}")
            print(expr, look, flush=True)
        time.sleep(wait)
    rec_log, _ = recorder.communicate(timeout=duration + 30)
    print("recorder:", " / ".join(l for l in rec_log.splitlines() if "recorded" in l or "rror" in l), flush=True)
    time.sleep(1)
    adb(s, "pull", REMOTE, args.out)
    adb(s, "shell", f"rm -f {REMOTE}")
    adb(s, "shell", "am broadcast -a app.mychatibot.DEBUG_EYES --ez overlay true")
    print("saved", args.out, Path(args.out).stat().st_size, "bytes", flush=True)


if __name__ == "__main__":
    main()
