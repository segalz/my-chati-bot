"""Stage 6C story evaluation on the phone: plays each story with DEBUG_STORY, then rebuilds from
logcat what the eyes showed over time and checks, at the middle of every sentence, whether that
matches the label (in context). Also counts expression changes and the shortest display.
Usage: python stage6c_story_eval.py --serial SERIAL --testset FILE --out FILE.txt
       [--set dev|holdout|all] [--linger MS] [--carry MS] [--dwell MS] [--wps F]
"""

import argparse
import json
import re
import shlex
import time
from pathlib import Path

from stage6_eval import adb

START = re.compile(r"story: (\d+)/(\d+) start t=(\d+) planned=\d+ spoken=(\d+)")
STATE = re.compile(r"t=(\d+) mode=.* applied: (\w+) (\w+)")
DECISION = re.compile(r"result: (\w+ \w+ \w+|[A-Z ]+?) \|.*\| story (\d+)/\d+")


def spoken_ms(sentence, wps):
    words = sum(1 for w in sentence.split(" ") if any(c.isalnum() for c in w))
    return max(1200, int(words * 1000 / wps) + 400)


def play(s, story, extras, wps):
    text = " ".join(x["text"] for x in story["sentences"])
    adb(s, "logcat", "-c")
    adb(s, "shell", f"am broadcast -a app.mychatibot.DEBUG_STORY {extras} --es text {shlex.quote(text)}")
    total = sum(spoken_ms(x["text"], wps) for x in story["sentences"])
    time.sleep(total / 1000 + 9)  # first decision + linger/carry tail
    return adb(s, "logcat", "-d", "-s", "ChatiEyes:I").splitlines()


def evaluate(story, lines):
    starts, states, decisions = {}, [], {}
    for line in lines:
        m = DECISION.search(line)
        if m and "in: say:" in line:
            decisions[int(m[2]) - 1] = m[1]
        m = START.search(line)
        if m:
            starts[int(m[1]) - 1] = (int(m[3]), int(m[4]))
        m = STATE.search(line)
        if m:
            states.append((int(m[1]), m[2], m[3]))
    rows, ok = [], 0
    for i, x in enumerate(story["sentences"]):
        if i not in starts:
            rows.append(f"  MISS {i + 1} {x['text']} (no start logged)")
            continue
        t0, sp = starts[i]
        mid = t0 + sp // 2
        shown = [st for st in states if st[0] <= mid]
        expr, look = (shown[-1][1], shown[-1][2]) if shown else ("NEUTRAL", "center")
        good = expr in x["expect"] and (x.get("look") is None or look == x["look"])
        ok += good
        want = "/".join(x["expect"]) + (f" look {x['look']}" if x.get("look") else "")
        rows.append(f"  {'PASS' if good else 'FAIL'} {i + 1} shown {expr} {look} | want {want} | "
                    f"decided {decisions.get(i, '?')} | {x['text']}")
    # expression changes and display lengths inside the story
    if starts:
        begin = min(t for t, _ in starts.values())
        timeline = [st for st in states if st[0] >= begin]
        changes, durations, last = 0, [], None
        for t, expr, _ in timeline:
            if last is None or expr != last[1]:
                if last is not None:
                    changes += 1
                    if last[1] != "NEUTRAL":
                        durations.append(t - last[0])
                last = (t, expr)
        shortest = min(durations) if durations else None
    else:
        changes, shortest = 0, None
    return ok, rows, changes, shortest, len(starts)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--testset", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--set", default="all")
    ap.add_argument("--linger", type=int)
    ap.add_argument("--carry", type=int)
    ap.add_argument("--dwell", type=int)
    ap.add_argument("--wps", type=float, default=2.5)
    args = ap.parse_args()
    s = args.serial
    stories = [json.loads(l) for l in Path(args.testset).read_text(encoding="utf-8").splitlines() if l.strip()]
    stories = [st for st in stories if args.set == "all" or st["set"] == args.set]
    extras = f"--ef wps {args.wps}"
    for key, val in (("linger_ms", args.linger), ("carry_ms", args.carry), ("dwell_ms", args.dwell)):
        if val is not None:
            extras += f" --ei {key} {val}"

    out, totals = [f"params: {extras}"], {}
    for st in stories:
        ok, rows, changes, shortest, started = evaluate(st, play(s, st, extras, args.wps))
        n = len(st["sentences"])
        head = f"{st['id']} [{st['set']}] {ok}/{n} correct, {changes} changes, shortest display {shortest} ms"
        if started != n:
            head += f", only {started}/{n} sentences started"
        out += [head] + rows
        print(head, flush=True)
        t = totals.setdefault(st["set"], [0, 0, 0])
        t[0] += ok
        t[1] += n
        t[2] += changes
    summary = [f"{k}: {v[0]}/{v[1]} correct, {v[2]} changes" for k, v in totals.items()]
    Path(args.out).write_text("\n".join(out + [""] + summary) + "\n", encoding="utf-8")
    print("\n".join(summary), flush=True)


if __name__ == "__main__":
    main()
