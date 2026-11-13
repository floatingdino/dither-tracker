package au.com.josephmark.dithertracker.widgets

/**
 * A bank of one-shot timers by small int id — the same contract as `play.Scheduler`, restated here so
 * the widget kit has no dependency on the play screen. A host adapts its scheduler in one line
 * (`object : WidgetTimer { override fun schedule(id, d) = s.schedule(base + id, d) ... }`).
 */
interface WidgetTimer {
    /** Arms timer [id] to call [Target.onTimer] after [delayMillis]; re-arming replaces it. */
    fun schedule(id: Int, delayMillis: Long)

    /** Disarms timer [id] (no-op when not armed). */
    fun cancel(id: Int)

    fun interface Target {
        fun onTimer(id: Int)
    }

    companion object {
        /** Never fires: holds never repeat. */
        val NONE: WidgetTimer = object : WidgetTimer {
            override fun schedule(id: Int, delayMillis: Long) {}
            override fun cancel(id: Int) {}
        }
    }
}

/**
 * Press semantics for widgets, multi-pointer safe. Pure (no MotionEvent, no clock), allocation-free.
 * Generic counterpart of `play.PlayInput`, keyed by widget ids from a [Targets] map (a [HitRegion]).
 *
 * - **down**: the pointer binds to the id under it; if the id was not pressed, [Sink.onPressed] (the
 *   host inverts the id's feedback rect). Nothing is applied.
 * - **up**: [Sink.onTap] (the action fires on release) unless the id is already repeating, then
 *   [Sink.onReleased] when it was the id's last pointer — same event, so effect and restore share a frame.
 * - **move**: sliding off the bound id's touch rect cancels that pointer (no action); it does not
 *   re-target, so a finger dragged across a list never presses its neighbours.
 * - **cancel** ([cancel], [cancelAll], a down on a pointer that missed its up, replacing [targets]):
 *   releases without applying anything.
 * - **hold** (only ids whose [Targets.repeats] is true, e.g. stepper ±): [holdDelayMillis] after the
 *   press, [Sink.onRepeat], then every [repeatMillis]. A hold never also fires the tap. Timers are the
 *   host's ([WidgetTimer]); this class only arms/cancels them. Defaults match the play screen's slow
 *   e-ink repeat (500 ms, then 1 s).
 *
 * Two fingers on one id share its press and timer; each finger's own lift is its tap. Different ids
 * press independently.
 */
class PressTracker(
    private val sink: Sink,
    private val timer: WidgetTimer = WidgetTimer.NONE,
    /** Size of the id space (ids are `0 until idCount`). */
    val idCount: Int = HitRegion.DEFAULT_CAPACITY,
) : WidgetTimer.Target {

    interface Targets {
        /** Id under dither (gx, gy), or a negative value for nothing. */
        fun hitTest(gx: Int, gy: Int): Int

        /** Whether holding [id] repeats. */
        fun repeats(id: Int): Boolean
    }

    interface Sink {
        /** The id went from unpressed to pressed: invert its feedback rect. */
        fun onPressed(id: Int)

        /** The id's last pointer lifted or was cancelled: restore it. */
        fun onReleased(id: Int)

        /** Released without having repeated: perform the action. */
        fun onTap(id: Int)

        /** Held: apply one repeat step. */
        fun onRepeat(id: Int) {}
    }

    var holdDelayMillis: Long = HOLD_DELAY_MS
    var repeatMillis: Long = REPEAT_MS

    /** Current hit map; touches are ignored while null. Replacing it cancels every press. */
    var targets: Targets? = null
        set(value) {
            cancelAll()
            field = value
        }

    private val pointerId = IntArray(MAX_POINTERS) { NONE }
    private val idPointers = IntArray(idCount)
    private val repeats = IntArray(idCount)

    fun isPressed(id: Int): Boolean = id in 0 until idCount && idPointers[id] > 0

    /** True while any id is pressed. */
    fun anyPressed(): Boolean {
        for (n in idPointers) if (n > 0) return true
        return false
    }

    /** Repeats fired in [id]'s current press. */
    fun repeatCount(id: Int): Int = if (id in 0 until idCount) repeats[id] else 0

    /** Id bound to [pointer], or [NONE]. */
    fun idOf(pointer: Int): Int = if (pointer in 0 until MAX_POINTERS) pointerId[pointer] else NONE

    fun down(pointer: Int, gx: Int, gy: Int) {
        val t = targets ?: return
        if (pointer !in 0 until MAX_POINTERS) return
        if (pointerId[pointer] != NONE) release(pointer, apply = false) // missed an up
        val id = t.hitTest(gx, gy)
        if (id < 0 || id >= idCount) return
        pointerId[pointer] = id
        if (idPointers[id]++ == 0) {
            repeats[id] = 0
            if (t.repeats(id)) timer.schedule(id, holdDelayMillis)
            sink.onPressed(id)
        }
    }

    /** The pointer moved to (gx, gy): leaving its id's touch rect cancels it. */
    fun move(pointer: Int, gx: Int, gy: Int) {
        val t = targets ?: return
        val id = idOf(pointer)
        if (id == NONE) return
        if (t.hitTest(gx, gy) != id) release(pointer, apply = false)
    }

    /** The pointer lifted: fires the tap unless its id is repeating. */
    fun up(pointer: Int) = release(pointer, apply = true)

    /** This pointer's gesture was cancelled. */
    fun cancel(pointer: Int) = release(pointer, apply = false)

    fun cancelAll() {
        for (p in 0 until MAX_POINTERS) release(p, apply = false)
    }

    private fun release(pointer: Int, apply: Boolean) {
        if (pointer !in 0 until MAX_POINTERS) return
        val id = pointerId[pointer]
        if (id == NONE) return
        pointerId[pointer] = NONE
        val tap = apply && repeats[id] == 0
        val last = --idPointers[id] == 0
        if (last) timer.cancel(id)
        if (tap) sink.onTap(id)
        if (last) sink.onReleased(id)
    }

    override fun onTimer(id: Int) {
        if (!isPressed(id)) return // stale
        if (targets?.repeats(id) != true) return
        repeats[id]++
        timer.schedule(id, repeatMillis)
        sink.onRepeat(id)
    }

    companion object {
        const val NONE = -1
        const val MAX_POINTERS = 32
        const val HOLD_DELAY_MS = 500L
        const val REPEAT_MS = 1000L
    }
}
