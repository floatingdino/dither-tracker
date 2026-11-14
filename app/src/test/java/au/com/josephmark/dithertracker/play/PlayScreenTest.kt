package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.commander.CommanderZones
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.MINUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.PLUS
import au.com.josephmark.dithertracker.play.PlayRig.Companion.mid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Touch on the whole Play screen through the Stage (T34): which component, seat and local id a
 * point hits, and what a press inverts. These were `PlayLayout.hitTest`, `DrawerLayout.hitTest` and
 * `CommanderLayout.hitTest` before the component refactor; the assertions are the same, re-expressed
 * per component (the tab is the drawer's `DrawerTab`, the diamond is a block until T09).
 */
class PlayScreenTest {

    private val screens = listOf(1872 to 1404, 2560 to 1600, 1280 to 800, 1404 to 1053)

    // ---- life halves, tabs, diamond (was PlayLayoutTest.hitTestZonesFromThePlayersPointOfView) ----

    @Test fun hitTestZonesFromThePlayersPointOfView() {
        val rig = PlayRig()
        val l = rig.l
        fun midY(p: IRect) = (p.top + p.bottom) / 2
        for (s in 0 until 4) {
            val p = l.panels[s]
            val screenLeft = rig.hit(p.left + 20, midY(p))
            val screenRight = rig.hit(p.right - 21, midY(p))
            if (s < 2) { // top row, rotated: screen-left is the player's right → +
                assertEquals("life:$s:$PLUS", screenLeft)
                assertEquals("life:$s:$MINUS", screenRight)
            } else {
                assertEquals("life:$s:$MINUS", screenLeft)
                assertEquals("life:$s:$PLUS", screenRight)
            }
            // the glyphs themselves sit in their own zone
            assertEquals("life:$s:$MINUS", rig.hit(l.minusGlyph[s].left + 1, l.minusGlyph[s].top + 1))
            assertEquals("life:$s:$PLUS", rig.hit(l.plusGlyph[s].left + 1, l.plusGlyph[s].top + 1))
            // the tab is the drawer tab's zone (pre-T34: reserved for the drawer)
            assertEquals("tab:$s", rig.hit(l.tab[s].left + 2, l.tab[s].top + 2))
        }
        // The diamond opens the menu (T09; pre-T09 a block).
        assertEquals("menu", rig.hit(l.diamondCx, l.diamondCy))
        assertEquals("menu", rig.hit(l.diamondCx + 5, l.diamondCy - 5))
        assertEquals("none", rig.hit(l.dividerV.left, 50))
        assertEquals("none", rig.hit(50, l.dividerH.top))
        assertEquals("none", rig.hit(467, 50)) // spare column
        assertEquals("none", rig.hit(-1, 10))
        // Diamond corner region outside the shape belongs to a panel.
        assertEquals("life:3:$PLUS", rig.hit(l.diamondCx - 20, l.diamondCy + 20))
    }

    /** Was the hit half of PlayLayoutTest.zonesCoverEachPanelExactlyAndMatchHitTest. */
    @Test fun lifeZoneCentresHitTheirZoneOrTheTabOrDiamond() {
        for ((w, h) in screens) for (sc in 2..6) {
            val rig = PlayRig(w, h, sc)
            val l = rig.l
            for (s in 0 until 4) for (side in intArrayOf(MINUS, PLUS)) {
                val r = l.zones[PlayLayout.zone(s, side)]
                val hit = rig.hit((r.left + r.right) / 2, (r.top + r.bottom) / 2)
                assertTrue("$w×$h S=$sc seat $s side $side: $hit", hit == "life:$s:$side" || hit == "tab:$s" || hit == "none")
            }
        }
    }

    // ---- drawer (was DrawerLayoutTest) -------------------------------------------------------------

    private fun PlayRig.allOpen() {
        for (s in 0 until 4) { drawer.state.setOpen(s, true); drawer.state.setPaging(s, 100, 6) }
    }

    @Test fun drawerHitTestFromThePlayersPointOfView() {
        val rig = PlayRig()
        val d = rig.drawer.layout!!
        rig.allOpen()
        val st = rig.drawer.state
        for (s in 0 until 4) {
            // the player's left half of slot 0 is "−" whichever way up the seat is
            val left = d.halfHit[(s * DrawerZones.MAX_SLOTS) * 2]
            val c = mid(left)
            assertEquals("drawer:$s:${DrawerZones.cell(0, DrawerZones.MINUS)}", rig.hit(c[0], c[1]))
            val right = d.halfHit[(s * DrawerZones.MAX_SLOTS) * 2 + 1]
            val c2 = mid(right)
            assertEquals("drawer:$s:${DrawerZones.cell(0, DrawerZones.PLUS)}", rig.hit(c2[0], c2[1]))
            if (d.play.rotation[s] == 180) assertTrue(left.left > right.left) else assertTrue(left.left < right.left)
            val b = mid(d.barSeg[s * 3 + 1])
            assertEquals("drawer:$s:${DrawerZones.TAB}", rig.hit(b[0], b[1]))
            // upper area still drives life
            val u = mid(d.lifeZones[s * 2 + PLUS])
            assertEquals("life:$s:$PLUS", rig.hit(u[0], u[1]))
        }
        // The bar's left segment is the commander button (T30), on every seat, whichever way up.
        for (s in 0 until 4) {
            val b = mid(d.cmdVis[s])
            assertEquals("drawer:$s:${DrawerZones.BUTTON}", rig.hit(b[0], b[1]))
        }
        // page 0 of 2: the right segment pages on
        val n = mid(d.barSeg[3 * 3 + 2])
        assertEquals("drawer:3:${DrawerZones.NEXT}", rig.hit(n[0], n[1]))
        // the bar's hit extends upward by the tab's hit pad
        assertEquals("drawer:3:${DrawerZones.NEXT}", rig.hit(n[0], d.bar[3].top - d.barHitPad))
        // on the last page it goes back to the first; with one page it closes
        st.setPaging(3, d.perPage + 1, d.perPage); st.nextPage(3)
        assertFalse(st.hasNext(3))
        assertEquals("drawer:3:${DrawerZones.PREV}", rig.hit(n[0], n[1]))
        st.setPaging(3, 3, d.perPage)
        assertEquals("drawer:3:${DrawerZones.TAB}", rig.hit(n[0], n[1]))
        // the diamond is still the menu's
        assertEquals("menu", rig.hit(d.play.diamondCx, d.play.diamondCy))
    }

    @Test fun emptyCellsAndClosedDrawers() {
        val rig = PlayRig()
        val d = rig.drawer.layout!!
        val st = rig.drawer.state
        // closed: tab → the tab's zone, the rest of the panel is life
        val t = d.play.tab[2]
        assertEquals("tab:2", rig.hit(t.left + 2, t.top + 2))
        val cell = d.halfHit[(2 * DrawerZones.MAX_SLOTS + 5) * 2]
        assertTrue(rig.hit(cell.left + 1, cell.top + 1).startsWith("life:2:"))
        // open with only 2 items: slot 5 is empty → nothing
        st.setOpen(2, true); st.setPaging(2, 2, d.perPage)
        assertEquals("none", rig.hit(cell.left + 1, cell.top + 1))
    }

    @Test fun drawerFeedbackRects() {
        val rig = PlayRig()
        val d = rig.drawer.layout!!
        val st = rig.drawer.state
        assertEquals(d.play.tab[1], rig.feedback(rig.tab, 1, 0))
        assertEquals(d.play.zones[PlayLayout.zone(1, MINUS)], rig.feedback(rig.life, 1, MINUS))
        assertNull(rig.feedback(rig.drawer, 1, DrawerZones.cell(0, DrawerZones.PLUS))) // closed
        assertNull(rig.feedback(rig.drawer, 1, DrawerZones.BUTTON)) // closed
        st.setOpen(1, true)
        assertEquals(d.lifeZones[PlayLayout.zone(1, MINUS)], rig.feedback(rig.life, 1, MINUS))
        assertEquals(d.halfVis[(1 * DrawerZones.MAX_SLOTS + 3) * 2 + 1], rig.feedback(rig.drawer, 1, DrawerZones.cell(3, DrawerZones.PLUS)))
        assertEquals(d.barSeg[1 * 3 + 1], rig.feedback(rig.drawer, 1, DrawerZones.TAB))
        assertEquals(d.barSeg[1 * 3 + 1], rig.feedback(rig.tab, 1, 0)) // the same place while open
        assertEquals(d.barSeg[1 * 3 + 2], rig.feedback(rig.drawer, 1, DrawerZones.NEXT))
        assertEquals(d.cmdVis[1], rig.feedback(rig.drawer, 1, DrawerZones.BUTTON))
    }

    // ---- commander mode (was CommanderLayoutTest.hitTestRotatedSeatsAndPartnerQuarters) -------------

    @Test fun commanderHitTestRotatedSeatsAndPartnerQuarters() {
        val rig = PlayRig()
        val c = rig.cmd.layout!!
        val l = rig.l
        // Outside the mode nothing is the mode's (the commander button is in the drawer bar).
        for (s in 0 until 4) {
            val n = mid(l.numberSlot[s])
            assertTrue(rig.hit(n[0], n[1]).startsWith("life:$s:"))
        }
        assertEquals("menu", rig.hit(l.diamondCx, l.diamondCy))

        // Target 3 (upright, BL).
        rig.cmd.mode.press(3)
        val n3 = mid(l.numberSlot[3])
        assertEquals("cmd:3:${CommanderZones.TARGET}", rig.hit(n3[0], n3[1]))
        assertEquals("close", rig.hit(l.diamondCx, l.diamondCy))
        // Drawer tabs are hidden in the mode: their spot is each tile's PARTNER tab (T52), and
        // beside it the tile's half.
        val tab = l.tab[2]
        assertEquals("cmd:2:${CommanderZones.PARTNER}", rig.hit(tab.left + 2, tab.top + 2))
        assertEquals("cmd:3:${CommanderZones.PARTNER}", mid(l.tab[3]).let { rig.hit(it[0], it[1]) })
        assertEquals("cmd:2:${CommanderZones.A_MINUS}", rig.hit(l.panels[2].left + 2, tab.top + 2))
        // Upright seat 2: screen-left half is −.
        val p2 = l.panels[2]
        assertEquals("cmd:2:${CommanderZones.A_MINUS}", rig.hit(p2.left + 10, n3[1]))
        assertEquals("cmd:2:${CommanderZones.A_PLUS}", rig.hit(p2.right - 10, n3[1]))
        // Rotated seat 0: screen-left is the player's right → +.
        val p0 = l.panels[0]
        val y0 = mid(l.numberSlot[0])[1]
        assertEquals("cmd:0:${CommanderZones.A_PLUS}", rig.hit(p0.left + 10, y0))
        assertEquals("cmd:0:${CommanderZones.A_MINUS}", rig.hit(p0.right - 10, y0))
        // Partners: quarters, in the player's frame (seats 0 and 1 are rotated).
        rig.session.dispatch(Action.SetPartner(rig.id(0), true), 0)
        rig.session.dispatch(Action.SetPartner(rig.id(1), true), 0)
        val want = intArrayOf(CommanderZones.A_MINUS, CommanderZones.A_PLUS, CommanderZones.B_MINUS, CommanderZones.B_PLUS)
        for (s in intArrayOf(0, 1)) for (i in 0 until 4) {
            val m = mid(c.quarter[s * 4 + i])
            assertEquals("seat $s quarter $i", "cmd:$s:${want[i]}", rig.hit(m[0], m[1]))
        }
        // The player-frame first quarter of a rotated seat is on the screen's right.
        assertTrue(c.quarter[0].left > c.quarter[3].left)
        // Dividers and margins are nothing.
        assertEquals("none", rig.hit(l.dividerV.left, 20))
        // The close X inverts the X's square.
        assertEquals(c.closeIcon, rig.feedback(rig.close, -1, 0))
    }

    @Test fun commanderModeHidesLifeDrawerTabsAndMenu() {
        val rig = PlayRig()
        rig.drawer.controller.onTap(2, DrawerZones.TAB)
        assertTrue(rig.drawer.state.isOpen(2))
        rig.cmd.mode.press(3)
        assertFalse(rig.drawer.state.isOpen(2)) // the cover closed it
        for (s in 0 until 4) {
            assertFalse(rig.stage.isVisible(rig.life, s)); assertFalse(rig.stage.isVisible(rig.drawer, s)); assertFalse(rig.stage.isVisible(rig.tab, s))
            assertTrue(rig.stage.isVisible(rig.readout, s)); assertTrue(rig.stage.isVisible(rig.cmd, s))
        }
        assertFalse(rig.stage.isVisible(rig.menu, -1)); assertTrue(rig.stage.isVisible(rig.close, -1))
        rig.cmd.mode.close()
        for (s in 0 until 4) assertTrue(rig.stage.isVisible(rig.life, s))
        assertTrue(rig.stage.isVisible(rig.menu, -1))
    }
}
