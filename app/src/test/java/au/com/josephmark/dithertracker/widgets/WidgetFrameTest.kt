package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetFrameTest {

    private val rotations = intArrayOf(0, 90, 180, 270)

    @Test fun upright() {
        val f = WidgetFrame().set(10, 20, 100, 50, 0)
        assertEquals(IRect(15, 27, 25, 30), f.map(5, 7, 10, 3, IRect()))
    }

    @Test fun rotatedCornersLandOnTheRightScreenCorners() {
        // Local top-left pixel → screen corner the reader's top-left is turned to.
        val W = 30; val H = 20
        val corner = mapOf(0 to (0 to 0), 90 to (H - 1 to 0), 180 to (W - 1 to H - 1), 270 to (0 to W - 1))
        for (r in rotations) {
            val f = WidgetFrame().set(0, 0, W, H, r)
            val p = f.map(0, 0, 1, 1, IRect())
            assertEquals("rot $r", corner[r]!!.first, p.left)
            assertEquals("rot $r", corner[r]!!.second, p.top)
            assertEquals(if (r % 180 == 0) W else H, f.footprintW)
        }
    }

    @Test fun localOfMappedPixelRoundTrips() {
        for (r in rotations) {
            val f = WidgetFrame().set(7, 3, 13, 9, r)
            val p = IRect()
            for (u in 0 until 13) for (v in 0 until 9) {
                f.map(u, v, 1, 1, p)
                assertEquals("rot $r", u, f.localX(p.left, p.top))
                assertEquals("rot $r", v, f.localY(p.left, p.top))
            }
        }
    }

    @Test fun mappedRectsStayInsideTheFootprint() {
        for (r in rotations) {
            val f = WidgetFrame().set(5, 6, 40, 25, r)
            val o = f.map(0, 0, 40, 25, IRect())
            assertEquals(IRect(5, 6, 5 + f.footprintW, 6 + f.footprintH), o)
        }
    }

    @Test fun centredIsTheSameForTheReaderAtEveryRotation() {
        // An odd leftover: the reader always sees the extra pixel after (right/below) the box.
        val container = IRect(0, 0, 101, 61)
        for (r in rotations) {
            val f = WidgetFrame().setCentred(container, r, 40, 20)
            // Local pixel (0,0) of the box, seen from the container's own reader frame.
            val cont = WidgetFrame().setScreen(container, r)
            val p = f.map(0, 0, 1, 1, IRect())
            val lx = cont.localX(p.left, p.top)
            val ly = cont.localY(p.left, p.top)
            assertEquals("rot $r", (cont.width - 40) / 2, lx)
            assertEquals("rot $r", (cont.height - 20) / 2, ly)
        }
    }

    @Test fun setScreenSwapsSidewaysBox() {
        val f = WidgetFrame().setScreen(IRect(0, 0, 80, 30), 90)
        assertEquals(30, f.width); assertEquals(80, f.height)
    }
}
