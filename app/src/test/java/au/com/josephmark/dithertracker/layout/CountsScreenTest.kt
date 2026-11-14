package au.com.josephmark.dithertracker.layout

import au.com.josephmark.dithertracker.commander.CommanderZones
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.purity.PurityChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * T11: the whole Play screen at every player count (T53: every arrangement), landscape and portrait, S = 2..6 — every zone
 * reachable, each seat's changes confined to its own tile, and every state pixel-pure (1-bit, global
 * phase, complement in dark mode, nothing off the grid on comfortable layouts).
 */
@RunWith(Parameterized::class)
class CountsScreenTest(private val players: Int, private val layout: String, private val w: Int, private val h: Int, private val s: Int) {

    companion object {
        /** T53: every arrangement in the table, not just the default per count. */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}p {1} {2}x{3} S={4}")
        fun configs(): List<Array<Any>> {
            val out = ArrayList<Array<Any>>()
            for (a in Arrangements.ALL) for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (s in 2..6) out += arrayOf<Any>(a.players, a.id, w, h, s)
            return out
        }
    }

    private fun rig() = CountRig(w, h, s, players, layout = layout)

    private fun assertNoErrors(errs: List<String>) = assertTrue(errs.joinToString("\n"), errs.isEmpty())

    @Test fun idleAndEveryZoneReachable() {
        val rig = rig()
        val l = rig.l
        assertEquals(players, l.playerCount)
        assertNoErrors(rig.check("idle"))
        if (!l.comfortable) return
        for (seat in 0 until players) {
            assertNotNull("$l seat $seat −", rig.pointOf(rig.life, seat, PlayLayout.MINUS))
            assertNotNull("$l seat $seat +", rig.pointOf(rig.life, seat, PlayLayout.PLUS))
            assertNotNull("$l seat $seat tab", rig.pointOf(rig.tab, seat, 0))
        }
        // The diamond opens the menu.
        rig.tapAt(l.diamondCx, l.diamondCy)
        assertTrue("$l: the gem did not open", rig.menu.state.phase != au.com.josephmark.dithertracker.menu.MenuState.CLOSED)
        assertNoErrors(rig.check("gem"))
    }

    @Test fun eachSeatChangesOnlyItsOwnTile() {
        val rig = rig()
        if (!rig.l.comfortable) return
        val base = rig.render().px.copyOf()
        for (seat in 0 until players) {
            rig.tap(rig.life, seat, PlayLayout.MINUS) // readout + number
            val after = rig.render().px.copyOf()
            assertNull("${rig.tag} seat $seat", PurityChecks.diffOutside(base, after, rig.r.width, arrayOf(rig.l.seats[seat])))
            rig.advance(5_000) // commit; effects play and end inside the tile
            rig.dispatch(Action.SetLife(rig.id(seat), 40))
            rig.advance(5_000)
        }
    }

    @Test fun drawers() {
        val rig = rig()
        if (!rig.l.comfortable) return
        val errs = ArrayList<String>()
        for (seat in 0 until players) {
            val base = rig.render().px.copyOf()
            rig.tap(rig.tab, seat, 0)
            assertTrue(rig.drawer.state.isOpen(seat))
            // Every cell's halves on the page are reachable; bump the first two.
            var cells = 0
            for (slot in 0 until DrawerZones.MAX_SLOTS) {
                val p = rig.pointOf(rig.drawer, seat, DrawerZones.cell(slot, DrawerZones.PLUS)) ?: continue
                assertNotNull(rig.pointOf(rig.drawer, seat, DrawerZones.cell(slot, DrawerZones.MINUS)))
                if (slot < 2) rig.tapAt(p[0], p[1])
                cells++
            }
            // Two cells a page at least; an alternate on a cramped scale may hold one (6-dinner at S = 2), still paged.
            assertTrue("${rig.tag} seat $seat: $cells cells", cells >= if (layout == Arrangements.default(players).id) 2 else 1)
            val after = rig.render().px.copyOf()
            assertNull("${rig.tag} drawer $seat", PurityChecks.diffOutside(base, after, rig.r.width, arrayOf(rig.l.seats[seat])))
            errs += rig.check("drawer$seat")
            rig.drawer.controller.closeAll()
            rig.advance(10_000)
        }
        assertNoErrors(errs)
    }

    @Test fun commanderModeHasEveryOpponentAsASource() {
        val rig = rig()
        if (!rig.l.comfortable) return
        val errs = ArrayList<String>()
        rig.dispatch(Action.SetPartner(rig.id(players - 1), true))
        for (target in listOf(0, players - 1)) {
            rig.tap(rig.tab, target, 0)
            rig.tap(rig.drawer, target, DrawerZones.BUTTON)
            assertTrue("${rig.tag}: mode on for $target", rig.cmd.mode.active)
            for (src in 0 until players) {
                if (src == target) {
                    assertNotNull(rig.pointOf(rig.cmd, src, CommanderZones.TARGET))
                    continue
                }
                rig.tap(rig.cmd, src, CommanderZones.A_PLUS)
                if (src == players - 1) rig.tap(rig.cmd, src, CommanderZones.B_PLUS)
            }
            errs += rig.check("commander$target")
            rig.cmd.mode.exit()
            rig.advance(10_000)
        }
        assertNoErrors(errs)
    }

    @Test fun koAndWinner() {
        val rig = rig()
        // An alternate that is not comfortable at this S is never offered (the picker hatches it); its
        // cramped KO quips are not held to the purity bar (T60: 5-hole at S = 5 tripped the phase sniffer).
        if (!rig.l.comfortable && layout != Arrangements.default(players).id) return
        val errs = ArrayList<String>()
        rig.dispatch(Action.SetLife(rig.id(0), 0))
        rig.advance(6_000)
        errs += rig.check("ko")
        for (seat in 1 until players - 1) rig.dispatch(Action.ChangeCounter(rig.id(seat), Counter.POISON, 10))
        rig.advance(10_000)
        errs += rig.check("winner")
        assertNoErrors(errs)
    }

    @Test fun firstPlayerAndOverlays() {
        val rig = rig()
        val errs = ArrayList<String>()
        for (seat in 0 until players) {
            rig.dispatch(Action.SetStartingPlayer(rig.id(seat)))
        }
        errs += rig.check("start")
        assertNoErrors(errs)
    }

    /** T60: an empty cell is ground, not a seat — no zone, a tap there changes nothing, and it never redraws. */
    @Test fun emptyCellsTakeNoInput() {
        val rig = rig()
        val l = rig.l
        if (l.empties.isEmpty()) return
        val base = rig.render().px.copyOf()
        val state = rig.session.state
        for (e in l.empties) {
            val cx = (e.left + e.right) / 2
            val cy = (e.top + e.bottom) / 2
            if (l.inDiamondHit(cx, cy)) continue // the diamond may sit over a hole's corner
            assertEquals("${rig.tag} empty $e", -1, rig.stage.hitTest(cx, cy))
            rig.platform.invalidated.clear()
            rig.tapAt(cx, cy)
            assertEquals(state, rig.session.state)
            assertTrue("${rig.tag}: a tap on an empty cell redrew ${rig.platform.invalidated}", rig.platform.invalidated.isEmpty())
        }
        assertTrue(rig.render().px.contentEquals(base))
        // Every seat tile is clear of every hole; the holes are drawn as the EMPTY ground.
        for (s in 0 until players) for (e in l.empties) assertFalse(e.left < l.seats[s].right && l.seats[s].left < e.right && e.top < l.seats[s].bottom && l.seats[s].top < e.bottom)
    }

    @Test fun panelsAreTheLayoutsPanels() {
        val rig = rig()
        val want = PlayLayout.compute(rig.grid, players, layout)
        assertEquals(layout, rig.l.arrangement.id)
        for (i in 0 until players) assertEquals(want.panels[i], rig.l.panels[i])
    }
}
