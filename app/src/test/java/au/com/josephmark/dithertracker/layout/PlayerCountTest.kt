package au.com.josephmark.dithertracker.layout

import au.com.josephmark.dithertracker.menu.GemLayout
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.menu.MenuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T11: the player count chosen in settings takes effect on NEW, and the screen re-lays out in place. */
class PlayerCountTest {

    private fun newGameThroughTheGem(rig: CountRig) {
        rig.tapAt(rig.l.diamondCx, rig.l.diamondCy)
        assertEquals("${rig.l}", MenuState.OPEN, rig.menu.state.phase)
        val g = rig.menu.gem!!
        val slot = GemLayout.slotOf(MenuItem.NEW)
        rig.tapAt(g.facetX[slot], g.facetY[slot])
        assertEquals("${rig.l} after NEW", MenuState.CONFIRM, rig.menu.state.phase)
        rig.tapAt(g.confirmBtn.right - 3, g.cy + 5)
    }

    @Test fun newGameAppliesTheChosenCountAndRelaysOut() {
        val rig = CountRig(1872, 1404, 4, 4)
        // No automatic e-ink clean mid-script (its flash swallows a tap, as on the device).
        rig.stage.settings.update(rig.stage.settings.current.copy(fullRefreshEvery = 0))
        var from = 4
        for (n in intArrayOf(6, 2, 5, 3, 4)) {
            rig.stage.settings.update(rig.stage.settings.current.copy(playerCount = n))
            rig.tap(rig.life, 0, 0) // some play first
            newGameThroughTheGem(rig)
            assertEquals("$from -> $n", n, rig.session.state.players.size)
            assertEquals(n, rig.l.playerCount)
            assertEquals(Arrangements.default(n).id, rig.l.arrangement.id)
            assertTrue(rig.session.state.players.all { it.life == 40 })
            rig.prewarm()
            assertTrue(rig.check("after-$n").joinToString(), rig.check("after-$n").isEmpty())
            // Every seat of the new layout takes a tap.
            for (s in 0 until n) rig.tap(rig.life, s, 1)
            assertTrue(rig.session.state.players.all { it.life == 41 })
            rig.advance(10_000)
            from = n
        }
    }

    @Test fun sameCountIsAPlainNewGame() {
        val rig = CountRig(1872, 1404, 4, 4)
        rig.tap(rig.life, 2, 0)
        newGameThroughTheGem(rig)
        assertEquals(4, rig.session.state.players.size)
        assertTrue(rig.session.state.players.all { it.life == 40 })
    }
}
