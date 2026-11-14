#!/usr/bin/env bash
# T51 captures: KO and winner tiles without the life total (4 and 6 players; a poison KO keeps its
# cause chip; the winner's trophy), the closed menu diamond and a gem facet pressed (only their shape
# inverts).
# Drives one device over adb (an emulator is fine): installs the debug APK, clears data, launches with
# the debug extras (--ei players, --es lives, --es poison; debuggable builds only), taps, screencaps,
# then checks every PNG with tools/check_1bit.py --scale 4.
#
#   ANDROID_SERIAL=emulator-5562 tools/capture_t51.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Coordinates are device px for landscape 1872x1404 at S = 4, 4 players (see capture_scenarios.sh).
set -euo pipefail

OUT=${1:?usage: capture_t51.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=com.floatingdino.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
mkdir -p "$OUT"

tap() { $A shell input tap "$1" "$2"; sleep 0.8; }
cap() { sleep 1.5; $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
# Finger down on (x, y), screencap once the press shows, then cancel (nothing is applied).
cap_pressed() { # name x y
  $A shell input motionevent DOWN "$2" "$3"
  sleep 1.2                          # past the press delay (T57: quick taps never invert)
  $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"
  $A shell input motionevent CANCEL "$2" "$3"
  sleep 0.8
}
launch() { # extras…
  $A shell input keyevent KEYCODE_HOME
  sleep 1
  $A shell am force-stop $P
  $A shell pm clear $P >/dev/null
  $A shell am start -S -n $P/au.com.josephmark.dithertracker.MainActivity --el seed 7 "$@" >/dev/null
  sleep 7
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

# 4 players: seat 0 out of life, seat 2 poisoned (its chip stays), seat 3 marked out by life; seat 1 wins.
launch --ei players 4 --es lives 0,40,12,-4 --es poison 0,0,10,0
cap t51-ko-winner-4p
# 6 players: four dead (life, life, poison, life), two alive — the KO tiles at the narrow tier.
launch --ei players 6 --es lives 0,40,30,-2,25,0 --es poison 0,0,10,0,0,0
cap t51-ko-6p
# 6 players, one left standing (the middle top seat).
launch --ei players 6 --es lives 0,40,0,-2,0,0 --es poison 0,0,10,0,0,0
cap t51-winner-6p

# The menu: the closed diamond pressed, then the gem with a facet (SETUP) pressed.
launch --ei players 4
sleep 2
cap_pressed t51-diamond-pressed 934 702
tap 934 702
cap_pressed t51-gem-facet-pressed 1050 818

for f in "$OUT"/t51-*.png; do python3 tools/check_1bit.py "$f" --scale 4 | tail -1; done
