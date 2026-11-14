package au.com.josephmark.dithertracker

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import au.com.josephmark.dithertracker.data.Persistence
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.layout.Arrangements
import au.com.josephmark.dithertracker.eink.EinkConfig
import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.eink.EinkDisplay
import au.com.josephmark.dithertracker.eink.EinkViewMode
import au.com.josephmark.dithertracker.eink.ScreenCleaner
import au.com.josephmark.dithertracker.eink.SupernoteFullRefresh
import au.com.josephmark.dithertracker.fx.FxDebug
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayView
import au.com.josephmark.dithertracker.tools.ToolsDebug
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var root: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent?.getBooleanExtra("trace", false) == true
        if (BuildConfig.DEBUG) strictMode()
        val t0 = SystemClock.elapsedRealtimeNanos()
        // Once per process: load the save synchronously (a few ms) so the first frame is the restored
        // game, then save changes in the background. No-op on activity recreation. The one deliberate
        // main-thread disk read (a ~200-byte file; docs/performance.md has its cost), so StrictMode is
        // told to allow it and reports anything else.
        val policy = StrictMode.allowThreadDiskReads()
        try {
            Persistence.start(applicationContext)
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
        val t1 = SystemClock.elapsedRealtimeNanos()
        // Debug/QA hooks (not reachable from the UI). "Debuggable builds only" ones are gated on the
        // compile-time BuildConfig.DEBUG, so R8 drops them (and DitherDemoView) from release; `inverted`
        // and `trace` stay in release (harmless; `trace` is how release onDraw times are measured).
        //   --ez inverted true  start in dark mode (overrides the saved setting either way)
        //   --ez demo true      (debuggable builds only) the T04/T05 engine + type demo instead of the Play
        //                       screen (portrait)
        //   --ez trace true     log Play onDraw clip + duration (tag PlayView)
        //   --es partners 1,3   (debuggable builds only) set `partner` on exactly these seats (T30/T32
        //                       QA until T13 adds a UI for it); an empty string clears every partner
        //   --el seed N         (debuggable builds only) seed the commander-mode quip picker, so
        //                       `tools/capture_scenarios.sh` captures are reproducible
        //   --es fx <name|all>  (debuggable builds only) effects gallery (T28c): sets every life to
        //                       --ei fxLife N (default 37) and plays that effect / cycles them all on
        //                       all four seats, looping until a touch; see fx/README.md
        //   --ef fxHold X       (debuggable builds only) scale every effect hold by X (FxPlayer.holdScale)
        //   --es displaySpeed fast|slow  (debuggable builds only) force eink/DisplaySpeed (T59): how many
        //                       keyframes the dice / first-player presentations play
        //   --ef diceHold X     (debuggable builds only) scale every dice / coin / first-player presentation
        //                       hold by X (T52, tools.ToolsDebug), so a screencap lands in each keyframe
        //   --ei cleanEvery N   (debuggable builds only) automatic screen-clean threshold N (overrides the
        //                       "Screen clean" setting; 0 = off), to trigger it with a few taps (T19b)
        //   --ez vendorRefresh true  (debuggable builds only) full cleans use the Supernote vendor refresh
        //                       instead of the black flash (SupernoteFullRefresh; off by default)
        //   --ei einkMode N     (debuggable builds only) pin the root view's e-ink mode instead of the Display
        //                       setting (T58): 0 none, 1 DU (= FAST), 2 GC, 4 A2, 7 AUTO, 11 DUX; with --ez
        //                       trace true (or any debug build) tag EinkViewMode logs the mode read back
        //   --ez gcClean true   (debuggable builds only) cleans redraw in the GC waveform instead of the black
        //                       ink frame (T58, ScreenCleaner.waveform; full cleans still prefer vendorRefresh)
        //   --ei players N      (debuggable builds only) start a new game at N seats (2..6) and choose N in
        //                       settings (T11 captures); applied before --es partners / --es lives
        //   --es lives 40,0,12  (debuggable builds only) set each seat's life in order (T11 captures: a KO)
        //   --ei turn S         (debuggable builds only) start turn tracking from seat S (T17 captures), the
        //                       clock backdated --ei turnAgo M minutes; --ei turnPass N then passes N times,
        //                       the last pass --ei turnPassAgo M minutes ago (default 0)
        //   --es turns off|on|game|turn  (debuggable builds only) the Turns setting (T17)
        //   --es layout ID      (debuggable builds only) choose arrangement ID (e.g. 3-pyramid) for its
        //                       player count (T53; with --ei players N for that count it is the table's)
        //   --ei pressDelay MS  (debuggable builds only) how long a touch must be down before its zone shows
        //                       the press inversion (T57; default PlayInput.PRESS_DELAY_MS, 0 = on touch-down)
        val inverted = if (intent?.hasExtra("inverted") == true) {
            intent.getBooleanExtra("inverted", false)
        } else {
            SettingsHolder.shared.current.inverted
        }
        applyDebugPlayers()
        applyDebugPartners()
        applyDebugLives()
        applyDebugToggles()
        applyDebugTurns()
        applyDebugFx()
        applyEink()
        root = if (BuildConfig.DEBUG && intent?.getBooleanExtra("demo", false) == true) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DitherDemoView(this).apply { setInverted(inverted) }
        } else {
            // The game lives in GameSession.shared (process memory, restored from disk by Persistence).
            PlayView(this, GameSession.shared, debugRandom() ?: Random(System.nanoTime())).apply {
                this.trace = trace
                setInverted(inverted)
                if (BuildConfig.DEBUG && intent?.hasExtra("pressDelay") == true) {
                    pressDelayMillis = intent.getIntExtra("pressDelay", 0).coerceAtLeast(0).toLong()
                }
            }
        }
        setContentView(root)
        startEinkDisplay(trace)
        if (trace) {
            val t2 = SystemClock.elapsedRealtimeNanos()
            Log.d(
                "PlayView",
                "onCreate: persistence ${(t1 - t0) / 1000}us, the rest ${(t2 - t1) / 1000}us; " +
                    "${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()}ms after process start",
            )
        }
    }

    /**
     * Debug builds (T20): StrictMode logs (tag `StrictMode`) any disk or network access on the main
     * thread and leaked closeables; release builds never install it.
     */
    private fun strictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build(),
        )
    }

    /** Debug-only capture hook: `--el seed N` makes the commander-mode quip picks reproducible. */
    private fun debugRandom(): Random? {
        if (!BuildConfig.DEBUG) return null
        if (intent?.hasExtra("seed") != true) return null
        return Random(intent.getLongExtra("seed", 0L))
    }

    /** Debug-only effects hooks (T28c): `--es fx <name|all>`, `--ei fxLife N`, `--ef fxHold X`. */
    private fun applyDebugFx() {
        if (!BuildConfig.DEBUG) return
        val i = intent ?: return
        FxDebug.gallery = i.getStringExtra("fx")
        FxDebug.life = i.getIntExtra("fxLife", 37)
        FxDebug.holdScale = if (i.hasExtra("fxHold")) i.getFloatExtra("fxHold", 1f).coerceAtLeast(0f) else 1f
        ToolsDebug.holdScale = if (i.hasExtra("diceHold")) i.getFloatExtra("diceHold", 1f).coerceAtLeast(0f) else 1f
    }

    /** E-ink cleanup switches (T19b): the vendor refresh (constant, or the debug extra) and the debug threshold. */
    private fun applyEink() {
        val debug = BuildConfig.DEBUG
        val i = intent
        EinkConfig.cleanEvery = if (debug && i?.hasExtra("cleanEvery") == true) i.getIntExtra("cleanEvery", -1) else -1
        val vendor = SupernoteFullRefresh.USE_VENDOR_REFRESH ||
            (debug && i?.getBooleanExtra("vendorRefresh", false) == true)
        val ctx = applicationContext
        EinkConfig.vendorFull = if (vendor) ScreenCleaner.FullRefresh { SupernoteFullRefresh.tryFullRefresh(ctx) } else null
        // T58: before the Play view (and its settings table) is built — the Display row is Supernote-only.
        EinkConfig.fastDisplayAvailable = EinkViewMode.available
    }

    /**
     * The root view's e-ink waveform (T58, eink/EinkDisplay): the Display setting (FAST = DU) or the debug
     * `--ei einkMode N`, set now and again on resume and on every setting change; `--ez gcClean true`
     * makes cleans use the GC waveform. A no-op off a Supernote (EinkViewMode's guard).
     */
    private fun startEinkDisplay(trace: Boolean) {
        val debug = BuildConfig.DEBUG
        val i = intent
        val override = if (debug && i?.hasExtra("einkMode") == true) i.getIntExtra("einkMode", 0) else EinkDisplay.NO_OVERRIDE
        val view = root
        val port = object : EinkDisplay.Port {
            override fun set(mode: Int) = EinkViewMode.set(view, mode)
            override fun read() = EinkViewMode.read(view)
        }
        val d = EinkDisplay(port, override, if (debug || trace) { msg -> Log.d("EinkViewMode", msg) } else null)
        display = d
        d.apply(SettingsHolder.shared.current.fastDisplay, "create")
        // T59: the display speed features read (eink/DisplaySpeed): FAST off e-ink, or with the fast waveform set.
        DisplaySpeed.einkPanel = DisplaySpeed.isEinkDevice(Build.MANUFACTURER, Build.BRAND, Build.MODEL)
        DisplaySpeed.fastMode = DisplaySpeed.FastMode { DisplaySpeed.isFastWaveform(d.mode) && EinkViewMode.available }
        DisplaySpeed.override = if (debug) DisplaySpeed.parse(i?.getStringExtra("displaySpeed")) else DisplaySpeed.NO_OVERRIDE
        EinkConfig.gcClean = if (debug && i?.getBooleanExtra("gcClean", false) == true) d else null
    }

    private var display: EinkDisplay? = null

    private val displaySetting = SettingsHolder.Listener { old, new ->
        if (old.fastDisplay != new.fastDisplay) display?.apply(new.fastDisplay, "setting")
    }

    /**
     * Debug-only capture hooks (T11, T53): `--es layout ID` chooses arrangement ID for its count (as the
     * layout picker does); `--ei players N` starts a new game at N seats and selects N in settings.
     */
    private fun applyDebugPlayers() {
        if (!BuildConfig.DEBUG) return
        val i = intent ?: return
        val layout = i.getStringExtra("layout")?.let { Arrangements.byId(it) }
        if (layout != null) {
            val s = SettingsHolder.shared.current
            SettingsHolder.shared.update(s.copy(layout = Arrangements.choose(s.layout, layout.players, layout.id)))
        }
        if (!i.hasExtra("players")) return
        val n = i.getIntExtra("players", 4)
        if (n !in AppSettings.PLAYER_COUNT_RANGE) return
        SettingsHolder.shared.update(SettingsHolder.shared.current.copy(playerCount = n))
        val session = GameSession.shared
        if (session.state.players.size != n) session.dispatch(Action.NewGameFor(n), System.currentTimeMillis())
    }

    /** Debug-only capture hook (T11): `--es lives 40,0,12` sets each seat's life, in seat order. */
    private fun applyDebugLives() {
        if (!BuildConfig.DEBUG) return
        val spec = intent?.getStringExtra("lives") ?: return
        val session = GameSession.shared
        val values = spec.split(',').map { it.trim().toIntOrNull() }
        for ((seat, v) in values.withIndex()) {
            val p = session.state.players.getOrNull(seat) ?: break
            if (v != null && p.life != v) session.dispatch(Action.SetLife(p.id, v), System.currentTimeMillis())
        }
    }

    /** Debug-only QA hook: `--es partners 1,3` sets the partner flag on exactly those seats. */
    private fun applyDebugPartners() {
        if (!BuildConfig.DEBUG) return
        val spec = intent?.getStringExtra("partners") ?: return
        val seats = spec.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        val session = GameSession.shared
        for (p in session.state.players) {
            val want = p.seat in seats
            if (p.partner != want) session.dispatch(Action.SetPartner(p.id, want), System.currentTimeMillis())
        }
    }

    /**
     * Debug-only capture hook (T14): `--es toggles m,ib,,r` gives each seat (in seat order) its toggles —
     * m monarch, i initiative, b city's blessing, r ring-bearer; `--es daynight day|night`;
     * `--es poison 3,0,9` / `--es tax 2,0,4` set those values per seat (for peeks beside the badges).
     */
    private fun applyDebugToggles() {
        if (!BuildConfig.DEBUG) return
        val i = intent ?: return
        val session = GameSession.shared
        fun d(a: Action) = session.dispatch(a, System.currentTimeMillis())
        fun perSeat(key: String, f: (Player, String) -> Unit) {
            val spec = i.getStringExtra(key) ?: return
            for ((seat, v) in spec.split(',').withIndex()) f(session.state.players.getOrNull(seat) ?: break, v.trim())
        }
        perSeat("toggles") { p, v ->
            for ((c, t) in listOf('m' to Toggle.MONARCH, 'i' to Toggle.INITIATIVE, 'b' to Toggle.CITYS_BLESSING, 'r' to Toggle.RING_BEARER)) {
                if (c in v) d(Action.SetToggle(p.id, t, true))
            }
        }
        perSeat("poison") { p, v -> v.toIntOrNull()?.let { d(Action.SetCounter(p.id, Counter.POISON, it)) } }
        perSeat("tax") { p, v -> v.toIntOrNull()?.let { d(Action.ChangeCommanderTax(p.id, Commander.PRIMARY, it - p.commanderTax.primary)) } }
        when (i.getStringExtra("daynight")) {
            "day" -> d(Action.SetDayNight(DayNight.DAY))
            "night" -> d(Action.SetDayNight(DayNight.NIGHT))
        }
    }

    /**
     * Debug-only capture hook (T17): `--es turns off|on|game|turn` sets the Turns setting; `--ei turn S`
     * starts tracking from seat S with the clock `--ei turnAgo M` minutes back, then `--ei turnPass N`
     * passes N times, the last one `--ei turnPassAgo M` minutes back (so the timer shows minutes at once).
     */
    private fun applyDebugTurns() {
        if (!BuildConfig.DEBUG) return
        val i = intent ?: return
        val h = SettingsHolder.shared
        when (i.getStringExtra("turns")) {
            "off" -> h.update(h.current.copy(turnTracker = false, turnTimer = TurnTimer.OFF))
            "on" -> h.update(h.current.copy(turnTracker = true, turnTimer = TurnTimer.OFF))
            "game" -> h.update(h.current.copy(turnTracker = true, turnTimer = TurnTimer.GAME))
            "turn" -> h.update(h.current.copy(turnTracker = true, turnTimer = TurnTimer.TURN))
        }
        if (!i.hasExtra("turn")) return
        val session = GameSession.shared
        val p = session.state.players.getOrNull(i.getIntExtra("turn", 0)) ?: return
        val now = System.currentTimeMillis()
        val ago = i.getIntExtra("turnAgo", 0) * 60_000L
        session.dispatch(Action.SetStartingPlayer(p.id), now - ago)
        val passes = i.getIntExtra("turnPass", 0)
        val passAgo = i.getIntExtra("turnPassAgo", 0) * 60_000L
        for (n in 1..passes) session.dispatch(Action.NextTurn, if (n == passes) now - passAgo else now - ago)
    }

    /** Keep-awake (T12): `FLAG_KEEP_SCREEN_ON` on the play window only while resumed and the setting is on. */
    private val keepAwake = SettingsHolder.Listener { old, new ->
        if (old.keepScreenAwake != new.keepScreenAwake) applyKeepAwake(new.keepScreenAwake)
    }

    private fun applyKeepAwake(on: Boolean) {
        if (on && root is PlayView) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onResume() {
        super.onResume()
        SettingsHolder.shared.addListener(keepAwake)
        applyKeepAwake(SettingsHolder.shared.current.keepScreenAwake)
        SettingsHolder.shared.addListener(displaySetting)
        display?.apply(SettingsHolder.shared.current.fastDisplay, "resume")
        (root as? PlayView)?.onHostResume()
    }

    override fun onPause() {
        super.onPause()
        SettingsHolder.shared.removeListener(keepAwake)
        applyKeepAwake(false)
        SettingsHolder.shared.removeListener(displaySetting)
        display?.check("pause")
        (root as? PlayView)?.onHostPause() // no e-ink cleanup timer while in the background
        // Home, sleep, swipe-away: write anything still waiting on the debounce before we can be killed.
        Persistence.flush()
    }

    override fun onStart() {
        super.onStart()
        (root as? PlayView)?.onHostStart()
    }

    override fun onStop() {
        super.onStop()
        (root as? PlayView)?.onHostStop() // timers that still fire draw nothing until visible again
        Persistence.flush() // no-op unless something changed since onPause
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
        display?.check(if (hasFocus) "focus" else "focus lost")
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun enterImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            root.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
}
