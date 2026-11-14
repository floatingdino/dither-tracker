package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.play.Scheduler
import kotlin.math.roundToLong

/**
 * Per-seat effect playback: which keyframe of which effect each seat shows now. Pure Kotlin,
 * allocation-free per step; time comes from a [Scheduler] whose timer ids are seat indices, so give
 * it **its own** scheduler (e.g. `HandlerScheduler(handler, seats)` with `target = player`) — the ids
 * would collide with [au.com.josephmark.dithertracker.play.PendingDeltas]' timers otherwise.
 *
 * Rules (SPEC rule 4, design/fx/README.md):
 * - [play] starts at frame 0 and holds each frame `fx.holdMs(frame) × holdScale`; the effect's last
 *   frame is the empty one, so reaching it ends playback: the seat is clean ([current] = null) and
 *   [Listener.onFrameChanged] fires once more so the view redraws the plain number.
 * - Never queue: [play] on a busy seat replaces its effect immediately.
 * - [cancel] is immediate (one [Listener.onFrameChanged] so the seat redraws clean).
 * - At most [maxConcurrent] seats animate at once; a [play] on another seat beyond that is dropped
 *   (returns false) and the caller just draws clean.
 * - [enabled] = false drops every [play] and cancels anything running.
 * - **All seats at once** (T55, `dusk`/`dawn`): [playAll] starts one effect on a set of seats past
 *   the cap, stepped together by ONE timer ([groupTimer] = `seats`), so each keyframe is one entry
 *   point and one flush for every seat (one panel update, not one per seat). A seat leaves the group
 *   when it is cancelled or given another effect; the others carry on.
 *
 * [Listener.onFrameChanged] fires exactly when what a seat shows changes — once per keyframe — and
 * is the only invalidation effects need. Main-thread only, like the scheduler.
 */
class FxPlayer(
    seats: Int,
    private val scheduler: Scheduler,
    private val listener: Listener,
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
) : Scheduler.Target {

    fun interface Listener {
        /** What [seat] shows changed (a new keyframe, or clean): redraw that seat's number area. */
        fun onFrameChanged(seat: Int)
    }

    /** Global on-device tuning knob: every hold is `holdMs × holdScale` (rounded, ≥ 0). */
    var holdScale: Float = 1f
        set(value) {
            require(value >= 0f && !value.isNaN()) { "holdScale must be >= 0, was $value" }
            field = value
        }

    /** At most this many seats animate at once (default 2). Lowering it does not stop running ones. */
    var maxConcurrent: Int = maxConcurrent
        set(value) {
            require(value >= 0) { "maxConcurrent must be >= 0, was $value" }
            field = value
        }

    /** Settings switch. Turning it off cancels every running effect. */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) cancelAll()
        }

    private val effect = arrayOfNulls<Fx>(seats)
    private val frame = IntArray(seats)

    /** The scheduler id of the all-seat group's timer (the seat ids are `0 until seats`). */
    val groupTimer: Int = seats

    /** Seats playing the all-seat group's effect (bit `1 shl seat`). */
    var group: Int = 0
        private set
    private var groupFx: Fx? = null
    private var groupFrame = 0

    /** Number of seats currently animating. */
    var active: Int = 0
        private set

    /** The effect [seat] is showing, or null when it is clean. */
    fun current(seat: Int): Fx? = if (seat in effect.indices) effect[seat] else null

    /** The keyframe index [seat] is showing (0-based into [Fx.frameCount]), or -1 when clean. */
    fun frame(seat: Int): Int = if (current(seat) != null) frame[seat] else -1

    fun isPlaying(seat: Int): Boolean = current(seat) != null

    /**
     * Starts [fx] on [seat] at its first frame, replacing whatever that seat was showing.
     * Returns false (and changes nothing) when disabled, the seat is invalid, or [maxConcurrent]
     * other seats are already animating — the caller then just draws the number clean.
     */
    fun play(seat: Int, fx: Fx): Boolean {
        if (!enabled || seat !in effect.indices) return false
        if (effect[seat] == null && active >= maxConcurrent) return false
        if (fx.frameCount < 2) return false // nothing drawn (generator guarantees ≥ 2)
        leaveGroup(seat)
        if (effect[seat] == null) active++
        effect[seat] = fx
        frame[seat] = 0
        scheduler.schedule(seat, hold(fx, 0))
        listener.onFrameChanged(seat)
        return true
    }

    /**
     * Starts [fx] on every seat in [seats] (bit mask) at once, past [maxConcurrent], stepped by one
     * timer. Replaces what those seats show (a previous group stops first). False when disabled or
     * nothing to play.
     */
    fun playAll(seats: Int, fx: Fx): Boolean {
        if (!enabled || fx.frameCount < 2) return false
        var mask = 0
        for (s in effect.indices) if (seats and (1 shl s) != 0) mask = mask or (1 shl s)
        if (mask == 0) return false
        cancelGroup()
        for (s in effect.indices) {
            if (mask and (1 shl s) == 0) continue
            if (effect[s] == null) active++ else scheduler.cancel(s)
            effect[s] = fx
            frame[s] = 0
        }
        group = mask
        groupFx = fx
        groupFrame = 0
        scheduler.schedule(groupTimer, hold(fx, 0))
        for (s in effect.indices) if (mask and (1 shl s) != 0) listener.onFrameChanged(s)
        return true
    }

    /** Stops [seat]'s effect now; it redraws clean. No-op when it is not animating. */
    fun cancel(seat: Int) {
        if (current(seat) == null) return
        scheduler.cancel(seat)
        leaveGroup(seat)
        clear(seat)
    }

    private fun leaveGroup(seat: Int) {
        val bit = 1 shl seat
        if (group and bit == 0) return
        group = group and bit.inv()
        if (group == 0) {
            scheduler.cancel(groupTimer)
            groupFx = null
        }
    }

    private fun cancelGroup() {
        for (s in effect.indices) if (group and (1 shl s) != 0) cancel(s)
    }

    /** Stops every running effect now. */
    fun cancelAll() {
        for (s in effect.indices) cancel(s)
    }

    override fun onTimer(id: Int) {
        if (id == groupTimer) return stepGroup()
        val fx = current(id) ?: return
        val next = frame[id] + 1
        if (next >= fx.frameCount - 1) {
            // The last frame is the empty end state: the clean number.
            clear(id)
            return
        }
        frame[id] = next
        scheduler.schedule(id, hold(fx, next))
        listener.onFrameChanged(id)
    }

    private fun stepGroup() {
        val fx = groupFx ?: return
        val next = groupFrame + 1
        if (next >= fx.frameCount - 1) {
            val g = group
            group = 0
            groupFx = null
            for (s in effect.indices) if (g and (1 shl s) != 0) clear(s)
            return
        }
        groupFrame = next
        scheduler.schedule(groupTimer, hold(fx, next))
        for (s in effect.indices) if (group and (1 shl s) != 0) {
            frame[s] = next
            listener.onFrameChanged(s)
        }
    }

    /** Scaled hold of [frame] of [fx], in ms. */
    fun hold(fx: Fx, frame: Int): Long = (fx.holdMs(frame) * holdScale).roundToLong().coerceAtLeast(0L)

    private fun clear(seat: Int) {
        effect[seat] = null
        frame[seat] = 0
        active--
        listener.onFrameChanged(seat)
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 2
    }
}
