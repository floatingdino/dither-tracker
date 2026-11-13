# T34 regression captures

`tools/capture_scenarios.sh` on the Nomad (S = 4, 1872×1404), built from `ed758fa` (the component
refactor complete, before T37/T15 changed any pixels). All 19 are byte-identical to the same script
run on the pre-refactor build (`fe98dc3`), and every one passes `tools/check_1bit.py --scale 4`.

01 fresh · 02/03 rotated seat 1 taps + hold, readout / committed · 04 upright seat 3 taps + hold,
readout · 05/06 drawers open (upright 3, rotated 1) with counters bumped, poison 8 warning ·
07 tax cell held (press inversion) · 08 seat 1 page 2 · 09/11 peeks · 10 seat 0 tab held ·
12 commander mode (target 3) · 13 damage from seats 2 and 1 (lethal 21), readouts · 14 committed ·
15 target tile held · 16 left by the target tile · 17 mode again · 18 close X held · 19 after the X.

Later tasks that change pixels on purpose (T37 numerals, T15 KO, …) make new baselines with the
same script; it is the regression harness, not a frozen golden set.
