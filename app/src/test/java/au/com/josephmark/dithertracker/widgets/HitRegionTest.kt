package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HitRegionTest {

    @Test fun hitTestFindsIdsAndMisses() {
        val r = HitRegion()
        r.add(3, IRect(0, 0, 10, 10))
        r.add(7, IRect(20, 0, 30, 10), IRect(22, 2, 28, 8), repeats = true)
        assertEquals(3, r.hitTest(0, 0))
        assertEquals(3, r.hitTest(9, 9))
        assertEquals(HitRegion.NONE, r.hitTest(10, 5))
        assertEquals(7, r.hitTest(20, 0)) // touch rect is larger than the feedback rect
        assertTrue(r.repeats(7)); assertFalse(r.repeats(3)); assertFalse(r.repeats(99))
        val out = IRect()
        assertTrue(r.feedbackRect(7, out)); assertEquals(IRect(22, 2, 28, 8), out)
        assertTrue(r.hitRect(7, out)); assertEquals(IRect(20, 0, 30, 10), out)
        assertFalse(r.feedbackRect(5, out))
    }

    @Test fun laterEntriesWin() {
        val r = HitRegion()
        r.add(1, IRect(0, 0, 100, 100)) // screen below
        r.add(2, IRect(40, 40, 60, 60)) // overlay collected after it
        assertEquals(2, r.hitTest(50, 50))
        assertEquals(1, r.hitTest(10, 10))
    }

    @Test fun addLocalMapsThroughTheFrame() {
        val f = WidgetFrame().set(100, 50, 40, 20, 180)
        val r = HitRegion()
        r.addLocal(4, f, IRect(0, 0, 10, 20))
        // Local left strip of a 180° frame is the screen's right strip.
        val out = IRect()
        r.hitRect(4, out)
        assertEquals(IRect(130, 50, 140, 70), out)
        assertEquals(4, r.hitTest(135, 60))
        assertEquals(HitRegion.NONE, r.hitTest(105, 60))
    }

    @Test fun clearEmpties() {
        val r = HitRegion(2)
        r.add(0, IRect(0, 0, 5, 5)); r.add(1, IRect(0, 0, 5, 5))
        r.clear()
        assertEquals(0, r.size)
        assertEquals(HitRegion.NONE, r.hitTest(1, 1))
        r.add(0, IRect(0, 0, 5, 5)) // capacity reusable
        assertEquals(1, r.size)
    }

    @Test(expected = IllegalStateException::class) fun overflowThrows() {
        val r = HitRegion(1)
        r.add(0, IRect(0, 0, 1, 1)); r.add(1, IRect(0, 0, 1, 1))
    }
}
