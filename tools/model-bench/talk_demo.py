"""Record the phone screen while sentences are sent to the on-phone eye model (no log polling
during the recording, so timing is what a user would see). Usage:
  python talk_demo.py --serial SERIAL --out demo.mp4
"""

import argparse
import shlex
import subprocess
import time
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
REMOTE = "/data/local/tmp/chati-talk.mp4"
SENTENCES = [
    "Hello! I just got the job I wanted!",
    "But my dog is sick and I'm worried.",
    "Look to the left, someone is at the door.",
    "Someone stole my bike, I'm furious!",
    "Whoa, look at that shooting star!",
    "This waiting room is so boring.",
    "I'm so sleepy, time for bed.",
]
GAP_S = 5.0


def adb(serial, *args):
    return subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True, encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    s = args.serial
    duration = int(len(SENTENCES) * GAP_S) + 4
    adb(s, "logcat", "-c")
    recorder = subprocess.Popen([ADB, "-s", s, "shell", f"screenrecord --verbose --time-limit {duration} {REMOTE}"],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    time.sleep(2)
    for text in SENTENCES:
        adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_SAY --es text {shlex.quote(text)}")
        print(time.strftime("%H:%M:%S"), "say:", text, flush=True)
        time.sleep(GAP_S)
    log, _ = recorder.communicate(timeout=duration + 30)
    print("recorder:", " / ".join(l for l in log.splitlines() if "recorded" in l), flush=True)
    adb(s, "pull", REMOTE, args.out)
    adb(s, "shell", f"rm -f {REMOTE}")
    for line in adb(s, "logcat", "-d", "-s", "ChatiEyes:I").stdout.splitlines():
        if "in: say:" in line and "result:" in line:
            print(line.split("ChatiEyes: ", 1)[-1].replace("mode=", "\n  mode="), flush=True)


if __name__ == "__main__":
    main()
