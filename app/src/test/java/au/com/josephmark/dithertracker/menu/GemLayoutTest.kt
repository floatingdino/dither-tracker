package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.CENTRE
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.E
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.FOLD
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.N
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.NE
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.NW
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.S
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.SE
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.SLOTS
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.SW
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.W
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.type.Fonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemLayoutTest {

    private fun gem(s: Int, w: Int = 1872, h: Int = 1404) = GemLayout(PlayLayout.compute(DitherGrid(w, h, s)))

    private val scales = 2..6

    @Test fun nomadNumbersAtS4() {
        val g = gem(4)
        assertEquals(1, g.k)
        assertEquals(29, g.r)
        assertEquals(27, g.rf)
        assertEquals(90, g.outerR)
        assertEquals(181, g.bounds.width)
        assertEquals(233, g.cx); assertEquals(175, g.cy)
        assertTrue(g.labelled)
        assertEquals(191, g.panel.width)
        assertEquals(93, g.panel.height)
    }

    @Test fun facetsSitOnTheLatticeWithStaircaseEdges() {
        for (s in scales) {
            val g = gem(s)
            // Centres: tips 2r out on the axes, diagonals (±r, ±r).
            assertEquals(g.cx - 2 * g.r, g.facetX[W]); assertEquals(g.cy, g.facetY[W])
            assertEquals(g.cx + 2 * g.r, g.facetX[E]); assertEquals(g.cy, g.facetY[E])
            assertEquals(g.cy - 2 * g.r, g.facetY[N]); assertEquals(g.cy + 2 * g.r, g.facetY[S])
            assertEquals(g.cx - g.r, g.facetX[SW]); assertEquals(g.cy + g.r, g.facetY[SW])
            assertEquals(g.cx + g.r, g.facetX[NE]); assertEquals(g.cy - g.r, g.facetY[NE])
            assertEquals(g.cx, g.facetX[CENTRE]); assertEquals(g.cy, g.facetY[CENTRE])
            // 45° staircase: each row one px narrower per side than the one before.
            for (dy in 1..g.rf) assertEquals(g.facetRowHalf(dy - 1) - 1, g.facetRowHalf(dy))
            assertEquals(-1, g.facetRowHalf(g.rf + 1))
            // Facets are disjoint (a gutter between neighbours) and inside the gem, inside the outline.
            for (a in 0 until SLOTS) {
                val ra = g.facetRect[a]
                for (y in ra.top until ra.bottom) for (x in ra.left until ra.right) {
                    if (!g.onFacet(a, x, y)) continue
                    assertTrue("S=$s facet $a at $x,$y outside the ring", Math.abs(x - g.cx) + Math.abs(y - g.cy) <= g.outerR - 2)
                    for (b in 0 until SLOTS) if (b != a) assertFalse("S=$s facets $a/$b overlap", g.onFacet(b, x, y))
                    assertEquals("S=$s facet $a's px hit-tests to it", a, g.slotAt(x, y))
                }
            }
        }
    }

    @Test fun hitCellsMeetTheFingertipRule() {
        for (s in scales) {
            val g = gem(s)
            val target = PlayLayout.compute(DitherGrid(1872, 1404, s)).target
            // A cell's inscribed axis-aligned square is r + 1 a side.
            assertTrue("S=$s r ${g.r} < target $target", g.r + 1 >= target)
            for (slot in 0 until SLOTS) {
                val x0 = g.facetX[slot] - g.r / 2
                val y0 = g.facetY[slot] - g.r / 2
                for (y in y0..y0 + g.r) for (x in x0..x0 + g.r) {
                    // Cells share their boundary: a boundary px may go to the neighbour; inside it's ours.
                    val inner = Math.abs(x - g.facetX[slot]) + Math.abs(y - g.facetY[slot]) < g.r
                    if (inner) assertEquals("S=$s slot $slot", slot, g.slotAt(x, y))
                }
            }
        }
    }

    @Test fun hitTestingIncludingTheTips() {
        val g = gem(4)
        assertEquals(CENTRE, g.slotAt(g.cx, g.cy))
        assertEquals(W, g.slotAt(g.cx - g.outerR, g.cy))
        assertEquals(E, g.slotAt(g.cx + g.outerR, g.cy))
        assertEquals(N, g.slotAt(g.cx, g.cy - g.outerR))
        assertEquals(S, g.slotAt(g.cx, g.cy + g.outerR))
        assertEquals(-1, g.slotAt(g.cx + g.outerR + 1, g.cy))
        assertEquals(-1, g.slotAt(g.cx - g.outerR, g.cy - 1))
        assertEquals(-1, g.slotAt(g.bounds.left, g.bounds.top))
        assertEquals(NW, g.slotAt(g.cx - 30, g.cy - 30))
        assertEquals(SE, g.slotAt(g.cx + 30, g.cy + 30))
        // The tips' rim (between the facet and the outline) belongs to the tip.
        assertEquals(W, g.slotAt(g.cx - 3 * g.r - 1, g.cy))
        assertEquals(N, g.slotAt(g.cx + 2, g.cy - 3 * g.r - 1))
    }

    @Test fun labelsFaceTheirNearestSeatedEdge() {
        for (s in scales) {
            val g = gem(s)
            for (slot in intArrayOf(SW, S, SE)) assertEquals(0, g.rotation[slot])
            for (slot in intArrayOf(NW, N, NE)) assertEquals(180, g.rotation[slot])
            assertEquals(FOLD, g.rotation[W]); assertEquals(FOLD, g.rotation[E])
        }
        // Assignment: tips hold the rotation-symmetric icons, undo/redo are labelled, 180° pairs.
        assertEquals(MenuItem.DICE, GemLayout.ITEMS[W]); assertEquals(MenuItem.CLEAN, GemLayout.ITEMS[E])
        assertEquals(MenuItem.UNDO, GemLayout.ITEMS[SW]); assertEquals(MenuItem.REDO, GemLayout.ITEMS[NE])
        assertEquals(MenuItem.LOG, GemLayout.ITEMS[NW]); assertEquals(MenuItem.SETUP, GemLayout.ITEMS[SE])
        assertEquals(MenuItem.NEW, GemLayout.ITEMS[N]); assertEquals(MenuItem.FIRST, GemLayout.ITEMS[S])
        assertEquals(MenuItem.entries.size, GemLayout.ITEMS.count { it != null })
    }

    @Test fun contentStaysInsideItsFacetAndN_IsTheImageOfS() {
        for (s in scales) {
            val g = gem(s)
            assertEquals("S=$s", s <= 4, g.labelled)
            for (slot in 0 until SLOTS) {
                val item = GemLayout.ITEMS[slot] ?: continue
                val ic = g.iconRect[slot]
                // Icon art (1-px margin) inside the facet diamond.
                val m = g.k
                for (corner in corners(ic.left + m, ic.top + m, ic.right - m - 1, ic.bottom - m - 1)) {
                    assertTrue("S=$s $item icon corner", g.onFacet(slot, corner[0], corner[1]))
                }
                val lr = g.labelRect[slot]
                if (!g.labelled || g.rotation[slot] == FOLD) { assertTrue(lr.isEmpty); continue }
                val capH = Fonts.UI.capHeight * g.k
                val capTop = if (g.rotation[slot] == 0) lr.top else lr.bottom - capH
                for (corner in corners(lr.left, capTop, lr.right - 1, capTop + capH - 1)) {
                    assertTrue("S=$s $item label corner", g.onFacet(slot, corner[0], corner[1]))
                }
                // Label and icon never overlap.
                assertFalse("S=$s $item", intersects(ic, capBand(lr, capH, g.rotation[slot])))
            }
            // The N facet is the S facet's 180° image about the crossing (same label width: NEW/FIRST differ,
            // so compare UNDO (SW) with REDO (NE): both 4 glyphs).
            val a = g.labelRect[SW]; val b = g.labelRect[NE]
            if (g.labelled) {
                assertEquals(2 * g.cx - (a.right - 1), b.left)
                assertEquals(2 * g.cy - (a.bottom - 1), b.top)
                assertEquals(2 * g.cy - (g.iconRect[SW].bottom - 1), g.iconRect[NE].top)
            }
        }
    }

    @Test fun gemAndConfirmStayInTheFreeSpaceAtEveryComfortableScale() {
        for (s in scales) {
            val l = PlayLayout.compute(DitherGrid(1872, 1404, s))
            if (!l.comfortable) continue
            val g = GemLayout(l)
            val keep = ArrayList<Pair<String, IRect>>()
            for (seat in 0 until 4) {
                keep += "number $seat" to l.numberSlot[seat]
                keep += "minus $seat" to l.minusGlyph[seat]
                keep += "plus $seat" to l.plusGlyph[seat]
                keep += "tab $seat" to l.tabHit[seat]
                keep += "readout $seat" to l.readoutSlot[seat] // the gem only: opening commits every readout
                keep += "under row $seat" to l.underRow[seat]
            }
            for ((name, r) in keep) {
                for (y in r.top until r.bottom) for (x in r.left until r.right) {
                    assertFalse("S=$s gem over $name at $x,$y", g.inGem(x, y))
                }
                if (!name.startsWith("readout")) assertFalse("S=$s confirm over $name", intersects(g.panelOuter, r))
            }
        }
    }

    @Test fun confirmPanelIsSymmetricAboutTheCrossing() {
        for (s in scales) {
            val g = gem(s)
            assertEquals(g.cx - g.panel.left, g.panel.right - 1 - g.cx)
            assertEquals(g.cy - g.panel.top, g.panel.bottom - 1 - g.cy)
            assertEquals(g.cy - g.fold.top, g.fold.bottom - 1 - g.cy)
            assertEquals(g.upperFace.height, g.lowerFace.height)
            assertEquals(g.cx - g.cancelBtn.left, g.confirmBtn.right - 1 - g.cx)
            assertTrue(g.cancelBtn.right <= g.foldRect.left && g.foldRect.right <= g.confirmBtn.left)
            val target = PlayLayout.compute(DitherGrid(1872, 1404, s)).target
            assertTrue("S=$s fold ${g.fold.height}", g.cancelHit.height >= target && g.confirmHit.height >= target)
            assertTrue(g.cancelHit.width >= target)
            assertTrue(g.onFoldDiamond(g.cx, g.cy)); assertFalse(g.onFoldDiamond(g.cx, g.fold.top))
        }
    }

    private fun capBand(lr: IRect, capH: Int, rot: Int): IRect =
        if (rot == 0) IRect(lr.left, lr.top, lr.right, lr.top + capH) else IRect(lr.left, lr.bottom - capH, lr.right, lr.bottom)

    private fun corners(x0: Int, y0: Int, x1: Int, y1: Int) = arrayOf(intArrayOf(x0, y0), intArrayOf(x1, y0), intArrayOf(x0, y1), intArrayOf(x1, y1))

    private fun intersects(a: IRect, b: IRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}
