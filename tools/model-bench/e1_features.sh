#!/usr/bin/env bash
# E1 per-feature check: each feature on alone, screenshots as evidence. Usage: e1_features.sh SERIAL OUTDIR
set -u
export MSYS_NO_PATHCONV=1
ADB=/c/Users/segal/AppData/Local/Android/Sdk/platform-tools/adb.exe
S=$1; OUT=$2; mkdir -p "$OUT"
A() { "$ADB" -s "$S" "$@"; }
t() { A shell date +%H:%M:%S | tr -d '\r'; }
shot() { A exec-out screencap -p > "$OUT/$1.png"; echo "$(t) shot $1"; }
fx() { A shell am broadcast -a app.mychatibot.DEBUG_LOOK --es effects "$1" >/dev/null; echo "$(t) effects $1"; }
say() { A shell am broadcast -a app.mychatibot.DEBUG_SAY --es text "\"$1\"" >/dev/null; echo "$(t) say: $1"; }
OFF="intensity=off,thinking=off,sleep=off,error_eyes=off"
only() { echo "$OFF" | sed "s/$1=off/$1=on/"; }
A logcat -c

if [ "${SKIP1:-}" != 1 ]; then
echo "== 1 colors file (#15): private colors.txt with waiting=#FF00FF, then restore"
fx "$OFF"; sleep 2; shot 1a-before
sed 's/^waiting=#00BFFF/waiting=#FF00FF/' /c/Develop/my-chati-bot/android/app/src/main/assets/colors.txt \
  | A shell "run-as app.mychatibot sh -c 'mkdir -p files/config && cat > files/config/colors.txt'"
A shell am broadcast -a app.mychatibot.DEBUG_LOOK --ez colors_reload true >/dev/null; sleep 2; shot 1b-magenta
A shell "run-as app.mychatibot sh -c 'cat > files/config/colors.txt'" < /c/Develop/my-chati-bot/android/app/src/main/assets/colors.txt
A shell am broadcast -a app.mychatibot.DEBUG_LOOK --ez colors_reload true >/dev/null; sleep 2; shot 1c-restored

fi
echo "== 2 intensity (#13): strong vs mild happy, intensity off then on"
for mode in off on; do
  fx "$( [ $mode = on ] && only intensity || echo "$OFF")"; sleep 4
  say "This is the best day of my whole life, I am so incredibly happy!"; sleep 2.5; shot "2-$mode-strong"; sleep 4
  say "That is kind of nice, I guess."; sleep 2.5; shot "2-$mode-mild"; sleep 4
done

echo "== 3 thinking (#9): glance up-left while the model works"
fx "$(only thinking)"; sleep 4
for i in 1 2 3; do say "I wonder what the weather will be like next week."; sleep 0.45; shot "3-thinking-$i"; sleep 5; done

echo "== 4 error eyes (#12): the x x drawing (shape 2) for 1.2 s, then back"
fx "$(only error_eyes)"; sleep 2
A shell am broadcast -a app.mychatibot.DEBUG_EYES --ei shape 2 >/dev/null; sleep 0.3; shot 4a-cross
sleep 1; A shell am broadcast -a app.mychatibot.DEBUG_EYES --ei shape 0 >/dev/null; sleep 1; shot 4b-back

echo "== 5 sleep (#11): no input for 120 s, then a sentence wakes"
fx "$(only sleep)"; echo "$(t) waiting 130 s"; sleep 130; shot 5a-asleep; sleep 1; shot 5b-asleep-zzz
say "Hello, are you awake?"; sleep 1.5; shot 5c-awake

echo "== restore all-off"
fx "$OFF"
A logcat -d -s ChatiEyes:* > "$OUT/logcat.txt"
echo "$(t) done"
