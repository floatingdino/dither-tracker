package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.serial.SaveCodec
import au.com.josephmark.dithertracker.core.serial.SaveFile
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.layout.Arrangements
import au.com.josephmark.dithertracker.layout.CountRig
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.widgets.PagedList
import au.com.josephmark.dithertracker.widgets.Segmented
import au.com.josephmark.dithertracker.widgets.WidgetTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import au.com.josephmark.dithertracker.type.TextLayout
import org.junit.Test
import java.io.File

/**
 * T53: the Players row's › opens the layout picker for the chosen count — a thumbnail per
 * arrangement, the current one selected, only comfortable ones tappable; a choice is stored per count
 * and applies to the table at once for its own count, at NEW for another. Every count, every card
 * rotation, S = 2..6, both colour modes pure.
 */
class LayoutPickerTest {

    private class Rig(players: Int = 4, s: Int = 4, w: Int = 1872, h: Int = 1404, layout: String = "") {
        val rig = CountRig(w, h, s, players, layout = layout)
        val stage get() = rig.stage
        val settings get() = rig.stage.settings
        val overlay = rig.stage.components.filterIsInstance<SettingsOverlay>().single()
        val card get() = overlay.card
        val players = overlay.main.rows.first { it is PlayersRow } as PlayersRow

        fun tap(r: IRect) {
            stage.down(0, (r.left + r.right) / 2, (r.top + r.bottom) / 2, rig.now)
            stage.up(0)
        }

        fun tapId(id: Int) {
            val r = IRect()
            assertTrue("id $id not on the card", card.region.hitRect(id, r))
            tap(r)
        }

        fun openPicker(rotation: Int = 0) {
            overlay.open(rotation)
            tapId(players.widget.id + (players.widget as Segmented).chevronPart)
        }

        val picker: LayoutPickerRow get() = overlay.page.rows.last() as LayoutPickerRow
        val grid get() = picker.grid

        fun choosePlayers(n: Int) = tapId(players.widget.id + SettingsTable.PLAYERS.indexOf(n))
    }

    @Test fun playersRowHasAChevronThatOpensTheLayoutsForTheChosenCount() {
        val r = Rig()
        r.overlay.open(0)
        val seg = r.players.widget as Segmented
        assertTrue(seg.chevron)
        assertEquals(SettingsTable.PLAYERS.size + 1, seg.idCount)
        assertEquals("the chevron is a fingertip", true, seg.chevronHit.width >= r.card.m.target && seg.chevronHit.height >= r.card.m.target)
        assertFalse("chevron clear of the bar", seg.chevronHit.left < seg.bar.right)
        // The segments still choose a count.
        r.choosePlayers(5)
        assertEquals(5, r.settings.current.playerCount)
        r.tapId(seg.id + seg.chevronPart)
        assertEquals("LAYOUT: 5 PLAYERS", r.card.title.toString())
        assertEquals(r.card.title.length, r.card.titleChars)
        assertEquals(Arrangements.candidates(5).size, r.grid.count)
        assertEquals(0, r.grid.selected) // the default
        // Back returns to the main page.
        r.tapId(r.overlay.page.rows[0].widget.id)
        assertSame(r.overlay.main, r.overlay.page)
    }

    @Test fun everyCountShowsItsArrangementsAndOnlyComfortableOnesAreTappable() {
        for (n in 2..6) {
            val r = Rig()
            r.overlay.open(0)
            r.choosePlayers(n)
            r.openPicker()
            val cs = Arrangements.candidates(n)
            assertEquals(cs.size, r.grid.count)
            for (i in cs.indices) {
                assertSame(cs[i], r.grid.items[i])
                val ok = PlayLayout.compute(DitherGrid(1872, 1404, 4), n, cs[i].id).comfortable
                assertEquals("$n ${cs[i].id}", ok, r.grid.enabledAt[i])
                if (i >= LayoutGrid.PER_PAGE) continue
                // Every one on the page is tappable (a hatched one explains itself on the hint line).
                assertTrue("$n ${cs[i].id} hit", r.card.region.indexOf(r.grid.id + i) >= 0)
                if (!ok) {
                    val before = r.settings.current.layout
                    r.tapId(r.grid.id + i)
                    assertEquals("a hatched one is not chosen", before, r.settings.current.layout)
                    val hint = r.grid.hint.toString()
                    assertTrue(hint, hint.startsWith(LayoutPickerRow.NEEDS_SCALE) || hint == LayoutPickerRow.TOO_SMALL)
                    assertEquals("the hint is whole", hint.length, r.grid.hintChars)
                }
            }
        }
    }

    @Test fun choosingForTheTablesCountAppliesAtOnceAndPersists() {
        val r = Rig(players = 3)
        r.settings.update(r.settings.current.copy(playerCount = 3))
        r.openPicker()
        val i = r.grid.items.indexOfFirst { it?.id == "3-pyramid" }
        r.tapId(r.grid.id + i)
        assertEquals("3-pyramid", Arrangements.of(3, r.settings.current.layout).id)
        assertEquals(i, r.grid.selected)
        // What the screen re-lays out with (PlayView does this on the settings change).
        assertEquals("3-pyramid", PlayLayout.compute(r.rig.grid, 3, r.settings.current.layout).arrangement.id)
        // Tapping the selected one again changes nothing.
        r.tapId(r.grid.id + i)
        assertEquals("3-pyramid", r.settings.current.layout)
        // It survives a save and a reopen.
        val back = SaveCodec.decodeOrNull(SaveCodec.encode(SaveFile(r.rig.session.game, r.settings.current)))!!
        assertEquals("3-pyramid", Arrangements.of(3, back.settings.layout).id)
        r.overlay.close()
        r.openPicker()
        assertEquals(i, r.grid.selected)
    }

    @Test fun choosingForAnotherCountWaitsForNewAndKeepsTheOthers() {
        val r = Rig(players = 4)
        r.overlay.open(0)
        r.choosePlayers(5)
        r.tapId(r.players.widget.id + (r.players.widget as Segmented).chevronPart)
        val i = r.grid.items.indexOfFirst { it?.id == "5-end" }
        r.tapId(r.grid.id + i)
        assertEquals("5-end", r.settings.current.layout)
        assertEquals("the running 4-player table is untouched", "4-grid", PlayLayout.compute(r.rig.grid, 4, r.settings.current.layout).arrangement.id)
        // NEW for five seats lays the table out as chosen.
        r.overlay.close()
        r.rig.dispatch(Action.NewGameFor(5))
        assertEquals("5-end", r.rig.l.arrangement.id)
        // A choice for three leaves five's.
        r.overlay.open(0)
        r.choosePlayers(3)
        r.tapId(r.players.widget.id + (r.players.widget as Segmented).chevronPart)
        r.tapId(r.grid.id + r.grid.items.indexOfFirst { it?.id == "3-pyramid" })
        assertEquals("3-pyramid,5-end", r.settings.current.layout)
    }

    @Test fun pickerFitsAndHitTestsInEveryRotationAndScale() {
        for (s in 2..6) for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (n in 2..6) {
            val r = Rig(s = s, w = w, h = h)
            r.overlay.open(0)
            r.choosePlayers(n)
            r.openPicker()
            for (rot in WidgetTesting.rotations) {
                while (r.overlay.rotation != rot) r.overlay.rotate()
                val g = r.grid
                val what = "S=$s ${w}x$h $n p rot $rot"
                assertFalse("$what: no paging on the picker", r.card.list.hasFooter)
                assertTrue(what, g.bounds.width > 0 && g.bounds.height > 0)
                for (slot in 0 until LayoutGrid.PER_PAGE) {
                    val i = g.itemAt(slot)
                    if (i < 0) continue
                    val box = g.boxes[slot]
                    val cell = g.cells[slot]
                    assertTrue("$what $i box in cell", box.left >= cell.left && box.right <= cell.right && box.top >= cell.top && box.bottom <= cell.bottom)
                    val tw = if (TextLayout.isSideways(rot)) g.thumbH[i] else g.thumbW[i]
                    val th = if (TextLayout.isSideways(rot)) g.thumbW[i] else g.thumbH[i]
                    assertTrue("$what $i thumb in box", tw + 2 * (LayoutGrid.SEL + 1) <= box.width && th + 2 * (LayoutGrid.SEL + 1) <= box.height)
                    assertTrue("$what $i fingertip", cell.width >= r.card.m.target && cell.height >= r.card.m.target)
                    for (k in 0 until g.seatCount[i]) assertTrue("$what $i seat $k drawn", g.seatRects[i][k].width > 0 && g.seatRects[i][k].height > 0)
                }
                // Every live hit rect hit-tests to itself.
                val reg = r.card.region
                for (k in 0 until reg.size) {
                    val hr = reg.hitAt(k)
                    val id = r.stage.hitTest((hr.left + hr.right) / 2, (hr.top + hr.bottom) / 2)
                    assertTrue(what, id >= 0 && r.stage.zoneOwner(id) === r.overlay && r.stage.zoneLocal(id) == reg.idAt(k))
                }
            }
        }
    }

    private fun <T> assertNotNull(v: T?): T { org.junit.Assert.assertNotNull(v); return v!! }

    @Test fun everyCountsPickerIsPureInBothModesAndRotations() {
        val errs = ArrayList<String>()
        for (n in 2..6) for (s in intArrayOf(2, 4, 6)) {
            val r = Rig(s = s)
            r.overlay.open(0)
            r.choosePlayers(n)
            r.openPicker()
            for (rot in WidgetTesting.rotations) {
                while (r.overlay.rotation != rot) r.overlay.rotate()
                errs += r.rig.check("t53-picker-$n-S$s-r$rot")
                if (s == 4) r.rig.render().writePng(File(au.com.josephmark.dithertracker.purity.PurityChecks.outDir, "t53/picker-${n}p-r$rot.png"), 4)
            }
        }
        assertTrue(errs.joinToString("\n"), errs.isEmpty())
    }

    @Test fun countersAndTrackersPageRendersPure() {
        val r = Rig()
        r.overlay.open(0)
        r.tapId(r.overlay.main.rows.first { it is LinkRow }.widget.id)
        val errs = ArrayList<String>()
        errs += r.rig.check("t53-counters-1")
        r.rig.render().writePng(File(au.com.josephmark.dithertracker.purity.PurityChecks.outDir, "t53/counters-1.png"), 4)
        r.tapId(SettingsCard.LIST + PagedList.NEXT)
        errs += r.rig.check("t53-counters-2")
        r.rig.render().writePng(File(au.com.josephmark.dithertracker.purity.PurityChecks.outDir, "t53/counters-2.png"), 4)
        assertTrue(errs.joinToString("\n"), errs.isEmpty())
    }

    @Test fun aCountWithMoreThanAPagePagesWithTheFooter() {
        val r = Rig()
        r.overlay.open(0)
        r.choosePlayers(3)
        r.openPicker()
        val g = r.grid
        assertEquals(1, g.pages)
        assertFalse(g.hasFooter)
        // Pretend the count had 20 (the table has 12 at most today): two pages, ‹ disabled on the first.
        val keep = g.count
        g.count = 20
        for (i in keep until 20) { g.items[i] = g.items[0]; g.layouts[i] = g.layouts[0]; g.enabledAt[i] = true }
        g.setPage(0)
        r.overlay.rotate(); r.overlay.rotate(); r.overlay.rotate(); r.overlay.rotate() // re-lay out
        assertEquals(2, g.pages)
        assertTrue(g.hasFooter)
        assertEquals("1/2", g.indicator.toString())
        assertTrue(r.card.region.indexOf(g.id + LayoutGrid.NEXT) >= 0)
        assertFalse(r.card.region.indexOf(g.id + LayoutGrid.PREV) >= 0)
        r.tapId(g.id + LayoutGrid.NEXT)
        assertEquals(1, g.page)
        assertEquals("2/2", g.indicator.toString())
        assertEquals(LayoutGrid.PER_PAGE, g.itemAt(0))
        assertEquals(-1, g.itemAt(20 - LayoutGrid.PER_PAGE))
        r.tapId(g.id + LayoutGrid.PREV)
        assertEquals(0, g.page)
    }
}
