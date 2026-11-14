package au.com.josephmark.dithertracker.history

import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.menu.GemLayout
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.menu.MenuOverlay
import au.com.josephmark.dithertracker.menu.MenuState
import au.com.josephmark.dithertracker.play.PlayRig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryOverlayTest {

    private class Rig {
        val rig = PlayRig()
        val history = rig.stage.components.filterIsInstance<HistoryOverlay>().single()
        val menu = rig.stage.components.filterIsInstance<MenuOverlay>().single()
        val l = rig.l
        val card get() = history.card

        fun tap(x: Int, y: Int) {
            rig.stage.down(0, x, y, rig.platform.clock.now)
            rig.stage.up(0)
        }

        fun tap(r: IRect) = tap((r.left + r.right) / 2, (r.top + r.bottom) / 2)
        fun tapZone(id: Int) = tap(card.screenHit[id])

        fun openViaMenu() {
            if (!menu.state.isOpen) tap(l.diamondCx, l.diamondCy) // T51: the gem stays open under the card
            assertEquals(MenuState.OPEN, menu.state.phase)
            val g = menu.gem!!
            val slot = GemLayout.slotOf(MenuItem.LOG)
            tap(g.facetX[slot], g.facetY[slot])
        }

        /** Owner key of whatever is hit at (x, y): "history:<local>", another rig key, or "none". */
        fun hit(x: Int, y: Int): String {
            val id = rig.stage.hitTest(x, y)
            if (id < 0) return "none"
            return if (rig.stage.zoneOwner(id) === history) "history:${rig.stage.zoneLocal(id)}" else rig.key(id)
        }

        fun lifeOf(seat: Int) = rig.session.state.players[seat].life
        fun firstRow() = card.rows[0].toString()
    }

    @Test fun logFacetIsLiveAndOpensTheCardFacingItsSide() {
        val r = Rig()
        r.rig.session.changeLife(2, -3, 0)
        r.openViaMenu()
        assertTrue(r.history.isOpen)
        assertEquals(MenuState.OPEN, r.menu.state.phase) // T51: the card opens over the gem
        val g = r.menu.gem!!
        assertEquals(g.rotation[GemLayout.slotOf(MenuItem.LOG)].let { if (it == GemLayout.FOLD) 0 else it }, r.history.rotation)
        assertTrue(r.history.cover.active)
        assertEquals("[life] s2 | −3 → 37", r.firstRow())
    }

    @Test fun whileOpenTheTableTakesNoInputAndOutsideTapsClose() {
        val r = Rig()
        r.history.open(MenuItem.LOG, 0)
        val life = r.lifeOf(2)
        val p = r.l.panels[2]
        // A corner of the bottom-left panel, outside the card: the overlay's dismiss zone.
        val x = p.left + 4
        val y = p.bottom - 4
        assertFalse(r.card.screenCard.contains(x, y))
        assertEquals("history:${HistoryOverlay.DISMISS}", r.hit(x, y))
        // The diamond is covered (under the card's paper, which swallows the tap).
        assertEquals("none", r.hit(r.l.diamondCx, r.l.diamondCy))
        r.tap(x, y)
        assertFalse(r.history.isOpen)
        assertFalse(r.history.cover.active)
        assertEquals(life, r.lifeOf(2))
        assertEquals("menu", r.hit(r.l.diamondCx, r.l.diamondCy))
    }

    @Test fun closeButtonClosesBackToPlay() {
        val r = Rig()
        r.history.open(MenuItem.LOG, 180)
        r.tapZone(HistoryCard.CLOSE)
        assertFalse(r.history.isOpen)
        val p = r.l.panels[3]
        assertTrue(r.hit(p.left + 10, (p.top + p.bottom) / 2).startsWith("life:3"))
    }

    @Test fun rotateStepsAQuarterAtATime() {
        val r = Rig()
        r.history.open(MenuItem.LOG, 180)
        val seen = ArrayList<Int>()
        repeat(4) {
            r.tapZone(HistoryCard.ROTATE)
            seen += r.history.rotation
            // The button is wherever the turned card put it: hit-testing follows.
            assertEquals("history:${HistoryCard.ROTATE}", r.hit(r.card.screenHit[HistoryCard.ROTATE].let { (it.left + it.right) / 2 }, r.card.screenHit[HistoryCard.ROTATE].let { (it.top + it.bottom) / 2 }))
        }
        assertEquals(listOf(270, 0, 90, 180), seen)
    }

    @Test fun pagerTurnsPagesAndRedrawsOnlyTheCard() {
        val r = Rig()
        for (i in 0 until 40) r.rig.session.changeLife(i % 4, -1, i * 10_000L) // 40 separate groups
        r.history.open(MenuItem.LOG, 0)
        assertTrue(r.card.list.pageCount > 1)
        assertTrue(r.card.live[HistoryCard.NEXT])
        r.rig.platform.invalidated.clear()
        r.tapZone(HistoryCard.NEXT)
        assertEquals(1, r.card.list.page)
        val dev = IRect()
        r.l.grid.toDevice(r.card.screenCard, dev)
        assertTrue(r.rig.platform.invalidated.any { it.left <= dev.left && it.top <= dev.top && it.right >= dev.right && it.bottom >= dev.bottom })
        r.tapZone(HistoryCard.PREV)
        assertEquals(0, r.card.list.page)
    }

    @Test fun liveUpdateWhenTheLogChangesWhileOpen() {
        val r = Rig()
        r.history.open(MenuItem.LOG, 0)
        assertTrue(r.card.isEmpty)
        r.rig.platform.invalidated.clear()
        r.rig.session.changeLife(1, -4, 0)
        assertFalse(r.card.isEmpty)
        assertEquals("[life] s1 | −4 → 36", r.firstRow())
        assertTrue(r.rig.platform.invalidated.isNotEmpty())
        r.rig.session.changeLife(1, -1, 100) // coalesces into the same group: the row updates in place
        assertEquals(1, r.card.index.size)
        assertEquals("[life] s1 | −5 → 35", r.firstRow())
    }

    @Test fun undoFromHistoryRemovesTheNewestGroupAndRefreshes() {
        val r = Rig()
        r.rig.session.changeLife(0, -2, 0)
        r.rig.session.changeLife(3, 5, 10_000)
        r.history.open(MenuItem.LOG, 0)
        assertTrue(r.card.live[HistoryCard.UNDO])
        assertEquals("[life] s3 | +5 → 45", r.firstRow())
        r.tapZone(HistoryCard.UNDO)
        assertEquals(40, r.lifeOf(3))
        assertEquals("[life] s0 | −2 → 38", r.firstRow())
        assertTrue(r.history.isOpen)
        r.tapZone(HistoryCard.UNDO)
        assertTrue(r.card.isEmpty)
        assertFalse(r.card.live[HistoryCard.UNDO]) // nothing left: the button hatches and takes no tap
        assertEquals("none", r.hit(r.card.screenHit[HistoryCard.UNDO].left + 2, r.card.screenHit[HistoryCard.UNDO].top + 2))
    }

    @Test fun emptyStateLineComesFromFlavour() {
        val r = Rig()
        r.history.open(MenuItem.LOG, 0)
        assertTrue(r.card.isEmpty)
        assertTrue(r.card.emptyLine.toString() in Flavour.quippy.lines(QuipKind.EMPTY_HISTORY))
        assertNotNull(r.card.emptyLine)
    }
}
