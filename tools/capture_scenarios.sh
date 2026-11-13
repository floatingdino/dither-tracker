#!/usr/bin/env bash
# Play-screen regression harness (T34): drives the Supernote Nomad over adb through a fixed script of
# taps / holds and screencaps each state, so two builds can be compared byte for byte:
#
#   tools/capture_scenarios.sh <outdir>            # installs app/build/outputs/apk/debug/app-debug.apk
#   NO_INSTALL=1 tools/capture_scenarios.sh <outdir>
#   for f in before/*.png; do cmp "$f" "after/$(basename "$f")"; done
#
# Device rules (docs/BACKLOG.md): only one task uses the Nomad at a time; this script only installs,
# clears, launches, taps and screencaps this app.
#
# Geometry (landscape 1872x1404 device px, S = 4 -> 468x351 dither px, panels 233x175): seats run
# clockwise from the top-left, 0 TL, 1 TR, 2 BR, 3 BL; the top row (0, 1) is rotated 180, so a top
# player's "left" (-) is the screen right and their tab is at the screen top. Seat 1 is launched
# with a partner (`--es partners 1`) so its tray has 9 items (page 2) and its commander-mode tile
# splits into quarters. The commander quip is seeded (`--el seed 7`) so the target tile is
# reproducible.
#
# Timing: a pending-delta readout commits 3 s after that seat's last change, a hold repeats at 500 ms
# then every 1000 ms (holds here are 2000 ms = 2 repeats, or 1000 ms = 1 repeat: 500 ms margins), the
# commander mode times out after 45 s idle. Captures that must show a readout are taken right after
# the last change; captures that must not are taken after a >= 4 s sleep. Press captures hold a
# non-repeating zone in the background and screencap mid-press.
set -euo pipefail

OUT=${1:?usage: capture_scenarios.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=au.com.josephmark.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
mkdir -p "$OUT"

tap() { $A shell input tap "$1" "$2"; }
hold() { $A shell input swipe "$1" "$2" "$1" "$2" "$3"; } # x y ms
cap() { $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
# Press-and-hold a (non-repeating) zone in the background and capture while it is down.
cap_pressed() { # name x y
  $A shell input swipe "$2" "$3" "$2" "$3" 3500 &
  local pid=$!
  sleep 2
  cap "$1"
  wait $pid
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi
$A shell am force-stop $P
$A shell pm clear $P >/dev/null
$A shell am start -n $P/.MainActivity --el seed 7 --es partners 1 >/dev/null
sleep 4
cap 01_fresh

# --- life: a rotated seat (1) then an upright seat (3), readouts visible -------------------------
tap 1700 350; tap 1700 350          # seat 1 "-" (player's left = screen right) x2
hold 1700 350 2000                  # -20
cap 02_seat1_readout                # 18, readout -22
sleep 4
cap 03_seat1_committed
tap 700 1050; tap 700 1050; tap 700 1050  # seat 3 "+" x3
hold 700 1050 2000                  # +20
cap 04_seat3_readout                # 63, readout +23
sleep 4

# --- drawers: upright seat 3 and rotated seat 1, counters bumped, page 2 -------------------------
tap 466 1362                        # seat 3 tab: open
tap 174 1242                        # tax + (2)
tap 406 1242; tap 406 1242          # poison + x2
tap 638 1242                        # energy +
cap 05_drawer3_open
tap 1402 42                         # seat 1 tab (screen top): open
tap 1694 162                        # tax + (2)
tap 1230 162; tap 1230 162; tap 1230 162  # poison + x3
hold 1230 162 1000                  # poison +5 -> 8 (warning)
cap 06_drawers_open
cap_pressed 07_press_tax_cell 58 1242   # seat 3 tax "-" held (drawer cell inversion); release: tax 0
tap 1052 258                        # seat 1 page chevron: page 2
tap 1694 162                        # generic + on page 2
cap 08_drawer1_page2
sleep 4

# --- drawers closed: peeks -----------------------------------------------------------------------
tap 1402 258                        # seat 1 bar middle: close
tap 466 1146                        # seat 3 bar middle: close
sleep 1
cap 09_peeks
cap_pressed 10_press_tab0 466 42    # seat 0 tab held (tab inversion); release opens it
tap 466 258                         # seat 0 bar middle: close
sleep 1
cap 11_peeks_again

# --- commander mode: target seat 3 from its drawer bar ---------------------------------------------
tap 466 1362                        # seat 3 tab: open
tap 116 1146                        # imp button: seat 3 is the target
sleep 1
cap 12_mode_enter
tap 1650 1050; tap 1650 1050; tap 1650 1050  # seat 2 (upright) "+" x3
sleep 5                             # seat 2's and the target's readouts commit
hold 1520 350 2000                  # seat 1 primary "+" (partner quarters, rotated): +20
tap 1520 350                        # +1 -> 21 lethal
cap 13_mode_damage                  # readouts on seat 1 and the target
sleep 4
cap 14_mode_committed
cap_pressed 15_press_target 466 1050  # target tile held (whole tile inverted); release leaves the mode
sleep 1
cap 16_mode_left_by_target
tap 466 1362                        # seat 3 tab: open
tap 116 1146                        # imp: target again (next quip)
sleep 1
cap 17_mode_again
cap_pressed 18_press_close 936 702  # centre X held; release leaves the mode
sleep 1
cap 19_after_close

echo "done: $(ls "$OUT"/*.png | wc -l) captures in $OUT"
