package au.com.josephmark.dithertracker.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMetricsTest {

    /** 9 mm in device px at [dpi] (fractional), the physical fingertip rule. */
    private fun nineMm(dpi: Int) = 9.0 * dpi / 25.4

    @Test fun nomadAtScale4() {
        val m = WidgetMetrics(4)
        assertEquals(1, m.k)
        assertEquals(1, m.stroke)
        assertEquals(27, m.target)
        assertEquals(27, m.rowH)
        assertEquals(16, m.icon)
        assertEquals(6, m.padX)
        assertEquals(4, m.gap)
        assertEquals(2, m.inset)
        assertEquals(9, m.textH)
        assertEquals(14, m.numH)
    }

    @Test fun targetIsNineMillimetresAtEveryScaleAndDensity() {
        for (dpi in intArrayOf(160, 213, 240, 300, 320, 400)) for (s in 2..6) {
            val m = WidgetMetrics(s, dpi)
            assertTrue("$m", m.target * s >= nineMm(dpi))
            assertTrue("$m not minimal", (m.target - 1) * s < nineMm(dpi))
            assertTrue("$m", m.rowH >= m.target)
        }
    }

    @Test fun physicalSizesTrackScale4() {
        // Per scale on the Nomad: (k, target, rowH).
        val expect = mapOf(2 to Triple(2, 54, 54), 3 to Triple(1, 36, 36), 4 to Triple(1, 27, 27), 5 to Triple(1, 22, 22), 6 to Triple(1, 18, 20))
        for ((s, e) in expect) {
            val m = WidgetMetrics(s)
            assertEquals("$m", e.first, m.k)
            assertEquals("$m", e.second, m.target)
            assertEquals("$m", e.third, m.rowH)
            // Every row holds an icon or a num-m value with an inset and a stroke on both sides.
            assertTrue("$m", m.rowH >= maxOf(m.icon, m.numH, m.textH) + 2 * (m.inset + m.stroke))
        }
    }
}
