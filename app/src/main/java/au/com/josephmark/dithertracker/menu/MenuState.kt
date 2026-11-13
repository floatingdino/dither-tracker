package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.play.Scheduler

/**
 * The table menu's phase machine (pure, JVM-tested on a fake [Scheduler]):
 *
 * ```
 * CLOSED ──open()──▶ [BLOOM ──KEY_MS──▶] OPEN ──confirm()──▶ CONFIRM
 *    ▲                                    │  ◀──back()──────────┘
 *    └──────── close() / IDLE_MS without a touch ─────────────────┘
 * ```
 *
 * [BLOOM] is the optional keyframe (`concept-1-gem-key1.png`, an empty doubled diamond) shown for
 * [KEY_MS] before the full gem; it is off ([bloom] false) unless it clearly reads better on e-ink.
 * Every phase change and every [touched] re-arms the idle timeout. [listener] hears every change
 * (the component invalidates). Allocation-free.
 */
class MenuState(
    private val timers: Scheduler,
    private val listener: Listener,
    /** Show the key1 beat before the gem. */
    var bloom: Boolean = BLOOM_DEFAULT,
    val idleMillis: Long = IDLE_MS,
) {
    fun interface Listener {
        fun onPhaseChanged(old: Int, new: Int)
    }

    var phase: Int = CLOSED
        private set

    val isOpen: Boolean get() = phase != CLOSED

    fun open() {
        if (phase != CLOSED) return
        if (bloom) {
            timers.schedule(TIMER_BLOOM, KEY_MS)
            set(BLOOM)
        } else {
            set(OPEN)
        }
    }

    fun close() {
        timers.cancel(TIMER_BLOOM)
        timers.cancel(TIMER_IDLE)
        set(CLOSED)
    }

    /** NEW was tapped: show the restart confirm. */
    fun confirm() {
        if (phase == OPEN || phase == BLOOM) {
            timers.cancel(TIMER_BLOOM)
            set(CONFIRM)
        }
    }

    /** Cancel on the confirm: back to the gem. */
    fun back() {
        if (phase == CONFIRM) set(OPEN)
    }

    /** Any touch on the menu: the idle timeout starts again. */
    fun touched() {
        if (phase != CLOSED) timers.schedule(TIMER_IDLE, idleMillis)
    }

    fun onTimer(id: Int) {
        when (id) {
            TIMER_IDLE -> if (phase != CLOSED) close()
            TIMER_BLOOM -> if (phase == BLOOM) set(OPEN)
        }
    }

    private fun set(p: Int) {
        val old = phase
        if (p != CLOSED) timers.schedule(TIMER_IDLE, idleMillis)
        if (old == p) return
        phase = p
        listener.onPhaseChanged(old, p)
    }

    companion object {
        const val CLOSED = 0
        const val BLOOM = 1
        const val OPEN = 2
        const val CONFIRM = 3

        /** Local timer ids (the owner's `timers` bank). */
        const val TIMER_IDLE = 0
        const val TIMER_BLOOM = 1
        const val TIMERS = 2

        /**
         * Idle timeout: the open menu blocks the tiles' input (a tap outside only closes it), so it
         * should not linger when the table walks away — but long enough to read a facet, undo a few
         * steps or think about a restart.
         */
        const val IDLE_MS = 20_000L

        /** How long the optional bloom keyframe is held (SPEC rule 4: ≥ ~250 ms). */
        const val KEY_MS = 250L

        /** The bloom beat is off by default: one redraw to open. */
        const val BLOOM_DEFAULT = false
    }
}
