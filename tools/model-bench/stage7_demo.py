"""Stage 7 review: live demo on the phone (offline). Idle eyes, single sentences (eye model),
a silent story, then a spoken story (voice + eyes). Prints what the phone decided.
Usage: python stage7_demo.py --serial SERIAL
"""

import argparse
import shlex
import time

from stage6_eval import adb, status_for

SENTENCES = [
    "I passed my driving test!",
    "My grandmother is in the hospital.",
    "Look to the left, please.",
    "The meeting is at three o'clock.",
    "Someone stole my bike again!",
    "Wow, I did not expect that at all!",
]
SILENT_STORY = ("Today we had a three hour meeting. The manager read every slide out loud. "
                "I almost fell asleep. Then he announced that the company was sold! Everyone got a big bonus.")
SPOKEN_STORY = ("It has been raining all week. I feel so lonely. "
                "Then my sister called me. She is coming to visit tomorrow! We are going to bake cookies together.")


def step(title):
    print(f"\n== {time.strftime('%H:%M:%S')} {title}", flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    s = ap.parse_args().serial

    step("1. idle eyes (5 s)")
    time.sleep(5)

    step("2. single sentences -> on-phone eye model")
    for text in SENTENCES:
        adb(s, "logcat", "-c")
        adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_SAY --es text {shlex.quote(text)}")
        time.sleep(2.5)
        result = status_for(s, text) or (time.sleep(1.5) or status_for(s, text))
        print(f"  {text} -> {result.split(' applied:')[0]}", flush=True)
        time.sleep(3.0)

    step("3. silent story (eyes only, speaking pace)")
    adb(s, "logcat", "-c")
    adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_STORY --es text {shlex.quote(SILENT_STORY)}")
    time.sleep(22)
    for line in adb(s, "logcat", "-d", "-s", "ChatiEyes:I").splitlines():
        if "result:" in line and "| story" in line:
            print("  " + line.split("result: ")[1].split(" | ")[0] + "  (" + line.split("| story ")[1].split()[0] + ")")

    step("4. spoken story (voice + eyes)")
    adb(s, "logcat", "-c")
    adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_SPEAK --es text {shlex.quote(SPOKEN_STORY)}")
    deadline = time.time() + 40
    while time.time() < deadline and "speech: 5/5" not in adb(s, "logcat", "-d", "-s", "ChatiVoice:I"):
        time.sleep(1)
    time.sleep(3)
    for line in adb(s, "logcat", "-d", "-s", "ChatiEyes:I").splitlines():
        if "result:" in line and "| story" in line:
            print("  " + line.split("result: ")[1].split(" | ")[0] + "  (" + line.split("| story ")[1].split()[0] + ")")
    print("\n== demo finished", flush=True)


if __name__ == "__main__":
    main()
