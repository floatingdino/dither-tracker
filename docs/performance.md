# Performance and battery (T20)

Measured on the **Supernote Nomad** (Android 11 / API 30, 4 × Cortex-A55, landscape 1872×1404,
S = 4, 4 players) unless a row says otherwise. "Before" is `7ca1914` (HEAD when T20 started plus
the measuring hooks only); "after" is the T20 commits on top. The owner's Nomad runs the **debug**
build, so most rows are debug; release rows come from a debug-signed release build installed beside
it under another application id (`.t20`, a fresh game, uninstalled afterwards — never over the
owner's install). Debuggable builds run interpreted/JIT, so their CPU times are 2–4× release.

## Budgets

| What | Budget | Now (Nomad) |
|---|---|---|
| Cold start to first frame, release | < 1 s | 583 ms |
| Warm start (activity re-created), release | < 300 ms | 138 ms |
| `onDraw`, any frame, release | < 10 ms (the panel takes ~300 ms) | life tap 1.5 ms, effect keyframe ≤ 7.7 ms, worst 13 ms (dice reveal) |
| Allocations in `onDraw` / touch handling | 0 (core's state change aside) | 0 — JVM tests pin it |
| Bitmaps built inside `onDraw` | 0 | 0 — purity-suite check |
| CPU while idle, foreground or background | 0 ticks | 0 ticks over 120 s each |
| Timers armed after the app is paused | 0 | 0 — `perf/BackgroundTest` |
| Turn timer (T17), while on and showing | ≤ 1 redraw a minute, digits only | 1 a minute, 10×14 dither px (`turns/TurnTabTest`) |
| Frames per quick life tap (T57: released before the 120 ms press delay) | 1 (the result) | 1 (`play/TapFramesTest`) |
| Frames per held tap (≥ 120 ms) | ≤ 2 (press, release) | 2 |
| StrictMode violations (debug) | 0 | 0 |

## Before / after

All Nomad. Start = `am start -W` TotalTime, median of 5 (cold: process killed; warm: BACK then
start, process alive; hot: HOME then start — `WaitTime`, as `am` reports a stale TotalTime for a
task brought to the front).

| Metric | Before | After | How |
|---|---:|---:|---|
| Cold start, debug | 2648 ms | 1274 ms | `bench.sh` start |
| Warm start, debug | 1629 ms | 234 ms | 〃 |
| Hot start, debug | 28 ms | 27 ms | 〃 |
| Cold start, release (fresh install, before ART's background compile) | 1790 ms | 583 ms | 〃, `PKG=…t20` |
| Warm start, release | 1393 ms | 138 ms | 〃 |
| First `onDraw` after process start, debug / release | 2365 / 1577 ms | 986 / 380 ms | trace |
| Prewarm before the first frame, debug / release | 1563 / 1352 ms | 187 / 148 ms | trace `layout took …, prewarm …` |
| Prewarm after the first frame (effects, overlays), debug | — | 398 ms | trace `prewarm later` |
| Scaled-mask cache (ALPHA_8 bitmaps) | 32.3 MB, 411 masks | 10.3 MB, 436 masks | trace `masks=… bytes=…` |
| PSS after launch, debug / release | 70.9 / 54.8 MB | 46.3 / 33.9 MB | `dumpsys meminfo` |
| Native heap (bitmap pixels), debug | 45.7 MB | 24.5 MB | 〃 |
| Masks built inside `onDraw` during play | some on first use (drawer, commander, history, KO/winner quips, dark-mode effects) and a new one whenever two icon scales alternated in a frame (unbounded) | 0 (trace mask count flat over the whole session) | purity-suite check; trace `masks=` per frame |
| Bytes allocated per full-screen draw (JVM, every state and effect frame) | 0 | 0 | `perf/AllocationBudgetTest` |
| Bytes allocated per drawer open+close (JVM) | 24 (after T14 landed) | 0 | 〃 |
| Bytes allocated per life tap (JVM) | 1408, all in `:core` | 1408, all in `:core` | 〃 |
| CPU, 120 s idle in the foreground | 0 ticks, 0 draws, 0 alarms, 0 wake locks | 0 / 0 / 0 / 0 | `bench.sh` idle |
| CPU, 120 s in the background after a tap | 2 ticks, 4 frames drawn (readout commit + effect, 3.5–20 ms each), timer fired | 0 ticks, 1 frame (the pause commit, drawn as the app leaves), no timer fired | `bench.sh` bg |
| StrictMode (debug), cold start + scripted session | (not installed) | 0 violations | `bench.sh` |
| Release APK | 200 KB | 205 KB | `assembleRelease` |

### `onDraw` per interaction (Nomad)

The HWUI canvas clip in `onDraw` is always the whole view, so every frame re-records the whole
screen; the per-frame cost is the whole screen's, whatever was invalidated. What we control is how
many frames an action makes and what we ask to be redrawn (the "invalidated" columns: the device-px
rect each entry point flushes, as % of the screen). `bench.sh` play, `onDraw` median in ms:

| interaction | frames (after) | debug before | debug after | release before | release after | invalidated median / max % (after) |
|---|---:|---:|---:|---:|---:|---:|
| life tap | 5 | 3.3 | 2.9 | 1.3 | 1.5 | 16.2 / 19.9 |
| hold repeat | 5 | 2.9 | 2.6 | 0.8 | 1.0 | 6.5 / 12.5 |
| readout commit + effect | 4 | 15.7 | 15.1 | 7.8 | 6.7 | 0.5 / 24.8 |
| drawer open | 1 | 2.2 | 2.7 | 1.2 | 1.3 | 13.0 / 24.8 |
| drawer cell | 2 | 2.0 | 2.0 | 1.1 | 1.0 | 0.6 / 0.9 |
| drawer close | 1 | 1.9 | 1.6 | 0.6 | 0.7 | 13.2 / 24.8 |
| commander mode in | 2 | 6.2 | 6.0 | 1.0 | 1.0 | 13.0 / 100 |
| commander damage | 4 | 1.6 | 1.7 | 0.9 | 0.9 | 12.5 / 49.8 |
| commander mode out | 3 | 9.0 | 9.4 | 1.2 | 3.1 | 12.9 / 100 |
| gem open | 3 | 7.4 | 2.7 | 6.7 | 7.5 | 100 / 100 |
| gem close | 2 | 6.9 | 6.4 | 2.5 | 2.9 | 19.9 / 100 |
| dice / coin | 10 | 2.3 | 3.2 | 1.3 | 1.4 | 11.0 / 100 |
| settings open | 2 | 3.4 | 3.6 | 1.9 | 2.0 | 10.9 / 100 |
| dark mode on + off | 2 | – | 2.7 | 1.8 | 1.7 | 4.7 / 4.7 |
| history open + close | 4 | – | 2.7 | 1.6 | 1.4 | 60.0 / 100 |
| KO (hold to 0, flourish, dead tile) | 8 | 2.5 | 1.7 | 0.8 | 0.9 | 16.1 / 24.8 |
| revive | 3 | 1.7 | 1.5 | 0.6 | 0.6 | 6.9 / 24.8 |

(ms. "–": the before run's settings taps missed after the card's layout changed. Frame times were not
a target and did not move; the effect frames are the dearest: release 6–8 ms.)

Effects, debug gallery (all four seats at once, `bench.sh` fx), median / max `onDraw`:

| effect | before median / max ms | after median / max ms | invalidated max % of screen |
|---|---:|---:|---:|
| claw-cut | 4.7 / 7.1 | 4.2 / 16.6 | 74 |
| crack | 7.0 / 15.6 | 5.2 / 22.0 | 52 |
| fault | 4.7 / 10.5 | 4.8 / 14.8 | 100 |
| fizzle | 6.3 / 13.5 | 5.2 / 18.1 | 47 |
| focus | 5.3 / 9.1 | 3.2 / 10.7 | 100 |
| heart | 5.0 / 11.1 | 4.6 / 10.6 | 31 |
| ko-shatter | 4.7 / 10.4 | 4.4 / 9.4 | 100 |
| pow | 3.1 / 6.3 | 3.4 / 6.2 | 48 |
| quake | 4.0 / 7.8 | 4.4 / 8.4 | 100 |
| rise | 3.4 / 5.9 | 3.3 / 10.1 | 100 |
| sprout | 4.7 / 7.3 | 4.9 / 8.6 | 19 |
| sunrise | 3.3 / 7.8 | 2.1 / 9.6 | 100 |
| twinkle | 3.1 / 7.3 | 3.6 / 8.1 | 64 |
| vines | 2.3 / 6.9 | 2.8 / 8.0 | 100 |
| winner | 3.3 / 7.4 | 3.8 / 8.1 | 100 |

(Gallery: the same effect on all four seats, so a frame's footprint is four seats' worth; in play an
effect invalidates its own seat only, at most one panel = 25 %.)

## What was fixed

1. **Masks cropped to their ink box, built row by row** (`9acf1f2`). `CanvasDitherRenderer` kept
   every image as a full-size ALPHA_8 bitmap at S² — effect frames are up to a whole panel — and
   built each with one `isInk` call per device pixel. Now only the ink bounding box is stored
   (drawn at its offset; alpha 0 never drew anything), each dither row expanded once and copied S−1
   times. 32.3 → 10.3 MB, prewarm 1563 → 448 ms (debug). Pixel-identical: the emulator's 98 purity
   captures byte-identical to the build before (effect captures as sets of distinct keyframes,
   because their timing moves with start-up speed).
2. **No bitmap is built inside `onDraw`** (`bcc4dfc`). `IconPainter` and `WidgetPainter` cached
   scaled icons for one k and emptied the cache when asked for another: two components at different
   k in one frame made new instances every frame, the renderer built a new mask for each in `onDraw`
   and kept the old ones until the next scale change (a leak during play). Now one cache per k. KO
   and winner tiles now prewarm their whole face (quip punctuation was built at the first KO),
   history its button halos, effects their tile paints in both colour modes (dark mode built them on
   the first effect). Found by a new purity-suite check (below).
3. **Effects and overlays prewarm after the first frame** (`8823013`). They cannot show before a
   touch; `Stage.prewarm(f, phase)` runs `FIRST_FRAME` (everything a restored game can show, KO and
   winner tiles included) in the layout pass and posts `LATER`. Cold start 1550 → 1293 ms (debug).
4. **Nothing ticks or draws in the background** (`61564c5`). On pause the readout commits at once
   (its 3 s timer used to fire in the background, after the save flush) and effects stop; while
   stopped, redraws are held until `onStart` (one redraw then).
5. **Drawer open/close allocated an iterator** (`9463928`, one line in T14's `DrawerItems.tray`):
   caught by the allocation test the day the toggle cells landed.
6. **StrictMode in debug builds** (`95cc042`): thread policy (disk, network) and VM policy (leaked
   closeables, activity leaks), log only. The one deliberate main-thread read (the save, below) is
   allowed explicitly.

7. **Layout before the first frame** (T50, `44a1494` and the settings commit after it). The
   trace's per-component `layout …us` lines showed where it went:
   - `KoComponent.onLayout` was not the quips (they are only arranged for seats already dead): it
     built the tombstone's cut-out four times, once per rotation, with a per-pixel dilation through
     `TextLayout.pointX/Y` — slow when interpreted (debug). It now builds one mask with the shared
     `HaloMask.of` and lets `blitHaloed` turn it (pixel-identical). KO and winner tiles still lay out
     and prewarm before the first frame (`PrewarmPhaseTest`).
   - `SettingsOverlay.onLayout` measured the card: every row on every page, and the scale row's
     options, which lay the Play screen out at each S (`SettingsTable.scaleValues`). The card cannot be
     open before the first frame, so `onLayout` now only records the grid and the card is measured on
     first need: the post-first-frame prewarm (an overlay, so `LATER`) or an `open`, whichever comes
     first; a relayout while it is open re-measures at once (`perf/LayoutDeferralTest`).

   Emulator (debug, cold start with `--ez trace true`, median of 8; relative numbers only — the
   emulator is several times faster than the Nomad):

   | | before (`02f301c`) | after |
   |---|---:|---:|
   | layout before the first frame (`layout took`) | 44.8 ms | 14.2 ms |
   | `KoComponent.onLayout` | 30.7 ms | 2.5 ms |
   | `SettingsOverlay.onLayout` | 5.9 ms | 0.0 ms (its measure moved into the `LATER` prewarm: 1.7 → 6.2 ms) |
   | prewarm before the first frame | 63.8 ms | 61.8 ms |
   | first `onDraw` after process start | 289 ms | 242 ms |

   Pixel-identical: of the emulator's 98 purity captures, the 47 non-effect ones are byte-identical to
   the build before; the effect captures differ only in the dead seat's random KO quip (carried into
   the gallery) and, with that seat masked, have the same keyframe sets (claw-cut and fault caught one
   more keyframe than the before run: timing). The JVM purity suite's 1280 shots are byte-identical.

## Regression tests

- `perf/AllocationBudgetTest` — `ThreadMXBean` per-thread allocated bytes over the real Play screen
  (`ScreenRig`): 0 bytes per full-screen draw in every state (both colour modes) and every effect
  keyframe; 0 for press/release, gem and drawer open/close; a life tap and a tap + commit + effect
  round add nothing over `:core`'s own state change (budget 4 KB, measured 1.4 KB).
- `perf/BackgroundTest` — after `Events.PAUSE`: readout committed, no effect playing, no timer armed,
  no redraw over a minute; also mid-effect and mid-KO; and with the turn timer on (T17), nothing armed
  or drawn over three paused hours, one re-synced redraw on resume, nothing armed with the timer off.
- `perf/AllocationBudgetTest` also pins the turn tab (T17): 0 bytes to draw it (minutes, pressed, on a
  KO tile) and 0 bytes per minute tick.
- `perf/PrewarmPhaseTest` — the first prewarm phase covers a fresh table, KO tiles and a winner at
  S = 2..6, both orientations and colour modes; the phases add up to everything.
- `play/TapFramesTest` (T57) — a quick life tap, drawer open and gem open flush exactly one frame;
  delay 0 two; nothing of the press tracker left armed after a tap. `stage/StageTest` pins the press
  delay's state machine (cancel, pause, cover, detach, multi-pointer, hold).
- `perf/LayoutDeferralTest` — the settings card is not measured in the layout pass, and measures the
  same on open, after a relayout while open, and after a relayout while closed.
- Purity suite (`purity/PuritySuiteTest`): every image or sparse tile paint a shot draws must have
  been built by the host's prewarm (`RasterRenderer.warm` / `cold`), so the device never builds a
  bitmap in `onDraw`. (The `effects` script is exempt: it plays the gallery's non-policy effects.)
- Test infrastructure made allocation-free so these can measure: `RasterRenderer` no longer builds a
  string per pixel or an `IRect` per clip; `FakePlatform.record = false` only counts invalidations.

## Found, not fixed

- **The whole screen is re-recorded every frame.** Under HWUI the `onDraw` clip is the full view, so a
  life tap costs a full-screen record (release ~1.3 ms, effect keyframes ~6–8 ms; debug 2–4 / 13–17
  ms). On the panel this costs nothing extra — unchanged pixels are not driven (`docs/eink.md` §2) —
  and it is two orders of magnitude below the panel's update time. The lever, if ever needed:
  per-seat `RenderNode`s (API 29+) re-recorded only when that seat is invalidated, drawn in layer
  order with `drawRenderNode`. Not done: it reorders the host's layer-by-layer draw and its
  press-inversion pass, for no visible gain.
- **Layout before the first frame** was 140 ms debug / 86 ms release on the Nomad, mostly
  `KoComponent` (46 ms debug) and `SettingsOverlay` (35 ms) — cut in T50 (fix 7). What remains is
  `DrawerComponent` (14 ms debug on the Nomad, 2 ms on the emulator) and the tile shaders; not
  re-measured on the Nomad.
- **`onCreate`** is ~380 ms debug / ~50 ms release: `Persistence.start` (100 / 6.5 ms: thread start,
  the 30 ms debug decode) and building the component list, including class-initialising the
  generated effect data. Release is already small.
- **The save is read on the main thread at start** (~200 bytes; read 4.3 ms, decode 26 ms debug, 0.4
  ms total release). Deliberate: the first frame must be the restored game, or the panel draws a
  fresh table and then the restored one (two full updates). Allowed explicitly under StrictMode.
- **`onPause` waits for the save** (up to 1 s, normally the write itself: ~6.5 ms incl. `fsync` on the
  save thread) so a swipe-away right after a tap cannot lose it. Saves are otherwise debounced (1 s
  quiet, at most every 10 s while tapping), written off the main thread, atomically (temp, `fsync`,
  rename) — one ~290-byte write per burst.
- **Effect masks stay resident** (~9.2 MB of the 10.3 MB) for the whole session, so an effect never
  stalls the tap that triggers it. Building each effect's masks on first use instead would save
  that memory at the cost of a stall (and allocation) at the first commit of each effect.
- **First frames of a mode still allocate a few objects** (debug trace: up to 52 objects on the
  first commander-mode frame, 3 on the first drawer and gem frames, none afterwards, no masks):
  one-time class initialisation and lazily created state on first use, not per frame.
- **Baseline profile: no.** On API 30 a baseline profile only takes effect when something installs it:
  Play (cloud profiles) or AndroidX ProfileInstaller writing it next to the app. We have neither (no
  AndroidX, sideloaded from a file manager), so a `baseline.prof` in the APK would be ignored. The
  upper bound of what AOT would give, measured by forcing it (`cmd package compile -m speed -f` on
  the `.t20` release install): cold start 583 → 315 ms, warm 138 → 124 ms (release, Nomad). ART's own profile-guided background compilation does the same
  over time on the device, once the app has been used and the Nomad is idle and charging.

## E-ink

- **Frames per action.** A quick tap is ONE frame, its result (T57: the press inversion only shows
  once a touch has been down 120 ms; before, every tap was two — press inversion, then the release
  with the new number and readout — and the panel held the region ~450 ms after the first, so the
  result showed ~0.7–0.8 s after touch). A tap held past the delay is still two; a hold step is one; the effect's keyframes (2–4, each ≥ 250 ms) and a clean end
  frame start 300 ms after the seat's last life change (T55: `FxComponent.QUIET_MS`, one per-seat
  one-shot timer, cancelled on pause, no allocation), once per band per readout group; the readout's
  commit (3 s idle) is one more frame. Nothing else draws: idle is 0 frames.
- **Frames per quick tap, before / after T57** (`play/TapFramesTest`, JVM, the real Play screen at
  1872×1404 S = 4; a tap = down, 60 ms, up; "before" = `--ei pressDelay 0`). Invalidated rect per
  frame as % of the screen:

  | quick tap | before: frames (area) | after: frames (area) | to the result on the Nomad |
  |---|---|---|---|
  | life ± | 2 (half 12.4 %, then half + number + readout 19.8 %) | 1 (number + readout 7.0 %) | ~0.2 s + the update, was ~0.7–0.8 s |
  | drawer open (tab) | 2 (tab 1.1 %, then the seat 24.8 %) | 1 (the seat 24.8 %) | the ~450 ms press hold is gone |
  | gem open (diamond) | 2 (diamond 1.0 %, then the gem 19.9 %) | 1 (the gem 19.9 %) | 〃 |

  A tap held ≥ 120 ms is still press + release (2). Not yet re-filmed on the Nomad.
- **Area asked per frame** (invalidated rect, % of the screen): quick life tap 7 % (the seat's number
  and readout; 16–20 % when held, with the half's restore), hold step 6–12 %, drawer cell under 1 %, drawer open/close ~13–25 % (the seat), an
  effect keyframe its footprint up to one panel (25 %), mode and overlay changes up to the whole
  screen. What the panel actually drives is the set of pixels that changed.
- **Clean cadence cost** (`bench.sh` clean, Nomad, `--ei cleanEvery 3`): a seat clean is two extra
  frames over that panel (black, then the redraw: 25 % of the screen each, every pixel of the panel
  flipped twice); a full clean is two full-screen frames. At the default "Screen clean 20" that is two
  large updates per ~20 counted changes, only after 2 s of quiet. Its timer is a single 2 s one-shot,
  armed only while a clean is due, dropped on pause; nothing is scheduled while it is OFF or below
  the threshold. Design unchanged.
- **Turn tracker and timer** (T17, `turns/TurnTab`): a pass is one frame over the two tabs (old and
  new, 89×21 dither px each at S = 4; the stage unions them into one rect, but only their pixels
  change); a press inverts one tab. The timer (Turns = GAME / TURN, off by default) is ONE one-shot
  armed for the next minute boundary of the clock, only while the timer is on, a tab is showing (not
  under an overlay, in commander mode, or after the game) and the app is resumed; it redraws only the
  digits (10×14 dither px per digit; the whole tab when 9 → 10 or 99 → 100) and re-arms. Dropped on
  `Events.PAUSE`, re-synced from the wall clock on `RESUME` (the minutes come from `TurnState`'s
  persisted start stamps, never a counter). A life tap does not re-arm it. With the timer off nothing
  is ever scheduled. Known slack: while the active seat's drawer is open the tab is hidden but the
  tick stays armed, so the panel is asked once a minute to redraw ~140 unchanged pixels there.
- **Keep awake** holds `FLAG_KEEP_SCREEN_ON` on the play window only while resumed and the setting is
  on; no wake locks otherwise (`dumpsys power`: none while idle).

## How to reproduce

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
./gradlew :app:testDebugUnitTest --tests 'au.com.josephmark.dithertracker.perf.*'   # allocation, background, prewarm phases
./gradlew :app:testDebugUnitTest --tests 'au.com.josephmark.dithertracker.purity.*' # includes "nothing built in onDraw"

# Device (serial in local/README.md); backs the save up and restores it, never clears data:
ANDROID_SERIAL=<serial> tools/bench.sh <outdir>                       # all parts, ~15 min
PARTS="start play" IDLE_SECS=300 ANDROID_SERIAL=<serial> tools/bench.sh <outdir>
python3 tools/bench_summary.py <outdir>                               # the tables above

# Release beside the debug install: build with `applicationIdSuffix = ".t20"` on the release type
# (scratch worktree only), -PdebugSignRelease, then
adb install app-release.apk
PKG=au.com.josephmark.dithertracker.t20 KEEP_SAVE=0 NO_INSTALL=1 PARTS="start play mem" tools/bench.sh <outdir>
adb uninstall au.com.josephmark.dithertracker.t20

# Pixel equivalence of a renderer change: capture both builds with the same script and compare
APK=<before.apk> ANDROID_SERIAL=emulator-<port> tools/check_purity.sh <before>
APK=<after.apk>  ANDROID_SERIAL=emulator-<port> tools/check_purity.sh <after>
for f in <before>/*.png; do cmp -s "$f" "<after>/$(basename "$f")" || echo "$f"; done
```

Sections of `bench.sh`: `start` (cold/warm/hot ×5 and one traced cold start's phases), `play` (a
scripted session split by `MARK` log lines: life taps, hold, readout commit + effect, drawer, commander
mode, gem, dice, settings, dark mode, history, KO, revive), `clean`, `fx` (debug gallery, every effect),
`mem`, `idle` (CPU ticks from `/proc/<pid>/stat`, `onDraw` count, `dumpsys alarm`, `dumpsys power`)
and `bg` (a pending readout, then HOME). The trace (`--ez trace true`, kept in release) logs every
`onDraw` (time, allocations via `Debug.getThreadAllocCount`, mask count), every invalidation, timer,
layout and prewarm phase.
