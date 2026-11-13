package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.dither.DitherGrid
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
        override fun onZoneTap(zone: Int, eventTimeMillis: Long) { events += "tap$zone" }
        override fun onZoneRepeat(zone: Int, delta: Int) { events += "rep$zone:$delta" }
        override fun onZonePressed(zone: Int) { events += "press$zone" }
        override fun onZoneReleased(zone: Int) { events += "release$zone" }
    }
    private val layout = PlayLayout.compute(DitherGrid(1872, 1404, 4))
    private val clock = FakeScheduler(PlayLayout.PLAYERS * 2)
    private val input = PlayInput(sink, clock).also { it.layout = layout; clock.target = it }

    private fun centre(z: Int): IntArray {
        val r = layout.zones[z]
        return intArrayOf((r.left + r.right) / 2, (r.top + r.bottom) / 2)
    }

    private fun down(id: Int, z: Int) { val c = centre(z); input.down(id, c[0], c[1], 0) }

    @Test fun simultaneousTapsOnDifferentSeats() {
        down(0, zone(3, MINUS))
        down(1, zone(0, PLUS))
        assertEquals(listOf("tap6", "press6", "tap1", "press1"), events)
        assertTrue(input.isPressed(zone(3, MINUS))); assertTrue(input.isPressed(zone(0, PLUS)))
        input.up(0)
        assertFalse(input.isPressed(zone(3, MINUS))); assertTrue(input.isPressed(zone(0, PLUS)))
        input.up(1)
        assertEquals(listOf("tap6", "press6", "tap1", "press1", "release6", "release1"), events)
    }

    @Test fun twoFingersInOneZoneReleaseOnLastUp() {
        down(3, zone(2, PLUS))
        down(5, zone(2, PLUS))
        assertEquals(listOf("tap5", "press5", "tap5"), events)
        input.up(3)
        assertTrue(input.isPressed(zone(2, PLUS)))
        input.up(5)
        assertEquals("release5", events.last())
    }

    @Test fun reservedAreasAndMarginsDoNothing() {
        input.down(0, layout.diamondCx, layout.diamondCy, 0)
        input.down(1, layout.tab[1].left + 3, layout.tab[1].top + 3, 0)
        input.down(2, layout.dividerV.left, 10, 0)
        input.down(3, -5, -5, 0)
        assertTrue(events.isEmpty())
        input.up(0); input.up(1); input.up(2); input.up(3)
        assertTrue(events.isEmpty())
    }

    @Test fun cancelReleasesEverythingOnce() {
        down(0, zone(0, MINUS)); down(1, zone(1, MINUS)); down(2, zone(1, MINUS))
        events.clear()
        input.cancelAll()
        assertEquals(listOf("release0", "release2"), events)
        input.cancelAll()
        assertEquals(2, events.size)
    }

    @Test fun pointerKeepsItsZoneAndRepeatedDownReleasesFirst() {
        down(0, zone(3, PLUS))
        assertEquals(zone(3, PLUS), input.zoneOf(0))
        down(0, zone(2, PLUS)) // missed up
        assertEquals(listOf("tap7", "press7", "release7", "tap5", "press5"), events)
    }

    @Test fun outOfRangePointerIdsAreIgnored() {
        val c = centre(zone(3, MINUS))
        input.down(PlayInput.MAX_POINTERS, c[0], c[1], 0)
        input.down(-1, c[0], c[1], 0)
        assertTrue(events.isEmpty())
    }

    // ---- hold-to-repeat -------------------------------------------------------------------

    private fun reps() = events.filter { it.startsWith("rep") }

    @Test fun tapShorterThanHoldDelayNeverRepeats() {
        down(0, zone(3, MINUS))
        clock.advance(PlayInput.HOLD_DELAY_MS - 1)
        input.up(0)
        clock.advance(5_000)
        assertEquals(listOf("tap6", "press6", "release6"), events)
        assertFalse(clock.isArmed(zone(3, MINUS)))
    }

    @Test fun holdRepeatsAfterDelayAtFixedCadenceAndAbsorbsTheTap() {
        down(0, zone(3, MINUS))
        clock.advance(PlayInput.HOLD_DELAY_MS)
        assertEquals(listOf("rep6:-9"), reps()) // 40 → 39 (tap) → 30
        clock.advance(PlayInput.REPEAT_MS - 1)
        assertEquals(1, reps().size)
        clock.advance(1)
        assertEquals(listOf("rep6:-9", "rep6:-10"), reps()) // → 20
        clock.advance(PlayInput.REPEAT_MS * 3)
        assertEquals(5, reps().size)
        assertEquals(5, input.repeatCount(zone(3, MINUS)))
        // net from press: -1 - 9 - 10*4 = -50, i.e. exactly five tens
        assertEquals(-50, -1 + reps().sumOf { it.substringAfter(':').toInt() })
        assertTrue(PlayInput.REPEAT_MS >= 300)
    }

    @Test fun plusHoldsUpward() {
        down(0, zone(2, PLUS))
        clock.advance(PlayInput.HOLD_DELAY_MS + PlayInput.REPEAT_MS)
        assertEquals(listOf("rep5:9", "rep5:10"), reps())
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
        assertEquals(listOf("rep2:-9"), reps())
        clock.advance(200)
        assertEquals(listOf("rep2:-9", "rep7:9"), reps())
        input.up(0) // seat 1 lets go; seat 3 keeps going
        clock.advance(PlayInput.REPEAT_MS * 2)
        assertEquals(listOf("rep2:-9", "rep7:9", "rep7:10", "rep7:10"), reps())
    }

    @Test fun twoFingersInOneZoneDoNotDoubleTheRate() {
        down(0, zone(2, MINUS))
        clock.advance(300)
        down(1, zone(2, MINUS)) // second finger: one extra tap, same timer
        assertEquals(clock.dueAt(zone(2, MINUS)), PlayInput.HOLD_DELAY_MS)
        clock.advance(PlayInput.HOLD_DELAY_MS - 300 + PlayInput.REPEAT_MS * 2)
        assertEquals(listOf("rep4:-9", "rep4:-10", "rep4:-10"), reps())
        input.up(0) // one finger still holds: keeps repeating at the same cadence
        clock.advance(PlayInput.REPEAT_MS)
        assertEquals(4, reps().size)
        input.up(1)
        clock.advance(PlayInput.REPEAT_MS * 3)
        assertEquals(4, reps().size)
    }

    @Test fun pressAgainAfterReleaseStartsAFreshHold() {
        down(0, zone(3, MINUS))
        clock.advance(PlayInput.HOLD_DELAY_MS + PlayInput.REPEAT_MS)
        input.up(0)
        events.clear()
        down(0, zone(3, MINUS))
        clock.advance(PlayInput.HOLD_DELAY_MS)
        assertEquals(listOf("rep6:-9"), reps()) // absorbs this press's tap again
    }

    @Test fun stepIsAParameter() {
        input.holdStep = 5
        down(0, zone(3, PLUS))
        clock.advance(PlayInput.HOLD_DELAY_MS + PlayInput.REPEAT_MS)
        assertEquals(listOf("rep7:4", "rep7:5"), reps())
        input.up(0); events.clear()
        input.holdStep = 1 // first repeat would be 0: skipped (no frame), then ±1s
        down(0, zone(3, PLUS))
        clock.advance(PlayInput.HOLD_DELAY_MS + PlayInput.REPEAT_MS)
        assertEquals(listOf("rep7:1"), reps())
        input.up(0); events.clear()
        input.holdStep = 0 // off
        down(0, zone(3, PLUS))
        clock.advance(10_000)
        assertTrue(reps().isEmpty())
        assertFalse(clock.isArmed(zone(3, PLUS)))
    }

    @Test fun staleTimerAfterLayoutChangeDoesNothing() {
        down(0, zone(3, MINUS))
        input.layout = layout // re-layout cancels everything
        input.onTimer(zone(3, MINUS))
        assertTrue(reps().isEmpty())
    }
}
