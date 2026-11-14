package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.menu.GemLayout
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.menu.MenuOverlay
import au.com.josephmark.dithertracker.menu.MenuState
import au.com.josephmark.dithertracker.peeks.PeekLayout
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayRig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPlayerTest {

    private class Rig(s: Int = 4) {
        val rig = PlayRig(s = s)
        val menu = rig.stage.components.filterIsInstance<MenuOverlay>().single()
        val first = rig.stage.components.filterIsInstance<FirstPlayer>().single()
        val l get() = first.layout!!

        fun tap(x: Int, y: Int) {
            rig.stage.down(0, x, y, rig.platform.clock.now)
            rig.stage.up(0)
        }

        fun pickFromMenu(land: Boolean = true) {
            tap(rig.l.diamondCx, rig.l.diamondCy)
            val g = menu.gem!!
            val s = GemLayout.slotOf(MenuItem.FIRST)
            tap(g.facetX[s], g.facetY[s])
            if (land) rig.platform.clock.advance(FirstPlayer.WHO_MS)
        }

        /** [FirstPlayer.pick] and let the "ME?" beat pass. */
        fun pick() {
            first.pick()
            rig.platform.clock.advance(FirstPlayer.WHO_MS)
        }

        fun device(r: IRect) = IRect().also { rig.l.grid.toDevice(r, it) }
        val turn get() = rig.session.state.turn
    }

    private fun IRect.meets(o: IRect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    private fun IRect.inside(o: IRect) = left >= o.left && top >= o.top && right <= o.right && bottom <= o.bottom

    @Test fun firstFacetPicksMarksTheTileAndStartsTurns() {
        val r = Rig()
        r.tap(r.rig.l.diamondCx, r.rig.l.diamondCy)
        r.rig.platform.invalidated.clear()
        val g = r.menu.gem!!
        val s = GemLayout.slotOf(MenuItem.FIRST)
        val before = r.turn
        r.tap(g.facetX[s], g.facetY[s])
        assertEquals(MenuState.OPEN, r.menu.state.phase) // T51: the gem stays open
        // First the "ME?" beat on every live seat: nothing picked on screen, nothing in the game yet.
        assertTrue(r.first.isAsking)
        assertEquals(0b1111, r.first.asking)
        assertEquals(-1, r.first.seat)
        assertEquals(before, r.turn)
        for (i in 0..3) {
            val b = r.device(r.l.bandRect(i, IRect()))
            assertTrue(r.rig.platform.invalidated.any { it.left <= b.left && it.top <= b.top && it.right >= b.right && it.bottom >= b.bottom })
        }
        r.rig.platform.clock.advance(FirstPlayer.WHO_MS - 1)
        assertTrue(r.first.isAsking)
        r.rig.platform.clock.advance(1)
        assertFalse(r.first.isAsking)
        assertEquals(0, r.first.asking)
        val seat = r.first.seat
        assertTrue(seat in 0..3)
        val id = r.rig.id(seat)
        assertEquals(id, r.turn.startingPlayer)
        assertEquals(id, r.turn.activePlayer)
        assertEquals(1, r.turn.number)
        assertTrue(r.first.caption in Flavour.quippy.lines(QuipKind.STARTING_PLAYER).map { it.uppercase() })
        // The band was redrawn (with the gem's closing, in the same frame).
        val band = r.device(r.l.bandRect(seat, IRect()))
        assertTrue(r.rig.platform.invalidated.any { it.left <= band.left && it.top <= band.top && it.right >= band.right && it.bottom >= band.bottom })
        // Undoable like any other game change.
        assertTrue(r.rig.session.history.canUndo)
    }

    @Test fun clearsAfterAWhileWithOneRedrawOfTheBand() {
        val r = Rig()
        r.rig.stage.components.filterIsInstance<au.com.josephmark.dithertracker.fx.FxComponent>().single().player.enabled = false // T55's effect (fx tests) aside
        r.pickFromMenu()
        val seat = r.first.seat
        r.rig.platform.invalidated.clear()
        r.rig.platform.clock.advance(FirstPlayer.SHOW_MS - 1)
        assertEquals(seat, r.first.seat)
        r.rig.platform.clock.advance(1)
        assertEquals(-1, r.first.seat)
        assertEquals(listOf(r.device(r.l.bandRect(seat, IRect()))), r.rig.platform.invalidated)
    }

    @Test fun anyTouchClearsAndStillActs() {
        val r = Rig()
        r.pickFromMenu()
        assertTrue(r.first.seat >= 0)
        r.menu.state.close() // T51: the gem stays open until dismissed; with it open a tile tap only dismisses it
        val z = PlayRig.mid(r.rig.l.zones[PlayLayout.zone(1, PlayLayout.PLUS)])
        r.tap(z[0], z[1])
        assertEquals(-1, r.first.seat)
        assertEquals(41, r.rig.session.state.players[1].life)
        // The centre diamond too (once the gem's X has closed it, T51).
        r.pickFromMenu()
        assertTrue(r.first.seat >= 0)
        r.tap(r.rig.l.diamondCx, r.rig.l.diamondCy)
        assertEquals(MenuState.CLOSED, r.menu.state.phase)
        r.tap(r.rig.l.diamondCx, r.rig.l.diamondCy)
        assertEquals(-1, r.first.seat)
        assertEquals(MenuState.OPEN, r.menu.state.phase)
    }

    @Test fun neverPicksAKnockedOutPlayer() {
        val r = Rig()
        val s = r.rig.session
        s.dispatch(Action.MarkDead(r.rig.id(0)), 0)
        s.dispatch(Action.SetLife(r.rig.id(2), 0), 0)
        s.dispatch(Action.MarkDead(r.rig.id(3)), 0)
        repeat(12) {
            r.pick()
            assertEquals(1, r.first.seat)
            assertEquals(r.rig.id(1), r.turn.startingPlayer)
        }
        // Over many picks with everyone alive, every seat comes up.
        val r2 = Rig()
        val seen = HashSet<Int>()
        repeat(60) { r2.pick(); seen += r2.first.seat }
        assertEquals(setOf(0, 1, 2, 3), seen)
    }

    @Test fun captionsNeverRepeatTwiceRunning() {
        val r = Rig()
        var last = ""
        repeat(30) {
            r.pick()
            assertFalse(r.first.caption == last)
            last = r.first.caption
        }
    }

    @Test fun resultsHookHearsThePick() {
        val r = Rig()
        val heard = ArrayList<Int>()
        r.first.results = object : ToolResults {
            override fun onStartingPlayer(id: Int) { heard += id }
        }
        r.first.pick()
        assertTrue("heard when it lands, not before", heard.isEmpty())
        r.rig.platform.clock.advance(FirstPlayer.WHO_MS)
        assertEquals(listOf(r.rig.id(r.first.seat)), heard)
    }

    @Test fun aTouchDuringTheBeatLandsThePickAndStillActs() {
        val r = Rig()
        r.first.pick()
        assertTrue(r.first.isAsking)
        val z = PlayRig.mid(r.rig.l.zones[PlayLayout.zone(1, PlayLayout.PLUS)])
        r.tap(z[0], z[1])
        assertEquals(41, r.rig.session.state.players[1].life)
        r.rig.platform.clock.advance(0)
        assertFalse(r.first.isAsking)
        assertTrue(r.first.seat >= 0)
        assertEquals(r.rig.id(r.first.seat), r.turn.startingPlayer)
    }

    @Test fun onlyLiveSeatsAskAndPauseLandsWithNothingArmed() {
        val r = Rig()
        r.rig.session.dispatch(Action.MarkDead(r.rig.id(2)), 0)
        r.first.pick()
        assertEquals(0b1011, r.first.asking)
        r.rig.stage.send(au.com.josephmark.dithertracker.stage.Events.PAUSE, au.com.josephmark.dithertracker.stage.Stage.SCREEN)
        assertFalse(r.first.isAsking)
        assertTrue(r.first.seat >= 0)
        assertEquals(r.rig.id(r.first.seat), r.turn.startingPlayer)
        assertFalse("nothing armed after pause", r.rig.stage.timersPending())
        val seat = r.first.seat
        r.rig.platform.clock.advance(60_000)
        assertEquals("the badge waits for the player to come back", seat, r.first.seat)
        r.rig.stage.send(au.com.josephmark.dithertracker.stage.Events.RESUME, au.com.josephmark.dithertracker.stage.Stage.SCREEN)
        r.rig.platform.clock.advance(FirstPlayer.SHOW_MS)
        assertEquals(-1, r.first.seat)
    }

    @Test fun askTextFitsEveryBand() {
        for (s in 2..6) {
            val p = PlayLayout.compute(DitherGrid(1872, 1404, s))
            val l = FirstPlayerLayout(p)
            if (!l.enabled) continue
            val out = IRect()
            for (seat in 0 until p.playerCount) {
                val k = l.askFootprint(seat, FirstPlayer.ME.length, out)
                if (s <= 4) assertTrue("S=$s: shown", k >= l.kMax)
                if (k > 0) assertTrue("S=$s seat $seat $out", out.inside(l.bandRect(seat, IRect())))
            }
        }
    }

    /** The band sits in the under row between the peek corners, clear of the number and the tab. */
    @Test fun bandGeometryAndCaptionsFit() {
        for (s in 2..4) {
            val p = PlayLayout.compute(DitherGrid(1872, 1404, s))
            val l = FirstPlayerLayout(p)
            assertTrue(l.enabled)
            val peek = PeekLayout(p)
            val chip = IRect()
            val fp = IRect()
            for (seat in 0 until PlayLayout.PLAYERS) {
                val band = l.bandRect(seat, IRect())
                assertTrue(band.inside(p.panels[seat]))
                assertFalse(band.meets(p.numberSlot[seat]))
                assertFalse(band.meets(p.tabHit[seat]))
                for (v in listOf(-999, 40, 9999)) { p.numberFootprint(seat, v, fp); assertFalse(band.meets(fp)) }
                for (i in 0 until peek.slots) {
                    peek.chip(seat, i, peek.colW, inverted = true, out = chip)
                    assertFalse("S=$s seat $seat peek $i", band.meets(chip))
                }
                for (q in Flavour.quippy.lines(QuipKind.STARTING_PLAYER)) {
                    l.arrange(seat, q.uppercase())
                    assertFalse("S=$s '$q'", l.truncated)
                    for (i in 0 until l.lines) assertTrue("S=$s '$q' ${l.line[i]} in $band", l.line[i].inside(band))
                }
            }
        }
    }

    @Test fun worksAtEveryScale() {
        for (s in 2..6) {
            val r = Rig(s)
            r.pickFromMenu()
            assertEquals(r.rig.id(r.first.seat), r.turn.startingPlayer)
        }
    }
}
