# seat — the seat glyph (T44)

Players are never named in the UI (Sam, 2026-11-14: "Player 1/2/3/4 are meaningless"; names dropped).
A player is shown as a **seat glyph**: a tiny picture of the table — the screen's panel arrangement,
outlined, with that player's panel filled solid.

```
seat 0 (top-left), 16-px art box, as it lies on the screen:

###############
########......#
########......#
########......#
########......#
###############
#......#......#
#......#......#
#......#......#
#......#......#
###############
```

## API

| Piece | What it is |
|---|---|
| `SeatGlyphs.forPanels(play.panels)` | One per layout. Copies the panel rects; `sameLayout(panels)` says whether a new layout needs a new one. |
| `glyph(seat, rotation, size = 16, k = 1)` | A cached `MonoImage` for layout seat `seat` (or `SeatGlyphs.EMPTY`: outline only), for a frame turned `rotation`, fitting a `size`×`size` art box, enlarged `k`× (the widget glyph scale, like icons). Array lookup, no allocation once its (size, k) is built. |
| `prewarm(size, k)` | Builds all glyphs for that (size, k). Call it (and blit each glyph once) from `Component.prewarm`. |
| `SeatGlyphs.seatOf(state, playerId)` | The player's current layout seat, or `EMPTY` if they are no longer seated. |
| `SeatMap.compute(panels, size)` | The lattice itself (screen orientation), for anything that wants to draw its own variant. |

## The one rule: blit it with the rotation you draw everything else with

The glyph is a picture of the *screen*, so it must lie on the glass the way the table does, whatever
way the overlay around it faces. `glyph(seat, rotation)` is stored **counter-rotated**, so

```kotlin
val g = seats.glyph(seat, frame.rotation, SeatGlyphs.ICON, m.k)
frame.map(localRectOf(g), tmp)                          // g.width × g.height in the reader's frame
canvas.blit(g, tmp.left, tmp.top, frame.rotation, invert = false, opaque = false)
```

ends up in screen orientation, and a reader on any side sees their own seat nearest them. Note that
`g.width`/`g.height` are the *reader's-frame* size: 15×11 for 0/180, 11×15 for a sideways reader
(they see the landscape tablet as portrait). Centre it in a 16×16 slot. Tile content (KO tile, commander
mode, first-player pick) uses the seat's own `PlayLayout.rotation[seat]` the same way.

## How the miniature is derived (and why it generalises)

Per axis, panel edges are clustered into boundaries (the 1-px dividers between panels vanish into one
boundary). The long axis takes the largest length ≤ size at which panels of equal real extent get equal
cells; the short axis the length nearest the screen's aspect with the same property. Cells share their
1-px lines. 2×2 today: 15×11 at 16 px (cells 6×4 inside), 23×17 at 24 px. A 3-, 5- or 6-player layout
(T11) needs no change: pass its panels. Equal cells beat filling the box — a 3-over-2 split is 13 wide
at 16 px.

Fill: solid, inset 1 px from the cell lines (`INSET = 1`, the designer's pick over a flush fill; at
16 px that is a 4×2 block inside its 6×4 cell). There is
deliberately no dither fill for a second seat: a pattern baked into a mask can't keep the global pattern
phase (SPEC rule 3) and at 6×4 px it reads as noise — show two glyphs (`source → target`) instead.

## Where to adopt it

- `history/` rows (done, T44): kind icon · seat glyph (or `source → target`) · note · `delta → value`.
- `tools/` first-player pick: if it ever names or points at the picked player off their own tile, use
  `glyph(seat, rotation)` of the reader's frame.
- `ko/` winner / KO tile: names are not shown there today; if a "who did it" line is ever added, use the
  source's glyph at the tile's rotation.
- Anything new that would say "Player N": use the glyph, never the name.
