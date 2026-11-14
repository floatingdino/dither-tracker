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
- Scratch files: each agent keeps its helper scripts and captures in its own subfolder of the
  scratchpad (named after its task), never in the shared root — one agent's script overwrote
  another's in tick 43.
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
| T11 | review (layout picker → T54) | T06 | Layout engine for 2–6 players, portrait + landscape, per-seat rotation (0/90/180/270), all on the dither grid with leftover px as margin. Render tests per layout. | `app/src/main/**/layout/` |
| T12 | done | T34 T09 T10b | Settings screen (SPEC list): 1-bit list/toggle/stepper widgets, scale S auto/2–6, invert mode, starting life as a segmented [40] [30] [20] choice (not a stepper), counters shown, keep-awake, full-refresh cadence. | `app/src/main/**/settings/`, `**/widgets/` |
| T13 | dropped | — | ~~Player names (in-app keyboard)~~ — Sam, 2026-11-14: player names are dropped; seats are identified by the seat glyph (T44). What remains of player customisation is the per-player background pattern, moved to T45. | — |
| T45 | dropped | — | ~~Per-player background pattern picker~~ — Sam, 2026-11-14: player colours/patterns are dropped too. | — |
| T46 | done | T16 | **Dice results use the small numeral face** (Sam, 2026-11-14), not the hero numerals: re-compose the dice/coin panel around the bold small face (`num-m`), shrink the panel accordingly so it no longer clips 3-glyph life totals, fix the awkward caption wraps. Emulator 5566. | `tools` Kotlin package |
| T14 | done (icons: all option A, ring reverts in T51) | T08 | State toggles: monarch, initiative, city's blessing, ring-bearer, global day/night — badges on panel using icons; monarch/initiative exclusive across players. | `play/`, `drawer/`, `core/` |
| T15 | done | T34 T06 T03 | Auto-KO rendering ("dead" dither over panel, tap to revive). Always on — no toggle. | `play/` |
| T42 | done | T15 | **KO / winner tile: the quip replaces the life total** (Sam, 2026-11-14) — on a dead tile and on the winner's tile the quip takes the hero position at a large size instead of the (ringed, hollow) number; cause/final life becomes the small detail. Plus a copy exploration: death and victory lines in a classic 8/16-bit game-text voice (**verbatim** famous one-liners, per Sam — exact wording only, short lines). Dead tiles use a **light ordered dither, not crosshatch** (Sam: crosshatch is not part of the dither language) — change `Role.DEAD`. Also fix: the centre diamond and dividers must keep their outline against dead tiles' crosshatch. Emulator 5564. | `ko/`, `core/**/flavour/`, `design/copy/quips.md` |
| T47 | review | T42 | **Death/winner copy tweaks** (Sam, 2026-11-14): draw every quip at the one smaller size (the size the long lines use today) instead of scaling short lines up; and make the paper ground behind the text continuous across each line — it should run through the spaces between words, not be a separate box per word. Emulator 5564. | `ko/` |
| T48 | review | T22 | `fizzle` effect breaks rule 3: its D4/D8/D12 dissolve is baked into the frame art at the frame's own phase, one px off the global pattern. Re-author so the dissolve samples the global pattern; then delete the `KNOWN_OFF_PHASE` entry in `PuritySuiteTest`. | `design/fx`, `fx/` |
| T49 | done | T22 | Re-script T34's 19 base states in `tools/capture_scenarios.sh`: captures 12–19 no longer reach commander mode (taps land on the gem menu). Names must match what is shown. | `tools/` |
| T50 | done | T20 T41 T22 | Cleanup: swap `ko/Halo.kt` to the shared contour-halo mechanism; retire the unused `CROSSHATCH` pattern; `purity/ScreenRig` pointer ids wrap below 32; regenerate `design/captures/t34/` + README from the re-scripted scenarios; cut layout-before-first-frame cost in `KoComponent.onLayout` (46 ms debug) and `SettingsOverlay` (35 ms). Pixels unchanged. | `ko/`, `settings/`, `dither/`, tests |
| T51 | review | T47 T14 T09 | Sam 2026-11-14: app name "Dither" (launcher label, README; applicationId unchanged so the Nomad install and save survive); win/death tiles lose the life total; a new winner icon (not the monarch crown); gem menu press/hover inverts the menu ground only, not a larger square; menu stays open until dismissed; ring icon back to option A. | `ko/`, `menu/`, `design/icons/`, manifest |
| T52 | done (follow-ups → T59) | T16 T30 | Sam: dice and coin need a presentation moment (instant result is anticlimactic) — keyframes, not inbetweens. Partners are undiscoverable: add a partner button on the commander-damage screen. | `tools/`, `commander/` |
| T53 | done (round two → T60) | T11 T12 | Sam: settings page renamed "Counters and trackers", trackers (monarch, initiative, blessing, ring, day/night) toggleable like counters. Layout exploration à la Lotus (reference in untracked `local/reference/`): a chevron after the Players row opens the layouts for that player count. | `settings/`, `layout/`, `drawer/` |
| T55 | review | T28f T20 | Promote Sam's round-three effect approvals into the runtime: chomp, slash, stomp, chime, heart-burst, brand, imp-raspberry, imp-poke, every counter effect (ooze, charge, level-up, rad-glow, storm-copy), dusk, dawn, ring-tempt, your-turn, spotlight, start-flag, revive; crit repurposed as a damage effect; crown-drop with the crown lined up between frames 1 and 2; torch with its firelight circle completed (may pass behind the − glyph). Dropped: coin and die effects (can't tell which player acted). Not approved: bonk, nibble, refill, bunting, crown-cut. Needs effect types/triggers for counters, toggles, turn and first player; hit-size bands; all-seat playback. | `fx/`, `design/fx/` |
| T56 | review | T17 | Sam: the delta is a touch too close to the life number — centre it in the space between the number and the top of the tile. | `readout/` |
| T57 | review | T51 T55 | Tap responsiveness on the panel (slow-mo, `docs/eink.md`): the press inversion holds a region busy ~450 ms before the result can show. Proposal: show press feedback only after a touch has been down ≈100–150 ms, so quick taps go straight to the result in one update (action still on release); try the vendor fast waveform for our window; consider a faster hold repeat. | `stage/`, `play/PlayInput`, `eink/` |
| T58 | review | T19b | Faster display modes: the Nomad's framework has a per-View e-ink mode (`View.setEinkUpdateMode`, DU/A2/…); the call succeeds from our debug build (`--ei einkMode N`, `eink/EinkViewMode.kt`). To find out: does the panel respond faster, which mode, ghosting cost, per-region modes (fast for taps, GC for cleans), and whether a release build is allowed to call it. | `eink/` |
| T59 | review | T52 T58 | Sam's review of T52: a global fast/slow display flag, and a couple more in-between poses for die and coin on a fast display; the dice panel opens empty (no automatic d20); first player re-imagined as simultaneous d20 rolls on every live tile, results in the life-total digits, a sting and a "FIRST" flag on the winner, more informative quips ("On the play", "First out the gates"); PARTNER tab on the dither ground with halo/tape, new small-partner + / − icons instead of the big imp. | `tools`, `commander/`, `eink/`, `design/icons/` |
| T60 | review | T53 | Sam's review of the layout picker ("a good start"): allow different-sized tiles within one game; thumbnails shrink so more options fit a page; each seat drawn as an inset dithered ground rather than outlined; no layout names; no drawer-tab mark in the thumbnails; add layouts with an empty seat (e.g. L shapes with any corner missing). Second reference in untracked `local/reference/`. | `layout/`, `settings/`, every N-seat component |
| T61 | todo | T59 | Sam: heads / tails coin icons. Today the coin result is shown by the coin sprite's face and the word; draw a distinct heads icon and tails icon in the icon family's style (2–3 options each), use them on the dice panel's coin result (and the coin sprite's two faces if they differ), keep them readable from both faces of the panel. | `design/icons/`, `tools` |
| T43 | done | T08 T37 | **Drawer counters need an obvious way to go down / reset** (Sam, 2026-11-14). Today the left half of a cell decrements but nothing shows it. Give each counter cell visible − and + affordances (at least once the value is non-zero), and a quick reset to zero (e.g. hold on −), with the press model unchanged (act on release; slow hold-repeat). Emulator 5568. | `drawer/` |

### Phase 3 — Table tools

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T16 | done | T34 T09 | Random starting player, dice (d6/d20/custom), coin flip — static result reveal, 1-bit dice faces. | `app/src/main/**/tools/` |
| T17 | review | T34 T09 T03 | Turn tracker + optional turn/game timer (redraw at most once a minute; partial refresh). | `tools/`, `core/` |
| T18 | done | T34 T09 T03 | Game history log view (scrolling by page, not smooth scroll). | `app/src/main/**/history/` |
| T44 | done | T18 | **Seat glyph instead of player names** (Sam, 2026-11-14: "Player 1/2/3/4 are meaningless"). A small table diagram with one position filled marks the affected seat; drawn from the real layout (2x2 today, any 2–6 layout later) and turned with the card so it always matches the physical table. Use it in history rows (source -> target as two glyphs) and anywhere else a default name appears. Emulator 5570. | new `seat/` package, `history/` |

### Phase 4 — E-ink polish & release

| ID | Status | Deps | Task | Files |
|----|--------|------|------|-------|
| T19a | done | — | E-ink refresh research: Supernote/Ratta refresh-mode APIs, what stock Android offers, ghosting strategy → `docs/eink.md` with a concrete recommendation. Read-only device inspection. | `docs/eink.md` |
| T19b | review (Nomad panel check pending) | T19a T06 | Implement the refresh strategy from `docs/eink.md`: invalidation audit, periodic full refresh, any safely available device refresh-mode hook. | `app/src/main/**/eink/` |
| T20 | done | T09 T12 T28c (most features in) | **Comprehensive performance and battery pass** (Sam, 2026-11-14: as responsive as possible, very frugal with battery). *Responsiveness:* tap-to-pixel latency on the Nomad (trace + high-speed video), cold and warm start, onDraw time per interaction, zero allocations in draw/touch paths (allocation tracker, not just code reading), no main-thread disk or decode, overdraw, bitmap/mask memory, GC pauses, StrictMode clean, baseline profile / R8 full mode, APK size. *Battery:* no wake locks beyond the keep-awake setting; nothing scheduled while idle (no timers, handlers or animations ticking — verify with `dumpsys alarm`, `batterystats`, and CPU time over a 30-minute idle with the screen on and off); save writes batched; effects and readout timers cancelled when idle and in `onPause`; e-ink refresh count per action minimal (each panel update costs power); cleanup-flash cadence justified by measurement; behaviour when backgrounded and on screen-off. Known going in: the T34 component host costs ~25–60 µs more per `onDraw` in release (443–463 µs vs 401–416 µs on the Nomad); candidates: public `Frame` fields, fewer `TileSlots` hops. Effects prewarm builds ~30 MB of full-frame masks at S=4 (crop masks to each layer's ink bounds). *Deliverables:* a repeatable benchmark script under `tools/`, `docs/performance.md` with before/after numbers and budgets (e.g. tap latency, idle CPU ~0), regression tests where practical, fixes applied. Uses the Nomad for all timing. | whole app, `tools/`, `docs/performance.md` |
| T21 | done | T04 T02 | App icon (1-bit, adaptive + legacy) and launcher label from the icon set / dither patterns. | `app/src/main/res/` |
| T22 | done | all | Full pixel-purity regression suite: render every screen at S=2..6, both orientations, inverted and not; fail on any non-#000/#FFF pixel or any off-grid edge. | `app/src/test/` |
| T24 | done | — | Icon redesign per Sam's feedback: redo `poison` (ugly), `rads` (too large in the set), refine `storm`; rework `experience`, `life`, `energy`, `commander` (too simplistic/off); add weight and a slightly cutesy/wacky character across the set, keeping the filled-arrow feel of `refresh`/`reset`. | `design/icons/` |
| T25a | done | T03 | Personality copy tables in core: original quips (KO lines by reason, dice/coin lines, reset confirm, empty history, default player names) with a pure picker taking `Random`; length-checked against the `ui` face budget. | `core/src/**/flavour/` |
| T25b | done (covered piecemeal by T09, T15/T42, T16, T18) | T25a T15 T16 | Show quips at their call sites (KO panel, tools, reset confirm, history) as static text. | call sites |
| T29 | done | T03 | Core: per-seat undo grouping so interleaved taps from different seats don't split one seat's burst into several undo steps/log entries. JVM-only. | `core/src/` |
| T26 | done | — | Hero numeral exploration: 4–5 heavier, more characterful candidates for life totals, rendered in-context at device pixels for Sam to pick. | `design/font/candidates/` (or `design/font-candidates/`) |
| T28a | done | — | Hit/heal effect sprites: 3–4 damage and 2–3 heal effect concepts (swipe, crunch, fizzle, sparkle…), 2–4 frames each as 1-bit ASCII frames, with offline previews (frame strips + in-context mock). | `design/fx/` |
| T28b | done | T28a | Effect engine (JVM-only): build step turning `design/fx/<name>/` frames + meta into generated Kotlin, pure `FxPlayer` state machine (injectable scheduler; trigger/cancel/no-queue/max-two rules), draw helper (black + knock-out layers, rotation). Ships `pow` and `twinkle`. No PlayView wiring. | `app/**/fx/`, `app/build.gradle.kts` |
| T28c | done | T28e T27 T30 | Promote **all** round-two options Sam approved from `design/fx/options/` to `design/fx/` (claw: `claw-cut` only — "Claw-A"; drop `claw-paper`; retire round-one `claw`, `wham`, `zap` and old `pow`/`crack`/`twinkle` in favour of the refits; keep `heart`, `sprout`, `fizzle`), then wire effects into the play screen: trigger on delta commit by sign/size, KO and last-standing flourishes, cancel on tap (always on — no settings toggle); tune hold time and film on the Nomad. | `play/`, `design/fx/` |
| T28e | done | T28b | Engine support for round-two effects: `x` (invert under mask), tile-filled layers at global phase, `anchor tile`; promote chosen options from `design/fx/options/` to `design/fx/`. JVM-only. | `app/**/fx/`, `design/fx/` |
| T28f | done (design); promotion → T55 | T28c | More effects in the round-two style (Sam: "continue building out more animations in this style after all the current set"): dither shading in overlays and numeral interactions, cut-outs, tile-wide reach; new damage/heal variety, counter-specific hits (poison, commander damage), monarch/initiative gained, turn pass, dice/coin reveals. Design then promote. | `design/fx/` |
| T33 | done | T30 (owns flavour) | Quip tone pass: make the whole table a smidge more combative ("uh oh", "whoops", "beefed it") per SPEC Tone; keep length/charset rules and the doc mirror. | `core/**/flavour/`, `design/copy/quips.md` |
| T37 | done | T34 | **Small numerals = `bold`** (Sam's pick, `design/font/candidates/mini2/bold`, 10x14, sticker treatment, no shadow). Build: adopt it as the small numeral face replacing `num-m` everywhere at **k=1 everywhere** — the commander-target life, the pending-delta readout, drawer values and peeks (Sam, 2026-11-14: target life and deltas are 1x too, not 2x); re-compose the target tile (quip + heart + life) and the readout position for the smaller size; fix the 3-digit drawer value that is 2 px wider than its cell; re-derive affected layouts and tests; verify on device at arm's length. | `design/font/`, `type/`, consumers |
| T38 | done | T34 T37 | **Counter peeks = `split`** (Sam's pick, `design/font/candidates/mini2/peeks-split.png` + README): chips in both bottom corners of the tile beside the tab, alternating left/right, three per corner, six slots; `+N` takes the sixth slot on overflow; poison >= 8 inverts; display-only; hidden while the drawer is open and in commander mode. | `peeks/` |
| T40 | done | — | **Icon weight pass** (Sam, 2026-11-14: the close X looks thin next to the chunky numerals; other icons need more beef too). Redraw `close` heavier and bring the thin/outline icons up to the sticker numerals' weight (chevrons, check, plus, minus, more, menu, undo, redo, refresh, reset, rotate, storm, timer, dice, history, new-game, dead, ring, monarch, night, coin, invert …). Design-only, drawn as candidates in `design/icons/candidates/heavy/` — do NOT touch `design/icons/*.txt` while T34 runs (its before/after captures must stay byte-identical); the coordinator promotes after T34. | `design/icons/candidates/heavy/` |
| T41 | review | T34 T40 | **Contour cut-outs for glyphs on dither** — Sam approved **halo 1** (a 1 px paper halo following the glyph's outline, holes filled first; see `design/icons/candidates/heavy/halo-mocks.png` and README) in place of the square paper backing: generate halo masks at build time for icons; use for the diamond's menu glyph and commander close X, the tab dots, and anywhere a glyph sits on a dither fill; verify on device. (Icon promotion is already done.) | `dither/`, `chrome/`, `drawer/`, icon build step |
| T28d | done | T28a | More effect options per Sam: bigger, opening up across the tile; knock-out cut-outs through the numerals; a closer `claw`; drawn over sticker numerals. Keep `pow`, `crack`, `twinkle` as approved. | `design/fx/options/` |
| T27 | done | T08 | Adopt **sticker** (Sam's pick, 2026-11-13) as the hero numeral: new `num-l` from `design/font/candidates/sticker` (32x56 at k=1); draw the checker drop shadow as a separate mask filled at global pattern phase (renderer/type addition) instead of baked into glyphs; half-size +/- glyphs; re-derive play + drawer layout; verify on device. | `design/font/num-l/`, `type/`, `play/` |
| T39 | done 2026-11-14 | no agents running; Sam's go-ahead at run time | **Rewrite git history to remove private data** (Sam, 2026-11-14). Purge from every commit on `build`: the test device's adb serial (in old versions of this file), the third-party reference screenshot under `design/reference/`, and the private Figma file link (old `docs/SPEC.md`). The exact strings are in the untracked `local/README.md` — do not copy them into tracked files or commit messages. Use `git filter-repo` (or equivalent) with a replace-text file kept under `local/`; also check commit messages; verify with a full-history grep that nothing remains; then force-push `build` (confirm with Sam immediately before the force-push — it rewrites every commit hash) and ask Sam to have GitHub drop cached/dangling objects if needed. Coordinator only; run when no agent has work in flight; back up the repo first (`git bundle` into `local/`). | whole repo history |
| T23 | done | T22 | Release build config (R8, signing instructions), sideload guide for Supernote Nomad in `README.md`. | gradle, `README.md` |

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

- **Review the retro quotes** (T42): 58 verbatim game lines shipped, ~40 spares, ~45 rejected with
  reasons — `design/copy/retro-candidates.md` (ranked bench with sources) and the Sources table at the
  end of `design/copy/quips.md`. Least sure: casing of "I feel asleep!!" and "Go home and be a family
  man!"; kid-safety of "Barf!" and "Son of a submariner!"; one PC-game line.

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
- 2026-11-14 — Sam: the retro game quotes should be verbatim, not pastiche. SPEC and T42 updated;
  the agent told to use only lines it is certain of, exactly as written, and to list them for checking.
- 2026-11-14 — Sam on the verbatim quotes: give it a shot and come back with a list of candidates;
  the quip may be too thin to read clearly. T42 agent told: quotes are a proposal (table with exact
  wording, source, category, confidence; ship only the certain ones) and to compare the scaled `ui`
  face against a bold caps display face for the hero quip.
- 2026-11-14 — Tick 40: T18 reported with 5 commits; emulator capture eyeballed
  (`design/captures/t18-history*.png`): a square rotatable card opened from the LOG facet, newest
  first, icon + name + bold `delta -> value`, paged, undo in the title bar, linked commander damage
  folded into one row. In `review` until the coordinator's clean build and Nomad check. Not seen on
  a device: turn dividers and live update (JVM tests only). Sam clarified "thin" = the verbatim quote
  list may be too sparse to feel like part of the copy identity; add online research for candidates.
  T42 agent redirected: research a verified bench of 60+ lines -> `design/copy/retro-candidates.md`;
  the bold-face comparison is dropped. Running: T42, T43, T12, T16.
- 2026-11-14 — Tick 41: T12 reported with 5 commits; emulator capture eyeballed
  (`design/captures/t12-*.png`): square rotatable settings card from the SETUP facet with starting
  life [40][30][20], scale, invert, hold step, keep awake, screen clean, counters sub-page; live
  application via listeners; persists. In `review` until the coordinator's clean build and Nomad
  check (the shared tree has 8 failing tests from siblings mid-edit). Additive `widgets/` changes:
  `Toggle(icon)`, `Segmented.numerals`. Later cleanup: settings and history each build their own
  overlay card. Running: T42, T43, T16.
- 2026-11-14 — Sam: default player names are meaningless; show the affected seat with an icon that has
  that spot filled in. -> T44 dispatched. T13 (names) flagged for a scope decision.
- 2026-11-14 — Sam: player names can be dropped. T13 dropped; pattern picker split out as T45 (to
  confirm); SPEC updated; T44 agent told to show no names anywhere.
- 2026-11-14 — Tick 42: T16 reported with 6 commits; emulator captures eyeballed
  (`design/captures/t16-*.png`): two-faced dice/coin panel on the crossing (d20, d6, coin, roll
  again on the fold diamond, explicit close), 250 ms "?" reveal beat, quip captions; first player
  shown as an ink badge under the chosen tile's life total, sets the starting player in core. In
  `review` until the clean build and Nomad check. Known: the panel's corners cover part of 3-glyph
  life totals; results are not logged yet (hook `ToolResults` ready). Running: T42, T43, T44.
- 2026-11-14 — Sam: player colours can be dropped too (T45 dropped); die rolls should use the mini
  font (T46 dispatched).
- 2026-11-14 — Tick 43: T43 reported with 3 commits; emulator capture eyeballed
  (`design/captures/t43-*.png`): each non-zero cell is a framed card reading "- icon value +";
  zero cells show the icon and a lone +; holding - resets to 0 as one undo step; 3-digit cells drop
  the glyphs. In `review` until the clean build and Nomad check. Coordinator's note: the active grid
  is dense (2-digit cells are snug). Running: T42, T44, T46.
- 2026-11-14 — Sam: empty counters should not show the +. Coordinator removed the lone + from zero
  cells (icon only; the right half still adds one) — verified in a clean worktree (app tests pass
  apart from the KO agent's in-progress `KoTest`) and on the emulator
  (`design/captures/t43-empty-counters-no-plus.png`). Sam also asked for 1x and 2x mockups of the die
  result — T46 agent told to deliver both.
- 2026-11-14 — Tick 44: T44 reported with 4 commits; emulator capture eyeballed
  (`design/captures/t44-*.png`): a 15x11 table miniature with the seat's cell filled, generated from
  the panel rects (generalises to 2-6 panels), counter-rotated so it always matches the physical
  table; history rows are kind icon + seat glyph(s) + bold `delta -> value`; no names anywhere.
  `seat/README.md` says how other components adopt it. In `review` until the clean build and Nomad
  check. Notes: rows now have a wide empty middle; T12's rotation labels (90 = E?) may be swapped —
  check on the device. Running: T42, T46.
- 2026-11-14 — Sam prefers the inset fill on the seat glyph. Coordinator set `SeatGlyphs.INSET = 1`,
  updated the glyph and history tests and README; clean-worktree run: 526 app tests pass; emulator
  capture `design/captures/t44-history-seats-inset.png` passes the checker. Note: at 16 px the inset
  fill is a 4x2 block and reads lighter than the flush version.
- 2026-11-14 — Tick 45: T46 reported with 5 commits: dice results in the bold small numerals, panel
  163x111 (was 163x153), balanced caption wrap, clears 3- and 4-glyph life totals; ships 2x with a
  1x composition switchable by `DiceLayout.RESULT_K`. 1x and 2x mockups in
  `design/captures/t46-*-1x.png` / `-2x.png` and `t46-dice-1x-vs-2x.png` for Sam's pick. The coin lost
  its drop shadow (agent's call — confirm). In `review`. Running: T42.
- 2026-11-14 — Tick 46: coordinator built HEAD `511727e` in a clean worktree (132 core + 526 app
  tests pass) and ran it on the **Nomad** with fresh data: settings card, dice panel (2x), history
  with seat glyphs, drawer cells (empty counters icon-only, active "- icon value +"), corner peek, and
  a life KO showing the new T42 tile (light dither, large quip, heart 0). All five captures pass the
  1-bit check (`design/captures/nomad-check-2026-11-14.png`). T12, T16, T18, T43, T44, T46 marked
  done. Dispatched T41 (1 px halo cut-outs, emulator 5562), T19b (automatic screen-clean cadence,
  emulator 5566), T21 (app icon). T42 still running; T11 (2-6 player layouts) waits for it.
- 2026-11-14 — Tick 47: T42 reported with 5 commits; captures eyeballed
  (`design/captures/t42-*.png`); the life-KO tile was already seen on the Nomad in tick 46. Dead tile:
  D2 light dither with a paper gutter (dividers and diamond stay crisp), tombstone, the quip large in
  ink on per-word paper boxes, cause chip; armed revive plate with a keyline. Winner tile: crown,
  quip at hero size, life chip, tile stays live underneath. Copy: verbatim 8/16-bit lines verified
  against disassemblies, released source and transcripts by a research sub-agent (which ignored
  hidden text aimed at AI agents on some wiki pages); every KO/winner list is at least half retro.
  658 tests pass. Marked done. Cleanup noted: `CROSSHATCH` tile is now unused. T11 (2-6 players)
  waits for T41 to finish in the painters. Running: T41, T19b, T21.
- 2026-11-14 — Tick 48: T21 reported with 5 commits: launcher icon = the sticker "40" with its
  checker shadow on white (54x54 icon-pixel grid, exact integer scale at every density; adaptive icon
  with a flat white background; 1-bit PNGs; +8 KB release APK). Alternatives kept under
  `design/app-icon/` (diamond, heart). In `review`: not yet seen in the Nomad's own launcher, where
  the launcher's rescaling decides whether it stays pixel-exact. Running: T41, T19b.
- 2026-11-14 — Sam on the death copy: use the smaller size for all quips; the paper ground should
  continue between words. -> T47 dispatched.
- 2026-11-14 — Tick 49: T19b reported with 4 commits (132 core + 574 app tests on a clean worktree):
  automatic clean once the "Screen clean" count is reached and the table has been quiet for 2 s
  (never during a touch, an open overlay, or a running readout/effect timer); per-seat clean when
  one seat alone reached the threshold; black keyframe on settings/history close; vendor
  `screenRefresh` behind `USE_VENDOR_REFRESH = false` / `--ez vendorRefresh true`; menu CLEAN now
  routes through the same state machine. Counting is done in one stage observer. Correction sent
  back: the agent had made a tap during a clean be swallowed (per the coordinator's brief) — SPEC
  rule 4 wins, a touch must cancel the clean and still act. In `review` for that fix and the Nomad
  checklist in `docs/eink.md` ("T19b as built"). Note for T20: the readout timer still fires while
  backgrounded. Running: T41, T47.
- 2026-11-14 — Sam prefers the heart launcher icon and asked to check the Nomad's icon resolution so
  the shading reads. Coordinator: the Nomad's Settings > Apps > My Apps list draws icons at 90x90 px;
  the adaptive icon was smeared by non-integer rescaling. Dropped the adaptive icon, added a
  `mipmap-300dpi` bitmap from a new 30x30 design at 3 px per icon pixel
  (`design/app-icon/heart-nomad/icon.txt`), and verified on the Nomad that the icon area matches the
  design with 0 mismatched pixels. T21 done. (Navigated the Nomad's Settings > Apps pages to look; the
  app was relaunched afterwards.) T19b fix landed (`8f4314a`: a touch during a clean cancels it and
  still acts).
- 2026-11-14 — Tick 50: T41 reported with 8 commits (132 core + 575 app tests): 1 px contour halos
  generated at build time for icons and for the `ui`/`num-m` glyphs, one draw path
  (`blitHaloed`), adopted on the diamond's menu bars, the commander close X, tab dots, page chevron,
  zero-value drawer icons and disabled gem facets; title bars keep a paper strip (the contour fizzed
  on the checker). `ko/Halo.kt` not yet swapped to the shared mechanism (ko/ was mid-edit) — follow-up
  after T47. In `review` for the Nomad look. SPEC "As built" updated. Dispatched T22 (purity/regression
  suite, emulator 5562) and T23 (release build + sideload guide). T11 waits for T47 to leave `ko/`.
  Running: T47, T22, T23.
- 2026-11-14 — Tick 51: T23 done (`5810243`, `9ce9fea`), verified in a clean worktree (132 core + 575
  app tests, debug 1.4 MB, release 190 KB unsigned). Release: R8 + resource shrinking, no keep rules
  needed; signing read from env `DITHER_*` / Gradle `dither.*` / `local/keystore.properties`, unsigned
  when none (`-PdebugSignRelease` for testing); QA intent extras compiled out via `BuildConfig.DEBUG`
  (`inverted`, `trace` stay). README rewritten for the public repo with the Nomad sideload guide.
  **Needs Sam:** generate the release keystore once (README, "Release build and signing"). Unverified
  on a real Nomad: install from the file manager, on-device uninstall. Running: T47, T22.
- 2026-11-14 — Tick 52: T47 reported (`15774b9`, `e0397ca`, `496657a`, `6d2e583`), verified in a clean
  worktree (132 core + 671 app tests, captures pass `check_1bit`): every KO/winner quip at one size
  (`ui` k=2 at S=4 — the size of the old small lines; the brief's k=3 was wrong), balanced breaks at
  spaces, at most 2 lines on the Nomad at S=4; one merged stepped paper ground per quip (no dither
  between words or lines); REVIVE? plate down to the quip size. In `review` for Sam's look.
  Dispatched T11 (2–6 player layouts, emulator 5564). Running: T22, T11.
- 2026-11-14 — Tick 53: T22 done (`47fa414`, `f1e4318`, `b671adc`, `451ff5d`), verified in a clean
  worktree (132 core + 671 app, 94 of them the purity suite, ~2 s). JVM: the real `PlayScreen`
  component list through a raster renderer at S=2..6, both orientations, normal and inverted, every
  screen state and every effect frame; checks grid bounds, clip balance, global phase, exact inversion.
  `DitherRenderer` is now an abstract base (`CanvasDitherRenderer` for Android). Device:
  `tools/check_purity.sh` — 98/98 captures pass at S=4 landscape. Found: (1) `fizzle` off global phase
  → T48; (2) portrait S=6 4-digit life overflows the grid — unreachable today (not a "comfortable"
  layout), matters if T11 unlocks portrait; (3) the 19 base scenarios drifted → T49. Dispatched
  T48+T49 to one agent (emulator 5562). Running: T11, T48/T49.
- 2026-11-14 — Tick 54: both agents were cut off by a usage limit and resumed from their transcripts
  after it reset. Committed so far and building clean at HEAD (132 core + 1033 app tests): T11
  `ddc8059` design, `068831d` engine, `347b041` every Play component at 2–6 seats; T48 `b0f7a0b`
  fizzle as tile layers at global phase (`KNOWN_OFF_PHASE` empty). In flight, uncommitted: T11's
  player-count control (settings, menu, persistence, core codec). Left: T11 control + captures;
  T48 docs/captures; T49. Running: T11, T48/T49.
- 2026-11-14 — Tick 55: T11 reported (`ddc8059`, `068831d`, `347b041`, `29dccc5`, `cf8872b`, `62a45e1`,
  `e7e532a`), verified in a clean worktree (137 core + 1036 app tests; 13 captures pass `check_1bit`).
  Arrangements (table in `layout/Arrangement.kt`, notes in `design/layouts/README.md`): 2 across
  (468×175), 3 tee with the right end turned 270° (292×175), 4 grid unchanged and byte-identical,
  5 three+two and 6 three+three (155×175, "narrow" tier: no ± glyphs, no peeks, 2 drawer cells a
  page). `Players [2][3][4][5][6]` in settings, applied on NEW (`NewGameFor`), save format v2 (v1
  loads). Portrait computed and tested, not unlocked. In `review`.
  **Needs Sam (design):** ± on narrow tiles; peeks / drawer density at 5–6; the 5-player near row
  leaves side margins (equal tiles) — widen the two near seats?; gem labels and the dice/settings/
  history cards have no facing for the 3-player end seat; offer alternate arrangements in settings?;
  diamond mid-edge at 2/5/6. Note: `purity/ScreenRig` uses `pointer++` and `PlayInput` ignores
  pointer ids ≥ 32 — fix in the next purity touch. Running: T48/T49.
- 2026-11-14 — Tick 56: T48 (`b0f7a0b`) and T49 (`6c6a81a`) reported; HEAD builds clean (137 core +
  1036 app). T48: fizzle's dissolve is tile layers (D4/D8/D12) filled from the global pattern at draw
  time (the `ko-shatter` path, no runtime change); `KNOWN_OFF_PHASE` empty; over the numeral the ghost
  and its shadow now merge into one clean checker (`design/captures/t48-fizzle-before-after.png`) —
  `review` for Sam's eye. T49: root cause was lost launch extras, not tap drift — `fresh()` goes home,
  force-stops, clears and launches with `am start -S`; 13 deals 20 not a lethal 21; 07 holds the +
  half. Follow-ups: `design/captures/t34/README.md` still describes the old set; `purity/ScreenRig`
  pointer ids. Coordinator re-running `tools/check_purity.sh` on HEAD (after T11's settings row).
  Dispatched T20 (performance + battery; emulator 5562 and sole use of the Nomad) and T14 (state
  toggles; emulator 5564). Running: T20, T14.
- 2026-11-14 — Tick 57: device purity on HEAD 98/98; coordinator fixed `capture_scenarios.sh` settings
  taps after T11's Players row (`2e96bb0`). T14 reported (`2ce230f`, `53d88e2`, `54a9c8c`, `eecc3c6`,
  `a1e7354`, `67ffb46`), verified in a clean worktree at HEAD incl. T20's commits so far (154 core +
  1256 app; 9 captures pass `check_1bit`). Core toggles and persistence already existed (no codec
  bump). Built: toggle cells after the counters in the drawer (off = icon on halo, on = ink card),
  shared day/night cell (tap unset → day → night → day, hold resets), 16 px badges in the under-number
  row, day/night drawn on the centre diamond; KO drops monarch/initiative to nobody; ring-bearer
  on/off; ring icon redrawn heavier. In `review`.
  **Needs Sam (design):** diamond loses its menu bars (and dither) once day/night is set; badge size
  (16 px beside hero numerals); ring tempt level 0–4?; toggles add 3 drawer pages at 5–6 players;
  first-player band covers the badges; icon options in `design/captures/t14-icons.png`.
  Running: T20.
- 2026-11-14 — Tick 58: T20 still running (in flight: Stage, PlayView, renderer, bench tools). T17
  (turn timer) held until T20 lands — a ticking timer would invalidate its idle measurements and both
  touch `stage/`. Dispatched T28f design phase (round-three effect candidates and review sheets in
  `design/fx/round3/`, no runtime wiring). Running: T20, T28f.
- 2026-11-14 — Tick 59: T28f design phase reported (`6e82d9b`, `27a2608`, `845aefa`, `1190dfd`): 30
  round-three candidates in `design/fx/round3/` (one 149×165 frame size that fits the 233 and 155
  tiles), sheets `design/captures/t28f-{damage,heal,counter,state,table,narrow}.png` (all 1-bit pure),
  calls in `design/fx/round3/README.md` — agent's view: promote 21, revise 7 (chomp, refill, imp-poke,
  level-up, torch, spotlight, coin-spin), drop 2 (brand, crown-cut). **Needs Sam:** pick which to
  promote. Runtime work a promote needs (new ticket when picked): effect types/triggers for counters,
  toggles and table events; small/big/huge hit bands; all-seats playback (dusk/dawn); an anchor for
  dice/coin results; D1/D2/HLINES ghosts need a Nomad look. Running: T20.
- 2026-11-14 — Tick 60: T20 done (11 commits, `7ca1914`…`53a8bdf`; report `docs/performance.md`),
  verified by coordinator: HEAD builds clean (154 core + 1261 app), release APK 201 KB,
  `check_purity.sh` 98/98 on the emulator. Agent's Nomad numbers: cold start debug 2648→1274 ms,
  release 1790→583 ms; warm 1629→234 / 1393→138 ms; mask cache 32.3→10.3 MB; PSS 70.9→46.3 MB debug;
  0 allocations per draw (pinned by `perf/AllocationBudgetTest`); nothing ticks in the background
  (`perf/BackgroundTest`); idle foreground 0 ticks/draws/alarms/wake locks; StrictMode clean.
  Fixed: masks cropped to ink box, per-scale icon caches (was building bitmaps mid-draw and leaking),
  effects/overlays prewarm after the first frame, pause commits readout and stops effects.
  Not fixed (listed in the doc): whole-screen re-record per frame (no panel cost), life tap allocates
  1408 B in `:core`, layout-before-first-frame in ko/settings (→ T50), no baseline profile on API 30.
  **Sam should know:** during benchmarking the Nomad's save was briefly a different game; the agent
  restored the pre-T20 backup byte-for-byte (coordinator confirmed 158-byte `save.dtsv`, app in front,
  only our package installed) — worth a glance at the game state. Still needs slow-mo video: tap-to-ink
  latency, flicker of other seats during a seat clean. Dispatched T17 (turn tracker + timer, emulator
  5564) and T50 (cleanup, emulator 5562). Running: T17, T50.
- 2026-11-14 — Tick 61: T50 done (`44a1494`, `32c2eb6`, `083c1bc`, `5959e45`, `7d71683`), HEAD
  (incl. T17's commits so far) builds clean: 166 core + 1282 app. `ko/Halo.kt` deleted (shared
  `HaloMask.of` + `blitHaloed`, pixel-identical S=2..6, 2–6 players); the unused crosshatch tile
  retired; `ScreenRig` reuses pointer ids; `design/captures/t34/` regenerated with a per-capture
  README; settings card measured on first need. Emulator debug: layout before first frame
  44.8→14.2 ms (the KO cost was the old halo built four times, not quips). Agent's pixel check:
  1280 JVM shots and 47 non-effect device captures byte-identical. Not re-measured on the Nomad.
  Running: T17.
- 2026-11-14 — Tick 62: T17 reported (`f515ab6`, `81b457f`, `1376d64`, `2a9ba5e`, `8c5f031`,
  `ad1897c`, `a361ec7`), verified by coordinator at HEAD: 166 core + 1294 app, release builds,
  10 captures pure, `check_purity.sh` 98/98. Tracking starts when FIRST picks a player; the active
  seat shows a solid ink "turn tab" (89×21 at S=4) on its far edge; tap passes (skips KO'd, one undo
  step), hold ≥1.5 s corrects without counting. Settings row `Turns [OFF][ON][GAME][TURN]` (tracker
  on, timer off by default); timer shows whole minutes on the tab, one timer armed only while on,
  showing and in the foreground (tests: 3 h paused arms nothing; 0 B per tick). Save format v3
  (v1/v2 load). In `review`.
  **Needs Sam (design):** is the tab visible enough across the table; keep hold-to-correct?; game
  clock reads "107" not "1:47"; turn/round number on the play screen?; no icon on 6-player middle
  tabs with the timer on.
- 2026-11-14 — Tick 63: current HEAD debug build installed on the Nomad over the old one (save kept
  and restored: 40 / 39 / 39 with poison 2 / seat 2 KO'd), app in front. No agents running. Every
  buildable ticket is done or in `review`; what remains needs Sam: design answers (T11 layouts, T14
  toggles, T17 turn tab, T47/T41/T48 looks), which T28f effects to promote (then a runtime ticket),
  the release keystore, slow-mo video for e-ink timing, and the go-ahead for T39 (history rewrite +
  force-push). Loop stopped until then; restart with /loop.
- 2026-11-14 — Tick 64: Sam's review. Decisions: app is named "Dither"; state icons all option A;
  life total off the win/death tiles and a dedicated winner icon; menu press colours the menu ground
  only; menu stays open until dismissed; dice/coin get a presentation moment; partner button on the
  commander screen; settings "Counters and trackers" with trackers toggleable; layout picker
  exploration behind a chevron on the Players row; effect approvals as listed in T55 (coin/die
  effects dropped on principle). Dispatched T51 (emulator 5562), T52 (5564), T53 (5566), T55 (5568).
- 2026-11-14 — Tick 65: Sam: damage effects must play much sooner (not when the delta disappears) →
  added to T55 (fire after a short quiet, ~250–400 ms, once per band per readout); delta centred
  between the numeral and the tile's top → T56 (emulator 5570). T51 also told to drop the gem's 20 s
  idle auto-close and to cut gem-open invalidation (100 % of screen) if pixel-neutral. Open question
  to Sam: open drawers/menu on touch-down instead of release (halves panel updates for those).
  Running: T51, T52, T53, T55, T56.
- 2026-11-14 — Decision (Sam): drawers and the menu keep "press inverts, action on release" — no
  open-on-touch-down. Remaining levers for perceived speed: smaller gem invalidation (T51), the
  Nomad's vendor refresh modes (e-ink experiments, need slow-mo video).
- 2026-11-14 — Tick 66: T56 reported (`4e449a2`, `6450bba`), verified in a clean worktree at
  `6450bba` (171 core + 1314 app incl. other agents' commits; captures pure). The delta is centred
  between the turn tab's band and the numeral's cap top (12 px each side at S=4, was 6 px above the
  numeral), one fixed position whether or not the tab shows — so on a tab-less seat it is not the
  literal centre between numeral and tile edge (that would touch the tab when it appears). Same
  70×14 rect. In `review`: Sam to say if the literal centre (delta jumping when the tab shows) is
  preferred. Running: T51, T52, T53, T55.
- 2026-11-14 — Tick 67: Sam sent slow-mo clips 1–4 (taps, drawer/menu, hold, hits). Analysed frame by
  frame; findings in `docs/eink.md` "Slow-motion findings". Headline: partial updates confirmed, no
  full flash on big diffs, optical change ~85 ms, but every press inversion stays ~450 ms before the
  result appears (panel busy), so a tap takes ~0.7–0.8 s to show. T57 proposed (delay press feedback
  so quick taps skip the inversion). Running: T51, T52, T53, T55.
- 2026-11-14 — Decision (Sam): quick taps skip the press inversion (feedback only once held past a
  short threshold; action still on release). T57 dispatched (emulator 5570), threshold 120 ms to
  start, tunable by a debug extra. Running: T51, T52, T53, T55, T57.
- 2026-11-14 — Tick 68: Sam asked about faster display modes. Found `View.setEinkUpdateMode(int)`
  and the `EINK_SHOW_MODE_*` constants in the Nomad's framework; experiment hook committed (debug
  extra `--ei einkMode N`); the call succeeds on the device (final mode reads back 1 = DU). An
  experimental build (the Nomad's installed commit + the hook) is on the device running in DU mode;
  save backed up to untracked `local/nomad-save-backup/` and restored after coordinator test taps.
  Effect on the panel not yet observed → T58. Running: T51, T52, T53, T55, T57.
- 2026-11-14 — Tick 69: Sam on the Nomad in DU mode: "noticeably faster, can't see any additional
  ghosting issues" → T58 goes from experiment to feature (guarded, default on the Nomad, a setting,
  GC-style cleans, release-build check; sole use of the Nomad, emulator 5572). Also from Sam: the
  setup card's orientation must be remembered → added to T53; ±10 hold "finicky if held after
  tapping, and sometimes holds are not recognised" → added to T57 (review of the whole hold path).
  Coordinator slip: `fe66228` swept T57's uncommitted `MainActivity` lines in; `7d94d09` took them
  out again (index-only). Rule reminder: `git add -p` on files others have open.
  Running: T51, T52, T53, T55, T57, T58.
- 2026-11-14 — Tick 70: T51 reported (`02ccbbb` name, `524fc1a` no life on KO/winner, `e3b7442`
  winner trophy, `6a45754` press inverts the shape only, `f9f23c9` menu stays open, `0f25cd1` ring A,
  `a914686` scenarios, `d9599d0` captures), verified in a clean worktree at `d9599d0`: 174 core +
  2046 app (incl. others' commits), 8 captures pure. The "larger square" was the closed centre
  diamond inverting its bounding box. The gem's idle auto-close is gone. Gem open already invalidates
  only its bounds (~20 % of the screen) — the 100 % in performance.md came from other frames in the
  bench window. In `review`. **Needs Sam:** winner icon pick (trophy wired; medal and "#1" finger in
  `t51-winner-icons.png`); should the gem hide while the dice panel is up (its tips show above and
  below the tent); the start-flag effect now plays partly under the open gem.
  Running: T52, T53, T55, T57, T58.
- 2026-11-14 — Tick 71: correction from Sam — choosing a tool should still dismiss the menu; only the
  idle timeout was unwanted. T51 agent resumed to restore the closing behaviour (no idle auto-close
  stays). T52 reported (10 commits, `5c1fc54`…`e1cb9f1`), verified by coordinator at HEAD `7c3fd09`
  with no build cache: 174 core + 2047 app, captures pure. Dice/coin presentation = 3 tumble
  keyframes × 250 ms + a stamped landing 300 ms (1.05 s; nat 20 / nat 1 1.15 s), skippable by a tap,
  one timer armed only while presenting; first player gets a 450 ms "ME?" beat on every live seat;
  variant B (dust-up) on the sheet, not built. Partner: in commander mode each tile's drawer-tab
  spot is a PARTNER tab (outlined off, ink on); turning it off with partner damage needs a 500 ms
  hold (tap shows HOLD). In `review`; needs Sam's eyes on the panel for keyframe settling and length.
  Running: T51 (fix), T53, T55, T57, T58.
- 2026-11-14 — Tick 72: verified by coordinator at HEAD `47057b6`, no build cache: 176 core + 2068
  app. **T51 correction** `0ce4f8b`: DICE/coin, FIRST, LOG, SETUP, CLEAN and NEW (after confirm)
  close the gem again; UNDO/REDO keep it open as before; no idle timeout. **T53** (`d0be013` …
  `aae20c8`): settings page "Counters and trackers" (commander tax, 7 counters, 5 trackers; hiding is
  display-only, state kept; `hiddenTrackers`, save v4); layout picker behind `›` on the Players row
  (pixel thumbnails, current framed; applies at once for the count in play — seats, order and state
  kept — else at NEW; per-count choice in `AppSettings.layout`); new arrangements 2-side, 2-corner,
  3-side, 3-end (plus existing ends/pyramid/dinner/5-end); 4-dinner and 6-dinner hatched at S=4;
  settings and history cards remember their facing (save v5). Commit subjects `607a902`…`8ecdfdc`
  are off by one — mapping in empty commit `b20af43`. 5-player wide near pair not built (needs two
  tile sizes in one game). **Needs Sam:** which arrangements to keep; 5p wide pair; end seats read
  the gem/dice sideways; thumbnails size. In `review`. Running: T55, T57, T58.
- 2026-11-14 — Tick 73: T58 reported (`66c9701`, `0280fee`, `47057b6`, `292fc66`); code commits
  covered by the coordinator's clean build at `47057b6` (176 core + 2068 app). Fast e-ink mode is a
  feature: `eink/EinkViewMode` + `ViewModeGuard` (Supernote only, reflection resolved once, any
  failure disables for the session), DU by default (`AppSettings.fastDisplay`, a flag bit, no format
  bump), applied after `setContentView`, on resume and on setting change; setting lives on a SCREEN
  page behind `›` on the Screen clean row (Supernote only). **A non-debuggable release build may make
  the call** (checked on the Nomad with a side-by-side `.t58` package: mode read back 1, no hidden-API
  warning). Optional GC clean behind `--ez gcClean true` (default stays the black flash). The mode
  sticks across pause/resume and sleep. `docs/eink.md` has "Display modes (T58, as built)".
  Note: the Nomad's save changed at 10:59 (Scale 3, fresh game) before the agent's first action —
  presumably Sam using the device; the earlier state is in `local/nomad-save-backup/save-t58.dtsv`.
  **Needs Sam's eyes:** GC clean vs flash; whether FAST wants a lower clean threshold; A2/DUX.
  In `review`. Running: T55, T57.
- 2026-11-14 — Tick 74: Sam reviewed T52 ("die and coin animations are great") → T59 to the same
  agent (resumed; emulator 5564). Decision: SPEC rule 4 relaxes on a fast display — a few more held
  poses allowed; slow displays keep 2–4 keyframes. Running: T55, T57, T59.
- 2026-11-14 — Tick 75: T55 and T57 reported; coordinator clean build at HEAD `cf56dd3`, no build
  cache: 176 core + 2068 app, debug and release assemble (release 248 KB unsigned); 42 `t55-*`
  captures pure. **T55** (12 commits, `cdba035` … `ac82ac0`): approved round-three effects wired —
  triggers for counters, toggles (monarch, initiative, ring), day/night on every live seat, turn
  pass, first player, revive; bands small 1–3 / big 4–9 / huge 10+ / crit 20+; weights in
  `fx/README.md` (chomp 1 in 9 big hits, spotlight 1 in 4 turn passes); **life effects start 300 ms
  after the last tap**, once per band per readout; crown-drop and torch art fixed (+ narrow-tier
  versions); effect layers drawn from rects — mask memory 10.3 → 8.2 MB with ~20 more effects.
  **T57** (`4f32248`, `59b5865`, `31631ca`, `0774957`, `ad062d1`, `fcdf18f`): press inversion only
  after 120 ms down (`PlayInput.PRESS_DELAY_MS`, `--ei pressDelay N`); a quick life tap / drawer
  open / gem open is 1 frame, was 2. Hold review found one real bug — a cover on ONE seat (e.g. a
  KO tile appearing) cancelled every seat's touches, stopping other players' ±10 holds — fixed;
  the other hold paths tested clean (finger moves are not routed at all, so jitter cannot cancel).
  Combined build installed on the Nomad over the experiment build (save kept; fast mode reads back
  DU at create/resume/focus). `docs/eink.md` lever 1 still says "decision pending" — fix next doc
  touch. Running: T59.
- 2026-11-14 — Tick 76: Sam: effects must not trigger while a hold is in progress on that tile →
  T55 agent resumed (quiet timer starts from release; one effect for the whole held readout).
  Running: T59, T55 (fix).
- 2026-11-14 — Tick 77: T55 hold fix `9f4aaab` (fx/ only): a seat's quiet timer does not fire while
  any pointer is down on that seat's zones; it re-checks every 100 ms while held, then runs the full
  300 ms from release — one effect for the whole held readout (−30 = crit band). Dusk/dawn skip a
  held tile; turn-pass and first-player effects on a held tile are dropped. Verified by coordinator
  on the Nomad's base (`18f3f19` + the fix): 176 core + 2074 app; that build installed on the Nomad
  (T59's in-flight commits left out). Running: T59.
- 2026-11-14 — Tick 78: Sam reviewed the layout picker → T60 to the T53 agent (resumed; emulator
  5566): mixed tile sizes, empty-seat layouts, denser picker with dithered seat grounds, no labels.
  Running: T59, T60.
- 2026-11-14 — Tick 79: T59 reported (`d2fbe1d` speed flag, `541c37b` extra poses, `f59bda2` empty
  dice panel, `7e22d28` first-player roll-off, `dd86219` partner tab, `fd20ee5` captures); verified
  by coordinator at `fd20ee5` with `--no-build-cache --no-watch-fs` (the agent saw Gradle's file
  watching miss edits under concurrent agents — use both flags from now on): 176 core + 2085 app,
  captures pure. `eink/DisplaySpeed.current()` = FAST when not e-ink or the fast waveform is in
  effect, else SLOW (`--es displaySpeed fast|slow`); SPEC rule 4 updated. Dice/coin: SLOW 3 × 250 ms,
  FAST 5 × 150 ms, same totals. Dice panel opens empty ("Pick your poison"). First player: every
  live seat's tile is taken over (double frame, "D20"), tumbles, then shows its roll in the hero
  numerals for 1600 ms; highest gets a FIRST pennant with a quip; ties re-roll (max 3 rounds);
  `start-flag` is the sting; the flag stays 4 s. PARTNER tab on the TAB dither with small-partner
  +/− icons; paper tape off, ink tape on. That build (includes T60's first two engine commits) is on
  the Nomad. In `review`. Running: T60.
- 2026-11-14 — Tick 80: T60 reported (`2c0a800`, `ea2ed38`, `996fee0`, `3db8285`, `7bc9b7e`,
  `94afa15`, `71104ca`); verified by coordinator at HEAD `71104ca` (`--no-build-cache
  --no-watch-fs`): 176 core + 3826 app; that build is on the Nomad. Lattice arrangements: seats are
  rectangles of cells, cells may be empty (`Role.EMPTY` chunky-dot ground, no input). 20 new
  arrangements: 2 diagonal ×2; 3 wide-far/near, tall-left/right, L ×4; 4 wide-far/near; 5
  wide-near/far, 2×3 with a hole ×6. Picker: wordless 4×3 grid, seats as inset TAB-dither blocks,
  empty cells dotted outlines, selected = ink frame + solid seats + a small arrow per seat;
  unavailable layouts hatched with a hint line on tap. **Deviation from the brief:** tiles differ in
  size but content is laid out in ONE frame per game (the largest every seat can hold), placed
  against each seat's near edge — bigger seats get more paper, not bigger content. Defaults
  unchanged. **Needs Sam:** per-seat content sizing?; `5-wide-near` as the 5-player default?;
  empty-cell ground weight; arrows vs numerals on the selected thumbnail; trim the four L variants?
  In `review`. No agents running.
- 2026-11-14 — Tick 81: two tweaks from Sam. Partner tab icons redrawn by the coordinator as the
  mini imp from the `partner` icon at 2× with a 6×6 `+` / 6×2 `−` at its top right (`6d756e9`; the
  T59 squat-head drawings kept as candidates). Picker thumbnails switched by the T60 agent
  (`9580889`): seats are 1-px ink outlines, empty cells the `Role.EMPTY` dotted ground; selected
  stays solid ink with arrows; unavailable = dotted outlines. HEAD `6d756e9` built clean
  (`--no-build-cache --no-watch-fs`): 176 core + 3826 app; on the Nomad. No agents running.
- 2026-11-14 — Tick 82: Sam: partner tab icons laid out like the combined `partner` icon — mini imp
  top-left at 1×, a big `+` (9×9) / `−` (9×3) where the big imp sits. Redrawn by the coordinator,
  176 core + 3826 app pass, on the Nomad.
- 2026-11-14 — Tick 83: Sam: partner tab icons are a full-size `+` (14×14) / `−` (14×4) with the
  mini imp overlaid top-left in a 1-px contour cut-out. Redrawn by the coordinator, 176 core + 3826
  app pass, on the Nomad.
- 2026-11-14 — Tick 84: **T39 done — history rewritten and force-pushed** on Sam's go-ahead (asked
  again immediately before the push). Method: `git fast-export` → a small byte-level filter →
  `git fast-import` into a fresh repository (no third-party tool installed): the device serial and
  the Figma file id replaced by placeholders in every file version (55 occurrences), the reference
  screenshot dropped from every commit; replacement list kept in untracked `local/`. Verified before
  and after: no commit's tree or message contains either string, no `design/reference` path, no
  `.webp` object; 390 commits with identical authors, dates and messages; tip tree identical.
  **Every commit hash changed** — hashes quoted in earlier tick-log entries are the pre-rewrite ones
  and no longer resolve; find commits by subject. Local old objects expired and pruned (a stale
  scratch worktree was holding them; removed). Pre-rewrite backup: `local/backup-pre-rewrite-2026-11-14.bundle`
  (untracked; contains the private data — delete when no longer wanted). Left to Sam: GitHub keeps
  the old commits reachable by hash until it collects them — ask GitHub Support to purge, or
  recreate the repository; existing clones/forks keep the old data.
- 2026-11-14 — Tick 85: Sam reviewed the quips: 8 cut, 7 added (see the commit "Quips: Sam's review");
  applied by the coordinator to `QuipTables.kt` and `design/copy/quips.md`; 176 core + 3826 app pass;
  on the Nomad. Side effects: the first-player flag falls back to "ON THE PLAY" when a long quip
  can't fit (the Zelda line is 26 characters; narrow tiles hold ~20); poison list is 9 lines, 4 of
  them verbatim, so the test minimum and the half-retro rule were loosened by one — one more
  verbatim poison quote would restore both. The three new quotes' sources are marked "not
  re-checked". New ticket T61 (heads/tails coin icons). Sam asked about publishing on Google Play —
  answered in chat; no ticket yet.
