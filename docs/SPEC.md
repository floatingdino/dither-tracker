# Dither Tracker — Spec

A 1-bit life tracker for MTG Commander, built for E-ink. Primary target: **Supernote Nomad**
(7.8", 1404×1872 @ 300ppi, Android 11, sideloaded APK, slow CPU, slow panel refresh).

## Hard rules (every task must obey)

1. **1-bit only.** Every pixel drawn is pure black (#000000) or pure white (#FFFFFF). No greys,
   no alpha blending, no anti-aliasing (`Paint.isAntiAlias = false`, `isFilterBitmap = false`,
   `isDither = false`). Shading is expressed *only* through the predefined dither patterns.
2. **Integer scaling only.** There is one global integer `scale` S (device px per "dither pixel",
   default 4 on the Nomad — the Figma divider is 4px thick with a 4-on/4-off dash, icons are 16×16
   art). All patterns, icons, bitmap glyphs and borders are drawn on the S-grid, nearest-neighbour.
   Layout is computed in dither pixels (`floor(devicePx / S)`); leftover device pixels become
   margin. **Fidelity always beats filling the view.** Never stretch, never fractional-scale.
3. **Pattern phase is global.** Dither tiles are anchored to the screen origin, not to each view,
   so adjacent shaded regions tile seamlessly.
4. **Keyframes, not inbetweens.** (Sam, 2026-11-13: the rule is about making the best use of the
   screen, not about banning animation.) No interpolation, easing, fades, smooth scrolling or
   tickers — anything that needs many frames to read. Limited animation designed around the panel is
   welcome: a few hand-drawn 1-bit keyframes (typically 2–4), each a complete still held long enough
   for the panel to settle (≥ ~250 ms, tuned on device). Uses: hit/heal effects (swipe, crunch,
   fizzle), a KO or winner flourish, dice/coin reveals, a drawer or menu arriving in one or two
   beats. Constraints: effects may open up across a player's tile when it earns it (Sam likes
   cut-outs knocked through the numerals and wants options that use more of the tile), but keep ink
   sparse so few pixels flip and stay inside that player's tile; end on a clean frame with no residue; never delay or block input (a tap cancels a running
   sequence, sequences never queue); plain state changes stay a single redraw. If something looks smeary or trashy on the Nomad it is cut.
5. **Cheap.** No Compose, no heavy deps. Custom `View`s drawing on `Canvas`. No allocations in
   `onDraw`. Cache tile bitmaps/shaders. Cold start should be near-instant; APK should be tiny.
7. **Single window.** No Dialog, PopupWindow, Toast or system IME; overlays are drawn in-app. System
   bars are hidden once and never toggled. Opaque white window background. (See `docs/eink.md`.)
6. **Pressed feedback is an inversion** (black↔white swap of the touched region), nothing else.

## Stack

- Native Android, Kotlin, single Activity, custom Views. `minSdk 26`, target latest installed.
- Gradle modules:
  - `:core` — pure Kotlin/JVM, no Android imports: game state, rules, reducers, serialisation. JUnit tests.
  - `:app` — Android: dither engine, renderer, views, input, persistence, settings.
- Package: `au.com.josephmark.dithertracker` (namespace only; nothing is published).
- Local toolchain: JDK 21, Android SDK at `~/Library/Android/sdk` (platform `android-37.0`,
  build-tools `36.0.0`, emulator + a `google_apis_playstore` image). `gradle`/`adb` not on PATH.

## Design reference

Figma: https://www.figma.com/design/REDACTED/Dither-Tracker (pages: Screens, Icons).
Renders are in `design/figma/` (`play.png`, `panel.png`, `button.png`, 3 icons).
`design/png2txt.py <png> [x0 y0 x1 y1]` prints a PNG region as ASCII for pixel inspection.
The Figma renders are **early and resampled** (patterns are blurry there) — treat them as intent
for layout/feel, not pixel truth. The app must be crisper than the mock.

What the mock establishes:
- Landscape 1872×1404, 4 players in a 2×2 grid; the top row is rotated 180° to face the opposite seat.
- Each panel: huge monospace life total centred, `-` on the player's left, `+` on the right.
- Panels separated by a 1-dither-pixel dotted line (1 on / 1 off).
- Centre: a diamond (square rotated 45°) menu button filled with a mid dither, with a 3-bar glyph.
- Each panel has a tab on its bottom edge (mid dither fill, `...` glyph). Tapping it opens a
  drawer rising from the bottom edge: tab bar (mid dither) over a body (light sparse dither)
  holding the player's extra trackers. First item: a black `COMMANDER DAMAGE` button.
- Button: mono caps label; Default = white on black-outline / Dark = white text on black.

## Dither pattern set

A fixed, named set of tiles (source of truth: one Kotlin file in `:app`, mirrored as ASCII in
`design/patterns/`). Proposed: 4×4 ordered (Bayer) levels `D0`(white)…`D16`(black) plus a few
hand-tuned named tiles: `CHECKER` (50%), `SPARSE` (drawer body), `DOTS`, `HLINES`, `VLINES`,
`DIAG`. UI code refers to semantic roles (`surface`, `tab`, `drawer`, `disabled`, `selected`)
mapped to tiles — never to raw tiles directly.

**As built (T04, `app/.../dither/`):** tiles `D0..D16`, `CHECKER` (== `D8`), `SPARSE`, `DOTS`, `HLINES`,
`VLINES`, `DIAG`, `CROSSHATCH`; roles `SURFACE`, `INK`, `TAB`, `MENU`, `DRAWER`, `DISABLED`, `SELECTED`,
`DEAD`, `DOTTED`. ASCII mirror + index in `design/patterns/`. All drawing goes through `DitherRenderer`
in dither-px coordinates. Rules that follow from the engine:
- **Never `Canvas.rotate/scale`.** The renderer assumes root-view coordinates; rotated seats rotate
  content through `blit(..., rotation)` and layout maths, so global pattern phase is preserved.
- **Glyphs/icons on a dithered fill are drawn `opaque = true`** (or on a paper backing) — transparent
  ink on a mid dither is unreadable.
- In-place `invertRect` needs API 29+; on older devices redraw the region inside `withInverted {}`.
- Check any capture with `python3 tools/check_1bit.py <png> --scale S` (purity + grid alignment).

## Icons

16×16 1-bit bitmaps, authored as ASCII (`#` = black, `.` = white) in `design/icons/<name>.txt`,
one file per icon, exactly 16 lines × 16 chars. Outline style, ~1px strokes, 1px empty margin on
all sides. See "Tone" for the style direction (friendlier, slightly heavier than the first pass).
Icons must read clearly at S=2..6 and when inverted.

## Features (inspired by Lotus and similar trackers; adapt to e-ink)

**Core play**
- 2–6 players; layouts per count, each seat rotated to face its edge. Portrait and landscape.
- Starting life 20/25/30/40/custom. Tap ±1; press-and-hold repeats ±10 (stepwise, no animation).
- Pending-delta readout (e.g. `-7`) that commits after a short idle; shown as static text.
- Commander damage per opponent (with partner support = 2 sources per opponent), auto-links to life loss (toggle).
- Per-player counters (drawer): poison, energy, experience, rad, storm, commander tax (per commander), tickets, generic.
- Player state toggles: monarch, initiative, city's blessing, day/night (global), ring-bearer.
- Auto-KO marker: life ≤ 0, poison ≥ 10, commander damage ≥ 21 — panel renders in a "dead" dither. Toggleable.
- Player names + per-player background pattern choice (the 1-bit substitute for player colours).

**Table tools (centre menu)**
- New game / reset (with confirm), choose starting player at random, dice (d6/d20/custom), coin flip,
  turn tracker + optional turn/game timer (minute-resolution redraws only), undo/redo, game history log.

**Settings**
- Player count & layout, starting life, integer scale S (auto / 2–6), invert (dark mode),
  hold-to-repeat step, keep screen awake, which counters appear in the drawer, full-refresh every
  N changes (ghosting cleanup), haptics off by default.
- **Not settings — always on** (Sam, 2026-11-13): commander damage reduces life, auto knock-out,
  effects, quips. Don't build toggles for them (core keeps the flags; the UI never exposes them).

**Persistence**
- Game state + settings survive process death (single small file, written off the main thread).

## Tone (direction from Sam, 2026-11-13)

Friendly, lighthearted, doesn't take itself too seriously — never overt or cringey. Personality
lives in small spaces, not in the main play surface:
- **Effects**: Sam loves dither shading used both in overlays and in how effects interact with the
  numerals (ghosting, cut-outs, gloss). Keep building in that language.
- **Icons** lean slightly cutesy/wacky: rounder, a bit more character (faces, tilt, small details),
  and a bit more weight — partly filled shapes (like the arrowheads on `refresh`/`reset`) read well.
  The three original Figma icons are *not* sacred: `poison` and `rads` get redesigned, `storm` refined.
- **Copy voice** (Sam, 2026-11-13): a smidge combative — the friend who heckles you across the table:
  "uh oh", "whoops", "beefed it". Short, blunt, playful; teasing, never mean or crude.
- **Copy**: short deadpan one-liners in a few spots — a random quip when a player is knocked out
  (in the spirit of Lotus's "didn't pay the 1"), dice/coin results, the reset confirm, empty history,
  first-launch names. Original lines only (MTG table humour, no card text or copied quotes), always
  short enough for the `ui` face. Quips are always on (no setting).
- Never at the cost of the hard rules: no animation, no extra redraws, nothing that slows a tap.

## Non-goals (for now)
Networking, accounts, card lookup, colour themes, animations, sound.
