#!/usr/bin/env bash
# T55 captures: the promoted round-three effects on the device (an emulator is fine), through the
# debug gallery (`--es fx <name> --ef fxHold H`: the effect on every seat at once, holds × H so a
# screencap lands inside its keyframe), plus two real-trigger shots: dusk on every seat after a
# drawer's day/night cell, and the narrow tier at six players. Every PNG is checked with
# tools/check_1bit.py --scale 4.
#
#   ANDROID_SERIAL=emulator-5568 tools/capture_t55.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Writes t55-<effect>-f<i>.png (i = 0-based keyframe): every keyframe of the art-fixed and repurposed
# effects (crown-drop, torch, crit; their -narrow twins at six players), the main beat of every other
# promoted effect; t55-6p-narrow.png; t55-dusk-all-seats.png.
# Coordinates are device px for landscape 1872x1404 at S = 4.
set -euo pipefail

OUT=${1:?usage: capture_t55.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=au.com.josephmark.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
H=${FX_HOLD:-8}
mkdir -p "$OUT"

cap() { $A exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
tap() { $A shell input tap "$1" "$2"; sleep 0.6; }
launch() { # extras…
  $A shell am force-stop $P
  sleep 1
  $A shell am start -n $P/.MainActivity --el seed 7 "$@" >/dev/null
}

# Waits for the gallery's first effect to start (its trace line), then a beat for the frame to land.
waitstart() {
  local n=0
  until $A logcat -d -s PlayView:D | grep -q "fx gallery:"; do sleep 0.1; n=$((n + 1)); [ $n -lt 150 ] || break; done
}

# gallery <effect> <first frame> <last frame> [extras…]: capture keyframes first..last of the effect.
gallery() {
  local name=$1 from=$2 to=$3; shift 3
  local holds; holds=$(awk '/^hold_ms/ { $1 = ""; print }' "design/fx/$name/meta.txt")
  $A logcat -c
  launch --es fx "$name" --ef fxHold "$H" --ez trace true "$@"
  waitstart
  local i=0 ms half
  for ms in $holds; do
    [ "$ms" -eq 0 ] && break
    half=$(awk -v m="$ms" -v h="$H" 'BEGIN { printf "%.2f", m * h / 2000 }')
    sleep "$half"
    if [ "$i" -ge "$from" ] && [ "$i" -le "$to" ]; then cap "t55-$name-f$i"; fi
    sleep "$half"
    i=$((i + 1))
  done
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

# Art-fixed and repurposed: every keyframe.
for n in crown-drop torch crit; do gallery "$n" 0 9; done
for n in crown-drop-narrow torch-narrow; do gallery "$n" 0 9 --ei players 6; done
# One representative beat (the second keyframe: the main one) of every other promoted effect.
for n in slash chomp stomp chime heart-burst imp-raspberry imp-poke ooze brand charge level-up rad-glow \
         storm-copy ring-tempt dusk dawn your-turn spotlight start-flag revive; do
  gallery "$n" 1 1
done

# Six players, the narrow tier: ooze's main beat on every seat.
$A logcat -c
launch --es fx ooze --ef fxHold "$H" --ei players 6 --ez trace true
waitstart; sleep "$(awk -v h="$H" 'BEGIN { printf "%.2f", (300 + 175) * h / 1000 }')"
cap t55-6p-narrow

# A hit beside its readout (T55 timing: the effect starts 300 ms after the last tap, the delta stays):
# seat 3 (near left) taps − twice; its first beat with the −2 still up.
launch --ei players 4 --ef fxHold "$H" --es lives 40,40,40,40
sleep 6
$A shell input tap 300 1050; $A shell input tap 300 1050
sleep "$(awk -v h="$H" 'BEGIN { printf "%.2f", 0.3 + 150 * h / 1000 }')"
cap t55-delta-with-effect

# Dusk for real: day on the table, seat 3 (near left) opens its drawer, pages to the toggles and taps
# the day/night cell (day → night): every live seat plays it at once (seat 3 above its drawer).
launch --ei players 4 --es daynight day --ef fxHold "$H" --es lives 40,40,40,40
sleep 6
tap 466 1362   # seat 3 tab
tap 818 1144   # seat 3 ›: the toggle page
$A shell input tap 116 1346   # day/night cell: day → night
sleep "$(awk -v h="$H" 'BEGIN { printf "%.2f", 0.3 + (300 + 200) * h / 1000 }')"
cap t55-dusk-all-seats

$A shell am force-stop $P
for f in "$OUT"/t55-*.png; do python3 tools/check_1bit.py "$f" --scale 4 | tail -1; done
