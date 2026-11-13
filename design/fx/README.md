# Hit / heal effects (T28a)

Design concepts for the SPEC rule-4 exception: small, few-frame overlay flourishes when a life
total takes damage or heals. Nothing here is wired into the app yet.

`sheet.png` has every effect: base number, then each frame composited over a sample `37`, at true
Nomad pixels (S=4). Each cell is one real seat panel (233×175 dither px). `strip-<name>.png` has the
same content for one effect. The `in-context-*.png` files show the 1872×1404 play screen with the
effect on an upright seat (bottom-left) and a rotated seat (top-right). They are built from
`design/captures/t06-play.png`.

## Format

```
design/fx/<name>/frame-1.txt … frame-N.txt   all the same size
design/fx/<name>/meta.txt
```

- `#` draws black, `o` draws white (knock-out: paper cuts through the numeral), `.` is transparent.
- **frame-N is always empty.** The runtime ends by redrawing the seat's clean number. For `fizzle`
  it ends by drawing the KO/dead panel instead.
- `meta.txt` holds:
  - `size W H`
  - `frames N`
  - `anchor <ref> dx dy`: the frame centre goes at a reference point of the life total's **ink
    box**, plus (dx, dy). Units are dither px, in the seat's upright coordinates with y down. `ref`
    is one of `centre|tl|tr|bl|br|top|bottom|left|right`. Corner-anchored effects (`pow`, `heart`)
    therefore follow the box for 1-, 2- and 3-digit totals. A rotated seat rotates the frame and
    the offset by 180°, exactly like its other content (`blit(..., rotation)`).
  - `hold_ms` per frame. The last entry is 0 because the empty frame is the end state.
  - `ink_px` and `knockout_px` per frame.
  - `flips`: dither px that change on each transition, base→f1→…→clean, measured over `37`. One
    dither px is 16 device px.
- `author.py` draws the frames from a few primitives and writes `meta.txt`. Re-running it for an
  effect overwrites that effect's `.txt` files, so do hand edits after the last run.
  `render.py` (stdlib only) draws the PNGs:
  `python3 design/fx/render.py --context pow:1:33 zap:1:33 heart:2:43`.

## E-ink rules these follow

1. **Comic beats, not motion.** Each frame is a still the panel must settle on (~250–400 ms).
   Effects have 2–3 drawn beats plus the clean frame, about 0.6–1.1 s in total. Each beat reads on
   its own: burst → chips, bolt → sparks, small heart → big heart.
2. **Small and sparse.** Frames are 32×28 to 76×68 dither px. A few heal frames are wider so the
   marks can sit beside the numeral, but those frames are mostly empty. A beat changes **50–570
   dither px**. For scale, a normal number change (37→36) already changes ~750 dither px, and
   40→33 changes ~1100. One effect beat costs about as much as one number change, or less.
3. **One pen.** Effects are drawn on a 2-dither-px grid (`up2`), which is the same pen as `num-l`
   at k=2 and the icons. Marks drawn with 1 px lines looked spindly and the 1 px twinkles read as
   `+` signs, so they were cut in pass 1.
4. **Paper halo.** Marks that cross the numeral get a 1 px `o` outline. They stay legible over ink,
   and the cut through the digit is part of the effect: a dent, a slash or a crack. Over paper the
   halo costs nothing.
5. **Sparse frame before the erase.** The second-to-last frame is dots, chips or ticks, so the
   final erase changes little and leaves little residue.
6. **The only solid black areas are small:** the bolt (~500 dither px), the big heart (~300) and
   the leaves. Everything else is 2 px strokes.
7. **No text, no direction-dependent meaning.** Each frame is drawn in the seat's own frame, so
   the seated player always sees it upright. Shapes are symmetric or read fine upside down for the
   other players.
8. **Dither only from the tile set.** Only `fizzle` uses dither: `D4`/`D8`/`D12` as knock-out, and
   only over the numeral. Its tile phase is frame-local. Phase only matters next to other shading,
   and effects sit on the `SURFACE` (D0) paper, so it doesn't show. If that changes, snap the frame
   origin to a multiple of 4 dither px, or re-dither at runtime with the global phase.

## The effects

### Damage

- **pow**: a comic impact burst lands on the numeral's top-right shoulder (`anchor tr`) and dents
  the digit with its paper interior. Next beat, four chips fly off with speed ticks. Then clean.
  - Frames: 2 drawn + clean. 60×60. Flips 439 / 479 / 96.
  - **Risk:** the 16-point outline is close to a "sun" or "badge" shape, so check that it reads
    as an impact at arm's length. It sits on a corner, so the total stays readable throughout.
- **zap**: a chunky bolt in the icon style strikes the top of the numeral. Next beat, four sparks
  kick up from the impact point; then a three-dot smoke wisp; then clean.
  - Frames: 3 drawn + clean. 48×64, entirely above the numeral (`anchor top`). Flips 500 / 571 /
    103 / 16.
  - This one reads instantly.
  - **Risks:** it has the most solid black of any effect (a ghosting candidate). The bolt is also
    close to the `energy` counter icon. In context its tip comes within ~5 dither px of the panel
    margin.
- **claw**: three bold 45° slashes rake the middle of the numeral, then shrink to short 1 px
  scratches, then clean.
  - Frames: 2 drawn + clean. 64×64. Flips 308 / 270 / 62.
  - **Risk:** it covers the digits mid-number. The first versions used long slashes at the
    font's own slope, which read as a crossed-out number or a different digit (`3\3` → "38"). It
    is fixed by using 45° (no glyph has that stroke), shorter slashes and wider spacing, but it is
    still the busiest damage frame.
- **crack**: a zig-zag hairline crack (2 px, with halo) splits the numeral between the digits. Next
  beat it branches and two shards drop off; then two crumbs; then clean.
  - Frames: 3 drawn + clean. 76×68. Flips 148 / 63 / 219 / 8.
  - It is the quietest damage effect and reads more as "crack" than as "hit". It could be a good
    calmer default.

### Heal

- **heart**: a small heart appears off the numeral's top-right shoulder. It grows into a bigger
  heart with a paper shine and four pop ticks, then leaves three dots, then clean.
  - Frames: 3 drawn + clean. 44×44 (`anchor tr`). Flips 108 / 264 / 328 / 12.
  - Its meaning matches the `life` icon and it never touches the numeral.
  - **Risk:** the big heart is solid, about 300 dither px. That is small, but check it for ghosting.
- **sprout**: a seedling pokes up out of the gap between the digits, unfurls two leaves, then
  leaves two dots, then clean.
  - Frames: 3 drawn + clean. 32×28. The cheapest effect: flips 52 / 164 / 144 / 8.
  - It is charming and on-tone. It's also subtle, so the leaves could grow by 1 art px if it reads
    too small on the device.
- **twinkle**: two fat-cored twinkles pop at the numeral's shoulders. They swap sides and drift
  upward, with a hollow glint each time, then fade to dots, then clean.
  - Frames: 3 drawn + clean. 100×76 bounding box, mostly empty. Flips 181 / 361 / 192 / 12.
  - The diamond core keeps them from reading as a `+`.
  - **Risk:** it is the most "generic app" of the three.

### Optional

- **wham**, *big hit, change ≥ 10*: eight impact wedges point in at the numeral from all round,
  then recoil outward as ticks, then clean.
  - Frames: 2 drawn + clean. 120×96, all of it sparse. Flips 320 / 416 / 96.
  - It frames the number rather than covering it, so it can be layered after the normal damage
    effect, or replace it.
- **fizzle**, *KO*: the numeral dissolves through D4 → D8 → D12 knock-out with little puffs rising,
  and then the dead (CROSSHATCH) panel draws.
  - Frames: 3 drawn + dead panel. Only numeral pixels flip (~230 per beat).
  - This reads beautifully as "fizzling out". It is the only effect that works through dither, so
    check the D12 frame for grey-to-grey smear.

### Cut

- **chomp** (a bite out of the corner): it is invisible on an outline numeral, because there is no
  solid mass to bite.
- **glow** (a dithered ring around the number): it read as a selection frame or button and flipped
  the most pixels.
- **bubbles**: the small circles read as `37°`.
- **shake** (cartoon wobble brackets): it read as `((37))`.

## Recommendation

**Prototype `pow` (damage) and `heart` (heal) first.**

- Both are corner-anchored: they never cover the digits, so the new total is readable from the
  first frame.
- Each is one simple silhouette that reads at a glance.
- Both have only 2–3 drawn beats.
- Both are cheap in flips.

`zap` is the strongest read and the runner-up. Try it second, once we know how the Nomad treats
a ~500-dither-px solid shape that appears and disappears. `sprout` is the charming alternative heal and
the cheapest effect of all.

### When effects should trigger

Trigger **once per committed delta group, never per tap**.

The pending-delta model already gathers taps into `-7` and commits after a short idle. Play one
effect for the committed group, choosing damage or heal by the sign of the net delta. Play
nothing for a net 0.

1. **Panel time.** Each beat costs a panel update of a few hundred ms. Per tap, a run of 7 taps
   would queue ~20 frames, or keep cancelling itself, leaving half-drawn residue. Per commit, there
   is one 0.6–1 s flourish, after the user has stopped touching that seat.
2. **Four players at once.** Commits are per seat and staggered. Each seat's effect is confined to
   its own View and its own few hundred pixels, so concurrent effects in two seats only add pixels
   to the same panel updates. They don't add extra full redraws. Still cap it: **at most two seats
   animating at once**. A third commit just redraws its number clean.
3. **Never block or queue** (SPEC rule 4).
   - A tap on a seat cancels its running effect and redraws that seat clean immediately.
   - Hold-to-repeat never triggers an effect; only the final commit does.
   - Effects never start within ~1 s of a cleanup flash, and the flash cadence counts an effect
     as one change.
4. **Size thresholds.** `|Δ| ≥ 1` plays the normal effect. `|Δ| ≥ 10` may use `wham` (optional).
   A KO plays `fizzle` instead of the damage effect. Commander damage that auto-links to life uses
   the same rule on the life change. Poison and counters get no effect.
5. **Off switch** in settings (rule 4). Consider defaulting to *on for big hits and KO only* if the
   on-device test shows any residue.

Runtime shape:

1. Draw frame k into the seat View over the freshly drawn number, with `#` as ink and `o` as
   paper. In the same frame, invalidate only that seat.
2. `postDelayed(hold_ms[k])`, then go to the next frame.
3. The final frame is the normal `onDraw` of the seat.

Frames are tiny ASCII → `IntArray` masks. Bake them at build time like the fonts so `onDraw` does
no allocation.

## What to test on the device

1. **Settle time per beat.** Film at 240 fps and find the shortest hold where frame 1 fully
   resolves before frame 2 (start at 300 ms; try 200/300/400). That sets every `hold_ms`.
2. **Residue after the clean frame.** Run 50× `pow`, `zap` and `heart` on one seat, then photograph
   it under raking light. Is there a ghost star, bolt or heart? Compare with 50 plain number
   changes (eink.md E3).
3. **Knock-out over ink.** Do the `o` cuts through digit strokes come back clean when the number
   is redrawn, or do they leave grey seams?
4. **`fizzle` D12 frame**, the only dithered effect: check that it is crisp and not smeared.
5. **Four seats at once.** Commit on all four within ~1 s. Is there any full-screen flash, and
   does input latency hold up? Check that the cap of two animating seats feels right.
6. **Read at arm's length** from an opposite seat: does each effect read as hit or heal, upside
   down, in under a second? Check that `pow` doesn't read as a sun and `zap` doesn't read as the
   energy counter.
7. **Inverted (dark) mode:** `#` and `o` swap with the theme, and halos should still work.
