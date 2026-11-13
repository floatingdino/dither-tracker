package au.com.josephmark.dithertracker.eink

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.Scheduler

/**
 * An e-ink cleanup (ghosting): one solid ink frame over a region — a seat's tile ([cleanRegion]) or
 * the whole screen ([cleanFull]) — held [holdMillis] so the panel drives every pixel there, then a
 * normal redraw of that region. One state machine and one timer for every clean (manual CLEAN, the
 * automatic cadence, the overlay-close clean; see `CleanComponent`).
 *
 * A full clean may instead use the vendor refresh ([vendorFull], `SupernoteFullRefresh`): nothing is
 * drawn, the call runs [VENDOR_DELAY_MS] later (so a redraw posted in the same frame reaches the panel
 * first), and on failure it falls back to the ink frame.
 *
 * The owner draws [draw] last in its frame, routes [timerId] to [onTimer], swallows touches inside
 * [region] while [active] (a block zone) and calls [cancel] on any touch (SPEC rule 4: a tap cancels a
 * running sequence; the redraw then happens at once). Allocation-free.
 */
class ScreenCleaner(
    private val timers: Scheduler,
    val timerId: Int,
    private val host: Host,
    val holdMillis: Long = HOLD_MS,
) {
    interface Host {
        /** Redraw [region] (dither px). */
        fun redraw(region: IRect)

        /** A clean ran to its end ([full]: the whole screen). Not called when cancelled. */
        fun onCleaned(full: Boolean) {}
    }

    /** The vendor full refresh, or null for the app-drawn flash only. Returns true when it ran. */
    fun interface FullRefresh {
        fun refresh(): Boolean
    }

    var vendorFull: FullRefresh? = null

    /** The whole screen (dither px); the owner sets it on layout. */
    val screen = IRect()

    /** The region being cleaned (valid while [active]). */
    val region = IRect()

    /** True from the start of a clean until it ends or is cancelled. */
    var active: Boolean = false
        private set

    /** Whether the current clean is the whole screen. */
    var full: Boolean = false
        private set

    /** True while the ink frame is up (false while a vendor refresh is pending). */
    var black: Boolean = false
        private set

    /** Number of cleans started (tests / trace). */
    var count: Int = 0
        private set

    /** Cleans [rect] (a seat's tile). Restarting while active cancels the running one first. */
    fun cleanRegion(rect: IRect) = begin(rect, false)

    /** Cleans the whole [screen] (vendor refresh when [vendorFull] is set). */
    fun cleanFull() = begin(screen, true)

    private fun begin(rect: IRect, full: Boolean) {
        cancel()
        region.set(rect.left, rect.top, rect.right, rect.bottom)
        this.full = full
        active = true
        count++
        if (full && vendorFull != null) {
            black = false
            timers.schedule(timerId, VENDOR_DELAY_MS)
            return
        }
        showBlack()
    }

    private fun showBlack() {
        black = true
        timers.schedule(timerId, holdMillis)
        host.redraw(region)
    }

    /** Ends a clean early (a touch): redraws now if the ink frame was up. No-op when idle. */
    fun cancel() {
        if (!active) return
        timers.cancel(timerId)
        val wasBlack = black
        active = false
        black = false
        if (wasBlack) host.redraw(region)
    }

    /** The owner's timer fired. */
    fun onTimer(id: Int) {
        if (id != timerId || !active) return
        if (!black) {
            // Vendor refresh pending: run it; on failure fall back to the app-drawn flash.
            if (vendorFull?.refresh() == true) {
                active = false
                host.onCleaned(full)
            } else {
                showBlack()
            }
            return
        }
        active = false
        black = false
        host.redraw(region)
        host.onCleaned(full)
    }

    /** Draws the ink frame over [region] while it is up. */
    fun draw(r: DitherRenderer) {
        if (black) r.rect(region.left, region.top, region.width, region.height, ink = true)
    }

    companion object {
        /** Long enough for the Nomad's panel to settle fully black (SPEC rule 4: ≥ ~250 ms). */
        const val HOLD_MS = 300L

        /** Before a vendor full refresh: lets the frame drawn just before it reach the panel. */
        const val VENDOR_DELAY_MS = 300L
    }
}
