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

### The picker (T60 redesign)

`Players [2][3][4][5][6] ›`: the chevron opens **LAYOUT: N PLAYERS** for the count chosen on that row.
Under a back row, **wordless thumbnails, four columns of three a page** (`settings/LayoutPicker.kt`),
default first; a count with more than 12 pages with a `‹ n/m ›` footer (none does today: 3 players has
exactly 12). Each thumbnail is the real play layout in miniature, in **screen orientation** (whichever
side the card faces, it lies the way the tablet does). After the owner saw it on the Nomad ("border
for seat, dither for empty"): every seat's whole tile is an **inset ink outline on paper** with paper
gaps between; an **empty cell** is a block of the play screen's own empty ground (`Role.EMPTY`, chunky
dots — the same "not a seat" texture the table shows, rather than the tab dither, which belongs to seats
and tabs); no names, no notches; margins paper. Only the **selected** thumbnail says which way people
face: an ink frame round it and its seats **solid ink** (the strongest contrast with the outlined
others), each with a small paper arrow pointing at the edge its player sits at. A layout **not
available** at the table's scale draws its seats as **dotted** outlines — broken lines read "not
usable", and stay unfilled so they can't be taken for a dithered empty cell; tapping one does not
choose it but writes why on the page's **hint line**: "Needs scale 3 (Settings > Scale)" or "Too small
at any scale". The card's remembered facing applies to this page as to every other.

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

## T60 — tiles of their own sizes, and empty cells

### The tile model

An arrangement is either a **band** arrangement (T11/T53: far band, near band, end seats, every tile the
same size, `Seating`) or a **lattice** (`layout/Lattice.kt`, placed by `LatticeSeating`): the table cut
into equal cells, each owned by a seat or **empty**; a seat is a rectangle of cells facing one edge, so
seats can differ in size (a full-width seat over two halves, a tall end seat beside two). Per seat the
layout gives:

| `PlayLayout` | what |
|---|---|
| `seats[s]` | the seat's **whole tile** (screen) — touch (the life halves cover all of it), the KO field and zone, the stage's per-seat draw, the seat glyph |
| `panels[s]` | the seat's **content frame** (screen): where every component draws |
| `rotation[s]` | 0 / 90 / 180 / 270 as before |
| `panelW × panelH`, `tier`, `kMax` | the content frame and its tier — **one per game** |
| `empties` | the empty cells |

**One content frame per game.** Every component (numerals, ±, readout, drawer tray, peeks, badges,
turn tab, KO tile, commander quarters, effects, first player, partner tab) keeps laying its content out
once, in one player-frame tile: the largest frame **every** seat holds (the smallest seat width × the
smallest seat depth, each in its own player's frame). A bigger seat holds it **against its near edge,
centred along it** (the drawer, turn and partner tabs stay on the edge the player sits at) with paper
round it. Decision: same numeral scale everywhere, more air for the bigger seat — not a bigger integer
k. A bigger k needs 392 px of frame width (k = 2), which only a full-width seat has; one player's life
total twice the size of everyone else's reads as more important, and every component and effect would
need a second layout per game. The comfort tier of an arrangement is the frame's tier.

**Empty cells** show a quiet `Role.EMPTY` ground (chunky 2×2 dots at 25 %, not paper — "not a seat",
and unlike anything a seat shows), drawn with the dividers (backdrop: never covered, never
invalidated by play), with **no zone**: a tap does nothing and redraws nothing. Dividers run between a
seat and anything else (another seat or a hole), never between two holes. The centre diamond keeps its
place at the crossing nearest the middle; it and the open gem **may sit over a hole's corner** (the
crossing of an L is still where three seats meet).

### Lattice arrangements (S = 4, Nomad landscape; frame and tier; all offered)

Text form: one row per lattice row, a letter per cell, `.` empty; facings `n` far, `s` near, `w`/`e` ends.

```
2-diagonal      2-diagonal-flip   3-wide-far       3-wide-near      3-tall-left      3-tall-right
┌──────┬──────┐ ┌──────┬──────┐   ┌─────────────┐  ┌──────┬──────┐  ┌─────┬───────┐  ┌───────┬─────┐
│ P0 n │::::::│ │::::::│ P0 n │   │    P0 n     │  │ P0 n │ P1 n │  │     │ P0 n  │  │ P0 n  │     │
├──────┼──────┤ ├──────┼──────┤   ├──────┬──────┤  ├──────┴──────┤  │P2 w ├───────┤  ├───────┤P1 e │
│::::::│ P1 s │ │ P1 s │::::::│   │ P2 s │ P1 s │  │    P2 s     │  │     │ P1 s  │  │ P2 s  │     │
└──────┴──────┘ └──────┴──────┘   └──────┴──────┘  └─────────────┘  └─────┴───────┘  └───────┴─────┘
 233×175 full    233×175 full      frame 233×175    frame 233×175    frame 233×175    frame 233×175

3-l-<empty corner> (×4: far-left, far-right, near-left, near-right)       4-wide-far        4-wide-near
┌──────┬──────┐ ┌──────┬──────┐ ┌──────┬──────┐ ┌──────┬──────┐       ┌───────────────┐ ┌────┬────┬────┐
│::::::│ P0 n │ │ P0 n │::::::│ │ P0 n │ P1 n │ │ P0 n │ P1 n │       │     P0 n      │ │P0 n│P1 n│P2 n│
├──────┼──────┤ ├──────┼──────┤ ├──────┼──────┤ ├──────┼──────┤       ├────┬────┬─────┤ ├────┴────┴────┤
│ P2 s │ P1 s │ │ P2 s │ P1 s │ │::::::│ P2 s │ │ P2 s │::::::│       │P3 s│P2 s│P1 s │ │     P3 s      │
└──────┴──────┘ └──────┴──────┘ └──────┴──────┘ └──────┴──────┘       └────┴────┴─────┘ └───────────────┘
 233×175 full each                                                     frame 155×175 narrow (both)

5-wide-near                5-wide-far                 5-hole-<empty cell> (×6: far/near × left/mid/right)
┌────┬────┬────┐           ┌───────┬───────┐          ┌────┬────┬────┐
│P0 n│P1 n│P2 n│           │ P0 n  │ P1 n  │          │::::│P0 n│P1 n│   e.g. 5-hole-far-left
├────┴──┬─┴────┤           ├────┬──┴─┬─────┤          ├────┼────┼────┤
│ P4 s  │ P3 s │           │P4 s│P3 s│P2 s │          │P4 s│P3 s│P2 s│
└───────┴──────┘           └────┴────┴─────┘          └────┴────┴────┘
 frame 155×175 narrow (the near pair 233 wide: the T53 question, now built)   155×175 narrow
```

| count | arrangement | seats (tile at S = 4) | frame / tier | notes |
|---|---|---|---|---|
| 2 | 2-diagonal, -flip | 233×175 ×2, two holes | 233×175 full | sitting kitty-corner across a square table |
| 3 | 3-wide-far / -near | 467×175 + 233×175 ×2 | 233×175 full | the reference's "full-width seat over two halves", both ways up |
| 3 | 3-tall-left / -right | end seat 233×351 (turned) + 233×175 ×2 | 233×175 full | "a tall seat beside two" |
| 3 | 3-l-* (×4) | 233×175 ×3, a corner empty | 233×175 full | the owner's L shapes, every corner; generated |
| 4 | 4-wide-far / -near | 467×175 + 155×175 ×3 | 155×175 narrow | "three across + one long" |
| 5 | 5-wide-near | 155×175 ×3 + 233×175 ×2 | 155×175 narrow | the near pair fills the width (deferred in T53) |
| 5 | 5-wide-far | 233×175 ×2 + 155×175 ×3 | 155×175 narrow | its twin |
| 5 | 5-hole-* (×6) | 155×175 ×5, a cell empty | 155×175 narrow | a 2×3 with one seat empty; generated |

**Rejected in T60:** 4 with two tall ends and two in the middle (the end seats' depth 155 < the 166 the
stack needs: `4-dinner` again); 4 on a 2×3 with two holes (smaller tiles than 4-grid for no reason a
tablet needs); 3 as one-sided-plus-hole and 6 on anything but 3 + 3 (no tile ≥ 148 × 166 fits); a
bigger numeral scale for bigger seats (see the tile model).

**Defaults unchanged** (2-across, 3-tee, 4-grid, 5-three-two, 6-three-three): each is still the largest
frame with no hole. Recommendation to consider: **5-wide-near** for 5 players — same frame and tier as
5-three-two, but the near pair get the full width instead of 78 px of paper either side; I have not
changed it (the owner's call).

### Captures

T60: `design/captures/t60-layout-picker-<n>p.png` (2–6, every page; at 3 players an L is chosen, at 5
the wide near pair) and `t60-layout-picker-4p-hint.png` (the hatched dinner tapped), `t60-play-<id>.png`
for each new arrangement (seat 0 KO'd, toggles, peeks, the turn on seat 1, the smallest seat's drawer
open), `t60-play-3-l-near-left-gem.png` (the gem open over the hole's corner) and
`t60-arrangements.png` (every arrangement idle, 1 px per dither px, labelled), from
`tools/capture_t60.sh`. T53: `design/captures/t53-layout-picker-<n>p.png` (2–6; at 3 the pyramid is chosen),
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
| Turn tab (T17, T66) | 89 × 21 on the far edge, centred (beside the diamond at 2 players and the 3-player end seat); ▶\| icon, or for 5 s after a pass the `m:ss` / `h:mm:ss` report in `num-m` | 89 × 21 centred on seats clear of the diamond; **45 × 21 beside it** on the middle seats (5: seat 1; 6: seats 1 and 4), the report there in the `ui` face; at S = 5, 6 at least the icon's 16 px tall |
| Winner-screen times (T66) | egg-timer icon (`hourglass-2`) + `m:ss` chip in the detail band (winner, plain KO) or the near band under a cause chip, `num-m` | the same; the icon dropped, then the `ui` face, where a band is too small (some S = 2, 3 alternates) |

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
