package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.purity.ScreenRig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T51 (Sam: "menu should stay open until dismissed"): a tool's panel or card opens over the gem, takes
 * the input while it is up, and when it closes the gem is back exactly as it was — still open, same
 * pixels, redrawn by the panel's own close — until the centre X or a tap outside closes it.
 */
class MenuPersistenceTest {

    private fun rig(s: Int = 4, w: Int = 1872, h: Int = 1404): ScreenRig {
        val rig = ScreenRig(w, h, s)
        rig.dispatch(Action.ChangeLife(rig.id(2), -3)) // something to undo
        rig.advance(10_000)
        rig.openMenu()
        assertEquals(MenuState.OPEN, rig.menu.state.phase)
        return rig
    }

    private fun tapItem(rig: ScreenRig, item: MenuItem) = rig.gemPoint(item).let { rig.tapAt(it[0], it[1]) }

    private fun frame(rig: ScreenRig): ByteArray = rig.render(false).px.copyOf()

    /** Device-px rects flushed since the last clear contain [r] (dither px). */
    private fun redrawn(rig: ScreenRig, r: IRect): Boolean {
        val d = IRect().also { rig.l.grid.toDevice(r, it) }
        return rig.platform.invalidated.any { it.left <= d.left && it.top <= d.top && it.right >= d.right && it.bottom >= d.bottom }
    }

    @Test fun diceOpensOverTheGemAndTheGemIsBackWhenItCloses() {
        for (s in 2..6) {
            val rig = rig(s)
            val gem = frame(rig)
            tapItem(rig, MenuItem.DICE)
            rig.advance(au.com.josephmark.dithertracker.tools.DiceState.PRESENT_MAX_MS) // the roll's presentation (T52)
            assertTrue(rig.dice.state.isOpen)
            assertEquals("S=$s", MenuState.OPEN, rig.menu.state.phase)
            assertFalse(gem.contentEquals(frame(rig)))
            // The panel is on top: its zones win at the crossing, and a tap outside closes only the panel.
            assertTrue(rig.stage.zoneOwner(rig.stage.hitTest(rig.l.diamondCx, rig.l.diamondCy)) === rig.dice)
            rig.platform.invalidated.clear()
            rig.tapAt(1, 1)
            assertFalse(rig.dice.state.isOpen)
            assertEquals(MenuState.OPEN, rig.menu.state.phase)
            val panel = IRect().also { rig.dice.bounds(it) }
            assertTrue("the panel's close repaints its rect (and the gem under it)", redrawn(rig, panel))
            assertTrue("S=$s the gem is back as it was", gem.contentEquals(frame(rig)))
            // Now the gem's own dismiss: the tap is swallowed, no life changes.
            val life = rig.session.state.players.map { it.life }
            rig.tapAt(1, 1)
            assertEquals(MenuState.CLOSED, rig.menu.state.phase)
            assertEquals(life, rig.session.state.players.map { it.life })
        }
    }

    @Test fun cardsOpenOverTheGemAndTheGemIsBackWhenTheyClose() {
        val rig = rig()
        val gem = frame(rig)
        tapItem(rig, MenuItem.LOG)
        assertTrue(rig.history.isOpen)
        assertEquals(MenuState.OPEN, rig.menu.state.phase)
        rig.tapAt(1, 1) // outside the card: closes the card only
        assertFalse(rig.history.isOpen)
        assertEquals(MenuState.OPEN, rig.menu.state.phase)
        rig.advance(10_000) // the overlay-close clean, if any
        assertTrue("after history", gem.contentEquals(frame(rig)))

        tapItem(rig, MenuItem.SETUP)
        assertTrue(rig.settings.isOpen)
        assertEquals(MenuState.OPEN, rig.menu.state.phase)
        // The card takes every touch while it is up (the gem underneath gets none).
        assertTrue(rig.stage.zoneOwner(rig.stage.hitTest(rig.l.diamondCx, rig.l.diamondCy)) !== rig.menu)
        rig.settings.close()
        assertEquals(MenuState.OPEN, rig.menu.state.phase)
        rig.advance(10_000)
        assertTrue("after settings", gem.contentEquals(frame(rig)))
    }

    @Test fun firstPlayerAndUndoKeepItOpen() {
        val rig = rig()
        tapItem(rig, MenuItem.FIRST)
        assertEquals(MenuState.OPEN, rig.menu.state.phase)
        rig.advance(au.com.josephmark.dithertracker.tools.FirstPlayer.WHO_MS) // the "ME?" beat (T52), then the pick lands
        assertTrue(rig.session.state.turn.startingPlayer != null)
        tapItem(rig, MenuItem.UNDO) // undoes the pick
        assertEquals(MenuState.OPEN, rig.menu.state.phase)
        rig.advance(3_600_000)
        assertEquals(MenuState.OPEN, rig.menu.state.phase)
        rig.tapAt(rig.l.diamondCx, rig.l.diamondCy) // the centre X
        assertEquals(MenuState.CLOSED, rig.menu.state.phase)
    }
}
