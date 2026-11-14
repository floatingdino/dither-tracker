package au.com.josephmark.dithertracker.toggles

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.core.serial.Tracker
import au.com.josephmark.dithertracker.drawer.DrawerItems
import au.com.josephmark.dithertracker.layout.CountRig
import au.com.josephmark.dithertracker.purity.PurityChecks
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T53: trackers hidden in settings ("Counters and trackers") leave every drawer, their badges and the
 * day/night diamond are not drawn, and the game state is kept (display only): showing them again
 * brings back exactly what was there.
 */
class HiddenTrackersTest {

    private fun hide(rig: CountRig, vararg t: Tracker) =
        rig.stage.settings.update(rig.stage.settings.current.copy(hiddenTrackers = t.toSet()))

    private fun trayItems(rig: CountRig, seat: Int): List<Int> {
        if (!rig.drawer.state.isOpen(seat)) rig.tap(rig.tab, seat, 0)
        val out = IntArray(DrawerItems.MAX_ITEMS)
        val s = rig.stage.settings.current
        val n = DrawerItems.tray(rig.session.state.players[seat], s.drawerCounters, out, withToggles = true, hidden = s.hiddenTrackerMask)
        assertEquals("the open drawer lists what tray() says", n, rig.drawer.controller.itemCount(seat))
        return out.take(n)
    }

    @Test fun hiddenTrackersLeaveTheTrayAndShrinkItsPages() {
        for (players in intArrayOf(4, 6)) {
            val rig = CountRig(1872, 1404, 4, players)
            rig.dispatch(Action.SetPartner(rig.id(0), true))
            val all = trayItems(rig, 0)
            assertEquals(2 + 7 + 4 + 1, all.size) // two tax cells (partner), seven counters, four toggles, day/night
            val pagesBefore = rig.drawer.state.pageCount(0)
            hide(rig, *Tracker.entries.toTypedArray())
            val left = trayItems(rig, 0)
            assertEquals("$players p: only the counters are left", 7, left.size)
            assertTrue(left.all { DrawerItems.kind(it) == DrawerItems.COUNTER })
            assertTrue("$players p: fewer pages", rig.drawer.state.pageCount(0) < pagesBefore)
            // One at a time: exactly that tracker's cells go.
            for (t in Tracker.entries) {
                hide(rig, t)
                val items = trayItems(rig, 0)
                val gone = all.filter { it !in items }
                val want = when (t) {
                    Tracker.COMMANDER_TAX -> all.filter { DrawerItems.kind(it) == DrawerItems.TAX }
                    Tracker.DAY_NIGHT -> listOf(DrawerItems.item(DrawerItems.DAY_NIGHT, 0))
                    else -> listOf(DrawerItems.item(DrawerItems.TOGGLE, Toggle.valueOf(t.name).ordinal))
                }
                assertEquals("$t", want, gone)
            }
            hide(rig)
            assertEquals(all, trayItems(rig, 0))
            assertTrue(rig.check("t53-hidden").isEmpty())
        }
    }

    @Test fun hiddenBadgesAndDiamondAreNotDrawnButTheStateIsKept() {
        val rig = CountRig(1872, 1404, 4, 4)
        val plain = rig.render().px.copyOf()
        rig.dispatch(Action.SetToggle(rig.id(0), Toggle.MONARCH, true))
        rig.dispatch(Action.SetToggle(rig.id(2), Toggle.RING_BEARER, true))
        rig.dispatch(Action.SetDayNight(DayNight.NIGHT))
        rig.advance(10_000)
        val shown = rig.render().px.copyOf()
        assertTrue(PurityChecks.diffOutside(plain, shown, rig.r.width, emptyArray()) != null)
        val before = rig.session.state
        hide(rig, Tracker.MONARCH, Tracker.RING_BEARER, Tracker.DAY_NIGHT)
        // The table looks as if nobody held anything, and nothing in the game changed.
        assertNull("hidden marks still drawn", PurityChecks.diffOutside(plain, rig.render().px.copyOf(), rig.r.width, emptyArray()))
        assertEquals(before, rig.session.state)
        assertTrue(rig.session.state.players[0].toggles.monarch)
        assertEquals(DayNight.NIGHT, rig.session.state.dayNight)
        // Showing them again brings back exactly what was there.
        hide(rig)
        assertArrayEquals(shown, rig.render().px)
        // Hiding one badge of two leaves the other drawn (day/night stays hidden: the diamond is plain).
        hide(rig, Tracker.MONARCH, Tracker.DAY_NIGHT)
        val oneHidden = rig.render().px.copyOf()
        assertNull(PurityChecks.diffOutside(plain, oneHidden, rig.r.width, arrayOf(rig.one<BadgesComponent>().layout!!.row[2])))
        assertFalse(oneHidden.contentEquals(plain))
    }

    @Test fun aHiddenTaxHasNoPeek() {
        val rig = CountRig(1872, 1404, 4, 4)
        val plain = rig.render().px.copyOf()
        rig.dispatch(Action.ChangeCommanderTax(rig.id(3), 0, 2))
        rig.advance(10_000)
        val taxed = rig.render().px.copyOf()
        assertFalse(taxed.contentEquals(plain))
        hide(rig, Tracker.COMMANDER_TAX)
        assertArrayEquals(plain, rig.render().px)
        assertEquals(2, rig.session.state.players[3].commanderTax[0])
    }

    @Test fun aSettingsChangeRedrawsOnlyWhatItHides() {
        val rig = CountRig(1872, 1404, 4, 4)
        rig.dispatch(Action.SetDayNight(DayNight.DAY))
        rig.platform.invalidated.clear()
        hide(rig, Tracker.DAY_NIGHT)
        assertTrue(rig.platform.invalidated.isNotEmpty())
        assertTrue(rig.platform.invalidated.all { it.width < rig.grid.deviceWidth })
    }
}
