package au.com.josephmark.dithertracker.eink

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.LogEntry
import au.com.josephmark.dithertracker.core.LogEvent
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.ZoneSink

/**
 * E-ink cleanup on the Play screen (T19b): the one [ScreenCleaner] (manual CLEAN, automatic cadence,
 * overlay close) plus the [CleanCadence] fed by the stage's frames ([Stage.Observer]). Registered last
 * in the OVERLAY layer so its ink frame covers everything.
 *
 * - **Counting** happens here and only here: every invalidation the stage unions is mapped to the
 *   seats whose panel it touches, and every entry point's end is reported with its cause.
 * - **Busy** (no automatic clean): a pointer down, a press, a clean running, any other component's
 *   timer armed (effects, readouts, menu idle, KO hold…) or any overlay declaring touch zones (menu,
 *   dice, settings, history open).
 * - **Overlay close**: when a cover that hid the whole table (settings, history) goes away, the full
 *   redraw gets an ink keyframe first — only while the setting is on.
 * - **Manual**: [Events.CLEAN] (the menu) cleans the whole screen, setting on or off.
 * - While a clean runs, touches inside its region are swallowed (a block zone); any pointer-down
 *   cancels it and the region is redrawn in the same frame.
 * - Resets on new game and player-count change; [Events.PAUSE] drops the quiet timer.
 */
class CleanComponent : Component(Layer.OVERLAY, Kind.OVERLAY, timerCount = TIMERS),
    Stage.Observer, ScreenCleaner.Host, CleanCadence.Host {

    lateinit var cleaner: ScreenCleaner
        private set

    lateinit var cadence: CleanCadence
        private set

    private var play: PlayLayout? = null

    /** Pointers physically down (from the stage's down / up / cancel). */
    private var pointers = 0

    /** True while this component invalidates its own region (not a panel-changing action). */
    private var self = false

    /** The seat being cleaned, or [CleanCadence.FULL]. */
    private var cleaning = CleanCadence.FULL

    /** A full-table cover (settings, history) was on at the last cover change. */
    private var tableCovered = false

    /** The head of the game log at the last state change (a fresh `NewGameStarted` head = new game). */
    private var logHead: LogEntry? = null

    private val probe = object : ZoneSink {
        var any = false
        override fun add(local: Int, rect: IRect) { any = true }
        override fun block(rect: IRect) { any = true }
    }

    override fun onAdded() {
        cleaner = ScreenCleaner(timers, TIMER_CLEAN, this)
        cadence = CleanCadence(timers, TIMER_QUIET, this, stage.seats)
        stage.observer = this
    }

    override fun onAttach() {
        cleaner.vendorFull = EinkConfig.vendorFull
        cadence.setThreshold(threshold(stage.settings.current))
        logHead = stage.session.game.log.firstOrNull()
        tableCovered = stage.covered(Kind.STATUS, 0)
        pointers = 0
    }

    override fun onDetach() {
        cleaner.cancel()
        cadence.timersCancelled() // the stage cancels every timer after this
        pointers = 0
    }

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        this.play = play
        cleaner.cancel()
        cleaner.screen.set(0, 0, play.grid.width, play.grid.height)
    }

    // ---- drawing / touch -----------------------------------------------------------------------

    override fun draw(f: Frame, seat: Int) {
        if (cleaner.black && f.hit(cleaner.region)) cleaner.draw(f.r)
    }

    override fun zones(seat: Int, z: ZoneSink) {
        if (cleaner.active) z.block(cleaner.region)
    }

    // ---- notifications -------------------------------------------------------------------------

    override fun onTimer(id: Int) {
        when (id) {
            TIMER_CLEAN -> cleaner.onTimer(id)
            TIMER_QUIET -> cadence.onTimer(id)
        }
    }

    override fun onEvent(event: Int, seat: Int) {
        when (event) {
            Events.CLEAN -> startFull("manual")
            Events.PAUSE -> {
                cleaner.cancel()
                cadence.pause()
            }
            Events.RESUME -> cadence.resume()
        }
    }

    override fun onSettingsChanged(old: AppSettings, new: AppSettings) {
        if (old.fullRefreshEvery != new.fullRefreshEvery) cadence.setThreshold(threshold(new))
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        val head = stage.session.game.log.firstOrNull()
        if (head !== logHead) {
            logHead = head
            if (head != null && head.event == LogEvent.NewGameStarted) {
                cadence.reset()
                trace("eink new game: counts reset")
            }
        }
    }

    override fun onPlayersChanged() {
        cleaner.cancel()
        cadence.reset()
    }

    override fun onCoversChanged() {
        val now = stage.covered(Kind.STATUS, 0)
        if (tableCovered && !now && cadence.threshold > 0) startFull("overlay closed")
        tableCovered = now
    }

    private fun startFull(why: String) {
        cleaning = CleanCadence.FULL
        trace("eink clean start: full ($why)")
        cleaner.cleanFull()
    }

    // ---- Stage.Observer ------------------------------------------------------------------------

    override fun onInvalidate(rect: IRect) {
        if (self) return
        cadence.touched(seatMask(rect))
    }

    override fun onPointerDown() {
        pointers++
        if (cleaner.active) {
            trace("eink clean cancelled by touch")
            cleaner.cancel()
        }
    }

    override fun onSettled(cause: Int) {
        when (cause) {
            Stage.Cause.TOUCH_UP -> if (pointers > 0) pointers--
            Stage.Cause.TOUCH_CANCEL -> pointers = 0
        }
        cadence.settled(
            when (cause) {
                Stage.Cause.TOUCH_UP, Stage.Cause.HOLD, Stage.Cause.STATE -> CleanCadence.CAUSE_ACTION
                Stage.Cause.TIMER -> CleanCadence.CAUSE_TIMER
                else -> CleanCadence.CAUSE_OTHER
            },
        )
    }

    private fun seatMask(r: IRect): Int {
        val l = play ?: return 0
        var m = 0
        val n = minOf(l.playerCount, stage.seats)
        for (s in 0 until n) {
            val p = l.panels[s]
            if (r.left < p.right && p.left < r.right && r.top < p.bottom && p.top < r.bottom) m = m or (1 shl s)
        }
        return m
    }

    // ---- ScreenCleaner.Host --------------------------------------------------------------------

    override fun redraw(region: IRect) {
        self = true
        try {
            stage.invalidate(region)
        } finally {
            self = false
        }
    }

    override fun onCleaned(full: Boolean) {
        trace("eink clean end: ${if (full) "full" else "seat $cleaning"}${if (full && !cleaner.black && cleaner.vendorFull != null) " (vendor)" else ""}")
        cadence.cleaned(if (full) CleanCadence.FULL else cleaning)
    }

    // ---- CleanCadence.Host ---------------------------------------------------------------------

    override fun isBusy(): Boolean =
        pointers > 0 || stage.input.pressedCount > 0 || cleaner.active ||
            stage.timersPending(this) || overlayTakesInput()

    override fun clean(seat: Int) {
        val l = play
        if (seat == CleanCadence.FULL || l == null || seat >= l.playerCount) {
            startFull("cadence")
            return
        }
        cleaning = seat
        trace("eink clean start: seat $seat")
        cleaner.cleanRegion(l.panels[seat])
    }

    override val traceOn: Boolean get() = stage.log != null

    override fun trace(msg: String) {
        stage.log?.invoke(msg)
    }

    /** Whether any other visible overlay declares a touch zone (it is open and owns input). */
    private fun overlayTakesInput(): Boolean {
        val cs = stage.components
        for (i in cs.indices) {
            val c = cs[i]
            if (c === this || c.layer != Layer.OVERLAY || !stage.isVisible(c, Stage.SCREEN)) continue
            probe.any = false
            c.zones(Stage.SCREEN, probe)
            if (probe.any) return true
        }
        return false
    }

    private fun threshold(s: AppSettings): Int {
        val o = EinkConfig.cleanEvery
        return if (o >= 0) o else s.fullRefreshEvery
    }

    companion object {
        const val TIMER_CLEAN = 0
        const val TIMER_QUIET = 1
        const val TIMERS = 2
    }
}

/**
 * Process-wide e-ink switches, set once by `MainActivity` before the Play screen attaches.
 */
object EinkConfig {
    /** Debug override of the "Screen clean" threshold (`--ei cleanEvery N`); < 0 = use the setting. */
    var cleanEvery: Int = -1

    /** The vendor full refresh (`SupernoteFullRefresh`) when enabled, else null (app flash only). */
    var vendorFull: ScreenCleaner.FullRefresh? = null
}
