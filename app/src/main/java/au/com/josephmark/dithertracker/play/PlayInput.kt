package au.com.josephmark.dithertracker.play

/**
 * Multi-touch bookkeeping for the Play screen. Pure Kotlin (no MotionEvent), allocation-free.
 *
 * Each pointer id is tracked independently, so several players can tap at once. A pointer is bound
 * to the zone it went down in until it lifts (moving does not re-target it: on a crowded table a
 * finger that slides should not press a neighbour's zone). A zone stays pressed while at least one
 * pointer holds it.
 *
 * Life changes fire on **touch-down** ([Sink.onZoneTap]), together with the press feedback in the
 * same frame: on e-ink every frame is a slow panel update, so committing on down gets the new
 * number onto the glass one panel-update earlier than waiting for up, and the inversion + number
 * land as one update rather than two. A cancelled gesture (e.g. palm rejection) does not undo the
 * step; undo is in the centre menu (T09).
 *
 * T07 hooks: hold-to-repeat starts a timer in [Sink.onZonePressed] and stops it in
 * [Sink.onZoneReleased]; [downTime] gives each pointer's down timestamp.
 */
class PlayInput(private val sink: Sink) {

    interface Sink {
        /** A new pointer went down on a life zone: apply the step now. */
        fun onZoneTap(zone: Int, eventTimeMillis: Long)

        /** The zone went from unpressed to pressed (invert it). */
        fun onZonePressed(zone: Int)

        /** The last pointer holding the zone lifted or was cancelled (restore it). */
        fun onZoneReleased(zone: Int)
    }

    /** Current layout; touches are ignored while null. */
    var layout: PlayLayout? = null
        set(value) {
            cancelAll()
            field = value
        }

    private val pointerZone = IntArray(MAX_POINTERS) { PlayLayout.NONE }
    private val pointerDown = LongArray(MAX_POINTERS)
    private val zoneCount = IntArray(PlayLayout.PLAYERS * 2)

    fun isPressed(zone: Int): Boolean = zone in zoneCount.indices && zoneCount[zone] > 0

    /** Zone held by [pointerId], or [PlayLayout.NONE]. */
    fun zoneOf(pointerId: Int): Int = if (pointerId in 0 until MAX_POINTERS) pointerZone[pointerId] else PlayLayout.NONE

    fun downTime(pointerId: Int): Long = if (pointerId in 0 until MAX_POINTERS) pointerDown[pointerId] else 0L

    /** A pointer went down at dither (gx, gy). */
    fun down(pointerId: Int, gx: Int, gy: Int, eventTimeMillis: Long) {
        val l = layout ?: return
        if (pointerId !in 0 until MAX_POINTERS) return
        if (pointerZone[pointerId] != PlayLayout.NONE) up(pointerId) // missed an up: release first
        val zone = l.hitTest(gx, gy)
        if (zone < 0 || zone >= zoneCount.size) return // NONE or RESERVED: nothing happens (T08/T09)
        pointerZone[pointerId] = zone
        pointerDown[pointerId] = eventTimeMillis
        sink.onZoneTap(zone, eventTimeMillis)
        if (zoneCount[zone]++ == 0) sink.onZonePressed(zone)
    }

    /** The pointer lifted. */
    fun up(pointerId: Int) {
        if (pointerId !in 0 until MAX_POINTERS) return
        val zone = pointerZone[pointerId]
        if (zone == PlayLayout.NONE) return
        pointerZone[pointerId] = PlayLayout.NONE
        if (--zoneCount[zone] == 0) sink.onZoneReleased(zone)
    }

    /** Gesture cancelled: release everything. */
    fun cancelAll() {
        for (id in 0 until MAX_POINTERS) up(id)
    }

    companion object {
        /** MotionEvent pointer ids are small dense ints; ids beyond this are ignored. */
        const val MAX_POINTERS = 32
    }
}
