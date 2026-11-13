# T34 regression captures

The 19 base states of `tools/capture_scenarios.sh` (what `BASE_ONLY=1` stops after), as re-scripted in T49, captured
in T50 on the Nomad-shaped emulator (S = 4, landscape 1872×1404, 4 players; seat 1 has a partner,
commander quips seeded with `--el seed 7`). Built from `02f301c` plus the T50 cleanups; all 19 are
byte-identical to the same script run on `02f301c` itself, and every one passes
`tools/check_1bit.py --scale 4`.

Seats run clockwise from the top-left: 0 TL, 1 TR (both turned 180°), 2 BR, 3 BL; everyone starts at 40.

| capture | what it shows |
|---|---|
| `01_fresh` | A fresh table: four 40s, the diamond, closed tabs. |
| `02_seat1_readout` | Seat 1 (turned) after two `−` taps and a hold: 18, pending readout `−22`. |
| `03_seat1_committed` | 4 s later: the readout committed and its effect finished; 18 alone. |
| `04_seat3_readout` | Seat 3 (upright) after three `+` taps and a hold: 63, readout `+23`. |
| `05_drawer3_open` | Seat 3's drawer open: tax 2, poison 2, energy 1 (the life total moves up to the compact slot). |
| `06_drawers_open` | Seat 1's drawer open too (turned, partner tray): tax 2, poison 8 in the warning (inverted) style. |
| `07_press_tax_cell` | Seat 3's tax cell `+` half held: the press inversion on that half. |
| `08_drawer1_page2` | Seat 1's drawer on page 2 with its generic counter at 1; seat 3's tax now 4. |
| `09_peeks` | Both drawers closed: the counters peek from the tile corners (seat 3: energy 1, tax 4, poison 2; seat 1: poison 8 inverted, tax 2, generic 1). |
| `10_press_tab0` | Seat 0's tab held: the tab's dither inverted. |
| `11_peeks_again` | Seat 0's drawer opened by that release and closed again: identical to `09`. |
| `12_mode_enter` | Commander mode, seat 3 the target: its tile shows the quip (`THIS'LL STING`) and its life (63); the other seats show damage dealt to it (0; seat 1 in partner quarters), the diamond is the close X. |
| `13_mode_damage` | Seat 2 dealt 3 (committed), then seat 1's primary 20 by a hold: readouts `+20` on seat 1 and `−20` on the target, target life 40 (not lethal). |
| `14_mode_committed` | 4 s later: readouts committed (3 and 20 dealt, target 40). |
| `15_press_target` | The target tile held: the whole tile inverted. |
| `16_mode_left_by_target` | Released: back to play; seat 3 at 40 after 23 commander damage, peeks as before. |
| `17_mode_again` | Commander mode again on seat 3: the next quip (`BRACE YOURSELF`), damage tallies kept. |
| `18_press_close` | The centre X held (inverted). |
| `19_after_close` | Released: back to play; identical to `16`. |

Later tasks that change pixels on purpose make new baselines with the same script; it is the
regression harness, not a frozen golden set. The full device set (`tools/check_purity.sh`, 98
captures with the gem, dice, settings, history, KO, dark mode and every effect keyframe) is not
kept here.
