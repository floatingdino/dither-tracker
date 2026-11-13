package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.drawer.DrawerLayout
import au.com.josephmark.dithertracker.drawer.DrawerState
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.MINUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.PLUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayInputTest {

    private val events = ArrayList<String>()
    private val sink = object : PlayInput.Sink {
        override fun onZoneTap(zone: Int) { events += "tap$zone" }
        override fun onZoneRepeat(zone: Int) { events += "rep$zone" }
        override fun onZonePressed(zone: Int) { events += "press$zone" }
        override fun onZoneReleased(zone: Int) { events += "release$zone" }
    }
    private val layout = PlayLayout.compute(DitherGrid(1872, 1404, 4))
    private val drawerLayout = DrawerLayout(layout)
    private val drawers = DrawerState(4)
    private var lifeRepeats = true
    private val map = object : PlayInput.ZoneMap {
        override fun hitTest(gx: Int, gy: Int) = drawerLayout.hitTest(gx, gy, drawers)
        override fun repeats(zone: Int) = if (DrawerZones.isLife(zone)) lifeRepeats else false
    }
    private val clock = FakeScheduler(DrawerZones.COUNT)
    private val input = PlayInput(sink, clock, DrawerZones.COUNT).also { it.zones = map; clock.target = it }

    private fun centre(z: Int): IntArray {
        val r = layout.zones[z]
        return intArrayOf((r.left + r.right) / 2, (r.top + r.bottom) / 2)
    }

    private fun down(id: Int, z: Int) { val c = centre(z); input.down(id, c[0], c[1], 0) }

    // ---- press model: down = feedback only, lift = tap ------------------------------------

    @Test fun downOnlyPressesAndUpTaps() {
        down(0, zone(3, MINUS))
        assertEquals(listOf("press6"), events)
        input.up(0)
        assertEquals(listOf("press6", "tap6", "release6"), events)
    }

    @Test fun simultaneousTapsOnDifferentSeats() {
        down(0, zone(3, MINUS))
        down(1, zone(0, PLUS))
        assertEquals(listOf("press6", "press1"), events)
        assertTrue(input.isPressed(zone(3, MINUS))); assertTrue(input.isPressed(zone(0, PLUS)))
        input.up(0)
        assertFalse(input.isPressed(zone(3, MINUS))); assertTrue(input.isPressed(zone(0, PLUS)))
        input.up(1)
        assertEquals(listOf("press6", "press1", "tap6", "release6", "tap1", "release1"), events)
    }

    @Test fun twoFingersInOneZoneEachTapOnTheirOwnLift() {
        down(3, zone(2, PLUS))
        down(5, zone(2, PLUS))
        assertEquals(listOf("press5"), events)
        input.up(3)
        assertEquals(listOf("press5", "tap5"), events)
        assertTrue(input.isPressed(zone(2, PLUS)))
        input.up(5)
        assertEquals(listOf("press5", "tap5", "tap5", "release5"), events)
    }

    @Test fun reservedAreasAndMarginsDoNothing() {
        input.down(0, layout.diamondCx, layout.diamondCy, 0)
        input.down(2, layout.dividerV.left, 10, 0)
        input.down(3, -5, -5, 0)
        assertTrue(events.isEmpty())
        input.up(0); input.up(2); input.up(3)
        assertTrue(events.isEmpty())
    }

    @Test fun theTabIsADrawerZone() {
        input.down(1, layout.tab[1].left + 3, layout.tab[1].top + 3, 0)
        val z = DrawerZones.zone(1, DrawerZones.TAB)
        assertEquals(listOf("press$z"), events)
        input.up(1)
        assertEquals(listOf("press$z", "tap$z", "release$z"), events)
    }

    @Test fun cancelReleasesEverythingOnceAndAppliesNothing() {
        down(0, zone(0, MINUS)); down(1, zone(1, MINUS)); down(2, zone(1, MINUS))
        events.clear()
        input.cancelAll()
        assertEquals(listOf("release0", "release2"), events)
        input.cancelAll()
        assertEquals(2, events.size)
    }

    @Test fun cancelZonesOnlyTouchesTheRange() {
        down(0, zone(0, MINUS)); down(1, zone(1, PLUS))
        events.clear()
        input.cancelZones(zone(1, MINUS), zone(1, PLUS) + 1)
        assertEquals(listOf("release3"), events)
        assertTrue(input.isPressed(zone(0, MINUS)))
    }

    @Test fun pointerKeepsItsZoneAndRepeatedDownReleasesWithoutTapping() {
        down(0, zone(3, PLUS))
        assertEquals(zone(3, PLUS), input.zoneOf(0))
        down(0, zone(2, PLUS)) // missed up
        assertEquals(listOf("press7", "release7", "press5"), events)
    }

    @Test fun outOfRangePointerIdsAreIgnored() {
        val c = centre(zone(3, MINUS))
        input.down(PlayInput.MAX_POINTERS, c[0], c[1], 0)
        input.down(-1, c[0], c[1], 0)
        assertTrue(events.isEmpty())
    }

    // ---- hold-to-repeat -------------------------------------------------------------------

    private fun reps() = events.filter { it.startsWith("rep") }

    @Test fun cadenceDefaults() {
        assertEquals(500L, PlayInput.HOLD_DELAY_MS)
        assertEquals(1000L, PlayInput.REPEAT_MS)
    }

    @Test fun tapShorterThanHoldDelayIsOneTapOnRelease() {
        down(0, zone(3, MINUS))
        clock.advance(PlayInput.HOLD_DELAY_MS - 1)
        assertEquals(listOf("press6"), events)
        input.up(0)
        clock.advance(5_000)
        assertEquals(listOf("press6", "tap6", "release6"), events)
        assertFalse(clock.isArmed(zone(3, MINUS)))
    }

    @Test fun holdRepeatsWithoutATap() {
        down(0, zone(3, MINUS))
        clock.advance(PlayInput.HOLD_DELAY_MS)
        assertEquals(listOf("rep6"), reps()) // 40 → 30
        clock.advance(PlayInput.REPEAT_MS - 1)
        assertEquals(1, reps().size)
        clock.advance(1)
        assertEquals(2, reps().size) // → 20
        input.up(0)
        assertEquals(listOf("press6", "rep6", "rep6", "release6"), events) // no tap on release
    }

    @Test fun adbSwipeOf2700msGivesThreeSteps() {
        // `input swipe x y x y 2700`: repeats at 500, 1500, 2500 → −30 (200 ms margin either side).
        down(0, zone(3, MINUS))
        clock.advance(2700)
        input.up(0)
        assertEquals(3, reps().size)
        assertFalse(events.contains("tap6"))
    }

    @Test fun releaseStopsRepeatImmediately() {
        down(0, zone(3, PLUS))
        clock.advance(PlayInput.HOLD_DELAY_MS + 10)
        input.up(0)
        assertFalse(clock.isArmed(zone(3, PLUS)))
        clock.advance(10_000)
        assertEquals(1, reps().size)
        assertEquals("release7", events.last())
    }

    @Test fun cancelStopsAllRepeats() {
        down(0, zone(0, MINUS)); down(1, zone(2, PLUS))
        clock.advance(PlayInput.HOLD_DELAY_MS)
        assertEquals(2, reps().size)
        input.cancelAll()
        clock.advance(10_000)
        assertEquals(2, reps().size)
        for (z in 0 until 8) assertFalse(clock.isArmed(z))
    }

    @Test fun twoZonesRepeatIndependently() {
        down(0, zone(1, MINUS))
        clock.advance(200)
        down(1, zone(3, PLUS))
        clock.advance(PlayInput.HOLD_DELAY_MS - 200) // zone 2 fires at 500, zone 7 at 700
        assertEquals(listOf("rep2"), reps())
        clock.advance(200)
        assertEquals(listOf("rep2", "rep7"), reps())
        input.up(0) // seat 1 lets go; seat 3 keeps going
        clock.advance(PlayInput.REPEAT_MS * 2)
        assertEquals(listOf("rep2", "rep7", "rep7", "rep7"), reps())
    }

    @Test fun secondFingerInAHeldZoneDoesNotSpeedItUpOrTap() {
        down(0, zone(2, MINUS))
        clock.advance(300)
        down(1, zone(2, MINUS))
        assertEquals(PlayInput.HOLD_DELAY_MS, clock.dueAt(zone(2, MINUS)))
        clock.advance(PlayInput.HOLD_DELAY_MS - 300 + PlayInput.REPEAT_MS * 2)
        assertEquals(3, reps().size)
        input.up(0) // zone is repeating: lifting one finger is not a tap
        assertFalse(events.contains("tap4"))
        clock.advance(PlayInput.REPEAT_MS)
        assertEquals(4, reps().size)
        input.up(1)
        clock.advance(PlayInput.REPEAT_MS * 3)
        assertEquals(4, reps().size)
        assertFalse(events.contains("tap4"))
    }

    @Test fun pressAgainAfterReleaseStartsAFreshHold() {
        down(0, zone(3, MINUS))
        clock.advance(PlayInput.HOLD_DELAY_MS + PlayInput.REPEAT_MS)
        input.up(0)
        events.clear()
        down(0, zone(3, MINUS))
        clock.advance(PlayInput.HOLD_DELAY_MS - 1)
        assertTrue(reps().isEmpty())
        clock.advance(1)
        assertEquals(listOf("rep6"), reps())
    }

    @Test fun nonRepeatingZonesNeverArmATimer() {
        lifeRepeats = false
        down(0, zone(3, PLUS))
        assertFalse(clock.isArmed(zone(3, PLUS)))
        clock.advance(10_000)
        input.up(0)
        assertEquals(listOf("press7", "tap7", "release7"), events) // a long press is still a tap
    }

    @Test fun cadenceIsTunable() {
        input.holdDelayMillis = 600; input.repeatMillis = 300
        down(0, zone(3, PLUS))
        clock.advance(599); assertTrue(reps().isEmpty())
        clock.advance(1 + 300 * 2)
        assertEquals(3, reps().size)
    }

    @Test fun staleTimerAfterRebindDoesNothing() {
        down(0, zone(3, MINUS))
        input.zones = map // re-binding cancels everything
        input.onTimer(zone(3, MINUS))
        assertTrue(reps().isEmpty())
        assertFalse(events.contains("tap6"))
    }
}
