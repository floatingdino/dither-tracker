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
        override fun onZonePressed(zone: Int) { events += "press$zone" }
        override fun onZoneReleased(zone: Int) { events += "release$zone" }
    }
    private val layout = PlayLayout.compute(DitherGrid(1872, 1404, 4))
    private val input = PlayInput(sink).also { it.layout = layout }

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
}
