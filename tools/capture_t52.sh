#!/usr/bin/env bash
# T52 captures: the dice / coin roll presentation keyframe by keyframe, the first-player "ME?" beat
# and its landing, and commander mode's PARTNER tabs (off, on, the HOLD hint, pressed) at 4 and 6
# players. Drives one device over adb (an emulator is fine): installs the debug APK, clears data,
# launches with debug extras (MainActivity, debuggable builds only), taps, screencaps, then checks
# every PNG with tools/check_1bit.py --scale 4.
#
#   --ef diceHold X   every presentation hold × X (tools.ToolsDebug): at 16 each keyframe holds 4 s+,
#                     so a screencap taken mid-hold lands in it
#   --el seed N       the shared Random (dice values, quips) is reproducible
#
#   ANDROID_SERIAL=emulator-5564 tools/capture_t52.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Coordinates are device px for landscape 1872x1404 at S = 4 (dither px × 4 + 2).
set -euo pipefail

OUT=${1:?usage: capture_t52.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=com.floatingdino.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
HOLD=16
mkdir -p "$OUT"

tap() { $A shell input tap "$1" "$2"; sleep 0.8; }
snap() { $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
cap() { sleep 1.5; snap "$1"; }
cap_pressed() { # name x y
  $A shell input swipe "$2" "$3" "$2" "$3" 3500 &
  local pid=$!
  sleep 2
  snap "$1"
  wait $pid
  sleep 0.8
}
launch() { # extras…
  $A shell input keyevent KEYCODE_HOME
  sleep 1
  $A shell am force-stop $P
  $A shell pm clear $P >/dev/null
  $A shell am start -S -n $P/au.com.josephmark.dithertracker.MainActivity --el seed 7 --ef diceHold $HOLD "$@" >/dev/null
  sleep 7
}
# A presentation started by the tap at (x, y): one screencap in the middle of each of its four
# keyframes (holds 250, 250, 250, 300+ ms × HOLD), then the result.
present() { # name x y
  $A shell input tap "$2" "$3"
  sleep 2
  snap "$1-1-toss"
  sleep 3.5
  snap "$1-2-bounce"
  sleep 3.5
  snap "$1-3-skid"
  sleep 3.6
  snap "$1-4-land"
  sleep 6
  snap "$1-5-result"
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

# ---- dice and coin -------------------------------------------------------------------------------
launch
tap 934 702                          # the gem
tap 702 702                          # DICE: the panel opens empty (T59)
present t52-d20 686 702              # d20
sleep 1
present t52-coin 1058 702            # coin
# The landing's stamp at real speed (no hold scale), for the timing feel: AGAIN, then a tap skips.
tap 1182 702                         # close

# ---- first player ----------------------------------------------------------------------------------
tap 934 702
$A shell input tap 934 934           # FIRST
sleep 2
snap t52-first-1-asking              # every live seat: "ME?"
sleep 8
snap t52-first-2-landed              # the pick's badge (and its turn tab)

# ---- commander mode: PARTNER tabs, 4 players --------------------------------------------------------
launch --ei players 4
tap 466 1362                         # seat 3 tab: the drawer
tap 116 1146                         # imp: seat 3 is the target
cap t52-partner-4p-off
tap 1402 42                          # seat 1 (rotated): PARTNER on
tap 1402 1362                        # seat 2: PARTNER on
tap 1050 350; tap 1050 350           # seat 1's partner "+" (rotated: the screen-left quarter) ×2
sleep 4                              # the readouts commit
tap 1402 42                          # seat 1 again: partner damage at stake, so it only says HOLD
snap t52-partner-4p-on
cap_pressed t52-partner-4p-pressed 466 42   # seat 0's tab held (inverted); release turns it on

# ---- commander mode: PARTNER tabs, 6 players (narrow tier) -------------------------------------------
launch --ei players 6
tap 310 58                           # seat 0 tab
tap 518 166                          # imp: seat 0 is the target
cap t52-partner-6p-off
tap 934 42; tap 1558 1362; tap 310 1362   # seats 1, 3 and 5: PARTNER on
cap t52-partner-6p-on

for f in "$OUT"/t52-*.png; do python3 tools/check_1bit.py "$f" --scale 4 >/dev/null || echo "IMPURE $f"; done
echo "done"
