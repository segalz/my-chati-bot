"""Stage 9 phone check: speaks Hebrew lines (with English for the eyes) one after another and prints
the time to first audio, the speech length and what the eyes did per sentence.
Usage: python stage9_hebrew_check.py --serial SERIAL
"""

import argparse
import re
import shlex
import time

from stage6_eval import adb

LINES = [
    ("הזמנתי טלפון לפני שבועיים והוא עדיין לא הגיע! ועכשיו הם מסרבים להחזיר לי את הכסף. זה פשוט מגוחך!",
     "I ordered a phone two weeks ago and it still has not arrived! And now they refuse to give my money back. This is ridiculous!"),
    ("נכנסתי הביתה והאורות היו כבויים. פתאום כולם קפצו וצעקו הפתעה! לא האמנתי!",
     "I walked into my home and the lights were off. Suddenly everyone jumped out and shouted surprise! I could not believe it!"),
    ("היה יום ארוך מאוד. עבדתי שתים עשרה שעות. אני בקושי מצליח להחזיק את העיניים פתוחות.",
     "It has been a very long day. I worked for twelve hours. I can barely keep my eyes open."),
    ("ירד גשם כל השבוע והרגשתי בודד. ואז אחותי התקשרה. היא באה לבקר מחר! אנחנו הולכים לאפות עוגיות ביחד.",
     "It has been raining all week and I felt lonely. Then my sister called me. She is coming to visit tomorrow! We are going to bake cookies together."),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    s = ap.parse_args().serial
    for he, en in LINES:
        adb(s, "logcat", "-c")
        sent = time.time()
        adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_SPEAK --es text {shlex.quote(he)} --es en {shlex.quote(en)}")
        n = None
        log = ""
        deadline = time.time() + 60
        while time.time() < deadline:
            log = adb(s, "logcat", "-d", "-s", "ChatiVoice:*", "ChatiEyes:I")
            m = re.search(r"speak \(cloud\): (\d+) sentences", log)
            n = int(m.group(1)) if m else n
            if n and (f"speech: {n}/{n}" in log or "unavailable" in log):
                break
            time.sleep(0.5)
        time.sleep(4)
        log = adb(s, "logcat", "-d", "-s", "ChatiVoice:*", "ChatiEyes:I")
        first = re.search(r"first audio after (\d+) ms", log)
        ended = re.search(r"stream ended, (\d+) ms of speech", log)
        failed = re.search(r"cloud voice failed: (.*)", log)
        print(f"\n{en}")
        print(f"  first audio {first.group(1) if first else '-'} ms, speech {ended.group(1) if ended else '-'} ms"
              + (f", FAILED: {failed.group(1)}" if failed else ""))
        seen = set()
        for line in log.splitlines():
            m = re.search(r"result: (\S+ \S+ \S+) \|.*\| story (\d+/\d+)", line)
            if m and m.group(2) not in seen:
                seen.add(m.group(2))
                print(f"  {m.group(2)}: {m.group(1)}")
        time.sleep(2)


if __name__ == "__main__":
    main()
