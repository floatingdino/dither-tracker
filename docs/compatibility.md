# Compatibility: screens and devices (T62)

Dither is built and tuned for one device, the **Supernote Nomad** (7.8", 1404×1872 at 300 dpi,
Android 11). This page says what happens on other Android screens, with **Android e-ink devices first**:
which ones work, how big things are on them, what was fixed for them, and what is still open.

Release APKs install on Android 8.0 (API 26) and later. The application id is
`com.floatingdino.dithertracker`.

## Which devices is this for

Dither is for **Android e-ink tablets of about 7" and larger that can sideload an APK**: the Supernote
Nomad and Manta, and the Onyx Boox Nova / Tab Mini, Page / Go 7 / Leaf, Note Air / Go 10.3 and Note
Max / Tab X lines (colour Kaleido models included; the app is black and white only). On these, every
player count from 2 to 6 is laid out comfortably and every screen works. A 6" Boox (Poke, Go 6) works too,
with smaller numerals. **Phone-shaped e-ink** (Boox Palma, Bigme HiBreak, Hisense A9) and small phones run
in a cramped mode that is usable but drops some on-tile markers. Ordinary Android phones and LCD tablets
work as a side effect; they are not what the app is designed for. reMarkable, Kindle, Kobo and PocketBook
readers do not run Android apps and cannot install Dither.

## How the size is chosen

Everything is drawn on a grid of **dither pixels**, each an integer S × S block of device pixels (SPEC
rule 2). AUTO picks S like this (`DitherGrid.chooseScale`, then `PlayLayout.chooseScale`):

1. Start from the screen's density: `S = round(densityDpi / 75)`, clamped to 2..6, so one dither pixel
   is about 0.34 mm on any screen (300 dpi gives 4).
2. Step down while the short side would have fewer than 200 dither pixels.
3. Step down (not below 2) while the **current game's arrangement** is not comfortable at that S.

Leftover device pixels become paper margin. The Settings → Scale row offers AUTO plus every S at which the
default 4-player layout is comfortable. The tab, the diamond and the icons ("chrome") are sized in device
pixels as they are on the Nomad, so they stay physically the same size on any 300-dpi screen whatever S is.

**Density, not just pixels.** AUTO already starts from the density, so a 6" phone does not get the
Nomad's 468×351 grid of tiny tiles, and a low-density TV does not get a huge one. What it does not do is
size the chrome by density: on a screen denser than 300 dpi the tab is physically smaller than on the
Nomad (5–7 mm hit height on a phone instead of 9.8 mm), and on a sparser one it is larger (13–18 mm). See
"Open: density-aware chrome" below. `densityDpi` is what Android reports, not necessarily the panel's
real ppi: Boox lets you change an app's DPI in its E Ink Center, and doing so changes the S AUTO picks.

**When nothing fits.** Where no S makes the game's arrangement comfortable, AUTO falls back to S = 2 and
the layout runs **uncomfortable**: the number, ±, readout, tabs, drawer, peeks and every overlay still
work and nothing is drawn off the grid or crashes (`TinyScreenTest`, the purity suites), but the toggle
badges (monarch, initiative, city's blessing, ring-bearer) and the commander-mode partner icons are not
drawn on the tile, and the readout sits tight above the number. The badges' state is still in the
drawer. The layout picker shows every arrangement there as not available ("Too small at any scale").

## E-ink devices (the main matrix)

Sizes are from the vendors' spec sheets as collected by
[comparisontabl.es](https://comparisontabl.es/onyx-boox-e-readers/) and the
[Supernote Manta page](https://supernote.com/products/supernote-a5-x2-manta); rows marked *unverified*
come from memory or a single secondary source. The density used is the panel's ppi; a device that
reports a different `densityDpi` gets the S for that value instead. Tile and numeral sizes are for the
default 4-player layout (`4-grid`); "cap" is the height of the hero numeral's capitals; "tab" is the
drawer tab's touch depth. "Emulator" means the app was run at that resolution and density on the
Nomad-shaped emulator (`wm size` / `wm density`) and every listed screen was opened.

| Device class | Panel (portrait px, ppi) | S | Grid | 4p tile | Cap | Tab | Comfortable | Verdict |
|---|---|---|---|---|---|---|---|---|
| **Supernote Nomad** (A6 X2); Boox Nova, Nova Air, Tab Mini C (7.8") | 1404×1872, 300 | 4 | 468×351 | 79×59 mm | 17.9 mm | 9.8 mm | 2–6, every arrangement but 4-dinner and 6-dinner | Reference device |
| **Supernote Manta** (A5 X2, 10.7") | 1920×2560, 300 | 4 | 640×480 | 108×81 mm | 17.9 mm (35.9 at 2–3p) | 9.8 mm | 2–6, every arrangement | Works; bigger tiles, k = 2 numerals at 2–3 players |
| **Boox 10.3" colour / new mono**: Note Air 3 C, 4 C, 5 C; Go 10.3 | 1860×2480, 300 | 4 | 620×465 | 105×79 mm | 17.9 mm | 9.8 mm | 2–6, every arrangement | Works |
| **Boox 10.3" older mono**: Note Air, Air 2, Air 2 Plus, Air 3; Bigme inkNote (*unverified*) | 1404×1872, 227 | 3 | 624×468 | 104×78 mm | 17.8 mm | 13.1 mm | 2–6, every arrangement | Works; chrome physically larger than on the Nomad |
| **Boox 13.3"**: Note Max, Tab X C | 2400×3200, 300 | 4 | 800×600 | 135×101 mm | 35.9 mm (k = 2) | 9.8 mm | 2–6, every arrangement | Works |
| **Boox Tab X** (13.3", first gen) | 1650×2200, 207 | 3 | 733×550 | 135×101 mm | 19.5 mm | 14.4 mm | 2–6, every arrangement | Works; chrome large |
| **Boox 7"**: Page, Go 7, Go Color 7, Leaf 2; Meebook M7, Bigme B751C (*unverified*) | 1264×1680, 300 | 3 | 560×421 | 71×53 mm | 13.5 mm | 9.9 mm | 2–6, every arrangement | Works |
| **Boox 6"**: Poke 3/5, Go 6 | 1072×1448, 300 | 2 | 724×536 | 61×45 mm | 9.0 mm | 9.8 mm | 2–6, every arrangement but 6-dinner | Works; small numerals (S = 3 is not comfortable at 4 players) |
| **Boox 6" low-res**: Poke 4 Lite; Meebook M6 (*unverified*) | 758×1024, 212 | 2 | 512×379 | 61×45 mm | 12.7 mm | 13.9 mm | none | Cramped mode (above): the oversized chrome leaves no room for the badge row |
| **Phone-shaped e-ink**: Boox Palma / Palma 2 / 3, Bigme HiBreak Pro, Hisense A9 | 824×1648, 300 | 2 | 824×412 | 70×35 mm | 9.0 mm | 9.8 mm | 2-ends only | Cramped mode at every count. 69 mm of screen height cannot hold two rows of 9 mm numerals plus 9.8 mm tabs and the badge row |
| *Excluded*: reMarkable, Kindle, Kobo, PocketBook | — | — | — | — | — | — | — | Linux-based readers; cannot install Android apps |

**Colour (Kaleido) panels.** A Kaleido panel is a full-resolution black-and-white panel under a colour
filter; colour has half the resolution (150 ppi), black and white keeps 300. Dither only draws pure black
and pure white at the black-and-white resolution, so the colour resolution does not matter. The filter
makes white a little darker and may tint fine dither patterns faintly (not seen on a device: *untested*).

**Hardest case: phone-shaped e-ink.** The Palma class is landscape 1648×824 at 300 dpi. At S = 2 the
4-player tile is 70×35 mm; at S = 3 it is 46 mm wide but only 136 dither px tall, too short for the
number and both tab bands. It runs in the cramped mode at every count (only 2-ends is comfortable, at 2
players). Holding it upright does not help (the app is landscape-locked, and the turned table has the
same tiles). It is usable for a game; the toggle badges are missing from the tiles.

### Refresh control by vendor, and what to set

`eink/DisplaySpeed` decides how many keyframes an animation plays: SLOW (2–4 held poses) on an e-ink panel
on its normal waveform, FAST (a few more, shorter) on a non-e-ink screen or with the Supernote fast mode
on. **Before T62 only a Supernote counted as e-ink**, so every other e-ink device was treated as a FAST LCD
and got the extra poses. Now `DisplaySpeed.isEinkDevice` recognises the e-ink vendors by
`Build.MANUFACTURER` / `BRAND` (Supernote, Ratta, Onyx, Boox, Bigme, Boyue, Likebook, Meebook; Hisense only
for its A5 / A7 / A9 models, since it also sells LCD phones) and reports SLOW there. A false match only
costs a few poses on a fast screen. There is no user override off a Supernote: the Display setting stays
Supernote-only because it switches the Supernote's own waveform (proposal below).

| Family | Per-view refresh API | Used by Dither | What `DisplaySpeed` reports | What to set on the device |
|---|---|---|---|---|
| Supernote (Nomad, Manta) | Hidden `View.setEinkUpdateMode` (works from a sideloaded app, `docs/eink.md`) | Yes: Settings → Display FAST | SLOW; FAST with Display FAST | Nothing; use Settings → Display in the app |
| Onyx Boox | Public SDK, `EpdController.setViewDefaultUpdateMode(view, …)` ([OnyxAndroidDemo](https://github.com/onyx-intl/OnyxAndroidDemo)) | No (no device to test on) | SLOW | E Ink Center (swipe from the edge) → per-app: refresh mode **HD** or **Balanced** (Dither changes few pixels per tap, so the cleaner modes cost little; Fast / Ultrafast and Speed modes ghost more); leave DPI at the default unless you want a different S; turn off dark-colour enhancement ([Boox refresh modes](https://help.boox.com/hc/en-us/articles/10701257029780-Refresh-Modes)) |
| Bigme (HiBreak, inkNote, B751C) | Hidden Rockchip-style `EinkManager`; per-app refresh in system settings (*unverified*) | No | SLOW | Per-app refresh mode on its clearest setting (*unverified*) |
| Hisense A5 / A7 / A9 | Vendor-only; per-app display modes in settings (*unverified*) | No | SLOW (A5/A7/A9 models) | Per-app clear / high-quality mode (*unverified*) |
| Meebook / Boyue (Likebook) | Rockchip hidden `EinkManager` | No | SLOW | Per-app refresh, if the firmware has it (*unverified*) |
| Phones, LCD tablets, the emulator | — | — | FAST | — |

## Phones and LCD tablets (secondary)

| Screen (landscape px, dpi) | Example | S | Grid | 4p tile | Cap | Tab | Comfortable | Verdict |
|---|---|---|---|---|---|---|---|---|
| 1280×720, 320 | small phone | 2 | 640×360 | 51×28 mm | 8.4 mm | 9.2 mm | none | Cramped mode; the 2-ends gem would leave the grid (fixed: now not offered) |
| 2400×1080, 420 | common phone | 2 | 1200×540 | 72×33 mm | 6.4 mm | 7.0 mm | 2–6 | Works, small: numerals 6 mm, tab under 9 mm |
| 3120×1440, 560 | tall phone | 4 | 780×360 | 71×32 mm | 9.6 mm | 5.3 mm | 2–6 | Works; tab hit 5.3 mm is under the 9 mm fingertip rule |
| 1920×1200, 240 | small tablet | 3 | 640×400 | 101×63 mm | 16.8 mm | 12.4 mm | 2–6 | Works |
| 1920×1200, 320 | small tablet | 3 | 640×400 | 76×47 mm | 12.6 mm | 9.3 mm | 2–6 | Works |
| 2560×1600, 320 | 10–11" tablet | 4 | 640×400 | 101×63 mm | 16.8 mm | 9.2 mm | 2–6 | Works |
| 2000×1200, 240 | 10" tablet | 3 | 666×400 | 105×63 mm | 16.8 mm | 12.4 mm | 2–6 | Works |
| 2800×1752, 266 | 12" tablet | 4 | 700×438 | 133×83 mm | 20.2 mm | 11.1 mm | 2–6 | Works |
| 2208×1840, 374 | foldable, open | 5 (4 at 5–6p) | 441×368 | 75×62 mm | 18.0 mm | 7.8 mm | 2–6 (fewer alternates) | Works; S changes with the player count |
| 1920×1080, 160 | low-density big screen | 2 | 960×540 | 152×85 mm | 16.8 mm | 18.4 mm | 2–6 | Works; chrome physically large |

The full per-count table (S, grid, tile, numeral scale, comfortable arrangements) is written by
`ScreenSurveyTest` to `app/build/purity/t62/screen-survey.txt`.

## What T62 fixed

- **Other e-ink panels count as e-ink** (`DisplaySpeed.isEinkDevice`): SLOW keyframes on Boox, Bigme,
  Meebook and Hisense e-ink, not the LCD pose count.
- **Display cutouts** (emulator: the tall, hole and corner cutout overlays at 2400×1080). The app targets API 37, and from Android 15 an app that targets 35+ is drawn
  edge to edge, under a notch or hole punch, whatever its cutout mode. `PlayView` now reads the cutout's
  safe insets and lays the dither grid inside them (`DitherGrid(…, insetLeft, insetTop)`); the inset
  becomes paper margin. No change on a screen without a cutout (the Nomad).
- **The open gem must fit the grid.** On a 720-px-tall phone the 2-ends diamond sits mid-height and its
  gem reached one row past the grid. A layout whose gem cannot open whole is no longer comfortable.
- **Tiny windows.** A window a few pixels across (a squeezed freeform window) crashed the seat glyphs
  (division by a zero extent). `TinyScreenTest` now lays out, draws, opens every overlay and taps every
  tab on windows from a phone's split-screen half down to 0×0.
- The JVM screen suites (`CountsScreenTest`, `PuritySuiteTest`) now also run on every screen above at
  its AUTO scale, every arrangement.

## Robustness

- **Rounded corners.** Not handled. The counter peeks sit in each tile's near corners, so on a corner
  tile the outermost peek chip is in a screen corner, where a phone's rounded corner can clip a few
  pixels of it (it stays readable; it is not a touch target). Insetting by `RoundedCorner` (API 31+)
  would cost every phone a margin all round. E-ink devices have square corners.
- **Gesture navigation** (checked on the emulator, API 37, gesture navigation on, 2400×1080). The
  tabs sit on the long edges, inside the system-gesture strips (bottom 84 px mandatory, sides 78 px).
  A tap on a bottom tab opens the drawer down to 3 px from the edge; only the last 3 device px (well
  under 0.5 mm) do not reach the app. A press-and-hold 3 px from the left edge (inside the back-gesture
  zone) reached the app as a hold (+20). The top edge reaches the app to row 0. System gestures need a
  swipe; no `setSystemGestureExclusionRects` is needed.
- **System bars.** Hidden once (immersive, transient on swipe; API 30+ `WindowInsetsController`, older the
  `SYSTEM_UI_FLAG_IMMERSIVE_STICKY` flags) and drawn over the app when swiped in; they are not insets.
- **Configuration changes** (emulator). `configChanges` covers orientation, size, screen layout and
  smallest width, so a fold/unfold, a window resize or a split-screen change re-lays the screen out in
  place: lives 33 / 22 / 11 / 7 survived 1872×1404 → 2208×1840 at 374 dpi → 2400×1080 at 420 dpi. A
  density change recreates the activity; the game lives in `GameSession.shared` and survived it too.
- **Orientation** (emulator, API 37). Landscape-locked. On a phone-sized display held upright
  (1080×2400, 411 dp wide) the lock holds and the app runs landscape. On a large display (1404×1872 at
  300 dpi, 749 dp wide) Android 16+ ignores the lock for apps targeting 36+, so the app runs in portrait:
  the engine lays out the table turned 90° with the same tiles, and it reads correctly. Of the e-ink
  devices above, only the Android 16 ones with a large screen (the Boox Note Air 6 C) can ignore the lock;
  held upright they get this portrait layout (*untested on a device*). The Palma 3 (Android 16) is under
  600 dp and keeps the lock.
- **Multi-window and freeform.** Not exercised on the emulator beyond resizing the display; the layout
  survives any window size (`TinyScreenTest`, down to 0×0) and falls back to the cramped mode in a
  phone's split-screen half.
- **Non-e-ink screens.** The Supernote fast mode stays inert (its guard checks the manufacturer and the
  hidden API); `DisplaySpeed` reports FAST.
- **API 26–29.** Lint reports no API-level problems (`NewApi`) at minSdk 26; in-place inversion falls back
  to a redraw below API 29 (as before). *Untested on a device or emulator below API 37.*

## Open decisions and proposals

1. **Density-aware chrome.** The tab, diamond, icons and gem are designed in device pixels at 300 dpi
   (`PlayLayout.Chrome.size`, `WidgetMetrics.size`, about 84 call sites). Sizing them by the real density,
   but never smaller than the Nomad's device pixels, would make them physically Nomad-sized on low-density
   screens (the Poke 4 Lite would become comfortable; the older Note Air and Tab X would get Nomad-sized
   tabs) and leave every 300-dpi device, the Nomad included, byte-identical. Making phones meet 9 mm too
   would leave no comfortable 4-player layout on any phone; that is a product call.
2. **Portrait on phones.** Not needed for e-ink tablets. A phone held upright has the same tile sizes as
   landscape (the table is turned), so unlocking orientation does not make phones more comfortable; it
   only lets the app follow the device. Recommendation: keep the lock.
3. **A compact tier for phone-shaped e-ink.** The Palma class loses the toggle badges on the tile. A
   smaller tab band or badges beside the number would bring them back; a design question.
4. **A display-speed override off a Supernote** (e.g. "Display: slow / fast" on any e-ink device, meaning
   only the keyframe count). Small; add it if an owner of another e-ink device asks.
5. **Onyx `EpdController`.** The best refresh API of any vendor; worth using if someone with a Boox can
   test.

## How this was checked

- **JVM**: `ScreenSurveyTest` (the numbers above, and AUTO lands on a comfortable layout wherever one
  exists; the Nomad is S = 4 at every count, both orientations), `CountsScreenTest` and `PuritySuiteTest`
  on every screen in the tables at its AUTO scale (every arrangement: zones reachable, drawers, commander
  mode, KO and winner, gem, settings, history, dice, first player, effects; 1-bit, global phase, nothing
  off the grid on comfortable layouts), `TinyScreenTest`, `DisplaySpeedTest`.
- **Emulator** (API 37, `wm size` / `wm density` at each row's resolution and density): for all 20
  screens, 4 players idle, a busy table (a KO quip, monarch and initiative, poison and tax peeks, a
  partner), the gem, the settings card, an open drawer, the dice panel with a d20 rolled, 2 players and 6
  players: 160 captures, every one 1-bit pure and aligned to its own S (`tools/check_1bit.py`).
- **The Nomad is unchanged**: `tools/check_purity.sh` on the Nomad-shaped emulator before and after the
  T62 changes, 199 captures each, all pass; the 46 non-effect captures are byte-identical.
- *Not checked on any real device other than the Nomad.* Untested: API 26–36 system images, a real
  cutout, real Boox / Bigme / Hisense firmware (what `densityDpi` and `Build.MANUFACTURER` they report),
  split-screen and freeform windows driven through the system UI.

## Captures

`design/captures/t62-<class>-<state>.png`, `idle4` (4 players, fresh) and `busy4` (seat 0 knocked out,
toggles, peeks, a partner) for: `phone` (2400×1080 at 420 dpi, S = 2), `tablet-10in` (2560×1600 at 320,
S = 4), `boox-7in` (Boox Page / Go 7 class, 1680×1264 at 300, S = 3) and `palma` (phone-shaped e-ink,
1648×824 at 300, S = 2: the cramped mode, no toggle badges). Each passes
`python3 tools/check_1bit.py <png> --scale <S>` at its own S.
