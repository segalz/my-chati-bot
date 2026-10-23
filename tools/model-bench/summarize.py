"""Summarize run_bench.py result files into a comparison table (Markdown)."""

import json
import statistics
import sys
from pathlib import Path


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, round(p / 100 * (len(xs) - 1)))] if xs else None


def rate(rows, key):
    vals = [r[key] for r in rows if key in r]
    return f"{sum(vals)}/{len(vals)}" if vals else "-"


def summarize(run):
    out = {"model": run["model"], "load_s": run["load_s"],
           "first_ms": run["first_request_ms"], "peak_rss_mb": run["peak_rss_mb"],
           "anon_mb": run.get("rss_anon_mb"),
           "temp": f"{run['temp_before'][0]}→{run['temp_after'][0]} °C CPU"}
    for subset in sorted({r["set"] for r in run["results"]}):
        rows = [r for r in run["results"] if r["set"] == subset]
        if not rows:
            continue
        lat = [r["wall_ms"] for r in rows]
        gen = [r["timings"].get("predicted_per_second") for r in rows if r.get("timings")]
        out[subset] = {
            "n": len(rows),
            "full": f"{sum(bool(r.get('valid') and r.get('react_ok', True) and r.get('expr_ok', True) and r.get('gaze_ok', True)) for r in rows)}/{len(rows)}",
            "valid": rate(rows, "valid"),
            "react": rate(rows, "react_ok"),
            "expr": rate(rows, "expr_ok"),
            "gaze": rate(rows, "gaze_ok"),
            "p50_ms": statistics.median(lat),
            "p95_ms": pct(lat, 95),
            "gen_tok_s": round(statistics.median([g for g in gen if g]), 1) if any(gen) else None,
        }
    return out


def main():
    runs = []
    for f in sys.argv[1:]:
        runs += json.loads(Path(f).read_text(encoding="utf-8"))
    for run in runs:
        s = summarize(run)
        print(f"## {s['model']}  load {s['load_s']} s, first {s['first_ms']} ms, "
              f"peak RSS {s['peak_rss_mb']} MB, anon {s['anon_mb']} MB, {s['temp']}")
        print("| set | n | full | valid | react | expr | gaze | p50 ms | p95 ms | gen tok/s |")
        print("|---|---|---|---|---|---|---|---|---|---|")
        for subset in sorted({r["set"] for r in run["results"]}):
            if subset in s:
                d = s[subset]
                print(f"| {subset} | {d['n']} | {d['full']} | {d['valid']} | {d['react']} | {d['expr']} | "
                      f"{d['gaze']} | {d['p50_ms']} | {d['p95_ms']} | {d['gen_tok_s']} |")
        misses = [r for r in run["results"] if not (r.get("valid") and r.get("react_ok", True)
                  and r.get("expr_ok", True) and r.get("gaze_ok", True))]
        print("misses: " + "; ".join(f"{r['id']}={' '.join(r['output'].split())}" for r in misses))
        print()


if __name__ == "__main__":
    main()
