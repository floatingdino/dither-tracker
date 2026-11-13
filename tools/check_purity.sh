#!/usr/bin/env bash
# Device half of the pixel-purity suite (T22): captures every scripted state with
# tools/capture_scenarios.sh, runs tools/check_1bit.py --scale S on each capture, prints a summary
# table and exits non-zero if any capture fails (or none were taken).
#
#   ANDROID_SERIAL=emulator-5562 tools/check_purity.sh <outdir>      # install, capture, check
#   NO_CAPTURE=1 tools/check_purity.sh <outdir>                      # only check PNGs already there
#   SCALE=4 (default)   the S the captures were drawn at (the emulator / Nomad pick 4)
#
# Every capture_scenarios.sh switch passes through (NO_INSTALL, BASE_ONLY, NO_FX, FX_HOLD). Always
# name the device with ANDROID_SERIAL when more than one is attached. See docs/architecture.md,
# "Verification harness".
set -uo pipefail

OUT=${1:?usage: check_purity.sh <outdir>}
S=${SCALE:-4}
HERE=$(cd "$(dirname "$0")" && pwd)

if [ -z "${NO_CAPTURE:-}" ]; then
  mkdir -p "$OUT"
  rm -f "$OUT"/*.png
  if ! "$HERE/capture_scenarios.sh" "$OUT"; then
    echo "check_purity: capture_scenarios.sh failed" >&2
    exit 2
  fi
fi

shopt -s nullglob
files=("$OUT"/*.png)
if [ ${#files[@]} -eq 0 ]; then
  echo "check_purity: no captures in $OUT" >&2
  exit 2
fi

pass=0
fail=0
printf '%-34s %-5s %s\n' capture result detail
printf '%-34s %-5s %s\n' ---------------------------------- ----- ------
for f in "${files[@]}"; do
  name=$(basename "$f" .png)
  report=$(python3 "$HERE/check_1bit.py" "$f" --scale "$S" --max-report 3)
  rc=$?
  if [ $rc -eq 0 ]; then
    pass=$((pass + 1))
    detail=$(printf '%s\n' "$report" | sed -n 's/.*(\([0-9.]*% black\)).*/\1/p' | tail -1)
    printf '%-34s %-5s %s\n' "$name" PASS "$detail"
  else
    fail=$((fail + 1))
    detail=$(printf '%s\n' "$report" | grep -v '^grid\|^FAIL' | head -1 | sed 's/^ *//')
    printf '%-34s %-5s %s\n' "$name" FAIL "$detail"
  fi
done
echo
echo "$pass passed, $fail failed, S=$S, $OUT"
[ $fail -eq 0 ]
