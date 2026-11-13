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
                    // Inside the card's frame sideways (value boxes are card-tall: the digits centre in them;
                    // the icon footprint's empty outer columns may touch the frame).
                    val inner = IRect(ca.left + 1, ca.top, ca.right - 1, ca.bottom)
                    val stepper = listOf(d.trayMinus[i], d.trayPlus[i], d.iconInk(d.trayIcon[i], IRect()), d.trayValue[i])
                    val wide = listOf(d.iconInk(d.trayIconWide[i], IRect()), d.trayValueWide[i])
                    for (set in listOf(stepper, wide)) {
                        for (r in set) assertTrue("$d content $r in $ca", inside(r, inner))
                        for (x in set.indices) for (y in x + 1 until set.size) assertFalse("$d ${set[x]} ${set[y]}", overlaps(set[x], set[y]))
                    }
                    assertTrue(inside(d.trayIcon[i], ca)); assertTrue(inside(d.trayIconWide[i], ca))
                    // A zero cell's halos (icon footprint and + grown by `halo`) stay in the card.
                    for (r in listOf(d.trayIcon[i], d.trayPlus[i])) {
                        assertTrue("$d halo $r", inside(IRect(r.left - d.halo, r.top - d.halo, r.right + d.halo, r.bottom + d.halo), ca))
                    }
                }
            }
        }
    }

    @Test fun theOpenReadoutKeepsItsClosedSizeAndGap() {
        // T37: 1× beside the number whether the drawer is open or not, READOUT_GAP·k above it.
        for (d in nomad.take(3)) {
            assertEquals("$d", d.play.readoutK, d.readoutK)
            assertEquals("$d", PlayLayout.READOUT_GAP * d.numberK, d.numberTop - d.readoutTop - d.play.readoutFont.height(d.readoutK))
        }
    }

    @Test fun everyCounterValueFitsItsCellWithoutClipping() {
        // T43 at S = 4: a 56-px card holds `− icon 99 +` exactly (22-px value box, 2 digits at the
        // face's tracking); 3 digits drop the glyphs for a 37-px box and close to tracking 1 (T37).
        val d4 = nomad[2]
        val i0 = 3 * DrawerZones.MAX_SLOTS
        assertEquals(22, d4.trayValue[i0].width)
        assertEquals(37, d4.trayValueWide[i0].width)
        assertTrue(d4.showsSteps(i0, 7)); assertTrue(d4.showsSteps(i0, 42)); assertTrue(d4.showsSteps(i0, 99))
        assertFalse(d4.showsSteps(i0, 100)); assertFalse(d4.showsSteps(i0, 999))
        assertEquals(2, d4.valueTracking(42, d4.valueRect(i0, 42).width))
        assertEquals(2, d4.valueTracking(100, d4.valueRect(i0, 100).width))
        assertEquals(2, d4.valueTracking(999, d4.valueRect(i0, 999).width)) // 34 ≤ 37: no closing up at S = 4 any more
        for (d in layouts()) {
            if (!d.comfortable) continue
            for (s in 0 until 4) for (slot in 0 until d.perPage) {
                val i = s * DrawerZones.MAX_SLOTS + slot
                for (value in intArrayOf(1, 9, 10, 98, 99, 100, 998, 999)) {
                    val v = d.valueRect(i, value)
                    val t = d.valueTracking(value, v.width)
                    assertTrue("$d $value", t >= DrawerLayout.TIGHT_TRACKING)
                    assertTrue("$d $value", d.numFont.measureInt(value, d.tk, t) <= v.width)
                    // 1- and 2-digit values always get the stepper and the face's own tracking.
                    if (value < 100) { assertTrue("$d $value", d.showsSteps(i, value)); assertEquals("$d $value", d.numFont.tracking, t) }
                    assertTrue(d.numFont.height(d.tk) <= v.height)
                    assertFalse("$d", overlaps(d.iconInk(d.iconRect(i, value), IRect()), v))
                }
                // zero cells keep the stepper's icon position (the icon does not jump on the first tap)
                assertEquals(d.trayIcon[i], d.iconRect(i, 0))
            }
        }
    }

    @Test fun nomadCellMetricsAtScale4() {
        // Player frame of the bottom-left seat's first cell: hit 58 × 27 from (0, 297); card inset 1.
        val d = nomad[2]
        val i = 3 * DrawerZones.MAX_SLOTS
        assertEquals(IRect(0, 297, 58, 324), d.cellHit[i])
        assertEquals(IRect(1, 298, 57, 323), d.cellVis[i]) // 56 × 25
        assertEquals(6, d.pm); assertEquals(2, d.pmStroke)
        assertEquals(IRect(3, 309, 9, 311), d.trayMinus[i]) // 6 × 2, frame + 1 px gap to its left
        assertEquals(IRect(49, 307, 55, 313), d.trayPlus[i]) // 6 × 6, 1 px gap + frame to its right
        assertEquals(IRect(9, 302, 25, 318), d.trayIcon[i]) // ink 10..23, 1 px after the −
        assertEquals(IRect(25, 298, 47, 323), d.trayValue[i]) // 22 wide, 2 px before the +
        assertEquals(IRect(2, 302, 18, 318), d.trayIconWide[i])
        assertEquals(IRect(18, 298, 55, 323), d.trayValueWide[i])
        assertEquals(56, d.stepperW); assertEquals(51, d.wideW)
        // − and + share a centre line with each other and the value box.
        assertEquals(d.trayMinus[i].top + d.trayMinus[i].bottom, d.trayPlus[i].top + d.trayPlus[i].bottom)
        assertEquals(d.trayPlus[i].top + d.trayPlus[i].bottom, d.trayValue[i].top + d.trayValue[i].bottom - 1)
    }

    @Test fun glyphsSitInTheirHalvesWhicheverWayUpTheSeatIs() {
        for (d in layouts()) {
            if (!d.comfortable) continue
            for (s in 0 until 4) for (slot in 0 until d.perPage) {
                val i = s * DrawerZones.MAX_SLOTS + slot
                val minusHalf = d.halfVis[i * 2 + DrawerZones.MINUS]
                val plusHalf = d.halfVis[i * 2 + DrawerZones.PLUS]
                assertTrue("$d $s $slot", inside(d.trayMinus[i], minusHalf))
                assertTrue("$d $s $slot", inside(d.trayPlus[i], plusHalf))
                assertTrue("$d $s $slot", inside(d.trayMinus[i], d.halfHit[i * 2 + DrawerZones.MINUS]))
                assertTrue("$d $s $slot", inside(d.trayPlus[i], d.halfHit[i * 2 + DrawerZones.PLUS]))
                // Strokes never under 2 dither px, and glyphs at least 6 (≈ 2 mm at S = 4).
                assertTrue(d.pmStroke >= 2); assertEquals(d.pmStroke, d.trayMinus[i].height); assertEquals(d.pm, d.trayPlus[i].width)
                // The player's left is the screen's right on a turned seat.
                if (d.play.rotation[s] == 180) assertTrue(d.trayMinus[i].left > d.trayPlus[i].left)
                else assertTrue(d.trayMinus[i].left < d.trayPlus[i].left)
            }
        }
    }

    @Test fun trayIconsLeaveTheirOuterColumnsEmpty() {
        // DrawerLayout.ICON_MARGIN: the stepper packs the − and the value against the icon's ink.
        for (icon in DrawerItems.TRAY_ICONS) for (y in 0 until icon.height) for (m in 0 until DrawerLayout.ICON_MARGIN) {
            assertFalse("$icon", icon.isInk(m, y)); assertFalse("$icon", icon.isInk(icon.width - 1 - m, y))
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
                for (slot in 0 until d.perPage) {
                    val a = up * DrawerZones.MAX_SLOTS + slot
                    val b = rot * DrawerZones.MAX_SLOTS + slot
                    for (arr in listOf(d.trayMinus, d.trayPlus, d.trayIcon, d.trayValue, d.trayIconWide, d.trayValueWide)) {
                        assertEquals(rel(arr[a], pl.panels[up]), mirror(arr[b], pl.panels[rot]))
                    }
                }
            }
        }
    }

    // Hit-testing and feedback rects are the drawer components' now: see play.PlayScreenTest.

    @Test fun localIdsRoundTrip() {
        val seen = HashSet<Int>()
        for (slot in 0 until DrawerZones.MAX_SLOTS) for (half in 0..1) {
            val local = DrawerZones.cell(slot, half)
            assertTrue(DrawerZones.isCell(local)); assertTrue(seen.add(local))
            assertEquals(slot, DrawerZones.slotOf(local)); assertEquals(half, DrawerZones.halfOf(local))
            assertTrue(local < DrawerZones.PER_SEAT)
        }
        for (local in intArrayOf(DrawerZones.TAB, DrawerZones.PREV, DrawerZones.NEXT, DrawerZones.BUTTON)) {
            assertFalse(DrawerZones.isCell(local)); assertTrue(seen.add(local))
        }
        assertEquals(DrawerZones.PER_SEAT, seen.size)
        // The commander button is outside the range a page turn / open / close cancels.
        assertTrue(DrawerZones.BUTTON >= DrawerZones.CELL_END)
    }
}
