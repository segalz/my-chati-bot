"""Eye-color check on the phone: speaks Hebrew lines (English for the eyes) with different emotions,
takes a screenshot in the middle of each sentence and prints the eyes' dominant color, plus one
screenshot while waiting. Usage: python color_check.py --serial SERIAL
"""

import argparse
import shlex
import subprocess
import time
from collections import Counter
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
LINES = [
    ("קיבלתי את העבודה במאפייה! אני כל כך שמח!", "I got the job at the bakery! I am so happy!"),
    ("הכלב שלי נפטר אתמול. אני מתגעגע אליו מאוד.", "My dog died yesterday. I miss him very much."),
    ("הם מסרבים להחזיר לי את הכסף! זה מקומם!", "They refuse to give my money back! This is outrageous!"),
    ("מה? זכיתי בלוטו? לא ייאמן!", "What? I won the lottery? Unbelievable!"),
]


def adb(s, *args, binary=False):
    r = subprocess.run([ADB, "-s", s, *args], capture_output=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def eye_color(s):
    """Dominant non-black color of the middle band of the screen (raw screencap, no image library)."""
    raw = adb(s, "exec-out", "screencap", binary=True)
    w, h = int.from_bytes(raw[0:4], "little"), int.from_bytes(raw[4:8], "little")
    header = len(raw) - w * h * 4  # 12 or 16 bytes depending on the Android version
    px = raw[header:]
    buckets, sums = Counter(), {}
    for y in range(int(h * 0.2), int(h * 0.8), 8):
        row = y * w * 4
        for x in range(0, w, 8):
            i = row + x * 4
            r, g, b = px[i], px[i + 1], px[i + 2]
            if max(r, g, b) <= 60:
                continue  # black background
            k = (r // 32, g // 32, b // 32)
            buckets[k] += 1
            sr, sg, sb, n = sums.get(k, (0, 0, 0, 0))
            sums[k] = (sr + r, sg + g, sb + b, n + 1)
    if not buckets:
        return "none"
    sr, sg, sb, n = sums[buckets.most_common(1)[0][0]]
    return "#%02X%02X%02X" % (sr // n, sg // n, sb // n)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    s = ap.parse_args().serial
    print("waiting:", eye_color(s))
    for he, en in LINES:
        adb(s, "logcat", "-c")
        adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_SPEAK --es text {shlex.quote(he)} --es en {shlex.quote(en)}")
        n = 0
        deadline = time.time() + 20
        while time.time() < deadline and n == 0:
            for line in adb(s, "logcat", "-d", "-s", "ChatiVoice:I").splitlines():
                if "speak:" in line and " sentences" in line:
                    n = int(line.split("speak: ")[1].split()[0])
            time.sleep(0.2)
        for k in range(1, n + 1):
            deadline = time.time() + 20
            while time.time() < deadline and f"speech: {k}/{n}" not in adb(s, "logcat", "-d", "-s", "ChatiVoice:I"):
                time.sleep(0.2)
            time.sleep(0.8)  # into the sentence, after the 0.3 s fade
            shown = ""
            for line in adb(s, "logcat", "-d", "-s", "ChatiEyes:I").splitlines():
                if f"| story {k}/{n}" in line and "result:" in line:
                    shown = line.split("result: ")[1].split(" | ")[0]
            print(f"{en.split('!')[0][:40]:40}  sentence {k}: {shown:28} color {eye_color(s)}", flush=True)
        time.sleep(4)
    time.sleep(4)
    print("waiting again:", eye_color(s))


if __name__ == "__main__":
    main()
