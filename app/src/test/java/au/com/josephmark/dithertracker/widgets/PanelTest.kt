package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.widgets.RasterCanvas.Companion.INK
import au.com.josephmark.dithertracker.widgets.WidgetTesting.assertFingertip
import au.com.josephmark.dithertracker.widgets.WidgetTesting.inside
import au.com.josephmark.dithertracker.widgets.WidgetTesting.overlaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelTest {

    private val m4 = WidgetMetrics(4)

    private fun settings() = Panel(
        arrayOf(
            Stepper(0, "Starting life", 40, 1, 999),
            Toggle(2, "Auto KO", true),
            Segmented(3, "Players", arrayOf("2", "3", "4", "5", "6"), 2),
            ListRow(8, "History", icon = Icon.HISTORY, chevron = true),
        ),
        title = "Settings", closeId = 9,
    )

    @Test fun stackAtScale4() {
        val p = settings()
        val w = 240
        val h = p.naturalHeight(m4, w)
        // stroke + title 27 + padY 4 + 4 rows of 27 + 3 dividers + padY 4 + stroke
        assertEquals(1 + 27 + 4 + 4 * 27 + 3 + 4 + 1, h)
        p.layout(m4, 0, 0, w, h)
        assertTrue(p.fits)
        assertEquals(IRect(1, 1, 239, 28), p.titleBar)
        assertEquals(IRect(239 - 27, 1, 239, 28), p.close!!.bounds)
        assertEquals(8, p.titleChars)
        assertEquals(IRect(1, 32, 239, 32 + 27), p.rows[0].bounds)
        assertEquals(IRect(7, 59, 233, 60), p.dividers[0])
        assertEquals(60, p.rows[1].bounds.top)
        assertTrue(p.dividers[3].isEmpty)
        assertEquals(h - 1 - 4, p.rows[3].bounds.bottom)
    }

    @Test fun everyHitIsFingertipInsideAndDisjointAtEveryScale() {
        for (s in WidgetTesting.scales) {
            val m = WidgetMetrics(s)
            val p = settings()
            val w = 240 * 4 / s
            p.layout(m, 0, 0, w, p.naturalHeight(m, w))
            val r = HitRegion()
            p.collectHits(r, WidgetFrame().set(0, 0, w, p.bounds.height, 0))
            assertEquals(1 + 2 + 1 + 5 + 1, r.size)
            for (i in 0 until r.size) {
                assertFingertip(m, r.hitAt(i), "id ${r.idAt(i)}")
                assertTrue(inside(r.hitAt(i), p.bounds))
                for (j in 0 until i) assertFalse("$m ${r.idAt(i)} overlaps ${r.idAt(j)}", overlaps(r.hitAt(i), r.hitAt(j)))
            }
        }
    }

    @Test fun shortPanelReportsNotFitting() {
        val p = settings()
        p.layout(m4, 0, 0, 240, 100)
        assertFalse(p.fits)
    }

    @Test fun twoColumns() {
        val rows = arrayOf<Widget>(Toggle(0, "A"), Toggle(1, "B"), Toggle(2, "C"), Toggle(3, "D"), Toggle(4, "E"))
        val p = Panel(rows, columnBreak = 3)
        assertEquals(2, p.columns)
        val w = 401
        val h = p.naturalHeight(m4, w)
        assertEquals(2 + 2 * 4 + 3 * 27 + 2, h) // no title, the taller column wins
        p.layout(m4, 0, 0, w, h)
        assertEquals(IRect(1, 5, 1 + 199, 5 + 27), rows[0].bounds)
        assertEquals(IRect(201, 5, 400, 5 + 27), rows[3].bounds)
        assertEquals(IRect(200, 5, 201, h - 5), p.columnRule)
        assertTrue(p.dividers[2].isEmpty) // last of column 0
        assertFalse(p.dividers[3].isEmpty)
    }

    @Test fun lookAtScale4() {
        val p = settings()
        p.layout(m4, 0, 0, 240, p.naturalHeight(m4, 240))
        val c = RasterCanvas(240, p.bounds.height)
        WidgetPainter(c).draw(WidgetFrame().set(0, 0, 240, p.bounds.height, 0), p)
        assertEquals(INK, c[0, 0]); assertEquals(INK, c[239, p.bounds.height - 1]) // outline
        // Dotted divider: alternate pixels along it.
        val d = p.dividers[0]
        assertTrue(c[d.left, d.top] != c[d.left + 1, d.top])
        // Title on a paper backing over the TAB dither: the backing's top-left corner is paper.
        val tx = p.titleRect.left - m4.halo
        val ty = p.titleRect.top + (27 - 7) / 2 - m4.halo
        assertEquals(RasterCanvas.PAPER, c[tx, ty])
        c.writePng(java.io.File(WidgetTesting.outDir, "panel-s4.png"), 4)
    }

    @Test fun rotationIsExactForAllFourRotations() {
        for (s in intArrayOf(2, 4, 6)) {
            val m = WidgetMetrics(s)
            val p = settings()
            val w = 240 * 4 / s
            val h = p.naturalHeight(m, w)
            p.layout(m, 0, 0, w, h)
            WidgetTesting.assertRotationExact(w, h) { pt, f -> pt.draw(f, p) }
        }
    }

    @Test fun rotatedPanelHitsMapToTheSameControls() {
        // Nomad landscape grid at S = 4; the panel faces each seat in turn.
        val screen = IRect(0, 0, 468, 351)
        for (rot in WidgetTesting.rotations) {
            val p = settings()
            val f = WidgetFrame()
            val side = rot % 180 != 0
            val w = 240
            val h = p.naturalHeight(m4, w)
            p.layout(m4, 0, 0, w, h)
            f.setCentred(screen, rot, w, h)
            assertTrue(inside(IRect(f.x, f.y, f.x + f.footprintW, f.y + f.footprintH), screen))
            assertEquals(if (side) h else w, f.footprintW)
            val r = HitRegion()
            p.collectHits(r, f)
            // The local centre of every control maps to a screen point that hits that control.
            val st = p.rows[0] as Stepper
            val local = listOf(0 to st.minusHit, 1 to st.plusHit, 9 to p.close!!.hit, 8 to p.rows[3].bounds)
            val tmp = IRect()
            for ((id, lr) in local) {
                f.map(lr.left + lr.width / 2, lr.top + lr.height / 2, 1, 1, tmp)
                assertEquals("rot $rot id $id", id, r.hitTest(tmp.left, tmp.top))
                val hr = IRect(); r.hitRect(id, hr)
                assertEquals(f.map(lr, IRect()), hr)
            }
            // The close button is at the reader's top-right whichever way the panel faces.
            val c = IRect(); r.hitRect(9, c)
            when (rot) {
                0 -> { assertEquals(f.y + 1, c.top); assertEquals(f.x + w - 1, c.right) }
                90 -> { assertEquals(f.x + h - 1, c.right); assertEquals(f.y + w - 1, c.bottom) }
                180 -> { assertEquals(f.y + h - 1, c.bottom); assertEquals(f.x + 1, c.left) }
                270 -> { assertEquals(f.x + 1, c.left); assertEquals(f.y + 1, c.top) }
            }
        }
    }

    @Test fun pressFallbackMatchesInversion() {
        val p = settings()
        val h = p.naturalHeight(m4, 240)
        p.layout(m4, 0, 0, 240, h)
        val f = WidgetFrame().set(0, 0, 240, h, 0)
        val r = HitRegion()
        p.collectHits(r, f)
        WidgetTesting.assertFallbackMatchesInvert(240, h, r) { it.draw(f, p) }
    }
}
