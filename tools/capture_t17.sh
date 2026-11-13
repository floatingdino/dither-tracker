#!/usr/bin/env bash
# T17 captures: the turn tab (whose turn) mid-game with toggles and peeks, the tab pressed, a 3-player
# table with the sideways end seat active, the 6-player narrow tier (middle seat beside the diamond,
# corner seat), the timer (game clock and turn timer), a knocked-out active seat and the settings row.
# Drives one device over adb (an emulator is fine): installs the debug APK, clears data, launches with
# the debug extras (MainActivity, debuggable builds only):
#   --es turns off|on|game|turn   the Turns setting
#   --ei turn S --ei turnAgo M    start tracking from seat S, the clock M minutes back
#   --ei turnPass N --ei turnPassAgo M   then pass N times, the last M minutes back
# plus T11/T14's --ei players, --es lives, --es toggles, --es poison, --es tax; taps, screencaps,
# then checks every PNG with tools/check_1bit.py --scale 4.
#
#   ANDROID_SERIAL=emulator-5564 tools/capture_t17.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Coordinates are device px for landscape 1872x1404 at S = 4 (dither px × 4 + 2).
set -euo pipefail

OUT=${1:?usage: capture_t17.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=au.com.josephmark.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
mkdir -p "$OUT"

tap() { $A shell input tap "$1" "$2"; sleep 0.8; }
cap() { sleep 1.5; $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
launch() { # extras…
  $A shell input keyevent KEYCODE_HOME
  sleep 1
  $A shell am force-stop $P
  $A shell pm clear $P >/dev/null
  $A shell am start -S -n $P/.MainActivity --el seed 7 "$@" >/dev/null
  sleep 7
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

# 4 players mid-game: toggles and peeks on every seat, tracking from seat 0, two passes: seat 2's turn.
MID=(--ei players 4 --es lives 34,27,40,19 --es toggles "mr,ib,b," --es poison "0,3,0,9" --es tax "2,0,4,0" --es turns on --ei turn 0 --ei turnPass 2)
launch "${MID[@]}"
cap t17-4p-midgame
# Seat 2's tab pressed (bottom right, its far edge on the centre line): screencap while the finger is
# down (a hold only starts correcting after 1.5 s), then lift — a tap: the turn passes to seat 3.
$A shell input motionevent DOWN 1402 746
sleep 0.3
$A exec-out screencap -p > "$OUT/t17-4p-pressed.png"; echo "captured t17-4p-pressed"
$A shell input motionevent UP 1402 746
cap t17-4p-passed

# 3 players: the end seat (1, turned 270°) has the turn; its tab stands beside the diamond on its far edge.
launch --ei players 3 --es lives 40,33,21 --es toggles "b,mr,i" --es poison "2,4,0" --es turns on --ei turn 1
cap t17-3p-sideways

# 6 players, narrow tier: the top middle seat (diamond mid far edge: a 45 px tab beside it, minutes
# alone) on the game clock; then the top left seat (full-size tab, icon + minutes).
launch --ei players 6 --es toggles "mr,b,ib,,br,r" --es turns game --ei turn 1 --ei turnAgo 42
cap t17-6p-narrow-middle
launch --ei players 6 --es toggles "mr,b,ib,,br,r" --es turns game --ei turn 0 --ei turnAgo 7
cap t17-6p-narrow-corner

# The timer: game clock 1 h 47 (107 minutes) on seat 3; the turn timer at 4 minutes on seat 1.
launch --ei players 4 --es lives 22,31,8,26 --es turns game --ei turn 3 --ei turnAgo 107
cap t17-timer-game
launch --ei players 4 --es lives 22,31,8,26 --es turns turn --ei turn 0 --ei turnAgo 50 --ei turnPass 1 --ei turnPassAgo 4
cap t17-timer-turn

# Seat 2 knocked out on its own turn: the tab drops to its near edge (the hidden drawer tab's place).
launch --ei players 4 --es lives 40,40,0,40 --es turns turn --ei turn 2 --ei turnAgo 3
cap t17-ko-active

# The settings card with the Turns row (GAME chosen): gem → SETUP.
launch --ei players 4 --es turns game --ei turn 0
tap 934 702; tap 1050 818
cap t17-settings

for f in "$OUT"/t17-*.png; do
  python3 tools/check_1bit.py "$f" --scale 4 >/dev/null && echo "ok   $f" || { echo "FAIL $f"; exit 1; }
done
