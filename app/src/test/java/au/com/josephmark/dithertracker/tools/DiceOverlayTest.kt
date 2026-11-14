package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.menu.GemLayout
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.menu.MenuOverlay
import au.com.josephmark.dithertracker.menu.MenuState
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayRig
import au.com.josephmark.dithertracker.stage.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceOverlayTest {

    private class Rig(s: Int = 4) {
        val rig = PlayRig(s = s)
        val menu = rig.stage.components.filterIsInstance<MenuOverlay>().single()
        val dice = rig.stage.components.filterIsInstance<DiceOverlay>().single()
        val l get() = dice.layout!!
        val state get() = dice.state

        fun tap(x: Int, y: Int) {
            rig.stage.down(0, x, y, rig.platform.clock.now)
            rig.stage.up(0)
        }

        fun tap(r: IRect) = tap((r.left + r.right) / 2, (r.top + r.bottom) / 2)

        /** Opens the gem and taps its DICE tip (and, with [present], lets the presentation play out). */
        fun openFromMenu(present: Boolean = true) {
            tap(rig.l.diamondCx, rig.l.diamondCy)
            val g = menu.gem!!
            val s = GemLayout.slotOf(MenuItem.DICE)
            tap(g.facetX[s], g.facetY[s])
            if (present) reveal()
        }

        /** Lets the presentation play out. */
        fun reveal() = rig.platform.clock.advance(DiceState.PRESENT_MAX_MS)

        fun hit(x: Int, y: Int): String {
            val id = rig.stage.hitTest(x, y)
            if (id < 0) return "none"
            return if (rig.stage.zoneOwner(id) === dice) "dice:${rig.stage.zoneLocal(id)}" else rig.key(id)
        }

        fun life(seat: Int) = rig.session.state.players[seat].life
        fun deviceRect(r: IRect) = IRect().also { rig.l.grid.toDevice(r, it) }
        fun invalidatedCovers(r: IRect): Boolean {
            val d = deviceRect(r)
            return rig.platform.invalidated.any { it.left <= d.left && it.top <= d.top && it.right >= d.right && it.bottom >= d.bottom }
        }
    }

    @Test fun diceTipOpensOnAFreshD20InOneRedraw() {
        val r = Rig()
        r.tap(r.rig.l.diamondCx, r.rig.l.diamondCy)
        assertTrue(r.menu.enabled[GemLayout.slotOf(MenuItem.DICE)])
        r.rig.platform.invalidated.clear()
        val g = r.menu.gem!!
        val s = GemLayout.slotOf(MenuItem.DICE)
        r.tap(g.facetX[s], g.facetY[s])
        assertEquals(MenuState.CLOSED, r.menu.state.phase)
        assertTrue(r.state.isOpen)
        assertEquals(Tool.D20, r.state.tool)
        assertTrue(r.state.value in 1..20)
        assertTrue(r.invalidatedCovers(r.l.panelOuter))
        // The presentation: one redraw of the panel only per keyframe, then the result.
        r.rig.platform.invalidated.clear()
        r.reveal()
        assertEquals(DiceState.SHOWN, r.state.phase)
        val n = Presentation.TUMBLE.beats(r.state.outcome)
        assertEquals(List(n) { r.deviceRect(r.l.panelOuter) }, r.rig.platform.invalidated)
    }

    @Test fun hitTestingWhileOpen() {
        val r = Rig()
        r.openFromMenu()
        val l = r.l
        for (b in 0 until DiceLayout.BUTTONS) {
            val bt = l.button[b]
            assertEquals("dice:$b", r.hit((bt.left + bt.right) / 2, l.cy - 5))
            assertEquals("dice:$b", r.hit(bt.left, l.cy + 5))
        }
        assertEquals("dice:${DiceOverlay.AGAIN}", r.hit(l.cx, l.cy))
        // The diamond's corners are not the diamond (the fold row there swallows).
        assertEquals("none", r.hit(l.foldRect.left, l.foldRect.top))
        // Faces and rim swallow; outside dismisses, even over a life total.
        assertEquals("none", r.hit(l.resultBox[0].left + 3, l.resultBox[0].top + 3))
        assertEquals("none", r.hit(l.panelOuter.left, l.panelOuter.top))
        assertEquals("dice:${DiceOverlay.DISMISS}", r.hit(l.panelOuter.left - 1, l.cy))
        val n = PlayRig.mid(r.rig.l.numberSlot[2])
        assertEquals("dice:${DiceOverlay.DISMISS}", r.hit(n[0], n[1]))
        // Closed: the tiles are live again.
        r.state.close()
        assertTrue(r.hit(n[0], n[1]).startsWith("life:2:"))
    }

    @Test fun foldButtonsRollAtOnceAndTheDiamondRollsAgain() {
        val r = Rig()
        r.openFromMenu()
        r.tap(r.l.button[DiceLayout.D6])
        assertEquals(Tool.D6, r.state.tool)
        assertEquals(DiceState.ROLLING, r.state.phase)
        r.reveal()
        assertTrue(r.state.value in 1..6)
        val n = r.state.rolls
        r.tap(r.l.cx, r.l.cy)
        assertEquals(Tool.D6, r.state.tool)
        assertEquals(n + 1, r.state.rolls)
        r.reveal()
        r.tap(r.l.button[DiceLayout.COIN])
        r.reveal()
        assertEquals(Tool.COIN, r.state.tool)
        assertTrue(r.dice.label() == DiceOverlay.HEADS || r.dice.label() == DiceOverlay.TAILS)
        r.reveal()
        r.tap(r.l.button[DiceLayout.D20])
        assertEquals(Tool.D20, r.state.tool)
        assertEquals("d20", r.dice.label())
        // A tap during the presentation (anywhere but ×) jumps to the result and does nothing else.
        assertEquals(DiceState.ROLLING, r.state.phase)
        val rolls = r.state.rolls
        r.tap(r.l.button[DiceLayout.D6])
        assertEquals(DiceState.SHOWN, r.state.phase)
        assertEquals(Tool.D20, r.state.tool)
        assertEquals(rolls, r.state.rolls)
        // AGAIN replays the presentation with a fresh roll.
        r.tap(r.l.cx, r.l.cy)
        assertEquals(DiceState.ROLLING, r.state.phase)
        assertEquals(rolls + 1, r.state.rolls)
        // A tap outside during it shows the result rather than closing.
        r.tap(r.l.panelOuter.left - 2, r.l.cy)
        assertTrue(r.state.isOpen)
        assertEquals(DiceState.SHOWN, r.state.phase)
    }

    @Test fun closesOnCloseOutsideAndIdleNeverTouchingLife() {
        val r = Rig()
        r.openFromMenu()
        r.tap(r.l.button[DiceLayout.CLOSE])
        assertFalse(r.state.isOpen)
        assertTrue(r.invalidatedCovers(r.l.panelOuter))
        // Outside: closes, and the tap never reaches the tile underneath.
        r.openFromMenu()
        for (seat in 0 until 4) {
            val z = PlayRig.mid(r.rig.l.zones[PlayLayout.zone(seat, PlayLayout.PLUS)])
            r.tap(z[0], z[1])
            assertFalse(r.state.isOpen)
            assertEquals(40, r.life(seat))
            r.openFromMenu()
        }
        // Idle (the open's presentation already took its share of it).
        r.rig.platform.clock.advance(DiceState.IDLE_MS - 1 - DiceState.PRESENT_MAX_MS)
        assertTrue(r.state.isOpen)
        r.tap(r.l.button[DiceLayout.D6]) // a touch re-arms
        r.rig.platform.clock.advance(DiceState.IDLE_MS - 1)
        assertTrue(r.state.isOpen)
        r.rig.platform.clock.advance(1)
        assertFalse(r.state.isOpen)
        for (seat in 0 until 4) assertEquals(40, r.life(seat))
    }

    @Test fun pressFeedbackIsTheButtonOrTheDiamond() {
        val r = Rig()
        r.openFromMenu()
        val out = IRect()
        val bt = r.l.button[DiceLayout.COIN]
        r.rig.stage.down(0, bt.left + 2, r.l.cy, 0)
        val id = r.rig.stage.zoneId(r.dice, Stage.SCREEN, DiceLayout.COIN)
        assertTrue(r.rig.stage.feedback(id, out))
        assertEquals(bt, out)
        r.rig.stage.cancel()
        assertEquals(Tool.D20, r.state.tool) // a cancelled press rolls nothing
        assertTrue(r.rig.stage.feedback(r.rig.stage.zoneId(r.dice, Stage.SCREEN, DiceOverlay.AGAIN), out))
        assertEquals(r.l.foldRect, out)
        assertFalse(r.rig.stage.feedback(r.rig.stage.zoneId(r.dice, Stage.SCREEN, DiceOverlay.DISMISS), out))
    }

    @Test fun worksAtEveryScale() {
        for (s in 2..6) {
            val r = Rig(s)
            r.openFromMenu()
            assertTrue("S=$s", r.state.isOpen)
            r.tap(r.l.button[DiceLayout.COIN])
            assertEquals(Tool.COIN, r.state.tool)
            r.reveal()
            r.tap(r.l.cx, r.l.cy)
            assertEquals(2, r.state.rolls - 1)
            r.tap(r.l.button[DiceLayout.CLOSE])
            assertFalse("S=$s", r.state.isOpen)
        }
    }

    @Test fun resultsHookHearsEveryRoll() {
        val r = Rig()
        val heard = ArrayList<String>()
        r.dice.results = object : ToolResults {
            override fun onRoll(tool: Tool, value: Int) { heard += "$tool:$value" }
        }
        r.openFromMenu()
        r.tap(r.l.button[DiceLayout.D6])
        r.reveal()
        r.tap(r.l.button[DiceLayout.COIN])
        assertEquals(3, heard.size)
        assertEquals("COIN:${r.state.value}", heard.last())
    }

    @Test fun labelNamesTheDieAndCallsNaturals() {
        val r = Rig()
        r.openFromMenu()
        val seen = HashSet<String>()
        repeat(400) {
            r.tap(r.l.cx, r.l.cy)
            assertEquals("d20", r.dice.label()) // the beat shows the die
            r.reveal()
            val expect = when (r.state.value) { 20 -> DiceOverlay.NAT_20; 1 -> DiceOverlay.NAT_1; else -> "d20" }
            assertEquals(expect, r.dice.label())
            seen += expect
        }
        assertEquals(setOf("d20", DiceOverlay.NAT_1, DiceOverlay.NAT_20), seen)
        r.tap(r.l.button[DiceLayout.D6]); r.reveal()
        assertEquals("d6", r.dice.label())
    }
}
