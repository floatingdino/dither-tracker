#!/usr/bin/env bash
# Performance and battery benchmark (T20). Drives the app over adb through a fixed script, then
# summarises it with tools/bench_summary.py. See docs/performance.md for what each number means.
#
#   ANDROID_SERIAL=<serial> tools/bench.sh <outdir>               # everything (~12 min with IDLE_SECS=120)
#   PARTS="start play fx mem idle bg" (default: all)                # pick sections
#   IDLE_SECS=120     length of each idle / background CPU window
#   NO_INSTALL=1      don't install app/build/outputs/apk/debug/app-debug.apk (APK=... to pick another)
#   PKG=<appId>       measure a build installed under another applicationId (KEEP_SAVE=0 for a release
#                     build: run-as needs a debuggable app)
#   KEEP_SAVE=1       (default) back the app's save up first and put it back at the end (run-as, so a
#                     debuggable build): the scripted taps change the game. Never clears app data, so
#                     it is safe on a device whose save matters (the Nomad).
#
# Geometry: landscape 1872x1404 at S = 4, 4 players (the Nomad and the emulator); seats clockwise from
# the top-left, top row turned 180. Coordinates as in tools/capture_scenarios.sh.
#
# Output in <outdir>: logcat.txt (PlayView trace + marks), start.txt, gfx-<section>.txt,
# meminfo-*.txt, idle.txt, summary.md (the tables docs/performance.md quotes).
set -euo pipefail

OUT=${1:?usage: bench.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=${PKG:-com.floatingdino.dithertracker} # PKG: another applicationId (e.g. a release build installed beside the debug one)
ACT=$P/au.com.josephmark.dithertracker.MainActivity
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
PARTS=${PARTS:-start play clean fx mem idle bg}
IDLE_SECS=${IDLE_SECS:-120}
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
has() { case " $PARTS " in *" $1 "*) return 0 ;; esac; return 1; }

tap() { $A shell input tap "$1" "$2"; }
hold() { $A shell input swipe "$1" "$2" "$1" "$2" "$3"; } # x y ms
mark() { # section name: a log line the summary splits the trace on; resets gfxinfo for the section
  $A shell log -t PlayView "MARK $1" >/dev/null
  $A shell dumpsys gfxinfo $P reset >/dev/null || true
  CUR=$1
}
endmark() { $A shell dumpsys gfxinfo $P > "$OUT/gfx-$CUR.txt" || true; }
pid() { $A shell pidof $P | tr -d '\r'; }
cputicks() { # utime + stime of the process, in clock ticks (USER_HZ = 100)
  $A shell cat /proc/"$1"/stat | awk '{ print $14 + $15 }'
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

SAVE_BAK="$OUT/save-backup"
if [ "${KEEP_SAVE:-1}" = 1 ]; then
  mkdir -p "$SAVE_BAK"
  for f in save.dtsv save.dtsv.bak; do
    $A exec-out run-as $P cat files/$f > "$SAVE_BAK/$f" 2>/dev/null || rm -f "$SAVE_BAK/$f"
    [ -s "$SAVE_BAK/$f" ] || rm -f "$SAVE_BAK/$f"
  done
fi
restore_save() {
  [ "${KEEP_SAVE:-1}" = 1 ] || return 0
  $A shell input keyevent KEYCODE_HOME
  sleep 1
  $A shell am force-stop $P
  for f in save.dtsv save.dtsv.bak; do
    if [ -f "$SAVE_BAK/$f" ]; then
      $A exec-in run-as $P sh -c "cat > files/$f" < "$SAVE_BAK/$f" 2>/dev/null ||
        $A shell "run-as $P sh -c 'cat > files/$f'" < "$SAVE_BAK/$f"
    else
      $A shell run-as $P rm -f files/$f
    fi
  done
  $A shell run-as $P rm -f files/save.dtsv.tmp
  for f in save.dtsv save.dtsv.bak; do # read it back: a restore that silently failed would leave the bench's game
    [ -f "$SAVE_BAK/$f" ] || continue
    $A exec-out run-as $P cat files/$f | cmp -s - "$SAVE_BAK/$f" || echo "WARNING: $f NOT restored (backup in $SAVE_BAK)" >&2
  done
  $A shell am start -n $ACT >/dev/null
  sleep 5 # then check nothing rewrote it on launch
  $A exec-out run-as $P cat files/save.dtsv | cmp -s - "$SAVE_BAK/save.dtsv" || echo "WARNING: save.dtsv changed after relaunch (backup in $SAVE_BAK)" >&2
  echo "save restored, app relaunched"
}
trap restore_save EXIT

launch() { # extras...
  $A shell input keyevent KEYCODE_HOME
  sleep 1
  $A shell am force-stop $P
  $A shell am start -S -n $ACT "$@" >/dev/null
}

# ---- start time ---------------------------------------------------------------------------------
if has start; then
  : > "$OUT/start.txt"
  for i in 1 2 3 4 5; do # cold: process killed
    $A shell am force-stop $P; sleep 2
    t=$($A shell am start -W -n $ACT | awk '/TotalTime/ { print $2 }' | tr -d '\r')
    echo "cold $t" >> "$OUT/start.txt"; sleep 3
  done
  for i in 1 2 3 4 5; do # warm: process alive, activity finished (BACK) and created again
    $A shell input keyevent KEYCODE_BACK; sleep 2
    t=$($A shell am start -W -n $ACT | awk '/TotalTime/ { print $2 }' | tr -d '\r')
    echo "warm $t" >> "$OUT/start.txt"; sleep 3
  done
  for i in 1 2 3 4 5; do # hot: activity stopped (HOME) and brought back. am reports a stale TotalTime
    $A shell input keyevent KEYCODE_HOME; sleep 2 # for a hot start ("brought to the front"): use WaitTime
    t=$($A shell am start -W -n $ACT | awk '/WaitTime/ { print $2 }' | tr -d '\r')
    echo "hot $t" >> "$OUT/start.txt"; sleep 3
  done
  $A logcat -d -s DitherSave:I | grep -E "restore:|flush:" | tail -20 > "$OUT/save-log.txt" || true
  # Phases of one cold start (trace: onCreate, layout + prewarm, first onDraw)
  $A shell am force-stop $P; sleep 2; $A logcat -c
  $A shell am start -W -n $ACT --ez trace true >/dev/null; sleep 3
  $A logcat -d -s PlayView:D DitherSave:I | grep -E "onCreate|layout took|first onDraw|restore" > "$OUT/start-phases.txt" || true
  $A logcat -d -s StrictMode:D > "$OUT/strictmode-start.txt" || true # debug builds: main-thread disk I/O etc.
  cat "$OUT/start.txt"
fi

# ---- play: onDraw time, dirty rect and allocations per interaction --------------------------------
if has play; then
  launch --ez trace true --el seed 7
  sleep 5
  $A logcat -c
  mark launch_settled; sleep 1; endmark
  mark life_tap;       for i in 1 2 3 4 5; do tap 700 1050; sleep 0.6; done; endmark
  mark readout_commit; sleep 6; endmark                      # commit (3 s) + its effect + clean timer
  mark hold_repeat;    hold 700 1050 2600; endmark            # +1, then repeats at 0.5 s and 1.5 s
  mark hold_commit;    sleep 6; endmark
  mark drawer_open;    tap 466 1362; sleep 1; endmark
  mark drawer_cell;    tap 406 1242; sleep 0.6; tap 406 1242; sleep 1; endmark
  mark drawer_close;   tap 466 1146; sleep 4; endmark
  mark cmdr_enter;     tap 466 1362; sleep 0.6; tap 116 1146; sleep 1; endmark
  mark cmdr_damage;    for i in 1 2 3; do tap 1650 1050; sleep 0.6; done; sleep 5; endmark
  mark cmdr_exit;      tap 936 702; sleep 1.5; endmark
  mark gem_open;       tap 934 702; sleep 1.5; endmark
  mark gem_close;      tap 934 702; sleep 1.5; endmark
  mark dice;           tap 934 702; sleep 1; tap 702 702; sleep 1.5; tap 810 702; sleep 1.5; tap 1058 702; sleep 1.5; endmark
  mark dice_close;     tap 1182 702; sleep 1.5; endmark
  mark settings_open;  tap 934 702; sleep 1; tap 1050 818; sleep 1.5; endmark
  mark dark_mode;      tap 934 702; sleep 1.5; tap 934 702; sleep 1.5; endmark   # "Invert" row on, then off
  mark settings_page;  tap 934 1150; sleep 1.5; endmark                       # Counters page
  mark settings_close; tap 358 126; sleep 1.5; tap 1514 1278; sleep 2; endmark # rotate, then its close X
  mark history;        tap 934 702; sleep 1; tap 818 586; sleep 1.5; tap 2 2; sleep 1.5; endmark
  mark ko;             hold 1100 1050 5000; sleep 7; endmark  # seat 2 -> <= 0: readout, KO flourish, dead tile
  mark revive;         tap 1400 1050; sleep 0.6; tap 1400 1050; sleep 1.5; endmark
  mark idle_tail;      sleep 5; endmark
  $A logcat -d -s PlayView:D > "$OUT/logcat-play.txt"
  $A logcat -d -s StrictMode:D > "$OUT/strictmode-play.txt" || true
fi

# ---- e-ink clean cadence (debug builds: --ei cleanEvery 3 makes it due after three counted changes) --
if has clean; then
  launch --ez trace true --ei cleanEvery 3
  sleep 5
  $A logcat -c
  mark clean_seat; for i in 1 2 3; do tap 700 1050; sleep 0.6; done; sleep 9; endmark  # one seat: seat clean
  mark clean_full; tap 700 1050; sleep 0.6; tap 1650 1050; sleep 0.6; tap 1700 350; sleep 9; endmark # spread: full
  $A logcat -d -s PlayView:D > "$OUT/logcat-clean.txt"
fi

# ---- effects: every shipped effect through the debug gallery (debug builds) --------------------------
if has fx; then
  launch --ez trace true --es fx all
  $A logcat -c
  CUR=fx_gallery
  sleep "${FX_SECS:-90}"
  $A logcat -d -s PlayView:D > "$OUT/logcat-fx.txt"
  $A shell dumpsys gfxinfo $P > "$OUT/gfx-fx_gallery.txt" || true
fi

# ---- memory -------------------------------------------------------------------------------------
if has mem; then
  launch --ez trace true
  sleep 6
  $A shell dumpsys meminfo $P > "$OUT/meminfo-launch.txt"
  $A logcat -d -s PlayView:D | grep -E "masks|layout" | tail -5 > "$OUT/masks.txt" || true
  tap 700 1050; sleep 6 # one effect played
  $A shell dumpsys meminfo $P > "$OUT/meminfo-played.txt"
  $A logcat -d | grep -E "art *: .*(GC|paused)" | grep -i "$(pid)" | tail -20 > "$OUT/gc.txt" || true
fi

# ---- idle: CPU time while nobody touches the screen (foreground, then background) ------------------
if has idle; then
  launch --ez trace true
  sleep 10
  $A logcat -c
  p=$(pid); c0=$(cputicks "$p"); t0=$(date +%s)
  sleep "$IDLE_SECS"
  c1=$(cputicks "$p"); t1=$(date +%s)
  draws=$($A logcat -d -s PlayView:D | grep -c onDraw || true)
  alarms=$($A shell dumpsys alarm | grep -c "$P" || true)
  wakelocks=$($A shell dumpsys power | grep -i "$P" | grep -ci wake || true)
  echo "idle_fg secs=$((t1 - t0)) cpu_ticks=$((c1 - c0)) draws=$draws alarms=$alarms wakelocks=$wakelocks" | tee "$OUT/idle.txt"
fi
if has bg; then
  [ -n "$(pid)" ] || launch
  sleep 3
  tap 700 1050 # a pending readout: it must not keep the app busy once it is in the background
  sleep 0.3
  $A logcat -c
  $A shell input keyevent KEYCODE_HOME
  sleep 2
  p=$(pid); c0=$(cputicks "$p"); t0=$(date +%s)
  sleep "$IDLE_SECS"
  c1=$(cputicks "$p"); t1=$(date +%s)
  draws=$($A logcat -d -s PlayView:D | grep -c onDraw || true)
  timers=$($A logcat -d -s PlayView:D | grep -c "timer" || true)
  echo "idle_bg secs=$((t1 - t0)) cpu_ticks=$((c1 - c0)) draws=$draws timers=$timers" | tee -a "$OUT/idle.txt"
  $A logcat -d -s PlayView:D > "$OUT/logcat-bg.txt"
  $A logcat -d -s DitherSave:I | grep -E "flush|saved" > "$OUT/save-bg.txt" || true # onPause flush: main-thread wait
fi

python3 "$HERE/bench_summary.py" "$OUT" | tee "$OUT/summary.md"
