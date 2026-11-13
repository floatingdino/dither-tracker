# Mini sticker numerals (T37 candidates)

Designer feedback: the commander-target tile's life total (`num-m` in paper on a solid ink strip)
loses the link to the hero total, and the strip is heavy. These are three shadowless "little
sibling" cuts of the `num-l` sticker face: digits, `+`, `-`, space and notdef, monospace, no `:`
cells. They sit two levels below `design/font/`, so `:app:generateFonts` ignores them. To adopt
one, copy it to `design/font/<face>/glyphs.txt` and set `face`.

`python3 design/font/candidates/mini/render.py` regenerates everything at true Nomad pixels (S=4,
composed on the dither grid, patterns at global phase, upscaled ×4; all three in-context boards
pass `tools/check_1bit.py --scale 4`):

- `specimen-<name>.png`: every glyph at k=1 and k=2, then 40 37 35 108 −7 +12 0 21 in the hero
  sticker (with shadow) beside the mini face, bottoms aligned.
- `in-context-<name>.png`: a 1872×1404 board. Top-left (rotated) and bottom-left (upright) are
  commander targets ("HERE IT COMES" 35, "UH OH" 21). Top-right has a `+12` pending readout.
  Bottom-right has peeks (tax 4, poison 7, energy 12). It is a composite of states, not one
  real game state.
- `comparison.png`: half, plump, pocket and `num-m` in columns, in the three contexts: target
  tile; readout above the hero; peeks plus a drawer row.

All three were drawn by hand at size, using the hero halved row by row as the reference, not a
downsample (a geometric draft at 15 px closed every counter). Shared rules: the zero keeps its
pill dot, the 1 its ball flag with a notch and no foot, the 4 its solid wedge, the 2, 3, 5, 6 and 9
their ball terminals, and the 7 its curved stem. 9 is the 6 turned 180°, as in the hero.

## Candidates

| Face | Box | Tracking | Pen | `-999` |
|---|---|---|---|---|
| `half` | 15×27 | 3 | 5 px (zero sides 4) | 69×27 |
| `plump` | 19×27 | 3 | 6 px verticals / 5 px horizontals | 85×27 |
| `pocket` | 13×19 | 2 | 4 px verticals / 3 px horizontals | 58×19 |

**half** (a): a faithful half-size redraw. The 15×27 body is the hero's 29×53 halved, the
proportions (0.55 wide) and pen/width ratio (0.33) are the hero's, and each glyph follows the
hero's skeleton. Optical corrections: the zero's sides thin to 4 px through the middle, so the
counter is 7 px and holds a 3-px pill dot (a 1-px slit read as a bar). The 6/9 ball is swollen to
6 px with a 2-px throat. The 4 keeps a 1-px slit. Strengths: it is unmistakably the hero's little
sibling. A small 35 has the same shapes and rhythm as the big 35. At 27 px it is the same height
as today's readout (`num-m` k=2, 28 px), so it drops in with no layout change. Weaknesses: small
counters (the 8's top bowl is 3×4, the 6/9 throat 2 px). It is the darkest of the three, and the
ball terminals are subtle at this size.

**plump** (b): an optical-size cut at the same height, 4 px wider. The heavier verticals and the
width open everything: the zero's counter is 7 px with a rounded 3×7 pill dot, the 8's top bowl is
5 px wide, the 4 gets a real 3×4 triangular counter, the 6/9 throat is 3 px, and the 1's notch is
2 px. Strengths: the most legible at arm's length, especially 6/9/8/0, and the most even in
colour. Weaknesses: the extra width (0.70 against the hero's 0.55) pushes it towards a rounded
grotesk. The oval zero and the narrow, tall sticker stance are what tie the family together, and
plump loses some of both even after its shoulders were rounded. `-999` is 85 px wide.

**pocket** (c): a compact cut for tight spots. 19 rows is chosen on purpose, not 20–22: at S=4 a
drawer card's content box is exactly 19 px (row 27 − 2×2 inset − 2×2 pad), and the peek band
between the number and the tab or button hit areas is 22 px. pocket fits both with no layout
change (the peek row grows from 18 to 19). Horizontals are 3 px and verticals 4, so the counters
stay tall. The zero keeps a 1×3 dot. Strengths: it fits everywhere `num-m` goes, it is
noticeably heavier and rounder than `num-m`, so peeks and cell values finally match the hero and
the solid icons, and every distinguishing feature survives. Weaknesses: on the target tile it
reads as small, the dot and the 4's slit are 1 px, and the 3-px `+`/`-` are a little light.

## Target tile (proposal, all boards)

The quip (`ui`, ALL CAPS) and the life are one unit, centred on the hero's cap centre, so the eye
lands where the big total usually sits. The quip is capped at k=3 (k=4 made "UH OH" as tall as the
life and blocky; long quips step down to k=2 as today). The gap is 3k+2 (11 px at k=3, 8 at k=2).
The life sits below on plain paper, led by the 16-px solid heart 4 px to its left, centred on the
numeral cap band, with heart and number centred as a unit. There is no strip and no frame. The
mini face's weight is enough to anchor the pair under the lighter quip.

**Recommendation: `half`** for the target tile. It is the only one whose small 35 reads instantly
as the big 35, which is the designer's point. Its 27-px life gives the pair a clear hierarchy
(heavy number, lighter quip), and it matches the readout height, so one new face covers both.
Take plump only if the 2-px throats and the 8's small top bowl prove too dark on the device. Check
at S=4 at arm's length before deciding.

## Should the mini face also replace `num-m`?

- **Pending-delta readout: yes, with `half`.** It is a drop-in: 27 px against `num-m` k=2's 28,
  and `+10998` is 6×15 + 5×3 = 105 px, under today's 116-px slot. `num-m` at k=2 is a 4-px-stroke
  digital face that looks foreign above the sticker (see `design/captures/t27-drawer.png`). In the
  mini face, "−7" over "40" reads as one family, and the delta keeps its weight without
  competing. The +/− glyphs are full-width bars like the hero's.
- **Counter peeks and drawer cell values: yes, with `pocket`, not `half` or `plump`.** At 27 px,
  half and plump do not fit a drawer card (comparison row 3 shows them overflowing), and as peeks
  they crowd the number and dwarf the 16-px icons. pocket fits both, needs only the peek row
  grown by 1 px, and is a clear upgrade on `num-m` k=1. Keep `num-m` for dice and anything needing
  `/` or `:` until pocket gets those glyphs.

So the recommended set is `half` (target life and readout) plus `pocket` (peeks and drawer
values). Two faces, both visibly the sticker's family. `num-m` stays only for dice and slashed
values. Test on the device before adopting either. These are mocks rendered on the dither grid,
not device captures.
