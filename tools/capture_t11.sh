#!/usr/bin/env bash
# T11 captures: the Play screen at 2–6 players (idle), plus two busy states for 2, 3, 5 and 6
# (a KO with a drawer open and counters bumped; commander-damage mode with damage dealt and the KO).
# Drives one device over adb (an emulator is fine): installs the debug APK, clears data, launches with
# the debug extras `--ei players N` / `--es lives …` (MainActivity, debuggable builds only), taps,
# screencaps. Every PNG is then checked with tools/check_1bit.py --scale 4.
#
#   ANDROID_SERIAL=emulator-5564 tools/capture_t11.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Coordinates are device px for landscape 1872x1404 at S = 4 (dither px × 4 + 2), from the T11 layouts
# (design/layouts/README.md): seat 0 is the far-left seat (its drawer opens downwards from the screen
# top); seat 0 is the commander target, so every other living seat is a source.
set -euo pipefail

OUT=${1:?usage: capture_t11.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=au.com.josephmark.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
mkdir -p "$OUT"

tap() { $A shell input tap "$1" "$2"; sleep 0.4; }
cap() { $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
launch() { # extras…
  $A shell am force-stop $P
  $A shell pm clear $P >/dev/null
  $A shell am start -n $P/.MainActivity --el seed 7 "$@" >/dev/null
  sleep 4
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

# ---- idle, every count ---------------------------------------------------------------------------
for n in 2 3 4 5 6; do
  launch --ei players $n
  cap "t11-${n}p"
done

# ---- busy: n, KO seat, lives, tab A, cell+ 0, cell+ 1, button A, "x y" per living source ----------
busy() {
  local n=$1 lives=$2 tab=$3 c0=$4 c1=$5 btn=$6; shift 6
  launch --ei players "$n" --es lives "$lives" --es partners 1
  sleep 3                                    # the KO flourish settles
  tap $tab                                   # seat 0's drawer
  tap $c0; tap $c0; tap $c1                  # commander tax +2, poison +1
  sleep 4                                    # readouts commit
  cap "t11-${n}p-busy-drawer"
  tap $btn                                   # seat 0 becomes the commander target
  for xy in "$@"; do tap $xy; tap $xy; tap $xy; done
  cap "t11-${n}p-busy-commander"
}

# 2 players: seat 1 is out (so seat 0 is also the winner); its tile is the only source.
busy 2 "40,0" "938 58" "1638 54" "1326 54" "1718 166"
# 3 players: the near seat (2) is out; the end seat (1) deals damage.
busy 3 "40,40,0" "586 58" "994 54" "762 54" "1054 166" "1522 410"
# 5 players: near right (3) is out; far middle, far right and near left deal damage.
busy 5 "40,40,40,0,40" "310 58" "390 54" "78 54" "518 166" "782 350" "1406 350" "778 1054"
# 6 players: near right (3) is out; the other four sources deal damage.
busy 6 "40,40,40,0,40,40" "310 58" "390 54" "78 54" "518 166" "782 350" "1406 350" "1090 1054" "466 1054"

for f in "$OUT"/t11-*.png; do python3 tools/check_1bit.py "$f" --scale 4 >/dev/null && echo "ok   $f" || { echo "FAIL $f"; exit 1; }; done
