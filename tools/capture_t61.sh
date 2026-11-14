#!/usr/bin/env bash
# T61 captures: a coin flip's landing showing the heads face (the imp) and the tails face (its tail) on
# the dice panel, on a slow display (the landing keyframe, held with --ef diceHold so the screencap lands
# inside it). The flip is random, so it flips (AGAIN) until each face has landed once, telling them
# apart by the landed coin's pixels against design/tools/coin/{heads,tails}.txt. Then checks every PNG
# with tools/check_1bit.py --scale 4.
#
#   ANDROID_SERIAL=emulator-5562 tools/capture_t61.sh design/captures
#   NO_INSTALL=1 …   skip the install
#
# Coordinates are device px for landscape 1872x1404 at S = 4 (taps: dither px × 4 + 2).
set -euo pipefail

OUT=${1:?usage: capture_t61.sh <outdir>}
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
P=com.floatingdino.dithertracker
APK=${APK:-app/build/outputs/apk/debug/app-debug.apk}
HOLD=16 # slow: tumble 3 × 250 ms, landing 300 ms → 12 s, then 4.8 s
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

tap() { $A shell input tap "$1" "$2"; sleep 0.8; }
launch() { # extras…
  $A shell input keyevent KEYCODE_HOME
  sleep 1
  $A shell am force-stop $P
  $A shell pm clear $P >/dev/null
  $A shell am start -S -n $P/au.com.josephmark.dithertracker.MainActivity --el seed 7 "$@" >/dev/null
  sleep 7
}

# Which face landed: the lower face's coin (dither px (168, 194), 28 × 28) against the two faces.
face_of() {
  python3 - "$1" <<'EOF'
import struct, sys, zlib
d = open(sys.argv[1], 'rb').read()
i, idat = 8, b''
while i < len(d):
    n = struct.unpack('>I', d[i:i + 4])[0]; t = d[i + 4:i + 8]
    if t == b'IHDR': w, h, bd, ct = struct.unpack('>IIBB', d[i + 8:i + 18])
    if t == b'IDAT': idat += d[i + 8:i + 8 + n]
    i += 12 + n
bpp = {0: 1, 2: 3, 4: 2, 6: 4}[ct]
raw = zlib.decompress(idat); stride = w * bpp
rows, prev = [], bytearray(stride)
for y in range(h):
    f = raw[y * (stride + 1)]; line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
    for x in range(stride):
        a = line[x - bpp] if x >= bpp else 0; b = prev[x]; c = prev[x - bpp] if x >= bpp else 0
        if f == 1: line[x] = (line[x] + a) & 255
        elif f == 2: line[x] = (line[x] + b) & 255
        elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
        elif f == 4:
            p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
    rows.append(line); prev = line
ink = lambda x, y: rows[y][x * bpp] < 128
best = None
for side in ('heads', 'tails'):
    art = open('design/tools/coin/%s.txt' % side).read().split()
    miss = sum(ink(168 * 4 + x * 4 + 2, 194 * 4 + y * 4 + 2) != (art[y][x] == '#') for y in range(28) for x in range(28))
    if best is None or miss < best[1]: best = (side, miss)
print(best[0] if best[1] < 60 else 'none')
EOF
}

if [ -z "${NO_INSTALL:-}" ]; then $A install -r "$APK" >/dev/null; fi

launch --ef diceHold $HOLD --es displaySpeed slow
tap 934 702; tap 702 702             # gem, DICE: the panel opens empty
$A shell input tap 1058 702          # coin
got_heads=; got_tails=
for n in $(seq 1 12); do
  sleep 13.2                         # the tumble (12 s), into the landing
  $A exec-out screencap -p > "$TMP/land.png"
  f=$(face_of "$TMP/land.png")
  echo "flip $n: $f"
  if [ "$f" = heads ] && [ -z "$got_heads" ]; then cp "$TMP/land.png" "$OUT/t61-heads.png"; got_heads=1; fi
  if [ "$f" = tails ] && [ -z "$got_tails" ]; then cp "$TMP/land.png" "$OUT/t61-tails.png"; got_tails=1; fi
  [ -n "$got_heads" ] && [ -n "$got_tails" ] && break
  sleep 5                            # past the landing: the result
  $A shell input tap 934 702         # AGAIN
done
[ -n "$got_heads" ] && [ -n "$got_tails" ] || { echo "both faces did not land in 12 flips" >&2; exit 1; }
for f in t61-heads t61-tails; do python3 tools/check_1bit.py "$OUT/$f.png" --scale 4; done
