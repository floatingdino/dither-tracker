#!/usr/bin/env bash
# T59 captures: the dice panel opening empty and a d20 roll's five fast-display poses; the first-player
# d20 roll-off on the tiles at 4 players (every keyframe on a fast display, a pose on a slow one), at 6
# (the narrow tier) and at 3 (a sideways end seat); commander mode's PARTNER tabs on their dither
# ground (off, on with the HOLD hint). Drives one device over adb (an emulator is fine), then checks
# every PNG with tools/check_1bit.py --scale 4.
#
#   --ef diceHold X        every presentation hold × X (tools.ToolsDebug), so a screencap lands inside
#   --es displaySpeed slow|fast   force eink/DisplaySpeed (the emulator is not e-ink: FAST)
#
#   ANDROID_SERIAL=emulator-5564 tools/capture_t59.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Coordinates are device px for landscape 1872x1404 at S = 4 (dither px × 4 + 2).
set -euo pipefail

OUT=${1:?usage: capture_t59.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=com.floatingdino.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
mkdir -p "$OUT"

tap() { $A shell input tap "$1" "$2"; sleep 0.8; }
snap() { $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
launch() { # extras…
  $A shell input keyevent KEYCODE_HOME
  sleep 1
  $A shell am force-stop $P
  $A shell pm clear $P >/dev/null
  $A shell am start -S -n $P/au.com.josephmark.dithertracker.MainActivity --el seed 7 "$@" >/dev/null
  sleep 7
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

# ---- dice: empty, then a d20's five fast poses (150 ms × 16 = 2.4 s each) ----------------------------
launch --ef diceHold 16 --es displaySpeed fast
tap 934 702; tap 702 702             # gem, DICE: the panel opens empty
snap t59-dice-empty
$A shell input tap 686 702           # d20
sleep 1.2
for i in 1 2 3 4 5; do snap "t59-dice-fast-$i"; sleep 1.9; done
sleep 1; snap t59-dice-fast-6-land
tap 1182 702                         # close

# ---- first player, 4 players, fast: 5 poses (150 ms × 12 = 1.8 s), the landing (19 s), the flag ----------
launch --ef diceHold 12 --es displaySpeed fast
tap 934 702; $A shell input tap 934 934   # gem, FIRST
sleep 0.9
for i in 1 2 3 4 5; do snap "t59-first-4p-pose-$i"; sleep 1.45; done
sleep 2; snap t59-first-4p-landed
sleep 19; snap t59-first-4p-flag     # the tiles are back; the FIRST flag stays; start-flag's sting

# ---- first player, 4 players, slow: 3 poses (250 ms × 12 = 3 s) ---------------------------------------------
launch --ef diceHold 12 --es displaySpeed slow
tap 934 702; $A shell input tap 934 934
sleep 1.5; snap t59-first-4p-slow-pose-1

# ---- first player, 6 players (narrow tier) and 3 players (a sideways end seat) ---------------------------------
launch --ei players 6 --ef diceHold 12 --es displaySpeed fast
tap 934 702; $A shell input tap 934 934
sleep 4.5; snap t59-first-6p-pose
sleep 6; snap t59-first-6p-landed
launch --ei players 3 --ef diceHold 12 --es displaySpeed fast
tap 1170 702; $A shell input tap 1170 934
sleep 4.5; snap t59-first-3p-pose
sleep 6; snap t59-first-3p-landed

# ---- commander mode: PARTNER tabs on the dither ground (4 players) ----------------------------------------------
launch --ei players 4
tap 466 1362                         # seat 3 tab: the drawer
tap 116 1146                         # imp: seat 3 is the target
sleep 1; snap t59-partner-off
tap 1402 42                          # seat 1 (rotated): PARTNER on
tap 1402 1362                        # seat 2: PARTNER on
tap 1050 350; tap 1050 350           # seat 1's partner "+" ×2
sleep 4
tap 1402 42                          # seat 1 again: damage at stake, so it says HOLD
snap t59-partner-on-hold

for f in "$OUT"/t59-*.png; do python3 tools/check_1bit.py "$f" --scale 4 >/dev/null || echo "IMPURE $f"; done
echo "done"
