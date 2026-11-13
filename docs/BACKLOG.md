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
- **Commits are frequent and atomic** (Sam, 2026-11-13). Agents commit their own work to `build` as
  they go: one logical change per commit (a layout rule + its tests; one icon; one renamed API), each
  commit building and passing tests, message `T07: <what changed>`, ending with the Co-Authored-By
  line. Stage explicit paths only — never `git add -A`, `git add .` or a whole shared directory; never
  stage another agent's files. If `index.lock` is held by another agent, wait a moment and retry.
  Agents never push, never commit to `main`, never rewrite history. **The coordinator pushes `build` to
  `origin` (github.com/floatingdino/dither-tracker, public) regularly** — after each verified task or
  backlog update (Sam, 2026-11-13). **Private material never goes in a commit** (Sam, 2026-11-13): device
  serials, third-party reference screenshots, private links (e.g. the Figma file), personal details.
  Put them in the untracked `local/` folder (gitignored; see `local/README.md`) or the coordinator's
  memory, and refer to them from tracked docs without the content. The coordinator still verifies each task
  after the agent reports and commits follow-up fixes and backlog updates separately.
- **Devices.** Two kinds of target:
  - **Emulator** (`tools/emulator.sh up <port>` -> `emulator-<port>`; Nomad-shaped, 1872x1404 landscape,
    API 37, read-only instances so several run at once): the default for layout, input, persistence
    and 1-bit purity checks. **Each agent that needs a device takes its own port** (5560, 5562, …) and
    runs `tools/emulator.sh down <port>` when done. Always pass `adb -s emulator-<port>`. It is not
    e-ink and not the Nomad's Android 11: never use it to judge refresh, ghosting, timing or weight.
  - **The Nomad** (physical, one at a time, coordinator-assigned): for anything about the panel —
    refresh, ghosting, effects timing, legibility at arm's length — and a final check of each
    user-visible task. Its adb serial is in the untracked `local/README.md`; always pass `adb -s`.
    At most one running task may use it. (Two agents tapping it at once corrupted each other's runs
    in tick 11.) With both attached, a bare `adb` command fails — always name the target.
- Agents: work only inside the task's **Files**; don't edit this file; finish by
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
| T05 | done | T04 | Bitmap type: integer-scaled 1-bit mono font. Large numerals (life totals) + small caps UI face (A–Z, 0–9, punctuation). Hand-authored ASCII glyphs or an OFL pixel font converted at build time; no AA. Text measure/draw API on the dither grid. | `app/src/main/**/type/`, `design/font/` |

### Phase 1 — Play screen (matches Figma)

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T06 | done | T03 T04 T05 | Play screen for 4 players landscape: 2×2 panels, top row rotated 180°, dotted dividers, life total, −/+ hit zones (whole left/right half), inversion press feedback, dirty-rect redraws. | `app/src/main/**/play/` |
| T07 | done | T06 | Hold-to-repeat (±10 steps) and pending-delta readout with idle commit. | `play/`, `core/` reducer hooks |
| T08 | done | T06 T02 | Player drawer: bottom tab, opens (no animation) to tracker tray; counters with icon + value + −/+; `COMMANDER DAMAGE` button → per-opponent damage view (partner aware, optional life link). | `app/src/main/**/drawer/` |
| T30 | done | T08 T27 | **Commander damage mode on the main interface** (Sam, 2026-11-13): an icon button on each player's tile; pressing it makes that player the *target* — every other tile swaps its life total for the commander damage that tile's player has dealt to the target (−/+ adjust it; partner slots supported), and the target's own tile swaps its life total for a quip (e.g. "Target"). Press again (or the target tile) to leave. Remove the drawer's COMMANDER DAMAGE button/view. Add a target quip category to core flavour. Same agent as T27, after it. | `play/`, `drawer/`, `core/**/flavour/` |
| T31 | done | — | Icon: redraw `commander` (and `partner` to match) to read as an *enemy* — demon/imp — in the current solid style. Design-only. | `design/icons/` |
| T32 | done | T30 | Counter peeks (Sam, 2026-11-13): with the drawer closed, show each non-zero counter as a small icon + value chip on the tile, display-only, overflow as "+N". Same agent as T27/T30. | `play/`, `drawer/` |
| T34 | done | T27 T30 T32 | **Component refactor** (Sam approved, 2026-11-13: play-screen edits were blocking compartmentalised features). No behaviour change. A `Component` contract (layout on the grid, draw, claim touch zones, tap/hold, invalidation on state change); a host view that owns no feature logic and loops over an ordered component list; zone ids allocated by the host; a per-seat tile layer (life, readout, drawer, peeks, commander mode, effects slot, KO slot) and a screen layer for overlays (menu, settings, history, tools). Existing tests pass; before/after device captures pixel-identical. Write `docs/architecture.md`. | `app/` |
| T35 | done | T05 | Shared 1-bit widgets (JVM-only, new package): button, icon button, list row, toggle, stepper, segmented choice, confirm prompt, paged list — pure layout + painter + hit-testing over `DitherRenderer`/`TextRenderer`, press inversion, fingertip-size rule. | `app/**/widgets/` |
| T36 | done | — | Centre menu design options (Sam, 2026-11-13: likes that the Lotus radial menu isn't pointed at any one player (reference screenshot kept out of the repo) but no angled buttons; achieve it in our pixel-perfect language). 3–4 concepts as true-pixel mocks, incl. how second-level panels (settings, history, players) orient. Design-only. | `design/menu/` |
| T09 | done | T34 T35 | **Centre menu = the gem** (Sam's pick, `design/menu/concept-1-gem-*`, README): the diamond grows into a stepped diamond of 3x3 facets; items NEW, UNDO, REDO, DICE, FIRST, LOG, SETUP, CLEAN; centre becomes a close X; labelled facets face their nearest seated edge (upside-down labels on the far half are fine). **Fix:** the icon-only doubled undo/redo tips on the divider are inscrutable — give undo and redo labelled facets like the rest, and put rotation-symmetric items (e.g. DICE, CLEAN) on the two divider tips, or otherwise make undo/redo unmistakable. Second level per the mocks: two-faced restart confirm and dice result, settings card with a rotate control. Built as a screen-layer component with `widgets/`. | `app/**/menu/` |
| T10a | done | T03 | Core serialisation: versioned, dependency-free encode/decode of `Game` (state + log) and app settings; round-trip + corrupt-input tests. | `core/src/**/serial/` |
| T10b | done | T10a T06 | App persistence: async single-file write, restore on launch, survives process death; verified on device. | `app/src/main/**/data/` |

### Phase 2 — Layouts & settings

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T11 | todo | T06 | Layout engine for 2–6 players, portrait + landscape, per-seat rotation (0/90/180/270), all on the dither grid with leftover px as margin. Render tests per layout. | `app/src/main/**/layout/` |
| T12 | wip | T34 T09 T10b | Settings screen (SPEC list): 1-bit list/toggle/stepper widgets, scale S auto/2–6, invert mode, starting life as a segmented [40] [30] [20] choice (not a stepper), counters shown, keep-awake, full-refresh cadence. | `app/src/main/**/settings/`, `**/widgets/` |
| T13 | todo | T08 T12 | Player customisation: name entry (in-app 1-bit keyboard — no system IME, see SPEC rule 7), per-player background pattern picker. | `app/src/main/**/player/` |
| T14 | todo | T08 | State toggles: monarch, initiative, city's blessing, ring-bearer, global day/night — badges on panel using icons; monarch/initiative exclusive across players. | `play/`, `drawer/`, `core/` |
| T15 | done | T34 T06 T03 | Auto-KO rendering ("dead" dither over panel, tap to revive). Always on — no toggle. | `play/` |
| T42 | wip | T15 | **KO / winner tile: the quip replaces the life total** (Sam, 2026-11-14) — on a dead tile and on the winner's tile the quip takes the hero position at a large size instead of the (ringed, hollow) number; cause/final life becomes the small detail. Plus a copy exploration: death and victory lines in a classic 8/16-bit game-text voice (pastiche, not verbatim quotes). Dead tiles use a **light ordered dither, not crosshatch** (Sam: crosshatch is not part of the dither language) — change `Role.DEAD`. Also fix: the centre diamond and dividers must keep their outline against dead tiles' crosshatch. Emulator 5564. | `ko/`, `core/**/flavour/`, `design/copy/quips.md` |
| T43 | wip | T08 T37 | **Drawer counters need an obvious way to go down / reset** (Sam, 2026-11-14). Today the left half of a cell decrements but nothing shows it. Give each counter cell visible − and + affordances (at least once the value is non-zero), and a quick reset to zero (e.g. hold on −), with the press model unchanged (act on release; slow hold-repeat). Emulator 5568. | `drawer/` |

### Phase 3 — Table tools

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T16 | wip | T34 T09 | Random starting player, dice (d6/d20/custom), coin flip — static result reveal, 1-bit dice faces. | `app/src/main/**/tools/` |
| T17 | todo | T34 T09 T03 | Turn tracker + optional turn/game timer (redraw at most once a minute; partial refresh). | `tools/`, `core/` |
| T18 | wip | T34 T09 T03 | Game history log view (scrolling by page, not smooth scroll). | `app/src/main/**/history/` |

### Phase 4 — E-ink polish & release

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T19a | done | — | E-ink refresh research: Supernote/Ratta refresh-mode APIs, what stock Android offers, ghosting strategy → `docs/eink.md` with a concrete recommendation. Read-only device inspection. | `docs/eink.md` |
| T19b | todo | T19a T06 | Implement the refresh strategy from `docs/eink.md`: invalidation audit, periodic full refresh, any safely available device refresh-mode hook. | `app/src/main/**/eink/` |
| T20 | todo | T09 T12 T28c (most features in) | **Comprehensive performance and battery pass** (Sam, 2026-11-14: as responsive as possible, very frugal with battery). *Responsiveness:* tap-to-pixel latency on the Nomad (trace + high-speed video), cold and warm start, onDraw time per interaction, zero allocations in draw/touch paths (allocation tracker, not just code reading), no main-thread disk or decode, overdraw, bitmap/mask memory, GC pauses, StrictMode clean, baseline profile / R8 full mode, APK size. *Battery:* no wake locks beyond the keep-awake setting; nothing scheduled while idle (no timers, handlers or animations ticking — verify with `dumpsys alarm`, `batterystats`, and CPU time over a 30-minute idle with the screen on and off); save writes batched; effects and readout timers cancelled when idle and in `onPause`; e-ink refresh count per action minimal (each panel update costs power); cleanup-flash cadence justified by measurement; behaviour when backgrounded and on screen-off. Known going in: the T34 component host costs ~25–60 µs more per `onDraw` in release (443–463 µs vs 401–416 µs on the Nomad); candidates: public `Frame` fields, fewer `TileSlots` hops. Effects prewarm builds ~30 MB of full-frame masks at S=4 (crop masks to each layer's ink bounds). *Deliverables:* a repeatable benchmark script under `tools/`, `docs/performance.md` with before/after numbers and budgets (e.g. tap latency, idle CPU ~0), regression tests where practical, fixes applied. Uses the Nomad for all timing. | whole app, `tools/`, `docs/performance.md` |
| T21 | todo | T04 T02 | App icon (1-bit, adaptive + legacy) and launcher label from the icon set / dither patterns. | `app/src/main/res/` |
| T22 | todo | all | Full pixel-purity regression suite: render every screen at S=2..6, both orientations, inverted and not; fail on any non-#000/#FFF pixel or any off-grid edge. | `app/src/test/` |
| T24 | done | — | Icon redesign per Sam's feedback: redo `poison` (ugly), `rads` (too large in the set), refine `storm`; rework `experience`, `life`, `energy`, `commander` (too simplistic/off); add weight and a slightly cutesy/wacky character across the set, keeping the filled-arrow feel of `refresh`/`reset`. | `design/icons/` |
| T25a | done | T03 | Personality copy tables in core: original quips (KO lines by reason, dice/coin lines, reset confirm, empty history, default player names) with a pure picker taking `Random`; length-checked against the `ui` face budget. | `core/src/**/flavour/` |
| T25b | todo | T25a T15 T16 | Show quips at their call sites (KO panel, tools, reset confirm, history) as static text. | call sites |
| T29 | done | T03 | Core: per-seat undo grouping so interleaved taps from different seats don't split one seat's burst into several undo steps/log entries. JVM-only. | `core/src/` |
| T26 | done | — | Hero numeral exploration: 4–5 heavier, more characterful candidates for life totals, rendered in-context at device pixels for Sam to pick. | `design/font/candidates/` (or `design/font-candidates/`) |
| T28a | done | — | Hit/heal effect sprites: 3–4 damage and 2–3 heal effect concepts (swipe, crunch, fizzle, sparkle…), 2–4 frames each as 1-bit ASCII frames, with offline previews (frame strips + in-context mock). | `design/fx/` |
| T28b | done | T28a | Effect engine (JVM-only): build step turning `design/fx/<name>/` frames + meta into generated Kotlin, pure `FxPlayer` state machine (injectable scheduler; trigger/cancel/no-queue/max-two rules), draw helper (black + knock-out layers, rotation). Ships `pow` and `twinkle`. No PlayView wiring. | `app/**/fx/`, `app/build.gradle.kts` |
| T28c | done | T28e T27 T30 | Promote **all** round-two options Sam approved from `design/fx/options/` to `design/fx/` (claw: `claw-cut` only — "Claw-A"; drop `claw-paper`; retire round-one `claw`, `wham`, `zap` and old `pow`/`crack`/`twinkle` in favour of the refits; keep `heart`, `sprout`, `fizzle`), then wire effects into the play screen: trigger on delta commit by sign/size, KO and last-standing flourishes, cancel on tap (always on — no settings toggle); tune hold time and film on the Nomad. | `play/`, `design/fx/` |
| T28e | done | T28b | Engine support for round-two effects: `x` (invert under mask), tile-filled layers at global phase, `anchor tile`; promote chosen options from `design/fx/options/` to `design/fx/`. JVM-only. | `app/**/fx/`, `design/fx/` |
| T28f | todo | T28c | More effects in the round-two style (Sam: "continue building out more animations in this style after all the current set"): dither shading in overlays and numeral interactions, cut-outs, tile-wide reach; new damage/heal variety, counter-specific hits (poison, commander damage), monarch/initiative gained, turn pass, dice/coin reveals. Design then promote. | `design/fx/` |
| T33 | done | T30 (owns flavour) | Quip tone pass: make the whole table a smidge more combative ("uh oh", "whoops", "beefed it") per SPEC Tone; keep length/charset rules and the doc mirror. | `core/**/flavour/`, `design/copy/quips.md` |
| T37 | done | T34 | **Small numerals = `bold`** (Sam's pick, `design/font/candidates/mini2/bold`, 10x14, sticker treatment, no shadow). Build: adopt it as the small numeral face replacing `num-m` everywhere at **k=1 everywhere** — the commander-target life, the pending-delta readout, drawer values and peeks (Sam, 2026-11-14: target life and deltas are 1x too, not 2x); re-compose the target tile (quip + heart + life) and the readout position for the smaller size; fix the 3-digit drawer value that is 2 px wider than its cell; re-derive affected layouts and tests; verify on device at arm's length. | `design/font/`, `type/`, consumers |
| T38 | done | T34 T37 | **Counter peeks = `split`** (Sam's pick, `design/font/candidates/mini2/peeks-split.png` + README): chips in both bottom corners of the tile beside the tab, alternating left/right, three per corner, six slots; `+N` takes the sixth slot on overflow; poison >= 8 inverts; display-only; hidden while the drawer is open and in commander mode. | `peeks/` |
| T40 | done | — | **Icon weight pass** (Sam, 2026-11-14: the close X looks thin next to the chunky numerals; other icons need more beef too). Redraw `close` heavier and bring the thin/outline icons up to the sticker numerals' weight (chevrons, check, plus, minus, more, menu, undo, redo, refresh, reset, rotate, storm, timer, dice, history, new-game, dead, ring, monarch, night, coin, invert …). Design-only, drawn as candidates in `design/icons/candidates/heavy/` — do NOT touch `design/icons/*.txt` while T34 runs (its before/after captures must stay byte-identical); the coordinator promotes after T34. | `design/icons/candidates/heavy/` |
| T41 | todo | T34 T40 | **Contour cut-outs for glyphs on dither** — Sam approved **halo 1** (a 1 px paper halo following the glyph's outline, holes filled first; see `design/icons/candidates/heavy/halo-mocks.png` and README) in place of the square paper backing: generate halo masks at build time for icons; use for the diamond's menu glyph and commander close X, the tab dots, and anywhere a glyph sits on a dither fill; verify on device. (Icon promotion is already done.) | `dither/`, `chrome/`, `drawer/`, icon build step |
| T28d | done | T28a | More effect options per Sam: bigger, opening up across the tile; knock-out cut-outs through the numerals; a closer `claw`; drawn over sticker numerals. Keep `pow`, `crack`, `twinkle` as approved. | `design/fx/options/` |
| T27 | done | T08 | Adopt **sticker** (Sam's pick, 2026-11-13) as the hero numeral: new `num-l` from `design/font/candidates/sticker` (32x56 at k=1); draw the checker drop shadow as a separate mask filled at global pattern phase (renderer/type addition) instead of baked into glyphs; half-size +/- glyphs; re-derive play + drawer layout; verify on device. | `design/font/num-l/`, `type/`, `play/` |
| T39 | todo | no agents running; Sam's go-ahead at run time | **Rewrite git history to remove private data** (Sam, 2026-11-14). Purge from every commit on `build`: the test device's adb serial (in old versions of this file), the third-party reference screenshot under `design/reference/`, and the private Figma file link (old `docs/SPEC.md`). The exact strings are in the untracked `local/README.md` — do not copy them into tracked files or commit messages. Use `git filter-repo` (or equivalent) with a replace-text file kept under `local/`; also check commit messages; verify with a full-history grep that nothing remains; then force-push `build` (confirm with Sam immediately before the force-push — it rewrites every commit hash) and ask Sam to have GitHub drop cached/dangling objects if needed. Coordinator only; run when no agent has work in flight; back up the repo first (`git bundle` into `local/`). | whole repo history |
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
  Android 11 / API 30, 1404x1872 @ 300dpi, arm64-v8a). Screen tasks must be
  checked on it: `adb install -r` the debug APK, launch, `adb exec-out screencap -p` and run the
  pixel-purity check on the capture. Only install/launch/screencap this app — touch nothing else on the device.

## Needs Sam

- E-ink experiments E1–E10 in `docs/eink.md` §5 need a phone slow-motion video / raking-light photos
  of the Nomad once the play screen is on it — the coordinator can drive the taps over adb.

- Icon redesign (T24) is awaiting Sam's eye: `design/icons/contact-sheet.png` vs `contact-sheet-before.png`.
  Coordinator's notes: `storm` is now the only outline icon in a mostly solid set; `partner` is busy;
  `initiative` still cone-like.

- Play screen look (T06, `design/captures/t06-play.png`) for Sam: numbers are ~2x the mock (largest
  integer multiple that fits "-999"); the -/+ glyphs are half the number's scale and look light;
  the diamond/tab fill (D6) is fainter than the mock; a tap inverts the half-panel including half the number.

- Quips (T25a) are in `design/copy/quips.md` for Sam to mark up. Least sure: "All quiet on the
  battlefield", "Took it personally", "Tails never fails, they say", "Drew nothing but lands" (close in
  spirit to Lotus's "flooded").

- **Film the effects on the Nomad** (T28c): the filming checklist and gallery commands are in
  `app/src/main/java/au/com/josephmark/dithertracker/fx/README.md` (`--es fx all`, `--es fx <name>`,
  `--ef fxHold <scale>`): settle time per beat, residue after 50 loops, readability from the far seat.

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
- 2026-11-13 — Tick 8: T05 verified (34 app tests pass; fresh Nomad capture of the type specimen
  passes the checker; eyeballed) and committed. Faces: `ui` 5x9, `num-m` 8x14, `num-l` 16x28, any
  integer multiple k; API in `app/.../type/` (`TextRenderer.drawIn/drawIntIn`). Known nits: `1` flag
  heavy, `5` bowl corner, `8` snowman, strokes slightly lighter than mock. Dispatched T06.
  T19a, T24 still running.
- 2026-11-13 — Tick 9: T19a reviewed and committed (`docs/eink.md`). Key findings: no documented
  refresh API for sideloaded apps; `invalidate(Rect)` doesn't limit redraw; strategy = minimal changed
  pixels, one frame per action, single window, app-drawn flash cleanup, optional guarded vendor
  `screenRefresh`. Added SPEC rule 7 (single window). Forwarded to the T06 agent. T06, T24 running.
- 2026-11-13 — Tick 10: T24 reviewed (validator: 43 icons, 0 problems; sheet eyeballed) and
  committed. 30 icons redrawn as solid shapes with cut-out details; arrows share filled heads. T06 running.
- 2026-11-13 — Tick 11: T06 verified (96 core + 57 app tests; fresh install on Nomad, 10 scripted
  taps across four zones gave exactly 37/42/41/36 incl. rotated row; capture 1872x1404 passes checker)
  and committed. One View kept (measured full re-record ~0.8 ms median; HWUI ignores dirty rects).
  Life applies on touch-down. Seats clockwise from TL: 0 TL, 1 TR, 2 BR, 3 BL. Hooks: `PlayInput`
  onZonePressed/Released (T07), `RESERVED` hits for tab/diamond (T08/T09), `GameSession.replace` (T10b).
  Agent saw one unreproduced run with 3 lost taps under adb injection — watch for it.
  Dispatched T07, T10b, T25a. Play-view files are single-writer: T08, T09, T11, T15 queue behind T07.
- 2026-11-13 — Sam's feedback (device): old `monarch` restored; `night` = old moon + two dot stars
  (the 4-point star read as a plus / experience) — done by coordinator; hero numerals too thin/boring
  -> T26 exploration dispatched; drawer tab and menu diamond too small -> forwarded to the T07 agent
  (owns PlayLayout); hold for +/-10 confirmed (T07).
- 2026-11-13 — Tick 12: T25a verified (112 core tests pass; all lines read) and committed.
  API: `Flavour.of(quips)`, `ko(reason, random)`, `dice`, `coin`, `resetConfirm`, `winner`, name sets;
  no-repeat via `RecentPicks`. NewGame and Reset share one confirm category. T07, T10b, T26 running.
- 2026-11-13 — Sam: consider small limited-frame overlay effects for damage/healing. SPEC rule 4
  amended (narrow exception with e-ink constraints). Added T28a (dispatched) and T28b.
- 2026-11-13 — Tick 13: T10b reported done (96 app tests pass on the mixed tree). Left in `review`,
  uncommitted: its files are interleaved with T07's in-progress edits (`GameSession.kt`) and T07 is
  using the device, so the coordinator's on-device restore check and the commit wait for T07.
  Design: `data/SaveStore` (atomic write + backup, 1 s debounce, 10 s cap), `Persistence` (worker
  thread, `onPause`/`onStop` flush, restore once per process), `SettingsHolder.shared`.
  Cold restore ~30 ms sync in `onCreate`. Known: a force-stop/crash within 1 s of a tap can lose it.
- 2026-11-13 — Sam clarified the animation rule: "keyframes, not inbetweens" — limited animation that
  respects the panel is welcome anywhere it earns its place, not just hit/heal. SPEC rule 4 rewritten.
  Later UI tasks (T08 drawer, T09 menu, T15 KO, T16 dice/coin) may use 1–2 keyframe beats.
- 2026-11-13 — Tick 14: T07 + T10b verified together by the coordinator on the Nomad (112 core + 96
  app tests; 3 taps 36->33; 1 s hold on a rotated seat 55->75 with "+20" readout facing that player;
  readout cleared; force-stop + relaunch capture pixel-identical; all captures pass the checker) and
  committed separately. Tab now 89x21, diamond 41x41 dither px. Hold: 500 ms delay, 400 ms cadence,
  first repeat absorbs the tap's 1 (39 -> 30 -> 20). Known core issue: `History` has one open group,
  so interleaved seats split undo steps -> T29. Dispatched T08 (device) and T29 (JVM-only).
  T26, T28a still running.
- 2026-11-13 — Tick 15: T26 reviewed (comparison sheet eyeballed) and committed; awaiting Sam's pick.
  Sam's hold feedback forwarded to the T08 agent (owns play/): no +/-1 on touch-down — tap applies on
  release, a hold applies only +/-10; repeat interval raised to ~1000 ms to stop overshoot.
- 2026-11-13 — Tick 16: Sam picked **sticker** for life totals -> T27 specified, queued behind T08.
  T28a reviewed (sheet eyeballed; passes 1-bit check per agent) and committed: pow, zap, claw, crack
  (damage); heart, sprout, twinkle (heal); wham (big hit), fizzle (KO) optional. T08, T29 running.
- 2026-11-13 — Sam on effects: `pow`, `crack`, `twinkle` good; `claw` close; likes knock-out cut-outs
  over the numerals; wants options that open up and cover more of the tile; start building with `pow`
  and `twinkle`. SPEC rule 4 footprint wording loosened. Dispatched T28b (engine, JVM-only) and T28d
  (more options, design-only). T08 (device), T29 running.
- 2026-11-13 — Tick 17: T29 verified (130 core tests pass on rerun) and committed. Undo grouping is
  per key via replay through `reduce`; undo removes the last-touched group and leaves other seats'
  changes intact. App follow-up forwarded to the T08 agent: use `history.endGroup(seatKey)` in
  PlayView's commit path. T08 (device), T28b, T28d running.
- 2026-11-13 — Tick 18: T28b verified (157 app tests pass incl. 23 fx; build.gradle change is an
  appended block only) and committed. `fx/` has `Fx` (generated, all 9 effects), `FxPlayer`,
  `FxRenderer`, `FxLayout`, `FxPolicy`; wiring notes for T28c in `app/.../fx/README.md`. Note for
  T28c: `PendingDeltas.commit` zeroes the delta before the sink callback — pass the net through.
  Metas lack a `role` line (built-in fallback table for the nine names). T08 (device), T28d running.
- 2026-11-13 — Tick 19: T08 verified by the coordinator on the Nomad (130 core + 157 app tests; fresh
  data; tap on release 40->39; 2.7 s hold 40->10; drawer opens, number moves up, tax cell 0->4; capture
  passes checker) and committed. New press model: nothing on touch-down, tap applies on release, hold
  = +/-10 at 500 ms then every 1000 ms. Sam: move commander damage out of the drawer into a main-
  interface "target" mode (T30) and make the commander icon an enemy imp/demon (T31). Dispatched one
  agent for T27 then T30 (device), and T31 (design-only). T28d still running.
- 2026-11-13 — Sam: add counter peeks when the drawer is closed -> T32, forwarded to the T27/T30 agent.
- 2026-11-13 — Sam: `poison` icon now uses the Phyrexian symbol (common shorthand) — drawn by the
  coordinator (circle bisected by a vertical bar, 2 px strokes). Note: it is a Wizards of the Coast
  mark; fine for a personal sideloaded app, revisit if the app is ever distributed. README row and
  contact sheets to be refreshed when T31 lands.
- 2026-11-13 — Tick 20: T31 reviewed (validator passes; sheet eyeballed) and committed: `commander`
  is a grinning imp head, `partner` an imp with a small sidekick. Poison README row fixed.
  Running: T27 -> T30 -> T32 (one agent, device), T28d.
- 2026-11-13 — Tick 21: T28d reviewed (sheet eyeballed; agent committed its own work in 8 atomic
  commits) — 14 options over sticker numerals; awaiting Sam's picks. Poison icon went through two more
  passes at Sam's request: now a heavy ring with a floating beam (`candidates/poison-options.png`).
  Running: T27 -> T30 -> T32 (one agent, device).
- 2026-11-13 — Tick 22: dispatched T28e (engine capability only, JVM-only, tested against
  `design/fx/options/`; promotion of chosen effects waits for Sam's picks). Poison icon reverted to
  option A at Sam's request. Running: T27 -> T30 -> T32 (device), T28e.
- 2026-11-13 — Sam: poison option E after all; all round-two animations approved, `claw-cut` (Claw-A)
  the pick of the claws, loves the dither shading in overlays and numeral interactions, wants more in
  this style afterwards (T28f); quips a smidge more combative (T33; target-mode lines told now).
- 2026-11-13 — Sam approved the component refactor ahead of the centre menu -> T34 (waits for the
  T27/T30/T32 agent, which owns the play files), then T09/T12/T15/T16/T17/T18/T28c can run in
  parallel as separate components. Dispatched T35 (widgets, JVM-only) now. Running: T27->T30->T32
  (device), T28e, T35.
- 2026-11-13 — Sam shared the Lotus radial menu as reference for the centre menu: player-neutral, but
  no angled buttons. Dispatched T36 (menu concepts as mocks, design-only); T09 waits on the pick.
- 2026-11-13 — Tick 23: play-screen agent reported T27 done (device-checked by it), T30/T32 written
  but unfinished; its `git commit` was blocked by the permission system so everything is uncommitted,
  and its shared-file edits (`app/build.gradle.kts`, `dither/DitherRenderer.kt`) deadlocked T28e, whose
  half-applied fx changes in turn broke the main tree's compile. Coordinator copied the play agent's
  files into a clean worktree (scratchpad `wt`, detached HEAD): core 131 pass; app 185 tests, 4 failing
  in `CommanderLayoutTest`. Agent sent back to fix there (JVM-only). Lesson: never run two agents with
  uncommitted edits to the same file — T34 exists to remove the need.
- 2026-11-13 — Sam on commander mode (device): hold = +/-10; drop "Beefed it" from target quips; the
  tile button is ugly -> move the entry point into the drawer; target quip one size smaller, ALL CAPS;
  remove the imp + "TO PLAYER X" label on opponent tiles; hide tabs/peeks/centre menu in the mode and
  turn the diamond into an X close. Forwarded to the agent.
- 2026-11-13 — Tick 24: play agent fixed the 4 failing tests in the worktree (one wrong expectation,
  three real bugs: `UpperLabel.toString`, layouts without an under-number row marked comfortable, quip
  scale ignoring height). Coordinator snapshotted that state, verified it in a second clean worktree
  (core 131, app 210) and on the Nomad (sticker numerals render with rotated shadows; tap 40->39; 1.7 s
  hold 40->60; capture passes the checker) and committed it: `6ad1f30` (target quips) and `bddac2c`
  (T27 + first-pass T30/T32 together — interleaved in PlayView). T27 done; T30/T32 stay wip for the
  redesign from Sam's feedback (agent continues in the worktree). Sam: no black strip behind the
  target's life; add a shadowless mini sticker face for it -> T37. T28e unblocked (shared files clean).
- 2026-11-13 — Tick 25: T28e verified in the main checkout (131 core + 240 app tests, 41 fx; tree
  compiles again; agent made 4 atomic commits). Engine now supports `x` cut, tile layers at global
  phase and `anchor tile`; all 14 options generate into a test-only `FxOptions`; shipping all 23
  effects adds ~39 KB to the debug APK. T28c notes in `app/.../fx/README.md` (pass the tile rect,
  clip to the panel, draw after the number, prewarm only what the policy can pick). Dispatched the
  design half of T37 (mini sticker face candidates). Running: T30/T32 redesign (worktree, JVM-only),
  T35 widgets, T36 menu concepts, T37 design.
- 2026-11-13 — Tick 26: T30 redesign + T32 verified by the coordinator (clean worktree: core 131,
  app 242; Nomad: drawer-bar imp button enters the mode; 2 + 21 + 3 damage took the target 40 -> 14;
  lethal tile inverts; X on the diamond leaves; state survives force-stop; peeks show tax 2 / poison 3
  / energy 2 under the number; all captures pass the checker) and committed (`8f84ca0`, `1e5a592`).
  In the mode you cannot switch target directly (leave, then use another drawer). Play files are now
  free: dispatched T34 (component refactor, device) and T33 (quip tone pass, core-only).
  Running: T33, T34, T35 widgets, T36 menu concepts, T37 mini numerals.
- 2026-11-13 — Tick 27: T36 reviewed (gem and tent mocks eyeballed; agent made 4 commits) — four
  centre-menu concepts with second-level mocks for the gem; awaiting Sam's pick and answers.
  Running: T33, T34 (device), T35, T37.
- 2026-11-13 — Tick 28: T33 verified (131 core tests pass on rerun; agent made 3 commits). Quips
  re-voiced in every category except the commander-target list; least sure: "Got got", "Thoroughly
  cooked", "Kind of had that coming", "Nailed it" (natural 1), "Death by tally marks".
  Running: T34 (device), T35, T37.
- 2026-11-13 — Tick 29: T35 verified (297 app tests pass, 96 widget tests; JVM render of the gallery
  eyeballed — `design/captures/t35-widget-gallery-render.png`; not yet seen on the device). Agent
  made 9 commits; `2f895a2` does not build on its own (fixed by the next commit; history not
  rewritten). Kit: Button/IconButton/ButtonRow, ListRow, Toggle, Stepper, Segmented, ConfirmPrompt,
  Panel, PagedList, TextFit/TextWrap, HitRegion, PressTracker, WidgetFrame (0/90/180/270).
  Wishlist for dither/type: shared `Icon.scaled(k)`, `TextRenderer.draw(text, start, end)`, a clip
  helper. Running: T34 (device), T37.
- 2026-11-13 — Sam: **gem** menu wins; labels are good; the undo icons on the corners are inscrutable
  (fix noted on T09). Settings to drop — always on: commander damage hits life, auto knock-out,
  effects, quips (SPEC updated; no toggles in T12/T15/T25b/T28c).
- 2026-11-13 — Sam: starting life is a segmented [40] [30] [20] choice, not a - 40 + stepper (SPEC, T12).
- 2026-11-13 — Tick 30: T37 design half reviewed (comparison sheet eyeballed; 7 commits by the
  agent). Awaiting Sam's pick; the build half (new faces in `design/font/`, swap `lifeFont`, readout,
  peeks/drawer values) follows. Running: T34 (device).
- 2026-11-13 — Sam on mini numerals: too big — about half that size; the `half` treatment (faithful
  sticker redraw) is the preference. Also plan to move counter peeks into a corner (T38). Dispatched
  T37 round two (design-only): a ~8x14 sticker-treatment face and corner-peek mocks.
- 2026-11-13 — Sam: push commits to GitHub regularly. The repo is public; at Sam's choice the tip was
  scrubbed (device serial removed from this file, Lotus reference screenshot deleted) and `build`
  pushed as is — both remain in earlier commits' history.
- 2026-11-13 — Sam: private material belongs in untracked fixtures or memory, not the repo. Added
  gitignored `local/` (Figma link, device serial); removed the Figma link from SPEC. Earlier commits
  on GitHub still contain the serial, the Lotus screenshot and the Figma link (history not rewritten).
- 2026-11-14 — Tick 31: T37 round two reviewed (comparison and in-context mocks eyeballed; 2 agent
  commits). Awaiting Sam's pick. Running: T34 (device).
- 2026-11-14 — Sam picked `bold` + `split`. T37 (build) and T38 are specified and wait for T34,
  which is refactoring the files they touch.
- 2026-11-14 — Sam: add a ticket to rewrite git history to remove private data -> T39 (coordinator,
  when no agents are running; force-push confirmed with Sam at run time).
- 2026-11-14 — Sam: commander-target life and the delta readout also use the new small face at 1x (T37 updated).
- 2026-11-14 — Sam: the menu/commander X is thin next to the chunky numerals; fit the paper cut-out
  around the glyph instead of the square backing; other icons need more weight -> T40 (design-only
  candidates, dispatched) and T41 (after T34).
- 2026-11-14 — Sam: `monarch` and `night` can be filled — it was the silhouettes that were liked, not
  the outline treatment. Forwarded to the T40 agent (solid versions keeping the current shapes).
- 2026-11-14 — Tick 32: Sam: set up a simulator if the device is a bottleneck. Added
  `tools/emulator.sh` and a `Dither_Nomad` AVD (1404x1872 @ 300 dpi, landscape override; API 37
  image already installed). Verified: boots headless in ~15 s, two read-only instances run side by
  side, the app installs and its captures pass `check_1bit --scale 4` and are identical across
  instances. Gotchas baked into the script: the first-run "Viewing full screen" sheet, and API 37
  ignoring the landscape lock on a large screen (so the display is overridden to 1872x1404).
  Sam: comprehensive performance + battery ticket -> T20 expanded.
- 2026-11-14 — Tick 33: the T34 refactor is committed (`docs/architecture.md`, `stage/` host,
  features as components; 131 core + 316 app tests pass at HEAD; the agent's final report and its
  Nomad capture comparison are still pending). With emulators available, dispatched four component
  tasks in parallel, each on its own emulator port: T09 gem menu (5562), T15 KO overlay with KO quips
  (5564), T37+T38 small numerals and split peeks (5566), T28c effects promotion and wiring (5568).
  Shared file `play/PlayScreen.kt` takes one registration line per feature — edit and commit it
  immediately. Nomad checks by the coordinator afterwards. T40 (icons) still running.
- 2026-11-14 — Tick 34: T40 reviewed (before/after and halo sheets eyeballed; 6 agent commits).
  Awaiting Sam's approval before promotion (T41). Running: T09, T15, T37+T38, T28c on emulators;
  T34's final report still pending.
- 2026-11-14 — Tick 35: T34 reported and accepted as done. Agent's proof: 19 of 19 Nomad captures
  byte-identical before/after (`design/captures/t34/`, all pass the 1-bit check — re-checked by the
  coordinator), an old-vs-new equivalence test over every dither pixel before the old paths were
  deleted, 131 core + 316 app tests. Coordinator: tests re-run green at HEAD; emulator capture matched.
  Cost: ~6–15% slower `onDraw` in release (tens of microseconds) — accepted, noted on T20. `widgets/`
  PressTracker/HitRegion not adopted by the host (needs range cancel, blocking/shape entries, live
  feedback rects). The Nomad is free again.
- 2026-11-14 — Tick 36: T37 (build) + T38 reported with 6 commits; emulator captures eyeballed
  (`design/captures/t37-*.png`, `t38-peeks-split.png`): bold small numerals at 1x everywhere, readout
  6 px above the number, target tile = caps quip / dotted rule / heart + life, peeks in both bottom
  corners with `+N` overflow and inverted poison >= 8. Left in `review`: the tree still has three
  siblings mid-flight (two of their tests red), so the coordinator's full test run and the Nomad
  legibility check wait until they land. Cross-agent notes sent: KO winner caption must not paint
  over the peek corners; menu layout tests must use the new readout slot.
- 2026-11-14 — Tick 37: T09 reported with 4 commits; emulator captures eyeballed
  (`design/captures/t09-gem-*.png`): gem with labelled UNDO/REDO facets, DICE and CLEAN icon-only on
  the divider tips, chunky close X, two-faced reset confirm, `eink/ScreenCleaner`. Left in `review`
  until the tree settles (one sibling test red) and the Nomad check. Destinations plug in via
  `menu.destinations.register(MenuItem.X, overlay)` — dispatched T12 settings (emulator 5562), T16
  dice/coin/first player (5566), T18 history (5570). Still running: T15, T28c.
- 2026-11-14 — Tick 38: T15 reported with 7 commits; emulator captures eyeballed
  (`design/captures/t15-*.png`): crosshatch dead tile, tombstone cut-out, cause chip for poison /
  commander damage, life total as a paper cut-out, quip on a paper plate, two-tap revive, winner
  caption between the peek corners. Decision: a +/- hold-repeat stops at the KO (effects agent told
  to update its test). Left in `review` for the Nomad check. Coordinator's visual notes: the centre
  diamond loses its outline against adjacent dead tiles' crosshatch and the dividers disappear
  between two dead tiles; the hollow ringed life numeral reads a little odd. Still running: T28c,
  T12, T16, T18.
- 2026-11-14 — Sam on the KO/winner tiles: the quip can replace the life total; explore classic
  8/16-bit era game-quote flavour. -> T42 dispatched (SPEC Tone updated: pastiche, not verbatim).
- 2026-11-14 — Tick 39: T28c reported with 8 commits (15 effects shipped, policy table, triggers on
  group commit, KO and winner flourishes, debug gallery; `b5928a2` does not pass tests on its own).
  Coordinator built HEAD `56b9b31` in a clean worktree: 131 core + 400 app tests pass. Installed on
  the **Nomad** with fresh data: three taps 40 -> 37 with the bold 1x readout, menu opens, UNDO
  restores 40 and enables REDO, drawer counters show as corner peeks; all five captures pass the
  1-bit check. On that basis T09, T15, T28c, T37, T38 are marked done (T15's look is being reworked in
  T42; effect timing/ghosting still needs filming on the panel). Sam: crosshatch does not belong in the
  dither language -> light dither for dead tiles (sent to the T42 agent). Running: T42, T12, T16, T18.
- 2026-11-14 — Sam: drawer counters need a mechanism to go down / reset -> T43 dispatched.
- 2026-11-14 — Sam on the heavier icons: keep `close` (A), the chevrons, `monarch` and `night`;
  everything else was better before; the checkmark should be bold but the proposal was wrong; halo 1
  is good. Coordinator promoted those seven into `design/icons/`, redrew `check` as a clean 3-px bold
  tick, left the other 21 candidates unpromoted (they stay in `candidates/heavy/` for reference), and
  updated T41 to the 1 px halo.
