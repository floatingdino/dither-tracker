# Dither Tracker — Backlog

Resumable task list. The coordinator (a `/loop` session) reads this file each tick, dispatches
Opus agents on ready tasks, verifies their work, and updates statuses here. **This file is the
single source of truth for progress** — any session can resume from it.

Read `docs/SPEC.md` first; its hard rules apply to every task.

## Protocol

- Status: `todo` → `wip` → `review` → `done` (or `blocked: <reason>`).
- A task is *ready* when all its deps are `done`.
- Coordinator, per tick: (1) verify anything in `review` (build + tests + look at output), mark
  `done` or send back; (2) pick ready tasks whose **Files** don't overlap any `wip` task;
  (3) mark them `wip`, dispatch one Opus agent each; (4) log the tick below.
- Commits: the coordinator (only) commits each verified task to the `build` branch, one commit
  per task (`T07: …`), staging only that task's files. Never push; never commit to `main`.
  (Approved by Sam 2026-11-13.)
- Agents: work only inside the task's **Files**; don't edit this file; don't commit; finish by
  reporting what was built, how it was verified, and anything left open.
- "Verified" always means `./gradlew :core:test :app:assembleDebug` passes, plus the task's own check.
- Screens are verified by rendering to PNG (Robolectric/Paparazzi-style JVM render or emulator
  screenshot at 1404×1872 / 1872×1404) and asserting **every pixel is #000 or #FFF**.

## Tasks

### Phase 0 — Foundations

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T01 | done | — | Gradle scaffold: wrapper, `:core` (Kotlin/JVM + JUnit) and `:app` (single Activity, blank custom View), `.gitignore`, builds a debug APK. Document build/run commands in `README.md`. | root gradle files, `app/`, `core/build.gradle.kts`, `README.md` |
| T02 | done | — | Icon set: 16×16 ASCII icons for every tracker/toggle/tool in SPEC (see T02 notes), plus `design/icons/README.md` contact sheet and a validator script. | `design/icons/` |
| T03 | done | T01 | Core game model: immutable `GameState`, players, counters, commander damage (partners), toggles, auto-KO rules, reducer of `Action`s, undo/redo stack, history log. Full unit tests. | `core/src/` |
| T04 | done | T01 | Dither engine: pattern set (SPEC), global-phase tiled fill, integer-scale grid maths (`DitherGrid`: device px ↔ dither px, margins), 1-bit paint helpers, icon loader/blitter (build step converts `design/icons/*.txt` → Kotlin/asset). JVM tests for grid maths; pixel-purity test. | `app/src/main/**/dither/`, `design/patterns/` |
| T05 | wip | T04 | Bitmap type: integer-scaled 1-bit mono font. Large numerals (life totals) + small caps UI face (A–Z, 0–9, punctuation). Hand-authored ASCII glyphs or an OFL pixel font converted at build time; no AA. Text measure/draw API on the dither grid. | `app/src/main/**/type/`, `design/font/` |

### Phase 1 — Play screen (matches Figma)

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T06 | todo | T03 T04 T05 | Play screen for 4 players landscape: 2×2 panels, top row rotated 180°, dotted dividers, life total, −/+ hit zones (whole left/right half), inversion press feedback, dirty-rect redraws. | `app/src/main/**/play/` |
| T07 | todo | T06 | Hold-to-repeat (±10 steps) and pending-delta readout with idle commit. | `play/`, `core/` reducer hooks |
| T08 | todo | T06 T02 | Player drawer: bottom tab, opens (no animation) to tracker tray; counters with icon + value + −/+; `COMMANDER DAMAGE` button → per-opponent damage view (partner aware, optional life link). | `app/src/main/**/drawer/` |
| T09 | todo | T06 | Centre diamond menu button + table menu overlay (new game, reset w/ confirm, undo/redo, settings entry, tools entry). | `app/src/main/**/menu/` |
| T10a | done | T03 | Core serialisation: versioned, dependency-free encode/decode of `Game` (state + log) and app settings; round-trip + corrupt-input tests. | `core/src/**/serial/` |
| T10b | todo | T10a T06 | App persistence: async single-file write, restore on launch, survives process death; verified on device. | `app/src/main/**/data/` |

### Phase 2 — Layouts & settings

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T11 | todo | T06 | Layout engine for 2–6 players, portrait + landscape, per-seat rotation (0/90/180/270), all on the dither grid with leftover px as margin. Render tests per layout. | `app/src/main/**/layout/` |
| T12 | todo | T09 T10b | Settings screen (SPEC list): 1-bit list/toggle/stepper widgets, scale S auto/2–6, invert mode, starting life, counters shown, keep-awake, full-refresh cadence. | `app/src/main/**/settings/`, `**/widgets/` |
| T13 | todo | T08 T12 | Player customisation: name entry (on-screen 1-bit keyboard or system IME), per-player background pattern picker. | `app/src/main/**/player/` |
| T14 | todo | T08 | State toggles: monarch, initiative, city's blessing, ring-bearer, global day/night — badges on panel using icons; monarch/initiative exclusive across players. | `play/`, `drawer/`, `core/` |
| T15 | todo | T06 T03 | Auto-KO rendering ("dead" dither over panel, tap to revive) + toggle. | `play/` |

### Phase 3 — Table tools

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T16 | todo | T09 | Random starting player, dice (d6/d20/custom), coin flip — static result reveal, 1-bit dice faces. | `app/src/main/**/tools/` |
| T17 | todo | T09 T03 | Turn tracker + optional turn/game timer (redraw at most once a minute; partial refresh). | `tools/`, `core/` |
| T18 | todo | T09 T03 | Game history log view (scrolling by page, not smooth scroll). | `app/src/main/**/history/` |

### Phase 4 — E-ink polish & release

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T19a | wip | — | E-ink refresh research: Supernote/Ratta refresh-mode APIs, what stock Android offers, ghosting strategy → `docs/eink.md` with a concrete recommendation. Read-only device inspection. | `docs/eink.md` |
| T19b | todo | T19a T06 | Implement the refresh strategy from `docs/eink.md`: invalidation audit, periodic full refresh, any safely available device refresh-mode hook. | `app/src/main/**/eink/` |
| T20 | todo | T11 T12 | Performance pass: zero allocations in draw, startup time, APK size, StrictMode clean; measure on emulator throttled. | whole app |
| T21 | todo | T04 T02 | App icon (1-bit, adaptive + legacy) and launcher label from the icon set / dither patterns. | `app/src/main/res/` |
| T22 | todo | all | Full pixel-purity regression suite: render every screen at S=2..6, both orientations, inverted and not; fail on any non-#000/#FFF pixel or any off-grid edge. | `app/src/test/` |
| T24 | wip | — | Icon redesign per Sam's feedback: redo `poison` (ugly), `rads` (too large in the set), refine `storm`; rework `experience`, `life`, `energy`, `commander` (too simplistic/off); add weight and a slightly cutesy/wacky character across the set, keeping the filled-arrow feel of `refresh`/`reset`. | `design/icons/` |
| T25 | todo | T15 T16 | Personality copy: `core` quip tables (KO lines by reason, dice/coin lines, reset confirm, empty history) picked via injected `Random`; shown as static `ui`-face text; "quips" setting. Original lines only. | `core/src/**/flavour/`, call sites |
| T23 | todo | T22 | Release build config (R8, signing instructions), sideload guide for Supernote Nomad in `README.md`. | gradle, `README.md` |

## Task notes

**T02 icons** — (superseded by T24: the Figma three are no longer fixed.) Original list: `energy`, `experience`,
`commander` (damage), `commander-tax`, `tickets`, `generic-counter`, `life` (heart), `monarch`
(crown), `initiative`, `citys-blessing`, `ring`, `day`, `night`, `dead` (skull is taken by poison
— use a tombstone/X), `dice`, `coin`, `timer`, `turn`, `undo`, `redo`, `reset`, `new-game`,
`settings`, `history`, `players`, `menu`, `more` (…), `plus`, `minus`, `close`, `check`,
`chevron-left/right/up/down`, `invert`, `scale`, `rotate`, `partner`, `refresh`.

## Decisions

- Typeface: true bitmap font on the dither grid (T05) — confirmed by Sam 2026-11-13.
- Device: a physical Supernote Nomad is connected over USB (`~/Library/Android/sdk/platform-tools/adb`,
  serial `<device-serial>`; Android 11 / API 30, 1404x1872 @ 300dpi, arm64-v8a). Screen tasks must be
  checked on it: `adb install -r` the debug APK, launch, `adb exec-out screencap -p` and run the
  pixel-purity check on the capture. Only install/launch/screencap this app — touch nothing else on the device.

## Tick log

- 2026-11-13 — Tick 1: read Figma (Play 1872×1404, panel+drawer, button, 3 icons), chose native
  Android/Kotlin custom-View stack, wrote SPEC + backlog, transcribed Figma icons. Dispatched T01, T02.
- 2026-11-13 — Tick 2: T01 verified (build + tests pass; APK installs and launches on the Nomad,
  full-screen pure white 1404x1872 capture) and committed. Toolchain: Gradle 9.6.0, AGP 9.5.0,
  Kotlin 2.2.10, compileSdk 37, `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`.
  Device reports portrait 1404x1872 natively. Dispatched T03, T04. T02 still running.
- 2026-11-13 — Tick 3: T03 verified (75 core tests pass on rerun, no Android/clock use) and
  committed; behaviour notes in `docs/core-api.md`. Split T10 into T10a (core) / T10b (app);
  dispatched T10a. T02, T04 still running.
- 2026-11-13 — Tick 4: T04 verified (20 app tests + build pass; fresh Nomad capture passes
  `tools/check_1bit.py --scale 4`; eyeballed) and committed. Engine rules folded into SPEC.
  Dispatched T05. T02, T10a still running.
- 2026-11-13 — Tick 5: T02 verified (validator: 43 icons, 0 problems; contact sheet reviewed;
  Figma icons untouched) and committed. Added T24 (icon polish). T05, T10a still running.
- 2026-11-13 — Tick 6: T10a verified (96 core tests pass on rerun) and committed. Save format:
  binary `DTSV` v1 + CRC32, ~2.2 KB for 4 players + 200 log entries; `SaveCodec.encode/decode`,
  `AppSettings` in `core.serial`; undo history not persisted (T10b: `History(save.game)`).
  Defaults to tune on device: `fullRefreshEvery = 20`, `keepScreenAwake = true`, `orientation = LANDSCAPE`.
  Only T05 running; T06 is next once it lands.
- 2026-11-13 — Tick 7: T05 still running (font files in progress). Split T19 into research (T19a,
  dispatched) and implementation (T19b).
- 2026-11-13 — Sam's direction: icons need more weight + friendlier/wackier character, Figma three to
  be redesigned; app-wide lighthearted tone with small personality moments. Added SPEC "Tone",
  repurposed T24 (dispatched), added T25.
