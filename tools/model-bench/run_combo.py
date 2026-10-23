"""Two models side by side on the phone (plan/eyes Stage 2B speed work).

Qwen decides react + look, Gemma decides the expression. Both llama-servers run
at the same time with the CPU cores split between them; every sentence is sent
to both concurrently, so the latency is the slower of the two.

Usage:
  python run_combo.py --serial SERIAL --qwen PATH --gemma PATH --testset FILE --out FILE.json
"""

import argparse
import json
import re
import statistics
import subprocess
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import run_bench as rb

rb.PROMPT_VERSION = "v4"


def post(port, body):
    req = urllib.request.Request(f"http://127.0.0.1:{port}/v1/chat/completions",
                                 data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    t0 = time.monotonic()
    with urllib.request.urlopen(req, timeout=120) as r:
        resp = json.loads(r.read().decode("utf-8"))
    return resp["choices"][0]["message"]["content"], (time.monotonic() - t0) * 1000


def body(text):
    return {"messages": [{"role": "system", "content": rb.SYSTEM_PROMPT_V4},
                         {"role": "user", "content": f"Sentence: {text}"}],
            "temperature": 0, "max_tokens": 24, "grammar": rb.GRAMMAR_V4,
            "chat_template_kwargs": {"enable_thinking": False}}


def wait_healthy(port, deadline_s=180):
    start = time.monotonic()
    while time.monotonic() - start < deadline_s:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=2) as r:
                if r.status == 200:
                    return
        except Exception:
            pass
        time.sleep(0.25)
    raise TimeoutError(f"server on {port} not healthy")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--qwen", required=True)
    ap.add_argument("--gemma", required=True)
    ap.add_argument("--testset", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--qwen-threads", default="-t 3 -tb 4")
    ap.add_argument("--gemma-threads", default="-t 3 -tb 4")
    args = ap.parse_args()

    s = args.serial
    cases = [json.loads(l) for l in Path(args.testset).read_text(encoding="utf-8").splitlines() if l.strip()]
    servers = {"qwen": (Path(args.qwen).name, 8080, 18080, args.qwen_threads),
               "gemma": (Path(args.gemma).name, 8081, 18081, args.gemma_threads)}
    procs = []
    try:
        rb.shell(s, "pkill -x llama-server; true")
        temp_before = rb.cpu_temp(s)
        for key, (name, pport, hport, threads) in servers.items():
            rb.adb(s, "forward", f"tcp:{hport}", f"tcp:{pport}")
            procs.append(subprocess.Popen(
                [rb.ADB, "-s", s, "shell",
                 f"cd {rb.PHONE_DIR} && ./llama-server -m {name} -c 1024 {threads} "
                 f"--host 127.0.0.1 --port {pport} --no-webui --reasoning off --swa-full "
                 f"> {rb.PHONE_DIR}/combo-{key}.log 2>&1"]))
        for _, _, hport, _ in servers.values():
            wait_healthy(hport)
        print("both loaded", flush=True)

        pool = ThreadPoolExecutor(max_workers=2)
        for hport in (18080, 18081):  # fill both prompt caches
            post(hport, body("Hello there!"))

        results = []
        for case in cases:
            t0 = time.monotonic()
            fq = pool.submit(post, 18080, body(case["text"]))
            fg = pool.submit(post, 18081, body(case["text"]))
            (cq, mq), (cg, mg) = fq.result(), fg.result()
            wall = (time.monotonic() - t0) * 1000
            iq, ig = rb.parse(cq), rb.parse(cg)
            intent = None
            if iq and ig:
                intent = {"react": iq["react"],
                          "expression": ig["expression"] if ig["react"] else "NEUTRAL",
                          "look": iq["look"]}
            sc = rb.score(case, intent)
            results.append({"id": case["id"], "set": case["set"], "qwen": cq, "gemma": cg,
                            "intent": intent, "wall_ms": round(wall),
                            "qwen_ms": round(mq), "gemma_ms": round(mg), **sc})
            print(f"   {case['id']:5} {round(wall):5} ms  Q={cq} G={cg}", flush=True)

        mem = {}
        for pid in rb.shell(s, "pidof llama-server").split():
            st = rb.shell(s, f"cat /proc/{pid}/status")
            mem[pid] = {k: int(re.search(rf"{k}:\s+(\d+) kB", st).group(1)) // 1024
                        for k in ("VmHWM", "RssAnon", "RssFile")}
        meminfo = rb.shell(s, "cat /proc/meminfo")
        avail = int(re.search(r"MemAvailable:\s+(\d+)", meminfo).group(1)) // 1024
        out = {"setup": "qwen react+look, gemma expression, parallel",
               "qwen_threads": args.qwen_threads, "gemma_threads": args.gemma_threads,
               "mem_mb": mem, "mem_available_mb_during": avail,
               "temp_before": temp_before, "temp_after": rb.cpu_temp(s),
               "p50_ms": statistics.median(r["wall_ms"] for r in results),
               "results": results}
        Path(args.out).write_text(json.dumps(out, indent=1, ensure_ascii=False), encoding="utf-8")
        for subset in sorted({r["set"] for r in results}):
            rows = [r for r in results if r["set"] == subset]
            full = sum(bool(r.get("valid") and r.get("react_ok", True) and r.get("expr_ok", True)
                            and r.get("gaze_ok", True)) for r in rows)
            lat = [r["wall_ms"] for r in rows]
            print(f"{subset}: {full}/{len(rows)} fully correct, p50 {statistics.median(lat)} ms", flush=True)
        print(f"memory: {mem}, MemAvailable during run: {avail} MB", flush=True)
    finally:
        rb.shell(s, "pkill -x llama-server; true")
        for p in procs:
            p.wait(timeout=30)
        for hport in (18080, 18081):
            rb.adb(s, "forward", "--remove", f"tcp:{hport}", check=False)


if __name__ == "__main__":
    main()
