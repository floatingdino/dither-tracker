package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.type.TextLayout
import au.com.josephmark.dithertracker.widgets.RasterCanvas.Companion.INK
import au.com.josephmark.dithertracker.widgets.RasterCanvas.Companion.PAPER
import au.com.josephmark.dithertracker.widgets.WidgetTesting.assertFingertip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonTest {

    private val m4 = WidgetMetrics(4)

    private fun laidOut(b: Button, m: WidgetMetrics, w: Int = b.naturalWidth(m)): Button {
        b.layout(m, 0, 0, w, b.naturalHeight(m, w)); return b
    }

    @Test fun sizesAtScale4() {
        val b = Button(0, "NEW GAME")
        assertEquals(8 * 6 - 1 + 2 * (1 + 6), b.naturalWidth(m4)) // 61
        assertEquals(27, b.naturalHeight(m4, 61))
        assertEquals(27, IconButton(1, Icon.CLOSE).naturalWidth(m4))
        assertEquals(16 + 4 + 5 * 6 - 1 + 14, Button(2, "RESET", Icon.RESET).naturalWidth(m4)) // 63
        assertEquals(27, Button(3, "OK").naturalWidth(m4)) // never under the fingertip size
    }

    @Test fun naturalSizeIsFingertipAndFitsTheLabelAtEveryScale() {
        for (s in WidgetTesting.scales) {
            val m = WidgetMetrics(s)
            for (b in listOf(Button(0, "OK"), Button(0, "COMMANDER DAMAGE"), Button(0, "UNDO", Icon.UNDO), IconButton(0, Icon.CLOSE))) {
                laidOut(b, m)
                assertFingertip(m, b.bounds, "$b")
                assertEquals("$m", b.label.length, b.labelChars) // natural width never truncates
                assertTrue(WidgetTesting.inside(b.labelRect, b.bounds))
                if (b.icon != null) assertTrue(WidgetTesting.inside(b.iconRect, b.bounds))
                assertFalse(WidgetTesting.overlaps(b.iconRect, b.labelRect))
            }
        }
    }

    @Test fun contentIsCentred() {
        val b = laidOut(Button(0, "NEW GAME"), m4, 101)
        // 47 px of label in 101: 27 px either side.
        assertEquals(27, b.labelRect.left)
        assertEquals(101 - 27, b.labelRect.right)
        val ib = laidOut(IconButton(0, Icon.CLOSE), m4, 27)
        assertEquals(IRect(5, 5, 21, 21), ib.iconRect)
    }

    @Test fun narrowButtonCutsTheLabelCleanly() {
        val b = laidOut(Button(0, "COMMANDER DAMAGE"), m4, 60)
        assertEquals("COMMAND", b.label.substring(0, b.labelChars))
        val w = laidOut(Button(0, "NEW GAME"), m4, 50)
        assertEquals("NEW", w.label.substring(0, w.labelChars)) // "NEW GA" → whole word
        val i = laidOut(Button(0, "RESET", Icon.RESET), m4, 27)
        assertEquals(0, i.labelChars) // no room for a glyph: icon only, centred
        assertEquals(5, i.iconRect.left)
        val stub = laidOut(Button(0, "RESET", Icon.RESET), m4, 39 + 6 * 1)
        assertEquals(0, stub.labelChars) // room for "RE" only: beside an icon that is a stub, so icon only
        assertEquals((45 - 16) / 2, stub.iconRect.left)
        assertEquals(3, laidOut(Button(0, "RESET", Icon.RESET), m4, 39 + 6 * 2).labelChars) // "RES" stays
        assertEquals(2, laidOut(Button(0, "OK", Icon.CHECK), m4, 39 + 6).labelChars) // a whole short label stays
    }

    @Test fun disabledButtonRegistersNoHit() {
        val b = laidOut(Button(0, "UNDO"), m4)
        val region = HitRegion()
        b.enabled = false
        b.collectHits(region, WidgetFrame().set(0, 0, 100, 100, 0))
        assertEquals(0, region.size)
        b.enabled = true
        b.collectHits(region, WidgetFrame().set(0, 0, 100, 100, 0))
        assertEquals(0, region.hitTest(5, 5))
    }

    @Test fun outlineAndFilledLook() {
        val f = WidgetFrame().set(0, 0, 61, 27, 0)
        val out = RasterCanvas(61, 27)
        WidgetPainter(out).draw(f, laidOut(Button(0, "NEW GAME"), m4))
        assertEquals(INK, out[0, 0]); assertEquals(INK, out[60, 26]) // 1-px outline
        assertEquals(PAPER, out[1, 1]); assertEquals(PAPER, out[3, 13])
        val dark = RasterCanvas(61, 27)
        WidgetPainter(dark).draw(f, laidOut(Button(0, "NEW GAME", style = ButtonStyle.FILLED), m4))
        assertEquals(INK, dark[1, 1]); assertEquals(INK, dark[3, 13])
        // Same glyphs, opposite polarity: the label region is the exact inverse.
        val lab = laidOut(Button(0, "NEW GAME"), m4).labelRect
        for (y in 1 until 26) for (x in lab.left until lab.right) assertEquals(1 - out[x, y], dark[x, y])
    }

    @Test fun disabledIsHatchedWithAnOpaqueLabel() {
        val b = laidOut(Button(0, "UNDO"), m4, 61).also { it.enabled = false }
        val r = RasterCanvas(61, 27)
        WidgetPainter(r).draw(WidgetFrame().set(0, 0, 61, 27, 0), b)
        assertEquals(INK, r[0, 0])
        assertTrue(r.inkIn(IRect(1, 1, 8, 26)) > 0) // hatch between outline and label
        // The paper rim (halo) around the label is clean: 1 px left of the label is paper on every row of the glyph box.
        val lx = b.labelRect.left - 1
        val top = (27 - 7) / 2
        for (y in top until top + 9) assertEquals(PAPER, r[lx, y])
    }

    @Test fun buttonRowHitsSpanTheRowAndAlignEnd() {
        for (s in WidgetTesting.scales) {
            val m = WidgetMetrics(s)
            val a = Button(0, "CANCEL"); val b = Button(1, "RESET", style = ButtonStyle.FILLED)
            val row = ButtonRow(a, b)
            val w = 300 * 4 / s
            row.layout(m, 0, 10, w, m.rowH)
            assertEquals(w - m.padX, b.bounds.right)
            assertEquals(b.bounds.left - m.gap, a.bounds.right)
            for (x in listOf(a, b)) {
                assertEquals(10, x.hit.top); assertEquals(10 + m.rowH, x.hit.bottom)
                assertEquals(m.rowH - 2 * m.inset, x.bounds.height)
                assertFingertip(m, x.hit, "row button")
            }
            val region = HitRegion()
            row.collectHits(region, WidgetFrame().set(0, 0, w, 100, 0))
            assertEquals(1, region.hitTest(b.bounds.left, 10))
            assertEquals(0, region.hitTest(a.bounds.right - 1, 10 + m.rowH - 1))
        }
    }

    @Test fun buttonRowFillAndOverflowShareTheRow() {
        val a = Button(0, "CANCEL"); val b = Button(1, "NEW GAME")
        val row = ButtonRow(a, b, align = ButtonRow.FILL)
        row.layout(m4, 0, 0, 200, 27)
        assertEquals(6, a.bounds.left); assertEquals(194, b.bounds.right)
        assertEquals(a.bounds.width, b.bounds.width)
        assertEquals(a.bounds.right + 4, b.bounds.left)
        // Too narrow for both at natural width: equal shares, labels cut.
        ButtonRow(a, b, align = TextLayout.START).layout(m4, 0, 0, 100, 27)
        assertEquals(a.bounds.width, b.bounds.width)
        assertTrue(b.labelChars < b.label.length)
    }

    @Test fun pressFallbackMatchesInversion() {
        val a = Button(0, "CANCEL"); val b = Button(1, "RESET", Icon.RESET, ButtonStyle.FILLED)
        val c = IconButton(2, Icon.CLOSE, ButtonStyle.PLAIN).also { it.backdrop = au.com.josephmark.dithertracker.dither.Role.TAB }
        val row = ButtonRow(a, b, c, align = TextLayout.START)
        row.layout(m4, 0, 0, 220, 27)
        val f = WidgetFrame().set(0, 0, 220, 27, 0)
        val region = HitRegion()
        row.collectHits(region, f)
        assertEquals(3, region.size)
        WidgetTesting.assertFallbackMatchesInvert(220, 27, region) { it.draw(f, row) }
    }

    @Test fun rotationIsExact() {
        for (s in intArrayOf(2, 4, 5)) {
            val m = WidgetMetrics(s)
            val row = ButtonRow(Button(0, "CANCEL"), Button(1, "RESET", Icon.RESET, ButtonStyle.FILLED), IconButton(2, Icon.CLOSE))
            val w = row.naturalWidth(m) + 7
            row.layout(m, 0, 0, w, m.rowH)
            WidgetTesting.assertRotationExact(w, m.rowH) { p, f -> p.draw(f, row) }
        }
    }
}
