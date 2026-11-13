# Mini sticker numerals, round two (T37)

Designer feedback on round one: the mini numerals are too big, about half that size again would
be good, and the `half` treatment is the preferred one. Also plan to move the counter peeks into a
corner instead of centring them under the life total.

This round has three hand-drawn faces in num-m's size class (about 8×14), each with the full
num-m charset (0–9, `+ - / :`, space, notdef), so any of them could replace `num-m` outright. It
also has four corner-peek layouts. The folder is nested two levels down, so `:app:generateFonts`
never reads it.

`python3 design/font/candidates/mini2/render.py` regenerates everything at true Nomad pixels (S=4,
composed on the dither grid, patterns at global phase, upscaled ×4). Both full-screen mocks pass
`tools/check_1bit.py --scale 4`. The script also checks every peek it places against the keep-out
rects (number slot, ± glyphs, tab hit area, readout, diamond + hit pad, V_MIN clearance) and prints
any collision; the readout slot (recommended face at k=2) counts as a keep-out too. The final run
prints collisions only for layout D (`far`) against the readout slot. They are real and
discussed below.

| File | What it shows |
|---|---|
| `specimen-<face>.png` | The charset at k=1, alternate zeros, k=2 above round-one `half`, k=4 for inspection, and samples beside the hero at k=1 and k=2 |
| `comparison.png` | strict, roomy, bold, `num-m` and round-one `half` in columns, in rows: digit strips; (a) the commander-target tile at k=1 and k=2; (b) the readout above the hero at k=1 and k=2; (c) drawer cells |
| `peeks-<layout>.png` | Each corner layout on an upright tile (seat 3) and a rotated one (seat 0), with 1, 3, 6 and 8 counters (8 includes poison 10 inverted and the `+N` overflow), values in `bold` |
| `in-context.png` | One real game state on the 1872×1404 Play screen, drawers closed. Seat 0 (rotated) has three counters. Seat 1 has a pending −6 readout over 34. Seat 3 has six counters, with poison 8 inverted. Peeks use `split`, values `bold` k=1, the readout `bold` k=2 |
| `in-context-commander.png` | One real commander-mode state. Seat 3 is the target: the readout is −3 and the life is `bold` k=2 under "NOT YOUR DAY". Seat 1's commander is lethal at 21 (ink card, +3 readout). Tabs are hidden and the diamond shows the close X. Compare with `design/captures/t30-commander-mode.png` |

## The three faces

All three are drawn pixel by pixel from the round-one `half` skeleton, not downsampled. They
keep what survives at 14 px: the oval stance with stepped round corners (2-1-0 steps), heavy even
colour, the 1's ball flag with a notch and no foot, the 4's solid wedge with a slit counter, the
7's curved stem, ball terminals on 2, 3, 5, 6 and 9 (a 3-px knob on a 2-px stroke), a pill-dotted
zero, and a 9 that is the 6 turned 180°. The stem ends of the 1 and 7 cannot be rounded at 2–3 px
wide. Neither can the hero's swollen balls, which become knobs.

| Face | Box | Pen | Tracking | `-999` | 2-digit peek chip | `+10998` at k=2 |
|---|---|---|---|---|---|---|
| `strict` (i) | 8×14 | 2 px | 2 | 38 px | 36 px | 116 px (the slot today) |
| `roomy` (ii) | 9×15 | 2 px | 2 | 42 px | 38 px | 128 px |
| `bold` (iii) | 10×14 | 3 px vertical / 2 px horizontal | 2 | 46 px | 40 px | 140 px (fits: the readout limit is 169) |
| `num-m` (today) | 8×14 | 2 px | 2 | 38 px | 36 px | 116 px |

**strict** (8×14, exactly num-m's footprint). The 2-px sides leave a 4-px counter. The zero holds
a 2×4 pill dot with 1-px gaps. A 1-px slit cannot be centred in an even counter, so it is not an
option here. The 8's top bowl is narrower than its bottom bowl, as in the hero, with a 2×2
counter. Strengths: it is a true drop-in, and every glyph is unambiguous (6/9/8/3/0 and 1/7
checked in `69 96 88 38 13 71 47`). Weakness: at k=1 it is hard to tell from `num-m`. The two
share a box, pen and colour. Only the 1, 4 and 7 and the small knobs differ, so the sticker link
is weak. A hero-style pointed-oval counter in the zero (alt `0-oval`) turns it into a near-solid
blob at k=1. Alternates on the specimen: no dot, pointed oval.

**roomy** (9×15, one more px each way). The odd width centres the zero's slit (1×5, a tall pill
like the hero's) and the 1's stem. The odd height gives the 3, 8 and `+`/`−` a true middle row,
and the `+`/`−` are 3 px thick. Weakness: one more px of width with the same 2-px pen makes it
*lighter*, which moves it away from the hero toward a generic rounded pixel face. The extra
legibility over strict is real but small. Of the three, it is the least like the sticker.
Alternates: no dot, 1×3 slit (which reads as a tick).

**bold** (10×14, num-m's height, 2 px wider). It has 3-px verticals and 2-px horizontals, like
the hero's heavy sides and thinner joins. That leaves room for the hero's pointed-oval zero
counter with a 2×4 pill. This is the one zero whose k=2 version reads as the big zero. The 4's
wedge, the 1's flag and the 6/9 knobs all carry, and the colour matches the solid 16-px icons in
peeks and cells. Weaknesses: it is 25% wider than num-m. The `+`/`−` (2 rows) look a little light
next to 3-px verticals at k=2, but they match the face's own horizontals. A 3-digit drawer value
(`100`) is 52 px against the 50-px cell content box, so a 3-digit cell would need the icon gap
cut from 2 to 0 or the value alone. Counters this big are rare (storm), but it is a real limit.
Alternate: no dot.

**At k=2 next to round-one `half`** (specimen row 2, comparison rows a2/b2). strict k=2 (16×28) has
a 4-px pen, close to half's 5. bold k=2 (20×28) has 6-px verticals, which matches half's 5 px on a
15-px box in colour. All three are free integer multiples, so one 14-px face covers both sizes
and no separate 27-px face is needed.

## Contexts (`comparison.png`)

- **(a) Commander target.** At k=1 the 14-px life sits under a k=2 quip whose caps are 14 px tall:
  the hierarchy flips and the life looks like a footnote. At **k=2** the life is the heaviest
  thing on the tile, as it should be. bold k=2 "♥14" reads as the hero's 14 shrunk, while today's
  `num-m` k=2 reads as a digital clock. Layout is CommanderLayout as is (lifeK = readoutK = 2, quip
  one size below the fit). One aside: the 16-px heart now looks small beside a 28-px bold life. A
  k=2 heart would balance it, but that is outside this task.
- **(b) Readout.** k=1 (14 px) is too quiet to notice a pending −7 from the seat. **k=2** keeps
  today's size and position. bold's "−7" over "33" reads as one family.
- **(c) Drawer cells.** All three fit the 19-px cell at k=1 (round-one `half` does not). bold
  matches the icons best.

## Corner peeks

**Which corners are free, per seat.** Each tile is laid out in the player's frame (y grows toward
the player). In that frame, the centre diamond always sits at one of the tile's **far** corners,
the one nearest the screen centre. That is far-left for seats 0 and 2 and far-right for seats 1
and 3. (For the bottom-row tiles this is their top inner corner on screen. For the rotated top row
it is their bottom inner corner on screen.) The **near** corners, either side of the tab, are free
on every seat. That makes them the only corners where a layout can be identical for all four
players. Near-corner room beside the tab is x 8..58 and x 175..225 (EDGE_MIN 8, tab hit ±8,
V_MIN 4, ink-chip pad 2), and from the bottom up to y 119 (number slot + V_MIN). Above that the
`−`/`+` glyphs (y 78..95) and the number slot (x 46..186) close it. So each near corner is about
50×52 dither px: one chip wide and three chips high (16-px icons at a 17-px pitch). The far corner
away from the diamond is x 8..50 × y 3..55: also three chips.

Chips are as today (icon, 2-px gap, value) in the mini face at k=1, with a 1-px paper rim so they
stay opaque on paper. Poison ≥ 8 is an ink chip with paper icon and value, 2-px pad. Peeks are
display only: they sit inside the ± press zones and claim no touch. When a layout runs out of
slots, its last slot becomes `+N` (the number hidden).

| Layout | Slots | Pros | Cons |
|---|---|---|---|
| **A `stack`**: bottom-left column, first counter in the corner, growing up the side | 3 | Smallest footprint (one corner). Always in the same place. Reads like a legend. | Only 3 slots: 6 counters show as 2 + `+4`. Lopsided: everything on the `−` side. |
| **B `split`**: both bottom corners either side of the tab, alternating L, R, L, R, bottom-up; left column on the left edge, right column right-aligned on the right edge | 6 | Six counters fit with no overflow, and eight show as 5 + `+3`. Symmetric: it frames the tab, so the tile stays balanced at any count. Uses the dead space beside the tab. The whole under-row band beneath the number is now empty, so the life total floats free. The same in every seat. | Uses both corners, so with 2+ counters there are two places to look. The alternating order is not a reading order, but the icons label every value. |
| **C `run`**: bottom-left run along the near edge toward the tab, wrapping upward | 3 in practice | The brief's idea, mocked honestly. | Two single-digit chips, even compact (icon + 1 px + digit, 2-px gap), need 52 px against the corner's 50, so the run never holds two chips and degenerates into A with tighter chips. Letting it pass the tab hit area, or run under the number, defeats the move. Rejected. |
| **D `far`**: the far corner away from the diamond, a column growing down | 3 | Nearest the table centre, so it is the best placement for *opponents* to read your poison (about one tile height, 6 cm, closer than a near corner). | The side flips by seat (far-left on 1 and 3, far-right on 0 and 2). It is not consistent in the player's frame, and on the rotated row it lands at the screen's middle seam, next to the diamond's tile. Only 3 slots. It **collides with the readout slot** (bold k=2: x 46..186, y 15..43) whenever a 2-digit or inverted chip sits beside it (the renderer flags 3- and 8-counter states), so it would also force the readout narrower. |

**Reading from across the table.** The values are 14 px = 56 device px, about 4.7 mm at 300 dpi,
the same as `num-m` today. The k=1 bold digits and solid icons carry at arm's length for the owner.
From across the table they are upside down and small whatever the layout. The inverted poison chip
(≥ 8) is the one peek an opponent will spot, and it reads as an ink block in any corner. Only D
gets closer to opponents, and the price is the inconsistency above.

## Recommendation

- **Face: `bold`** (10×14, 3-px verticals). It is the only one of the three that is unmistakably the
  sticker's little sibling at both k=1 and k=2. strict is honest about the 8×14 box, but at k=1
  it is num-m with a sticker 1, 4 and 7. roomy is lighter, not stickier. The 2 extra px of width
  cost nothing in any context: the readout fits, peeks fit, cells fit up to 2 digits. It has
  `/` and `:`, so it can replace `num-m` entirely, dice included. If the 3-digit drawer cell or
  num-m's exact width turns out to matter, **strict** is the drop-in fallback.
- **Sizes:** commander-target life **k=2**; pending-delta readout **k=2** (today's size, so no
  layout change); counter peeks **k=1**; drawer cell values **k=1**. One face, two integer sizes.
  This replaces round one's two-face plan (`half` + `pocket`).
- **Peeks: B `split`.** Both near corners either side of the tab, alternating, 6 slots, `+N` in
  the sixth when there are more. It is the only layout that fits the brief's six counters
  without overflow, it is identical for every seat, and it clears the number, ± glyphs, readout,
  tab and diamond with V_MIN to spare. Seeing the mocks, take A `stack` only if the designer
  wants peeks on one side and accepts `+N` from four counters up.

Mocks are composed on the dither grid at S=4, not device captures. Check bold k=1 at arm's length
on the Nomad before adopting it. To adopt it, copy `bold/glyphs.txt` to `design/font/num-m/` (or
a new face) and move PeekLayout's row into the two corner columns.
