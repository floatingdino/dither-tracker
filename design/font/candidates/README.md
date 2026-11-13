# Hero numeral candidates (T26)

Designer feedback: `num-l` feels thin and plain for life totals on the device. These are five
different heavier directions, each a complete `0`–`9`, `+`, `-` set in the usual `glyphs.txt` format.
They live one level deeper than real faces (`design/font/candidates/<name>/glyphs.txt`), and
`GenerateFontsTask` only picks up `design/font/*/glyphs.txt`, so the build ignores them. To adopt
one, copy it to `design/font/num-l/glyphs.txt` and change `face` to `num-l`.

`python3 design/font/candidates/render.py` regenerates, at true Nomad pixels (S=4, pure 1-bit):
`specimen-<name>.png` (every glyph, sample totals, pressed/inverted), `in-context-<name>.png` (the
1872×1404 Play screen with 40 / 37 / 108 / −7; `in-context-num-l.png` is the current face for
reference) and `comparison.png` (`40` and `-128` in every face). The mock follows `PlayLayout` as
committed at T06 (kMax by the same `fits` maths, ± at `pmK`). The one change: for faces drawn
natively at k=1, the ± are shown at half size (a 2×2 downsample). The current layout would draw them
at full digit size, so adopting a k=1 face also needs a small layout change: draw ± at half size,
or take them from `num-m` at k=2.

All five fit `-999` in ≤152×56 dither px. Every one keeps 6/9 as exact 180° rotations, which is
fine because nobody reads a total upside down. 1 and 7 are separated by a flag versus a full top
bar, and 3 and 8 by a closed versus open left side.

| Face | Box | Tracking | k | Stroke | `-999` |
|---|---|---|---|---|---|
| `gumdrop` | 30×56 | 6 | 1 | 8 px round pen | 138×56 |
| `sticker` | 32×56 (29×53 body) | 4 | 1 | 9 px round pen + dither shadow | 140×56 |
| `slab` | 16×28 | 4 | 2 | 4 px stems / 3 px bars (8/6 dither px) | 152×56 |
| `chamfer` | 16×28 | 4 | 2 | 4 px stems / 3 px bars | 152×56 |
| `scoreboard` | 24×56 | 8 | 1 | 7 px stems / 6 px bars | 120×56 |

**gumdrop**: a heavy rounded "soft grotesk". Every stroke is one 8-px round pen on squircle
(superellipse) bowls. It keeps the dotted zero as a pill-shaped dot, and the 1 has a flag and a base.
It is drawn at k=1, so its pixel grain (4 device px) matches the icon set exactly, and the curves
are smooth rather than stair-stepped. It reads instantly from across a table and feels friendly
and sturdy. Weakness: it is the safest of the five. The character comes from weight and roundness,
not from any quirk, so it can look a little generic (rounded-DIN territory).

**sticker**: puffy bubble numerals with swollen ball terminals (on the 1's flag and the 2, 3, 5, 6
and 9) and a 50% checker drop shadow, 3 px down and right behind a 1-px paper keyline. It looks
like a die-cut sticker and is the most playful set. It is also the only one that uses the app's
dither language. The shadow reads as a soft grey on e-ink and never fills a counter. Weaknesses:
the counters are small (the 8's top bowl and the 4), so it is the darkest at a glance. The checker
is baked into the glyph in its own phase. The advance is even, so a run of digits stays in phase,
but rule 3 (global pattern phase) properly needs the shadow drawn as a second mask filled with a
Role pattern. That is a small renderer change. It is also the busiest set during a partial
refresh, with more edge pixels to redraw.

**slab**: a chunky Clarendon/typewriter slab, with ball terminals (2, 3, 5, 6, 9), slab feet
(1, 4), spurs (2, 5, 7) and a slit-dotted zero. It uses the same 16×28 box and tracking as `num-l`,
so it is a drop-in at k=2 with no layout change. It has the most "voice" for the least risk: warm,
bookish and a little tongue-in-cheek, like a tabletop game manual. Weakness: at k=2 one font pixel
is 8 device px, so the curves are coarse and blocky, twice as coarse as the icons. The ball
terminals are only about 6 px across and look a little lumpy up close, though they are fine at
table distance.

**chamfer**: a classic-Mac/Chicago-spirited bitmap face. Every corner is a 45° cut instead of a
curve, the bowls are octagonal, and the zero has a diagonal slash. The cuts echo the centre
diamond's cut tips and the stepped corners in the icons. It is also a drop-in for `num-l` at k=2.
It is crisp and unambiguous, and nostalgic in a friendly way. Weakness: it feels more "computer"
than "toy". The 1 and 4 have little of the cut motif, and at k=2 it shares slab's coarse grain.

**scoreboard**: a tall, condensed, heavy face with big rounded outer corners, square inner corners
and a slot-dotted zero, like stadium scoreboard digits. It is the narrowest (`-999` is only 120
wide), so it would leave room for a larger k on bigger screens or for longer values. Weaknesses:
it is the least friendly of the five, close to a digital clock. The first draft's open-top 4 read
as an "h" upside down and had to be closed. At this size the narrow counters make it look darker
and colder than the others. It is included to show the direction, not as a contender.

## Recommendation

**gumdrop**, with **sticker** as runner-up.

gumdrop answers the feedback most directly. It is about twice the weight of `num-l`, the k=1 grain
matches the icons, the curves are smooth, and the round pen gives the same soft, chunky feel as the
redesigned icons without competing with them. It also keeps the play surface calm, which matches
the spec's line that personality "lives in small spaces". Adopting it needs the half-size ±
layout tweak described above.

If the designer wants more personality on the main surface, sticker is the most charming and the
most "Dither Tracker", since the dither shadow is the app's own visual language. But it costs a
renderer change for the global-phase shadow and some legibility in small counters. Pick slab if a
zero-layout-change drop-in is the priority.
