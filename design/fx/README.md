# Hit / heal effects (shipped set, T28c)

The effects the app ships. The app build reads every `design/fx/<name>/` with a `meta.txt` (one
level deep) into the generated `Fx` enum; `design/fx/options/` is the design archive of round two
(T28d) and is never shipped. Rules: SPEC rule 4 (keyframes, not inbetweens). Effects are always on
(no setting). Runtime: `app/.../fx/README.md`.

`sheet.png` shows every shipped effect as a strip over a sticker `37` in a full seat tile with its
furniture, at true Nomad pixels (S=4), each strip starting with the clean tile (= the end state),
labelled with flips per beat. Rebuild it with `python3 design/fx/sheet.py` (it reuses
`options/render2.py`). Device captures: `design/captures/t28c-*.png`.

## The set

| Effect | Role | Anchor | Size | Beats | Flips per beat (over `37`) | From |
|---|---|---|---|---|---|---|
| `pow` | damage | `tr -14 10` | 84×84 | 2 | 531 / 470 / 283 | options `pow2` |
| `crack` | damage | centre | 64×108 | 3 | 325 / 200 / 537 / 12 | options `crack2` |
| `claw-cut` | damage | centre | 160×132 | 2 | 1317 / 1278 / 163 | options `claw-cut` |
| `quake` | damage | tile | 233×175 | 3 | 372 / 624 / 272 / 20 | options |
| `fault` | damage (not in rotation) | tile | 233×175 | 3 | 983 / 123 / 1122 / 16 | options |
| `focus` | big hit | tile | 233×175 | 2 | 1443 / 1863 / 420 | options |
| `twinkle` | heal | centre | 150×100 | 3 | 230 / 510 / 292 / 12 | options `twinkle2` |
| `rise` | heal | tile | 233×175 | 3 | 342 / 772 / 450 / 20 | options |
| `sprout` | heal | `top 0 -14` | 32×28 | 3 | 52 / 164 / 144 / 8 | round one |
| `heart` | heal | `tr 6 -10` | 44×44 | 3 | 108 / 264 / 328 / 12 | round one |
| `sunrise` | big heal | tile | 233×175 | 2 | 286 / 1166 / 1094 | options |
| `vines` | big heal | tile | 233×175 | 3 | 306 / 674 / 864 / 116 | options |
| `ko-shatter` | KO | tile | 233×175 | 2 | 1541 / 1399 / (dead tile) | options |
| `fizzle` | KO (compact seat) | `centre 0 -2` | 80×76 | 3 | 498 / 485 / 420 / (dead tile) | round one |
| `winner` | last standing | tile | 233×175 | 3 | 1112 / 774 / 1666 / 100 | options |

For scale: a plain number change `37`→`36` flips 938 dither px. What each one looks like and its
risks: `options/README.md` (round-two effects) and the round-one notes below.

**Promotion (T28c).** The designer approved every round-two effect ("all the animations are great";
they love the dither shading in overlays and where effects meet the numerals). Of the claws,
`claw-cut` was picked; `claw-paper` and `claw-swipe` stay in the archive. The refits replaced their
round-one originals and took their names (`pow2` → `pow`, `crack2` → `crack`, `twinkle2` →
`twinkle`). Retired: round-one `claw`, `wham`, `zap`, `pow`, `crack`, `twinkle` (in git history).
Kept as alternates from round one: `heart`, `sprout`, `fizzle`. Frames are copied byte for byte;
each meta differs only in its title comment and an explicit `role` line (`FxPromotionTest`).

## Which effect plays (`FxPolicy`)

Once per committed pending-delta group (never per tap or hold step), by the group's net life change:

| Trigger | Full seat | Compact seat (drawer open) |
|---|---|---|
| damage (−1…−9) | `pow`, `crack`, `claw-cut`, `quake` | `pow`, `crack`, `claw-cut` |
| big hit (−10 or more) | `focus` | the damage pool |
| heal (+1…+9) | `twinkle`, `rise`, `sprout`, `heart` | `twinkle`, `sprout`, `heart` |
| big heal (+10 or more) | `sunrise`, `vines` | the heal pool |
| KO (the group took the seat out) | `ko-shatter` | `fizzle` |
| last player standing | `winner` on the survivor | the heal pool |

A pool is a rotation: a random pick that is never the effect that seat played last. Tile-scale
frames are drawn against the full tile's furniture, so a compact seat only gets number-anchored
ones. Commander damage that takes life counts as damage on the target's tile. Poison and other
counters get no effect. `fault` is shipped but not in a pool (the weakest tile-wide read; kept for
the gallery and a future style choice).

## Format

```
design/fx/<name>/frame-1.txt … frame-N.txt   all W×H; frame-N is empty (the clean end state)
design/fx/<name>/meta.txt
```

Symbols: `.` transparent, `#` ink, `o` paper (knock-out), `x` cut (invert what is under it: white
through the digits, black over paper), `1`–`9` a tile layer declared as `tile <d> <TILE>
<ink|paper|fill>` and sampled at global screen phase. `meta.txt` holds `size`, `frames`, **`role`**
(`damage|heal|big-hit|ko|win`, required here), `anchor`, `tile` lines, `hold_ms` (last = 0), and
optionally `ink_px`/`knockout_px`/`cut_px`/`tile_px`/`flips`. Full rules: `app/.../fx/README.md`
("meta.txt as parsed") and `options/README.md`.

**Anchors.** `anchor tile dx dy` centres a 233×175 frame on the seat tile. Every other ref is a
point of the life total's **whole footprint**: the sticker box, `n·32 + (n−1)·4` × 56 at k = 1,
shadow rows included, centred on the tile centre (116, 87). That is the box `render2.py` drew the
designs against and what the app passes (not the 53-row cap band). `FxAnchorTest` composites every
frame of every shipped effect against the real layout's footprint, on an upright and a turned seat
and for 1-, 2- and 3-digit totals, and matches `render2.py`'s placement pixel for pixel.

## Tools

- `sheet.py`: the shipped sheet (above).
- `author.py` (round one) re-authors only `heart`, `sprout` and `fizzle` now and refuses the other
  names, which would overwrite the shipped refits. `render.py` is its helper library; running it
  directly writes round-one strips and overwrites `sheet.png` (use `sheet.py`).
- `options/author2.py` and `options/render2.py` author and render the round-two archive; they read
  only `options/`. Its `r1-` rows read `design/fx/<name>`, which now holds the refits, so they no
  longer show the round-one frames.

## Round-one notes (the three still shipped)

- **heart** (heal): a small heart off the numeral's top-right shoulder grows into a bigger heart
  with a paper shine and pop ticks, then three dots. Never touches the digits. Its big heart is
  solid (~300 px): check it for ghosting.
- **sprout** (heal): a seedling pokes up between the digits and unfurls two leaves. The cheapest
  effect. Subtle; grow the leaves 1 art px if it reads too small.
- **fizzle** (KO, compact seats): the numeral dissolves through D4 → D8 → D12 knock-out with puffs
  rising, then the dead tile draws. Its dither is baked into the frame (frame-local phase), so it
  can sit out of phase with the checker shadow by a pixel. Check D12 for smear.

Round-one rules still hold for every effect: comic beats each held ~250–400 ms, a sparse frame
before the erase, no text, shapes that read upside down, a 1 px paper halo where marks cross ink.

## What to test on the device (T28c)

The coordinator films these on the Nomad with the gallery (`app/.../fx/README.md`, "Debug
gallery"). In short: settle time per beat (`fxHold`), residue after 50 runs per effect, grey seams
after `x` cuts and 1 px paper slices (`claw-cut`, `crack`, `quake`), smear in `HLINES`/`D4`/`D8`/
`D12` areas (`focus`, `sunrise`, `ko-shatter`, `fizzle`), a tile-scale beat next to a number change
(local partial refresh or a bigger flash?), two seats at once, and legibility from the opposite
seat.
