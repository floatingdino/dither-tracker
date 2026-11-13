package au.com.josephmark.dithertracker.stage

import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.Scheduler

/**
 * Multi-touch bookkeeping and hold-to-repeat for the Play screen. Pure Kotlin (no MotionEvent, no
 * clock: timing comes from the injected [Scheduler]), allocation-free.
 *
 * Zones are small ints from a [ZoneMap] (the [Stage]'s global zone ids, allocated per component). Each pointer id is tracked independently, so several players can press at
 * once. A pointer is bound to the zone it went down in until it lifts (moving does not re-target it:
 * on a crowded table a finger that slides should not press a neighbour's zone). A zone stays pressed
 * while at least one pointer holds it.
 *
 * **Press model (T08, designer feedback on T07).** Touch-down only shows the press inversion
 * ([Sink.onZonePressed]). Nothing is applied until:
 * - **tap**: a pointer lifts before its zone started repeating → [Sink.onZoneTap] (then
 *   [Sink.onZoneReleased] if it was the zone's last pointer), in the same event, so the change and
 *   the restore land in one frame;
 * - **hold**: the zone is still pressed [holdDelayMillis] after it became pressed (and the map says
 *   it [ZoneMap.repeats]) → [Sink.onZoneRepeat] at that moment and every [repeatMillis] after. A hold
 *   never applies the tap's ±1: holding − from 40 shows 40 → 30 → 20.
 * - **cancel** ([cancelAll], [cancelZones], a missed up): releases without applying anything.
 *
 * Multi-touch: a second finger in an already-pressed zone shares the zone's timer (no faster
 * repeat); each pointer's own lift is its tap, unless the zone is already repeating.
 */
class PlayInput(
    private val sink: Sink,
    private val scheduler: Scheduler = Scheduler.NONE,
    /** Size of the zone id space ([Stage.zoneCount] in the app). */
    val zoneCount: Int = PlayLayout.PLAYERS * 2,
) : Scheduler.Target {

    /** Geometry + policy: which zone a point hits, and whether holding a zone repeats. */
    interface ZoneMap {
        /** Zone id under dither (gx, gy), or a negative value for nothing ([PlayLayout.NONE]). */
        fun hitTest(gx: Int, gy: Int): Int

        /** Whether holding [zone] repeats (checked when the hold timer is armed and when it fires). */
        fun repeats(zone: Int): Boolean
    }

    interface Sink {
        /** A pointer lifted before the zone started repeating: apply one step now. */
        fun onZoneTap(zone: Int)

        /** The zone is being held: apply one repeat step now. */
        fun onZoneRepeat(zone: Int)

        /** The zone went from unpressed to pressed (invert it). */
        fun onZonePressed(zone: Int)

        /** The last pointer holding the zone lifted or was cancelled (restore it). */
        fun onZoneReleased(zone: Int)
    }

    /** Delay from press to the first repeat. */
    var holdDelayMillis: Long = HOLD_DELAY_MS

    /** Time between repeats: slow enough that the e-ink panel shows each value before the next. */
    var repeatMillis: Long = REPEAT_MS

    /** Current zone map; touches are ignored while null. Replacing it cancels every touch. */
    var zones: ZoneMap? = null
        set(value) {
            cancelAll()
            field = value
        }

    private val pointerZone = IntArray(MAX_POINTERS) { PlayLayout.NONE }
    private val pointerDown = LongArray(MAX_POINTERS)
    private val zonePointers = IntArray(zoneCount)

    /** Repeats fired in the zone's current press (0 = not repeating yet). */
    private val repeats = IntArray(zoneCount)

    fun isPressed(zone: Int): Boolean = zone in 0 until zoneCount && zonePointers[zone] > 0

    /** Number of zones pressed right now (0 = nothing to invert). */
    var pressedCount: Int = 0
        private set

    /** Number of repeats applied so far in [zone]'s current press. */
    fun repeatCount(zone: Int): Int = if (zone in 0 until zoneCount) repeats[zone] else 0

    /** Zone held by [pointerId], or [PlayLayout.NONE]. */
    fun zoneOf(pointerId: Int): Int = if (pointerId in 0 until MAX_POINTERS) pointerZone[pointerId] else PlayLayout.NONE

    fun downTime(pointerId: Int): Long = if (pointerId in 0 until MAX_POINTERS) pointerDown[pointerId] else 0L

    /** A pointer went down at dither (gx, gy). */
    fun down(pointerId: Int, gx: Int, gy: Int, eventTimeMillis: Long) {
        val map = zones ?: return
        if (pointerId !in 0 until MAX_POINTERS) return
        if (pointerZone[pointerId] != PlayLayout.NONE) release(pointerId, apply = false) // missed an up
        val zone = map.hitTest(gx, gy)
        if (zone < 0 || zone >= zoneCount) return // NONE: nothing happens
        pointerZone[pointerId] = zone
        pointerDown[pointerId] = eventTimeMillis
        if (zonePointers[zone]++ == 0) {
            pressedCount++
            repeats[zone] = 0
            if (map.repeats(zone)) scheduler.schedule(zone, holdDelayMillis)
            sink.onZonePressed(zone)
        }
    }

    /** The pointer lifted: a tap unless its zone is already repeating. */
    fun up(pointerId: Int) = release(pointerId, apply = true)

    /** Gesture cancelled: release everything, applying nothing. */
    fun cancelAll() {
        for (id in 0 until MAX_POINTERS) release(id, apply = false)
    }

    /** Releases (without applying) every pointer bound to a zone in `[from, until)`. */
    fun cancelZones(from: Int, until: Int) {
        for (id in 0 until MAX_POINTERS) {
            val z = pointerZone[id]
            if (z in from until until) release(id, apply = false)
        }
    }

    private fun release(pointerId: Int, apply: Boolean) {
        if (pointerId !in 0 until MAX_POINTERS) return
        val zone = pointerZone[pointerId]
        if (zone == PlayLayout.NONE) return
        pointerZone[pointerId] = PlayLayout.NONE
        val tap = apply && repeats[zone] == 0
        val last = --zonePointers[zone] == 0
        if (last) {
            pressedCount--
            scheduler.cancel(zone)
        }
        if (tap) sink.onZoneTap(zone)
        if (last) sink.onZoneReleased(zone)
    }

    /** Hold timer for [id] (a zone) fired. */
    override fun onTimer(id: Int) {
        if (!isPressed(id)) return // stale: released in the meantime
        if (zones?.repeats(id) != true) return
        repeats[id]++
        scheduler.schedule(id, repeatMillis)
        sink.onZoneRepeat(id)
    }

    companion object {
        /**
         * MotionEvent pointer ids are small dense ints, 0..31 on Android (`MAX_POINTER_ID` = 31), so
         * no real touch is dropped; ids outside `0 until MAX_POINTERS` are ignored silently. Test rigs
         * must reuse ids like a device does (`ScreenRig`, `CountRig`), not count up forever.
         */
        const val MAX_POINTERS = 32

        /** Long enough that a deliberate tap (≈100–250 ms on glass) never becomes a hold. */
        const val HOLD_DELAY_MS = 500L

        /**
         * One ±10 step a second (designer, on device): the Nomad's panel takes ~250–450 ms per
         * update, and faster steps made it too easy to overshoot before seeing the value.
         */
        const val REPEAT_MS = 1000L

        const val DEFAULT_HOLD_STEP = 10
    }
}
