# widgets — shared 1-bit controls (T35)

Buttons, rows, prompts, panels and paging for every overlay (centre menu, settings, history, tools,
name entry). Pure 1-bit through `DitherRenderer`/`TextRenderer`; laid out in dither px; rotatable to
face any seat; no scrolling; no allocations once laid out.

## Shape of the kit

| Piece | What it is |
|---|---|
| `WidgetMetrics(S, dpi = 300)` | Every size per scale. Design values at S = 4, converted to keep their physical size. `target` = 9 mm fingertip minimum. |
| `Widget` | Base: pure model + layout in a **local** frame. `naturalWidth/Height` → `layout(m, x, y, w, h)` → `collectHits(region, frame)`. |
| `WidgetFrame` | Local box → screen footprint at rotation 0/90/180/270 (`TextLayout` maths). `setCentred(container, rot, w, h)` places an overlay facing a seat. |
| `WidgetPainter(canvas)` | Draws any widget through its frame (`draw(frame, widget)`), plus press feedback (`drawPressed`). |
| `WidgetCanvas` / `RendererCanvas` | The drawing seam. App: `RendererCanvas(renderer, textRenderer)`, `begin(canvas)` per frame. Tests: a JVM raster. |
| `HitRegion` | id → touch rect + feedback rect (screen px). `hitTest(gx, gy)`; latest-added wins (overlays above screens). |
| `PressTracker` | Release-to-fire press model, multi-pointer, slide-off cancels, optional hold-repeat via a host `WidgetTimer`. |
| `TextFit`, `TextWrap` | Clean truncation (no ellipsis) and greedy word wrap for monospace faces. |

### Widgets

| Widget | Ids (parts) | Notes |
|---|---|---|
| `Button(id, label, icon?, style)` | `id` | `OUTLINE` (Default), `FILLED` (Dark), `PLAIN` (on `backdrop`). Disabled = `Role.DISABLED` hatch, no hit. |
| `IconButton(id, icon, style)` | `id` | Fingertip square. |
| `ButtonRow(buttons…, align)` | children's | `START`/`CENTRE`/`END` at natural widths, or `ButtonRow.FILL`. Touch = full row height. |
| `ListRow(id, label, value?, icon?, chevron)` | `id` | Whole row is the button. |
| `Toggle(id, label, checked)` | `id` | On = solid ink box, check knocked out; off = empty outlined box. `toggled()`. |
| `Stepper(id, label, value, min, max, step, holdStep, repeats)` | `id + MINUS`, `id + PLUS` | `num-m` value; `apply(part, repeat)`; a limit hatches its button. |
| `Segmented(id, label, options, selected)` | `id + i` | 2–5 options; selected = inverted. `select(i)`. |
| `ConfirmPrompt(id, prompt, confirm, cancel)` | `id + CONFIRM`, `id + CANCEL` | ≤ 2 wrapped lines, sized to content, centred in its rect. |
| `Panel(rows, title?, closeId, columnBreak)` | close + rows' | Frame, TAB title bar with close, dotted dividers, optional 2 columns. `fits`. |
| `PagedList(id, count, pool?, preferredRows)` | `id + PREV`, `id + NEXT` | Pages of rows + `‹ 2/5 ›` footer; `binder` fills a pool of row widgets. |

Ids are **local** small ints chosen by the screen (≥ 0, unique, `Widget.NO_ID` = none); a widget with
parts owns `id until id + idCount`, and `owns(id)` / `partOf(id)` decode a hit.

## Rules every widget follows

- Hit rects ≥ `target` (27 px at S = 4) in the short dimension; rows are `rowH` ≥ `target`.
- Press feedback = inversion of the widget's feedback rect (`drawPressed`); where in-place inversion
  is unavailable (API < 29) the owner is redrawn inverted inside a clip — tests prove the two match.
- Action on release: `PressTracker` fires `onTap` on up, never on down; cancel / slide-off fire nothing.
- Glyphs and icons on dither fills are opaque with a 1-stroke paper rim.
- Text that does not fit is cut at whole glyphs (at a word if that keeps ⅔ of the room), never ends
  on a separator, never gets an ellipsis. An icon button keeps at least 3 glyphs or shows the icon alone.
- Model changes → re-layout (top-level, e.g. the panel) → `region.clear()` + `collectHits`. All
  allocation-free (a test measures layout, draw, hit-test and press paths).

## Sizes at S = 4 (Nomad, 300 dpi)

k 1, stroke 1, target 27, rowH 27, icon 16, padX 6, gap 4, inset 2, padY 4. A button is 27 tall and
`label + 14` wide (min 27); in a `ButtonRow` it is drawn 23 tall inside a 27-tall touch column. A
toggle box is 18. Stepper buttons are 23 squares in 27×27 columns either side of a `num-m` value slot.
Panel: 1-px outline, 27-px title bar, rows 27 + 1-px dotted divider. The gallery panel on the Nomad
landscape grid (468×351) is 460×232.

## Example: a settings-like panel

```kotlin
val m = WidgetMetrics(grid.scale)
val life = Stepper(0, "Starting life", 40, 1, 999)                       // ids 0, 1
val ko = Toggle(2, "Auto KO", checked = settings.autoKo)
val players = Segmented(3, "Players", arrayOf("2", "3", "4", "5", "6"), 2) // ids 3..7
val panel = Panel(arrayOf(life, ko, players, ListRow(8, "History", chevron = true)), "Settings", closeId = 9)
val w = 240; val h = panel.naturalHeight(m, w)
panel.layout(m, 0, 0, w, h); frame.setCentred(screen, seatRotation, w, h)
region.clear(); panel.collectHits(region, frame); tracker.targets = region
// onDraw: canvas.begin(c); painter.draw(frame, panel); painter.drawPressed(region, tracker)
// onTap(id): if (ko.owns(id)) ko.checked = ko.toggled() else if (life.owns(id)) life.apply(life.partOf(id), false) …; re-layout + re-collect
```

`WidgetGallery` is the full worked example (every widget, standard tap/repeat behaviours); draw it on
a device with `drawWidgetGallery(canvas, renderer, textRenderer, grid, rotation)`.

## Adopting it in a component host (T34)

- One `HitRegion` per screen layer; collect overlays **after** the screen beneath so they win.
  Offset local widget ids into the host's zone space (`base + id`) or keep a per-component region
  and route by component first.
- Feed `MotionEvent`s to a `PressTracker` in dither px (`down/move/up/cancel`, pointer ids);
  `Sink.onPressed/onReleased` → invalidate the feedback rect, `onTap/onRepeat` → the component.
- Hold-repeat: adapt the app scheduler in one line — `object : WidgetTimer { schedule(id, d) =
  scheduler.schedule(base + id, d); cancel(id) = scheduler.cancel(base + id) }` and route the
  scheduler's `onTimer(base + id)` to `tracker.onTimer(id)`. Defaults: 500 ms then 1000 ms (play's).
- Replacing `tracker.targets` cancels in-flight presses (do it when a layer opens/closes).
