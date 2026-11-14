package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.purity.ScreenRig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T51 (Sam: "selecting a tool from the menu should still dismiss the menu — it just seemed like there
 * was an idle timeout I was trying to remove"): the open gem never closes by itself, however long it
 * waits; choosing a tool closes it (undo / redo excepted, as before), and so do its X and a tap outside.
 */
class MenuDismissTest {

    private fun rig(s: Int): ScreenRig {
        val rig = ScreenRig(1872, 1404, s)
        rig.dispatch(Action.ChangeLife(rig.id(2), -3)) // something to undo
        rig.advance(10_000)
        return rig
    }

    private fun openAndWait(rig: ScreenRig) {
        rig.openMenu()
        assertEquals(MenuState.OPEN, rig.menu.state.phase)
        rig.advance(3_600_000)
        assertEquals("no idle timeout", MenuState.OPEN, rig.menu.state.phase)
    }

    private fun tapItem(rig: ScreenRig, item: MenuItem) = rig.gemPoint(item).let { rig.tapAt(it[0], it[1]) }

    @Test fun everyToolClosesTheGemAndItNeverTimesOut() {
        for (s in 2..6) {
            val rig = rig(s)
            val tag = "S=$s"
            openAndWait(rig)
            tapItem(rig, MenuItem.UNDO)
            assertEquals("$tag undo keeps it", MenuState.OPEN, rig.menu.state.phase)
            tapItem(rig, MenuItem.REDO)
            assertEquals("$tag redo keeps it", MenuState.OPEN, rig.menu.state.phase)
            rig.closeMenu()

            openAndWait(rig)
            tapItem(rig, MenuItem.DICE)
            assertEquals("$tag dice", MenuState.CLOSED, rig.menu.state.phase)
            assertTrue(rig.dice.state.isOpen)
            rig.dice.state.close()

            openAndWait(rig)
            tapItem(rig, MenuItem.LOG)
            assertEquals("$tag log", MenuState.CLOSED, rig.menu.state.phase)
            assertTrue(rig.history.isOpen)
            rig.history.close()

            openAndWait(rig)
            tapItem(rig, MenuItem.SETUP)
            assertEquals("$tag setup", MenuState.CLOSED, rig.menu.state.phase)
            assertTrue(rig.settings.isOpen)
            rig.settings.close()
            rig.advance(10_000)

            openAndWait(rig)
            tapItem(rig, MenuItem.FIRST)
            assertEquals("$tag first", MenuState.CLOSED, rig.menu.state.phase)
            rig.advance(60_000)

            openAndWait(rig)
            tapItem(rig, MenuItem.CLEAN)
            assertEquals("$tag clean", MenuState.CLOSED, rig.menu.state.phase)
            rig.advance(60_000)

            openAndWait(rig)
            tapItem(rig, MenuItem.NEW)
            assertEquals(MenuState.CONFIRM, rig.menu.state.phase)
            rig.advance(3_600_000)
            assertEquals("$tag the confirm waits too", MenuState.CONFIRM, rig.menu.state.phase)
            val g = rig.menu.gem!!
            rig.tapAt(g.confirmBtn.right - 3, g.cy + 5)
            assertEquals("$tag new game", MenuState.CLOSED, rig.menu.state.phase)
            assertTrue(rig.session.state.players.all { it.life == 40 })

            // A tap outside closes it and never reaches the tile.
            openAndWait(rig)
            rig.tapAt(1, 1)
            assertEquals(MenuState.CLOSED, rig.menu.state.phase)
            assertTrue(rig.session.state.players.all { it.life == 40 })
        }
    }

    @Test fun theStateMachineArmsNoIdleTimer() {
        val t = au.com.josephmark.dithertracker.play.FakeScheduler(MenuState.TIMERS)
        val m = MenuState(t, { _, _ -> })
        m.open(); m.confirm(); m.back()
        assertEquals(MenuState.OPEN, m.phase)
        assertEquals(0, t.scheduled)
        assertEquals(1, MenuState.TIMERS) // the optional bloom beat only
    }
}
