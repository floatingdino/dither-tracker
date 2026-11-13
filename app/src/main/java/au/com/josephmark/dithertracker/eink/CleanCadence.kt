package au.com.josephmark.dithertracker.eink

import au.com.josephmark.dithertracker.play.Scheduler

/**
 * When to clean the panel automatically (T19b). Pure: counts **panel-changing actions** and waits for
 * a quiet table before asking its [Host] to clean. JVM-tested with a fake clock.
 *
 * **Counting.** The stage reports every invalidated rect ([touched], as a seat mask) and the end of
 * every entry point ([settled] with its cause). An entry point that changed pixels counts as **one
 * action** when it is a committed touch (release, a hold-repeat step) or an external state change;
 * timer-driven redraws (the readout clearing, an effect's keyframes, a menu timing out) count **once
 * per burst** — the first such redraw after a user action counts, the rest of the burst does not.
 * Touch-down and cancel redraws (press inversion only) never count. Each counted action adds 1 to
 * [count] and 1 to [seatCount] of every seat it touched.
 *
 * **Due.** `threshold > 0 && count >= threshold` ([threshold] = the "Screen clean" setting, 0 = off).
 *
 * **Never interrupt play.** While due, every settled entry point (re)arms one quiet timer
 * ([QUIET_MS]); when it fires and [Host.isBusy] is false (no touch down, no overlay taking input, no
 * component timer pending — effects, readouts — and no clean running) the clean runs. If the table
 * is busy nothing is re-armed: the entry point that ends the busy state (a timer firing, a release)
 * settles and re-arms. So: nothing scheduled while off or below the threshold, at most one timer
 * while due, nothing stacked.
 *
 * **Scope** ([scope]): when one seat alone holds the changes — its count has reached the threshold
 * and every other seat is at most [threshold] / [OTHERS_QUIET_DIVISOR] — only that seat's tile is
 * cleaned; otherwise the whole screen.
 *
 * **Reset.** [cleaned] after any clean that ran to its end (automatic, manual or overlay close); a
 * cancelled clean leaves the counts, so it is retried at the next quiet moment. [reset] on new game,
 * player-count change and threshold change.
 */
class CleanCadence(
    private val timers: Scheduler,
    val timerId: Int,
    private val host: Host,
    val seats: Int,
) {
    interface Host {
        /** Whether anything is going on that a clean must not interrupt. */
        fun isBusy(): Boolean

        /** Clean [seat]'s tile, or the whole screen for [FULL]. */
        fun clean(seat: Int)

        /** Trace (allocates when used; only called if [traceOn]). */
        fun trace(msg: String) {}

        val traceOn: Boolean get() = false
    }

    /** Actions between cleans; 0 = automatic cleanup off. */
    var threshold: Int = 0
        private set

    /** Counted actions since the last full clean (lowered after a seat clean). */
    var count: Int = 0
        private set

    private val seatCounts = IntArray(seats)

    fun seatCount(seat: Int): Int = seatCounts[seat]

    /** True while the quiet timer is armed. */
    var waiting: Boolean = false
        private set

    /** Paused (activity in the background): nothing is armed until [resume]. */
    var paused: Boolean = false
        private set

    // The entry point in progress: seats touched, and whether anything at all (centre-only) changed.
    private var mask = 0
    private var changed = false

    // A timer-driven burst has already been counted since the last user action.
    private var burstCounted = false

    // The quiet timer just found the table busy: its own entry point must not re-arm it.
    private var holdArm = false

    val due: Boolean get() = threshold > 0 && count >= threshold

    fun setThreshold(n: Int) {
        val t = if (n < 0) 0 else n
        if (t == threshold) return
        threshold = t
        reset()
        if (host.traceOn) host.trace("eink threshold=$t")
    }

    /** Clears every count and the quiet timer. */
    fun reset() {
        count = 0
        seatCounts.fill(0)
        mask = 0
        changed = false
        burstCounted = false
        holdArm = false
        disarm()
    }

    /** Pixels changed in this entry point on the seats in [seatMask] (0 = only outside every seat). */
    fun touched(seatMask: Int) {
        changed = true
        mask = mask or seatMask
    }

    /** An outermost entry point ended ([cause]: `Stage.Cause`). Counts it, then (re)arms if due. */
    fun settled(cause: Int) {
        val wasChanged = changed
        val m = mask
        changed = false
        mask = 0
        if (threshold <= 0) return
        if (wasChanged && counts(cause)) {
            count++
            for (s in 0 until seats) if ((m ushr s) and 1 != 0) seatCounts[s]++
            if (host.traceOn) host.trace("eink count=$count seats=${seatCounts.joinToString(",")} cause=$cause")
        }
        if (holdArm) {
            holdArm = false
            return
        }
        if (due) arm()
    }

    private fun counts(cause: Int): Boolean = when (cause) {
        CAUSE_ACTION -> {
            burstCounted = false
            true
        }
        CAUSE_TIMER -> if (burstCounted) false else {
            burstCounted = true
            true
        }
        else -> false
    }

    private fun arm() {
        if (paused) return
        waiting = true
        timers.schedule(timerId, QUIET_MS)
        if (host.traceOn) host.trace("eink pending: quiet timer ${QUIET_MS}ms")
    }

    private fun disarm() {
        if (!waiting) return
        waiting = false
        timers.cancel(timerId)
    }

    fun onTimer(id: Int) {
        if (id != timerId) return
        waiting = false
        if (!due || paused) return
        if (host.isBusy()) {
            holdArm = true
            if (host.traceOn) host.trace("eink quiet timer: busy, waiting for the next quiet moment")
            return
        }
        val s = scope()
        if (host.traceOn) host.trace("eink clean ${if (s == FULL) "full" else "seat $s"} (count=$count)")
        holdArm = true // this entry point's settle must not re-arm while the clean runs
        host.clean(s)
    }

    /** The seat to clean alone, or [FULL]. */
    fun scope(): Int {
        var hot = -1
        for (s in 0 until seats) if (seatCounts[s] >= threshold) {
            if (hot >= 0) return FULL
            hot = s
        }
        if (hot < 0) return FULL
        val quiet = threshold / OTHERS_QUIET_DIVISOR
        for (s in 0 until seats) if (s != hot && seatCounts[s] > quiet) return FULL
        return hot
    }

    /** A clean of [seat] (or [FULL]) ran to its end. */
    fun cleaned(seat: Int) {
        if (seat == FULL || seat !in 0 until seats) {
            count = 0
            seatCounts.fill(0)
        } else {
            seatCounts[seat] = 0
            // What is still on screen: the busiest other seat (below the threshold by construction).
            var rest = 0
            for (s in 0 until seats) if (seatCounts[s] > rest) rest = seatCounts[s]
            count = rest
        }
        burstCounted = false
        disarm()
        if (host.traceOn) host.trace("eink cleaned ${if (seat == FULL) "full" else "seat $seat"}: count=$count")
    }

    /** The activity paused: drop the quiet timer and arm nothing until [resume]. */
    fun pause() {
        paused = true
        disarm()
    }

    fun resume() {
        paused = false
        if (due) arm()
    }

    /** The stage cancelled every timer (detach): forget the armed one. */
    fun timersCancelled() {
        waiting = false
    }

    companion object {
        /** [Host.clean] argument: the whole screen. */
        const val FULL = -1

        /** No touch, no running effect/readout timer for this long before an automatic clean. */
        const val QUIET_MS = 2_000L

        /** One seat cleans alone when every other seat has at most threshold / this many changes. */
        const val OTHERS_QUIET_DIVISOR = 4

        /** [settled] causes, mapped from `Stage.Cause` by the owner. */
        const val CAUSE_OTHER = 0
        const val CAUSE_ACTION = 1
        const val CAUSE_TIMER = 2
    }
}
