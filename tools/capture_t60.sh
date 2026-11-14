#!/usr/bin/env bash
# T60 captures: the redesigned layout picker for every player count (every page), a busy play screen
# of every arrangement T60 added (a KO, toggles, peeks, the turn tab, the drawer open on the smallest
# seat), an empty-seat layout with the gem open, and a contact sheet of every arrangement's idle play
# screen (tools/contact_sheet_t60.py). Drives one device over adb (an emulator is fine) with the debug
# extras (MainActivity, debuggable builds only): --ei players, --es layout (T53), --es lives,
# --es toggles, --es poison, --es tax, --es turns / --ei turn; then checks every PNG with
# tools/check_1bit.py at the scale it was drawn at.
#
#   ANDROID_SERIAL=emulator-5566 tools/capture_t60.sh design/captures
#   NO_INSTALL=1 …   skip the install;  SHEET_ONLY=1 …   only the contact sheet
#
# Coordinates are device px for landscape 1872x1404 at S = 4 (dither px × 4 + 2): the tab of each seat
# and the diamond come from ArrangementSurveyTest.writeSurvey (app/build/purity/t53/arrangement-survey.txt).
set -euo pipefail

OUT=${1:?usage: capture_t60.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=com.floatingdino.dithertracker
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
  $A shell am start -S -n $P/au.com.josephmark.dithertracker.MainActivity --el seed 7 "$@" >/dev/null
  sleep 7
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

if [ -z "${SHEET_ONLY:-}" ]; then
# ---- the picker, every count (one page each today: 3 players has 12, a page holds 12) ------------------
for n in 2 3 4 5 6; do
  case $n in 3) launch --ei players 3 --es layout 3-l-near-left ;; 5) launch --ei players 5 --es layout 5-wide-near ;; *) launch --ei players $n ;; esac
  tap 934 702; tap 1050 818                 # diamond -> gem -> SETUP
  tap 1542 222                              # the Players row's ›
  cap "t60-layout-picker-${n}p"
done
# 4 players: tap the hatched dinner -> the hint line says why.
launch --ei players 4
tap 934 702; tap 1050 818; tap 1542 222
tap 766 446                                # second thumbnail (4-dinner, hatched at S = 4)
cap t60-layout-picker-4p-hint

# ---- every new arrangement, busy: seat 0 KO'd, toggles, peeks, the turn on seat 1, the smallest seat's drawer --
# id | players | smallest seat's tab (device px)
busy() {
  local id=$1 n=$2 tx=$3 ty=$4
  local lives toggles poison tax
  lives=$(python3 -c "print(','.join(['0'] + [str(v) for v in [33,27,40,19,36][:$n-1]]))")
  toggles=$(python3 -c "print(','.join(['', 'm', 'ib', 'r', 'b', ''][:$n]))")
  poison=$(python3 -c "print(','.join(['0','3','0','6','0','2'][:$n]))")
  tax=$(python3 -c "print(','.join(['0','2','4','0','0','2'][:$n]))")
  launch --ei players "$n" --es layout "$id" --es lives "$lives" --es toggles "$toggles" --es poison "$poison" --es tax "$tax" --es turns on --ei turn 1
  sleep 3                                  # the KO flourish settles
  tap "$tx" "$ty"                          # the smallest seat's drawer
  cap "t60-play-$id"
}
busy 2-diagonal 2 1402 1346
busy 2-diagonal-flip 2 466 1346
busy 3-wide-far 3 1402 1346
busy 3-wide-near 3 1402 58
busy 3-tall-left 3 1402 1346
busy 3-tall-right 3 466 1346
busy 3-l-far-left 3 466 1346
busy 3-l-far-right 3 466 1346
busy 3-l-near-left 3 1402 1346
busy 3-l-near-right 3 466 1346
busy 4-wide-far 4 934 1346
busy 4-wide-near 4 934 58
busy 5-wide-near 5 934 58
busy 5-wide-far 5 934 1346
busy 5-hole-far-left 5 934 1346
busy 5-hole-far-mid 5 934 1346
busy 5-hole-far-right 5 934 1346
busy 5-hole-near-left 5 934 58
busy 5-hole-near-mid 5 934 58
busy 5-hole-near-right 5 934 58

# ---- an empty-seat layout with the gem open over the hole's corner ----------------------------------------
launch --ei players 3 --es layout 3-l-near-left --es lives 40,33,27
tap 934 702
cap t60-play-3-l-near-left-gem

fi

# ---- contact sheet: every arrangement idle --------------------------------------------------------------
ORDER="$SHEET/order.txt"
cat > "$ORDER" <<'EOF'
2-across 4 468x175 full
2-ends 4 351x233 full
2-side 4 233x351 full
2-corner 4 233x234 full
2-diagonal 4 233x175 full, holes  NEW
2-diagonal-flip 4 233x175 full, holes  NEW
3-tee 4 292x175 full
3-pyramid 4 233x175 full
3-side 4 155x351 narrow
3-end 4 148x170 narrow
3-wide-far 4 frame 233x175 full  NEW
3-wide-near 4 frame 233x175 full  NEW
3-tall-left 4 frame 233x175 full  NEW
3-tall-right 4 frame 233x175 full  NEW
3-l-far-left 4 233x175 full, hole  NEW
3-l-far-right 4 233x175 full, hole  NEW
3-l-near-left 4 233x175 full, hole  NEW
3-l-near-right 4 233x175 full, hole  NEW
4-grid 4 233x175 full
4-dinner 3 S3: 252x185 (not at S4)
4-wide-far 4 frame 155x175 narrow  NEW
4-wide-near 4 frame 155x175 narrow  NEW
5-three-two 4 155x175 narrow
5-end 4 148x170 narrow
5-wide-near 4 frame 155x175 narrow  NEW
5-wide-far 4 frame 155x175 narrow  NEW
5-hole-far-left 4 155x175 narrow, hole  NEW
5-hole-far-mid 4 155x175 narrow, hole  NEW
5-hole-far-right 4 155x175 narrow, hole  NEW
5-hole-near-left 4 155x175 narrow, hole  NEW
5-hole-near-mid 4 155x175 narrow, hole  NEW
5-hole-near-right 4 155x175 narrow, hole  NEW
6-three-three 4 155x175 narrow
6-dinner 2 S2: 207x259 (not at S4), 1/2
EOF
while read -r id s _ <&3; do
  n=${id%%-*}
  lives=$(python3 -c "print(','.join(str(v) for v in [40,33,27,40,19,36][:$n]))")
  launch --ei players "$n" --es layout "$id" --es lives "$lives"
  $A exec-out screencap -p > "$SHEET/$id.png"; echo "sheet $id"
done 3< "$ORDER"
python3 "$HERE/contact_sheet_t60.py" "$SHEET" "$ORDER" "$OUT/t60-arrangements.png"

# ---- purity ---------------------------------------------------------------------------------------------
fail=0
for f in "$OUT"/t60-*.png; do
  [ -n "${SHEET_ONLY:-}" ] && break
  case "$f" in *t60-arrangements.png) continue ;; esac
  python3 "$HERE/check_1bit.py" "$f" --scale 4 >/dev/null || { echo "FAIL $f"; fail=1; }
done
while read -r id s _; do
  python3 "$HERE/check_1bit.py" "$SHEET/$id.png" --scale "$s" >/dev/null || { echo "FAIL $id (S=$s)"; fail=1; }
done < "$ORDER"
rm -rf "$SHEET"
[ $fail -eq 0 ] && echo "all T60 captures pure"
exit $fail
