#!/usr/bin/env bash
# T53 captures: the layout picker for every player count, a play screen of every arrangement T53 added
# (busy: toggles, peeks, a KO, day/night; the gem open where the diamond moved), the "Counters and
# trackers" settings page (both pages, some trackers hidden) and the table with them hidden, and a
# contact sheet of every arrangement's play screen (tools/contact_sheet_t53.py).
# Drives one device over adb (an emulator is fine): installs the debug APK, clears data, launches with
# the debug extras (MainActivity, debuggable builds only) `--ei players N`, `--es layout ID` (T53),
# `--es lives`, `--es toggles`, `--es poison`, `--es tax`, `--es daynight`; taps; screencaps; then
# checks every PNG with tools/check_1bit.py at the scale it was drawn at.
#
#   ANDROID_SERIAL=emulator-5566 tools/capture_t53.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Coordinates are device px for landscape 1872x1404 at S = 4 (dither px × 4 + 2). The settings card is
# 343 dither px square at (62, 4); its rows are 27 + 1 px from y = 42; the layout picker's cells are
# 165 × 133 from (68, 70).
set -euo pipefail

OUT=${1:?usage: capture_t53.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=au.com.josephmark.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
HERE=$(cd "$(dirname "$0")" && pwd)
SHEET=$(mktemp -d)
mkdir -p "$OUT"

tap() { $A shell input tap "$1" "$2"; sleep 1; }
cap() { sleep 1.5; $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
launch() { # extras…
  $A shell input keyevent KEYCODE_HOME
  sleep 1
  $A shell am force-stop $P
  $A shell pm clear $P >/dev/null
  $A shell am start -S -n $P/.MainActivity --el seed 7 "$@" >/dev/null
  sleep 7
}
settings() { tap 934 702; tap 1050 818; }   # diamond -> gem -> SETUP (S side)
ROW() { echo $((4 * (55 + 28 * $1) + 2)); }  # device y of card row i

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

# ---- the layout picker, every count (3 players: the pyramid chosen, to show a non-default selection) --
for n in 2 3 4 5 6; do
  if [ "$n" = 3 ]; then launch --ei players 3 --es layout 3-pyramid; else launch --ei players "$n"; fi
  settings
  tap 1542 "$(ROW 0)"                      # Players row: its ›
  cap "t53-layout-picker-${n}p"
done

# ---- the arrangements T53 added, busy -------------------------------------------------------------------
launch --ei players 2 --es layout 2-side --es lives 31,40 --es toggles "m,ib" --es poison "3,0" --es tax "2,4" --es daynight day
cap t53-play-2-side
tap 934 362                                # the diamond, 90 px down the divider: the gem opens whole
cap t53-play-2-side-gem
launch --ei players 2 --es layout 2-corner --es lives 40,12 --es toggles "r,m" --es poison "0,6" --es tax "0,2"
cap t53-play-2-corner
launch --ei players 3 --es layout 3-side --es lives 40,0,27 --es toggles "m,,ib" --es poison "2,0,4" --es tax "2,0,0" --es daynight night
cap t53-play-3-side
launch --ei players 3 --es layout 3-end --es lives 38,25,40 --es toggles "i,m,rb" --es poison "0,5,0" --es tax "4,0,2"
cap t53-play-3-end
tap 594 362                                # the diamond between the two near seats
cap t53-play-3-end-gem

# ---- counters and trackers (item A) ---------------------------------------------------------------------
launch --ei players 4 --es toggles "m,r,,ib" --es tax "2,0,0,4" --es daynight night
cap t53-counters-trackers-before
settings
tap 938 "$(ROW 8)"                         # "Counters and trackers"
cap t53-counters-trackers-1
tap 938 "$(ROW 1)"                         # hide commander tax
tap 1528 1296                              # › page 2
tap 938 "$(ROW 0)"                         # hide monarch
tap 938 "$(ROW 4)"                         # hide day/night
cap t53-counters-trackers-2
tap 1542 98                                # the card's close
tap 934 702                                # the gem's centre: closed
sleep 1
tap 466 1362                               # seat 3's drawer: no tax, no monarch, no day/night cells
cap t53-counters-trackers-hidden

# ---- contact sheet: every arrangement's play screen, idle with a few lives --------------------------------
for id in 2-across 2-ends 2-side 2-corner 3-tee 3-pyramid 3-side 3-end 4-grid 4-dinner 5-three-two 5-end 6-three-three 6-dinner; do
  n=${id%%-*}
  lives=$(python3 -c "print(','.join(str(v) for v in [40,33,27,40,19,36][:$n]))")
  launch --ei players "$n" --es layout "$id" --es lives "$lives"
  $A exec-out screencap -p > "$SHEET/$id.png"; echo "sheet $id"
done
python3 "$HERE/contact_sheet_t53.py" "$SHEET" "$OUT/t53-arrangements.png"

# ---- purity: every capture at the scale it was drawn (4; with Scale AUTO 4-dinner picks 3, 6-dinner 2) ------
fail=0
for f in "$OUT"/t53-*.png; do
  case "$f" in *t53-arrangements.png) continue ;; esac
  python3 "$HERE/check_1bit.py" "$f" --scale 4 >/dev/null || { echo "FAIL $f"; fail=1; }
done
for f in "$SHEET"/*.png; do
  s=4; case "$f" in *4-dinner*) s=3 ;; *6-dinner*) s=2 ;; esac
  python3 "$HERE/check_1bit.py" "$f" --scale $s >/dev/null || { echo "FAIL $f (S=$s)"; fail=1; }
done
rm -rf "$SHEET"
[ $fail -eq 0 ] && echo "all T53 captures pure"
exit $fail
