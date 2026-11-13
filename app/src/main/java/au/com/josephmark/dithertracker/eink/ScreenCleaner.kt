package au.com.josephmark.dithertracker.eink

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.Scheduler

/**
 * A manual e-ink cleanup (ghosting): one solid black frame over the whole screen, held [holdMillis]
 * so the panel drives every pixel, then a normal redraw of everything. Pure state + one timer, so the
 * later refresh-strategy task (auto cleanup every N changes) can reuse it from any component.
 *
 * The owner draws [draw] last in its frame (an `OVERLAY` component does), routes its timer
 * [timerId] to [onTimer], and calls [cancel] on any touch (SPEC rule 4: a tap cancels a running
 * sequence; the redraw then happens at once). [Host.invalidateScreen] must redraw the whole screen.
 * Allocation-free.
 */
class ScreenCleaner(
    private val timers: Scheduler,
    val timerId: Int,
    private val host: Host,
    val holdMillis: Long = HOLD_MS,
) {
    fun interface Host {
        fun invalidateScreen()
    }

    /** True while the black frame is up. */
    var active: Boolean = false
        private set

    /** Number of cleanups started (for tests / the refresh-strategy task's accounting). */
    var count: Int = 0
        private set

    /** Shows the black frame now and schedules the redraw. Restarting while active re-arms the hold. */
    fun start() {
        active = true
        count++
        timers.schedule(timerId, holdMillis)
        host.invalidateScreen()
    }

    /** Ends the black frame early (a touch): redraws now. No-op when idle. */
    fun cancel() {
        if (!active) return
        timers.cancel(timerId)
        finish()
    }

    /** The owner's timer fired. */
    fun onTimer(id: Int) {
        if (id == timerId && active) finish()
    }

    private fun finish() {
        active = false
        host.invalidateScreen()
    }

    /** Draws the black frame over [screen] (dither px) while [active]. */
    fun draw(r: DitherRenderer, screen: IRect) {
        if (active) r.rect(screen.left, screen.top, screen.width, screen.height, ink = true)
    }

    companion object {
        /** Long enough for the Nomad's panel to settle fully black (SPEC rule 4: ≥ ~250 ms). */
        const val HOLD_MS = 300L
    }
}
