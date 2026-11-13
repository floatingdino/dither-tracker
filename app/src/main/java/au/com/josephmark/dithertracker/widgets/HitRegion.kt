package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect

/**
 * The hit map of a screen built from widgets: id → touch rect + feedback rect, in screen dither px.
 * Widgets fill it ([Widget.collectHits]); the host hit-tests touches and finds what to invert
 * without writing any hit maths. Fixed capacity, allocation-free after construction.
 *
 * - [hitTest] returns the id of the *most recently added* entry containing the point (so overlays
 *   collected after the screen below them win), or [NONE].
 * - The feedback rect is what a press inverts (SPEC rule 6): the widget's visible rect, which may be
 *   smaller than its touch rect (e.g. a stepper button inside its fingertip-sized column).
 * - Each entry remembers its owner widget and frame so a painter can redraw it inverted where in-place
 *   inversion is unavailable (API < 29).
 *
 * Implements [PressTracker.Targets], so a [PressTracker] can use it directly.
 */
class HitRegion(val capacity: Int = DEFAULT_CAPACITY) : PressTracker.Targets {

    var size: Int = 0
        private set

    private val ids = IntArray(capacity)
    private val hits = Array(capacity) { IRect() }
    private val feedbacks = Array(capacity) { IRect() }
    private val repeat = BooleanArray(capacity)
    private val owners = arrayOfNulls<Widget>(capacity)
    private val frames = arrayOfNulls<WidgetFrame>(capacity)

    fun clear() {
        for (i in 0 until size) { owners[i] = null; frames[i] = null }
        size = 0
    }

    /** Adds an entry with screen rects (copied). */
    fun add(id: Int, hit: IRect, feedback: IRect = hit, repeats: Boolean = false, owner: Widget? = null, frame: WidgetFrame? = null) {
        val i = slot(id)
        hits[i].set(hit.left, hit.top, hit.right, hit.bottom)
        feedbacks[i].set(feedback.left, feedback.top, feedback.right, feedback.bottom)
        finish(i, id, repeats, owner, frame)
    }

    /** Adds an entry from local rects, mapped to the screen through [frame]. */
    fun addLocal(id: Int, frame: WidgetFrame, hit: IRect, feedback: IRect = hit, repeats: Boolean = false, owner: Widget? = null) {
        val i = slot(id)
        frame.map(hit, hits[i])
        frame.map(feedback, feedbacks[i])
        finish(i, id, repeats, owner, frame)
    }

    private fun slot(id: Int): Int {
        require(id >= 0) { "ids are >= 0, was $id" }
        check(size < capacity) { "HitRegion full ($capacity)" }
        return size
    }

    private fun finish(i: Int, id: Int, repeats: Boolean, owner: Widget?, frame: WidgetFrame?) {
        ids[i] = id
        repeat[i] = repeats
        owners[i] = owner
        frames[i] = frame
        size = i + 1
    }

    /** Id under screen (gx, gy), or [NONE]. */
    override fun hitTest(gx: Int, gy: Int): Int {
        for (i in size - 1 downTo 0) if (hits[i].contains(gx, gy)) return ids[i]
        return NONE
    }

    override fun repeats(id: Int): Boolean {
        val i = indexOf(id)
        return i >= 0 && repeat[i]
    }

    /** Entry index of [id] (latest wins), or -1. */
    fun indexOf(id: Int): Int {
        for (i in size - 1 downTo 0) if (ids[i] == id) return i
        return -1
    }

    fun contains(id: Int): Boolean = indexOf(id) >= 0

    /** Copies [id]'s feedback rect into [out]; false when absent. */
    fun feedbackRect(id: Int, out: IRect): Boolean {
        val i = indexOf(id)
        if (i < 0) return false
        val r = feedbacks[i]
        out.set(r.left, r.top, r.right, r.bottom)
        return true
    }

    /** Copies [id]'s touch rect into [out]; false when absent. */
    fun hitRect(id: Int, out: IRect): Boolean {
        val i = indexOf(id)
        if (i < 0) return false
        val r = hits[i]
        out.set(r.left, r.top, r.right, r.bottom)
        return true
    }

    // Index-based access, for painters and tests (no copies).
    fun idAt(i: Int): Int = ids[i]
    fun hitAt(i: Int): IRect = hits[i]
    fun feedbackAt(i: Int): IRect = feedbacks[i]
    fun repeatsAt(i: Int): Boolean = repeat[i]
    fun ownerAt(i: Int): Widget? = owners[i]
    fun frameAt(i: Int): WidgetFrame? = frames[i]

    companion object {
        const val NONE = -1
        const val DEFAULT_CAPACITY = 64
    }
}
