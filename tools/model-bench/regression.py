"""Phone regression measurements. Baselines use the same schema as result.json.

Run from any directory: python regression.py --serial SERIAL --out DIR.
Only main() contacts a device; parsers and gates are device independent.
"""

import argparse
import json
import math
import re
import shlex
import statistics
import sys
import time
from pathlib import Path

import stage6_eval as sentences
import stage6c_story_eval as stories
import stage7_smoke as smoke
import stage9_hebrew_check as hebrew


ROOT = Path(__file__).resolve().parents[2]
AREAS = ("accuracy", "stories", "latency", "voice", "startup", "smoke")
EN3 = hebrew.LINES[0][1]
EN7 = EN3 + " " + hebrew.LINES[3][1]
VOICE_LINES = {"en3": (EN3, None, 3), "en7": (EN7, None, 7),
               "he3": (hebrew.LINES[0][0], EN3, 3)}
STATS_FIELDS = ("frames", "redraws", "presents", "work_avg_us", "work_max_us",
                "int_frames", "int_p99_us", "int_max_us", "int_over50")
AUDIO = re.compile(r"audio-timestamp: sentence (\d+)/(\d+) "
                   r"proxy_t=(\d+) presented_t=(\d+) offset_ms=(-?\d+)")
SPEECH = re.compile(r"speech: (\d+)/(\d+) start t=(\d+)")


def percentile(values, percent):
    """Nearest rank, including the endpoints (not interpolated)."""
    if not values or not 0 <= percent <= 100:
        raise ValueError("percentile needs samples and a percentage in [0, 100]")
    return sorted(values)[max(0, math.ceil(len(values) * percent / 100) - 1)]


def quantiles(values):
    return {f"p{p}": percentile(values, p) for p in (50, 90, 99)} if values else {}


def band_rule(values, baseline_values, within=False):
    """Return stage median, baseline [min,max], and the gate verdict."""
    band = [min(baseline_values), max(baseline_values)] if baseline_values else None
    value = statistics.median(values) if values else None
    passed = None if band is None else value is not None and value <= band[1]
    if passed and within:
        passed = value >= band[0]
    return value, band, passed


def flips(items, baseline_items):
    current = {x["id"]: x["pass"] for x in items}
    return [x["id"] for x in baseline_items if x["pass"] and current.get(x["id"]) is False]


def parse_stats(line):
    if "stats " not in line:
        return None
    fields = {key: int(value) for key, value in
              re.findall(r"\b(\w+)=(-?\d+)", line.split("stats ", 1)[1])}
    return {key: fields[key] for key in STATS_FIELDS} if all(
        key in fields for key in STATS_FIELDS) else None


def parse_audio_timestamp(line):
    match = AUDIO.search(line)
    return dict(zip(("sentence", "total", "proxy_t", "presented_t", "offset_ms"),
                    map(int, match.groups()))) if match else None


def epoch(line):
    match = re.match(r"\s*(\d+\.\d+)\s", line)
    return float(match[1]) if match else None


def parse_voice(log, expected):
    starts, durations, offsets = {}, {}, {}
    t0 = first_epoch = None
    for line in log.splitlines():
        if re.search(r"speak(?: \([^)]*\))?: \d+ sentences", line) and t0 is None:
            t0 = epoch(line)
        match = SPEECH.search(line)
        if match and int(match[2]) == expected:
            index = int(match[1])
            starts[index] = int(match[3])
            if index == 1:
                first_epoch = epoch(line)
            duration = re.search(r"\b(?:audio(?:_ms)?|spoken)=(\d+)(?:\s*ms)?", line)
            if duration:
                durations[index] = int(duration[1])
        # Also accept a separate duration record for the same sentence.
        duration = re.search(r"(?:speech|sentence):? (\d+)/(\d+).*?\baudio(?:_ms)?=(\d+)", line)
        if duration and int(duration[2]) == expected:
            durations[int(duration[1])] = int(duration[3])
        stamp = parse_audio_timestamp(line)
        if stamp and stamp["total"] == expected:
            offsets[stamp["sentence"]] = stamp
    gaps = [starts[i + 1] - starts[i] - durations[i]
            for i in range(1, expected)
            if i in starts and i + 1 in starts and i in durations]
    first = round((first_epoch - t0) * 1000, 3) if (
        first_epoch is not None and t0 is not None) else None
    missing = [{"sentence": i, "missing": fields} for i in range(1, expected + 1)
               if (fields := [name for name, records in
                              (("start", starts), ("duration", durations), ("offset", offsets))
                              if i not in records])]
    complete = (not missing and first is not None and first >= 0 and
                set(starts) == set(range(1, expected + 1)) and
                len(gaps) == expected - 1 and
                set(offsets) == set(range(1, expected + 1)))
    return {"first_audio_ms": first, "starts_ms": starts,
            "audio_ms": durations, "gaps_ms": gaps,
            "audio_timestamps": list(offsets.values()),
            "offsets_ms": [offsets[i]["offset_ms"] for i in sorted(offsets)],
            "complete": complete, "missing": missing}


def read_cases(name):
    path = ROOT / "plan/eyes/evidence" / name
    if not path.is_file():
        raise ValueError(f"Test-set file is missing: {path}")
    cases = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()
             if line.strip()]
    if not cases:
        raise ValueError(f"Test-set file is empty: {path}")
    return cases


class Phone:
    def __init__(self, serial):
        self._serial = serial

    def adb(self, *args):
        return sentences.adb(self._serial, *args)

    def clear(self):
        self.adb("logcat", "-c")

    def log(self):
        return self.adb("logcat", "-d", "-v", "epoch", "-s",
                        "ChatiEyes:I", "ChatiVoice:*", "ChatiBrain:*")

    def send(self, action, text, en=None):
        extra = f" --es en {shlex.quote(en)}" if en else ""
        self.adb("shell", f"am broadcast -a app.mychatibot.{action} "
                 f"--es text {shlex.quote(text)}{extra}")

    def sentence(self, case):
        self.clear()
        result = ""
        for attempt in range(2):
            self.send("DEBUG_SAY", case["text"])
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                result = sentences.status_for(self._serial, case["text"]) or ""
                if result:
                    break
                time.sleep(0.2)
            if result:
                break
        match = re.search(r"\| model (\d+) ms", result)
        row = {"id": case["id"], "set": case["set"], "result": result,
               "pass": sentences.judge(result, case["react"], case.get("expr"), case.get("gaze")) if result else None,
               "model_ms": int(match[1]) if match else None,
               "source": "model" if match else "phrasebook" if "| phrasebook" in result else "missing"}
        time.sleep(0.8)
        return row

    def voice(self, text, en, count):
        self.clear()
        self.send("DEBUG_SPEAK", text, en)
        return self.wait_voice(count)

    def wait_voice(self, count):
        deadline = time.monotonic() + 180
        log = ""
        while time.monotonic() < deadline:
            log = self.log()
            parsed = parse_voice(log, count)
            # All starts, gaps and presentation timestamps are the evidence.
            # The app need not emit a separate state record after the last start.
            if parsed["complete"]:
                # Presentation timestamps arrive at sentence start. Let the last
                # audio finish before the next measured utterance can replace it.
                time.sleep(parsed["audio_ms"].get(count, 0) / 1000)
                return parsed
            time.sleep(0.5)
        parsed = parse_voice(log, count)
        parsed["complete"] = False
        return parsed

    def launch(self):
        self.adb("shell", "am force-stop app.mychatibot")
        self.clear()
        self.adb("shell", "log -p i -t ChatiEyes regression-launch; "
                 "am start -n app.mychatibot/.MainActivity")

    def startup(self, audible=False):
        deadline = time.monotonic() + 120
        self.launch()
        sent = False
        log = ""
        value = None
        while time.monotonic() < deadline:
            log = self.log()
            lines = log.splitlines()
            stamped = [epoch(line) for line in lines if "regression-launch" in line
                       and epoch(line) is not None]
            launch = stamped[-1] if stamped else None
            lines = [line for line in lines if launch is not None and
                     epoch(line) is not None and epoch(line) > launch]
            if audible:
                ready = any("ChatiEyes" in line and
                            ("effects:" in line or "version" in line.lower()) for line in lines)
                if ready and not sent:
                    self.send("DEBUG_SPEAK", EN3)
                    sent = True
                targets = [epoch(line) for line in lines if re.search(r"speech: 1/\d+ start t=", line)]
            else:
                brain = [epoch(line) for line in lines if "ChatiBrain" in line and "warm-up done" in line]
                voice = [epoch(line) for line in lines if "ChatiVoice" in line and "Hebrew voice warm-up done" in line]
                targets = [max(brain[0], voice[0])] if brain and voice else []
            if targets and stamped and targets[0] is not None:
                elapsed = targets[0] - launch
                if 0 <= elapsed <= 120:
                    value = round(elapsed * 1000, 3)
                break
            time.sleep(0.5)
        return {"ms": value, "complete": value is not None}

    def snapshot(self, minute):
        # Reuse the existing collection, retaining the new interval fields.
        before = time.monotonic()
        pid, _, raw = smoke.snapshot(self._serial, f"minute {minute:g}")
        after = time.monotonic()
        stats_lines = [line for line in raw.splitlines() if "stats " in line]
        stats = parse_stats(stats_lines[-1]) if stats_lines else None
        memory = {key: int(value) for key, value in re.findall(r"(VmRSS|VmHWM):\s*(\d+) kB", raw)}
        temperature = re.search(r"temperature:\s*(-?\d+)", raw)
        return {"minute": minute, "sample_monotonic_s": (before + after) / 2,
                "pid": pid, "stats": stats, "memory_kib": memory,
                "temperature_c": int(temperature[1]) / 10 if temperature else None}


def measure_sentences(phone, cases, runs, accuracy, latency):
    phone.sentence(cases[0])  # throw-away: all timing sets are warm
    passes = []
    for _ in range(runs if latency else 1):
        passes.append([phone.sentence(case) for case in cases])
    result = {}
    if accuracy:
        result["accuracy"] = {"items": passes[0], "counts": {
            name: sum(x["pass"] is True for x in passes[0] if x["set"] == name)
            for name in ("dev", "holdout")},
            "missing": {name: sum(not x["result"] for x in passes[0] if x["set"] == name)
                        for name in ("dev", "holdout")},
            "complete": len(passes[0]) == 100 and all(x["result"] for x in passes[0])}
    if latency:
        timed = []
        for rows in passes:
            values = [x["model_ms"] for x in rows if x["model_ms"] is not None]
            timed.append({"samples_ms": values, "items": rows,
                          "metrics": quantiles(values),
                          "complete": bool(rows) and all(x["source"] != "missing" for x in rows)})
        result["latency"] = {"runs": timed, "complete": all(x["complete"] for x in timed)}
    return result


def measure_stories(phone, cases=None):
    rows = []
    for story in read_cases("story-testset.jsonl") if cases is None else cases:
        correct, details, changes, shortest, started = stories.evaluate(
            story, stories.play(phone._serial, story, "--ef wps 2.5", 2.5))
        rows.append({"id": story["id"], "set": story["set"], "correct": correct,
                     "total": len(story["sentences"]), "started": started,
                     "changes": changes, "shortest_ms": shortest, "details": details})
    durations = [x["shortest_ms"] for x in rows if x["shortest_ms"] is not None]
    return {"items": rows, "counts": {name: sum(x["correct"] for x in rows if x["set"] == name)
                                      for name in ("dev", "holdout")},
            "changes": sum(x["changes"] for x in rows),
            "shortest_ms": min(durations) if durations else None,
            "complete": bool(rows) and all(x["started"] == x["total"] for x in rows) and bool(durations)}


def measure_voice(phone, runs):
    lines = {}
    for name, (text, en, count) in VOICE_LINES.items():
        phone.voice(text, en, count)  # throw-away warm-up
        measured = [phone.voice(text, en, count) for _ in range(runs)]
        for run in measured:
            run["metrics"] = {"first_audio_ms": run["first_audio_ms"]}
            run["metrics"].update({f"gap_{k}": v for k, v in quantiles(run["gaps_ms"]).items()})
            run["metrics"].update({f"offset_{k}": v for k, v in quantiles(run["offsets_ms"]).items()})
        lines[name] = {"runs": measured, "complete": all(x["complete"] for x in measured)}
    return {"lines": lines, "complete": all(x["complete"] for x in lines.values())}


def collect_stats(log, records):
    """Retain every epoch-stamped stats window; repeated logcat dumps overlap."""
    for line in log.splitlines():
        stamp, stats = epoch(line), parse_stats(line)
        if stamp is not None and stats is not None and "ChatiEyes" in line:
            records.setdefault(stamp, stats)


def aggregate_stats(records, lower, upper):
    ordered = sorted(records.items())
    windows = [(previous_stamp, stamp, previous, stats)
               for (previous_stamp, previous), (stamp, stats) in zip(ordered, ordered[1:])
               if lower <= previous_stamp < stamp <= upper]
    # Discard the old process and the reset window before accumulating.
    resets = [index for index, (_, _, previous, stats) in enumerate(windows)
              if stats["frames"] < previous["frames"]]
    if resets:
        windows = windows[resets[-1] + 1:]
    if not windows:
        return None, None
    stats = dict(windows[-1][3])
    stats["int_frames"] = sum(row["int_frames"] for _, _, _, row in windows)
    stats["int_over50"] = sum(row["int_over50"] for _, _, _, row in windows)
    for key in ("int_max_us", "int_p99_us"):
        # Max window p99 is conservative, not a pooled percentile.
        stats[key] = max(row[key] for _, _, _, row in windows)
    elapsed = sum(stamp - start for start, stamp, _, _ in windows)
    frames = sum(row["frames"] - previous["frames"] for _, _, previous, row in windows)
    return stats, frames / elapsed if frames >= 0 else None


def measure_smoke(phone, cases, minutes):
    snapshots = [phone.snapshot(0)]
    records = {}
    collect_stats(phone.log(), records)
    boundaries = [max(records) if records else float("-inf")]
    start = time.monotonic()
    end = start + minutes * 60
    next_sentence, next_voice, next_snapshot = start, start, start + 300
    next_poll = start + 30
    events, index = [], 0
    while True:
        now = time.monotonic()
        if now >= end:
            break
        if now >= next_sentence:
            case = cases[index % len(cases)]
            phone.send("DEBUG_SAY", case["text"])
            events.append({"elapsed_s": now - start, "kind": "sentence", "id": case["id"]})
            index += 1
            next_sentence += 15
        if now >= next_voice:
            phone.send("DEBUG_SPEAK", EN3)
            events.append({"elapsed_s": now - start, "kind": "voice"})
            next_voice += 120
        if now >= next_poll:
            collect_stats(phone.log(), records)
            next_poll = now + 30
        if now >= next_snapshot:
            snapshots.append(phone.snapshot((next_snapshot - start) / 60))
            collect_stats(phone.log(), records)
            boundaries.append(max(records) if records else boundaries[-1])
            next_snapshot += 300
        time.sleep(min(0.5, max(0, end - time.monotonic())))
    snapshots.append(phone.snapshot(minutes))
    collect_stats(phone.log(), records)
    boundaries.append(max(records) if records else boundaries[-1])
    intervals = []
    for i, (previous, current) in enumerate(zip(snapshots, snapshots[1:])):
        elapsed = current["sample_monotonic_s"] - previous["sample_monotonic_s"]
        stats, fps = aggregate_stats(records, boundaries[i], boundaries[i + 1])
        ordered = sorted(records.items())
        restarted = (previous["pid"] != current["pid"] or any(
            boundaries[i] < stamp <= boundaries[i + 1] and
            row["frames"] < before["frames"]
            for (_, before), (stamp, row) in zip(ordered, ordered[1:])))
        if restarted:
            stats, fps = None, None
        intervals.append({"elapsed_s": elapsed, "fps": fps,
                          "stats": stats, "restarted": restarted})
    return {"snapshots": snapshots, "intervals": intervals, "events": events,
            "stats_lines": [{"epoch": stamp, "stats": stats} for stamp, stats in sorted(records.items())],
            "complete": bool(intervals) and all(x["fps"] is not None for x in intervals) and all(x["stats"] and len(x["memory_kib"]) == 2 and
                            x["temperature_c"] is not None and x["pid"] for x in snapshots)}


def gate_table(result, baseline):
    """Rows are structured data too, so console and JSON always agree."""
    rows = []

    def add(name, value, band, passed):
        rows.append({"row": name, "value": value, "baseline_band": band,
                     "status": "FAIL" if value is None else
                     "no baseline" if passed is None else "PASS" if passed else "FAIL"})

    def compare(name, value, reference, direction="le", slack=0):
        passed = None if reference is None else value is not None and (
            value >= reference - slack if direction == "ge" else value <= reference + slack)
        add(name, value, None if reference is None else
            [reference - slack, None] if direction == "ge" else [None, reference + slack], passed)

    def timing(name, current, old):
        keys = set().union(*(x.get("metrics", {}).keys() for x in current + old))
        for key in sorted(keys):
            values = [x.get("metrics", {}).get(key) for x in current]
            refs = [x.get("metrics", {}).get(key) for x in old]
            valid = bool(values) and all(isinstance(x, (int, float)) for x in values)
            refs_valid = bool(refs) and all(isinstance(x, (int, float)) for x in refs)
            value, band, passed = band_rule(values if valid else [], refs if refs_valid else [],
                                             within=key.startswith("offset_"))
            add(f"{name}.{key}", value, band, passed)

    for area in AREAS:
        if area not in result:
            continue
        cur, old = result[area], baseline.get(area, {})
        add(f"{area}.data", cur.get("complete", False), "complete", bool(cur.get("complete")))
        if area in ("accuracy", "stories"):
            for split in ("dev", "holdout"):
                compare(f"{area}.{split}", cur.get("counts", {}).get(split),
                        old.get("counts", {}).get(split), "ge")
        if area == "accuracy":
            changed = flips(cur.get("items", []), old.get("items", []))
            add("accuracy.flips", changed, [] if "items" in old else None,
                not changed if "items" in old else None)
        elif area == "stories":
            compare("stories.changes", cur.get("changes"), old.get("changes"))
            compare("stories.shortest_ms", cur.get("shortest_ms"), old.get("shortest_ms"), "ge", 200)
        elif area == "latency":
            timing(area, cur.get("runs", []), old.get("runs", []))
        elif area == "voice":
            for name, line in cur.get("lines", {}).items():
                timing(f"voice.{name}", line.get("runs", []), old.get("lines", {}).get(name, {}).get("runs", []))
        elif area == "startup":
            for name in ("ready", "audible"):
                timing(f"startup.{name}", [{"metrics": {"ms": x["ms"]}} for x in cur.get(name, [])],
                       [{"metrics": {"ms": x["ms"]}} for x in old.get(name, [])])
        elif area == "smoke":
            intervals = cur.get("intervals", [])
            for i, interval in enumerate(intervals):
                stats = interval.get("stats") or {}
                compare(f"smoke.{i}.fps", None if interval.get("restarted") else interval.get("fps"), 59, "ge")
                compare(f"smoke.{i}.int_p99_us", stats.get("int_p99_us"), 25000)
                compare(f"smoke.{i}.int_max_us", stats.get("int_max_us"), 50000)
                compare(f"smoke.{i}.int_over50", stats.get("int_over50"), 0)
            snaps = cur.get("snapshots", [])
            peaks = [x.get("memory_kib", {}).get("VmHWM") for x in snaps]
            compare("smoke.VmHWM_kib", max(peaks) if peaks and all(x is not None for x in peaks) else None,
                    1.8 * 1024 * 1024)
            rss = {x["minute"]: x.get("memory_kib", {}).get("VmRSS") for x in snaps}
            delta = rss[30] - rss[5] if rss.get(30) is not None and rss.get(5) is not None else None
            compare("smoke.RSS_30_minus_5_kib", delta, 20 * 1024)
            old_snaps = old.get("snapshots", [])
            reference = old_snaps[-1].get("temperature_c") if old_snaps else None
            compare("smoke.temperature_c", snaps[-1].get("temperature_c") if snaps else None,
                    reference, slack=1)
            work = [x["stats"]["work_avg_us"] for x in intervals if x.get("stats")]
            refs = [x["stats"]["work_avg_us"] for x in old.get("intervals", []) if x.get("stats")]
            compare("smoke.work_avg_us", max(work) if work else None, max(refs) if refs else None)
    return rows


def print_table(rows):
    print("row | value | baseline band | PASS/FAIL")
    for row in rows:
        print(" | ".join(str(row[key]) for key in ("row", "value", "baseline_band", "status")))


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    serial = None
    for index, arg in enumerate(argv):
        if arg == "--serial" and index + 1 < len(argv):
            serial = argv[index + 1]
        elif arg.startswith("--serial="):
            serial = arg.split("=", 1)[1]
    try:
        return _main(argv)
    except Exception as error:
        message = f"Regression failed: {type(error).__name__}: {error}"
        print(message.replace(serial, "[redacted]") if serial else message, file=sys.stderr)
        return 1


def _main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--skip", default="")
    parser.add_argument("--smoke-minutes", type=float, default=30)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--save-baseline", action="store_true")
    args = parser.parse_args(argv)
    skip = set(filter(None, (x.strip() for x in args.skip.split(","))))
    if args.runs < 1 or not math.isfinite(args.smoke_minutes) or args.smoke_minutes <= 0 or skip - set(AREAS):
        parser.error("positive runs/minutes and known skip areas are required")
    baseline = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline else {}
    cases = read_cases("model-testset-en.jsonl") if {"accuracy", "latency", "smoke"} - skip else []
    story_cases = read_cases("story-testset.jsonl") if "stories" not in skip else []
    args.out.mkdir(parents=True, exist_ok=True)
    result = {"schema_version": 1, "runs": args.runs, "skipped": sorted(skip), "errors": {}}
    phone = Phone(args.serial)

    def collect(area, operation):
        print(f"Measuring {area}...", flush=True)
        try:
            result[area] = operation()
        except Exception as error:
            # Exceptions can contain subprocess argv (and thus the serial).
            result[area] = {"complete": False}
            result["errors"][area] = type(error).__name__

    if {"accuracy", "latency"} - skip:
        print("Measuring sentences...", flush=True)
        try:
            result.update(measure_sentences(phone, cases, args.runs, "accuracy" not in skip, "latency" not in skip))
        except Exception as error:
            for area in {"accuracy", "latency"} - skip:
                result[area] = {"complete": False}
                result["errors"][area] = type(error).__name__
    if "stories" not in skip:
        collect("stories", lambda: measure_stories(phone, story_cases))
    if "voice" not in skip:
        collect("voice", lambda: measure_voice(phone, args.runs))
    if "startup" not in skip:
        def startup():
            ready = [phone.startup() for _ in range(args.runs)]
            audible = [phone.startup(audible=True) for _ in range(args.runs)]
            return {"ready": ready, "audible": audible,
                    "complete": all(x["complete"] for x in ready + audible)}
        collect("startup", startup)
    if "smoke" not in skip:
        collect("smoke", lambda: measure_smoke(phone, cases, args.smoke_minutes))
    result["gates"] = gate_table(result, baseline)
    # Defense in depth: even unexpected device output cannot persist its serial.
    encoded = json.dumps(result, ensure_ascii=False, indent=2).replace(args.serial, "[redacted]")
    (args.out / "result.json").write_text(encoded + "\n", encoding="utf-8")
    if args.save_baseline:
        (args.out / "baseline.json").write_text(encoded + "\n", encoding="utf-8")
    print_table(json.loads(encoded)["gates"])
    if any(x["status"] == "FAIL" for x in result["gates"]):
        return 1
    if not args.save_baseline and any(x["status"] == "no baseline" for x in result["gates"]):
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
