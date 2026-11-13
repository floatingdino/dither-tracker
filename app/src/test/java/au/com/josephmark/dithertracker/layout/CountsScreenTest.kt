package au.com.josephmark.dithertracker.layout

import au.com.josephmark.dithertracker.commander.CommanderZones
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.purity.PurityChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * T11: the whole Play screen at every player count, landscape and portrait, S = 2..6 — every zone
 * reachable, each seat's changes confined to its own tile, and every state pixel-pure (1-bit, global
 * phase, complement in dark mode, nothing off the grid on comfortable layouts).
 */
@RunWith(Parameterized::class)
class CountsScreenTest(private val players: Int, private val w: Int, private val h: Int, private val s: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}p {1}x{2} S={3}")
        fun configs(): List<Array<Any>> {
            val out = ArrayList<Array<Any>>()
            for (n in 2..6) for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (s in 2..6) out += arrayOf<Any>(n, w, h, s)
            return out
        }
    }

    private fun rig() = CountRig(w, h, s, players)

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
            assertNull("${rig.tag} seat $seat", PurityChecks.diffOutside(base, after, rig.r.width, arrayOf(rig.l.panels[seat])))
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
            assertTrue("${rig.tag} seat $seat: $cells cells", cells >= 2)
            val after = rig.render().px.copyOf()
            assertNull("${rig.tag} drawer $seat", PurityChecks.diffOutside(base, after, rig.r.width, arrayOf(rig.l.panels[seat])))
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

    @Test fun panelsAreTheLayoutsPanels() {
        val rig = rig()
        val want = PlayLayout.compute(rig.grid, players)
        for (i in 0 until players) assertEquals(want.panels[i], rig.l.panels[i])
    }
}
