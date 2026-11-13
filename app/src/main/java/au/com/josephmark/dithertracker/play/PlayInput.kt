package au.com.josephmark.dithertracker.play

/**
 * Multi-touch bookkeeping and hold-to-repeat for the Play screen. Pure Kotlin (no MotionEvent, no
 * clock: timing comes from the injected [Scheduler]), allocation-free.
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
 * **Hold-to-repeat.** When a zone becomes pressed its timer is armed for [holdDelayMillis]; if the
 * zone is still pressed when it fires, the zone repeats every [repeatMillis] until its last pointer
 * lifts or the gesture is cancelled (both disarm the timer at once). Each repeat is one
 * [Sink.onZoneRepeat], i.e. one frame. Steps are counted *from where the press started*: the first
 * repeat applies `±(step − 1)` — it absorbs the tap's ±1 — and later ones `±step`, so holding `−`
 * from 40 shows 39 (the tap) → 30 → 20 and the pending readout reads −1 → −10 → −20. A player
 * holding to "take ten" gets exactly ten per step; from the usual round starting totals the life
 * also lands on round numbers. Each zone has its own timer (two seats holding repeat
 * independently); a second finger in an already-pressed zone taps once but does not double the
 * repeat rate.
 */
class PlayInput(
    private val sink: Sink,
    private val scheduler: Scheduler = Scheduler.NONE,
) : Scheduler.Target {

    interface Sink {
        /** A new pointer went down on a life zone: apply the step now. */
        fun onZoneTap(zone: Int, eventTimeMillis: Long)

        /** The zone is being held: apply [delta] (signed) now. */
        fun onZoneRepeat(zone: Int, delta: Int)

        /** The zone went from unpressed to pressed (invert it). */
        fun onZonePressed(zone: Int)

        /** The last pointer holding the zone lifted or was cancelled (restore it). */
        fun onZoneReleased(zone: Int)
    }

    /** Delay from press to the first repeat. */
    var holdDelayMillis: Long = HOLD_DELAY_MS

    /** Time between repeats (≥ 300 ms so each step gets its own e-ink panel update, `docs/eink.md` §4). */
    var repeatMillis: Long = REPEAT_MS

    /** Life per repeat (`AppSettings.holdRepeatStep`); ≤ 0 turns hold-to-repeat off. */
    var holdStep: Int = DEFAULT_HOLD_STEP

    /** Current layout; touches are ignored while null. */
    var layout: PlayLayout? = null
        set(value) {
            cancelAll()
            field = value
        }

    private val pointerZone = IntArray(MAX_POINTERS) { PlayLayout.NONE }
    private val pointerDown = LongArray(MAX_POINTERS)
    private val zoneCount = IntArray(PlayLayout.PLAYERS * 2)

    /** Repeats fired in the zone's current press (0 = still waiting for the hold delay). */
    private val repeats = IntArray(PlayLayout.PLAYERS * 2)

    fun isPressed(zone: Int): Boolean = zone in zoneCount.indices && zoneCount[zone] > 0

    /** Number of repeats applied so far in [zone]'s current press. */
    fun repeatCount(zone: Int): Int = if (zone in repeats.indices) repeats[zone] else 0

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
        if (zoneCount[zone]++ == 0) {
            repeats[zone] = 0
            if (holdStep > 0) scheduler.schedule(zone, holdDelayMillis)
            sink.onZonePressed(zone)
        }
    }

    /** The pointer lifted. */
    fun up(pointerId: Int) {
        if (pointerId !in 0 until MAX_POINTERS) return
        val zone = pointerZone[pointerId]
        if (zone == PlayLayout.NONE) return
        pointerZone[pointerId] = PlayLayout.NONE
        if (--zoneCount[zone] == 0) {
            scheduler.cancel(zone)
            sink.onZoneReleased(zone)
        }
    }

    /** Gesture cancelled: release everything. */
    fun cancelAll() {
        for (id in 0 until MAX_POINTERS) up(id)
    }

    /** Hold timer for [id] (a zone) fired. */
    override fun onTimer(id: Int) {
        if (!isPressed(id) || holdStep <= 0) return // stale: released in the meantime
        val first = repeats[id] == 0
        repeats[id]++
        scheduler.schedule(id, repeatMillis)
        val magnitude = if (first) holdStep - 1 else holdStep
        if (magnitude > 0) sink.onZoneRepeat(id, PlayLayout.zoneDelta(id) * magnitude)
    }

    companion object {
        /** MotionEvent pointer ids are small dense ints; ids beyond this are ignored. */
        const val MAX_POINTERS = 32

        /** Long enough that a deliberate tap (≈100–250 ms on glass) never becomes a hold. */
        const val HOLD_DELAY_MS = 500L

        /** One step per panel update with margin: the Nomad's partial update takes ~250–450 ms. */
        const val REPEAT_MS = 400L

        const val DEFAULT_HOLD_STEP = 10
    }
}
