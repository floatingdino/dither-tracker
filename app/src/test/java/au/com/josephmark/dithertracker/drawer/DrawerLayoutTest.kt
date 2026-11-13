package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.commander.CommanderZones
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerLayoutTest {

    private val screens = listOf(1872 to 1404, 2560 to 1600, 1280 to 800, 1404 to 1053)

    private fun layouts() = sequence {
        for ((w, h) in screens) for (s in 2..6) yield(DrawerLayout(PlayLayout.compute(DitherGrid(w, h, s))))
    }

    private val nomad = (2..6).map { DrawerLayout(PlayLayout.compute(DitherGrid(1872, 1404, it))) }

    private fun inside(inner: IRect, outer: IRect) =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun overlaps(a: IRect, b: IRect) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun allOpen() = DrawerState(4).apply { for (s in 0 until 4) { setOpen(s, true); setPaging(s, 100, 6) } }

    @Test fun dumpNomad() {
        for (d in nomad) println(d)
    }

    @Test fun nomadAtScale4() {
        val d = nomad[2]
        assertEquals(4, d.scale)
        assertEquals(1, d.tk)
        assertEquals(27, d.target)
        // No header button since T30: the tray has the whole body, rows are fingertip-high (27).
        assertEquals(4, d.cols); assertEquals(2, d.rows); assertEquals(8, d.perPage)
        assertEquals(27, d.rowH); assertEquals(21, d.barH)
        assertEquals(75, d.drawerH); assertEquals(100, d.upperH)
        assertEquals(1, d.numberK) // the life total keeps its closed size (sticker k = 1, T27)
        assertEquals(1, d.readoutK)
        assertTrue(d.comfortable)
        // Bottom-left seat (upright): drawer on the screen's bottom edge, number moved up.
        assertEquals(IRect(0, 176 + 100, 233, 351), d.drawer[3])
        assertEquals(IRect(0, 176 + 100, 233, 176 + 121), d.bar[3])
        // Top-left seat (180°): drawer on the screen's top edge.
        assertEquals(IRect(0, 0, 233, 75), d.drawer[0])
    }

    @Test fun everyNomadScaleIsComfortableAndKeepsTheClosedNumberSize() {
        // S = 5/6 cannot fit the sticker numerals at all (PlayLayout not comfortable, never auto-chosen).
        for (d in nomad.take(3)) {
            assertTrue("$d", d.comfortable)
            assertEquals("$d", d.play.kMax, d.numberK)
            assertTrue("$d", d.perPage >= 2)
        }
        // S = 2..4 (same physical size): two rows of four, eight cells a page
        for (d in nomad.take(3)) {
            assertEquals("$d", 2, d.rows); assertEquals("$d", 8, d.perPage)
        }
    }

    @Test fun everyLayoutWithRoomIsComfortable() {
        // Panels ≥ ~51 mm tall (the Nomad's is 59 mm). Smaller grids (e.g. 1280×800 forced to S = 4,
        // a 99-px panel) degrade: one row, smallest number, content may touch — but never crash.
        for (d in layouts()) {
            if (d.play.comfortable && d.play.panelH * d.scale >= 600) assertTrue("$d", d.comfortable)
            assertTrue("$d", d.perPage >= 1)
        }
    }

    @Test fun everyHitRectIsFingertipSized() {
        for (d in layouts()) {
            val S = d.scale
            fun ok(r: IRect, what: String) {
                if (r.isEmpty) return
                assertTrue("$d $what $r", minOf(r.width, r.height) * S >= DrawerLayout.MIN_TOUCH_DEVICE_PX)
            }
            if (!d.comfortable) continue
            for (s in 0 until 4) {
                for (i in 0 until 3) ok(d.barHit[s * 3 + i], "bar$i")
                for (slot in 0 until d.perPage) for (h in 0..1) ok(d.halfHit[(s * DrawerZones.MAX_SLOTS + slot) * 2 + h], "cell$slot.$h")
            }
        }
    }

    @Test fun nothingOverlapsAtS2to6() {
        for (d in layouts()) {
            if (!d.comfortable) continue
            val pl = d.play
            for (s in 0 until 4) {
                val panel = pl.panels[s]
                assertTrue(inside(d.drawer[s], panel))
                assertTrue(inside(d.upper[s], panel))
                assertFalse(overlaps(d.upper[s], d.drawer[s]))
                // life block in the upper area, clear of the bar's hit extension and the diamond
                for (r in listOf(d.numberSlot[s], d.readoutSlot[s], d.minusGlyph[s], d.plusGlyph[s])) {
                    assertTrue("$d $r", inside(r, d.upper[s]))
                    for (i in 0 until 3) assertFalse("$d $r bar", overlaps(r, d.barHit[s * 3 + i]))
                    assertFalse("$d $r diamond", overlaps(r, pl.diamond))
                }
                assertFalse(overlaps(d.readoutSlot[s], d.numberSlot[s]))
                assertFalse(overlaps(d.minusGlyph[s], d.numberSlot[s]))
                assertFalse(overlaps(d.plusGlyph[s], d.numberSlot[s]))
                // drawer parts
                assertTrue(inside(d.bar[s], d.drawer[s])); assertTrue(inside(d.body[s], d.drawer[s]))
                assertFalse(overlaps(d.bar[s], d.body[s]))
                assertTrue(inside(d.dotsBacking[s], d.barSeg[s * 3 + 1]))
                assertTrue("$d page icon", inside(d.pageIcon[s], d.barSeg[s * 3 + 2]))
                // The commander button: an ink card inside the bar's left segment, its icon inside the card.
                assertTrue("$d cmd", inside(d.cmdVis[s], d.barSeg[s * 3])); assertTrue("$d cmd icon", inside(d.cmdIcon[s], d.cmdVis[s]))
                val cells = (0 until d.perPage).map { d.cellVis[s * DrawerZones.MAX_SLOTS + it] }
                for ((a, ca) in cells.withIndex()) {
                    assertTrue(inside(ca, d.body[s]))
                    for (b in a + 1 until cells.size) assertFalse("$d cells $a $b", overlaps(ca, cells[b]))
                    val i = s * DrawerZones.MAX_SLOTS + a
                    for (r in listOf(d.trayIcon[i], d.trayValue[i])) {
                        assertTrue("$d content $r in $ca", inside(r, ca))
                    }
                    assertFalse(overlaps(d.trayIcon[i], d.trayValue[i]))
                }
            }
        }
    }

    @Test fun cellHitsTileTheBody() {
        for (d in nomad) for (s in 0 until 4) {
            var area = 0
            for (slot in 0 until d.perPage) for (h in 0..1) {
                val r = d.halfHit[(s * DrawerZones.MAX_SLOTS + slot) * 2 + h]
                area += r.width * r.height
            }
            assertEquals("$d", d.body[s].width * d.body[s].height, area)
        }
    }

    @Test fun rotatedSeatsMirrorUprightOnes() {
        for (d in nomad) {
            val pl = d.play
            fun rel(r: IRect, p: IRect) = IRect(r.left - p.left, r.top - p.top, r.right - p.left, r.bottom - p.top)
            fun mirror(r: IRect, p: IRect) = IRect(p.right - r.right, p.bottom - r.bottom, p.right - r.left, p.bottom - r.top)
            for ((up, rot) in listOf(3 to 0, 2 to 1)) {
                for (arr in listOf(d.drawer, d.bar, d.numberSlot, d.readoutSlot)) {
                    assertEquals(rel(arr[up], pl.panels[up]), mirror(arr[rot], pl.panels[rot]))
                }
            }
        }
    }

    @Test fun hitTestFromThePlayersPointOfView() {
        val d = nomad[2]
        val st = allOpen()
        fun mid(r: IRect) = intArrayOf((r.left + r.right) / 2, (r.top + r.bottom) / 2)
        for (s in 0 until 4) {
            // the player's left half of slot 0 is "−" whichever way up the seat is
            val left = d.halfHit[(s * DrawerZones.MAX_SLOTS) * 2]
            val c = mid(left)
            assertEquals(DrawerZones.cellZone(s, 0, DrawerZones.MINUS), d.hitTest(c[0], c[1], st))
            val right = d.halfHit[(s * DrawerZones.MAX_SLOTS) * 2 + 1]
            val c2 = mid(right)
            assertEquals(DrawerZones.cellZone(s, 0, DrawerZones.PLUS), d.hitTest(c2[0], c2[1], st))
            if (d.play.rotation[s] == 180) assertTrue(left.left > right.left) else assertTrue(left.left < right.left)
            val b = mid(d.barSeg[s * 3 + 1])
            assertEquals(DrawerZones.zone(s, DrawerZones.TAB), d.hitTest(b[0], b[1], st))
            // upper area still drives life
            val u = mid(d.lifeZones[s * 2 + PlayLayout.PLUS])
            assertEquals(PlayLayout.zone(s, PlayLayout.PLUS), d.hitTest(u[0], u[1], st))
        }
        // The bar's left segment is the commander button (T30), on every seat, whichever way up.
        for (s in 0 until 4) {
            val b = mid(d.cmdVis[s])
            assertEquals(au.com.josephmark.dithertracker.commander.CommanderZones.zone(s, au.com.josephmark.dithertracker.commander.CommanderZones.BUTTON), d.hitTest(b[0], b[1], st))
        }
        // page 0 of 2: the right segment pages on
        val n = mid(d.barSeg[3 * 3 + 2])
        assertEquals(DrawerZones.zone(3, DrawerZones.NEXT), d.hitTest(n[0], n[1], st))
        // the bar's hit extends upward by the tab's hit pad
        assertEquals(DrawerZones.zone(3, DrawerZones.NEXT), d.hitTest(n[0], d.bar[3].top - d.barHitPad, st))
        // on the last page it goes back to the first; with one page it closes
        st.setPaging(3, d.perPage + 1, d.perPage); st.nextPage(3)
        assertFalse(st.hasNext(3))
        assertEquals(DrawerZones.zone(3, DrawerZones.PREV), d.hitTest(n[0], n[1], st))
        st.setPaging(3, 3, d.perPage)
        assertEquals(DrawerZones.zone(3, DrawerZones.TAB), d.hitTest(n[0], n[1], st))
        // diamond still reserved
        assertEquals(PlayLayout.RESERVED, d.hitTest(d.play.diamondCx, d.play.diamondCy, st))
    }

    @Test fun emptyCellsAndClosedDrawers() {
        val d = nomad[2]
        val st = DrawerState(4)
        // closed: tab → TAB zone, the rest of the panel is life
        val t = d.play.tab[2]
        assertEquals(DrawerZones.zone(2, DrawerZones.TAB), d.hitTest(t.left + 2, t.top + 2, st))
        val cell = d.halfHit[(2 * DrawerZones.MAX_SLOTS + 5) * 2]
        assertTrue(DrawerZones.isLife(d.hitTest(cell.left + 1, cell.top + 1, st)))
        // open with only 2 items: slot 5 is empty → nothing
        st.setOpen(2, true); st.setPaging(2, 2, d.perPage)
        assertEquals(PlayLayout.NONE, d.hitTest(cell.left + 1, cell.top + 1, st))
    }

    @Test fun feedbackRects() {
        val d = nomad[2]
        val st = DrawerState(4)
        val out = IRect()
        assertTrue(d.feedbackRect(DrawerZones.zone(1, DrawerZones.TAB), st, out))
        assertEquals(d.play.tab[1], out)
        assertTrue(d.feedbackRect(PlayLayout.zone(1, PlayLayout.MINUS), st, out))
        assertEquals(d.play.zones[PlayLayout.zone(1, PlayLayout.MINUS)], out)
        assertFalse(d.feedbackRect(DrawerZones.cellZone(1, 0, DrawerZones.PLUS), st, out)) // closed
        st.setOpen(1, true)
        assertTrue(d.feedbackRect(PlayLayout.zone(1, PlayLayout.MINUS), st, out))
        assertEquals(d.lifeZones[PlayLayout.zone(1, PlayLayout.MINUS)], out)
        assertTrue(d.feedbackRect(DrawerZones.cellZone(1, 3, DrawerZones.PLUS), st, out))
        assertEquals(d.halfVis[(1 * DrawerZones.MAX_SLOTS + 3) * 2 + 1], out)
    }

    @Test fun zoneIdsRoundTrip() {
        for (s in 0 until 4) for (local in 0 until DrawerZones.PER_SEAT) {
            val z = DrawerZones.zone(s, local)
            assertTrue(DrawerZones.isDrawer(z)); assertFalse(DrawerZones.isLife(z))
            assertEquals(s, DrawerZones.seatOf(z)); assertEquals(local, DrawerZones.localOf(z))
        }
        val z = DrawerZones.cellZone(2, 5, DrawerZones.PLUS)
        assertEquals(5, DrawerZones.slotOf(DrawerZones.localOf(z))); assertEquals(1, DrawerZones.halfOf(DrawerZones.localOf(z)))
        for (z2 in 0 until DrawerZones.LIFE) assertEquals(PlayLayout.zoneSeat(z2), DrawerZones.seatOf(z2))
    }
}
