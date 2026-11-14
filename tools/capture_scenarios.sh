#!/usr/bin/env bash
# Play-screen regression harness (T34, extended in T22): drives the Supernote Nomad or the emulator over
# adb through a fixed script of taps / holds and screencaps each state, so two builds can be compared
# byte for byte, and every capture can be purity-checked (tools/check_purity.sh wraps this):
#
#   tools/capture_scenarios.sh <outdir>            # installs app/build/outputs/apk/debug/app-debug.apk
#   NO_INSTALL=1 tools/capture_scenarios.sh <outdir>
#   BASE_ONLY=1 ...   only T34's 19 states        NO_FX=1 ...   skip the effects keyframes
#   FX_HOLD=10        effect holds are scaled by this (--ef fxHold) so each screencap lands in its frame
#   for f in before/*.png; do cmp "$f" "after/$(basename "$f")"; done
#
# With more than one device attached (an emulator), pick the Nomad: ANDROID_SERIAL=<serial> tools/...
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

# A fresh game, launched with these extras. The app goes home first: killing it while it is the top
# task (an install, a clear) can make System UI relaunch it on its own, without extras, and our start
# then only reaches it as a new intent: the partner flag, seed or inverted launch were lost (T49).
fresh() {
  $A shell input keyevent KEYCODE_HOME
  sleep 1
  $A shell am force-stop $P
  $A shell pm clear $P >/dev/null
  $A shell am start -S -n $P/.MainActivity "$@" >/dev/null
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi
fresh --el seed 7 --es partners 1
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
cap_pressed 07_press_tax_cell 174 1242  # seat 3 tax "+" half held (its inversion); release: tax 4.
                                    # (T49: the "-" half repeats while held, so it would empty the cell.)
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
hold 1520 350 2000                  # seat 1 primary "+" (partner quarters, rotated): +20, not lethal
cap 13_mode_damage                  # readouts on seat 1 and the target
                                    # (T49: 21 is lethal and KOs the target since T15, so its tile then
                                    # arms REVIVE? on a press instead of leaving the mode: 15-19 drifted)
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

if [ -n "${BASE_ONLY:-}" ]; then echo "done: $(ls "$OUT"/*.png | wc -l) captures in $OUT"; exit 0; fi

# === T22 additions: the rest of the screens (BASE_ONLY=1 stops after 19) ===========================
# Gem facets at S = 4 (device px): NEW 934 470, LOG 818 586, REDO 1050 586, DICE 702 702,
# CLEAN 1166 702, UNDO 818 818, SETUP 1050 818, FIRST 934 934; centre X 934 702.
# Starts from a fresh game, so these states do not depend on where the 19 above ended.
sleep 4
fresh --el seed 7 --es partners 1
sleep 4
tap 700 1050; tap 700 1050          # seat 3 "+" x2: something to undo
tap 466 1362; tap 406 1242; tap 638 1242; tap 466 1146   # seat 3 drawer: poison +, energy +, close
sleep 4

# --- the gem, the NEW confirm, a held facet -----------------------------------------------------------
tap 934 702                         # diamond: the gem (undo enabled)
sleep 1
cap 20_gem
tap 934 470                         # NEW: the two-faced confirm
sleep 1
cap 21_new_confirm
tap 718 702                         # cancel: back to the gem
sleep 1
cap_pressed 22_press_gem_dice 702 702 # DICE held; release opens the dice panel

# --- dice / coin panel ---------------------------------------------------------------------------------
# T52: each roll first plays its ~1.1 s presentation (a tap during it only skips to the result), so
# every capture waits 2 s for the result; the keyframes themselves are tools/capture_t52.sh's.
sleep 2
cap 23_dice_d20
tap 810 702; sleep 2                # d6
cap 24_dice_d6
tap 1058 702; sleep 2               # coin
cap 25_coin
cap_pressed 26_press_again 934 702  # AGAIN held; release flips again
sleep 2
cap 27_coin_again
tap 1182 702; sleep 1               # close X

# --- first player ----------------------------------------------------------------------------------------
tap 934 702; sleep 1; tap 934 934   # gem -> FIRST
sleep 1.5                           # T52: the "ME?" beat (450 ms), then the badge
cap 28_first_player
sleep 6                             # the badge clears after 5 s

# --- settings card (opened from the SETUP facet) --------------------------------------------------------
tap 934 702; sleep 1; tap 1050 818
sleep 1
cap 29_settings
cap_pressed 30_press_settings_row 934 702   # "Invert" row held; release toggles it (dark mode)
sleep 1
cap 31_settings_inverted
tap 934 702                         # Invert back off
sleep 1
tap 1542 222                        # T53: the Players row's › -> the layout picker (4 players)
sleep 1
cap 31b_settings_layouts
tap 936 222                         # its back row
sleep 1
tap 934 1150                        # Counters page
sleep 1
cap 32_settings_counters
tap 1528 1296                       # T53: › page 2 of "Counters and trackers" (the trackers)
sleep 1
cap 32b_settings_trackers
tap 344 1296                        # ‹ page 1
sleep 1
tap 358 126                         # rotate the card a quarter turn (it now faces the east edge)
sleep 1
cap 33_settings_rotated
tap 1514 1278                       # its close X, turned with it to the lower right
sleep 1

# --- history card -----------------------------------------------------------------------------------------
tap 934 702; sleep 1; tap 818 586   # gem -> LOG
sleep 1
cap 34_history
tap 2 2                             # outside the card: closed
sleep 1

# --- KO and winner -------------------------------------------------------------------------------------------
# Every seat on 40. A 5 s hold is -50 (repeats at 0.5, 1.5, ... 4.5 s). Seat 2 (upright, "-" on the
# screen left of its panel), seat 0 (rotated, "-" on the screen right), then seat 3: seat 1 is left
# standing -> the winner line.
hold 1100 1050 5000                 # seat 2 -> <= 0
sleep 5                             # readout commits (3 s), KO flourish, dead tile
cap 39_ko_seat2
cap_pressed 40_press_ko 1400 1050   # dead tile held (one tap of the tap-tap revive)
sleep 5                             # the revive arm times out
hold 700 350 5000                   # seat 0 -> <= 0
sleep 5
hold 300 1050 5000                  # seat 3 -> <= 0: the last KO
sleep 3.3                           # its readout commits: KO flourish + winner effect
cap 41_last_ko_flourish
sleep 0.5
cap 42_last_ko_flourish_b
sleep 5
cap 43_winner

# --- dark mode at launch (fresh game), a drawer and the gem inverted -----------------------------------
fresh --el seed 7 --ez inverted true   # a fresh game (all four seats alive)
sleep 4
cap 44_inverted
tap 466 1362                        # seat 3 tab: open
tap 174 1242; tap 406 1242          # tax +, poison +
cap 45_inverted_drawer
cap_pressed 46_inverted_press_cell 406 1242
tap 466 1146                        # seat 3 bar: close
tap 934 702; sleep 1
cap 47_inverted_gem
tap 934 702; sleep 1

# --- effects: every keyframe (debug gallery, holds x FX_HOLD so a screencap lands inside each frame) -----
if [ -z "${NO_FX:-}" ]; then
  FXH=${FX_HOLD:-10}
  for d in design/fx/*/; do
    name=$(basename "$d")
    [ -f "$d/meta.txt" ] || continue
    holds=$(awk '/^hold_ms/ { $1 = ""; print }' "$d/meta.txt")
    $A shell am force-stop $P
    $A shell am start -n $P/.MainActivity --es fx "$name" --ef fxHold "$FXH" >/dev/null
    sleep 3.5                         # launch + the gallery's 1.5 s start delay
    i=0
    for ms in $holds; do
      if [ "$ms" -eq 0 ]; then
        sleep 1; cap "fx_${name}_$i"    # the clean end frame
      else
        half=$(awk -v m="$ms" -v h="$FXH" 'BEGIN { printf "%.2f", m * h / 2000 }')
        sleep "$half"; cap "fx_${name}_$i"; sleep "$half"
      fi
      i=$((i + 1))
    done
  done
  $A shell am force-stop $P
fi

echo "done: $(ls "$OUT"/*.png | wc -l) captures in $OUT"
