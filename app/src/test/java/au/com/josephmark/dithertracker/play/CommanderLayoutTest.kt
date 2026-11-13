package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerState
import au.com.josephmark.dithertracker.drawer.DrawerZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommanderLayoutTest {

    private val screens = listOf(1872 to 1404, 2560 to 1600, 1280 to 800, 1404 to 1053)
    private val nomad = (2..6).map { CommanderLayout(PlayLayout.compute(DitherGrid(1872, 1404, it))) }
    private val all = sequence {
        for ((w, h) in screens) for (s in 2..6) yield(CommanderLayout(PlayLayout.compute(DitherGrid(w, h, s))))
    }.filter { it.play.comfortable }.toList()

    private fun inside(inner: IRect, outer: IRect) =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun overlaps(a: IRect, b: IRect) =
        a.width > 0 && b.width > 0 && a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun mid(r: IRect) = intArrayOf((r.left + r.right) / 2, (r.top + r.bottom) / 2)

    private val players: List<Player> = GameState.create(4).players
    private val partnered: List<Player> = players.mapIndexed { i, p -> if (i == 1 || i == 0) p.copy(partner = true) else p }
    private val closed = DrawerState(4)

    @Test fun theSweepsReallyCoverTheNomadScales() {
        // The S = 2..6 sweeps below only see comfortable layouts: make sure that is not an empty set.
        assertTrue("${all.size}", all.size >= 7) // 20 screen/S combinations, 7 comfortable
        for (s in 2..4) assertTrue("S=$s", nomad[s - 2].play.comfortable && nomad[s - 2].play.underH > 0)
        for (s in 2..4) assertTrue("S=$s", all.any { it.play === nomad[s - 2].play || it.play.toString() == nomad[s - 2].play.toString() })
        for (c in all) assertTrue("$c", c.labelChars >= 8 && c.partnerLabelChars >= 1)
    }

    @Test fun nomadAtScale4() {
        val c = nomad[2]
        val l = c.play
        assertEquals(1, c.tk); assertEquals(16, c.iconSize)
        assertTrue(c.partnerPmVisible)
        // Upright seat 3: partner numbers centred in each half, ± with 2 px clear.
        assertEquals(IRect(24, 176 + 59, 92, 176 + 115), c.partnerSlot[3 * 2])
        assertEquals(IRect(140, 176 + 59, 208, 176 + 115), c.partnerSlot[3 * 2 + 1])
        assertEquals(IRect(2, 176 + 77, 22, 176 + 97), c.partnerMinus[3 * 2])
        assertEquals(IRect(94, 176 + 77, 114, 176 + 97), c.partnerPlus[3 * 2])
        assertEquals(l.underRow[3].top + 1, c.partnerIcon[3 * 2].top)
        // "TO " + 8 name chars ("PLAYER 1") fits both label layouts.
        assertTrue("${c.labelChars}", c.labelChars >= 8)
        assertTrue("${c.partnerLabelChars}", c.partnerLabelChars >= 8)
        assertEquals(4, c.quipKMax)
        assertEquals(2, c.quipK("Here it comes".length)) // k = 3 would be 231 wide > 233 − 2·8
        assertEquals(4, c.quipK("Uh oh".length))
        assertEquals(2, c.quipK("Should've ducked".length))
    }

    @Test fun everyQuipFitsTheTileAtS2to6() {
        val lines = au.com.josephmark.dithertracker.core.flavour.Flavour.quippy.lines(
            au.com.josephmark.dithertracker.core.flavour.QuipKind.COMMANDER_TARGET,
        ) + "Target"
        val fp = IRect()
        for (c in all) for (s in 0 until 4) for (q in lines) {
            val k = c.quipFootprint(s, q.length, fp)
            assertTrue("$c $q", k >= 1)
            assertTrue("$c '$q' $fp", inside(fp, c.content[s]))
            for (o in listOf(c.play.readoutSlot[s], c.play.underRow[s], c.play.cmdHit[s], c.play.tabHit[s], c.play.diamond)) {
                assertFalse("$c '$q' $fp vs $o", overlaps(fp, o))
            }
        }
    }

    @Test fun modeContentNeverCollidesAtS2to6() {
        val icon = IRect(); val text = IRect(); val num = IRect(); val band = IRect()
        for (c in all) {
            val l = c.play
            for (s in 0 until 4) {
                val p = l.panels[s]
                // Partner numbers and their ± stay in their half and clear of each other and the chrome.
                val parts = ArrayList<IRect>()
                for (i in 0..1) {
                    parts += c.partnerSlot[s * 2 + i]
                    if (c.partnerPmVisible) { parts += c.partnerMinus[s * 2 + i]; parts += c.partnerPlus[s * 2 + i] }
                }
                for (a in parts) {
                    assertTrue("$c $a", inside(a, p))
                    for (o in listOf(l.readoutSlot[s], l.underRow[s], l.cmdHit[s], l.tabHit[s], l.diamond)) assertFalse("$c $a vs $o", overlaps(a, o))
                }
                for (i in parts.indices) for (j in i + 1 until parts.size) assertFalse("$c ${parts[i]} ${parts[j]}", overlaps(parts[i], parts[j]))
                if (l.underH == 0) continue
                // Labels sit inside the under row.
                c.labelFootprint(s, CommanderLayout.TO.length + c.labelChars, icon, text)
                assertTrue("$c $icon", inside(icon, l.underRow[s])); assertTrue("$c $text", inside(text, l.underRow[s]))
                assertFalse(overlaps(icon, text))
                c.partnerLabelFootprint(s, CommanderLayout.TO.length + c.partnerLabelChars, text)
                assertTrue(inside(text, l.underRow[s]))
                for (i in 0..1) {
                    assertTrue(inside(c.partnerIcon[s * 2 + i], l.underRow[s]))
                    assertFalse("$c label vs icon", overlaps(text, c.partnerIcon[s * 2 + i]))
                }
                for (life in intArrayOf(40, 0, -999, 9999)) {
                    c.lifeFootprint(s, life, icon, num)
                    c.bandRect(s, band)
                    assertTrue(inside(band, l.underRow[s]))
                    if (life >= -999 && life <= 999) { assertTrue("$c $life", inside(icon, band)); assertTrue("$c $life", inside(num, band)) }
                }
            }
        }
    }

    @Test fun hitTestRotatedSeatsAndPartnerQuarters() {
        val c = nomad[2]
        val l = c.play
        // Outside the mode: only the buttons are the mode's.
        for (s in 0 until 4) {
            val b = mid(l.cmdHit[s])
            assertEquals(CommanderZones.zone(s, CommanderZones.BUTTON), c.hitTest(b[0], b[1], CommanderMode.NONE, players, closed))
            val n = mid(l.numberSlot[s])
            assertEquals(CommanderLayout.DELEGATE, c.hitTest(n[0], n[1], CommanderMode.NONE, players, closed))
        }
        assertEquals(CommanderLayout.DELEGATE, c.hitTest(l.diamondCx, l.diamondCy, CommanderMode.NONE, players, closed))
        // A drawer open on seat 2 hides its button.
        val open = DrawerState(4).apply { setOpen(2, true) }
        val b2 = mid(l.cmdHit[2])
        assertEquals(CommanderLayout.DELEGATE, c.hitTest(b2[0], b2[1], CommanderMode.NONE, players, open))

        // Target 3 (upright, BL).
        val t = 3
        val n3 = mid(l.numberSlot[3])
        assertEquals(CommanderZones.zone(3, CommanderZones.TARGET), c.hitTest(n3[0], n3[1], t, players, closed))
        assertEquals(CommanderZones.DIAMOND, c.hitTest(l.diamondCx, l.diamondCy, t, players, closed))
        val tab = mid(l.tab[2])
        assertEquals(PlayLayout.NONE, c.hitTest(tab[0], tab[1], t, players, closed)) // tabs inert
        // Upright seat 2: screen-left half is −.
        val p2 = l.panels[2]
        assertEquals(CommanderZones.zone(2, CommanderZones.A_MINUS), c.hitTest(p2.left + 10, n3[1], t, players, closed))
        assertEquals(CommanderZones.zone(2, CommanderZones.A_PLUS), c.hitTest(p2.right - 10, n3[1], t, players, closed))
        // Rotated seat 0: screen-left is the player's right → +.
        val p0 = l.panels[0]
        val y0 = mid(l.numberSlot[0])[1]
        assertEquals(CommanderZones.zone(0, CommanderZones.A_PLUS), c.hitTest(p0.left + 10, y0, t, players, closed))
        assertEquals(CommanderZones.zone(0, CommanderZones.A_MINUS), c.hitTest(p0.right - 10, y0, t, players, closed))
        // Partners: quarters, in the player's frame (seat 0 rotated, seat 1 rotated).
        fun q(s: Int, i: Int) = mid(c.quarter[s * 4 + i])
        val want = intArrayOf(CommanderZones.A_MINUS, CommanderZones.A_PLUS, CommanderZones.B_MINUS, CommanderZones.B_PLUS)
        for (s in intArrayOf(0, 1)) for (i in 0 until 4) {
            val m = q(s, i)
            assertEquals("seat $s quarter $i", CommanderZones.zone(s, want[i]), c.hitTest(m[0], m[1], t, partnered, closed))
        }
        // The player-frame first quarter of a rotated seat is on the screen's right.
        assertTrue(c.quarter[0].left > c.quarter[3].left)
        // The button still wins inside the mode.
        val b0 = mid(l.cmdHit[0])
        assertEquals(CommanderZones.zone(0, CommanderZones.BUTTON), c.hitTest(b0[0], b0[1], t, partnered, closed))
    }

    @Test fun feedbackRects() {
        val c = nomad[2]
        val l = c.play
        val out = IRect()
        assertTrue(c.feedbackRect(CommanderZones.zone(1, CommanderZones.BUTTON), players, out)); assertEquals(l.cmdVis[1], out)
        assertTrue(c.feedbackRect(CommanderZones.zone(1, CommanderZones.TARGET), players, out)); assertEquals(l.panels[1], out)
        assertTrue(c.feedbackRect(CommanderZones.zone(1, CommanderZones.A_PLUS), players, out))
        assertEquals(l.zones[PlayLayout.zone(1, PlayLayout.PLUS)], out)
        assertFalse(c.feedbackRect(CommanderZones.zone(2, CommanderZones.B_PLUS), players, out))
        assertTrue(c.feedbackRect(CommanderZones.zone(1, CommanderZones.B_PLUS), partnered, out)); assertEquals(c.quarter[1 * 4 + 3], out)
        assertTrue(c.feedbackRect(CommanderZones.DIAMOND, players, out)); assertEquals(l.menuBarsBacking, out)
        assertFalse(c.feedbackRect(DrawerZones.zone(1, DrawerZones.TAB), players, out))
    }

    @Test fun quartersTileEachPanel() {
        for (c in all) for (s in 0 until 4) {
            var area = 0
            for (i in 0 until 4) {
                val r = c.quarter[s * 4 + i]
                assertTrue(inside(r, c.play.panels[s]))
                area += r.width * r.height
                // fingertip-wide in the short dimension
                assertTrue("$c", r.width * c.play.grid.scale >= PlayLayout.MIN_TOUCH_DEVICE_PX)
            }
            assertEquals(c.play.panelW * c.play.panelH, area)
        }
    }

    @Test fun partnerNumbersStayInTheirHalfAndPmClearRule() {
        val c = nomad[2]
        val fp = IRect()
        for (v in intArrayOf(0, 7, 21, 99)) {
            c.partnerNumberFootprint(3, Commander.PRIMARY, v, fp)
            assertTrue("$v $fp", inside(fp, c.partnerSlot[3 * 2]))
            c.partnerNumberFootprint(0, Commander.PARTNER, v, fp)
            assertTrue("$v $fp", inside(fp, c.partnerSlot[0 * 2 + 1]))
            assertTrue(c.partnerPmClear(v))
        }
        assertFalse(c.partnerPmClear(100))
    }

    @Test fun upperLabelTruncatesAndUppercases() {
        val l = UpperLabel()
        l.set(CommanderLayout.TO, "  Gandalf the Grey ", 7)
        assertEquals("TO GANDALF", l.toString())
        assertEquals(10, l.length)
        l.set("", "ab", 0)
        assertEquals(0, l.length)
        // a partner flag from core reaches the hit test (sanity)
        val st = GameState.create(4)
        assertFalse(st.players[1].partner)
        assertTrue(au.com.josephmark.dithertracker.core.reduce(st, Action.SetPartner(st.players[1].id, true)).players[1].partner)
    }
}
