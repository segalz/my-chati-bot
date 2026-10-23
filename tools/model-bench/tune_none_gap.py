"""Offline calibration of the 'no reaction' threshold from stage6_eval output collected with the
threshold OFF. The app rule is deterministic, so simulating it here equals running it on the
phone: a MODEL decision with expression not in {NONE, NEUTRAL}, gaze center and gap < T becomes
NONE. T is chosen on the dev set only; the holdout set is reported, never used for choosing.
Usage: python tune_none_gap.py --testset FILE --eval FILE.txt
"""

import argparse
import json
import re
from pathlib import Path

LINE = re.compile(r"^(PASS|FAIL) (\S+)\s+\[(\w+|\?)\] .* -> (\S+) (\S+) (\S+) \|.*gap (-?[\d.]+)")


def judge(case, outcome, expr, look):
    if not case["react"]:
        return outcome in ("IGNORED_NO_REACTION", "IGNORED_NO_CHANGE")
    if outcome not in ("APPLIED", "QUEUED"):
        return False
    return expr in case["expr"] and (case.get("gaze") is None or look == case["gaze"])


def simulate(rows, cases, t):
    score = {}
    for r in rows:
        case = cases[r["id"]]
        outcome, expr, look = r["outcome"], r["expr"], r["look"]
        if (r["source"] == "model" and outcome in ("APPLIED", "QUEUED") and expr not in ("NONE", "NEUTRAL")
                and look == "center" and r["gap"] < t):
            outcome, expr = "IGNORED_NO_REACTION", "NONE"
        ok, n = score.get(case["set"], (0, 0))
        score[case["set"]] = (ok + judge(case, outcome, expr, look), n + 1)
    return score


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--testset", required=True)
    ap.add_argument("--eval", required=True)
    args = ap.parse_args()
    cases = {c["id"]: c for c in map(json.loads, Path(args.testset).read_text(encoding="utf-8").splitlines()) if c}
    rows = []
    for line in Path(args.eval).read_text(encoding="utf-8").splitlines():
        m = LINE.match(line)
        if m:
            rows.append({"id": m[2], "source": m[3], "outcome": m[4], "expr": m[5], "look": m[6], "gap": float(m[7])})
        elif line.startswith(("PASS", "FAIL")):
            # phrasebook lines have no gap; keep them unchanged
            parts = line.split(" -> ")[1].split()
            rows.append({"id": line.split()[1], "source": "phrasebook", "outcome": parts[0],
                         "expr": parts[1] if len(parts) > 1 else "", "look": parts[2] if len(parts) > 2 else "",
                         "gap": 99.0})
    base = simulate(rows, cases, 0.0)
    best_t, best_dev = 0.0, base["dev"][0]
    for i in range(1, 81):
        t = i * 0.1
        dev = simulate(rows, cases, t)["dev"][0]
        if dev > best_dev:
            best_t, best_dev = t, dev
    chosen = simulate(rows, cases, best_t)
    print(f"rows={len(rows)}  threshold off: dev {base['dev'][0]}/{base['dev'][1]}, holdout {base['holdout'][0]}/{base['holdout'][1]}")
    print(f"chosen on dev: T={best_t:.1f} -> dev {chosen['dev'][0]}/{chosen['dev'][1]}, "
          f"holdout {chosen['holdout'][0]}/{chosen['holdout'][1]}, total {chosen['dev'][0] + chosen['holdout'][0]}/100")
    for t in (0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0):
        s = simulate(rows, cases, t)
        print(f"  T={t:.1f}: dev {s['dev'][0]}  holdout {s['holdout'][0]}  total {s['dev'][0] + s['holdout'][0]}")


if __name__ == "__main__":
    main()
