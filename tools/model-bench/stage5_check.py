"""Stage 5 phone check: send valid and invalid intents through the debug channel and record
what the controller did (status lines from logcat) plus a few screenshots.

Usage: python stage5_check.py --serial SERIAL --out-dir DIR
"""

import argparse
import shlex
import subprocess
import time
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")

CASES = [
    # (label, action, payload, expectation)
    ("valid HAPPY", "INTENT", '{"schema_version":1,"expression":"HAPPY","look":"center"}', "APPLIED"),
    ("bad enum", "INTENT", '{"schema_version":1,"expression":"JOYFUL","look":"center"}', "REJECTED"),
    ("valid SAD down", "INTENT", '{"schema_version":1,"expression":"SAD","look":"down"}', "APPLIED"),
    ("malformed", "INTENT", '{"schema_version":1,"expression":"HAPPY"', "REJECTED"),
    ("NONE", "INTENT", '{"schema_version":1,"expression":"NONE","look":"center"}', "IGNORED_NO_REACTION"),
    ("NEUTRAL center", "INTENT", '{"schema_version":1,"expression":"NEUTRAL","look":"center"}', "IGNORED_NO_CHANGE"),
    ("hold out of range", "INTENT", '{"schema_version":1,"expression":"ANGRY","look":"center","hold_ms":50}', "REJECTED"),
    ("unknown field", "INTENT", '{"schema_version":1,"expression":"ANGRY","look":"center","camera":"on"}', "REJECTED"),
    ("duplicate key", "INTENT", '{"schema_version":1,"expression":"HAPPY","expression":"SAD","look":"center"}', "REJECTED"),
    ("valid SURPRISED left", "INTENT", '{"schema_version":1,"expression":"SURPRISED","look":"left","hold_ms":1500}', "APPLIED"),
    ("sentence (Stage 6)", "SAY", "I got the job!", "NOT AVAILABLE"),
]


def adb(serial, *args):
    return subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True, encoding="utf-8").stdout


def broadcast(serial, action, payload):
    if action == "INTENT":
        cmd = f"am broadcast -a app.mychatibot.DEBUG_INTENT --es json {shlex.quote(payload)}"
    else:
        cmd = f"am broadcast -a app.mychatibot.DEBUG_SAY --es text {shlex.quote(payload)}"
    adb(serial, "shell", cmd)


def last_status(serial):
    lines = [l for l in adb(serial, "logcat", "-d", "-s", "ChatiEyes:I").splitlines() if "mode=" in l]
    return lines[-1].split("ChatiEyes: ", 1)[-1] if lines else ""


def shot(serial, path):
    data = subprocess.run([ADB, "-s", serial, "exec-out", "screencap", "-p"], capture_output=True).stdout
    Path(path).write_bytes(data)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--out-dir", required=True)
    args = ap.parse_args()
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    s = args.serial
    report = []
    adb(s, "logcat", "-c")
    adb(s, "shell", "am broadcast -a app.mychatibot.DEBUG_EYES --ez overlay true")

    for i, (label, action, payload, expect) in enumerate(CASES, 1):
        broadcast(s, action, payload)
        time.sleep(1.2)  # > 0.8 s minimum dwell, so each valid intent applies immediately
        status = last_status(s)
        ok = expect in status
        report.append(f"{i:2}. {'PASS' if ok else 'FAIL'} {label}: expect {expect} | {status}")
        if label in ("valid HAPPY", "bad enum", "valid SURPRISED left", "sentence (Stage 6)"):
            shot(s, out / f"s5-{i:02d}-{label.split()[0].lower()}-{label.split()[-1].lower()}.png")

    # Newest-valid-wins: two intents inside the dwell window.
    broadcast(s, "INTENT", '{"schema_version":1,"expression":"SLEEPY","look":"center"}')
    time.sleep(0.2)
    broadcast(s, "INTENT", '{"schema_version":1,"expression":"BORED","look":"up"}')
    time.sleep(0.4)
    queued = last_status(s)
    time.sleep(1.0)
    after = last_status(s)
    ok = "QUEUED BORED" in queued and "applied: BORED up" in after
    report.append(f"12. {'PASS' if ok else 'FAIL'} dwell/newest wins: queued -> {queued} | after -> {after}")

    # Return to idle after the default 3 s hold.
    time.sleep(3.2)
    idle = last_status(s)
    ok = "NEUTRAL center (idle)" in idle and "mode=idle" in idle
    report.append(f"13. {'PASS' if ok else 'FAIL'} back to idle after hold: {idle}")
    shot(s, out / "s5-13-idle.png")

    crash = adb(s, "logcat", "-d", "-b", "crash").strip()
    report.append(f"crash buffer: {'empty' if not crash else crash[:300]}")
    text = "\n".join(report)
    (out / "stage5-check.txt").write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
