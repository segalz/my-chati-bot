"""Measure the gain from feeding grammar-forced tokens as prompt (plan/eyes speed work).

Same v4 task, but the fixed answer prefix '{"expression":"' is appended to the
templated prompt, so it is processed in one batch instead of being generated
token by token. The model then only produces: VALUE","look":"LOOK"}

Usage:
  python run_prefill.py --serial SERIAL --model PATH --testset FILE --out FILE.json [--server-args "-t 6 -tb 6"]
"""

import argparse
import json
import statistics
import subprocess
import time
from pathlib import Path

import run_bench as rb

rb.PROMPT_VERSION = "v4"
PREFIX = '{"expression":"'
GRAMMAR_REST = (
    'root ::= expr "\\",\\"look\\":\\"" look "\\"}"\n'
    "expr ::= " + " | ".join(f'"{e}"' for e in rb.EXPRESSIONS + ["NONE"]) + "\n"
    "look ::= " + " | ".join(f'"{l}"' for l in rb.LOOKS) + "\n"
)


def templated(text):
    _, resp = rb.http("/apply-template", {
        "messages": [{"role": "system", "content": rb.SYSTEM_PROMPT_V4},
                     {"role": "user", "content": f"Sentence: {text}"}],
        "chat_template_kwargs": {"enable_thinking": False}})
    return resp["prompt"]


def ask(text):
    body = {"prompt": templated(text) + PREFIX, "temperature": 0, "n_predict": 16,
            "grammar": GRAMMAR_REST, "cache_prompt": True}
    t0 = time.monotonic()
    _, resp = rb.http("/completion", body)
    return PREFIX + resp["content"], (time.monotonic() - t0) * 1000, resp.get("timings", {})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--testset", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--server-args", default="-t 6 -tb 6")
    args = ap.parse_args()
    s, name = args.serial, Path(args.model).name
    cases = [json.loads(l) for l in Path(args.testset).read_text(encoding="utf-8").splitlines() if l.strip()]
    rb.adb(s, "push", args.model, f"{rb.PHONE_DIR}/{name}")
    rb.shell(s, "pkill -x llama-server; true")
    rb.adb(s, "forward", f"tcp:{rb.HOST_PORT}", f"tcp:{rb.PHONE_PORT}")
    server = subprocess.Popen(
        [rb.ADB, "-s", s, "shell",
         f"cd {rb.PHONE_DIR} && ./llama-server -m {name} -c 1024 {args.server_args} "
         f"--host 127.0.0.1 --port {rb.PHONE_PORT} --no-webui --reasoning off --swa-full "
         f"> {rb.PHONE_DIR}/prefill-{name}.log 2>&1"])
    try:
        rb.wait_healthy(180)
        ask("Hello there!")
        results = []
        for case in cases:
            content, wall, timings = ask(case["text"])
            obj = rb.parse(content)
            sc = rb.score(case, obj)
            results.append({"id": case["id"], "set": case["set"], "output": content, "intent": obj,
                            "wall_ms": round(wall), "timings": timings, **sc})
            print(f"   {case['id']:5} {round(wall):5} ms  {content}", flush=True)
        Path(args.out).write_text(json.dumps([{"model": name, "prompt": "v4-prefill",
                                               "results": results}], indent=1, ensure_ascii=False),
                                  encoding="utf-8")
        print("p50", statistics.median(r["wall_ms"] for r in results), flush=True)
    finally:
        rb.shell(s, "pkill -x llama-server; true")
        server.wait(timeout=30)
        rb.adb(s, "forward", "--remove", f"tcp:{rb.HOST_PORT}", check=False)


if __name__ == "__main__":
    main()
