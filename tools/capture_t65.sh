#!/usr/bin/env bash
# T65 captures: the first-player roll-off's REST keyframe (the d20 stopped, square on the tile's floor)
# at 4 players, 6 (the narrow tier) and 3 (a sideways end seat). Fast display (the emulator is not
# e-ink); every hold × 12 (--ef diceHold 12): the five poses take 9.4 s, the rest 4.8 s from there.
#
#   ANDROID_SERIAL=emulator-5562 tools/capture_t65.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Coordinates are device px for landscape 1872x1404 at S = 4.
set -euo pipefail

OUT=${1:?usage: capture_t65.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=com.floatingdino.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
mkdir -p "$OUT"

snap() { $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
launch() { # extras…
  $A shell input keyevent KEYCODE_HOME
  sleep 1
  $A shell am force-stop $P
  $A shell pm clear $P >/dev/null
  $A shell am start -S -n $P/au.com.josephmark.dithertracker.MainActivity --el seed 7 --ef diceHold 12 --es displaySpeed fast "$@" >/dev/null
  sleep 7
}
# The gem at (x, y), then its FIRST facet 232 px below; the REST keyframe ~11.5 s later.
rest() { # name players diamondX
  launch --ei players "$2"
  $A shell input tap "$3" 702; sleep 0.8
  $A shell input tap "$3" 934
  sleep 11.5
  snap "$1"
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

rest t65-first-rest-4p 4 934
rest t65-first-rest-6p 6 934
rest t65-first-rest-3p 3 1170

for f in "$OUT"/t65-first-rest-*.png; do python3 tools/check_1bit.py "$f" --scale 4 >/dev/null || echo "IMPURE $f"; done
echo "done"
