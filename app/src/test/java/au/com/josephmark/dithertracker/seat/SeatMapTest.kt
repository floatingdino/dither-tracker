package au.com.josephmark.dithertracker.seat

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SeatMapTest {

    private fun nomad(s: Int = 4) = PlayLayout.compute(DitherGrid(1872, 1404, s))

    private fun rect(l: Int, t: Int, r: Int, b: Int) = IRect().set(l, t, r, b)

    /** Every pixel of [m] is either on a line or not; lines are exactly the cell frames, 1 px thick. */
    private fun assertCrisp(m: SeatMap, tag: String) {
        for (c in m.cells) {
            assertTrue("$tag cell inside", c.left >= 0 && c.top >= 0 && c.right <= m.width && c.bottom <= m.height)
            assertTrue("$tag cell has an interior $c", c.width >= 3 && c.height >= 3)
            // Interior pixels are never line pixels (no double or smeared borders).
            for (y in c.top + 1 until c.bottom - 1) for (x in c.left + 1 until c.right - 1) {
                assertFalse("$tag line inside cell at ($x,$y)\n${m.ascii(-1)}", m.isLine(x, y))
            }
            for (x in c.left until c.right) { assertTrue(tag, m.isLine(x, c.top)); assertTrue(tag, m.isLine(x, c.bottom - 1)) }
            for (y in c.top until c.bottom) { assertTrue(tag, m.isLine(c.left, y)); assertTrue(tag, m.isLine(c.right - 1, y)) }
        }
        // The outline: the lattice spans the whole box.
        assertEquals(tag, 0, m.cells.minOf { it.left }); assertEquals(tag, m.width, m.cells.maxOf { it.right })
        assertEquals(tag, 0, m.cells.minOf { it.top }); assertEquals(tag, m.height, m.cells.maxOf { it.bottom })
    }

    /** Panels of equal real width (height) get cells of equal width (height). */
    private fun assertProportional(panels: Array<IRect>, m: SeatMap, tag: String) {
        for (a in panels.indices) for (b in panels.indices) {
            if (abs(panels[a].width - panels[b].width) <= 2) assertEquals("$tag w $a/$b\n$m", m.cells[a].width, m.cells[b].width)
            if (abs(panels[a].height - panels[b].height) <= 2) assertEquals("$tag h $a/$b\n$m", m.cells[a].height, m.cells[b].height)
            // Wider panels never get narrower cells.
            if (panels[a].width > panels[b].width + 2) assertTrue("$tag $a wider than $b", m.cells[a].width >= m.cells[b].width)
        }
    }

    @Test fun nomadTwoByTwoAt16And24() {
        val l = nomad()
        val m16 = SeatMap.compute(l.panels, 16)
        assertEquals(15, m16.width); assertEquals(11, m16.height)
        assertEquals(
            """
            ###############
            ########......#
            ########......#
            ########......#
            ########......#
            ###############
            #......#......#
            #......#......#
            #......#......#
            #......#......#
            ###############
            """.trimIndent() + "\n",
            m16.ascii(0), // seat 0 (top-left) filled
        )
        val m24 = SeatMap.compute(l.panels, 24)
        assertEquals(23, m24.width); assertEquals(17, m24.height)
        for ((m, tag) in listOf(m16 to "16", m24 to "24")) {
            assertCrisp(m, tag)
            assertProportional(l.panels, m, tag)
            // Seat order follows the layout: 0 TL, 1 TR, 2 BR, 3 BL.
            val c = m.cells
            assertTrue(c[0].left == 0 && c[0].top == 0)
            assertTrue(c[1].right == m.width && c[1].top == 0)
            assertTrue(c[2].right == m.width && c[2].bottom == m.height)
            assertTrue(c[3].left == 0 && c[3].bottom == m.height)
            // Landscape 4:3, as the screen is.
            assertTrue("$tag aspect", abs(m.width.toDouble() / m.height - 468.0 / 351) < 0.12)
        }
    }

    @Test fun sameMiniatureAtEveryScale() {
        // The panels' proportions are the same at every S, so the glyph is too.
        val ref = SeatMap.compute(nomad(4).panels, 16).ascii(2)
        for (s in 2..6) assertEquals("S=$s", ref, SeatMap.compute(nomad(s).panels, 16).ascii(2))
    }

    @Test fun fillCoversExactlyTheSeatsCell() {
        val m = SeatMap.compute(nomad().panels, 16)
        for (seat in 0 until 4) for (y in 0 until m.height) for (x in 0 until m.width) {
            val c = m.cells[seat]
            val inside = x > c.left && x < c.right - 1 && y > c.top && y < c.bottom - 1
            assertEquals("seat $seat ($x,$y)", inside, m.isFill(seat, x, y))
        }
        // EMPTY / out of range: nothing filled.
        for (y in 0 until m.height) for (x in 0 until m.width) assertFalse(m.isFill(SeatGlyphs.EMPTY, x, y))
    }

    // ---- synthetic layouts (T11 will bring 2-6 players) -----------------------------------------

    private val synthetic: Map<String, Array<IRect>> = mapOf(
        // 2 players: face to face, top and bottom halves of a landscape screen.
        "2 stacked" to arrayOf(rect(0, 0, 468, 175), rect(0, 176, 468, 351)),
        // 2 players side by side.
        "2 side" to arrayOf(rect(0, 0, 233, 351), rect(234, 0, 468, 351)),
        // 3 players: one across the top, two below.
        "3 T" to arrayOf(rect(0, 0, 468, 175), rect(234, 176, 468, 351), rect(0, 176, 233, 351)),
        // 5 players: three across the top, two below (unequal widths).
        "5" to arrayOf(
            rect(0, 0, 155, 175), rect(156, 0, 311, 175), rect(312, 0, 468, 175),
            rect(234, 176, 468, 351), rect(0, 176, 233, 351),
        ),
        // 6 players: 3 × 2.
        "6" to arrayOf(
            rect(0, 0, 155, 175), rect(156, 0, 311, 175), rect(312, 0, 468, 175),
            rect(312, 176, 468, 351), rect(156, 176, 311, 351), rect(0, 176, 155, 351),
        ),
        // 6 players portrait: 2 × 3.
        "6 portrait" to arrayOf(
            rect(0, 0, 175, 155), rect(176, 0, 351, 155), rect(176, 156, 351, 311),
            rect(176, 312, 351, 468), rect(0, 312, 175, 468), rect(0, 156, 175, 311),
        ),
    )

    @Test fun generalisesToSyntheticLayouts() {
        for ((name, panels) in synthetic) for (size in intArrayOf(16, 24)) {
            val tag = "$name @$size"
            val m = SeatMap.compute(panels, size)
            assertTrue("$tag fits", m.width <= size && m.height <= size)
            // Equal cells beat filling the box (a 3-over-2 split wants (L - 1) divisible by 6), but not by much.
            assertTrue("$tag uses the box: $m", maxOf(m.width, m.height) * 4 >= size * 3)
            assertCrisp(m, tag)
            assertProportional(panels, m, tag)
            // Each seat's cell sits where its panel does (relative order on both axes).
            for (a in panels.indices) for (b in panels.indices) {
                if (panels[a].right <= panels[b].left) assertTrue("$tag $a left of $b", m.cells[a].right - 1 <= m.cells[b].left)
                if (panels[a].bottom <= panels[b].top) assertTrue("$tag $a above $b", m.cells[a].bottom - 1 <= m.cells[b].top)
            }
            // Aspect follows the screen's.
            val sw = panels.maxOf { it.right } - panels.minOf { it.left }
            val sh = panels.maxOf { it.bottom } - panels.minOf { it.top }
            assertTrue("$tag aspect ${m.width}x${m.height}", abs((m.width - 1).toDouble() / (m.height - 1) - sw.toDouble() / sh) < 0.25)
            // The filled cell is the seat's and no other.
            for (seat in panels.indices) {
                var filled = 0
                for (y in 0 until m.height) for (x in 0 until m.width) if (m.isFill(seat, x, y)) {
                    filled++
                    assertTrue(tag, m.cells[seat].contains(x, y))
                }
                assertTrue("$tag seat $seat filled", filled > 0)
            }
        }
    }

    @Test fun portraitLayoutsArePortrait() {
        val m = SeatMap.compute(synthetic.getValue("6 portrait"), 16)
        assertTrue("${m.width}x${m.height}", m.height > m.width)
        assertEquals(16, m.height)
    }
}
