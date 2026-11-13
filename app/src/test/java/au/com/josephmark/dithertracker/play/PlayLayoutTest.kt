package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.MINUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.NONE
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.PLUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.RESERVED
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayLayoutTest {

    private val screens = listOf(1872 to 1404, 2560 to 1600, 1280 to 800, 1404 to 1053)

    private fun layouts() = sequence {
        for ((w, h) in screens) for (s in 2..6) yield(PlayLayout.compute(DitherGrid(w, h, s)))
    }

    private fun inside(inner: IRect, outer: IRect) =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun overlaps(a: IRect, b: IRect) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun gridRect(l: PlayLayout) = IRect(0, 0, l.grid.width, l.grid.height)

    @Test fun nomadAtScale4() {
        val l = PlayLayout.compute(DitherGrid(1872, 1404, 4))
        assertEquals(468, l.grid.width); assertEquals(351, l.grid.height)
        assertEquals(233, l.panelW); assertEquals(175, l.panelH)
        assertEquals(0, l.originX); assertEquals(0, l.originY) // 1 spare column goes right
        assertEquals(1, l.kMax); assertEquals(1, l.pmK); assertTrue(l.pmVisible) // sticker, T27
        // Bottom-left seat (upright): − at x 13, slot 46..186 (−999 at k=1 is 140 wide), + at 200;
        // the ± cap band (17) is centred on the number's (53).
        assertEquals(IRect(13, 176 + 77, 33, 176 + 97), l.minusGlyph[3])
        assertEquals(IRect(46, 176 + 59, 186, 176 + 115), l.numberSlot[3])
        assertEquals(IRect(200, 176 + 77, 220, 176 + 97), l.plusGlyph[3])
        // Commander button (T30): 22×22 card 4 px in from the bottom-right corner, 30×30 hit.
        assertEquals(IRect(207, 176 + 149, 229, 176 + 171), l.cmdVis[3])
        assertEquals(IRect(203, 176 + 145, 233, 351), l.cmdHit[3])
        assertEquals(IRect(210, 176 + 152, 226, 176 + 168), l.cmdIcon[3])
        // Under-number row: between the number (bottom 115) and the button hit (top 145).
        assertEquals(IRect(8, 176 + 121, 225, 176 + 139), l.underRow[3])
        // Rotated seat: button in the screen's top-left corner of its panel.
        assertEquals(IRect(4, 4, 26, 26), l.cmdVis[0])
        assertEquals(IRect(72, 330, 161, 351), l.tab[3]) // 89×21 (T06: 55×12)
        assertEquals(IRect(58, 176 + 15, 174, 176 + 43), l.readoutSlot[3]) // 6 × num-m at k=2, above the number
        assertEquals(2, l.readoutK)
        // Top-left seat (180°): mirrored, tab on the screen top edge.
        assertEquals(IRect(200, 78, 220, 98), l.minusGlyph[0])
        assertEquals(IRect(13, 78, 33, 98), l.plusGlyph[0])
        assertEquals(IRect(72, 0, 161, 21), l.tab[0])
        assertEquals(IRect(59, 132, 175, 160), l.readoutSlot[0]) // "above" = towards the screen centre
        assertEquals(233, l.diamondCx); assertEquals(175, l.diamondCy)
        assertEquals(IRect(213, 155, 254, 196), l.diamond) // 41×41 (T06: 23×23)
        assertEquals(IRect(224, 166, 243, 185), l.menuBarsBacking)
    }

    @Test fun kMaxIsConsistentInPhysicalSizeOnTheNomad() {
        // Same physical number height (≈224 device px) whatever S, where the panel allows it.
        val ks = (2..6).map { PlayLayout.compute(DitherGrid(1872, 1404, it)).kMax }
        // Sticker is drawn natively at k = 1 (T27): k = 2 at S = 2 is the same physical size as k = 1
        // at S = 4; S = 5/6 cannot fit −999 with ± at all (not comfortable, never auto-chosen).
        assertEquals(listOf(2, 1, 1, 1, 1), ks)
        assertEquals(listOf(true, true, true, false, false), (2..6).map { PlayLayout.compute(DitherGrid(1872, 1404, it)).comfortable })
    }

    @Test fun kMaxIsTheLargestThatFits() {
        for (l in layouts()) {
            fun fits(k: Int) = PlayLayout.fits(l.font, l.pmFont, k, l.panelW, l.panelH, l.chrome.tabH)
            if (l.comfortable) assertTrue("$l", fits(l.kMax)) else assertEquals(1, l.kMax)
            assertFalse("$l", fits(l.kMax + 1))
        }
    }

    @Test fun panelsTileTheGridWithDividers() {
        for (l in layouts()) {
            val g = gridRect(l)
            for (p in l.panels) {
                assertTrue("$l", inside(p, g))
                assertEquals(l.panelW, p.width); assertEquals(l.panelH, p.height)
            }
            for (a in 0 until 4) for (b in a + 1 until 4) assertFalse(overlaps(l.panels[a], l.panels[b]))
            for (p in l.panels) { assertFalse(overlaps(p, l.dividerV)); assertFalse(overlaps(p, l.dividerH)) }
            assertTrue(inside(l.dividerV, g)); assertTrue(inside(l.dividerH, g))
            // leftover dither px are margin, at most 1 per side
            assertTrue(g.width - (2 * l.panelW + 1) in 0..1)
            assertTrue(g.height - (2 * l.panelH + 1) in 0..1)
            assertEquals(l.panels[0].right, l.dividerV.left)
            assertEquals(l.panels[1].left, l.dividerV.right)
            assertEquals(l.panels[0].bottom, l.dividerH.top)
            assertEquals(l.panels[3].top, l.dividerH.bottom)
        }
    }

    @Test fun panelContentNeverOverlaps() {
        val fp = IRect()
        val values = intArrayOf(0, 7, 40, -5, 100, -42, -999, 9999, 1234)
        for (l in layouts()) for (s in 0 until 4) {
            if (!l.comfortable) continue
            val p = l.panels[s]
            assertTrue(inside(l.minusGlyph[s], p)); assertTrue(inside(l.plusGlyph[s], p))
            assertTrue(inside(l.numberSlot[s], p)); assertTrue(inside(l.tab[s], p))
            assertFalse(overlaps(l.minusGlyph[s], l.numberSlot[s]))
            assertFalse(overlaps(l.plusGlyph[s], l.numberSlot[s]))
            assertFalse("$l", overlaps(l.numberSlot[s], l.tab[s]))
            assertFalse(overlaps(l.minusGlyph[s], l.tab[s]))
            assertFalse(overlaps(l.plusGlyph[s], l.diamond))
            assertFalse(overlaps(l.numberSlot[s], l.diamond))
            // spacing minimums hold in the player's frame
            val gapMinus = if (l.rotation[s] == 0) l.numberSlot[s].left - l.minusGlyph[s].right else l.minusGlyph[s].left - l.numberSlot[s].right
            assertTrue("$l gap $gapMinus", gapMinus >= PlayLayout.GAP_MIN)
            for (v in values) {
                val k = l.numberFootprint(s, v, fp)
                assertTrue("$l seat $s value $v $fp", inside(fp, l.numberSlot[s]))
                assertTrue(k in 1..l.kMax)
                // centred: equal space either side along the slot (±1 for odd leftovers)
                val left = fp.left - l.numberSlot[s].left
                val right = l.numberSlot[s].right - fp.right
                assertTrue("$l $v $left/$right", Math.abs(left - right) <= 1)
            }
        }
    }

    @Test fun wideNumbersDropKOnlyWhenNeeded() {
        val l = PlayLayout.compute(DitherGrid(1872, 1404, 4))
        for (n in 1..5) assertEquals(1, l.numberK(n)) // k = 1 is the smallest the face has
        val s2 = PlayLayout.compute(DitherGrid(1872, 1404, 2))
        for (n in 1..4) assertEquals(2, s2.numberK(n))
        assertEquals(1, s2.numberK(5)) // never happens with core limits, but stays inside the slot
    }

    @Test fun rotatedSeatsMirrorUprightOnes() {
        for (l in layouts()) {
            // Seat 0 (TL, 180°) vs seat 3 (BL, upright): same player-frame layout.
            fun mirror(r: IRect, p: IRect) = IRect(
                p.left + (p.right - r.right), p.top + (p.bottom - r.bottom),
                p.left + (p.right - r.left), p.top + (p.bottom - r.top),
            )
            fun rel(r: IRect, p: IRect) = IRect(r.left - p.left, r.top - p.top, r.right - p.left, r.bottom - p.top)
            val up = l.panels[3]; val rot = l.panels[0]
            assertEquals(rel(l.minusGlyph[3], up), rel(mirror(l.minusGlyph[0], rot), rot))
            assertEquals(rel(l.plusGlyph[3], up), rel(mirror(l.plusGlyph[0], rot), rot))
            assertEquals(rel(l.tab[3], up), rel(mirror(l.tab[0], rot), rot))
            assertEquals(rel(l.numberSlot[3], up), rel(mirror(l.numberSlot[0], rot), rot))
            // On screen, the rotated seat's − is right of its +.
            assertTrue(l.minusGlyph[0].left > l.plusGlyph[0].left)
            assertTrue(l.minusGlyph[3].left < l.plusGlyph[3].left)
            assertEquals(0, l.tab[0].top - l.panels[0].top)
            assertEquals(l.panels[3].bottom, l.tab[3].bottom)
        }
    }

    @Test fun hitTestZonesFromThePlayersPointOfView() {
        val l = PlayLayout.compute(DitherGrid(1872, 1404, 4))
        fun mid(p: IRect) = (p.top + p.bottom) / 2
        for (s in 0 until 4) {
            val p = l.panels[s]
            val screenLeft = l.hitTest(p.left + 20, mid(p))
            val screenRight = l.hitTest(p.right - 21, mid(p))
            if (s < 2) { // top row, rotated: screen-left is the player's right → +
                assertEquals(zone(s, PLUS), screenLeft)
                assertEquals(zone(s, MINUS), screenRight)
            } else {
                assertEquals(zone(s, MINUS), screenLeft)
                assertEquals(zone(s, PLUS), screenRight)
            }
            // the glyphs themselves sit in their own zone
            assertEquals(zone(s, MINUS), l.hitTest(l.minusGlyph[s].left + 1, l.minusGlyph[s].top + 1))
            assertEquals(zone(s, PLUS), l.hitTest(l.plusGlyph[s].left + 1, l.plusGlyph[s].top + 1))
            // tab is reserved
            assertEquals(RESERVED, l.hitTest(l.tab[s].left + 2, l.tab[s].top + 2))
        }
        assertEquals(RESERVED, l.hitTest(l.diamondCx, l.diamondCy))
        assertEquals(RESERVED, l.hitTest(l.diamondCx + 5, l.diamondCy - 5))
        assertEquals(NONE, l.hitTest(l.dividerV.left, 50))
        assertEquals(NONE, l.hitTest(50, l.dividerH.top))
        assertEquals(NONE, l.hitTest(467, 50)) // spare column
        assertEquals(NONE, l.hitTest(-1, 10))
        // Diamond corner region outside the shape belongs to a panel.
        assertEquals(zone(3, PLUS), l.hitTest(l.diamondCx - 20, l.diamondCy + 20))
    }

    @Test fun zonesCoverEachPanelExactlyAndMatchHitTest() {
        for (l in layouts()) for (s in 0 until 4) {
            val m = l.zones[zone(s, MINUS)]; val pl = l.zones[zone(s, PLUS)]
            assertFalse(overlaps(m, pl))
            assertEquals(l.panelW * l.panelH, m.width * m.height + pl.width * pl.height)
            assertTrue(inside(m, l.panels[s])); assertTrue(inside(pl, l.panels[s]))
            for (z in intArrayOf(zone(s, MINUS), zone(s, PLUS))) {
                val r = l.zones[z]
                val hit = l.hitTest((r.left + r.right) / 2, (r.top + r.bottom) / 2)
                assertTrue(hit == z || hit == RESERVED)
            }
        }
    }

    @Test fun everyAutoScaleLayoutIsComfortable() {
        // Since T27/T30 (sticker numerals at k = 1 + the under-number row) a 1280×800 screen has no
        // comfortable S at all — even S = 2 leaves a 199-px panel with no room for the row under the
        // number. There the auto scale bottoms out at MIN_SCALE; everywhere else it is comfortable.
        for ((w, h) in screens) for (dpi in intArrayOf(160, 213, 240, 300, 320, 400)) {
            val g = DitherGrid(w, h, PlayLayout.chooseScale(w, h, dpi))
            val anyComfortable = (DitherGrid.MIN_SCALE..6).any { PlayLayout.compute(DitherGrid(w, h, it)).comfortable }
            if (anyComfortable) assertTrue("$g", PlayLayout.compute(g).comfortable)
            else assertEquals("$g", DitherGrid.MIN_SCALE, g.scale)
        }
        assertFalse((DitherGrid.MIN_SCALE..6).any { PlayLayout.compute(DitherGrid(1280, 800, it)).comfortable })
        for ((w, h) in listOf(1872 to 1404, 2560 to 1600, 1404 to 1053)) {
            assertTrue("$w×$h", (DitherGrid.MIN_SCALE..6).any { PlayLayout.compute(DitherGrid(w, h, it)).comfortable })
        }
    }

    @Test fun nomadAutoScaleIs4() {
        assertEquals(4, PlayLayout.chooseScale(1872, 1404, 300))
        // Portrait (not used: the app is landscape-locked) is too narrow for the sticker at S = 4.
        assertEquals(3, PlayLayout.chooseScale(1404, 1872, 300))
    }

    @Test fun tinyScreensStillProduceALayout() {
        val l = PlayLayout.compute(DitherGrid(320, 240, 6))
        assertEquals(1, l.kMax)
        assertFalse(l.comfortable)
        assertTrue(l.panelW >= 1 && l.panelH >= 1)
    }

    @Test fun zoneHelpers() {
        assertEquals(-1, PlayLayout.zoneDelta(zone(2, MINUS)))
        assertEquals(1, PlayLayout.zoneDelta(zone(2, PLUS)))
        assertEquals(2, PlayLayout.zoneSeat(zone(2, PLUS)))
    }

    // ---- T07: readout and enlarged chrome --------------------------------------------------

    private val nomadScales = (2..6).map { PlayLayout.compute(DitherGrid(1872, 1404, it)) }

    @Test fun readoutNeverOverlapsAnythingAtS2to6() {
        val fp = IRect()
        val deltas = intArrayOf(1, -1, -7, 12, -20, 100, -999, 9999, -10998, 10998)
        for (l in nomadScales.filter { it.comfortable } + layouts().filter { it.comfortable }.toList()) for (s in 0 until 4) {
            val ro = l.readoutSlot[s]
            assertTrue("$l", inside(ro, l.panels[s]))
            for (other in listOf(l.numberSlot[s], l.minusGlyph[s], l.plusGlyph[s], l.tab[s], l.tabHit[s], l.diamond)) {
                assertFalse("$l seat $s $ro vs $other", overlaps(ro, other))
            }
            // V_MIN clear of the number and the far panel edge, in the player's frame
            val gapNum = if (l.rotation[s] == 0) l.numberSlot[s].top - ro.bottom else ro.top - l.numberSlot[s].bottom
            val gapEdge = if (l.rotation[s] == 0) ro.top - l.panels[s].top else l.panels[s].bottom - ro.bottom
            assertTrue("$l $gapNum", gapNum >= PlayLayout.V_MIN)
            assertTrue("$l $gapEdge", gapEdge >= PlayLayout.V_MIN)
            // above the number, in the player's frame
            if (l.rotation[s] == 0) assertTrue(ro.bottom <= l.numberSlot[s].top) else assertTrue(ro.top >= l.numberSlot[s].bottom)
            for (d in deltas) {
                val k = l.readoutFootprint(s, d, fp)
                assertTrue("$l $d $fp", inside(fp, ro))
                assertTrue(k in 1..l.readoutK)
                val a = fp.left - ro.left; val b = ro.right - fp.right
                assertTrue(Math.abs(a - b) <= 1)
            }
        }
    }

    @Test fun readoutIsHalfTheNumberOnTheNomad() {
        // num-m is half num-l's height; same k → half-size readout, same physical size at S=2..4.
        assertEquals(listOf(4, 2, 2), nomadScales.take(3).map { it.readoutK })
        for (l in nomadScales.take(3)) assertEquals(l.font.height(l.kMax), 2 * l.readoutFont.height(l.readoutK))
    }

    @Test fun rotatedReadoutMirrorsUpright() {
        for (l in layouts()) {
            val up = l.panels[3]; val rot = l.panels[0]
            val a = l.readoutSlot[3]; val b = l.readoutSlot[0]
            assertEquals(a.left - up.left, rot.right - b.right)
            assertEquals(a.top - up.top, rot.bottom - b.bottom)
            assertEquals(a.width, b.width); assertEquals(a.height, b.height)
        }
    }

    @Test fun chromeIsFingertipSizedAtEveryScale() {
        // ≥ 9 mm at 300 dpi ≈ 106 device px in the short dimension of each reserved hit area.
        for (l in nomadScales) {
            val S = l.grid.scale
            for (s in 0 until 4) {
                val h = minOf(l.tabHit[s].width, l.tabHit[s].height)
                assertTrue("$l tab hit $h", h * S >= 106)
                assertTrue(inside(l.tab[s], l.panels[s]))
                for (i in 0 until 3) assertTrue(inside(l.tabDots[s * 3 + i], l.tabDotsBacking[s]))
                assertTrue(inside(l.tabDotsBacking[s], l.tab[s]))
            }
            val across = 2 * (l.diamondHalf + l.chrome.diamondHitPad) + 1
            assertTrue("$l diamond $across", across * S >= 106)
            for (b in l.menuBars) assertTrue(inside(b, l.menuBarsBacking))
            // backing sits inside the diamond shape (every corner row within the row's half-width)
            val bk = l.menuBarsBacking
            for (y in bk.top until bk.bottom) {
                val hw = l.diamondRowHalf(y - l.diamondCy)
                assertTrue("$l row $y", hw >= 0 && bk.left >= l.diamondCx - hw && bk.right - 1 <= l.diamondCx + hw)
            }
            // bars are symmetric about the centre column
            assertEquals(l.diamondCx - l.menuBars[0].left, l.menuBars[2].right - 1 - l.diamondCx)
            assertEquals(l.diamondCx, (l.menuBars[1].left + l.menuBars[1].right - 1) / 2)
        }
    }

    @Test fun chromeAtS4IsTheBaseDesign() {
        val c = PlayLayout.Chrome.forScale(4)
        assertEquals(89, c.tabW); assertEquals(21, c.tabH); assertEquals(8, c.tabHitPad)
        assertEquals(24, c.diamondR); assertEquals(4, c.diamondCut)
        assertEquals(3, c.barW); assertEquals(15, c.barH); assertEquals(6, c.barPitch)
    }

    @Test fun tabAndDiamondClearNumberAndGlyphsAtS2to6() {
        for (l in nomadScales.filter { it.comfortable }) for (s in 0 until 4) {
            for (r in listOf(l.numberSlot[s], l.minusGlyph[s], l.plusGlyph[s])) {
                assertFalse("$l", overlaps(r, l.tabHit[s]))
                assertFalse("$l", overlaps(r, l.diamond))
            }
        }
    }

    // ---- T30/T32: commander button and the under-number row ---------------------------------

    @Test fun commanderButtonIsFingertipSizedAndClearOfEverything() {
        for (l in nomadScales.filter { it.comfortable } + layouts().filter { it.comfortable }.toList()) for (s in 0 until 4) {
            val S = l.grid.scale
            val hit = l.cmdHit[s]
            assertTrue("$l btn ${hit.width}", minOf(hit.width, hit.height) * S >= PlayLayout.MIN_TOUCH_DEVICE_PX)
            assertTrue(inside(hit, l.panels[s])); assertTrue(inside(l.cmdVis[s], hit)); assertTrue(inside(l.cmdIcon[s], l.cmdVis[s]))
            for (other in listOf(l.numberSlot[s], l.minusGlyph[s], l.plusGlyph[s], l.readoutSlot[s], l.tabHit[s], l.diamond, l.underRow[s])) {
                assertFalse("$l seat $s $hit vs $other", overlaps(hit, other))
            }
            assertFalse(l.inDiamondHit(hit.left, hit.top)); assertFalse(l.inDiamondHit(hit.right - 1, hit.bottom - 1))
            // In the player's bottom-right corner: touching the near edge and the player's right edge.
            if (l.rotation[s] == 0) {
                assertEquals(l.panels[s].bottom, hit.bottom); assertEquals(l.panels[s].right, hit.right)
            } else {
                assertEquals(l.panels[s].top, hit.top); assertEquals(l.panels[s].left, hit.left)
            }
        }
    }

    @Test fun underRowSitsBetweenNumberAndTab() {
        for (l in nomadScales.filter { it.comfortable } + layouts().filter { it.comfortable }.toList()) for (s in 0 until 4) {
            val row = l.underRow[s]
            if (row.width == 0) continue
            assertTrue(inside(row, l.panels[s]))
            for (other in listOf(l.numberSlot[s], l.minusGlyph[s], l.plusGlyph[s], l.readoutSlot[s], l.tabHit[s], l.cmdHit[s], l.diamond)) {
                assertFalse("$l seat $s $row vs $other", overlaps(row, other))
            }
            assertEquals(PlayLayout.ICON * l.tk + 2 * l.halo, row.height)
        }
        // The Nomad's comfortable scales all have the row.
        for (l in nomadScales.filter { it.comfortable }) assertTrue("$l", l.underH > 0)
    }

    @Test fun pmGlyphsShareTheNumbersCapCentre() {
        for (l in nomadScales.filter { it.comfortable }) {
            val k = l.kMax
            val numCapMid2 = 2 * (l.numberSlotY + l.font.capTop * k) + l.font.capHeight * k
            val pmY = l.pmCapAlignedY(l.numberSlotY, k)
            val pmCapMid2 = 2 * (pmY + l.pmFont.capTop * k) + l.pmFont.capHeight * k
            assertTrue("$l $numCapMid2 vs $pmCapMid2", Math.abs(numCapMid2 - pmCapMid2) <= 1)
        }
    }
}
