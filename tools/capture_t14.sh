#!/usr/bin/env bash
# T14 captures: state-toggle badges (drawer closed), day/night on the centre diamond, the drawer's
# toggle cells, a 3-player table (sideways end seat) and the 6-player narrow tier. Drives one device
# over adb (an emulator is fine): installs the debug APK, clears data, launches with the debug extras
# `--ei players N --es toggles m,ib,,r --es daynight day|night --es poison … --es tax …` (MainActivity,
# debuggable builds only), taps, screencaps, then checks every PNG with tools/check_1bit.py --scale 4.
#
#   ANDROID_SERIAL=emulator-5564 tools/capture_t14.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Coordinates are device px for landscape 1872x1404 at S = 4 (dither px × 4 + 2).
set -euo pipefail

OUT=${1:?usage: capture_t14.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=com.floatingdino.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
mkdir -p "$OUT"

tap() { $A shell input tap "$1" "$2"; sleep 0.6; }
cap() { sleep 1.5; $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
launch() { # extras…
  $A shell am force-stop $P
  $A shell pm clear $P >/dev/null
  $A shell am start -n $P/au.com.josephmark.dithertracker.MainActivity --el seed 7 "$@" >/dev/null
  sleep 7
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

# 4 players: seat 0 monarch + ring, seat 1 initiative + blessing, seat 2 blessing, seat 3 nothing;
# counter peeks beside them (poison 3 / 9, tax 2 / 4).
MIX=(--ei players 4 --es toggles "mr,ib,b," --es poison "0,3,0,9" --es tax "2,0,4,0")
launch "${MIX[@]}"
cap t14-4p-mix
launch "${MIX[@]}" --es daynight day
cap t14-4p-day
launch "${MIX[@]}" --es daynight night
cap t14-4p-night

# The toggle page (page 2 at four players): seat 3 (near left, all off, day/night unset) and seat 0
# (far left, turned 180°: monarch and ring on). Then seat 3 claims the monarch from seat 0.
launch "${MIX[@]}"
tap 466 1362   # seat 3 tab
tap 470 46     # seat 0 tab
tap 818 1144   # seat 3 ›
tap 118 260    # seat 0 ›
cap t14-4p-drawer-toggles
tap 116 1240   # seat 3 monarch cell
tap 116 1346   # seat 3 day/night cell: unset → day
cap t14-4p-drawer-claimed

# 3 players: the end seat (1, turned 270°) holds three toggles; night.
launch --ei players 3 --es toggles "b,mbr,i" --es poison "2,4,0" --es daynight night
cap t14-3p-mix

# 6 players, narrow tier (155 px tiles, no peeks): badges on every seat but one; day.
launch --ei players 6 --es toggles "mr,b,ib,,br,r" --es daynight day
cap t14-6p-narrow
# Seat 4's drawer (near middle, two cells a page) paged to its first toggle page.
tap 934 1362
for _ in 1 2 3 4; do tap 1142 1254; done
cap t14-6p-narrow-drawer

for f in "$OUT"/t14-*.png; do
  case "$f" in *t14-icons.png) continue ;; esac
  python3 tools/check_1bit.py "$f" --scale 4 >/dev/null && echo "ok   $f" || { echo "FAIL $f"; exit 1; }
done
