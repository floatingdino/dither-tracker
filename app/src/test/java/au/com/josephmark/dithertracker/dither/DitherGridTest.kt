package au.com.josephmark.dithertracker.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DitherGridTest {

    @Test fun nomadPortraitAtFourHasNoMargins() {
        val g = DitherGrid(1404, 1872, 4)
        assertEquals(351, g.width)
        assertEquals(468, g.height)
        assertEquals(0, g.marginLeft + g.marginRight + g.marginTop + g.marginBottom)
    }

    @Test fun nomadAtFiveHasCentredMargins() {
        val g = DitherGrid(1404, 1872, 5)
        assertEquals(280, g.width) // 1400 used, 4 left over
        assertEquals(374, g.height) // 1870 used, 2 left over
        assertEquals(2, g.marginLeft); assertEquals(2, g.marginRight)
        assertEquals(1, g.marginTop); assertEquals(1, g.marginBottom)
    }

    @Test fun oddLeftoverGoesRightAndBottom() {
        val g = DitherGrid(1872, 1404, 5) // 1870 + 2? -> 374*5=1870, 2 left; 280*5=1400, 4 left
        assertEquals(1, g.marginLeft); assertEquals(1, g.marginRight)
        val h = DitherGrid(103, 107, 4) // 25*4=100 -> 3 left; 26*4=104 -> 3 left
        assertEquals(1, h.marginLeft); assertEquals(2, h.marginRight)
        assertEquals(1, h.marginTop); assertEquals(2, h.marginBottom)
    }

    @Test fun marginsAlwaysSumAndAreDeterministic() {
        for (s in 1..8) for (w in 0..60) {
            val g = DitherGrid(w, w + 7, s)
            assertEquals(w, g.marginLeft + g.width * s + g.marginRight)
            assertEquals(w + 7, g.marginTop + g.height * s + g.marginBottom)
            assertTrue(g.marginLeft in 0 until s && g.marginRight in 0 until s)
            assertTrue(g.marginRight - g.marginLeft in 0..1)
            assertEquals(g, DitherGrid(w, w + 7, s))
        }
    }

    @Test fun ditherToDeviceAndBack() {
        val g = DitherGrid(1404, 1872, 5)
        assertEquals(2, g.toDeviceX(0)); assertEquals(1, g.toDeviceY(0))
        assertEquals(2 + 5 * 280, g.toDeviceX(280))
        val d = g.toDevice(IRect(3, 4, 10, 12), IRect())
        assertEquals(IRect(17, 21, 52, 61), d)
        // round trip is exact
        assertEquals(IRect(3, 4, 10, 12), g.toDitherCovering(d, IRect()))
        assertEquals(IRect(3, 4, 10, 12), g.toDitherInside(d, IRect()))
        for (x in 0 until 40) {
            assertEquals(x, g.toDitherX(g.toDeviceX(x)))
            assertEquals(x, g.toDitherX(g.toDeviceX(x) + 4))
        }
    }

    @Test fun deviceToDitherRoundsInwardAndOutward() {
        val g = DitherGrid(100, 100, 4) // no margins
        val dev = IRect(5, 5, 14, 15)
        assertEquals(IRect(1, 1, 4, 4), g.toDitherCovering(dev, IRect()))
        assertEquals(IRect(2, 2, 3, 3), g.toDitherInside(dev, IRect()))
        // inside margins → negative / beyond-grid indices, never wrapped
        val m = DitherGrid(103, 103, 4)
        assertEquals(-1, m.toDitherX(0))
        assertEquals(25, m.toDitherX(102))
    }

    @Test fun autoScaleNomadIsFour() {
        assertEquals(4, DitherGrid.chooseScale(1404, 1872, 300))
        assertEquals(4, DitherGrid.chooseScale(1872, 1404, 300))
    }

    @Test fun autoScaleClampsAndBacksOff() {
        assertEquals(2, DitherGrid.chooseScale(320, 480, 120)) // low dpi → min 2
        assertEquals(6, DitherGrid.chooseScale(1440, 3200, 640)) // 8.5 → clamped 6
        assertEquals(2, DitherGrid.chooseScale(1080, 2400, 160)) // 2.13 → 2
        assertEquals(5, DitherGrid.chooseScale(1080, 2400, 400)) // 5.33 → 5, 1080/5=216 >= 200
        assertEquals(4, DitherGrid.chooseScale(900, 1600, 480)) // 6 would leave 150 px; backs off to 4 (225)
        assertEquals(2, DitherGrid.chooseScale(300, 300, 600)) // never below 2
    }

    @Test fun overrideWinsAndIsClamped() {
        assertEquals(3, DitherGrid.chooseScale(1404, 1872, 300, override = 3))
        assertEquals(6, DitherGrid.chooseScale(1404, 1872, 300, override = 9))
        assertEquals(2, DitherGrid.chooseScale(1404, 1872, 300, override = 1))
    }

    /** T62: a cutout's safe inset moves the grid off it; the usable area is what is measured. */
    @Test fun insetsShiftTheGrid() {
        val g = DitherGrid(2400 - 120, 1080, 2, insetLeft = 120)
        assertEquals(1140, g.width)
        assertEquals(120, g.marginLeft)
        assertEquals(0, g.marginRight)
        assertEquals(120 + 2 * 10, g.toDeviceX(10))
        assertEquals(10, g.toDitherX(120 + 2 * 10 + 1))
        assertEquals(DitherGrid(1872, 1404, 4), DitherGrid(1872, 1404, 4, 0, 0))
        val h = DitherGrid(1001, 700, 4, insetLeft = 30, insetTop = 7)
        assertEquals(30 + 0, h.marginLeft) // leftover 1 goes right
        assertEquals(7 + 0, h.marginTop)
        assertEquals(1, h.marginRight)
    }
}
