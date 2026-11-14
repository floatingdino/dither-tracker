#!/usr/bin/env bash
# T66 captures: the turn tab's post-pass report (TURN and GAME, 4p; the 6p narrow middle seat in the ui
# face; the 3p sideways end seat), the winner screen with times (TURN at 4p; GAME on the mixed-size
# 4-wide-far), one keyframe of the diamond's hourglass pulse, then tools/hourglass_strip.py's 4x strip
# (from the JVM renders: run `./gradlew :app:testDebugUnitTest --tests '*HourglassPulseTest'` first).
# Debug extras (MainActivity): --es turns off|on|game|turn, --ei turn S --ei turnAgo M (the active clock
# runs over the made-up past), --ei turnPass N --ei turnPassAgo M, --es endLives …, --es layout <id>,
# --ef pulseHold X (the pulse's holds × X, so a screencap lands on a keyframe).
#
#   ANDROID_SERIAL=emulator-5564 tools/capture_t66.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Coordinates are device px for landscape 1872x1404 at S = 4 (dither px × 4 + 2). A report shows for
# 5 s after a tap on the tab (TurnTab.REPORT_MS), so each is captured ~3 s after the tap.
set -euo pipefail

OUT=${1:?usage: capture_t66.sh <outdir>}
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
  $A shell am start -S -n $P/au.com.josephmark.dithertracker.MainActivity --el seed 7 "$@" >/dev/null
  sleep 7
}
# Pass from the tab at (x, y), capture the new tab's report.
pass_and_snap() { # name x y
  $A shell input tap "$2" "$3"
  sleep 2.5
  snap "$1"
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

# 4p TURN: seat 0 (top left, turned 180°) has had the turn 3 min; its tab on the centre line. The
# pass reports 3:0x on seat 1's tab.
launch --ei players 4 --es lives 34,27,40,19 --es toggles "mr,ib,b," --es turns turn --ei turn 0 --ei turnAgo 3
pass_and_snap t66-4p-turn-report 466 658
# 4p GAME: 47 min in, three passes made (seat 3, bottom left, has the turn): the game so far on seat 0.
launch --ei players 4 --es lives 22,31,8,26 --es turns game --ei turn 0 --ei turnAgo 47 --ei turnPass 3 --ei turnPassAgo 2
pass_and_snap t66-4p-game-report 466 746
# 6p narrow: seat 0 passes to seat 1 (a middle seat beside the diamond: the 45 px tab, the ui face).
launch --ei players 6 --es turns turn --ei turn 0 --ei turnAgo 12
pass_and_snap t66-6p-narrow-report 310 658
# 3p: seat 0 passes to the sideways end seat (1, turned 270°).
launch --ei players 3 --es lives 40,33,21 --es turns turn --ei turn 0 --ei turnAgo 2
pass_and_snap t66-3p-sideways-report 586 658

# Winner screens: TURN (each seat its own time on turn) at 4p; GAME (the game's on every seat) on the
# mixed-size 4-wide-far with a poison KO (the time under its cause chip).
launch --ei players 4 --es turns turn --ei turn 0 --ei turnAgo 38 --ei turnPass 9 --es endLives 40,0,0,0
sleep 4
snap t66-winner-turn-4p
launch --ei players 4 --es layout 4-wide-far --es poison 0,10,0,0 --es turns game --ei turn 0 --ei turnAgo 74 --ei turnPass 7 --es endLives 0,40,40,0
sleep 4
snap t66-winner-game-4wide

# The hourglass pulse on the diamond: GAME from launch, holds × 20 (FAST: frame 0 for 3 s, then 7 s).
launch --ei players 4 --es lives 34,27,40,19 --es turns game --ei turn 0 --ef pulseHold 20 --es displaySpeed fast
sleep 56.5
snap t66-hourglass-4p

python3 tools/hourglass_strip.py "$OUT/t66-hourglass-strip.png"

for f in "$OUT"/t66-*.png; do python3 tools/check_1bit.py "$f" --scale 4 >/dev/null && echo "ok   $f" || { echo "FAIL $f"; exit 1; }; done
