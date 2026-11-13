# Hit / heal effects, round two (T28d)

These effects are drawn over the **sticker** hero numerals. They answer the designer's feedback on
round one: "pow, crack and twinkle are good; claw is close; I like the cut-outs on the numerals;
open up the space more."

Everything here is work in progress. It sits one folder below `design/fx/`, so the app build, which
reads `design/fx/<effect>/` directly, never picks it up. To adopt an effect, copy its folder up a
level. Its runtime also has to support any new symbols it uses (see Format).

## Files

- `sheet.png` shows every option as a strip. Each frame is drawn inside a **full seat tile** with
  its real furniture (tab, ± glyphs and the corner of the centre diamond), at true Nomad pixels
  (S=4), over a sticker `37`. Each strip starts with the clean tile, which is also the end state.
  Every frame is labelled with its flips: the dither px that change from the previous beat.
  - Rows prefixed `r1-` are the **before** rows: the unchanged round-one frames over the sticker
    numerals.
- `in-context-<name>-f<k>.png` are 1872×1404 play-screen mocks of the best four. Frame k is drawn
  on an upright seat (bottom-left) and a rotated seat (top-right). All four pass
  `tools/check_1bit.py --scale 4`.
- `render2.py` draws the PNGs and prints flips per transition. It also prints "furniture hits",
  which count changed pixels on the tab, the ± glyphs or the diamond zone in either top corner;
  every option scores 0. `author2.py` draws the frames, and `order.py` sets the sheet rows. They
  import `../render.py` and `../author.py` but leave both unchanged.
  - Rebuild with:
    `python3 design/fx/options/author2.py && python3 design/fx/options/render2.py --context quake:1`
- The base tile comes from `design/captures/t07-delta.png`, with the totals and the readout erased.
  Effects play after the commit, when the readout has already gone.

**Scale.** With sticker numerals, a plain number change costs **938 flips** (`37`→`36`), and
`40`→`33` costs 1171. Below, "flips" means dither px changed per beat: base→f1, f1→f2, …, last
beat→clean. One dither px is 16 device px.

## Format

The format is the same as round one, with two additions:

```
.   transparent
#   ink
o   paper (knock-out)
x   CUT: paper where the seat has ink under it, ink where it has paper. That is an invert under the
    mask. The runtime can XOR it, or split it into two masks with the numeral's own ink mask (it
    has just drawn the numeral from that mask). The furniture never sits under an x.
1-9 tile layer, declared in meta.txt as  `tile <digit> <TILE> <mode>`
      mode ink   = ink where the tile has ink, transparent elsewhere
      mode paper = paper where the tile has ink, transparent elsewhere  (ghosts the numeral)
      mode fill  = ink where the tile has ink, paper elsewhere           (opaque)
    The tile is sampled at GLOBAL screen phase (SPEC rule 3), after the frame has been rotated
    for the seat, exactly like any other fill in DitherRenderer.
```

`meta.txt` also allows `anchor tile dx dy`. With it, the frame centre is the seat tile's centre
plus (dx, dy). Tile-scale frames are exactly 233×175 with a blank 3 px margin, so they never
touch the dotted dividers. The other refs (`centre`, `tr`, …) are points of the total's layout box,
as in round one. Each frame is drawn in seat-upright coordinates and rotated 180° with the seat,
so the `x` and tile layers survive rotation unchanged.

Each `meta.txt` holds:

- `size`
- `frames`, where the last frame is empty and is the clean redraw
- `anchor`
- `tile` lines, where the effect uses tile layers
- `hold_ms`
- `ink_px`, `knockout_px`, `cut_px` and `tile_px` per frame
- `flips` per transition

**Why `x` exists.** A claw or a crack should be white inside the digits and black on paper, but a
frame is static while the total varies. `x` is the only way for a mark to do both for every
number. `claw-paper` shows the fallback without `x`. It works with a round-one runtime, but it
reads worse.

## The effects

### Refits of the approved three

- **pow2** (damage). A bigger 8-point burst lands on the total's top-right shoulder. Its paper
  interior punches a white star out of the digit, and its 2 px rim shows over paper. Next beat, a
  ragged **dent** stays bitten out of the digit, drawn in paper only, so it shows only where there
  is ink. Four 4 px chunks fly off with speed ticks. Then clean.
  - 84×84 (`anchor tr -14 10`). Flips 531 / 470 / 283.
  - Before/after: on the heavy digit the round-one burst read as a decoration beside the 7. Now it
    visibly takes a bite.
  - **Risk:** the bite position depends on the last digit's top-right corner. A `1` or a `4` has
    less ink there, so less is bitten.
- **crack2** (damage). A zig-zag drawn with `x` runs down through the gap between the digits and
  out above and below. It is a white fissure through solid ink and a black crack over paper. Next
  beat it widens to 4 px and branches sideways into both digits, and two shards drop. Then crumbs,
  then clean.
  - 64×108. Flips 325 / 200 / 537 / 12.
  - It reads instantly. The white fissure through a black digit is the most satisfying cut on the
    sheet.
  - **Risk:** where it crosses the checker shadow, `x` swaps that shadow's phase. That is
    invisible at arm's length.
- **twinkle2** (heal). A fat twinkle pops off the left shoulder, and a **gloss highlight** (a
  2 px paper arc plus a dot) is knocked into the top of the 7, like the shine on a vinyl sticker.
  The two swap: the gloss appears on the 3's bowl, and the twinkle appears top-right with a small
  one opposite. Then three dots, then clean.
  - 150×100. Flips 230 / 510 / 292 / 12.
  - Pass 2 used small cut-out 4-point glints. They read as `+` signs, which was the round-one
    lesson again, so the gloss replaced them.
  - **Risk:** the gloss is paper only, so for a total whose first or last digit has no ink at
    those points, that beat shows only the twinkle.

### Claw, closer

All three swipe `\` at about 50°. No sticker digit has a stroke at that angle. The slashes are
long and tapered, and they run well past the numeral on both sides, so they read as a swipe across
the tile rather than a crossed-out number.

- **claw-cut** (damage). Three slashes drawn with `x`, 5 px at the waist, tapering to 1 px points,
  spaced about 20 px apart. They are white gashes through the digits and black blades past them,
  extending about 30 px beyond the numeral both ways. Next beat only three short white **nicks**
  stay in the digits, and four 5–6 px chunks spill off the exit end. Then clean.
  - 160×132. Flips 1317 / 1278 / 163.
  - The total stays readable in both beats.
  - **Risk:** this is the busiest frame of the recommended set. Each beat costs about 1.4× a
    number change.
- **claw-paper** (damage, fallback). The same as claw-cut, but without `x`: each slash is paper
  inside the 2-digit box and ink outside it.
  - Flips 921 / 882 / 163.
  - Where a slash crosses paper inside the box, there is a visible gap, so the slashes look broken.
    On 3-digit totals the outer digits are not cut. Keep it only if `x` is refused.
- **claw-swipe** (damage or big hit, tile scale). The same three slashes, 160 px long, rake the
  tile from near its top-left to its bottom-right. They are fattest where they cross the digits
  and thin out to the tile edges, clear of the tab and both ±. Next beat: nicks plus five chunks
  landing in the bottom-right corner.
  - Flips 2109 / 2049 / 216.
  - This is the most dramatic option, but each beat costs about 2.2× a number change. Use it for
    big hits only.

**Cut:** `claw-curve`, three curved slashes. With any curve, at least one slash ran along a
digit's bowl and chopped the 3 into fragments, so it read as a broken number rather than a
swipe.

### Tile-wide options

- **quake** (damage). Beat 1: three 1 px paper **slices** cut horizontally through the whole
  numeral, a glitch or jolt, with impact ticks at its four corners. Beat 2: the shockwave has run
  out to the tile edges as two broken dotted elliptical arcs. The arcs break around the ± glyphs
  and the tab. Beat 3: five dots at the rim. Then clean.
  - Flips 372 / 624 / 272 / 20, cheaper than one number change per beat.
  - Its reach comes from the dots alone.
  - **Risk:** the slices are 1 px paper lines through ink. Check them on the device for grey seams
    after the redraw (round-one test 3).
- **fault** (damage). One `x` crack, 2–4 px with sharp irregular angles and two short branches,
  runs edge to edge across the tile through the numeral. It is white through the digits and black
  over paper, and it passes well clear of the ± glyphs. Beat 2: two more branches, and four
  chunks drop toward the bottom. Beat 3: crumbs. Then clean.
  - Flips 983 / 123 / 1122 / 16.
  - Beat 2 adds little, so it could be cut to two beats.
  - **Risk:** over paper, the long run of the crack can still read a little like a graph line.
    It's the weakest of the tile-wide set.
- **focus** (big hit). Beat 1: about 30 manga **focus lines**, tapered wedges 3 px at the base and
  22–34 px long, rush in from every edge of the tile, skipping the ±, tab and diamond zones.
  Meanwhile the whole numeral is **ghosted**: an `HLINES` tile in `paper` mode knocks out every
  other row. The ghost reads as a white flash and can't be mistaken for the checker shadow.
  Beat 2: the numeral is back, solid, framed by four 3 px impact corners. Then clean.
  - Flips 1443 / 1863 / 420.
  - This is the strongest "BIG HIT" read on the sheet, and the total stays legible even while
    ghosted.
  - **Risk:** HLINES is a 1 px line pattern. Check it for smear. The tile is sampled at global
    phase, so both seats show the same rows.
- **vines** (heal). Two vines sprout in the bottom corners and climb the side edges, below the ±
  glyphs, with leaves turned inward. Beat 2: they reach the ± height and each opens a flower, and
  runners with upright leaves creep along the bottom edge toward the tab. Beat 3: only the two
  flowers. Then clean.
  - Flips 306 / 674 / 864 / 116.
  - It is charming and on-tone, but its reach is modest. Taking the vines past the ± glyphs made
    them read as `}-` (tried in pass 2).
- **rise** (heal). Beat 1: four fat twinkles (r 6–11) pop low around the numeral. Beat 2: they
  have risen and spread across the upper tile, with three hollow diamond glints between them.
  Beat 3: five dots near the top. Then clean.
  - Flips 342 / 772 / 450 / 20.
  - This is the sparse way to fill the upper tile.
- **sunrise** (heal). Beat 1: seven short rays in a fan, filled with `D4` in `ink` mode, rise from
  behind the numeral. Beat 2: the rays reach the top edge of the tile (corners trimmed for the
  diamond), with a fat twinkle at two of the tips. Then clean.
  - Flips 286 / 1166 / 1094.
  - It is the softest and prettiest heal, and it is what the dither tiles are for.
  - **Risk:** the large D4 area appears and disappears in one beat each. It is the main ghosting
    candidate among the heals, and its beat 2 costs about as much as a number change.

### Optional flourishes

- **ko-shatter** (KO). Beat 1: the numeral is ghosted to 50% (`D8` paper), and a black crack
  with a paper halo runs across the tile through it. Beat 2: the ghost fades to 25% (`D12` paper),
  the crack is gone, and five chunks fall. Then the runtime draws the dead panel.
  - Flips 1541 / 1399, and then the dead panel. The 1312 on the sheet is measured to the clean
    number.
  - **Risk:** the D8 ghost is the same density as the checker shadow, so in beat 1 the shadow
    merges with the body. It still reads, because the crack carries it. Round one's `fizzle` is
    the cheaper KO.
- **winner** (last player standing). Beat 1: a **laurel wreath** closes round the numeral, with two
  fat twinkles above it. Beat 2: confetti bursts across the whole tile around the wreath. The
  confetti is tumbling bars, solid squares, squiggle streamers and `CHECKER`-filled squares.
  Beat 3: four pieces settle at the bottom. Then clean.
  - Flips 1112 / 774 / 1666 / 100.
  - It is the most "celebration" of anything here. It plays once per game, so its cost is fine.
  - **Risk:** at a glance, the squiggle streamers can read as `~`.

## Recommendation

| Role | 1st | 2nd | 3rd |
|---|---|---|---|
| **Damage** | **quake**: cut-out slices, tile-wide reach, cheapest per beat | **claw-cut**: the cut-out swipe the designer asked for | pow2, then crack2 |
| **Heal** | **twinkle2**: the gloss cut-out is charming and cheap | **rise**: sparse sparkles fill the upper tile | sunrise (try on device first), vines |
| **Big hit (≥10)** | **focus**: ghosted numeral and focus lines | claw-swipe | quake as the normal hit |
| **KO** | **ko-shatter** | round-one `fizzle`, cheaper | |
| **Last standing** | **winner** | | |

- A coherent default set is **quake / twinkle2 / focus / ko-shatter / winner**. Offer claw-cut,
  pow2 and rise as alternates if the settings ever get an effect-style choice.
- All of these need the `x` symbol and tile layers in the runtime. quake, pow2 and rise need
  neither, so they could ship on the round-one engine as it is being built now.

**Retire from round one:**

- `claw`: superseded by claw-cut. Its old frames read `3\7` on the sticker digits (see the `r1-claw`
  row).
- `wham`: superseded by focus, which does the same job (framing a big hit) with more punch.
- `zap`: its solid bolt is the biggest ghosting risk, and it clashes with the energy icon.
- `pow`, `crack` and `twinkle` in their round-one form: superseded by the refits. On the heavy
  numerals the round-one marks sit beside the digits instead of biting into them (see the
  `r1-` rows).
- Keep `heart` and `sprout` as quiet alternates, and `fizzle` as the cheap KO.

## What to test on the device

The round-one list still applies. In addition:

1. **The `x` cut and the 1 px paper slices.** After the clean redraw, are there grey seams where
   white cut through black?
2. **One tile-scale beat** (about 1.4–2k flips, as in focus or claw-swipe) **next to a number
   change.** Does the partial refresh stay local to the seat, or does it trigger a bigger flash?
3. **`HLINES` and `D4` areas** that appear and disappear in one beat (focus, sunrise). Check for
   smear and residue after 50 runs.
4. **1- and 3-digit totals.** claw-cut, crack2 and fault are anchored on the centre, and pow2 on
   the box corner. Every option here was only rendered over `37`. Run
   `render2.py --number 8` and `--number 108` before adopting one.
