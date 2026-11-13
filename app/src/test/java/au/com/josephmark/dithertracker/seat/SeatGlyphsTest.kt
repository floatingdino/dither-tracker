package au.com.josephmark.dithertracker.seat

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.widgets.RasterCanvas
import au.com.josephmark.dithertracker.widgets.WidgetFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatGlyphsTest {

    private val l = PlayLayout.compute(DitherGrid(1872, 1404, 4))
    private val glyphs = SeatGlyphs.forPanels(l.panels)
    private val screen = IRect().set(0, 0, l.grid.width, l.grid.height)
    private val rotations = intArrayOf(0, 90, 180, 270)

    /** Blits [g] with [rot] onto a raster and returns the footprint as ASCII (screen orientation). */
    private fun onScreen(g: SeatGlyph, rot: Int): String {
        val c = RasterCanvas(40, 40)
        c.blit(g, 0, 0, rot, invert = false, opaque = false)
        val fw = if (rot % 180 == 0) g.width else g.height
        val fh = if (rot % 180 == 0) g.height else g.width
        return buildString {
            for (y in 0 until fh) {
                for (x in 0 until fw) append(if (c[x, y] == RasterCanvas.INK) '#' else '.')
                append('\n')
            }
        }
    }

    @Test fun blittedWithTheCardsRotationTheGlyphLiesLikeTheTable() {
        for (size in intArrayOf(16, 24)) for (rot in rotations) for (seat in SeatGlyphs.EMPTY until 4) {
            val g = glyphs.glyph(seat, rot, size)
            assertEquals("seat $seat rot $rot @$size", glyphs.map(size).ascii(seat), onScreen(g, rot))
        }
    }

    /**
     * The point of the glyph: for a reader at whichever side the card faces, the filled cell is where
     * that player sits *relative to the reader*. The reader's frame comes from [WidgetFrame] alone (the
     * same maths the card uses to face a side); the glyph's fill centroid, in its own image (the reader's
     * frame), must lie in the same direction from its centre as the seat's panel lies from the screen's
     * centre in that frame.
     */
    @Test fun everySeatAppearsWhereItSitsForAReaderOnEverySide() {
        for (rot in rotations) {
            val reader = WidgetFrame().setScreen(screen, rot)
            val cx = reader.localX(screen.width / 2, screen.height / 2)
            val cy = reader.localY(screen.width / 2, screen.height / 2)
            for (seat in 0 until 4) {
                val p = l.panels[seat]
                val px = reader.localX((p.left + p.right) / 2, (p.top + p.bottom) / 2) - cx
                val py = reader.localY((p.left + p.right) / 2, (p.top + p.bottom) / 2) - cy
                val g = glyphs.glyph(seat, rot)
                val empty = glyphs.glyph(SeatGlyphs.EMPTY, rot)
                var sx = 0.0; var sy = 0.0; var n = 0
                for (y in 0 until g.height) for (x in 0 until g.width) if (g.isInk(x, y) && !empty.isInk(x, y)) {
                    sx += x; sy += y; n++
                }
                assertTrue(n > 0)
                val gx = sx / n - (g.width - 1) / 2.0
                val gy = sy / n - (g.height - 1) / 2.0
                val tag = "rot $rot seat $seat: panel at ($px,$py) in the reader's frame, fill at ($gx,$gy)\n${g.ascii()}"
                assertEquals(tag, Math.signum(px.toDouble()), Math.signum(gx), 0.0)
                assertEquals(tag, Math.signum(py.toDouble()), Math.signum(gy), 0.0)
            }
        }
    }

    @Test fun readerAtEachSideSeesTheirOwnRowNearest() {
        // Spelled out: the seats along the reader's edge are filled in the glyph's bottom half.
        for (rot in rotations) {
            val reader = WidgetFrame().setScreen(screen, rot)
            for (seat in 0 until 4) {
                val p = l.panels[seat]
                // Does this panel touch the edge the reader sits at (their local bottom)?
                val near = reader.localY((p.left + p.right) / 2, (p.top + p.bottom) / 2) > reader.height / 2
                val g = glyphs.glyph(seat, rot)
                val empty = glyphs.glyph(SeatGlyphs.EMPTY, rot)
                val bottomFill = (0 until g.height).any { y -> y > g.height / 2 && (0 until g.width).any { x -> g.isInk(x, y) && !empty.isInk(x, y) } }
                assertEquals("rot $rot seat $seat\n${g.ascii()}", near, bottomFill)
            }
        }
        // And by name: the card facing N (180) shows seats 0 and 1 (the top row) at the bottom.
        val north = glyphs.glyph(0, 180)
        assertTrue(north.ascii().lines()[north.height - 2].contains("######"))
    }

    @Test fun sidewaysReadersSeeAPortraitTable() {
        for (rot in rotations) {
            val g = glyphs.glyph(0, rot)
            if (rot % 180 == 0) assertEquals(15 to 11, g.width to g.height) else assertEquals(11 to 15, g.width to g.height)
            assertTrue(g.width <= SeatGlyphs.ICON && g.height <= SeatGlyphs.ICON)
        }
    }

    @Test fun emptyTableHasNoFilledCell() {
        val m = glyphs.map(16)
        for (rot in rotations) {
            val e = glyphs.glyph(SeatGlyphs.EMPTY, rot)
            var ink = 0
            for (y in 0 until e.height) for (x in 0 until e.width) if (e.isInk(x, y)) ink++
            var lines = 0
            for (y in 0 until m.height) for (x in 0 until m.width) if (m.isLine(x, y)) lines++
            assertEquals(lines, ink)
        }
        // Out-of-layout seats are empty too.
        assertSame(glyphs.glyph(SeatGlyphs.EMPTY, 90), glyphs.glyph(7, 90))
    }

    @Test fun glyphsScaleByWholePixels() {
        val g1 = glyphs.glyph(2, 90, 16, 1)
        val g2 = glyphs.glyph(2, 90, 16, 2)
        assertEquals(g1.width * 2, g2.width)
        for (y in 0 until g2.height) for (x in 0 until g2.width) assertEquals(g1.isInk(x / 2, y / 2), g2.isInk(x, y))
    }

    @Test fun cachedPerSeatRotationSizeAndK_andLookupsDoNotAllocate() {
        assertSame(glyphs.glyph(1, 90), glyphs.glyph(1, 90))
        assertNotSame(glyphs.glyph(1, 90), glyphs.glyph(1, 180))
        assertNotSame(glyphs.glyph(1, 90), glyphs.glyph(2, 90))
        assertNotSame(glyphs.glyph(1, 90, 16), glyphs.glyph(1, 90, 24))
        assertNotSame(glyphs.glyph(1, 90, 16, 1), glyphs.glyph(1, 90, 16, 2))
        glyphs.prewarm(16, 1); glyphs.prewarm(24, 1)
        val bean = java.lang.management.ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        val before = bean?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0
        var sink = 0
        repeat(1000) { i -> sink += glyphs.glyph(i % 5 - 1, (i % 4) * 90, if (i % 2 == 0) 16 else 24, 1).width }
        val after = bean?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0
        if (bean != null) assertTrue("allocated ${after - before} bytes over 1000 lookups", after - before < 1024)
        assertTrue(sink > 0)
    }

    @Test fun sameLayoutDetectsMovedPanels() {
        assertTrue(glyphs.sameLayout(l.panels))
        assertFalse(glyphs.sameLayout(PlayLayout.compute(DitherGrid(1872, 1404, 3)).panels))
    }

    @Test fun seatOfFollowsTheCurrentState() {
        val st = GameState.create(4)
        for (i in 0 until 4) assertEquals(i, SeatGlyphs.seatOf(st, st.players[i].id))
        assertEquals(SeatGlyphs.EMPTY, SeatGlyphs.seatOf(st, 99))
    }

    @Test fun generalisesToSixSeats() {
        val six = arrayOf(
            IRect().set(0, 0, 155, 175), IRect().set(156, 0, 311, 175), IRect().set(312, 0, 468, 175),
            IRect().set(312, 176, 468, 351), IRect().set(156, 176, 311, 351), IRect().set(0, 176, 155, 351),
        )
        val g = SeatGlyphs.forPanels(six)
        for (rot in rotations) for (seat in 0 until 6) {
            assertEquals(g.map(16).ascii(seat), onScreen(g.glyph(seat, rot), rot))
        }
    }
}
