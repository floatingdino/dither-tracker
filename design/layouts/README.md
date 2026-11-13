# Layouts for 2–6 players (T11)

The Nomad is landscape-locked: 1872×1404 device px, S = 4 → a **468×351** dither grid. All sizes
below are dither px at S = 4. The engine is `app/.../layout/` (`Arrangement`, `Seating`) feeding
`play/PlayLayout`; the table of arrangements is `Arrangements.ALL` and the pick per count is one map
entry, so an alternate is a one-line change (or `AppSettings.layout = "<id>"` at run time).

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

## What degrades at 5 and 6 players (narrow tile, 155×175)

| Component | Full tile (2–4) | Narrow tile (5–6) |
|---|---|---|
| Life total | `−` · number · `+` | number only; the two halves are still − / + (no glyphs) |
| Readout, hold ±10 | as before | as before |
| Counter peeks | three chips per near corner | **off** (the corners beside the tab are 11 px wide) — counters only in the drawer |
| Drawer tray | 4 columns × 2 rows = 8 cells a page | **2 columns** × 2 rows = 4 cells a page (more pages; the `›` chevron pages as before) |
| Commander mode | partner numbers with ± each | partner ± hidden (two 2-digit numbers and their icons) |
| KO / winner tile | quip at the one size | the same size; long quips wrap to more lines |
| Effects | tile-wide | tile-wide (the narrower tile) |

## Open design questions

1. **Narrow ± affordance.** On 5/6-player tiles the − / + glyphs are hidden; the halves still work.
   Options: ± above/below the number, ± in the near corners (where peeks would be), or a thinner face.
2. **Peeks on narrow tiles.** Off for now. A single-column peek above the tab, or a smaller tab?
3. **Sideways seats and the table menu.** The gem's facets face the long edges (N/S); the 3-player
   end seat reads its labels sideways. Should facets near an end seat face it?
4. **Two-faced overlays** (dice tent, settings and history cards) open facing N or S; at 3 players
   the end seat gets no facing of its own.
5. **Alternates.** Is `3-pyramid` (shoulder to shoulder) ever preferable? Should the player count
   control also offer a layout choice where two exist?
