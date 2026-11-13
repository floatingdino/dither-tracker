# Play screen architecture (T34)

The Play screen is a **host** that owns no feature logic plus an ordered list of **components**, one
feature each, in their own packages. Read this instead of `PlayView`; read SPEC hard rules first.

```
play/PlayView      Android shell: size → PlayLayout, MotionEvent → pointers, onDraw → Stage.draw,
                   Handler timers, invalidate. ~200 lines, no features.
play/PlayScreen    THE component list (the only file a new feature edits outside its package).
stage/             The host core (pure Kotlin, JVM-tested): Stage, Component, Cover, Frame,
                   PlayInput (press tracker), IconPainter, Readouts, Events, Origin, Layer, Kind.
play/              Shared context: PlayLayout (tile geometry), TileSlots (where things are now),
                   Diamond (centre shape), GameSession, Scheduler/HandlerScheduler.
life/ readout/ drawer/ peeks/ commander/ chrome/      Features (components + their pure classes).
```

## The contract: `stage.Component`

`Component(layer, kind, zonesPerSeat, timerCount)`; override only what you need:

| Hook | Called when | Must |
|---|---|---|
| `onAdded()` | `stage.add(this)` (before seal) | create state needing `stage`; register covers (`stage.addCover`) and services (`stage.readouts = this`) |
| `onLayout(play, tiles)` | first layout and every size/scale change (all touches cancelled) | build your pure layout from `PlayLayout` |
| `prewarm(f)` | after layout, renderer on a scratch canvas | draw every glyph/icon you can show once (`f.text.draw`, `f.icons.prewarm`) |
| `draw(f, seat)` | each frame, only while visible; tile components per seat whose panel is in the clip | draw via `f.r`, `f.text`, `f.icons`; cull with `f.hit(rect)`; no allocation |
| `zones(seat, z)` | every touch-down | `z.add(local, rect)` / `z.block(rect)` from **current** state, bottom first |
| `contains(seat, local, gx, gy)` | hit inside a declared rect | optional shape test (the diamond) |
| `feedback(seat, local, out)` | press, release, every frame while pressed | the rect a press inverts, from current state |
| `repeats(seat, local)` | press and each hold tick | whether holding repeats |
| `onTap` / `onRepeat` | release before repeating / each hold step (500 ms, then 1000 ms) | apply one step |
| `onStateChanged(old, new, origin)` | every game change | invalidate exactly what changed (diff `Player`s by reference) |
| `onPlayersChanged()` | player count changed | rebuild per-seat state (all redrawn) |
| `onSettingsChanged(old, new)` | settings change | |
| `onCoversChanged()` | any cover on/off/changed (touches already cancelled) | drop what is now hidden |
| `onEvent(event, seat)` | `stage.send` | react to `Events` constants |
| `onTimer(id)` | your `timers.schedule(id, ms)` fired | |
| `onAttach` / `onDetach` | view attach / detach (timers cancelled after) | commit or close open things |
| `bounds(out)` | screen components, on cover changes | the rect to redraw |

Services a component may use: `stage.session` (read `state`), `stage.settings`,
`stage.changeLife(seat, d)`, `stage.dispatch(origin, action, key)`, `stage.invalidate(rect)`,
`stage.cancelTouches(this, seat, from, until)`, `stage.isPressed`, `visible(seat)`, `stage.send`,
`stage.setCover`, `stage.readouts`, `stage.holdStep`, `timers`.

**Rule:** a component never references another feature's component class. Talk through the game
state, `TileSlots`, `Readouts`, `Events` and `Cover`s. Two components of *one* feature may share that
feature's state (`DrawerTab` → `DrawerComponent`, `CommanderClose` → `CommanderTile`).

## Layers, draw order and hit order

`stage.Layer`, bottom to top: `BACKDROP` (dividers) → `TILE` (per seat) → `TILE_CHROME` (per seat) →
`SCREEN` → `OVERLAY`. Inside a layer, order = `PlayScreen` registration order. Tile layers draw seat
by seat (all of seat 0's components, then seat 1's…), each in the player's frame via
`PlayLayout.mapRect`/rotation (never `Canvas.rotate`).

```
r.clear()
for layer in BACKDROP..OVERLAY:
    draw the layer's visible components
    invert the pressed zones OWNED by this layer's components     ← press feedback (SPEC rule 6)
```

So a press inverts everything drawn up to its own layer, and nothing above (a held life half is
inverted under the closed tab, which is `TILE_CHROME`). On API < 29 the host redraws layers up to the
owner's inverted inside the rect instead.

Hit-testing is the reverse: at each touch-down the stage collects every visible component's zones
(layer order, then registration order, then declaration order) and the **last** containing rect wins.
A `block` rect returns "nothing" (no press, nothing below). Current `TILE` order:
`LifeComponent` (halves) < `CommanderTile` < `ReadoutComponent` < `PeeksComponent` <
`DrawerComponent` (block, cells, bar) — then `DrawerTab`, then `MenuDiamond` (block) / `CommanderClose`.

## Zone ids

Components use **local** ids `0 until zonesPerSeat` (per seat for tile components). `Stage.seal()`
allocates one global block per component in registration order: `base + seat × zonesPerSeat + local`
(screen components: `base + local`). Nobody hand-partitions an id space; `PlayInput` (multi-pointer,
release-to-fire, hold-repeat, cancel) and the hold timers are keyed by those global ids. Component
timers get ids after the zones. `stage.zoneId/zoneOwner/zoneSeat/zoneLocal` translate for tests.

## Invalidation and frames

`stage.invalidate(rect)` (dither px) is unioned and flushed **once per entry point** (touch event,
timer, state change, settings change, layout, cover change) as one device-px `View.invalidate`, so
every action is one frame. Invalidate exactly what changed: the number slot on a life change, the
readout slot, one drawer cell, the under row.

## Shared tile context: `play.TileSlots`

Where a seat's number, ±, readout and life-half feedback are **right now**. A seat is *full*
(`PlayLayout` slots) or *compact*: the single `TileSlots.lower` occupant (the drawer) occupies the
lower tile and publishes the compact geometry once per layout (`publishCompact`). Read
`tiles.numberSlot(seat)`, `numberFootprint`, `readoutSlot`, `readoutFootprint`, `minusGlyph`,
`plusGlyph`, `pmK`, `lifeFeedback`, `underRow`, `compact(seat)`.

## Origins and the readout

`stage.changeLife(seat, …)` and `stage.dispatch(seat, …)` mark the change's **origin** seat; the
readout adds that seat's life delta. `Origin.ATTRIBUTED` = "I feed the readouts myself" (commander
damage: `readouts.add(source, dmg)`, `readouts.add(target, life)`, `setGroup(seat, coalesceKey)`).
Anything else (`session.dispatch` directly: undo, restore, menus) is `Origin.EXTERNAL` and commits every
open readout — the "another kind of action" rule.

## Mode / visibility: `stage.Cover`

A cover hides components by **kind** (`stage.Kind`: `BACKDROP`, `CONTENT`, `READOUT`, `CHROME`, `MODE`,
`EFFECT`, `OVERLAY`) on a seat mask and/or the screen layers. Hidden = not drawn, not hit-tested.
`stage.setCover(cover, seats, screen)` cancels every touch, calls `onCoversChanged` everywhere (the
readout commits, the drawer closes hidden seats) and redraws the affected panels + screen components.

Commander mode = `Cover(Kind.CONTENT or Kind.CHROME)` on all seats and the screen while a target is
set: life, peeks, drawers, tabs and the menu diamond disappear; its own `Kind.MODE` components and the
readout stay. A future overlay takes the whole screen with `Cover(Kind.ALL_BELOW_OVERLAY)`, or a region
with just some seats.

## Threading and allocation

Main thread only. No allocation in `draw`, `zones`, `feedback`, `onTap`/`onRepeat`, `onTimer`,
`onStateChanged` (beyond core actions): preallocate `IRect`s and arrays in the component, iterate
arrays with index loops / `for (x in array)`, never `List` iterators, lambdas or string building
(log only behind `stage.log?.invoke(...)`). Prewarm every glyph and icon in `prewarm`. Timers only via
`timers` (never your own `Handler`); they are cancelled on detach.

## How to add a tile feature (example: KO overlay, T15)

1. `app/src/main/java/.../ko/KoComponent.kt`:
   `class KoComponent : Component(Layer.TILE, Kind.CONTENT, zonesPerSeat = 1)`.
2. `onLayout`: keep `play` (panels, rotation) and `tiles`.
3. `draw(f, seat)`: if the player is KO (`stage.session.state.isKo(id)`), `f.r.fill(Role.DEAD, play.panels[seat])`
   and your text; return otherwise.
4. `zones(seat, z)`: when KO, `z.add(0, play.panels[seat])` (registered after life, so it wins the
   whole tile). `feedback` = the panel. `onTap` → `stage.dispatch(seat, Action.Revive(id))`.
5. `onStateChanged`: invalidate `play.panels[i]` for seats whose KO status flipped.
6. One line in `PlayScreen` at the KO slot. Tests: build `PlayRig` (test/play) and assert
   `rig.hit(x, y)`, draw order with a recording `Frame`, invalidations on `rig.platform.invalidated`.

Effects (T28c, `fx/FxComponent`) are the same shape: `Kind.EFFECT`, no zones, cancel on
`Events.TOUCH_DOWN` for the seat, trigger on `Events.READOUT_COMMITTED` (the readout's commit),
timers via `timers`, draw after the number (registered right after the drawer, before the KO tile),
and hold the KO tile off with `Events.KO_HOLD` / `KO_RELEASE` while a KO flourish plays. See
`fx/README.md`.

## How to add a screen overlay (example: table menu, T09)

1. `menu/MenuOverlay.kt`: `Component(Layer.OVERLAY, Kind.OVERLAY, zonesPerSeat = N)`; in `onAdded`
   `cover = stage.addCover(Cover(Kind.ALL_BELOW_OVERLAY))`.
2. Open: `stage.setCover(cover, Cover.ALL_SEATS, screen = true)` — tiles and chrome vanish and lose
   input, touches are cancelled, readouts commit, drawers close. Close: `setCover(cover, 0, false)`.
3. `zones(SCREEN, z)` declares your buttons (widgets' rects are fine: `widgets/` layout gives local
   rects); `feedback` returns the button's visible rect; `onTap` acts: table-wide actions go straight to the
   session (`stage.session.undo()`, `stage.session.dispatch(...)`), which is `Origin.EXTERNAL`.
4. `draw(f, SCREEN)` only while open. `bounds` = your panel.
5. The diamond: replace `MenuDiamond`'s block with a zone (or a new `MenuButton` component) whose
   `onTap` opens the overlay. Register the overlay in `PlayScreen`'s OVERLAY slot.
6. A region-only overlay (a per-seat confirm) covers `1 shl seat` and draws in that seat's panel.

## Verification harness

`tools/capture_scenarios.sh <outdir>` (device) captures 19 scripted states; compare two builds with
`cmp`, check each with `tools/check_1bit.py f --scale 4`. T34's after-set is in `design/captures/t34/`.

### Pixel-purity suite (T22)

Two halves; run both before calling a screen change done.

**JVM** — `./gradlew :app:testDebugUnitTest --tests 'au.com.josephmark.dithertracker.purity.*'`
(~2 s). `DitherRenderer` is an abstract base: the app draws with `CanvasDitherRenderer`, the tests with
`purity/RasterRenderer` (one byte per dither px, API 29+ semantics). `purity/ScreenRig` runs the real
`PlayScreen` component list on a fake clock; `PuritySuiteTest` scripts play (readouts, presses, 3–4
digit totals), drawers (zero and non-zero cells, page 2, presses), peeks, commander mode (partner
quarters, presses), KO and winner, the gem (every facet held, NEW confirm, CLEAN), settings (pages,
rotations), history, dice/coin, first player and every effect keyframe, at S = 2..6 on 1872×1404 and
1404×1872, each shot in both colour modes. Per shot it fails on: a draw outside the dither grid
(would land in the margin; enforced on `comfortable` layouts — the others are never offered),
an unbalanced `clipTo`, any 8×8 area that is a dither tile off the global phase (rule 3), a dark-mode
render that is not the exact complement of the normal one, and an effect frame that changes pixels
outside the seat panels. Failing shots are written to `app/build/purity/<screen>-S<s>/`;
`PURITY_DUMP=<dir>` (with `--no-daemon`) writes every shot. `PurityChecksTest` plants each violation.
Purity and the S-grid hold by construction here (two values, integer dither coordinates); what the
JVM cannot see is Skia: blending, filtering, the in-place DIFFERENCE invert, shader phase.
`KNOWN_OFF_PHASE` lists effects allowed to skip the phase scan; it is empty (T48 moved `fizzle`'s
dissolve to tile layers).

**Device** — `ANDROID_SERIAL=emulator-<port> tools/check_purity.sh <outdir>` (~6 min on the
emulator): installs the debug APK, runs `capture_scenarios.sh` (T34's 19 states, then the gem, NEW
confirm, dice/coin, first player, settings, history, KO and winner, dark mode, and every effect
keyframe via `--es fx <name> --ef fxHold 10`), checks every PNG with `check_1bit.py --scale 4`, prints a
table and exits non-zero on any failure. `NO_CAPTURE=1` re-checks an existing folder; `BASE_ONLY=1`,
`NO_FX=1` shorten the run. Only S = 4 landscape is captured (the script's coordinates are for it);
other scales and portrait are the JVM half's job. Effect captures are timed, so a name can be a frame
off; every capture is checked either way.
