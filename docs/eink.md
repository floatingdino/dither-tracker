# E-ink refresh on the Supernote Nomad (T19a)

Research for T19b. The question: how does a sideloaded app get the fastest, cleanest screen updates on
the Nomad, and how should it deal with ghosting?

Confidence labels used below:
- **[doc]**: official documentation or vendor source code.
- **[device]**: read on our own Nomad over adb (read-only).
- **[community]**: reverse-engineered by third parties and reported as working. Not a contract.
- **[inferred]**: our reasoning from the above. Not yet measured.
- **[unknown]**: no evidence either way. See the experiments section.

## TL;DR

1. **Ratta gives third-party apps no documented refresh or waveform API.** The official plugin SDK
   (`sn-plugin-lib`, React Native) has no screen-refresh call. There is also no per-app refresh
   setting in the Supernote UI that we can find. Sideloaded apps get whatever the firmware's
   hwcomposer and EBC driver decide for an ordinary window.
2. **One hidden vendor call works from an unsigned app [community].** It is
   `getSystemService("eink")` → `screenRefresh(false, 1)`. This is the same call the Supernote
   launcher's "slide up to refresh" gesture uses. It is reflection into a hidden API, so treat it as
   an optional, guarded nicety and never rely on it.
3. **Waveform selection is reachable after all [device] (T58).** Ratta's framework gives every View
   an e-ink mode (`View.setEinkUpdateMode`, DU/A2/GC/…), listed as public SDK in the device's
   hidden-API flags, so a release build may call it too. Each `invalidate(l, t, r, b)` hands the
   vendor's view root its rect *and* the invalidating view's mode, so updates are per-region as well.
   We ask for DU (fast, 1-bit) on our one view by default ("Display" setting). The community reports
   above were wrong for this firmware. See "Display modes (T58, as built)".
4. **What we do control is how many pixels change per action, and how many frames we post.** With
   1-bit content, a pixel that didn't change costs nothing on the panel. So the app should be built
   to change as few pixels as possible, in one frame per user action. Add an app-drawn "flash"
   cleanup on a cadence, plus a manual refresh button.

## 1. Refresh control available to third-party apps

### Supernote / Ratta

| Mechanism | Status | Confidence |
|---|---|---|
| Public refresh/waveform SDK | None. Ratta's developer docs cover only the Notes/Docs plugin system (`sn-plugin-lib`). Its API has file, page and plugin-view calls but nothing for screen refresh. ([docs.supernote.com](https://docs.supernote.com/en), e.g. [reloadFile](https://docs.supernote.com/en/api-reference/supernote-plugin/plugin-comm-api/reload-file)) | [doc] |
| Per-app "refresh mode" setting for sideloaded apps | None found. Changelogs list a system-wide **Auto Refresh** toggle under Settings → Display & Input, added in 3.20.31_beta / 3.22.31. Its default went from Enabled to Disabled in 3.24.38. Nothing says whether it applies to third-party apps. ([changelog](https://support.supernote.com/changelog-for-manta-and-nomad), [beta changelog](https://ratta.helpjuice.com/change-log/changelog-for-the-beta-versions-of-manta-and-nomad)) | [doc] for its existence, [unknown] for its scope |
| User-triggered full refresh | Slide up on the sidebar (the left one on the Nomad). ([FAQ](https://support.supernote.com/faq/how-to-clear-afterimages-on-the-screen)) It is a system gesture, so it should work over our app. | [doc] for the gesture, [inferred] for it working over third-party apps |
| `EinkManager` service (`"eink"`, `android.os.IEinkManager`) | Present on the device ([device]). It is Rockchip's hidden framework class ([source](https://github.com/aosp-rockchip/android_frameworks_base/blob/HEAD/core/java/android/os/EinkManager.java)). Ratta's build extends it with `screenRefresh(boolean,int)`, `setMode(String)`, `sendHwcCmd(int,int[])`, `enableFullUiAuto(..)`, `enableAutoRegal`, `disableRegal`, `freezeScreen`, `setDitherType`, and others ([method dump, A5X2](https://github.com/LVEVAN/ink2task/blob/HEAD/.claude/skills/supernote-plugin-dev/references/pen-emr.md)). | [community] |
| `screenRefresh(false, 1)` + static `android.view.SFCommand.screenRefresh()` | This is what `SupernoteLauncher`'s `GestureService.GCRefresh()` calls. It was reported **working on a Nomad from an unsigned app** ([plateaukao/ADR](https://github.com/plateaukao/ADR/blob/HEAD/AssistiveTouch-supernote-gesture-remap.md)). [readr](https://github.com/hunterchen7/readr/blob/HEAD/apps/mobile/android/app/src/main/java/com/readr/app/EpdModeModule.kt) uses the same calls. | [community] |
| `EinkManager.sendOneFullFrame()` (Rockchip stock; KOReader's RK35xx path) | It only bumps `sys.eink.one_full_mode_timeline`. That property is **unset on our Nomad** ([device]), so the Ratta HWC probably doesn't watch it. KOReader users report that automatic full refresh doesn't work on the Nomad ([r/Supernote](https://redlib.ssps.io/r/Supernote/comments/1b4y578/what_is_everyone_sideloading)). [inkread](https://github.com/j-raghavan/inkread/blob/HEAD/app/src/main/java/dev/jraghavan/inkread/eink/SupernoteEinkAdapter.kt) uses it anyway. Treat it as unreliable. | [device] + [community] |
| `EinkManager.setMode(String)` (writes the global `sys.eink.mode`) | `sys.eink.mode` is unset on our device ([device]). It is reported as "a no-op for an untrusted window" ([inkread](https://github.com/j-raghavan/inkread/blob/HEAD/docs/EINK-LIMITS.md)). It would be global system state anyway. **Do not use.** | [community] |
| `View.setEinkUpdateMode(int)` / `(int,int)`, `resetEinkUpdateMode()`, `forceEinkFullUpdate()` | Present, public-SDK-flagged, and working from our debug **and release** builds (T58). We use it: see "Display modes (T58, as built)". (Also used by Ratta's Notes app per [readr](https://github.com/hunterchen7/readr/blob/HEAD/apps/mobile/android/app/src/main/java/com/readr/app/EpdModeModule.kt).) | [device] |
| `/dev/ebc` ioctls, the `drawpath` service, `enableFullUiAuto`, `sendHwcCmd` | Privileged paths, SELinux-gated, global effect, or risky (inkread calls `/dev/ebc` "reboot-risky"). **Out of scope.** | [community] |

The panel driver is Rockchip's EBC. Supernote publishes the kernel
([Supernote-Ratta/kernel_Nomad_Manta](https://github.com/Supernote-Ratta/kernel_Nomad_Manta/blob/main/drivers/gpu/drm/rockchip/ebc-dev/ebc_dev.h)).
`enum panel_refresh_mode` defines `EPD_AUTO`, `EPD_PART_GC16`, `EPD_FULL_GC16`, `EPD_A2`, `EPD_DU`,
`EPD_FORCE_FULL`, and others. The mode logic is a binary blob (`ebc_dev_v8.S`). The hwcomposer
decides the mode per frame and is closed source. Rockchip's `EinkManager.getMode()` defaults to
`EPD_PART_GC16`, a partial 16-level waveform. **[doc]** for the enum, **[inferred]** for the
default being what our windows get.

### For comparison: other vendors

- **Onyx Boox** ships a real per-view SDK: `EpdController.setViewDefaultUpdateMode(view, UpdateMode.DU/A2/GC/REGAL)`,
  `invalidate(view, UpdateMode.GC)`, `resetViewUpdateMode(view)`. Users also get per-app refresh
  modes in the system UI. ([unofficial docs](https://progressive-kiwi.github.io/onyx-inking-unofficial-docs/epd-rendering.html),
  [OnyxAndroidDemo](https://github.com/onyx-intl/OnyxAndroidDemo)) This is the best case among
  Android e-ink vendors.
- **Other Rockchip readers** (Xiaomi/Duokan, Lenovo Smart Paper, Meebook, etc.) expose only the same
  hidden `EinkManager`. Results vary by device. KOReader keeps a separate controller per vendor
  ([android-luajit-launcher](https://github.com/koreader/android-luajit-launcher/blob/HEAD/app/src/main/java/org/koreader/launcher/device/epd/rockchip/RK35xxEPDController.kt)).
- **So Supernote sits at the bottom of the range:** no SDK, no per-app modes, and one hidden
  refresh call.

### What our Nomad reports (read-only adb, firmware `Chauvet.E103.2610191001`, Android 11 / SDK 30)

- Board `rk356x`, build `rk3566_ht_eink`, `ro.rksdk.version=ANDROID11_RKR10`, `ro.vendor.eink=true`,
  `ro.vendor.sdkversion=Eink_Supernote_103inch_0_2`.
- Binder services: `eink: [android.os.IEinkManager]`, `hteink: [hteink.IDeviceManagerService]`,
  `opt_service: [hteink.optimization.IOptService]`. HAL `vendor.hteink-hal-1-0-0` is running.
  `dumpsys eink` / `hteink` / `opt_service` print nothing.
- Vendor boot jars in `/system/framework`: `htfyview.jar`, `htfyOpt.jar`, `htfypw.jar`,
  `libeinkpwcoreapi.jar`. These come from the ODM (`htfyun`; the factory-test app is `com.htfyun.mmitest`).
  Presumably they hold the View/EinkManager extensions above. We didn't decompile them; that was
  outside the read-only brief.
- No `sys.eink.*` or `persist.*eink*` properties are set. `debug.hwui.use_partial_updates=false`:
  HWUI repaints the whole window buffer every frame and sends no damage region downstream.
- SurfaceFlinger: our window is composed as `DEVICE` (HWC), with a single 60 Hz mode and an idle timer
  that is off.
- Settings: `system.eink_darker_level=2` (a vendor contrast setting; it shouldn't affect pure B/W).
  `global.animator_duration_scale=0`, `transition_animation_scale=0` and `window_animation_scale=0`
  (system animations are already off). `system.screen_off_timeout=7200000` (a user choice).
  `system.set_standby_picture=/data/vendor/eink/standby1.png` (the sleep screen covers our UI).
  No Auto Refresh key shows up in `settings list`, so that setting lives in Ratta's own storage.

## 2. How stock Android behaviour reaches the panel

- **`invalidate(Rect)` does not reach the panel as a dirty rect.** Since API 21 the rect "is ignored
  entirely in favor of an internally-calculated area" (`View.invalidate(Rect)` deprecation note, AOSP
  `View.java`) **[doc]**. HWUI redraws the invalidated views' display lists. On this device, with
  partial updates disabled, it composites the **full** window buffer every frame **[device]**. What
  actually gets driven on the panel is decided downstream. The EBC's `PART`/`AUTO` modes compare the
  new frame with the old one and only drive pixels that changed **[inferred]**: that is how Rockchip
  partial waveforms work, and it matches the `AUTO_OLD`/`AUTO_NEW` buffers in `ebc_dev.h`.
  - Consequence: **the unit that matters for panel time and flashing is the set of changed pixels**,
    not the rect we pass. The unit that matters for CPU time is **which Views get invalidated**.
    So split the play screen into one View per seat, plus the menu button, drawer and so on, and
    invalidate only the View that changed. A plain `invalidate()` is fine. Keep the global pattern
    phase by drawing in root coordinates, which `DitherRenderer` already does.
  - Redrawing identical pixels costs CPU but nothing on the panel **[inferred]**. Changing pixels
    you didn't need to change (re-dithering a whole panel when only the number changed) costs real
    panel time and ghosting.
- **Software vs hardware rendering.** Keep hardware acceleration as the default. A software
  `LAYER_TYPE_SOFTWARE` layer still repaints the whole View into its bitmap and adds an upload
  **[inferred]**. Software rendering for the whole window (`android:hardwareAccelerated="false"`)
  does honour dirty rects for the CPU-side redraw, but it's a whole-app choice. Only worth trying if
  experiment E2 shows HWUI frame time is the bottleneck. The panel waveform (~hundreds of ms) will
  almost certainly dominate.
- **Window background.** Keep the opaque white `windowBackground` (already in `Theme.Dither`) and
  make every View opaque. That avoids a non-white first frame and pointless overdraw.
  Overdraw only costs CPU/GPU, not panel time, but the CPU here is slow.
- **Extra windows and system UI.** Dialogs, `PopupWindow`, `Toast`, IME, and system-bar show/hide
  each add, remove or resize a window. Each is likely to trigger a large-area or full refresh. The
  vendor even has a `screenRefresh(afterWindowHide, …)` flag for this case **[inferred]**. Keep
  everything (drawers, menus, confirms, the dice result) **inside our one window, drawn by our
  Views**. Hide the system bars once in `onCreate`/`onWindowFocusChanged` and never toggle them.
  `MainActivity` already uses `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, so an edge swipe can still
  bring them up briefly. That is acceptable.
- **`FLAG_KEEP_SCREEN_ON`.** E-ink holds its image without power. The risk is the Supernote sleep
  screen (standby picture) replacing the life totals mid-game. Set the flag only while a game screen
  is visible **and** the keep-awake setting is on (SPEC already lists that setting). Note that it
  stops the device suspending, so it costs battery (measure in E9). Turning it on or off doesn't
  redraw anything.
- **Pure black/white and A2/DU.** Our content is already 1-bit, so a 16-level partial waveform
  (`PART_GC16`) and the HWC's RGBA→grey conversion leave it untouched, whatever `eink_darker_level`
  or dithering the HWC applies **[inferred]**. 1-bit content is also the only content where fast
  binary waveforms (A2/DU) would be lossless. (Ratta's framework does expose them per view: T58,
  "Display modes", at the end.) Black/white transitions are also the ones a
  partial waveform drives most cleanly. Grey-to-grey is where ghosting comes from.
- **Animations.** SPEC already forbids them. Note that every intermediate frame posted becomes a
  panel update, including something as small as a press highlight followed by a release redraw.

## 3. Ghosting

- **How fast it builds up.** With partial GC16-class updates, 1-bit content ghosts slowly. With
  A2/DU it ghosts fast: Onyx guidance is a GC refresh on page turns or clear-all after repeated A2
  updates ([ref](https://progressive-kiwi.github.io/onyx-inking-unofficial-docs/epd-rendering.html)).
  We don't know which mode the Ratta HWC gives our window. That is the main question for E3. The
  digits of a life total change in the same spot over and over, so we expect visible "shadow
  digits" after some tens of changes in the worst case **[inferred]**.
- **Ways to force a clean:**
  1. **App-drawn flash (portable, no vendor API).** Paint the region (or the whole root) solid black,
     let that frame reach the panel (wait ~one panel update, about 300–500 ms, tune in E4), then
     draw the real content. Every pixel is driven to a saturated state and back, which approximates
     a GC clean. An invert of the region works too, at slightly lower strength. Whether a partial
     waveform clears ghosts this way as well as a true GC16 full refresh does is **[unknown]** until
     E4. It costs two visible panel updates.
  2. **`screenRefresh(false, 1)` via the `eink` service** — the same full GC refresh the system
     gesture uses **[community]**. Full screen only, so no region version.
  3. **The user's sidebar slide-up**: always available, costs us nothing.
- **Default cadence** (to be tuned after E3):
  - Full clean on **game start/reset** and when **closing a full-screen overlay** (menu, settings,
    history).
  - A **per-seat region flash** after that seat's area has changed **N = 20** times since its last
    clean. Run it on idle: after the pending-delta commit, never mid-tap.
  - A **global full clean** every **100** committed changes, or N × players. Expose it as the SPEC's
    "full-refresh every N changes" setting, with 0 = off.
  - A **manual refresh** item in the centre menu (a `refresh` icon is already planned).
  - Never flash during hold-to-repeat or within ~1 s of a touch.

## 4. Recommended strategy for T19b, in priority order

1. **Invalidation audit and View granularity.** One View per seat plus the shared chrome. Each state
   change invalidates only the Views whose pixels change, with exactly one frame per user action:
   batch the state update, then a single `invalidate()`. Press feedback = invert on down, redraw on
   up. That is two panel updates, which is inherent, so make sure there are no others. Hold-to-repeat
   steps ≥ 300 ms apart so each step gets its own panel update instead of piling up in the queue.
   *Verify:* turn on "Show GPU view updates" temporarily, or log `onDraw` per View, and check that
   each tap redraws only the expected Views. Film the screen with a phone's slow-mo camera: no
   flashing outside the touched seat, no double updates.
2. **Single-window discipline.** No Dialog/PopupWindow/Toast/IME. System bars hidden once and not
   toggled. Opaque white background, opaque Views.
   *Verify:* E5 (does a Dialog or bar toggle cause a full-screen flash?), plus
   `adb shell dumpsys window windows | grep dithertracker`: one app window.
3. **`EinkRefresher` abstraction** (`app/.../eink/`) with `cleanRegion(view or rect)` and
   `cleanFull()`. The default implementation is the app-drawn black flash, driven by a small state
   machine in the root View: draw black → `postDelayed(flashMs)` → redraw. No allocations, and input
   is ignored while it runs. Add a cadence policy (counters per seat, global count, triggers on
   reset and overlay close), hooked to the settings value.
   *Verify:* E3/E4 photos show residual digits gone after a region flash. Flash time matches the
   setting. Counters reset on new game.
4. **Keep-awake.** `FLAG_KEEP_SCREEN_ON` on the play screen only when the setting is on. Clear it
   in `onPause`.
   *Verify:* with the setting on, leave the device idle past the user's sleep timeout and confirm the
   life totals stay on screen. `adb shell dumpsys power | grep -i wake` shows our window's lock
   (read-only).
5. **Optional, guarded vendor refresh** (off by default, or enabled only after E4 shows a clear
   win). `SupernoteFullRefresh.tryFullRefresh(context)`. Only when `Build.MANUFACTURER == "Supernote"`.
   Resolve `context.getSystemService("eink")` and its `screenRefresh(Boolean, Int)` once via
   reflection, cached. Call `(false, 1)` inside `try { } catch (Throwable)` and fall back to the app
   flash on any failure. Never call `setMode`, `sendHwcCmd`, `enableFullUiAuto`, `setDitherType` or
   anything else that changes global state. Log once on failure, then disable for the session.
   *Verify:* on the Nomad, `cleanFull()` gives the same flash as the sidebar slide-up. In the
   emulator it's a silent no-op that falls back to the app flash. `adb logcat | grep -i eink` shows
   no errors.

Not recommended: `/dev/ebc`, root, `SFCommand`'s global switches, or anything that writes system
properties. (Per-view A2/DU mode selection was on this list until T58 found it works; see the end.)

## 5. On-device experiments (once the play screen exists)

Record each with a phone slow-motion video (240 fps) of the panel, plus a photo under raking light
for ghosting. Put the results back into this file.

| # | Experiment | What it tells us |
|---|---|---|
| E1 | Tap ±1 on one seat. Does anything outside that seat flicker? Compare against invalidating the whole root. | Whether the panel update really is limited to changed pixels (partial diff) |
| E2 | Tap-to-ink latency for: inversion on down, number change on commit. Try with HW rendering and with `hardwareAccelerated=false` (a throwaway build). | Whether rendering or the panel is the bottleneck. Real numbers for the hold-repeat cadence |
| E3 | 200 × (+1) on one seat. Photos at 10/25/50/100/200. Repeat in invert (dark) mode. | Ghost build-up rate → default N. Whether dark mode needs a lower N |
| E4 | After E3, compare cleans: region black-flash (try 150/300/500 ms holds), region invert-flash, full `screenRefresh(false,1)`, sidebar slide-up. | Which clean works, how long it takes, the minimum flash hold |
| E5 | Show/hide system bars, a Dialog, a Toast, the IME. | Which of these trigger a full flash (confirms the single-window rule) |
| E6 | Hold-to-repeat with a 100/200/300/500 ms step. | Where updates start to merge or smear. Choose the step |
| E7 | Settings → Display & Input → Auto Refresh on vs off while using our app. | Whether the system Auto Refresh applies to third-party apps and makes our cadence redundant |
| E8 | Large-area change (open/close a drawer, rotate the layout). | Whether the HWC switches to a full flash on its own for big diffs |
| E9 | Sleep/wake with keep-awake off; then 1 h idle with it on, checking battery % before and after. | Redraw on wake. Cost of the keep-awake setting |
| E10 | Log-only reflection probe (debug build): does `View.setEinkUpdateMode` / 5-arg `invalidate` exist? | Whether a per-view waveform hint is even present. Do not ship without a separate decision |

## Main uncertainties

- Which waveform the Ratta HWC gives a third-party window, and whether it diffs per pixel or
  refreshes the whole damaged layer. The community reports conflict: inkread says sideloaded apps
  effectively get full-screen updates only. E1 answers this. If inkread is right, granularity still
  saves CPU but not panel time, and the cleanup cadence matters more.
- Whether an app-drawn black flash cleans as well as the vendor GC refresh (E4).
- The `screenRefresh` call is reverse-engineered and could change or vanish with any firmware update.
  It must stay optional and degrade silently.

## T19b as built

Code: `app/.../eink/` — `ScreenCleaner` (one state machine: `cleanRegion(rect)` / `cleanFull()`),
`CleanCadence` (pure policy, JVM-tested), `CleanComponent` (the Play-screen glue, registered last in
the OVERLAY layer), `SupernoteFullRefresh` + `VendorRefreshGuard`. The stage hook is
`Stage.Observer` (every invalidation, every pointer-down, the end of every entry point with its
`Stage.Cause`) plus `Stage.timersPending(except)` and `Stage.covered(kind, seat)`.

**Constants**

| What | Value | Where |
|---|---|---|
| Ink frame hold | 300 ms | `ScreenCleaner.HOLD_MS` (unchanged from the manual CLEAN) |
| Quiet period before an automatic clean | 2000 ms | `CleanCadence.QUIET_MS` |
| Threshold | the "Screen clean" setting (`fullRefreshEvery`: OFF / 20 / 50 / 100) | debug override `--ei cleanEvery N` |
| Per-seat rule | one seat ≥ threshold and every other seat ≤ threshold / 4 | `CleanCadence.OTHERS_QUIET_DIVISOR` |
| Vendor refresh | off | `SupernoteFullRefresh.USE_VENDOR_REFRESH`; debug `--ez vendorRefresh true` |
| Delay before the vendor call | 300 ms | `ScreenCleaner.VENDOR_DELAY_MS` |

**What counts.** One count per entry point that changed pixels and is a committed touch (release,
hold-repeat step) or an outside game change; timer-driven redraws (readout clearing, effect
keyframes, menu timeout) count once per burst after a user action; touch-down and cancel (press
inversion only) never count. So a tap is ~1, a burst of taps then its commit and effect is taps + 1.
Each count also goes to every seat whose panel the redraw touched (the gem touches all four).

**When.** Due at `count ≥ threshold`. Then a single quiet timer is (re)armed at the end of every entry
point; when it fires the clean runs only if nothing is busy: no pointer down, no press, no clean
running, no other component timer armed (readout, effects, menu idle, KO hold, first-player…), and no
overlay declaring touch zones (menu, dice, settings, history open). Busy → nothing re-armed until the
next entry point (the one that ends the busy state). Nothing is scheduled while OFF or below the
threshold; `onPause` drops the timer (`Events.PAUSE`), `onResume` re-arms if due.

**Scope.** One seat alone (rule above) → black over that seat's panel, then redraw; otherwise full.
Closing a full-table overlay (settings, history) puts one ink keyframe in front of its full redraw
when the setting is on. Manual CLEAN (menu) is the same full clean and works with the setting OFF.
Counts reset after a clean that ran to its end (any kind), on new game and on a threshold change; a
cancelled clean keeps them, so it is retried at the next quiet moment.

**Touch during a clean.** Never blocks input (SPEC rule 4): any pointer-down cancels it, the region is
redrawn in that same frame and the touch acts normally, inside or outside the region, for seat, full,
manual and overlay-close cleans alike.

**Emulator (logic only, emulator-5566).** With `--ez trace true --ei cleanEvery 3`: three taps on one
seat → `eink count=…`, `pending`, the readout commit and its effect hold it off, the seat clean starts
2.0 s after the last effect keyframe and ends 300 ms later; screencap just before vs after the clean is
byte-identical (`cmp`); a tap 6 ms into the black frame cancelled it at once (that build swallowed the
tap; since changed so the tap acts, JVM-tested); the clean was retried 2 s later. Spread taps → full clean, before/after identical. Settings
card open 12 s → no timer; closing it → full ink keyframe, then the table. `cleanEvery 0` → no eink
activity in 12 taps. HOME while pending → no clean in the background; back → clean 2 s later. Every
capture passes `tools/check_1bit.py --scale 4`. Not judged: anything about the panel.

**What to look at on the Nomad** (`A=~/Library/Android/sdk/platform-tools/adb`, serial from
`local/README.md`, `P=au.com.josephmark.dithertracker`):

1. Cadence feel: `$A -s <nomad> shell am start -n $P/.MainActivity --ez trace true --ei cleanEvery 5`,
   `$A -s <nomad> logcat -s PlayView:D | grep eink`. Tap one seat 5×, wait: does the seat flash come
   only after the table is quiet, and does it feel like a deliberate beat rather than a glitch? Is
   2 s the right quiet period (too eager / too late)?
2. Does the 300 ms black hold clean the shadow digits (E3/E4)? Tap one seat ~30× at `cleanEvery 30`,
   photograph under raking light before and after the seat clean. Try the full clean too.
3. Does a seat clean flicker the other seats (E1/E8: whether the HWC escalates a big diff to a full
   flash)?
4. Tap during the black frame: the redraw must come back immediately (and the tap applies).
5. Settings → close with Screen clean on: one black beat, then the table. Does it read as intended or
   as a double flash on top of the HWC's own update?
6. Vendor refresh: `$A -s <nomad> shell getprop ro.product.manufacturer` (the guard requires
   "Supernote"), then relaunch with `--ez vendorRefresh true --ei cleanEvery 5` and trigger a full clean
   (spread taps, or menu → CLEAN). Expect the same flash as the sidebar slide-up; `logcat -s
   SupernoteRefresh` must stay silent (a line there means it failed once and fell back to the black
   flash for the session). Seat cleans always use the black flash.
7. Battery (T20): with the setting on and the table idle, `dumpsys alarm` / CPU stay flat — at most one
   2 s timer after the last action while a clean is due, none otherwise.

## Slow-motion findings (2026-11-14, clips 1–4)

Source: four phone clips of the Nomad (240 fps capture; the exported files are 30 fps at quarter
speed, so one exported frame = 1/120 s — calibrated against the 1000 ms hold repeat, which measures
131 frames). Build on the device predates T55/T56. Measured from frame differences on fixed regions;
the instant of finger contact cannot be seen from the camera angle, so touch-to-ink figures are
estimates from when the finger stops moving.

| Question | Finding |
|---|---|
| E1 partial updates | Confirmed. Nothing outside the touched seat changes during taps, holds or effects. |
| E8 large diffs | A drawer opening and the gem opening/closing do **not** trigger a full-screen flash; they cross-fade (old image ghosted for ~1 frame of the strip) in roughly 70–130 ms. |
| Optical transition time | A press inversion goes light→dark in about 10 frames ≈ 85 ms. The panel's visible change is fast. |
| Touch → first ink | Roughly 0.2–0.25 s (estimate). |
| **Press → result** | **The press inversion stays on screen for 54–56 frames ≈ 450–470 ms on every tap measured (four life taps, the gem diamond), including one where the finger had already left the frame.** That is too constant to be finger time: the panel does not start the release frame on a region until the press frame's update has finished. So a quick tap costs touch → ~0.2 s → inversion → ~0.45 s → result: about 0.7–0.8 s to see the number change or the drawer/menu open. |
| Coalescing | Three quick taps showed one inversion and then jumped straight to −3: frames arriving while the region is busy are dropped and the latest wins. |
| E6 hold repeat | Steps land cleanly at the 1000 ms cadence with the half still inverted; nothing merges or smears. There is room to go faster (a step is visible well inside 500 ms). |
| Effects | Keyframes land as distinct, crisp frames (pow burst, dotted ring); a faint ghost of the ring remains for a moment after it clears. The 3-damage hit waited ~2 s (the readout commit) before the effect began — T55 moves the trigger earlier. |

**Consequence.** Our drawing is not the bottleneck (1–7 ms), nor is the optical transition (~85 ms);
it is the ~450 ms the panel holds a region busy after the press inversion. Levers, in order:
1. Don't spend an update on the inversion for quick taps: show press feedback only once a touch has
   been down longer than a short threshold (≈100–150 ms), so a tap goes straight to its result in one
   update, and a hold still inverts. Action stays on release. (Decision pending — it changes the
   "press = inversion" rule for quick taps.)
2. A faster waveform for our window (vendor refresh mode, E4/E10) would shorten the busy time itself.
   (T58 built this: "Display modes", below.)
3. Press feedback that doesn't overlap the pixels the result changes would let both updates run at
   once — hard for life halves (the numeral is inside the half), plausible for the drawer tab and gem.

Still to film: clean flash (E3/E4), effects gallery and lightest dithers after T55 lands, stills of
ghost build-up, system Auto Refresh on/off (E7).

## Display modes (T58, as built)

Replaces the 2026-11-14 experiment (`--ei einkMode N` calling `View.setEinkUpdateMode` on the root by
reflection). Sources: the Nomad's `framework.jar` and the ODM's `htfyview.jar`, read with `dexdump`
(names, constants and hidden-API flags only; nothing of theirs is in this repo), plus logcat on the
Nomad. Firmware `Chauvet.E103.2610191001`.

### What the framework does [device]

- **The mode lives on the View.** `android.view.View` carries four vendor fields: show mode, data
  mode, an "A2 gate" (grey threshold, default 216) and a force-full flag. `getEinkFinalUpdateMode()`
  returns the show mode and `getEinkDataMode()` the data mode.
- **`setEinkUpdateMode(int mode)`** is `setEinkUpdateMode(0, mode)`. The two-argument form is
  **(data mode, show mode)**. If the show mode is unchanged it returns at once: no log, no effect.
  Otherwise it stores it, derives the data mode when the first argument is 0, logs
  `View-HtFy: setEinkUpdateMode:disp=…,DataMode=…`, and tells the view root.
- **Data mode** (`EINK_DATA_MODE_*`) is how the HWC turns our RGBA into panel levels before the
  waveform: A2 = 1 (1-bit, thresholded at the A2 gate), A4 = 2, DEFAULT = 3 (16 grey levels),
  DTH = 5 (dithered). DU, A2, DUX and UIFC (14) get data A2; DU4 (17) gets A4; DTH gets DTH; GC and
  everything else get DEFAULT. So DU on our view means 1-bit data with a 1-bit waveform. That is
  lossless for us because we only ever draw #000 and #FFF. That is why the experiment read back data
  mode 1 for show mode DU.
- **`resetEinkUpdateMode()`** does nothing when no mode is set. Otherwise it clears both modes and
  tells the root. It logs `View-HtFy: resetEinkUpdateMode…` on every call.
- **Where it takes effect.** Stock `ViewRootImpl`'s e-ink hooks are empty stubs. The ODM's subclass
  (`ViewRootImplEink`) implements them. Every `View.invalidate(l, t, r, b)` passes its rect, plus **the
  invalidating view's own mode**, into the vendor's per-frame dirty-rect list. The mode is not
  inherited from parents or the root. Our UI is one custom view set straight as the content view
  (`PlayView`, or the debug `DitherDemoView`), and every redraw is an `invalidate(rect)` of that view,
  so the root's mode covers all our drawing. The decor around it has no mode, but it is not redrawn
  after the first frame.
- **Pending rects follow mode changes.** Changing a view's mode re-labels its rects still waiting
  for the next draw. So a temporary mode (the GC clean) has to stay set until its frame has been
  drawn. We restore after the 300 ms hold.
- **Overlapping rects of different modes** in one frame are merged with a fixed priority: GC (and
  GCX) beat AUTO/DTH, which beat GL, …, A2, then DU last. A GC rect therefore wins over a DU rect
  that overlaps it.
- **`forceEinkFullUpdate()`.** On an attached view this is the eink service's
  `screenRefresh(false, 0)` followed by a full redraw of the window. On a detached view it sets a flag
  so that the view's next invalidation goes out as GC. It is essentially our existing vendor full
  refresh (that one passes mode 1). Not used.
- **Nothing resets an app view's mode.** Only these change it: the view's constructor,
  `resetEinkUpdateMode`, a vendor "bold text" helper (which itself sets DU with A2 data), and, on a
  display rotation, a reset of the **IME's** input view only. So the mode holds across
  pause/resume, sleep/wake and focus changes. Measured below.
- **`SFCommand`** holds global system switches: UI dither, full-auto, gamma, screen freeze,
  `screenRefresh`, and so on. That is global state, so we never touch it. **`GVWindow`** is the
  pen-writing overlay's glue. It is only involved for views that support pen writing, which ours
  does not.
- **Hidden-API flags** (the dex's own lists). These are **SDK** (allowed for every app):
  `setEinkUpdateMode` (both), `resetEinkUpdateMode`, `getEinkFinalUpdateMode`, `getEinkDataMode`,
  `forceEinkFullUpdate` (both), `setEinkA2Gate`, the `EINK_SHOW_MODE_*` constants, and
  `EinkManager.screenRefresh(boolean,int)`. These are **blocked**: the fields, `isEinkModeSet()`,
  and internal helpers. The experiment's `Accessing hidden method … (blacklist, reflection, allowed)`
  line was for `isEinkModeSet()` alone, which its debug readback called. The production code never
  calls it.

### Release builds [device]

The release APK, built with R8 and non-debuggable (the package flags show no `DEBUGGABLE`), was
debug-signed with `-PdebugSignRelease -PappIdSuffix=.t58`, installed beside the owner's app,
launched with `--ez trace true`, and uninstalled afterwards. Logcat:
`View-HtFy: setEinkUpdateMode:disp=1,DataMode=1@…` then `EinkViewMode: create: mode=1 set=true
read=1`, with **no hidden-API warning of any kind**. The methods are on the SDK list, so a release
build is allowed exactly as a debug one is. Nothing extra was needed: no indirection, no
`hidden_api_policy` change, no debuggable variant. R8 keeps the lookups: they are string constants
naming framework methods (`dexdump` of the release APK shows `"setEinkUpdateMode"`,
`"resetEinkUpdateMode"`, `"getEinkFinalUpdateMode"`), and R8 never renames framework members. It
does rename `PlayView`, which doesn't matter here.

### As built

| What | Where |
|---|---|
| Guard: Supernote only (`Build.MANUFACTURER`), the three methods resolved once, lazily, by reflection; any failure → logged once (tag `EinkViewMode`), disabled for the session; never throws | `eink/EinkViewMode.kt` (`EinkViewMode`, `ViewModeGuard`, JVM-tested with injected resolvers) |
| Policy: the "Display" setting (FAST = DU, NORMAL = `resetEinkUpdateMode()`) or the debug override; the GC clean's switch | `eink/EinkDisplay.kt` (JVM-tested) |
| Applied on create (right after `setContentView`; attach state doesn't matter, the field is read per invalidation), again on every resume and on every setting change. Debug builds (or `--ez trace true`) log the readback at create / resume / pause / focus | `MainActivity.startEinkDisplay` |
| Setting: `Display [FAST][NORMAL]`, FAST by default. On a Supernote it sits on a SCREEN page behind a › on "Screen clean": its own row would push the main card onto two pages at S=4. Elsewhere Screen clean has no › and Display doesn't exist | `settings/SettingsTable.screenClean` / `display` |
| Persisted as `AppSettings.fastDisplay`: a bit of the v1 settings flags set for NORMAL, so older saves load as FAST and the format version stays 5 | `core/serial/SaveCodec` |

**Measured on the Nomad** (side-by-side `.t58` builds, removed afterwards):

- FAST: `create: mode=1 read=1`. HOME and back, then sleep/wake: `pause` and `focus lost` read 1. The
  re-apply on `resume` produced no `View-HtFy` line, so the mode was still 1 and the call was a no-op.
  The mode sticks; the re-apply is belt and braces.
- NORMAL chosen in settings: `resetEinkUpdateMode:disp=1,…` then `setting: mode=0 read=0`. It
  survives a restart (`create: mode=0`).
- `--ei einkMode 4`: `disp=4,DataMode=1` (A2).
- Our overlays (menu, settings) are drawn inside the one view, so they share its mode. Opening and
  closing settings changed nothing.
- `adb shell dumpsys activity top` shows a view's `…/<show>,<data>` at the end of its line, e.g.
  `PlayView{… 0,0-1872,1404/1,1}`. That is a read-only way to check any running build.
- Emulator (not a Supernote): `set=false read=-1` throughout, no reflection, no warnings, nothing
  crashes, Screen clean has no ›.

### Cleans in a fast mode

The automatic clean (cadence, scope and defaults unchanged) still uses the black ink frame by default.
The alternative, behind the debug extra **`--ez gcClean true`**, is a **GC clean**
(`ScreenCleaner.waveform`). It draws no ink frame. Instead the view is switched to GC, the seat (or
the screen) is redrawn as it is, and after the 300 ms hold the view goes back to its mode. A GC
update drives every pixel in its rect through black and white, which is what a clean is for: one
panel update instead of the flash's two. A touch cancels it and restores the mode at once. When the
switch is unavailable it falls back to the ink frame. Full cleans still prefer `--ez vendorRefresh
true` when both are on. Code-wise it is the smallest option: one more branch in the existing state
machine and one more timer use. `forceEinkFullUpdate()` was the other candidate, but it is full-screen
only and just the vendor refresh again.

On the Nomad (`--ez gcClean true --ei cleanEvery 1 --ez trace true`): a seat clean logged
`setEinkUpdateMode:disp=2,DataMode=3`, then the vendor HAL's `eink: fixXGC(…)` line for that frame
(no such line for ordinary DU taps), then DU restored 300 ms later and `eink clean end: seat 2 (gc)`.
Closing settings ran a full GC clean the same way.

**Needs the owner's eyes** (we cannot see the panel):
1. **FAST vs NORMAL.** Is a quick tap's result faster than the ~450 ms press→result wait measured
   above? Does ghosting (shadow digits) build up faster in FAST? Is the gem or drawer opening clean
   in DU?
2. **GC clean vs ink flash, in FAST.** Relaunch the debug build with `--ez gcClean true --ei
   cleanEvery 5`, tap one seat five times, wait. Does the GC redraw visibly flash and clear the
   shadows as well as the black flash does? If the panel skips unchanged pixels even under GC, the GC
   clean would do nothing; then keep the flash. Compare with the default (no extra).
3. Whether FAST wants a lower Screen clean threshold than 20.
4. Other modes for comparison: `--ei einkMode 4` (A2), `11` (DUX), `7` (AUTO), `0` (none).

Try it on a debug build: `adb shell am start -S -n au.com.josephmark.dithertracker/.MainActivity
--ez trace true [--ei einkMode N] [--ez gcClean true]`, then `adb logcat -s EinkViewMode:D
View-HtFy:D PlayView:D`.
