package au.com.josephmark.dithertracker.toggles

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerItems
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.layout.CountRig
import au.com.josephmark.dithertracker.peeks.PeekLayout
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.purity.RasterRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * T14 at every player count, landscape and portrait, S = 2..6: the badge row's geometry (inside the
 * tile, clear of the tab, peeks, diamond, number, readout and ±), badges actually drawn where the
 * layout says, day/night on the diamond, toggle cells in the drawer, single small redraws, and every
 * state pixel-pure (both colour modes).
 */
@RunWith(Parameterized::class)
class TogglesScreenTest(private val players: Int, private val w: Int, private val h: Int, private val s: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}p {1}x{2} S={3}")
        fun configs(): List<Array<Any>> {
            val out = ArrayList<Array<Any>>()
            for (n in 2..6) for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (s in 2..6) out += arrayOf<Any>(n, w, h, s)
            return out
        }

        private fun overlaps(a: IRect, b: IRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
        private fun inside(i: IRect, o: IRect) = i.left >= o.left && i.top >= o.top && i.right <= o.right && i.bottom <= o.bottom
    }

    private fun assertNoErrors(errs: List<String>) = assertTrue(errs.joinToString("\n"), errs.isEmpty())

    @Test fun badgeRowIsClearOfEverythingOnTheTile() {
        val l = PlayLayout.compute(au.com.josephmark.dithertracker.dither.DitherGrid(w, h, s), players)
        val b = BadgeLayout(l)
        if (!l.comfortable) return
        assertTrue("$l $b", b.enabled)
        val pk = PeekLayout(l)
        val r = IRect()
        val corner = IRect()
        for (seat in 0 until players) {
            assertTrue("$b row in panel", inside(b.row[seat], l.panels[seat]))
            for (n in 1..BadgeLayout.MAX) for (i in 0 until n) {
                b.badge(seat, i, n, r)
                val what = "$l seat $seat badge $i/$n $r"
                assertTrue(what, inside(r, b.row[seat]))
                assertFalse("$what tab", overlaps(r, l.tabHit[seat]))
                assertFalse("$what number", overlaps(r, l.numberSlot[seat]))
                assertFalse("$what readout", overlaps(r, l.readoutSlot[seat]))
                assertFalse("$what diamond", overlaps(r, l.diamond))
                if (l.pmVisible) {
                    assertFalse("$what −", overlaps(r, l.minusGlyph[seat]))
                    assertFalse("$what +", overlaps(r, l.plusGlyph[seat]))
                }
                if (pk.enabled) for (c in 0..1) {
                    pk.corner(seat, c, corner)
                    assertFalse("$what peeks $c", overlaps(r, corner))
                }
                for (j in 0 until i) {
                    val q = IRect()
                    b.badge(seat, j, n, q)
                    assertFalse("$what vs $j", overlaps(r, q))
                }
            }
        }
    }

    /** Every seat holds something: monarch on seat 0, initiative on the last, blessing + ring on all; peeks too. */
    private fun crowd(rig: CountRig) {
        rig.dispatch(Action.SetToggle(rig.id(0), Toggle.MONARCH, true))
        rig.dispatch(Action.SetToggle(rig.id(players - 1), Toggle.INITIATIVE, true))
        for (seat in 0 until players) {
            rig.dispatch(Action.SetToggle(rig.id(seat), Toggle.CITYS_BLESSING, true))
            rig.dispatch(Action.SetToggle(rig.id(seat), Toggle.RING_BEARER, true))
            rig.dispatch(Action.ChangeCounter(rig.id(seat), Counter.POISON, 3)) // a peek in each corner set
            rig.dispatch(Action.ChangeCounter(rig.id(seat), Counter.ENERGY, 12))
        }
        rig.advance(10_000)
    }

    @Test fun badgesDrawWhereTheLayoutSaysAndThePaletteStaysPure() {
        val rig = CountRig(w, h, s, players)
        if (!rig.l.comfortable) return
        crowd(rig)
        assertNoErrors(rig.check("t14-badges"))
        val b = rig.one<BadgesComponent>().layout!!
        val px = rig.render().px
        val r = IRect()
        for (seat in 0 until players) {
            val n = rig.session.state.players[seat].toggles.let { t -> Toggle.entries.count { t[it] } }
            assertEquals(if (seat == 0 || seat == players - 1) 3 else 2, n)
            for (i in 0 until n) {
                b.badge(seat, i, n, r)
                var ink = 0
                for (y in r.top until r.bottom) for (x in r.left until r.right) if (px[y * rig.r.width + x] == RasterRenderer.INK) ink++
                assertTrue("${rig.tag} seat $seat badge $i drew nothing in $r", ink > 20)
            }
        }
        for (dn in listOf(DayNight.DAY, DayNight.NIGHT)) {
            rig.dispatch(Action.SetDayNight(dn))
            assertNoErrors(rig.check("t14-$dn"))
        }
    }

    @Test fun aToggleChangeIsOneSmallRedraw() {
        val rig = CountRig(w, h, s, players)
        if (!rig.l.comfortable) return
        val b = rig.one<BadgesComponent>().layout!!
        rig.dispatch(Action.SetToggle(rig.id(0), Toggle.MONARCH, true))
        rig.platform.invalidated.clear()
        rig.dispatch(Action.SetToggle(rig.id(1), Toggle.MONARCH, true)) // moves: two rows
        assertEquals(1, rig.platform.invalidated.size)
        val a0 = b.row[0]
        val a1 = b.row[1]
        val u = IRect(minOf(a0.left, a1.left), minOf(a0.top, a1.top), maxOf(a0.right, a1.right), maxOf(a0.bottom, a1.bottom))
        assertEquals(rig.l.grid.toDevice(u, IRect()), rig.platform.invalidated.single())
        rig.platform.invalidated.clear()
        rig.dispatch(Action.SetDayNight(DayNight.NIGHT))
        assertEquals(rig.l.grid.toDevice(rig.l.diamond, IRect()), rig.platform.invalidated.single())
    }

    @Test fun toggleCellsInEveryDrawer() {
        val rig = CountRig(w, h, s, players)
        if (!rig.l.comfortable) return
        crowd(rig)
        rig.dispatch(Action.SetDayNight(DayNight.DAY))
        for (seat in 0 until players) {
            rig.tap(rig.tab, seat, 0)
            // Page forward to the first toggle cell.
            var guard = 0
            while (!(0 until rig.drawer.state.itemsOnPage(seat)).any { DrawerItems.isToggle(rig.drawer.controller.itemAt(seat, it)) }) {
                rig.tap(rig.drawer, seat, DrawerZones.NEXT)
                assertTrue("${rig.tag} no toggle page", ++guard < 10)
            }
        }
        assertNoErrors(rig.check("t14-drawers"))
        // Seat 1 claims the monarch from its drawer, wherever the cell is.
        val seat = 1
        var slot = -1
        var guard = 0
        while (slot < 0) {
            slot = (0 until rig.drawer.state.itemsOnPage(seat)).firstOrNull {
                rig.drawer.controller.itemAt(seat, it) == DrawerItems.item(DrawerItems.TOGGLE, Toggle.MONARCH.ordinal)
            } ?: -1
            if (slot < 0) { rig.tap(rig.drawer, seat, DrawerZones.NEXT); assertTrue(++guard < 10) }
        }
        rig.tap(rig.drawer, seat, DrawerZones.cell(slot, DrawerZones.PLUS))
        assertTrue(rig.session.state.players[seat].toggles.monarch)
        assertFalse(rig.session.state.players[0].toggles.monarch)
        assertNoErrors(rig.check("t14-claimed"))
    }
}
