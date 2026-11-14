# Layouts for 2–6 players (T11)

The Nomad is landscape-locked: 1872×1404 device px, S = 4 → a **468×351** dither grid. All sizes
below are dither px at S = 4. The engine is `app/.../layout/` (`Arrangement`, `Seating`) feeding
`play/PlayLayout`; the table of arrangements is `Arrangements.ALL` and the pick per count is one map
entry, so an alternate is a one-line change. Since T53 the player chooses one per count in
**Settings → Players ›** (the layout picker, below); the choices live in `AppSettings.layout` as a spec of
one id per count that is not on its default (`"3-pyramid,5-end"`).

## Rules every arrangement follows

- **One tile per game.** Every seat gets the same tile (player-frame width × height), so every
  player sees the same numerals, drawer and tray, and every component lays a tile out once. A seat
  at a short end gets the tile turned sideways. Leftover px are paper margin (SPEC: fidelity beats
  filling the view).
- Seats face the edge they sit at: far long edge 180°, near long edge 0°, left end 90°, right end
  270°. Seat order is clockwise from the far left (4 players: 0 TL, 1 TR, 2 BR, 3 BL, as before).
- 1-px dotted dividers between tiles; the centre control (the diamond, which grows into the gem)
  sits on the long divider: where tiles meet at their corners if possible, else its middle.
- **Tiers.** *Full*: `−` and `+` beside the number (`−999` at k = 1 needs a tile ≥ 212 wide, ≥ 166
  tall with the under-number row). *Narrow*: tile ≥ 148 wide — the number alone with 4 px each side;
  ± glyphs are not drawn (the halves still work), and features that need side room degrade on
  their own (see "5 and 6 players" below). Anything else is not offered at that S.
- Portrait (unused while locked) is the same table **turned 90° clockwise**: the players have not
  moved, the tablet has. 1404×1872 at S = 4 is a 351×468 grid, so every tile size is identical.

## 2 players — pick: `2-across`

```
A  2-across  (picked)                    B  2-ends
┌────────────────────────────────┐       ┌───────────────┬────────────────┐
│          [ 2  P0  180 ]        │       │      P1       │      P0        │
│            468×175             │       │  ┐  90°       │       270°  ┌  │
├───────────────◆────────────────┤       │  │ 351×233    ◆   351×233   │  │
│          [ P1  0 ]             │       │  ┘ (turned)   │   (turned)  └  │
│            468×175             │       │               │                │
└────────────────────────────────┘       └───────────────┴────────────────┘
```

| | tile | tier | notes |
|---|---|---|---|
| **A `2-across`** | 468×175 | full | players across the long edges, like a 1v1 across a table |
| B `2-ends` | 351×233 | full | players at the short ends; every glyph sideways on the glass |

**Pick A:** a 1v1 sits across the table from each other; it keeps the long-edge seating the 2×2
established (no sideways text) and the diamond sits mid-divider, shared equally.

## 3 players — pick: `3-tee`

```
A  3-tee  (picked)                       B  3-pyramid
┌────────────────────┬───────────┐       ┌────────┬──────────────┬────────┐
│  P0  180   292×175 │           │       │ margin │ P0 180       │ margin │
│                    │  P1  270  │       │        │ 233×175      │        │
├────────────────────◆  175×292  │       ├────────┴──────◆───────┴────────┤
│  P2  0     292×175 │  (turned) │       │ P2  0  233×175 │ P1  0 233×175 │
│                    │           │       │                │               │
└────────────────────┴───────────┘       └────────────────┴───────────────┘
```

| | tile | tier | notes |
|---|---|---|---|
| **A `3-tee`** | 292×175 | full | one player per side: far, near, right end; the end tile has 29 px of its own margin above and below |
| B `3-pyramid` | 233×175 | full | two side by side on the near edge, one alone opposite; the 2×2's tile |

**Pick A:** a three-player pod spreads round the table rather than two sitting shoulder to
shoulder; the T gives everyone their own side, a bigger tile than the 2×2, and the diamond sits at
the T where all three tiles meet. (It is also the first layout with a sideways seat.)

## 4 players — pick: `4-grid` (unchanged)

```
A  4-grid  (picked, = T06)               B  4-dinner (not comfortable at S = 4)
┌───────────────┬────────────────┐       ┌──────┬─────────────────┬──────┐
│ P0 180        │ P1 180         │       │      │ P0 180  232×117 │      │
│ 233×175       │ 233×175        │       │ P3   ├────────◆────────┤ P1   │
├───────────────◆────────────────┤       │ 90°  │ P2 0    232×117 │ 270° │
│ P3 0          │ P2 0           │       │      │                 │      │
└───────────────┴────────────────┘       └──────┴─────────────────┴──────┘
```

| | tile | tier | notes |
|---|---|---|---|
| **A `4-grid`** | 233×175 | full | byte-identical to the shipped 2×2 |
| B `4-dinner` | 232×117 | — | 117 tall cannot hold the number over the tab band; comfortable only at S ≤ 3 |

## 5 players — pick: `5-three-two`

```
A  5-three-two  (picked)                 B  5-end
┌──────────┬──────────┬──────────┐       ┌──────────┬──────────┬─────────┐
│ P0 180   │ P1 180   │ P2 180   │       │ P0 180   │ P1 180   │         │
│ 155×175  │ 155×175  │ 155×175  │       │ 148×170  │ 148×170  │ P2 270  │
├─────┬────┴─────◆────┴────┬─────┤       ├──────────◆──────────┤ 170×148 │
│marg.│ P4 0     │ P3 0    │marg.│       │ P4 0     │ P3 0     │         │
│ 78  │ 155×175  │ 155×175 │ 79  │       │ 148×170  │ 148×170  │         │
└─────┴──────────┴─────────┴─────┘       └──────────┴──────────┴─────────┘
```

| | tile | tier | notes |
|---|---|---|---|
| **A `5-three-two`** | 155×175 | narrow | three far, two near (centred, paper either side) |
| B `5-end` | 148×170 | narrow | two-and-two plus one at the right end; smaller tile |

**Pick A:** the bigger tile, no sideways seat, and the diamond lands where the two near tiles meet,
under the middle far seat — it touches three of the five tiles.

## 6 players — pick: `6-three-three`

```
A  6-three-three  (picked)               B  6-dinner (not comfortable at S ≤ 3)
┌──────────┬──────────┬──────────┐       ┌──────┬────────┬────────┬──────┐
│ P0 180   │ P1 180   │ P2 180   │       │      │ P0 180 │ P1 180 │      │
│ 155×175  │ 155×175  │ 155×175  │       │ P5   │116×116 │116×116 │ P2   │
├──────────┼─────◆────┼──────────┤       │ 90°  ├────────◆────────┤ 270° │
│ P5 0     │ P4 0     │ P3 0     │       │      │ P4 0   │ P3 0   │      │
│ 155×175  │ 155×175  │ 155×175  │       │      │        │        │      │
└──────────┴──────────┴──────────┘       └──────┴────────┴────────┴──────┘
```

| | tile | tier | notes |
|---|---|---|---|
| **A `6-three-three`** | 155×175 | narrow | three a side, the classic long-table pod |
| B `6-dinner` | 116×116 | — | two a side plus the ends: too small for the hero numerals at any S ≥ 3 |

**Pick A:** the only arrangement whose tile holds the hero numerals at S = 4. The diamond sits in
the middle of the long divider (between the two middle seats) rather than at an off-centre crossing,
so it favours nobody.

## T53 — the layout picker and the exploration

### The picker

`Players [2][3][4][5][6] ›`: the chevron opens **LAYOUT: N PLAYERS** for the count chosen on that row.
Under a back row, a 2 × 2 grid of thumbnails (`settings/LayoutPicker.kt`), one per arrangement for the
count, default first. Each thumbnail is a pixel miniature of the real play layout on this screen at
this S — the `SeatMap` lattice of its panels plus the screen's own rect, so equal tiles get equal cells
and the paper margins show — with a solid **notch on each tile's near edge** (where the drawer tab is:
where that player sits, so the miniature says which way everyone faces) and a small ink diamond where
the menu diamond is. Like the seat glyphs it lies in **screen orientation**, whichever side the card
faces (the map matches the tablet as the reader sees it). Selected = an ink frame round the miniature
and its label as an ink chip (our "the black one is the current choice"); press = inversion; a tap
chooses. An arrangement not comfortable at the table's S is hatched (`DISABLED`), untappable, and its
label names the scale it would need (`Dinner (scale 3)`).

**When a choice applies.** For the count the table is playing: **at once**. A layout change is
non-destructive — the same players keep the same seats in the same clockwise order (seat i stays seat
i; only where the tiles are changes), every life, counter and KO is kept, and the screen re-lays out
like a scale change does. It is also how you fix a table that is sitting the wrong way mid-game,
without a NEW. For another count (the Players row set to the next game's) it is stored and waits for
NEW like the count itself (the existing "Next game: 5 players" hint covers it). Rejected: "only on
NEW, or at once if the game is fresh" — it would make the common case (we sat down, the tablet is
laid out wrong) cost a reset, to protect against a change that loses nothing.

### Per count: every arrangement (S = 4, Nomad landscape)

Shorthand: far band (180°) over near band (0°), `L`/`R` end seats (90° / 270°). Tiles are the player
frame (w × h); "picker" = offered at S = 4. Sizes at every S are in
`ArrangementSurveyTest.writeSurvey` (`app/build/purity/t53/arrangement-survey.txt`).

**2 players**

```
2-across (default)       2-ends                    2-side (new)              2-corner (new)
┌────────────────┐       ┌───────┬────────┐        ┌────────┬────────┐       ┌─────── margin ──────┐
│    P0 180      │       │       │        │        │        ◆        │       ├─────────┬───────────┤
├───────◆────────┤       │ P1 90 ◆ P0 270 │        │ P1 0   │ P0 0   │       │ P1 0    ◆  P0 270   │
│    P1 0        │       │       │        │        │        │        │       │ 233×234 │ 234×233   │
└────────────────┘       └───────┴────────┘        └────────┴────────┘       ├─────────┴───────────┤
  468×175 full             351×233 full              233×351 full             └─────── margin ──────┘ 233×234 full
```

| | tile | tier | picker | notes |
|---|---|---|---|---|
| **2-across** | 468×175 | full | ✓ default | face to face across the table |
| 2-ends | 351×233 | full | ✓ | at the short ends; every glyph sideways |
| 2-side *(new)* | 233×351 | full | ✓ | side by side on one edge (a table against a wall, two sharing a side); tall tiles, the number centred with room above. Diamond down the divider, 90 px from the far edge so the gem (outer radius 90) opens on the grid |
| 2-corner *(new)* | 233×234 | full | ✓ | round a table corner: near edge + right end; 58 px of paper above and below |

**3 players**

```
3-tee (default)          3-pyramid                 3-side (new)              3-end (new)
┌──────────┬─────┐       ┌───┬────────┬───┐        ┌─────┬─────┬─────┐       ┌───── margin ─────┐
│ P0 180   │     │       │   │ P0 180 │   │        │     ◆     │     │       ├────◆────┬────────┤
├──────────◆ P1  │       ├───┴───◆────┴───┤        │ P2 0│ P1 0│ P0 0│       │ P2 0│P1 0│ P0 270 │
│ P2 0     │ 270 │       │ P2 0   │ P1 0  │        │     │     │     │       ├─────┴────┴────────┤
└──────────┴─────┘       └────────┴───────┘        └─────┴─────┴─────┘       └───── margin ─────┘
  292×175 full             233×175 full              155×351 narrow            148×170 narrow
```

| | tile | tier | picker | notes |
|---|---|---|---|---|
| **3-tee** | 292×175 | full | ✓ default | one per side |
| 3-pyramid | 233×175 | full | ✓ | two shoulder to shoulder, one opposite |
| 3-side *(new)* | 155×351 | narrow | ✓ | all three on one edge; diamond on the left divider (the two dividers tie for the middle; the lower x wins, as everywhere) |
| 3-end *(new)* | 148×170 | narrow | ✓ | two on one edge + one at the end (the 5-end tile); a 170-px strip, 90 px of paper above and below — honest about what a 4:3 screen can do with this seating |

**4 players**

| | tile | tier | picker | notes |
|---|---|---|---|---|
| **4-grid** | 233×175 | full | ✓ default | 2 + 2 across |
| 4-dinner | 232×117 | — | hatched: *scale 3* | one a side (the natural square-table pod); at S = 3 it is 252×185 full. Shown so it can be chosen after Settings → Scale 3 (with Scale AUTO the table then steps to S = 3 by itself) |
| *rejected* 4-two-one-end | 148×170 | narrow | — | 2 near, 1 far, 1 end: the 5-end tile for one seat fewer, lopsided, nobody sits like it |
| *rejected* 4-three-one | 155×175 | narrow | — | 3 far, 1 near: the 6-player tile for four people |
| *rejected* 4 on one side | 116×351 | — | — | under the narrow 148 |
| *rejected* 4 on a 3 + 3 table, two seats empty (the reference greys unused seats) | 155×175 | narrow | — | smaller tiles than 4-grid for no gain on a tablet everyone reaches |

**5 players**

| | tile | tier | picker | notes |
|---|---|---|---|---|
| **5-three-two** | 155×175 | narrow | ✓ default | 3 far, 2 near centred, 78 px paper each side of the near pair |
| 5-end | 148×170 | narrow | ✓ | 2 + 2 + one at the end |
| *not built* 5-wide-near | far 155×175, near 233×175 | narrow / full | — | the near pair filling the width (the question Sam has not answered). It needs **two tile sizes in one game**: every component (drawer tray, peeks, KO and winner tiles, commander mode, turn tab, effects) lays a tile out once and maps it per seat, so this is a per-seat-frame change across packages other agents own right now. Offer as a follow-up arrangement once answered; the default would stay 5-three-two |
| *rejected* 2 + 1 + both ends | 148 needs fh ≤ 84 | — | — | no tile tall enough |

**6 players**

| | tile | tier | picker | notes |
|---|---|---|---|---|
| **6-three-three** | 155×175 | narrow | ✓ default | three a side |
| 6-dinner | 116×116 | — | hatched: *too small* at 4 | two a side + both ends; narrow only at S = 2 (207×259), where its drawer holds one cell a page |
| *rejected* 3 + 2 + end, 2 + 2 + 2 ends wider | — | — | — | no tile ≥ 148 wide and 166 tall fits |

### Recommended defaults

Unchanged: **2-across, 3-tee, 4-grid, 5-three-two, 6-three-three** — still the largest tile with
no sideways seat (or, at 3, one per side) and the diamond at a crossing. The new arrangements are for
tables the defaults do not fit (a wall table, a corner, a pair and an end); 2-side is the best of
them (full tier, the tallest tile in the app).

### Captures

`design/captures/t53-layout-picker-<n>p.png` (2–6; at 3 the pyramid is chosen),
`t53-play-<id>.png` for each new arrangement (busy: toggles, peeks, a KO, day/night) and
`t53-play-2-side-gem.png` / `t53-play-3-end-gem.png` (the gem open where the diamond moved),
`t53-arrangements.png` (every arrangement's play screen, 1 px per dither px, labelled), and for the
"Counters and trackers" page `t53-counters-trackers-*.png`. All from `tools/capture_t53.sh`, every
capture through `tools/check_1bit.py` at its scale.

### One-sided tables and the diamond

With only a near band there is no long divider. The diamond goes on the vertical divider nearest the
middle, at the band's far edge — or further down it when the band reaches the far edge of the screen,
far enough in that the open gem (`PlayLayout.menuReach`, 90 px at S = 4) stays on the grid. It then
touches the far corners of the two tiles either side (their readout and turn tab are centred, the ±
and number are mid-tile). A layout where it would touch a number or ± (a short tile on a 16:10 screen
at S = 2) is not comfortable and not offered.

## What degrades at 5 and 6 players (narrow tile, 155×175)

| Component | Full tile (2–4) | Narrow tile (5–6) |
|---|---|---|
| Life total | `−` · number · `+` | number only; the two halves are still − / + (no glyphs) |
| Readout, hold ±10 | as before | as before |
| Counter peeks | three chips per near corner (unchanged) | **off** (the corners beside the tab are 11 px wide) — counters only in the drawer |
| Toggle badges (T14) | icons in the under-number row between the peek corners | the under-number row less `EDGE_MIN` each side (139 px: room for all four) |
| Drawer tray | 4 columns × 2 rows = 8 a page (2×2); 6 × 1 at 2 players, 5 × 1 at 3 — the readout drops below a mid-edge diamond, leaving room for one row | **2 columns × 1 row = 2 cells a page** (9 items = 5 pages; the `›` chevron pages as before) |
| Commander mode | partner numbers with ± each | partner ± hidden (two 2-digit numbers and their icons) |
| KO / winner tile | quip at the one size | the same size; long quips wrap to more lines |
| Effects | tile-wide | tile-wide (the narrower tile) |
| Turn tab (T17) | 89 × 21 on the far edge, centred (beside the diamond at 2 players and the 3-player end seat); icon + minutes | 89 × 21 centred on seats clear of the diamond; **45 × 21 beside it** on the middle seats (5: seat 1; 6: seats 1 and 4), the minutes alone when the timer is on |

## Open design questions

1. **Narrow ± affordance.** On 5/6-player tiles the − / + glyphs are hidden; the halves still work.
   Options: ± above/below the number, ± in the near corners (where peeks would be), or a thinner face.
2. **Peeks on narrow tiles.** Off for now. A single-column peek above the tab, or a smaller tab?
   And the narrow drawer holds only 2 cells a page — a smaller number block while open, or a
   one-column tray with taller pages?
3. **Sideways seats and the table menu.** The gem's facets face the long edges (N/S); the 3-player
   end seat reads its labels sideways. Should facets near an end seat face it?
4. **Two-faced overlays** (dice tent, settings and history cards) open facing N or S; at 3 players
   the end seat gets no facing of its own.
5. **Alternates** (answered by T53's picker: every count offers its arrangements).
6. **5 players, near pair filling the width** (two tile sizes in one game) — not built; see T53 above.
7. **Sideways and one-sided seats and the overlays.** The settings and history cards remember the side
   they were turned to (T53), but the gem's facets and the dice tent still face N/S only; on 2-corner
   and 3-end the end player reads them sideways, and on the one-sided layouts nobody sits N.
