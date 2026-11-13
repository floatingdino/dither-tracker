package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.eink.CleanComponent
import au.com.josephmark.dithertracker.eink.ScreenCleaner
import au.com.josephmark.dithertracker.menu.GemLayout.Companion.CENTRE
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayRig
import au.com.josephmark.dithertracker.stage.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuOverlayTest {

    private class Rig(s: Int = 4) {
        val rig = PlayRig(s = s)
        val menu = rig.stage.components.filterIsInstance<MenuOverlay>().single()
        val g get() = menu.gem!!
        val l = rig.l

        fun tap(x: Int, y: Int) {
            rig.stage.down(0, x, y, rig.platform.clock.now)
            rig.stage.up(0)
        }

        fun tapSlot(slot: Int) = tap(g.facetX[slot], g.facetY[slot])
        fun tapItem(item: MenuItem) = tapSlot(GemLayout.slotOf(item))
        fun open() = tap(l.diamondCx, l.diamondCy)
        val phase get() = menu.state.phase

        /** "menu:<local>", "menu-diamond", another component's key, or "none". */
        fun hit(x: Int, y: Int): String {
            val id = rig.stage.hitTest(x, y)
            if (id < 0) return "none"
            return if (rig.stage.zoneOwner(id) === menu) "menu:${rig.stage.zoneLocal(id)}" else rig.key(id)
        }

        fun life(seat: Int) = rig.session.state.players[seat].life
        fun clearInvalidations() = rig.platform.invalidated.clear()
    }

    @Test fun diamondOpensWithOneRedrawOfTheGem() {
        val r = Rig()
        assertEquals("menu", r.hit(r.l.diamondCx, r.l.diamondCy))
        r.clearInvalidations()
        r.open()
        assertEquals(MenuState.OPEN, r.phase)
        // Press, tap and release: each entry point flushes once; the tap's frame covers the whole gem.
        val gemDev = IRect()
        r.l.grid.toDevice(r.g.bounds, gemDev)
        assertTrue(r.rig.platform.invalidated.any { it.left <= gemDev.left && it.top <= gemDev.top && it.right >= gemDev.right && it.bottom >= gemDev.bottom })
        assertEquals("menu:$CENTRE", r.hit(r.l.diamondCx, r.l.diamondCy))
    }

    @Test fun zonesWhileOpenAndClosed() {
        val r = Rig()
        r.rig.stage.collectNow()
        val closed = r.rig.stage.collectedCount
        r.rig.session.changeLife(0, -1, 0) // UNDO becomes available
        r.open()
        r.rig.stage.collectNow()
        // Open: + dismiss, the gem block, and every enabled facet: centre, NEW, UNDO, CLEAN, plus each
        // routed item that has a destination registered in PlayScreen (REDO is disabled).
        val routed = MenuItem.entries.count { it.routed && r.menu.destinations[it] != null }
        val facets = 4 + routed
        assertEquals(facets, r.menu.enabled.count { it })
        assertEquals(closed + 2 + facets, r.rig.stage.collectedCount)
        assertEquals(MenuOverlay.ZONES, r.menu.zonesPerSeat)
        // Closed again: back to the closed set.
        r.tapSlot(CENTRE)
        assertEquals(MenuState.CLOSED, r.phase)
        r.rig.stage.collectNow()
        assertEquals(closed, r.rig.stage.collectedCount)
    }

    @Test fun hitTestingWhileOpen() {
        val r = Rig()
        r.menu.destinations.unregister(MenuItem.DICE) // PlayScreen registers it (T16); this test wants it unrouted
        r.rig.session.changeLife(0, -1, 0)
        r.rig.session.changeLife(0, 1, 5_000) // a second step, so REDO appears after one undo
        r.open()
        val g = r.g
        assertEquals("menu:${GemLayout.SW}", r.hit(g.facetX[GemLayout.SW], g.facetY[GemLayout.SW]))
        assertEquals("menu:${GemLayout.E}", r.hit(g.cx + g.outerR, g.cy)) // E tip, on the rim
        // Disabled facets (REDO, unregistered DICE) and the outline swallow: no press, no dismiss.
        assertEquals("none", r.hit(g.facetX[GemLayout.NE], g.facetY[GemLayout.NE]))
        assertEquals("none", r.hit(g.facetX[GemLayout.W], g.facetY[GemLayout.W]))
        // Outside the gem (even the bbox corner) the dismiss zone takes it, not the tile.
        assertEquals("menu:${MenuOverlay.DISMISS}", r.hit(g.bounds.left, g.bounds.top))
        val n = PlayRig.mid(r.l.numberSlot[2])
        assertEquals("menu:${MenuOverlay.DISMISS}", r.hit(n[0], n[1]))
    }

    @Test fun undoRedoKeepTheMenuOpenAndFollowAvailability() {
        val r = Rig()
        assertFalse(r.rig.session.history.canUndo)
        r.open()
        val u = GemLayout.slotOf(MenuItem.UNDO)
        val rd = GemLayout.slotOf(MenuItem.REDO)
        assertFalse(r.menu.enabled[u]); assertFalse(r.menu.enabled[rd])
        r.tapItem(MenuItem.UNDO) // disabled: swallowed, nothing happens, still open
        assertEquals(MenuState.OPEN, r.phase)
        r.tapSlot(CENTRE)

        r.rig.stage.changeLife(3, -1); r.rig.stage.changeLife(3, -1)
        r.rig.platform.clock.advance(10_000) // readout commits, its group closes
        r.rig.stage.changeLife(1, 1)
        assertEquals(38, r.life(3)); assertEquals(41, r.life(1))
        r.open()
        assertTrue(r.menu.enabled[u]); assertFalse(r.menu.enabled[rd])
        r.clearInvalidations()
        r.tapItem(MenuItem.UNDO)
        assertEquals(40, r.life(1)); assertEquals(38, r.life(3))
        assertEquals(MenuState.OPEN, r.phase)
        assertTrue(r.menu.enabled[rd])
        r.tapItem(MenuItem.UNDO)
        assertEquals(40, r.life(3))
        assertFalse(r.menu.enabled[u])
        r.tapItem(MenuItem.REDO)
        assertEquals(38, r.life(3))
        r.tapItem(MenuItem.REDO)
        assertEquals(41, r.life(1))
        assertFalse(r.menu.enabled[rd])
        assertEquals(MenuState.OPEN, r.phase)
    }

    @Test fun newGameConfirmCancelAndConfirm() {
        val r = Rig()
        r.rig.stage.changeLife(2, -5)
        r.rig.session.dispatch(au.com.josephmark.dithertracker.core.Action.Rename(r.rig.id(2), "Cy"), 0)
        r.open()
        r.tapItem(MenuItem.NEW)
        assertEquals(MenuState.CONFIRM, r.phase)
        assertTrue(r.menu.copy.lines in 1..2)
        val g = r.g
        // Confirm zones: buttons (both faces), the fold diamond; outside dismisses.
        assertEquals("menu:${MenuOverlay.CANCEL}", r.hit(g.cancelBtn.left + 2, g.cy - 5))
        assertEquals("menu:${MenuOverlay.CONFIRM}", r.hit(g.confirmBtn.right - 3, g.cy + 5))
        assertEquals("menu:${MenuOverlay.FOLD_CANCEL}", r.hit(g.cx, g.cy))
        assertEquals("none", r.hit(g.cx, g.upperFace.top + 3)) // the prompt itself does nothing
        // Cancel → back to the gem; the fold diamond too.
        r.tap(g.cancelBtn.left + 2, g.cy)
        assertEquals(MenuState.OPEN, r.phase)
        r.tapItem(MenuItem.NEW)
        r.tap(g.cx, g.cy)
        assertEquals(MenuState.OPEN, r.phase)
        assertEquals(35, r.life(2))
        // Confirm → NewGame (values reset, names kept), menu closed.
        r.tapItem(MenuItem.NEW)
        r.tap(g.confirmBtn.left + 3, g.cy - 6)
        assertEquals(MenuState.CLOSED, r.phase)
        assertEquals(40, r.life(2))
        assertEquals("Cy", r.rig.session.state.players[2].name)
        // Undo can bring the old game back.
        assertTrue(r.rig.session.history.canUndo)
    }

    @Test fun confirmCopyComesFromCoreAndBreaksAtTheQuestion() {
        val c = ConfirmCopy()
        val seen = HashSet<String>()
        var last = ""
        repeat(40) {
            c.pick(kotlin.random.Random(it))
            seen += c.prompt
            c.layout(au.com.josephmark.dithertracker.type.Fonts.UI, 1, 181, 58)
            assertTrue(c.lines in 1..2)
            if (c.prompt.contains("? ") && c.prompt.length - c.prompt.indexOf("? ") - 2 <= 30) {
                assertEquals(c.prompt.indexOf("? ") + 1, c.end[0])
            }
            assertTrue(c.confirmChars > 0 && c.cancelChars > 0)
        }
        assertTrue(seen.size > 1)
        // Never the same prompt twice running.
        val r = kotlin.random.Random(7)
        repeat(30) { c.pick(r); assertFalse(c.prompt == last); last = c.prompt }
    }

    @Test fun closesOnXOutsideAndIdleTimeout() {
        val r = Rig()
        r.open(); r.tapSlot(CENTRE)
        assertEquals(MenuState.CLOSED, r.phase)
        // Outside: closes, and the tap does not reach the tile underneath.
        r.open()
        val n = PlayRig.mid(r.l.numberSlot[2])
        r.tap(n[0], n[1])
        assertEquals(MenuState.CLOSED, r.phase)
        assertEquals(40, r.life(2))
        // Idle: closes after IDLE_MS without a touch; a touch re-arms it.
        r.open()
        r.rig.platform.clock.advance(MenuState.IDLE_MS - 1_000)
        assertEquals(MenuState.OPEN, r.phase)
        r.tapItem(MenuItem.UNDO) // disabled facet: swallowed by the block, no tap → no re-arm
        r.rig.stage.changeLife(0, 0)
        r.rig.platform.clock.advance(1_000)
        assertEquals(MenuState.CLOSED, r.phase)
        r.open()
        r.rig.platform.clock.advance(MenuState.IDLE_MS - 1_000)
        r.tapItem(MenuItem.NEW); r.tap(r.g.cancelBtn.left + 2, r.g.cy) // two taps re-arm
        r.rig.platform.clock.advance(MenuState.IDLE_MS - 1_000)
        assertEquals(MenuState.OPEN, r.phase)
        r.rig.platform.clock.advance(1_000)
        assertEquals(MenuState.CLOSED, r.phase)
        // The confirm times out too.
        r.open(); r.tapItem(MenuItem.NEW)
        r.rig.platform.clock.advance(MenuState.IDLE_MS)
        assertEquals(MenuState.CLOSED, r.phase)
    }

    @Test fun cleanFlashesBlackThenRedraws() {
        val r = Rig()
        val cleaner = r.rig.stage.components.filterIsInstance<CleanComponent>().single().cleaner
        r.open()
        r.clearInvalidations()
        r.tapItem(MenuItem.CLEAN)
        assertEquals(MenuState.CLOSED, r.phase)
        assertTrue(cleaner.active)
        assertTrue(cleaner.full)
        val full = IRect()
        r.l.grid.toDevice(IRect(0, 0, r.l.grid.width, r.l.grid.height), full)
        assertTrue(r.rig.platform.invalidated.any { it == full })
        r.clearInvalidations()
        r.rig.platform.clock.advance(ScreenCleaner.HOLD_MS - 1)
        assertTrue(cleaner.active)
        r.rig.platform.clock.advance(1)
        assertFalse(cleaner.active)
        assertEquals(listOf(full), r.rig.platform.invalidated)
        // A touch cancels a running cleanup at once; inside the cleaned region (here the whole
        // screen) the tap itself is swallowed (T19b).
        r.open(); r.tapItem(MenuItem.CLEAN)
        val n = PlayRig.mid(r.l.zones[PlayLayout.zone(3, PlayLayout.PLUS)])
        r.tap(n[0], n[1])
        assertFalse(cleaner.active)
        assertEquals(40, r.life(3))
    }

    @Test fun routedItemsUseRegisteredDestinations() {
        val r = Rig()
        r.menu.destinations.unregister(MenuItem.DICE) // PlayScreen registers it (T16); this test wants it unrouted
        r.open()
        val dice = GemLayout.slotOf(MenuItem.DICE)
        assertFalse(r.menu.enabled[dice])
        r.tapSlot(CENTRE)
        val calls = ArrayList<String>()
        r.menu.destinations.register(MenuItem.DICE) { item, facing -> calls += "$item $facing" }
        r.menu.destinations.register(MenuItem.SETUP) { item, facing -> calls += "$item $facing" }
        r.open()
        assertTrue(r.menu.enabled[dice])
        r.tapItem(MenuItem.DICE)
        assertEquals(MenuState.CLOSED, r.phase)
        r.open(); r.tapItem(MenuItem.SETUP)
        assertEquals(listOf("DICE ${GemLayout.FOLD}", "SETUP 0"), calls)
        r.menu.destinations.unregister(MenuItem.DICE)
        assertNull(r.menu.destinations[MenuItem.DICE])
        try {
            r.menu.destinations.register(MenuItem.UNDO) { _, _ -> }
            throw AssertionError("UNDO is not routable")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun pressFeedbackIsTheFacetBox() {
        val r = Rig()
        r.open()
        val out = IRect()
        val s = GemLayout.slotOf(MenuItem.NEW)
        r.rig.stage.down(0, r.g.facetX[s], r.g.facetY[s], 0)
        val id = r.rig.stage.zoneId(r.menu, Stage.SCREEN, s)
        assertTrue(r.rig.stage.input.isPressed(id))
        assertTrue(r.rig.stage.feedback(id, out))
        assertEquals(r.g.facetRect[s], out)
        r.rig.stage.cancel()
        assertEquals(MenuState.OPEN, r.phase) // a cancelled press applies nothing
    }

    @Test fun openingCommitsReadoutsAndDrawersStayClearOfTheGem() {
        val r = Rig()
        r.rig.stage.changeLife(2, -3)
        assertEquals(-3, r.rig.stage.readouts.delta(2))
        r.open()
        assertEquals(0, r.rig.stage.readouts.delta(2))
        // With every drawer open the compact numbers (up to 3 glyphs: −99..999) still clear the gem.
        // (The compact inner ± glyph and a 4-glyph total's slot corner do not: the drawer moves the
        // number block towards the crossing. Only while both are open; the menu commits nothing there.)
        r.tapSlot(CENTRE)
        for (seat in 0 until 4) r.rig.drawer.state.setOpen(seat, true)
        val t = r.rig.stage.tiles!!
        for (seat in 0 until 4) {
            val n3 = IRect(); t.numberFootprint(seat, 999, n3)
            val m2 = IRect(); t.numberFootprint(seat, -99, m2)
            for (rect in arrayOf(n3, m2)) {
                for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) {
                    assertFalse("seat $seat $rect", r.g.inGem(x, y))
                }
            }
        }
    }

    @Test fun worksAtEveryScale() {
        for (s in 2..6) {
            val r = Rig(s)
            r.rig.session.changeLife(1, -1, 0)
            r.open()
            assertEquals(MenuState.OPEN, r.phase)
            r.tapItem(MenuItem.UNDO)
            assertEquals("S=$s", 40, r.life(1))
            r.tapSlot(CENTRE)
            assertEquals(MenuState.CLOSED, r.phase)
        }
    }
}
