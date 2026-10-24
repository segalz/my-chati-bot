#!/usr/bin/env bash
# E3 per-effect check: trigger each effect, screenshot it. Usage: e3_effects.sh SERIAL OUTDIR
set -u
export MSYS_NO_PATHCONV=1
ADB=/c/Users/segal/AppData/Local/Android/Sdk/platform-tools/adb.exe
S=$1; OUT=$2; mkdir -p "$OUT"
COLORS=$(cd "$(dirname "$0")/../../android/app/src/main/assets" && pwd)/colors.txt
A() { "$ADB" -s "$S" "$@"; }
t() { A shell date +%H:%M:%S | tr -d '\r'; }
shot() { A exec-out screencap -p > "$OUT/$1.png"; echo "$(t) shot $1"; }
fx() { A shell am broadcast -a app.mychatibot.DEBUG_LOOK --es effects "$1" >/dev/null; echo "$(t) effects $1"; }
intent() { A shell am broadcast -a app.mychatibot.DEBUG_INTENT --es json "'{\"schema_version\":1,\"expression\":\"$1\",\"look\":\"center\"}'" >/dev/null; echo "$(t) intent $1"; }
say() { A shell am broadcast -a app.mychatibot.DEBUG_SAY --es text "\"$1\"" >/dev/null; echo "$(t) say: $1"; }
speak() { A shell am broadcast -a app.mychatibot.DEBUG_SPEAK --es text "\"$1\"" >/dev/null; echo "$(t) speak: $1"; }
ALL="fx_sweat=on,fx_anger=on,fx_sparkles=on,fx_qmark=on,fx_notes=on,fx_rain=on"
OFF="fx_sweat=off,fx_anger=off,fx_sparkles=off,fx_qmark=off,fx_notes=off,fx_rain=off"
A logcat -c

echo "== off: ANGRY with every effect off (must look like before)"
fx "$OFF"; sleep 1; intent ANGRY; sleep 1; shot 0-angry-off; sleep 4
fx "$ALL"; sleep 1
echo "== anger mark";  intent ANGRY;     sleep 1; shot 1-anger;     sleep 4
echo "== rain";        intent SAD;       sleep 1; shot 2-rain;      sleep 4
echo "== sparkles";    intent HAPPY;     sleep 1; shot 3-sparkles;  sleep 4
echo "== exclaim";     intent SURPRISED; sleep 1; shot 4-exclaim;   sleep 4
echo "== sweat (SURPRISED right after SAD)"
intent SAD; sleep 1.5; intent SURPRISED; sleep 1; shot 5-sweat; sleep 5
echo "== question: SURPRISED with low strength (gap range raised for this check only)"
sed 's/^gap_low=.*/gap_low=60/; s/^gap_high=.*/gap_high=70/' "$COLORS" | A shell "run-as app.mychatibot sh -c 'cat > files/config/colors.txt'"
A shell am broadcast -a app.mychatibot.DEBUG_LOOK --ez colors_reload true >/dev/null
say "Wait, what? Is that really true?"; sleep 2.2; shot 6-question; sleep 4
A shell "run-as app.mychatibot sh -c 'cat > files/config/colors.txt'" < "$COLORS"
A shell am broadcast -a app.mychatibot.DEBUG_LOOK --ez colors_reload true >/dev/null
echo "== notes: HAPPY while speaking"
speak "What a wonderful sunny day, I am so happy we are going to the beach!"
for i in 1 2 3 4 5 6 7 8; do sleep 1.5; shot "7-notes-$i"; done
sleep 6
fx "$OFF"
A logcat -d -s ChatiEyes:* > "$OUT/logcat.txt"
echo "$(t) done"
