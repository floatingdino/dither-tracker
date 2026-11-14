# Effects (shipped set: T28c, round three promoted in T55)

The effects the app ships. The app build reads every `design/fx/<name>/` with a `meta.txt` (one
level deep) into the generated `Fx` enum; `design/fx/options/` (round two, T28d) and
`design/fx/round3/` (round three's remaining candidates, T28f) are design archives, never shipped. Rules: SPEC rule 4 (keyframes, not inbetweens). Effects are always on
(no setting). Runtime: `app/.../fx/README.md`.

`sheet.png` shows every shipped effect (round three's included) as a strip over a sticker `37` in a full seat tile with its
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
| `fizzle` | KO (compact seat) | `centre 0 -2` | 80×76 | 3 | 404 / 416 / 489 / (dead tile) | round one |
| `winner` | last standing | tile | 233×175 | 3 | 1112 / 774 / 1666 / 100 | options |

**Round three (T55, the owner's approvals; authored by `round3/author3.py`, one 149×165 frame
centred on the total for every tier unless noted):**

| Effect | Role | Size | Flips per beat (over `37`, quad tile) |
|---|---|---|---|
| `slash` | damage | 149×165 | 600 / 591 / 69 |
| `chomp` | damage (big band, rare) | 149×165 | 1418 / 3198 / 2036 / 138 |
| `stomp` | big hit (huge band) | 149×165 | 1024 / 3200 / 2684 / 450 / 88 |
| `crit` | big hit (crit band, repurposed: was a natural 20) | 149×165 | 2307 / 2859 / 576 / 16 |
| `chime` | heal | 149×165 | 165 / 612 / 459 / 12 |
| `heart-burst` | heal (huge band) | 149×165 | 1446 / 2320 / 1162 / 234 |
| `imp-raspberry`, `imp-poke` | commander damage taken | 149×165 | 492 / 823 / 883 / 52; 910 / 983 / 1135 / 102 |
| `ooze`, `brand` | poison gained | 149×165 | 1232 / 1423 / 1987 / 224; 1094 / 1562 / 506 / 48 |
| `charge` | energy gained | 149×165 | 687 / 1281 / 622 / 60 |
| `level-up` | experience gained | 149×165 | 1067 / 1809 / 898 / 118 |
| `rad-glow` | rad gained | 149×165 | 1111 / 2532 / 1635 / 16 |
| `storm-copy` | storm gained | 149×165 | 799 / 858 / 1419 / 60 |
| `crown-drop` (+ `-narrow`) | monarch claimed | 149×165 | 552 / 1128 / 718 / 62 (narrow 568 / 520 / 718 / 62) |
| `torch` (+ `-narrow`) | initiative taken | 173×165, `anchor centre -12 0` (narrow 149×165) | 529 / 838 / 1255 / 12 (narrow 367 / 392 / 537 / 12) |
| `ring-tempt` | ring-bearer on | 149×165 | 274 / 1882 / 1585 / 57 |
| `dusk` / `dawn` | night / day (every seat at once) | 149×165 | 567 / 1542 / 1169 / 440; 924 / 1379 / 547 / 252 |
| `your-turn`, `spotlight` | the turn passes to this seat | 149×165 | 528 / 948 / 420; 1351 / 1126 / 427 |
| `start-flag` | picked to start | 149×165 | 580 / 935 / 663 / 42 |
| `revive` | a KO'd seat revived | 149×165 | 145 / 928 / 1243 / 246 |

Art fixes the owner asked for (strips at 4×: `design/captures/t55-crown-drop-before-after.png`,
`t55-torch-before-after.png`, `t55-*-narrow.png`):
- **crown-drop**: frame 1's crown now falls on the vertical line it lands on in frame 2 (it came in
  from the right and landed centred), 20 px above its landing with speed lines between its spikes.
  On the narrow tier the diamond bites the middle seats' top edge over the centre line, so
  `crown-drop-narrow` falls 5 px from just under it.
- **torch**: the D1 firelight disc is whole — the frame widens to 173 px on the left (anchor
  `centre -12 0`) so the disc reaches behind the − glyph; both light tiles are `ink` mode, so the −
  still reads through them. A whole disc that size can't fit a 155-wide tile, so `torch-narrow` is a
  smaller whole disc for the narrow tier.
- A `<name>-narrow` folder is a narrow-tier twin: `FxPolicy.variant` plays it instead of `<name>`
  whenever the layout's tier is narrow (5 and 6 players at S = 4).

Not shipped: `bonk`, `nibble`, `refill`, `bunting`, `crown-cut` (not approved: still candidates in
`round3/`), `coin-spin` and `fumble` (dropped on principle: a coin or die effect can't show which
player acted).

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

**Frame holds (T65).** Every shipped effect's `hold_ms` is a curve — the read frame longest, in and
out shorter, every hold ≥ 250 ms; a fast display derives shorter in/out frames from it. Before/after
per effect: `app/.../fx/README.md`, "Timing (T65)".

**Timing (T55).** A seat's life effect starts 300 ms after its last life change (not at the
readout's commit), alongside the readout; once per band per readout group, again only when the
running total escalates into a bigger band. Counter and toggle effects play 300 ms after the last
change of a burst, on a net gain against the start of the burst, once per trigger until 3 s pass
without one. Details: `app/.../fx/README.md`.

Life bands: **small** 1–3, **big** 4–9, **huge** 10 or more, and for damage a **crit** band from 20.
Weights are relative within a pool (cheap effects common, expensive ones rare); a seat never gets the
same effect twice running when its pool has another.

| Trigger | Full seat (weight) | Compact seat (drawer open; clipped above the drawer) |
|---|---|---|
| damage, small | `pow` 3, `crack` 3, `slash` 3, `quake` 2 | `pow` 3, `crack` 3, `slash` 3 |
| damage, big | `claw-cut` 3, `quake` 2, `pow` 1, `crack` 1, `slash` 1, `chomp` 1 | `pow` 2, `crack` 2, `slash` 2, `claw-cut` 1 |
| damage, huge (10–19) | `focus` 1, `stomp` 1 | as big |
| damage, crit (20+) | `crit` | as big |
| heal, small | `twinkle` 3, `sprout` 3, `chime` 3, `heart` 2 | the same |
| heal, big | `rise` 2, `heart` 2, `twinkle` 1, `chime` 1 | `twinkle`, `sprout`, `chime`, `heart` |
| heal, huge | `sunrise` 1, `vines` 1, `heart-burst` 1 | as big |
| KO (the group took the seat out) | `ko-shatter` | `fizzle` |
| last player standing | `winner` on the survivor | the big heal pool |
| commander damage taken (the target's group) | `imp-raspberry` 1, `imp-poke` 1 | the same |
| poison gained | `ooze` 1, `brand` 1 | the same |
| energy / experience / rad / storm gained | `charge` / `level-up` / `rad-glow` / `storm-copy` | the same |
| monarch claimed / initiative taken / ring-bearer on | `crown-drop` / `torch` / `ring-tempt` | the same |
| it becomes night / day (every live seat at once) | `dusk` / `dawn` | the same |
| the turn passes to this seat | `your-turn` 3, `spotlight` 1 | the same |
| picked to start | `start-flag` | the same |
| revived from KO | `revive` | the same |

Why those weights: `chomp`'s bite is 3198 flips (3.4× a number change) and big hits are common, so
1 in 9 big hits; `claw-cut` (~1300 a beat) stays the usual big hit as before; `spotlight` (1351) is
1 turn pass in 4 (and never twice running on a seat); the huge, crit and table-moment triggers are
rare by nature, so their pools are even. The city's blessing, tickets and the generic counter play
nothing. Effects play only on a gain (never a decrement, an undo, a redo or a restored game), not on
a KO'd tile, not under a full overlay, not for a tracker the settings hide (T53), and a touch on the
seat stops them. `fault` is shipped but in no pool.

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
- `round3/author3.py` authors round three: promoted effects (`PROMOTED`, with their shipped role)
  into `design/fx/<name>/`, the remaining candidates into `round3/<name>/`. It reads icon art from
  `design/icons/` each run, so re-run it after an icon changes (T55 re-ran `ring-tempt` for T51's
  ring). `round3/sheet3.py` draws the round-three review sheets from either place.
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
  rising, then the dead tile draws. Each beat's dissolve is one tile layer over the box (`tile 1 D4
  paper`, `2 D8`, `3 D12`), so the pattern comes from the global screen phase at draw time (T48;
  it used to be baked into the frame at frame-local phase and sat off the global phase). Check D12
  for smear.

Round-one rules still hold for every effect: comic beats each held ~250–400 ms, a sparse frame
before the erase, no text, shapes that read upside down, a 1 px paper halo where marks cross ink.

## What to test on the device (T28c)

The coordinator films these on the Nomad with the gallery (`app/.../fx/README.md`, "Debug
gallery"). In short: settle time per beat (`fxHold`), residue after 50 runs per effect, grey seams
after `x` cuts and 1 px paper slices (`claw-cut`, `crack`, `quake`), smear in `HLINES`/`D4`/`D8`/
`D12` areas (`focus`, `sunrise`, `ko-shatter`, `fizzle`), a tile-scale beat next to a number change
(local partial refresh or a bigger flash?), two seats at once, and legibility from the opposite
seat.
