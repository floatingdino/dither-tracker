package au.com.josephmark.dithertracker.commander

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.type.Fonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CommanderLayoutTest {

    private val screens = listOf(1872 to 1404, 2560 to 1600, 1280 to 800, 1404 to 1053)
    private val nomad = (2..6).map { CommanderLayout(PlayLayout.compute(DitherGrid(1872, 1404, it))) }
    private val all = sequence {
        for ((w, h) in screens) for (s in 2..6) yield(CommanderLayout(PlayLayout.compute(DitherGrid(w, h, s))))
    }.filter { it.play.comfortable }.toList()

    private val quips = Flavour.quippy.lines(QuipKind.COMMANDER_TARGET) + Flavour.plain.lines(QuipKind.COMMANDER_TARGET)

    private fun inside(inner: IRect, outer: IRect) =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun overlaps(a: IRect, b: IRect) =
        a.width > 0 && b.width > 0 && a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun mid(r: IRect) = intArrayOf((r.left + r.right) / 2, (r.top + r.bottom) / 2)

    private val players: List<Player> = GameState.create(4).players
    private val partnered: List<Player> = players.mapIndexed { i, p -> if (i == 1 || i == 0) p.copy(partner = true) else p }

    @Test fun theSweepsReallyCoverTheNomadScales() {
        // The S = 2..6 sweeps below only see comfortable layouts: make sure that is not an empty set.
        assertTrue("${all.size}", all.size >= 7) // 20 screen/S combinations, 7 comfortable
        for (s in 2..4) assertTrue("S=$s", nomad[s - 2].play.comfortable && nomad[s - 2].play.underH > 0)
        for (s in 2..4) assertTrue("S=$s", all.any { it.play.toString() == nomad[s - 2].play.toString() })
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
        // Target block: below the readout (33..47, T56) + 4, above the tab band (154 − 8) − 4.
        assertEquals(51, c.targetTop); assertEquals(142, c.targetBottom)
        assertSame(Fonts.NUM_M, c.lifeFont); assertEquals(1, c.lifeK); assertEquals(16, c.lifeRowH)
        assertEquals(6, c.ruleGap)
        // T37: every quip at k = 2 (14-px caps, the 1× life's height), the longest included.
        assertEquals(2, c.quipKMax)
        for (q in quips) assertEquals(q, 2, c.quipK(q.length))
        // Block 14 + 6 + 1 + 6 + 16 = 43 tall, centred on the 175-px tile: caps 66..80, rule 86,
        // life row 93..109.
        assertEquals(43, c.blockH(2)); assertEquals(66, c.blockTop(2))
        val fp = IRect(); val icon = IRect(); val num = IRect()
        assertEquals(2, c.quipFootprint(3, 5, fp))
        assertEquals(IRect((233 - 58) / 2, 176 + 66, (233 - 58) / 2 + 58, 176 + 66 + 18), fp)
        c.ruleFootprint(3, 5, 40, fp)
        assertEquals(IRect((233 - 58) / 2, 176 + 86, (233 - 58) / 2 + 58, 176 + 87), fp) // as wide as "UH OH"
        c.ruleFootprint(3, 2, 40, fp)
        assertEquals(IRect(96, 176 + 86, 137, 176 + 87), fp) // a 2-char quip: as wide as the life
        assertEquals(1, c.lifeFootprint(3, 5, 40, icon, num))
        // icon 16 + gap 3 + "40" (22) = 41 wide, centred at x 96
        assertEquals(IRect(96, 176 + 93, 112, 176 + 109), icon)
        assertEquals(IRect(115, 176 + 94, 137, 176 + 108), num)
        // The close X sits centred on the diamond.
        assertEquals(IRect(225, 167, 241, 183), c.closeIcon)
    }

    @Test fun lifeFontIsAParameter() {
        val c = CommanderLayout(PlayLayout.compute(DitherGrid(1872, 1404, 4)), lifeFont = Fonts.UI)
        assertSame(Fonts.UI, c.lifeFont)
        assertEquals(maxOf(c.iconSize, Fonts.UI.height(c.lifeK)), c.lifeRowH)
    }

    @Test fun targetTileIsComposedAndClearAtS2to6() {
        val fp = IRect(); val icon = IRect(); val num = IRect()
        for (c in all) {
            val l = c.play
            for (s in 0 until 4) for (q in quips) {
                val k = c.quipFootprint(s, q.length, fp)
                assertTrue("$c $q", k in 1..c.quipKMax)
                assertEquals("$c $q", c.quipK(q.length), k)
                assertTrue("$c '$q' $fp", inside(fp, l.panels[s]))
                assertTrue("$c '$q' $fp", inside(fp, c.lifeRow[s]))
                c.lifeFootprint(s, q.length, -999, icon, num)
                for (r in listOf(icon, num)) {
                    assertTrue("$c '$q' $r", inside(r, c.lifeRow[s]))
                    assertFalse("$c '$q' quip vs life", overlaps(r, fp))
                }
                assertFalse(overlaps(icon, num))
                val rule = IRect(); c.ruleFootprint(s, q.length, -999, rule)
                assertEquals(1, rule.height)
                assertTrue("$c '$q' $rule", inside(rule, c.lifeRow[s]))
                // Deliberate spacing: ruleGap from the quip's caps to the rule and from the rule to the life row.
                val capBottom = if (l.rotation[s] == 0) fp.top + c.ui.capHeight * k else fp.bottom - c.ui.capHeight * k
                val lifeTop = if (l.rotation[s] == 0) minOf(icon.top, num.top) else maxOf(icon.bottom, num.bottom)
                if (l.rotation[s] == 0) {
                    assertEquals("$c '$q'", c.ruleGap, rule.top - capBottom)
                    assertEquals("$c '$q'", c.ruleGap, lifeTop - rule.bottom)
                } else {
                    assertEquals("$c '$q'", c.ruleGap, capBottom - rule.bottom)
                    assertEquals("$c '$q'", c.ruleGap, rule.top - lifeTop)
                }
                // Clear of the readout, the tab band and the diamond.
                for (o in listOf(l.readoutSlot[s], l.tabHit[s], l.diamond)) {
                    for (r in listOf(fp, icon, num, rule)) assertFalse("$c '$q' $r vs $o", overlaps(r, o))
                }
                // Centred across the tile (±1 for odd leftovers).
                val p = l.panels[s]
                assertTrue(Math.abs((fp.left - p.left) - (p.right - fp.right)) <= 1)
            }
        }
    }

    @Test fun opponentContentNeverCollidesAtS2to6() {
        for (c in all) {
            val l = c.play
            for (s in 0 until 4) {
                val p = l.panels[s]
                val parts = ArrayList<IRect>()
                for (i in 0..1) {
                    parts += c.partnerSlot[s * 2 + i]
                    if (c.partnerPmVisible) { parts += c.partnerMinus[s * 2 + i]; parts += c.partnerPlus[s * 2 + i] }
                    parts += c.partnerIcon[s * 2 + i]
                }
                for (a in parts) {
                    assertTrue("$c $a", inside(a, p))
                    for (o in listOf(l.readoutSlot[s], l.tabHit[s], l.diamond)) assertFalse("$c $a vs $o", overlaps(a, o))
                }
                for (i in parts.indices) for (j in i + 1 until parts.size) assertFalse("$c ${parts[i]} ${parts[j]}", overlaps(parts[i], parts[j]))
                for (i in 0..1) assertTrue(inside(c.partnerIcon[s * 2 + i], l.underRow[s]))
            }
            assertTrue("$c close X inside the diamond", inside(c.closeIcon, l.diamond))
            for (y in c.closeIcon.top until c.closeIcon.bottom) {
                val hw = l.diamondRowHalf(y - l.diamondCy)
                assertTrue("$c", c.closeIcon.left >= l.diamondCx - hw && c.closeIcon.right - 1 <= l.diamondCx + hw)
            }
        }
    }

    // Hit-testing is CommanderTile's / CommanderClose's now: see play.PlayScreenTest.

    @Test fun feedbackRects() {
        val c = nomad[2]
        val l = c.play
        val out = IRect()
        assertTrue(c.feedbackRect(1, CommanderZones.TARGET, players, out)); assertEquals(l.panels[1], out)
        assertTrue(c.feedbackRect(1, CommanderZones.A_PLUS, players, out))
        assertEquals(l.zones[PlayLayout.zone(1, PlayLayout.PLUS)], out)
        assertFalse(c.feedbackRect(2, CommanderZones.B_PLUS, players, out))
        assertTrue(c.feedbackRect(1, CommanderZones.B_PLUS, partnered, out)); assertEquals(c.quarter[1 * 4 + 3], out)
        assertFalse(c.feedbackRect(1, CommanderZones.PER_SEAT, players, out)) // not one of the mode's
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
        l.set("TO ", "  Gandalf the Grey ", 7)
        assertEquals("TO GANDALF", l.toString())
        assertEquals(10, l.length)
        // the quip path: no prefix, whole line, in caps
        l.set("", "It's you", 99)
        assertEquals("IT'S YOU", l.toString())
        l.set("", "ab", 0)
        assertEquals(0, l.length)
        // a partner flag from core reaches the hit test (sanity)
        val st = GameState.create(4)
        assertFalse(st.players[1].partner)
        assertTrue(au.com.josephmark.dithertracker.core.reduce(st, Action.SetPartner(st.players[1].id, true)).players[1].partner)
    }
}
