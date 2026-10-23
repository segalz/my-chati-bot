"""On-phone comparison of eye-intent models (plan/eyes Stage 2B).

Runs llama-server on the USB-connected phone for one model at a time, sends every
test sentence through the same system prompt and JSON schema, and records
latency, validity, accuracy, memory and temperature.

Usage:
  python run_bench.py --serial SERIAL --bin-dir DIR --model PATH [--model PATH ...]
                      --testset FILE --out FILE.json
"""

import argparse
import json
import re
import subprocess
import time
import urllib.request
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
PHONE_DIR = "/data/local/tmp/chati-bench"
PHONE_PORT = 8080
HOST_PORT = 18080

EXPRESSIONS = ["NEUTRAL", "HAPPY", "SAD", "ANGRY", "SURPRISED",
               "SLEEPY", "BORED", "WINK_LEFT", "WINK_RIGHT"]

SYSTEM_PROMPT = """You control the eyes of a small desk companion robot.
Read the user's sentence and decide how the eyes react. Answer only with JSON.

Fields:
- react: true if the sentence shows an emotion, is a greeting or farewell, asks for a wink, or asks the eyes to look somewhere. Plain facts, chores, numbers, schedules and neutral information: react = false.
- expression: one of NEUTRAL, HAPPY, SAD, ANGRY, SURPRISED, SLEEPY, BORED, WINK_LEFT, WINK_RIGHT. Mirror the emotion of the speaker.
- intensity: 0.0 to 1.0.
- gaze_x: -1 = look left, 0 = center, 1 = look right.
- gaze_y: -1 = look up, 0 = center, 1 = look down.
  Keep gaze at 0 unless the sentence asks to look somewhere.
- hold_ms: how long to keep the reaction, 800 to 5000.
If react is false, use expression NEUTRAL, intensity 0, gaze 0, hold_ms 0."""

SCHEMA = {
    "type": "object",
    "properties": {
        "react": {"type": "boolean"},
        "expression": {"enum": EXPRESSIONS},
        "intensity": {"type": "number", "minimum": 0, "maximum": 1},
        "gaze_x": {"type": "number", "minimum": -1, "maximum": 1},
        "gaze_y": {"type": "number", "minimum": -1, "maximum": 1},
        "hold_ms": {"type": "integer", "minimum": 0, "maximum": 5000},
    },
    "required": ["react", "expression", "intensity", "gaze_x", "gaze_y", "hold_ms"],
    "additionalProperties": False,
}

# v2: expression decided first, gaze as a direction word, intensity/hold left to the
# app controller, and a whitespace-free grammar to keep the output to a few tokens.
LOOKS = ["center", "left", "right", "up", "down"]

SYSTEM_PROMPT_V2 = """You control the eyes of a small desk companion robot.
For each sentence, choose the eye expression, decide whether the eyes should react, and where to look.

expression: NEUTRAL, HAPPY, SAD, ANGRY, SURPRISED, SLEEPY, BORED, WINK_LEFT or WINK_RIGHT. Mirror the speaker's feeling.
react: true for feelings, greetings, goodbyes, winks and requests to look somewhere. false for plain facts, numbers, schedules and chores.
look: left, right, up or down only when the sentence asks to look there, otherwise center.

Examples:
Sentence: I passed all my exams!
{"expression":"HAPPY","react":true,"look":"center"}
Sentence: My best friend is moving far away.
{"expression":"SAD","react":true,"look":"center"}
Sentence: Glance up at the window.
{"expression":"NEUTRAL","react":true,"look":"up"}
Sentence: The oven needs to be cleaned.
{"expression":"NEUTRAL","react":false,"look":"center"}
Sentence: You broke my favorite mug, I'm so mad!
{"expression":"ANGRY","react":true,"look":"center"}
Sentence: The train leaves at 7:15.
{"expression":"NEUTRAL","react":false,"look":"center"}"""

GRAMMAR_V2 = (
    'root ::= "{\\"expression\\":\\"" expr "\\",\\"react\\":" bool ",\\"look\\":\\"" look "\\"}"\n'
    "expr ::= " + " | ".join(f'"{e}"' for e in EXPRESSIONS) + "\n"
    'bool ::= "true" | "false"\n'
    "look ::= " + " | ".join(f'"{l}"' for l in LOOKS) + "\n"
)

# v3: the same decision in two words ("HAPPY center"), NONE meaning no reaction,
# so generation is ~3-4 tokens instead of a JSON object.
SYSTEM_PROMPT_V3 = """You control the eyes of a small desk companion robot.
For each sentence answer with two words: the eye expression, then where to look.

Expression: HAPPY, SAD, ANGRY, SURPRISED, SLEEPY, BORED, WINK_LEFT, WINK_RIGHT or NEUTRAL, mirroring the speaker's feeling.
Use NONE when the eyes should not react: plain facts, numbers, schedules and chores.
Look: left, right, up or down only when the sentence asks to look there, otherwise center.

Examples:
Sentence: I passed all my exams!
HAPPY center
Sentence: My best friend is moving far away.
SAD center
Sentence: Glance up at the window.
NEUTRAL up
Sentence: The oven needs to be cleaned.
NONE center
Sentence: You broke my favorite mug, I'm so mad!
ANGRY center
Sentence: The train leaves at 7:15.
NONE center"""

GRAMMAR_V3 = (
    "root ::= expr \" \" look\n"
    "expr ::= " + " | ".join(f'"{e}"' for e in EXPRESSIONS + ["NONE"]) + "\n"
    "look ::= " + " | ".join(f'"{l}"' for l in LOOKS) + "\n"
)

# v4: keeps v2's JSON keys (they help small models) but folds react into the
# expression (NONE) to shorten the output.
SYSTEM_PROMPT_V4 = """You control the eyes of a small desk companion robot.
For each sentence, choose the eye expression and where to look.

expression: HAPPY, SAD, ANGRY, SURPRISED, SLEEPY, BORED, WINK_LEFT, WINK_RIGHT or NEUTRAL. Mirror the speaker's feeling.
Use NONE when the eyes should not react: plain facts, numbers, schedules and chores.
look: left, right, up or down only when the sentence asks to look there, otherwise center.

Examples:
Sentence: I passed all my exams!
{"expression":"HAPPY","look":"center"}
Sentence: My best friend is moving far away.
{"expression":"SAD","look":"center"}
Sentence: Glance up at the window.
{"expression":"NEUTRAL","look":"up"}
Sentence: The oven needs to be cleaned.
{"expression":"NONE","look":"center"}
Sentence: You broke my favorite mug, I'm so mad!
{"expression":"ANGRY","look":"center"}
Sentence: The train leaves at 7:15.
{"expression":"NONE","look":"center"}"""

GRAMMAR_V4 = (
    'root ::= "{\\"expression\\":\\"" expr "\\",\\"look\\":\\"" look "\\"}"\n'
    "expr ::= " + " | ".join(f'"{e}"' for e in EXPRESSIONS + ["NONE"]) + "\n"
    "look ::= " + " | ".join(f'"{l}"' for l in LOOKS) + "\n"
)

PROMPT_VERSION = "v1"
SERVER_ARGS = ""

GAZE_RULES = {
    "left": lambda x, y: x <= -0.3,
    "right": lambda x, y: x >= 0.3,
    "up": lambda x, y: y <= -0.3,
    "down": lambda x, y: y >= 0.3,
}


def adb(serial, *args, check=True):
    return subprocess.run([ADB, "-s", serial, *args], capture_output=True,
                          text=True, encoding="utf-8", check=check).stdout


def shell(serial, cmd):
    return adb(serial, "shell", cmd)


def cpu_temp(serial):
    out = shell(serial, "dumpsys thermalservice")
    m = re.search(r"Temperature\{mValue=([\d.]+), mType=0, mName=CPU", out)
    return float(m.group(1)) if m else None


def battery_temp(serial):
    m = re.search(r"temperature: (\d+)", shell(serial, "dumpsys battery"))
    return int(m.group(1)) / 10 if m else None


def http(path, body=None, timeout=120):
    url = f"http://127.0.0.1:{HOST_PORT}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, json.loads(r.read().decode("utf-8") or "{}")


def wait_healthy(deadline_s):
    start = time.monotonic()
    while time.monotonic() - start < deadline_s:
        try:
            status, body = http("/health", timeout=2)
            if status == 200 and body.get("status") == "ok":
                return time.monotonic() - start
        except Exception:
            pass
        time.sleep(0.25)
    raise TimeoutError("llama-server did not become healthy")


def validate(obj):
    if PROMPT_VERSION in ("v2", "v3", "v4"):
        return (set(obj) == {"expression", "react", "look"} and obj["expression"] in EXPRESSIONS
                and isinstance(obj["react"], bool) and obj["look"] in LOOKS)
    if set(obj) != set(SCHEMA["required"]):
        return False
    if not isinstance(obj["react"], bool) or obj["expression"] not in EXPRESSIONS:
        return False
    for k, lo, hi in (("intensity", 0, 1), ("gaze_x", -1, 1), ("gaze_y", -1, 1)):
        if not isinstance(obj[k], (int, float)) or not lo <= obj[k] <= hi:
            return False
    return isinstance(obj["hold_ms"], int) and 0 <= obj["hold_ms"] <= 5000


def parse(content):
    """Return the intent dict for any prompt version, or None if unparseable."""
    try:
        if PROMPT_VERSION in ("v3", "v4"):
            if PROMPT_VERSION == "v3":
                expr, look = content.split()
            else:
                d = json.loads(content)
                expr, look = d["expression"], d["look"]
            return {"expression": "NEUTRAL" if expr == "NONE" else expr,
                    "react": expr != "NONE", "look": look}
        return json.loads(content)
    except Exception:
        return None


def ask(text, threads_note):
    if PROMPT_VERSION == "v4":
        body = {
            "messages": [{"role": "system", "content": SYSTEM_PROMPT_V4},
                         {"role": "user", "content": f"Sentence: {text}"}],
            "temperature": 0,
            "max_tokens": 24,
            "grammar": GRAMMAR_V4,
            "chat_template_kwargs": {"enable_thinking": False},
        }
    elif PROMPT_VERSION == "v3":
        body = {
            "messages": [{"role": "system", "content": SYSTEM_PROMPT_V3},
                         {"role": "user", "content": f"Sentence: {text}"}],
            "temperature": 0,
            "max_tokens": 12,
            "grammar": GRAMMAR_V3,
            "chat_template_kwargs": {"enable_thinking": False},
        }
    elif PROMPT_VERSION == "v2":
        body = {
            "messages": [{"role": "system", "content": SYSTEM_PROMPT_V2},
                         {"role": "user", "content": f"Sentence: {text}"}],
            "temperature": 0,
            "max_tokens": 32,
            "grammar": GRAMMAR_V2,
            "chat_template_kwargs": {"enable_thinking": False},
        }
    else:
        body = {
            "messages": [{"role": "system", "content": SYSTEM_PROMPT},
                         {"role": "user", "content": text}],
            "temperature": 0,
            "max_tokens": 96,
            "response_format": {"type": "json_schema",
                                "json_schema": {"name": "eye_intent", "schema": SCHEMA}},
            "chat_template_kwargs": {"enable_thinking": False},
        }
    t0 = time.monotonic()
    _, resp = http("/v1/chat/completions", body)
    wall_ms = (time.monotonic() - t0) * 1000
    content = resp["choices"][0]["message"].get("content", "")
    return content, wall_ms, resp.get("timings", {})


def score(case, obj):
    r = {"valid": obj is not None and validate(obj)}
    if not r["valid"]:
        return r
    r["react_ok"] = obj["react"] == case["react"]
    if case["react"]:
        r["expr_ok"] = obj["react"] and obj["expression"] in case["expr"]
        if "gaze" in case:
            if PROMPT_VERSION in ("v2", "v3", "v4"):
                r["gaze_ok"] = obj["react"] and obj["look"] == case["gaze"]
            else:
                r["gaze_ok"] = obj["react"] and GAZE_RULES[case["gaze"]](obj["gaze_x"], obj["gaze_y"])
    return r


def run_model(serial, model_path, cases, threads, ctx):
    name = Path(model_path).name
    print(f"== {name}: pushing", flush=True)
    adb(serial, "push", model_path, f"{PHONE_DIR}/{name}")
    shell(serial, "pkill -x llama-server; true")
    temp_before = (cpu_temp(serial), battery_temp(serial))
    server = subprocess.Popen(
        [ADB, "-s", serial, "shell",
         f"cd {PHONE_DIR} && LD_LIBRARY_PATH={PHONE_DIR} ./llama-server -m {name} "
         f"-c {ctx} -t {threads} --host 127.0.0.1 --port {PHONE_PORT} --no-webui --reasoning off --swa-full "
         f"{SERVER_ARGS} "
         f"> {PHONE_DIR}/server-{name}.log 2>&1"])
    try:
        load_s = wait_healthy(180)
        pid = shell(serial, "pidof llama-server").strip().split()[0]
        print(f"   loaded in {load_s:.1f}s pid={pid}", flush=True)

        _, first_ms, _ = ask("Hello there!", threads)  # first request, fills prompt cache
        results = []
        for case in cases:
            content, wall_ms, timings = ask(case["text"], threads)
            obj = parse(content)
            s = score(case, obj)
            results.append({"id": case["id"], "set": case["set"], "output": content, "intent": obj,
                            "wall_ms": round(wall_ms), "timings": timings, **s})
            print(f"   {case['id']:5} {round(wall_ms):5} ms  {content}", flush=True)

        status = shell(serial, f"cat /proc/{pid}/status")
        hwm = re.search(r"VmHWM:\s+(\d+) kB", status)
        rss = re.search(r"VmRSS:\s+(\d+) kB", status)
        anon = re.search(r"RssAnon:\s+(\d+) kB", status)
        return {
            "model": name,
            "prompt": PROMPT_VERSION,
            "threads": threads,
            "ctx": ctx,
            "load_s": round(load_s, 2),
            "first_request_ms": round(first_ms),
            "peak_rss_mb": round(int(hwm.group(1)) / 1024) if hwm else None,
            "rss_mb": round(int(rss.group(1)) / 1024) if rss else None,
            "rss_anon_mb": round(int(anon.group(1)) / 1024) if anon else None,
            "temp_before": temp_before,
            "temp_after": (cpu_temp(serial), battery_temp(serial)),
            "results": results,
        }
    finally:
        shell(serial, "pkill -x llama-server; true")
        server.wait(timeout=30)
        shell(serial, f"rm -f {PHONE_DIR}/{name}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--bin-dir", required=True)
    ap.add_argument("--model", action="append", required=True)
    ap.add_argument("--testset", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--ctx", type=int, default=1024)
    ap.add_argument("--cooldown", type=int, default=60)
    ap.add_argument("--prompt", choices=["v1", "v2", "v3", "v4"], default="v1")
    ap.add_argument("--server-args", default="", help="extra llama-server flags")
    args = ap.parse_args()
    global PROMPT_VERSION, SERVER_ARGS
    PROMPT_VERSION = args.prompt
    SERVER_ARGS = args.server_args

    cases = [json.loads(l) for l in Path(args.testset).read_text(encoding="utf-8").splitlines() if l.strip()]
    shell(args.serial, f"mkdir -p {PHONE_DIR}")
    for f in Path(args.bin_dir).iterdir():
        if f.name == "llama-server" or f.suffix == ".so":
            adb(args.serial, "push", str(f), f"{PHONE_DIR}/{f.name}")
    shell(args.serial, f"chmod 755 {PHONE_DIR}/llama-server")
    adb(args.serial, "forward", f"tcp:{HOST_PORT}", f"tcp:{PHONE_PORT}")

    runs = []
    try:
        for i, m in enumerate(args.model):
            if i:
                time.sleep(args.cooldown)
            runs.append(run_model(args.serial, m, cases, args.threads, args.ctx))
            Path(args.out).write_text(json.dumps(runs, indent=1, ensure_ascii=False), encoding="utf-8")
    finally:
        adb(args.serial, "forward", "--remove", f"tcp:{HOST_PORT}", check=False)
    print("done", flush=True)


if __name__ == "__main__":
    main()
