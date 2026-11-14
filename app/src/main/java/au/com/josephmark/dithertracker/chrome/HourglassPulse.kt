package au.com.josephmark.dithertracker.chrome

import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.TurnState
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.core.serial.Tracker
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.play.Diamond
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Stage

/**
 * The diamond's hourglass pulse (T66, Sam: "a short egg timer/hourglass animation that replaces the
 * menu glyph every 60 seconds when either timer mode is on"): the quiet "time is passing" mark that
 * replaced the turn tab's live minutes. Once a minute of **active** time — on the minute boundaries
 * of the current turn (TURN: restarts at each pass) or of the game (GAME), from core's active-clock
 * stamps — the diamond's glyph is replaced by a few held keyframes of an egg timer, then the glyph is back.
 *
 * - **Keyframes** (`design/icons/hourglass-1..4`, SPEC rule 4, uneven holds, the read frame longest):
 *   FAST sand on top 150 → running 350 → below 200 → on its side (the flip) 150 ms; SLOW on top 300
 *   → running 450 → below 300 ms. About a second, then the glyph again.
 * - **Every diamond state**: the plain diamond (the `MENU` dither: the egg timer on its cut-out halo,
 *   as the bars), day (paper: ink timer) and night (ink: paper timer) — the sun or moon is the
 *   glyph there and gives way for the second too, in that state's polarity (consistent: wherever the
 *   diamond is, the pulse looks the same). Only the glyph box ([box]: the bars' plate or the sun /
 *   moon, the larger) is drawn and redrawn; nothing outside it changes.
 * - **Never**: with Turns OFF / ON, before tracking starts, once there is a winner, while paused, with
 *   the gem open (its close X is on the diamond: [Events.MENU_OPEN] … [Events.MENU_CLOSED]), in
 *   commander mode, under overlays and during the first-player roll-off (covers hide [Kind.CHROME] on
 *   the screen). A press on the diamond, or the menu opening, ends a running pulse at once (the
 *   glyph returns in the same frame); the menu still opens (the diamond keeps its zone).
 * - **One timer** ([TICK]): the next minute boundary, then each keyframe's hold, then the next
 *   boundary. Armed only while all of the above allow; cancelled on pause, re-armed on resume to the
 *   next boundary from the persisted active clock. The e-ink clean cadence counts a timer-driven
 *   frame at most once per burst like any other timer redraw (the pulse touches no seat panel, so no
 *   seat is marked dirty by it).
 */
class HourglassPulse(
    private val speed: DisplaySpeed.Source = DisplaySpeed.Source { DisplaySpeed.current() },
) : Component(Layer.SCREEN, Kind.CHROME, timerCount = TIMERS) {

    private var l: PlayLayout? = null

    /** The glyph box (screen): what each keyframe paints and redraws. */
    val box = IRect()
    private val icon = IRect()

    /** The keyframe showing (an index into the running sequence), or −1. */
    var frame = -1
        private set

    /** The running sequence's icons and holds (FAST or SLOW, chosen when it starts). */
    private var icons: Array<Icon> = FAST_ICONS
    private var holds: LongArray = FAST_HOLDS

    /** Whether [TICK] is armed (tests) and, between pulses, the active-clock boundary it is for. */
    var armed = false
        private set
    private var armedFor = Long.MIN_VALUE

    /** Pulses played so far (tests). */
    var pulses = 0
        private set

    private var paused = false
    private var menuOpen = false

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
        val s = PlayLayout.ICON * play.tk
        val h = play.halo
        val ic = icon.setXywh(play.diamondCx - s / 2, play.diamondCy - s / 2, s, s)
        val b = play.menuBarsBacking
        box.set(
            minOf(b.left, ic.left - h), minOf(b.top, ic.top - h),
            maxOf(b.right, ic.right + h), maxOf(b.bottom, ic.bottom + h),
        )
        stop(invalidate = false)
        arm()
    }

    override fun prewarm(f: Frame) {
        val l = l ?: return
        f.icons.prewarm(ALL_ICONS, l.tk)
    }

    // ---- when ---------------------------------------------------------------------------------------

    /** Whether the pulse may run now. */
    private fun eligible(): Boolean {
        if (l == null || paused || menuOpen) return false
        val a = stage.settings.current
        if (!a.turnTracker || a.turnTimer == TurnTimer.OFF) return false
        val t = stage.session.state.turn
        if (!t.started || !t.stamped || t.endedAt != TurnState.NO_TIME) return false
        return visible(Stage.SCREEN)
    }

    /** The active-clock start the minutes count from. */
    private fun origin(t: TurnState): Long = if (stage.settings.current.turnTimer == TurnTimer.TURN) t.turnStartedAt else t.startedAt

    /** Arms [TICK] for the next minute boundary (no-op when already armed for it), or drops it. */
    private fun arm() {
        if (frame >= 0) return // a pulse is running: its own holds keep the timer
        if (!eligible()) {
            if (armed) timers.cancel(TICK)
            armed = false
            return
        }
        val start = origin(stage.session.state.turn)
        val now = stage.session.activeAt(stage.now())
        val boundary = start + ((now - start).coerceAtLeast(0) / MINUTE + 1) * MINUTE
        if (armed && boundary == armedFor) return
        // Active ms to the boundary = wall ms while resumed (the clock runs 1:1 then).
        timers.schedule(TICK, boundary - now)
        armedFor = boundary
        armed = true
    }

    /** Ends a running pulse (the glyph returns) and drops the timer. */
    private fun stop(invalidate: Boolean = true) {
        if (armed) timers.cancel(TICK)
        armed = false
        if (frame < 0) return
        frame = -1
        if (invalidate) invalidate(box)
    }

    override fun onTimer(id: Int) {
        if (id != TICK) return
        armed = false
        if (!eligible()) { stop(); arm(); return }
        if (frame < 0) {
            // The minute: start a pulse at the display's speed.
            val fast = speed.current() == DisplaySpeed.FAST
            icons = if (fast) FAST_ICONS else SLOW_ICONS
            holds = if (fast) FAST_HOLDS else SLOW_HOLDS
            frame = 0
            pulses++
        } else if (frame + 1 < icons.size) {
            frame++
        } else {
            frame = -1
            invalidate(box)
            arm()
            return
        }
        invalidate(box)
        timers.schedule(TICK, if (holdScale == 1f) holds[frame] else (holds[frame] * holdScale).toLong())
        armed = true
        armedFor = Long.MIN_VALUE
    }

    // ---- drawing ------------------------------------------------------------------------------------

    override fun draw(f: Frame, seat: Int) {
        if (frame < 0) return
        val l = l ?: return
        if (!f.hit(box)) return
        val ic = icons[frame]
        val dn = stage.session.state.dayNight
        val dayNight = dn != DayNight.UNSET && stage.settings.current.shows(Tracker.DAY_NIGHT)
        if (!dayNight) {
            // The plain diamond: its own dither under the box, the timer on its cut-out (the bars' way).
            f.r.clipTo(box)
            Diamond.fill(f.r, l)
            f.r.unclip()
            f.icons.icon(l.tk, ic, icon, 0, invert = false, opaque = false, halo = true)
        } else {
            val night = dn == DayNight.NIGHT
            f.r.rect(box, ink = night)
            f.icons.icon(l.tk, ic, icon, 0, invert = night, opaque = false)
        }
    }

    // ---- notifications ------------------------------------------------------------------------------

    override fun onEvent(event: Int, seat: Int) {
        when (event) {
            Events.PAUSE -> { paused = true; stop() }
            Events.RESUME -> { paused = false; arm() }
            Events.MENU_OPEN -> { menuOpen = true; stop() }
            Events.MENU_CLOSED -> { menuOpen = false; arm() }
        }
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        if (old.turn === new.turn && old.dayNight == new.dayNight) return
        if (frame >= 0 && !eligible()) stop()
        if (old.turn !== new.turn) {
            // A pass (TURN mode: the minutes restart), the game's end, a restart: re-aim the next pulse.
            if (frame < 0) arm()
        } else if (frame >= 0) invalidate(box) // day / night flipped mid-pulse: repaint in the new polarity
    }

    override fun onSettingsChanged(old: AppSettings, new: AppSettings) {
        if (old.turnTracker == new.turnTracker && old.turnTimer == new.turnTimer && old.shows(Tracker.DAY_NIGHT) == new.shows(Tracker.DAY_NIGHT)) return
        stop()
        arm()
    }

    override fun onCoversChanged() {
        if (!eligible()) stop()
        arm()
    }

    override fun onAttach() = arm()

    override fun onDetach() {
        armed = false // the stage cancels every timer after this
        frame = -1
    }

    companion object {
        const val TICK = 0
        const val TIMERS = 1
        const val MINUTE = 60_000L

        /** Debug builds only (`--ef pulseHold X`, captures): every keyframe hold scaled by X. */
        @JvmField var holdScale = 1f

        /** FAST: sand on top, running (the read frame, longest), below, on its side; ≈ 0.85 s. */
        val FAST_ICONS = arrayOf(Icon.HOURGLASS_1, Icon.HOURGLASS_2, Icon.HOURGLASS_3, Icon.HOURGLASS_4)
        val FAST_HOLDS = longArrayOf(150, 350, 200, 150)

        /** SLOW: three held frames, each ≥ 250 ms; ≈ 1.05 s. */
        val SLOW_ICONS = arrayOf(Icon.HOURGLASS_1, Icon.HOURGLASS_2, Icon.HOURGLASS_3)
        val SLOW_HOLDS = longArrayOf(300, 450, 300)

        private val ALL_ICONS = FAST_ICONS
    }
}
