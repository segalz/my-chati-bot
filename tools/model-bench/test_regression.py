"""Device-free tests: python -m unittest discover -s tools/model-bench -p test_regression.py."""

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import regression as r


class RegressionTests(unittest.TestCase):
    def test_nearest_rank(self):
        values = list(range(1, 101))
        self.assertEqual([r.percentile(values, p) for p in (0, 50, 90, 99, 100)], [1, 50, 90, 99, 100])
        self.assertEqual(r.percentile([9, 1, 3], 50), 3)
        with self.assertRaises(ValueError):
            r.percentile([], 90)

    def test_band_edges(self):
        self.assertEqual(r.band_rule([12, 10, 11], [8, 11, 9]), (11, [8, 11], True))
        self.assertFalse(r.band_rule([12, 12, 9], [8, 11, 9])[2])
        self.assertTrue(r.band_rule([1], [8, 11, 9])[2])
        self.assertFalse(r.band_rule([1], [8, 11, 9], within=True)[2])
        self.assertIsNone(r.band_rule([1], [])[2])
        self.assertFalse(r.band_rule([], [1])[2])

    def test_flips_exclude_missing_items(self):
        baseline = [{"id": "a", "pass": True}, {"id": "b", "pass": False}, {"id": "c", "pass": True}]
        self.assertEqual(r.flips([{"id": "a", "pass": False}, {"id": "b", "pass": True}], baseline), ["a"])

    def test_stats(self):
        line = "ChatiEyes: stats frames=100 redraws=80 presents=99 work_avg_us=12 work_max_us=80 int_frames=60 int_p99_us=20000 int_max_us=40000 int_over50=0"
        self.assertEqual(r.parse_stats(line)["int_p99_us"], 20000)
        self.assertEqual(r.parse_stats(line)["frames"], 100)
        self.assertIsNone(r.parse_stats("stats frames=1"))
        self.assertIsNone(r.parse_stats("unrelated"))

    def test_audio_timestamp(self):
        self.assertEqual(r.parse_audio_timestamp("ChatiVoice: audio-timestamp: sentence 2/3 proxy_t=1000 presented_t=997 offset_ms=-3"),
                         {"sentence": 2, "total": 3, "proxy_t": 1000, "presented_t": 997, "offset_ms": -3})
        self.assertIsNone(r.parse_audio_timestamp("audio-timestamp: missing"))

    def test_voice_epoch_and_uptime_are_not_mixed(self):
        log = "1700000000.000 I ChatiVoice: speak: 2 sentences\n"
        log += "1700000000.100 I ChatiVoice: speech: 1/2 start t=3000 audio=500\n"
        log += "1700000000.800 I ChatiVoice: speech: 2/2 start t=3700 audio=600\n"
        for i in (1, 2):
            log += f"audio-timestamp: sentence {i}/2 proxy_t=3000 presented_t=3002 offset_ms=2\n"
        parsed = r.parse_voice(log, 2)
        self.assertEqual(parsed["first_audio_ms"], 100)
        self.assertEqual(parsed["gaps_ms"], [200])
        self.assertTrue(parsed["complete"])
        self.assertEqual(parsed["missing"], [])
        self.assertFalse(r.parse_voice(log.replace("audio-timestamp:", "missing-timestamp:"), 2)["complete"])

    def test_voice_needs_no_state_after_final_start(self):
        phone = r.Phone("PRIVATE_DEVICE")
        log = ("1700000000.000 I ChatiVoice: speak: 1 sentences\n"
               "1700000000.100 I ChatiVoice: speech: 1/1 start t=3000 audio=500\n"
               "audio-timestamp: sentence 1/1 proxy_t=3000 presented_t=3002 offset_ms=2\n")
        with patch.object(phone, "adb", side_effect=AssertionError("No device clock reads")), \
                patch.object(phone, "log", side_effect=["state: idle\n" + log, log,
                                                        log + "state: idle t=3500\n"]) as logs, \
                patch.object(r.time, "monotonic", side_effect=[0, 0, 1, 2]), \
                patch.object(r.time, "sleep"):
            self.assertTrue(phone.wait_voice(1)["complete"])
            self.assertEqual(logs.call_count, 1)
        with patch.object(phone, "log", return_value=log.replace("audio-timestamp:", "missing-timestamp:")), \
                patch.object(r.time, "monotonic", side_effect=[0, 0, 181]), \
                patch.object(r.time, "sleep"):
            self.assertFalse(phone.wait_voice(1)["complete"])
        self.assertNotIn("/proc/uptime", Path(r.__file__).read_text(encoding="utf-8"))

    def test_sentence_polls_until_result_or_timeout(self):
        phone = r.Phone("PRIVATE_DEVICE")
        case = {"id": "a", "set": "dev", "text": "hello", "react": False}
        for responses, ticks, expected in [
                (["", "", "result | model 12 ms"], [0, 0, 0.2, 0.4], 12),
                (["", ""], [0, 0, 15, 15, 15, 30], None)]:
            with self.subTest(expected=expected), patch.object(phone, "clear"), \
                    patch.object(phone, "send"), \
                    patch.object(r.sentences, "status_for", side_effect=responses) as status, \
                    patch.object(r.sentences, "judge", return_value=False), \
                    patch.object(r.time, "monotonic", side_effect=ticks), \
                    patch.object(r.time, "sleep") as sleep:
                self.assertEqual(phone.sentence(case)["model_ms"], expected)
                self.assertEqual(status.call_count, len(responses))
                sleep.assert_any_call(0.2)

    def test_main_exit_codes_for_missing_baseline_and_failures(self):
        for complete in (True, False):
            for save in (True, False):
                with self.subTest(complete=complete, save=save), tempfile.TemporaryDirectory() as directory, \
                        patch.object(r, "Phone") as phone, \
                        patch.object(r, "read_cases", return_value=[{}]), \
                        patch.object(r, "measure_sentences", return_value={"latency": {
                            "complete": complete, "runs": [{"metrics": {"p50": 12}}]}}), \
                        contextlib.redirect_stdout(io.StringIO()):
                    argv = ["--serial", "PRIVATE_DEVICE", "--out", directory,
                            "--skip", ",".join(x for x in r.AREAS if x != "latency")]
                    code = r.main(argv + (["--save-baseline"] if save else []))
                    self.assertEqual(code, 1 if not complete else 0 if save else 2)
                    self.assertFalse(phone.return_value.mock_calls)

    def test_uncaught_exceptions_redact_serial(self):
        for serial_args in (["--serial", "PRIVATE_DEVICE"], ["--serial=PRIVATE_DEVICE"]):
            with tempfile.TemporaryDirectory() as directory, \
                    patch.object(r, "print_table", side_effect=RuntimeError("failed PRIVATE_DEVICE")), \
                    contextlib.redirect_stderr(io.StringIO()) as output:
                code = r.main(serial_args + ["--out", directory, "--skip", ",".join(r.AREAS)])
                self.assertEqual(code, 1)
                self.assertIn("RuntimeError: failed [redacted]", output.getvalue())
                self.assertNotIn("PRIVATE_DEVICE", output.getvalue())

    def test_preflight_missing_or_empty_testsets_before_phone_creation(self):
        for name in ("model-testset-en.jsonl", "story-testset.jsonl"):
            for empty in (True, False):
                with self.subTest(name=name, empty=empty), tempfile.TemporaryDirectory() as directory, \
                        patch.object(r, "ROOT", Path(directory)), patch.object(r, "Phone") as phone, \
                        contextlib.redirect_stderr(io.StringIO()) as output:
                    evidence = Path(directory) / "plan/eyes/evidence"
                    evidence.mkdir(parents=True)
                    for filename in ("model-testset-en.jsonl", "story-testset.jsonl"):
                        if filename != name:
                            (evidence / filename).write_text('{}\n', encoding="utf-8")
                        elif empty:
                            (evidence / filename).write_text('  \n', encoding="utf-8")
                    self.assertEqual(r.main(["--serial", "PRIVATE_DEVICE", "--out", directory]), 1)
                    self.assertIn(f"Test-set file is {'empty' if empty else 'missing'}:", output.getvalue())
                    self.assertIn(name, output.getvalue())
                    phone.assert_not_called()

    def test_gate_table(self):
        def timed(values):
            return {"complete": True, "runs": [{"metrics": {"p50": x}} for x in values]}
        baseline = {"accuracy": {"counts": {"dev": 37, "holdout": 51},
                                 "items": [{"id": "a", "pass": True}]},
                    "latency": timed([10, 20, 30]),
                    "stories": {"counts": {"dev": 30, "holdout": 25}, "changes": 10, "shortest_ms": 1000}}
        current = {"accuracy": {"complete": True, "counts": {"dev": 38, "holdout": 50},
                                "items": [{"id": "a", "pass": False}]},
                   "latency": timed([100, 30, 25]),
                   "stories": {"complete": True, "counts": {"dev": 30, "holdout": 25},
                               "changes": 11, "shortest_ms": 800},
                   "voice": {"complete": True, "lines": {"en3": timed([10])}}}
        table = {x["row"]: x for x in r.gate_table(current, baseline)}
        for key in ("accuracy.dev", "latency.p50", "stories.shortest_ms"):
            self.assertEqual(table[key]["status"], "PASS")
        for key in ("accuracy.holdout", "accuracy.flips", "stories.changes"):
            self.assertEqual(table[key]["status"], "FAIL")
        self.assertEqual(table["voice.en3.p50"]["status"], "no baseline")
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            r.print_table(list(table.values()))
        self.assertIn("accuracy.flips | ['a'] | [] | FAIL", output.getvalue())

    def test_no_device_and_no_serial_when_everything_skipped(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(r.sentences, "adb") as adb:
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                code = r.main(["--serial", "PRIVATE_DEVICE", "--out", directory,
                               "--skip", ",".join(r.AREAS), "--save-baseline"])
            self.assertEqual(code, 0)
            adb.assert_not_called()
            for name in ("result.json", "baseline.json"):
                text = (Path(directory) / name).read_text(encoding="utf-8")
                self.assertNotIn("PRIVATE_DEVICE", text)
                self.assertEqual(json.loads(text)["schema_version"], 1)
            self.assertNotIn("PRIVATE_DEVICE", output.getvalue())

    def test_smoke_thresholds_and_missing_measurements(self):
        def measurement(temperature, rss_delta=20 * 1024):
            return {"complete": True, "snapshots": [
                {"minute": 5, "memory_kib": {"VmRSS": 100000, "VmHWM": 1500000}, "temperature_c": temperature},
                {"minute": 30, "memory_kib": {"VmRSS": 100000 + rss_delta, "VmHWM": 1500000}, "temperature_c": temperature}],
                "intervals": [{"fps": 59, "stats": {"int_p99_us": 25000, "int_max_us": 50000,
                                                       "int_over50": 0, "work_avg_us": 100}}]}
        baseline = {"smoke": measurement(30)}
        current = {"smoke": measurement(31)}
        self.assertTrue(all(x["status"] == "PASS" for x in r.gate_table(current, baseline)))
        current["smoke"] = measurement(31.1, 20 * 1024 + 1)
        current["smoke"]["intervals"][0]["fps"] = 58.99
        current["smoke"]["intervals"][0]["stats"]["int_max_us"] = 50001
        rows = {x["row"]: x for x in r.gate_table(current, baseline)}
        for name in ("smoke.temperature_c", "smoke.RSS_30_minus_5_kib", "smoke.0.fps", "smoke.0.int_max_us"):
            self.assertEqual(rows[name]["status"], "FAIL")
        incomplete = {"latency": {"complete": False, "runs": [{"metrics": {}}]}}
        rows = {x["row"]: x for x in r.gate_table(incomplete, {"latency": {"runs": [{"metrics": {"p50": 2}}]}})}
        self.assertEqual(rows["latency.data"]["status"], "FAIL")
        self.assertEqual(rows["latency.p50"]["status"], "FAIL")


    def test_real_phone_voice_run_without_terminal_state(self):
        # Copied from baseline-E0/result.json, en3 run 1. Its old complete=False
        # was a wait_voice timeout override, not a parser/key-type failure.
        run = {'first_audio_ms': 3863.0,
         'starts_ms': {'1': 242819637, '2': 242823093, '3': 242825205},
         'audio_ms': {'1': 3204, '2': 1927, '3': 1195},
         'gaps_ms': [252, 185],
         'audio_timestamps': [{'sentence': 1,
                               'total': 3,
                               'proxy_t': 242819637,
                               'presented_t': 242819603,
                               'offset_ms': -34},
                              {'sentence': 2,
                               'total': 3,
                               'proxy_t': 242823093,
                               'presented_t': 242823057,
                               'offset_ms': -36},
                              {'sentence': 3,
                               'total': 3,
                               'proxy_t': 242825205,
                               'presented_t': 242825234,
                               'offset_ms': 29}],
         'offsets_ms': [-34, -36, 29],
         'complete': False,
         'metrics': {'first_audio_ms': 3863.0,
                     'gap_p50': 185,
                     'gap_p90': 252,
                     'gap_p99': 252,
                     'offset_p50': -34,
                     'offset_p90': 29,
                     'offset_p99': 29}}
        start_epoch = 1700000000.0
        log = f"{start_epoch:.3f} I ChatiVoice: speak: 3 sentences\n"
        for key, start in run["starts_ms"].items():
            stamp = start_epoch + run["first_audio_ms"] / 1000
            stamp += (start - run["starts_ms"]["1"]) / 1000
            log += (f"{stamp:.3f} I ChatiVoice: speech: {key}/3 start t={start} "
                    f"audio={run['audio_ms'][key]}\n")
        for row in run["audio_timestamps"]:
            log += (f"audio-timestamp: sentence {row['sentence']}/3 "
                    f"proxy_t={row['proxy_t']} presented_t={row['presented_t']} "
                    f"offset_ms={row['offset_ms']}\n")
        phone = r.Phone("PRIVATE_DEVICE")
        with patch.object(phone, "log", return_value=log), \
                patch.object(r.time, "monotonic", side_effect=[0, 0]), \
                patch.object(r.time, "sleep"):
            parsed = phone.wait_voice(3)
        self.assertTrue(parsed["complete"])
        for key in ("first_audio_ms", "gaps_ms", "offsets_ms"):
            self.assertEqual(parsed[key], run[key])

    def test_sentence_retry_recovers_or_reports_missing(self):
        phone = r.Phone("PRIVATE_DEVICE")
        case = {"id": "ho36", "set": "holdout", "text": "hello", "react": False}
        for response in ("result | model 12 ms", ""):
            with self.subTest(response=response), patch.object(phone, "clear"), \
                    patch.object(phone, "send") as send, \
                    patch.object(r.sentences, "status_for", side_effect=["", response]), \
                    patch.object(r.sentences, "judge", return_value=True) as judge, \
                    patch.object(r.time, "monotonic", side_effect=[0, 0, 15, 15, 15, 30]), \
                    patch.object(r.time, "sleep"):
                row = phone.sentence(case)
            self.assertEqual(send.call_count, 2)
            self.assertEqual(row["pass"], True if response else None)
            self.assertEqual(row["source"], "model" if response else "missing")
            self.assertEqual(judge.call_count, int(bool(response)))

    def test_missing_accuracy_and_phrasebook_latency(self):
        from unittest.mock import Mock
        rows = [{"id": "a", "set": "holdout", "result": "", "pass": None,
                 "model_ms": None, "source": "missing"},
                {"id": "b", "set": "holdout", "result": "ok | phrasebook", "pass": True,
                 "model_ms": None, "source": "phrasebook"}]
        phone = Mock()
        phone.sentence.side_effect = [None] + rows
        result = r.measure_sentences(phone, [{}, {}], 1, True, True)
        self.assertEqual(result["accuracy"]["counts"]["holdout"], 1)
        self.assertEqual(result["accuracy"]["missing"]["holdout"], 1)
        self.assertFalse(result["accuracy"]["complete"])
        self.assertFalse(result["latency"]["complete"])
        self.assertEqual(r.flips(rows, [{"id": "a", "pass": True}]), [])
        phone.sentence.side_effect = [None, rows[1]]
        result = r.measure_sentences(phone, [{}], 1, False, True)
        self.assertTrue(result["latency"]["complete"])
        self.assertEqual(result["latency"]["runs"][0]["samples_ms"], [])

    def test_startup_sends_only_after_app_ready_and_uses_launch_epoch(self):
        phone = r.Phone("PRIVATE_DEVICE")
        marker = "1700000000.000 I ChatiEyes: regression-launch\n"
        ready = marker + "1700000002.000 I ChatiEyes: effects: ready\n"
        spoken = ready + "1700000005.000 I ChatiVoice: speech: 1/3 start t=3000\n"
        events = []
        def log():
            events.append("log")
            return [marker, ready, spoken][events.count("log") - 1]
        with patch.object(phone, "launch"), patch.object(phone, "log", side_effect=log), \
                patch.object(phone, "send", side_effect=lambda *args: events.append("send")), \
                patch.object(r.time, "monotonic", side_effect=[0, 0, 1, 2]), \
                patch.object(r.time, "sleep"):
            self.assertEqual(phone.startup(True), {"ms": 5000, "complete": True})
        self.assertEqual(events, ["log", "log", "send", "log"])
        with patch.object(phone, "launch"), patch.object(phone, "log", return_value=marker), \
                patch.object(phone, "send") as send, \
                patch.object(r.time, "monotonic", side_effect=[0, 0, 120]), \
                patch.object(r.time, "sleep"):
            self.assertEqual(phone.startup(True), {"ms": None, "complete": False})
            send.assert_not_called()

    def test_missing_measurements_fail_even_without_reference(self):
        for reference in (None, 30):
            with self.subTest(reference=reference):
                baseline = {"smoke": {"snapshots": [{"temperature_c": reference}]},
                            "latency": {"runs": [{"metrics": {"p50": reference}}]}}
                current = {"smoke": {"complete": False, "snapshots": [
                    {"minute": 5, "memory_kib": {}, "temperature_c": None},
                    {"minute": 30, "memory_kib": {}, "temperature_c": None}]},
                    "latency": {"complete": False, "runs": [{"metrics": {"p50": None}}]}}
                rows = {row["row"]: row for row in r.gate_table(current, baseline)}
                for name in ("smoke.temperature_c", "smoke.VmHWM_kib",
                             "smoke.RSS_30_minus_5_kib", "latency.p50"):
                    self.assertEqual(rows[name]["status"], "FAIL")
                current["smoke"]["snapshots"][-1]["temperature_c"] = 29
                rows = {row["row"]: row for row in r.gate_table(current, baseline)}
                self.assertEqual(rows["smoke.temperature_c"]["status"],
                                 "no baseline" if reference is None else "PASS")

    def test_voice_reports_each_missing_sentence_field(self):
        log = ("1700000000.000 I ChatiVoice: speak: 3 sentences\n"
               "1700000000.100 I ChatiVoice: speech: 1/3 start t=100 audio=200\n"
               "1700000000.500 I ChatiVoice: speech: 3/3 start t=500\n"
               "audio-timestamp: sentence 3/3 proxy_t=500 presented_t=501 offset_ms=1\n")
        parsed = r.parse_voice(log, 3)
        self.assertFalse(parsed["complete"])
        self.assertEqual(parsed["missing"], [
            {"sentence": 1, "missing": ["offset"]},
            {"sentence": 2, "missing": ["start", "duration", "offset"]},
            {"sentence": 3, "missing": ["duration"]}])

    def test_startup_ignores_stale_equal_and_unstamped_events(self):
        phone = r.Phone("PRIVATE_DEVICE")
        marker = "1700000010.000 I ChatiEyes: regression-launch\n"
        stale = ""
        for stamp in ("1700000009.000 ", "1700000010.000 ", ""):
            for message in ("ChatiEyes: effects: ready", "ChatiEyes: version 1",
                            "ChatiBrain: warm-up done", "ChatiVoice: Hebrew voice warm-up done",
                            "ChatiVoice: speech: 1/3 start t=1"):
                stale += stamp + "I " + message + "\n"
        for audible in (False, True):
            fresh = ("1700000012.000 I ChatiEyes: effects: ready\n"
                     "1700000013.000 I ChatiBrain: warm-up done\n"
                     "1700000014.000 I ChatiVoice: Hebrew voice warm-up done\n"
                     "1700000015.000 I ChatiVoice: speech: 1/3 start t=100\n")
            with self.subTest(audible=audible), patch.object(phone, "launch"), \
                    patch.object(phone, "log", side_effect=[stale + marker, stale + marker + fresh]), \
                    patch.object(phone, "send") as send, \
                    patch.object(r.time, "monotonic", side_effect=[0, 0, 1]), \
                    patch.object(r.time, "sleep") as sleep:
                self.assertEqual(phone.startup(audible),
                                 {"ms": 5000 if audible else 4000, "complete": True})
                self.assertEqual(send.call_count, int(audible))
                sleep.assert_called_once()

    def test_stats_windows_must_be_fully_inside_interval(self):
        records = {stamp: {"frames": stamp * 60, "int_frames": 600,
                           "int_over50": int(stamp == 20),
                           "int_max_us": 90000 if stamp == 20 else 16000,
                           "int_p99_us": 90000 if stamp == 20 else 16000}
                   for stamp in (10, 20, 30, 40)}
        stats, fps = r.aggregate_stats(records, 15, 35)
        self.assertEqual(fps, 60)
        self.assertEqual(stats["int_frames"], 600)
        self.assertEqual(stats["int_over50"], 0)
        self.assertEqual(stats["int_max_us"], 16000)
        self.assertEqual(stats["int_p99_us"], 16000)
        self.assertEqual(r.aggregate_stats(records, 35, 45), (None, None))
        self.assertEqual(r.aggregate_stats(records, 30, 40)[0]["int_frames"], 600)

    def test_smoke_restart_fails_interval_and_recovers_next_interval(self):
        from unittest.mock import Mock
        for cause in ("pid", "frames"):
            clock = [0.0]
            phone = Mock()
            def log():
                lines = []
                for tick in range(0, int(clock[0]) + 1, 10):
                    frames = (tick - 180 if cause == "frames" and tick >= 180 else tick) * 60
                    lines.append(f"{1700000000 + tick:.3f} I ChatiEyes: stats frames={frames} "
                                 "redraws=0 presents=0 work_avg_us=100 work_max_us=16000 "
                                 "int_frames=600 int_p99_us=16000 int_max_us=16000 int_over50=0")
                return "\n".join(lines)
            phone.log.side_effect = log
            phone.snapshot.side_effect = lambda minute: {
                "minute": minute, "sample_monotonic_s": clock[0],
                "pid": "456" if cause == "pid" and minute >= 5 else "123",
                "stats": {"frames": 1}, "memory_kib": {"VmRSS": 1, "VmHWM": 2},
                "temperature_c": 30}
            with self.subTest(cause=cause), \
                    patch.object(r.time, "monotonic", side_effect=lambda: clock[0]), \
                    patch.object(r.time, "sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)):
                result = r.measure_smoke(phone, [{"id": "a", "text": "hi"}], 10)
            self.assertFalse(result["complete"])
            first, second = result["intervals"]
            self.assertTrue(first["restarted"])
            self.assertIsNone(first["fps"])
            self.assertFalse(second["restarted"])
            self.assertEqual(second["fps"], 60)
            self.assertEqual(second["stats"]["int_frames"], 18000)
            rows = {row["row"]: row for row in r.gate_table({"smoke": result}, {})}
            self.assertEqual(rows["smoke.0.fps"]["status"], "FAIL")
            self.assertEqual(rows["smoke.1.fps"]["status"], "PASS")
            # Even preexisting result files with a numeric FPS cannot pass a restart.
            first["fps"] = 60
            rows = {row["row"]: row for row in r.gate_table({"smoke": result}, {})}
            self.assertEqual(rows["smoke.0.fps"]["status"], "FAIL")

    def test_stats_accumulators_restart_after_last_frame_reset(self):
        records = {}
        for stamp, frames in ((0, 0), (10, 600), (20, 0), (30, 600),
                              (40, 0), (50, 600)):
            records[stamp] = {"frames": frames, "int_frames": 600,
                              "int_over50": int(stamp < 50),
                              "int_max_us": 90000 if stamp < 50 else 16000,
                              "int_p99_us": 90000 if stamp < 50 else 16000}
        stats, fps = r.aggregate_stats(records, 0, 50)
        self.assertEqual(fps, 60)
        self.assertEqual(stats["int_frames"], 600)
        self.assertEqual(stats["int_over50"], 0)
        self.assertEqual(stats["int_max_us"], 16000)
        self.assertEqual(stats["int_p99_us"], 16000)

    def test_smoke_polls_dedupes_and_aggregates_all_windows(self):
        from unittest.mock import Mock
        clock = [0.0]
        polls = []
        def log():
            polls.append(clock[0])
            lines = []
            for tick in range(max(0, int(clock[0]) - 35), int(clock[0]) + 1):
                if tick % 10:
                    continue
                tail = 60000 if tick == 20 else 16000
                lines.append(f"{1700000000 + tick:.3f} I ChatiEyes: stats frames={tick * 60} "
                             f"redraws=0 presents=0 work_avg_us=100 work_max_us=60000 "
                             f"int_frames=600 int_p99_us={tail} int_max_us={tail} "
                             f"int_over50={int(tick == 20)}")
            return "\n".join(lines)
        phone = Mock()
        phone.log.side_effect = log
        phone.snapshot.side_effect = lambda minute: {
            "minute": minute, "sample_monotonic_s": clock[0], "pid": "123",
            "stats": {"int_frames": 1}, "memory_kib": {"VmRSS": 1, "VmHWM": 2},
            "temperature_c": 30}
        with patch.object(r.time, "monotonic", side_effect=lambda: clock[0]), \
                patch.object(r.time, "sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)):
            result = r.measure_smoke(phone, [{"id": "a", "text": "hi"}], 10)
        self.assertTrue(result["complete"])
        self.assertEqual(len(result["stats_lines"]), 61)
        self.assertLessEqual(max(b - a for a, b in zip(polls, polls[1:])), 30)
        self.assertEqual(len(result["intervals"]), 2)
        for interval in result["intervals"]:
            self.assertEqual(interval["fps"], 60)
            self.assertEqual(interval["stats"]["int_frames"], 18000)
        stats = result["intervals"][0]["stats"]
        self.assertEqual(stats["int_p99_us"], 60000)
        self.assertEqual(stats["int_max_us"], 60000)
        self.assertEqual(stats["int_over50"], 1)
        self.assertEqual(result["intervals"][1]["stats"]["int_over50"], 0)


if __name__ == "__main__":
    unittest.main()
