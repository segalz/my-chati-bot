"""On-phone latency sweep over eye-model files and thread counts using the debug-only
DEBUG_BRAIN channel. Usage: python speed_sweep.py --serial SERIAL --out FILE.txt
"""

import argparse
import shlex
import statistics
import subprocess
import time
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
MODELS = ["gemma-3-1b-it-Q4_K_M.gguf", "gemma-3-1b-it-qat-Q4_0.gguf", "gemma-3-1b-it-Q4_0.gguf"]
THREADS = [(2, 2), (4, 4), (6, 6), (8, 8)]
SENTENCES = [  # none of these are phrasebook phrases
    "I got the job I wanted!", "My dog is sick and I'm worried.", "Someone stole my bike, I'm furious!",
    "Whoa, a shooting star!", "This waiting room is so boring.", "The meeting is at three o'clock.",
    "I failed my driving test again.", "Look to the left.",
]


def adb(serial, *args):
    return subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True, encoding="utf-8").stdout


def brain_log(serial):
    return adb(serial, "logcat", "-d", "-s", "ChatiBrain:I").splitlines()


def configure(serial, model, threads, batch, timeout=60):
    adb(serial, "logcat", "-c")
    adb(serial, "shell", f"am broadcast -a app.mychatibot.DEBUG_BRAIN --es model {model} "
                         f"--ei threads {threads} --ei threads_batch {batch}")
    deadline = time.time() + timeout
    while time.time() < deadline:
        time.sleep(1.0)
        lines = [l for l in brain_log(serial) if "configure:" in l]
        if lines:
            return lines[-1].split("ChatiBrain: ", 1)[-1]
    return "configure timeout"


def run(serial, sentences=SENTENCES):
    totals, prompts = [], []
    for text in sentences:
        adb(serial, "logcat", "-c")
        adb(serial, "shell", f"am broadcast -a app.mychatibot.DEBUG_SAY --es text {shlex.quote(text)}")
        time.sleep(3.0)  # no polling while the model runs
        for line in reversed(brain_log(serial)):
            if "brain: '" in line and "-> OK" in line:
                f = line.split("-> ", 1)[1].split(" | ")
                prompts.append(int(f[4]))
                totals.append(int(f[6]))
                break
        time.sleep(0.5)
    return totals, prompts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    rows = ["model | threads/batch | n | model p50 ms | max ms | prompt p50 ms"]
    for model in MODELS:
        for threads, batch in THREADS:
            state = configure(args.serial, model, threads, batch)
            run(args.serial, ["Hello there, how are you today?", "It is a quiet afternoon."])  # warm-up, discarded
            totals, prompts = run(args.serial)
            row = (f"{model} | {threads}/{batch} | {len(totals)} | "
                   f"{statistics.median(totals) if totals else '-'} | {max(totals) if totals else '-'} | "
                   f"{statistics.median(prompts) if prompts else '-'}")
            rows.append(row)
            print(row, "|", state, flush=True)
    temp = adb(args.serial, "shell", "dumpsys thermalservice | grep -m1 'mName=CPU'").strip()
    rows.append(temp)
    Path(args.out).write_text("\n".join(rows), encoding="utf-8")


if __name__ == "__main__":
    main()
