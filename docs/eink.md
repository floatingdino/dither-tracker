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
3. **Waveform selection (A2/DU fast modes) and true dirty-rect panel updates are not reachable from a
   sideloaded app [community].** Don't design around them.
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
| `View.setEinkUpdateMode(int,int)`, 5-arg `View.invalidate(l,t,r,b,waveform)` | Reported to exist in the Ratta framework and used by Ratta's Notes app ([readr](https://github.com/hunterchen7/readr/blob/HEAD/apps/mobile/android/app/src/main/java/com/readr/app/EpdModeModule.kt)). Whether it has any effect for a third-party window is not verified. | [unknown] |
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
  binary waveforms (A2/DU) would be lossless. If Ratta ever exposes them, or the HWC picks them
  automatically for B/W regions, we benefit for free. Black/white transitions are also the ones a
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

Not recommended: A2/DU mode selection, `View.setEinkUpdateMode`, `/dev/ebc`, root, or anything that
writes system properties. Revisit only if Ratta documents an API.

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
