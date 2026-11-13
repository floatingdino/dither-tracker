# Effects, round three (T28f): candidates, design only

New effects in the round-two style: dither shading inside the overlays, cut-outs that meet the
numeral, reach across the tile, open space. Nothing here is wired into the app. The build reads
`design/fx/<name>/` one level deep, so `design/fx/round3/<name>/` is never picked up (same as
`options/`). To adopt one, copy its folder up a level, then give it a `role` the runtime knows and
a pool in `FxPolicy` (see "Needs from the runtime" below).

## Files

- `author3.py` draws every candidate (`python3 design/fx/round3/author3.py [name ...]`). It
  imports the round-one and round-two primitives without changing them.
- `render3.py` is the round-two compositor (`options/render2.py`) for both seat tiers. Tile layers
  are sampled at the tile's real screen position (global phase). It counts flips, furniture and
  diamond hits, and clipping.
- `sheet3.py` writes the review sheets (`python3 design/fx/round3/sheet3.py`):
  - `design/captures/t28f-{damage,heal,counter,state,table}.png`: each group on the quad
    4-player tile (233×175, from `t07-delta`).
  - `design/captures/t28f-narrow.png`: every candidate on the narrow 5/6-player tile (155×175, the
    bottom-middle seat of `t14-6p-narrow`, with the diamond biting its top edge).

  The sheets are drawn at S = 4 over a sticker `37`. Each strip starts with the clean tile, and
  each beat is labelled with its flips. Every sheet passes `tools/check_1bit.py --scale 4`.
- Frame format: unchanged from round two (`. # o x 1-9`, `tile <d> <TILE> ink|paper|fill`). Every
  shading is a tile layer from the predefined set (`D1`, `D2`, `D4`, `D8`, `D12`, `CHECKER`,
  `HLINES`), so nothing is baked into a frame. `CROSSHATCH` is not used (retired from the set in T50).
  - The one exception is the chequered flag in `start-flag`. Its 5 px squares are a drawn motif,
    like `winner`'s confetti, not a shading.
- `meta.txt` holds the round-two keys plus a proposed `role`, a `# trigger:` comment, and the
  narrow-tile flips as a comment.

## Geometry: one frame for every tier

Every candidate is a single **149×165 frame anchored on the total's centre** (`nibble` alone is a
72×72 frame on the box's `tr`). The quad and narrow tiles both centre the total on the tile
centre, so the same frame fits both: on the narrow tile it leaves a 3 px margin each side. Nothing
depends on sub-tile centring. On the quad tile the ± glyphs (|x| ≥ 88) and the diamond corner lie
outside the frame.

`author3.keepout()` clears two zones and reports any cells it had to clear. The final art avoids
both zones:

- the tab: |x| ≤ 47, y ≥ 64
- the diamond on the narrow middle seats: |x| + (y + 87) ≤ 27

Every candidate scores 0 furniture or diamond hits on both tiers. Nothing clips with a 3-digit
`108` on the narrow tile.

Seats turned in 90° steps rotate the frame with the blit. All the art reads turned:
- there is no text
- the night sky, the beam and the bunting sit "above" the total in seat coordinates, so they turn
  with it
- `x` and tile layers survive rotation (round-two rule)

Effects may sit over the toggle badges under the total (y ≈ 33…46) for one beat. The clean redraw
restores them.

**Scale.** A plain number change (`37`→`36`) costs 938 flips. Shipped `focus` costs 1443 and 1863
per beat, and `claw-cut` costs 1317 and 1278.

## The candidates

Columns:
- **Flips** are per beat over `37` on the quad tile: base→f1, …, last beat→clean.
- **Holds** are in ms. The last frame is the empty clean redraw, held for 0 ms.
- **Tiers:** every candidate works on quad and narrow, full seats and turned seats.
- **Call:** promote, revise or drop.

### 1. Damage variety, scaled by hit size

| Effect | Size | Intent | Holds | Flips | Call |
|---|---|---|---|---|---|
| `bonk` | small, −1…−3 | A toy mallet (`D4` fill body, paper shine) bonks the top of the total. Dizzy stars circle on a dotted orbit, then two stars remain. | 300 350 300 | 979 / 1113 / 316 / 112 | **promote** |
| `nibble` | small, −1…−3 | A monster mouth (ink lips, paper inside) bites the last digit's top-right corner and leaves a scalloped bite (paper only) and crumbs. Anchored `tr`, so it follows 1-, 2- and 3-digit totals. | 300 350 300 | 791 / 591 / 22 / 310 | **promote** |
| `slash` | small to big, −1…−9 | One tapered sword stroke cuts across the total (`x`) with a glint at the tip. Then a 1 px paper split remains in the digits. | 350 350 | 600 / 591 / 69 | **promote** |
| `chomp` | big, −4…−9 | Jaws (gums in `D8`, paper teeth) open over and under the total, snap shut with a white zig-zag bitten through it, then tooth notches remain in its top and bottom edges. | 300 350 300 | 1418 / 3198 / 2036 / 138 | **revise** |
| `stomp` | huge, −10 or more | A `D2` shadow falls over the total. A giant bare footprint stamps it: the toes are ink, and under the sole the digits are squashed to a `D12` ghost. Dust puffs and cracks follow. | 250 400 300 250 | 1024 / 3200 / 2684 / 450 / 88 | **promote** (big-hit, alongside `focus`) |

- **bonk:** the cleanest "8-bit" hit on the sheet. The stars beat is cheap and readable from the
  opposite seat.
- **nibble:** the cut-out the designer liked, in a new shape, and cheap.
- **slash:** the cheapest cut-out on the sheet. Beat 2's hairline is subtle, so check on the device
  whether it reads at arm's length.
- **chomp:** a strong read, but beat 2 costs 3.4× a number change, which is too heavy for the most
  common hit band. Either drop the jaws from beat 2 and keep only the zig-zag and the impact ticks,
  or move it to the big-hit pool.
- **stomp:** the footprint reads at a glance, and the ghost under the sole is the cut-out. Beat 2
  is the heaviest frame among the promoted damage effects, which is acceptable for a rare hit.

### 2. Heal variety, scaled by heal size

| Effect | Size | Intent | Holds | Flips | Call |
|---|---|---|---|---|---|
| `chime` | small, +1…+3 | An item jingle: a note hops off the left shoulder, then a note and a beamed pair hop higher, then dots. | 300 350 250 | 165 / 612 / 459 / 12 | **promote** |
| `refill` | big, +4…+9 | An upturned flask (`D8` liquid) pours drops onto the total. Above a wavy `x` level line the total is drained to a `D12` ghost, and the level rises in beat 2. Then bubbles and a twinkle. | 300 300 300 | 1557 / 1466 / 1175 / 144 | **revise** |
| `heart-burst` | huge, +10 or more | A big heart outline with a `D2` blush wraps the total, then bursts into seven solid hearts flying to the corners. | 350 350 250 | 1446 / 2320 / 1162 / 234 | **promote** (big heal, alongside `sunrise` and `vines`) |

- **chime:** the cheapest effect here. It is a quiet alternate to `sprout`.
- **refill:** the "gauge refilling" idea is good. But the ghosted top half reads as fog rather than
  an empty glass, and the flask is small. Try `HLINES` for the empty part, as `focus` uses, and a
  bigger flask. Check the D12 ghost for smear.
- **heart-burst:** a big, soft, on-tone heal that uses the dither blush the designer loves.

### 3. Counter-specific

None of these change life. Today counters get no effect (`FxPolicy`), so every one needs a new
trigger.

| Effect | Trigger | Intent | Holds | Flips | Call |
|---|---|---|---|---|---|
| `ooze` | poison gained | Goo slops over the top of the total and drips down through it. Goo and drips are `x`, so they are white through the digits and black on paper. The poison mark (`icons/poison.txt`) sits beside it. Then a puddle. | 300 350 250 | 1232 / 1423 / 1987 / 224 | **promote** |
| `brand` | poison gained (alt) | The poison mark at 4× is slapped over the total like a sticker (2 px paper keyline), then shrinks to the shoulder and bubbles away. | 350 300 250 | 1094 / 1562 / 506 / 48 | **drop** |
| `imp-raspberry` | commander damage taken | The commander imp (redrawn from `icons/commander.txt`) peeks over the total gripping its top edge, blows a raspberry (squeezed eyes, tongue out, spit flecks), then vanishes in a puff. | 300 400 250 | 492 / 823 / 883 / 52 | **promote** |
| `imp-poke` | commander damage (alt) | The grinning imp jabs the total with a pitchfork. The tines go in as `x`, and three paper holes remain. | 300 350 300 | 910 / 983 / 1135 / 102 | **revise** |
| `charge` | energy gained | The energy bolt strikes the top of the total and cracks into it (`x`), then zig-zag sparks crackle round all four corners. | 300 300 250 | 687 / 1281 / 622 / 60 | **promote** |
| `level-up` | experience gained | A pillar of light (`D2`, fading to `D1`) rises behind the total with chevrons on both sides. The chevrons climb and the experience icon tops the total. Then twinkles. | 300 350 250 | 1067 / 1809 / 898 / 118 | **revise** |
| `rad-glow` | rad gained | A `D4` glow ring surrounds the total, with the rad trefoil. Then the total glitches into `HLINES` scanlines inside a wider `D2` ring. Then specks drift off. | 300 300 250 | 1111 / 2532 / 1635 / 16 | **promote** |
| `storm-copy` | storm count up | A `D8` storm cloud strikes the total once (`x` bolt), then copies the strike three times across it. | 300 350 250 | 799 / 858 / 1419 / 60 | **promote** |

- **ooze:** the most "Phyrexian" read on the sheet, and the drips melt the digits.
- **brand:** it hides the total behind a blob that reads as "3Φ". `ooze` does the job better.
- **imp-raspberry:** rude but friendly, and it uses the imp from the icons. It stays above the
  total, so the number never hides.
- **imp-poke:** the jab muddles the left digit. Shorten the tines, and let beat 2 keep only the
  holes plus the grin.
- **charge:** the bolt is fine here. It clashes with nothing, because this is the energy counter
  itself.
- **level-up:** the chevrons and the star are good. Beat 1's pillar reads a little like a
  grey box. Try a narrower beam, or drop it.
- **rad-glow:** the glitch scanlines are the best numeral interaction among the counters. Its
  beat 2 costs 2.7× a number change. Check `HLINES` for smear.
- **storm-copy:** "copy" carries the storm mechanic.

### 4. State changes

| Effect | Trigger | Intent | Holds | Flips | Call |
|---|---|---|---|---|---|
| `crown-drop` | monarch claimed | A crown (`icons/monarch.txt`) drops in with speed lines, lands on top of the total with a bounce and a glint, then twinkles. | 250 400 250 | 570 / 1146 / 718 / 62 | **promote** |
| `crown-cut` | monarch (alt) | A giant crown sticker is slapped over the total, then shrinks to sit on top of it. | 350 350 | 1425 / 1909 / 612 | **drop** |
| `torch` | initiative taken | The initiative flame flares at the left shoulder in a `D4` glow, then a bigger flame sits in a `D1` halo, embers rise and drift. | 300 350 250 | 529 / 754 / 1171 / 12 | **revise** |
| `dusk` | it becomes night (all seats) | The sun sinks at the total's right shoulder. Then a `D1` dome of night sky with the moon and stars rises behind the total. Then the moon and one star. | 300 400 250 | 567 / 1542 / 1169 / 440 | **promote** |
| `dawn` | it becomes day (all seats) | The moon sets at the left shoulder. Then the sun rises top right in a `D4` corona of eight rays. Then the sun alone. | 300 400 250 | 924 / 1379 / 547 / 252 | **promote** |
| `bunting` | city's blessing gained | The castle (`icons/citys-blessing.txt`) pops up, then bunting strings across the top of the total (ink and `CHECKER` pennants), then confetti. | 300 400 250 | 420 / 983 / 691 / 36 | **promote** |
| `ring-tempt` | the ring tempts you | The ring drops in with a glint. Then the total fades almost invisible (`D12` paper ghost) with the ring on it in a dotted aura. Then a glint where the ring was. | 300 450 250 | 362 / 2035 / 1626 / 57 | **promote** |

- **crown-drop:** a clear, cheap read, and a friendly "you're the monarch".
- **crown-cut:** a sticker that hides the total is not a cut-out. `crown-drop` is better.
- **torch:** the composition and the soft glow work. But the initiative icon at 2× reads as a water
  drop. It needs a flame drawn for the effect, or the icon at 3×.
- **dusk:** the sky dome is the tile-wide dither moment of this round, and it is cheap at D1.
- **dawn:** the pair to `dusk`. They play on every seat at once (see the runtime notes).
- **bunting:** celebration without confetti noise, and it is cheap.
- **ring-tempt:** "put it on and vanish" is the strongest numeral interaction in this group.

### 5. Table moments

| Effect | Trigger | Intent | Holds | Flips | Call |
|---|---|---|---|---|---|
| `your-turn` | the turn passes to this seat | Chevrons `>>>` / `<<<` rush in at the total from both sides, then four corner brackets lock on. | 300 350 | 528 / 948 / 420 | **promote** |
| `spotlight` | turn passes (alt) | A `D4` spotlight cone swings onto the total from the corner, then leaves a lit pool under it and two twinkles. | 350 350 | 1351 / 1126 / 427 | **revise** |
| `start-flag` | first player chosen | A chequered start flag pops up at the right shoulder, then flips and waves with speed lines, then a twinkle. | 300 350 250 | 580 / 935 / 663 / 42 | **promote** |
| `crit` | natural 20 | A 12-point `D4` starburst slams behind the total, then four fat twinkles sit at its corners. | 350 350 250 | 2307 / 2859 / 576 / 16 | **promote** |
| `fumble` | natural 1 | A `D8` rain cloud parks over the total, then rains on it (`x` drops through the digits) with a sweat drop. Then puddles. | 300 400 250 | 580 / 752 / 1384 / 72 | **promote** |
| `coin-spin` | coin flip (before the result) | The coin face, then the coin edge-on with spin arcs, then the face landed with a glint. | 300 300 350 | 648 / 893 / 860 / 679 | **revise** |
| `revive` | a KO'd seat revived | A flatline runs through the total (1 px `x`), kicks into a heartbeat spike (3 px `x`), then a heart pops at its end. | 300 350 300 | 145 / 928 / 1243 / 246 | **promote** |

- **your-turn:** cheap, legible from across the table, and it is about pointing at this seat.
- **spotlight:** pretty, but it costs more than `your-turn` for something that happens every
  turn. Keep it as an alternate style.
- **start-flag:** a classic racing-game start.
- **crit:** a big, rare burst, worth its cost.
- **fumble:** the "sad trombone" counterpart to `crit`. The rain is busy over the digits, but it
  lasts only one beat.
- **coin-spin:** the edge-on beat is good, but the coin icon at 3× reads as a target or an eye.
  Draw a coin face for the effect.
- **revive:** a tile-reach line that is cheap, a cut-out, and an unmistakable "it's alive".

## Summary

- **Promote (21):**
  - damage: `bonk`, `nibble`, `slash`, `stomp`
  - heal: `chime`, `heart-burst`
  - counters: `ooze`, `imp-raspberry`, `charge`, `rad-glow`, `storm-copy`
  - state changes: `crown-drop`, `dusk`, `dawn`, `bunting`, `ring-tempt`
  - table moments: `your-turn`, `start-flag`, `crit`, `fumble`, `revive`
- **Revise (7):** `chomp`, `refill`, `imp-poke`, `level-up`, `torch`, `spotlight`, `coin-spin`
- **Drop (2):** `brand`, `crown-cut`. Both are stickers that hide the total; that is not the
  cut-out language.

Big frames are kept rare. Every beat over 2500 flips is in a rare or once-per-game slot: `stomp`
(huge hits), `rad-glow` (rad), `crit` (natural 20), and `chomp`, which is up for revision for
that reason.

## Needs from the runtime (none of this is built)

1. **Roles and triggers.** `role` here uses proposed values: `poison`, `commander`, `energy`,
   `experience`, `rad`, `storm`, `monarch`, `initiative`, `day-night`, `citys-blessing`, `ring`,
   `turn`, `first-player`, `dice`, `coin`, `revive`. Today the parser accepts only
   `damage|heal|big-hit|ko|win`, and `FxPolicy` plays nothing for counters, toggles or table
   events.
   - `FxPolicy` would also need **size bands**: small 1–3, big 4–9, huge ≥10, against today's
     1–9 / ≥10.
   - The trigger would come from the same committed group (counters) or from the toggle or
     table-tool state change.
2. **Every seat at once** (`dusk` and `dawn`, and possibly `crit`/`fumble`).
   `FxPlayer.maxConcurrent` is 2 outside the gallery. A global event needs a "play on all seats"
   path that lifts the cap the way the gallery does.
3. **Dice and coin results** are shown in the centre overlay, not on a seat.
   - To play `crit`, `fumble` or `coin-spin` on the roller's seat, the runtime needs to know the
     roller.
   - To play them on the overlay's result cell, it needs a new anchor (the overlay box). As
     designed, they are anchored to the seat's total.
4. **Prewarm memory.** Each extra pool member adds full-frame ALPHA_8 masks (`fx/README.md`).
   Thirty more effects would be costly unless masks are cropped to their ink bounds, which is the
   T20 note and the performance pass in flight. These frames are 149×165, smaller than the
   tile-scale ones.
5. **Not needed, but noted:** a "shifted band" op would let `slash` slide the top half of the
   total sideways for one beat, a true split. No frame here relies on it.

## What to test on the device

- The round-one and round-two lists still apply.
- The `D1` sky dome (`dusk`) and the `D2` shadow and blush (`stomp`, `heart-burst`, `level-up`):
  does a 6–12% dither read on the Nomad, or vanish?
- `HLINES` scanlines (`rad-glow`) and the `D12` ghosts (`stomp`, `refill`, `ring-tempt`): check
  for smear and residue.
- `slash`'s 1 px paper split: is it visible from the opposite seat?
- All seats at once for `dusk` and `dawn`: is it one flash or four local updates?
