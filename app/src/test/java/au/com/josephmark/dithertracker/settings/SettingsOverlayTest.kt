package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.menu.GemLayout
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.menu.MenuOverlay
import au.com.josephmark.dithertracker.menu.MenuState
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayRig
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.StageTest
import au.com.josephmark.dithertracker.widgets.ListRow
import au.com.josephmark.dithertracker.widgets.PagedList
import au.com.josephmark.dithertracker.widgets.RasterCanvas
import au.com.josephmark.dithertracker.widgets.Segmented
import au.com.josephmark.dithertracker.widgets.WidgetMetrics
import au.com.josephmark.dithertracker.widgets.WidgetTesting
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory

class SettingsOverlayTest {

    private class Rig(s: Int = 4, w: Int = 1872, h: Int = 1404) {
        val rig = PlayRig(w = w, h = h, s = s)
        val stage get() = rig.stage
        val settings: SettingsHolder get() = rig.stage.settings
        val overlay = rig.stage.components.filterIsInstance<SettingsOverlay>().single()
        val menu = rig.stage.components.filterIsInstance<MenuOverlay>().single()
        val card get() = overlay.card
        val l = rig.l
        val screen = IRect(0, 0, l.grid.width, l.grid.height)

        /** Settings writes seen by a listener (what `Persistence` observes). */
        val writes = ArrayList<AppSettings>().also { list -> settings.addListener { _, new -> list += new } }

        fun tap(x: Int, y: Int) {
            stage.down(0, x, y, rig.platform.clock.now)
            stage.up(0)
        }

        fun tap(r: IRect) = tap((r.left + r.right) / 2, (r.top + r.bottom) / 2)

        /** Taps local id [id] at the centre of its live hit rect. */
        fun tapId(id: Int) {
            val r = IRect()
            assertTrue("id $id not on the card", card.region.hitRect(id, r))
            tap(r)
        }

        /** Opens it the way a player does: diamond → gem → SETUP facet. */
        fun openFromGem() {
            tap(l.diamondCx, l.diamondCy)
            assertEquals(MenuState.OPEN, menu.state.phase)
            val g = menu.gem!!
            val s = GemLayout.slotOf(MenuItem.SETUP)
            tap(g.facetX[s], g.facetY[s])
        }

        fun row(label: String): SettingsRow = overlay.page.rows.first {
            when (it) {
                is ChoiceRow -> it.label == label
                is ToggleRow -> it.label == label
                is LinkRow -> it.label == label
                is BackRow -> it.label == label
                else -> false
            }
        }

        /** Taps option [i] of the choice row [label]. */
        fun choose(label: String, i: Int) {
            val w = row(label).widget
            tapId(w.id + i)
        }

        fun hitLocal(x: Int, y: Int): Int {
            val id = stage.hitTest(x, y)
            if (id < 0) return -100
            return if (stage.zoneOwner(id) === overlay) stage.zoneLocal(id) else -200
        }
    }

    // ---- open / close / rotate ---------------------------------------------------------------------

    @Test fun gemSetupOpensTheCardFacingSouthAndCoversTheTable() {
        val r = Rig()
        assertNotNull(r.menu.destinations[MenuItem.SETUP])
        r.openFromGem()
        assertTrue(r.overlay.isOpen)
        assertEquals(MenuState.CLOSED, r.menu.state.phase)
        assertEquals(0, r.overlay.rotation)
        assertTrue(r.overlay.cover.active)
        assertTrue(r.overlay.cover.covers(0) && r.overlay.cover.covers(3) && r.overlay.cover.covers(Stage.SCREEN))
        // Tiles and the diamond are gone: a life half outside the card is swallowed, not a life change.
        val life0 = r.rig.session.state.players[3].life
        r.tap(r.l.zones[PlayLayout.zone(3, PlayLayout.PLUS)].right - 4, r.l.zones[PlayLayout.zone(3, PlayLayout.PLUS)].bottom - 4)
        assertEquals(life0, r.rig.session.state.players[3].life)
        assertTrue(r.overlay.isOpen)
        assertEquals(-100, r.hitLocal(2, 2))
        // The dividers stay (BACKDROP is not hidden); everything else below the overlay is.
        assertEquals(0, SettingsOverlay.COVER_HIDES and au.com.josephmark.dithertracker.stage.Kind.BACKDROP)
    }

    @Test fun northFacetOpensFacingNorthAndFoldFacesSouth() {
        val r = Rig()
        r.overlay.open(MenuItem.SETUP, 180)
        assertEquals(180, r.overlay.rotation)
        r.tapId(SettingsCard.CLOSE)
        r.overlay.open(MenuItem.SETUP, GemLayout.FOLD)
        assertEquals(0, r.overlay.rotation)
    }

    @Test fun closeReturnsToThePlayScreenNotTheGem() {
        val r = Rig()
        r.openFromGem()
        r.rig.platform.invalidated.clear()
        r.tapId(SettingsCard.CLOSE)
        assertFalse(r.overlay.isOpen)
        assertFalse(r.overlay.cover.active)
        assertEquals(MenuState.CLOSED, r.menu.state.phase)
        // The card's area (over the dividers) is redrawn, and the tiles are live again.
        val dev = IRect()
        r.l.grid.toDevice(r.card.bounds, dev)
        assertTrue(r.rig.platform.invalidated.any { it.left <= dev.left && it.top <= dev.top && it.right >= dev.right && it.bottom >= dev.bottom })
        assertEquals("menu", r.rig.hit(r.l.diamondCx, r.l.diamondCy))
    }

    @Test fun rotateStepsAQuarterClockwiseThroughAllFourSides() {
        val r = Rig()
        r.openFromGem()
        val seen = ArrayList<Int>()
        repeat(5) {
            seen += r.overlay.rotation
            r.tapId(SettingsCard.ROTATE)
        }
        assertEquals(listOf(0, 90, 180, 270, 0), seen)
        assertTrue(r.overlay.isOpen)
    }

    // ---- size and fit ------------------------------------------------------------------------------

    @Test fun cardIsSquareAndFitsInEveryRotationAtEveryScale() {
        for (s in WidgetTesting.scales) {
            val r = Rig(s)
            val m = WidgetMetrics(s)
            r.overlay.open(0)
            for (rot in WidgetTesting.rotations) {
                while (r.overlay.rotation != rot) r.overlay.rotate()
                val b = r.card.bounds
                assertEquals("S=$s", b.width, b.height)
                assertTrue("S=$s rot $rot $b in ${r.screen}", WidgetTesting.inside(b, r.screen))
                assertTrue("S=$s rows overflow", r.card.list.perPage >= 1)
                for (i in 0 until r.card.region.size) {
                    val hit = r.card.region.hitAt(i)
                    WidgetTesting.assertFingertip(m, hit, "S=$s rot $rot id ${r.card.region.idAt(i)}")
                    assertTrue(WidgetTesting.inside(hit, b))
                    assertTrue(WidgetTesting.inside(r.card.region.feedbackAt(i), hit))
                }
            }
        }
    }

    @Test fun atS4EveryMainRowFitsOnOnePageWithRoomForTheHint() {
        val r = Rig(4)
        r.overlay.open(0)
        val m = WidgetMetrics(4)
        // 8 rows (7 settings + the hint, or back + 7 counters), rowH 27 + 1-px dividers.
        assertEquals(8, r.overlay.pages.maxOf { it.rows.size })
        assertEquals(r.card.side, r.card.bounds.width)
        assertTrue(r.card.side <= minOf(r.screen.width, r.screen.height) - 2 * m.size(SettingsCard.MARGIN))
        assertEquals(1, r.card.list.pageCount)
        assertEquals(7, r.card.shownCount) // the hint is hidden until starting life changes
        assertFalse(r.card.list.hasFooter)
    }

    @Test fun rowsAreHitTestableInEveryRotation() {
        for (s in intArrayOf(2, 4, 6)) {
            val r = Rig(s)
            r.overlay.open(0)
            for (rot in WidgetTesting.rotations) {
                while (r.overlay.rotation != rot) r.overlay.rotate()
                val reg = r.card.region
                assertTrue(reg.size >= 2 + 3 + 4 + 1 + 3 + 1 + 4 + 1)
                for (i in 0 until reg.size) {
                    val h = reg.hitAt(i)
                    val cx = (h.left + h.right) / 2
                    val cy = (h.top + h.bottom) / 2
                    assertEquals("S=$s rot $rot", reg.idAt(i), r.hitLocal(cx, cy))
                }
                // Each row reads in the card's frame: "Starting life" is the first row along the reader's y.
                val life = r.card.frame
                val first = r.row("Starting life").widget.bounds
                val last = r.row("Counters").widget.bounds
                assertTrue(first.top < last.top)
                assertEquals(rot, life.rotation)
            }
        }
    }

    @Test fun rotatedCardIsTheUprightCardTurned() {
        val r = Rig(4)
        r.overlay.open(0)
        val card = r.card
        val side = card.side
        val origin = IRect(0, 0, side, side)
        fun render(rot: Int): RasterCanvas {
            card.layout(origin, rot)
            val c = RasterCanvas(side, side, flat = true)
            SettingsPainter(c).draw(card)
            return c
        }
        val up = render(0)
        for (rot in intArrayOf(90, 180, 270)) assertArrayEquals("rotation $rot", up.rotated(rot), render(rot).px)
    }

    @Test fun rendersInsideItsBoundsAndWritesCaptures() {
        for (s in intArrayOf(2, 4, 6)) {
            val r = Rig(s)
            r.overlay.open(0)
            for (rot in WidgetTesting.rotations) {
                while (r.overlay.rotation != rot) r.overlay.rotate()
                val c = RasterCanvas(r.screen.width, r.screen.height)
                SettingsPainter(c).draw(r.card)
                val b = r.card.bounds
                for (y in 0 until c.height) for (x in 0 until c.width) {
                    if (!b.contains(x, y)) assertEquals("S=$s rot $rot ($x,$y)", RasterCanvas.PAPER, c[x, y])
                }
                // Outline on every edge of the footprint.
                assertEquals(RasterCanvas.INK, c[b.left, (b.top + b.bottom) / 2])
                assertEquals(RasterCanvas.INK, c[b.right - 1, (b.top + b.bottom) / 2])
                c.writePng(File(WidgetTesting.outDir, "settings-s$s-r$rot.png"), s)
            }
        }
    }

    // ---- rows: read / write ------------------------------------------------------------------------

    @Test fun startingLifeIsASegmentedChoiceWrittenToTheGameAndHintedOnce() {
        val r = Rig()
        r.overlay.open(0)
        val row = r.row("Starting life") as ChoiceRow
        val seg = row.segmented
        assertArrayEquals(arrayOf("40", "30", "20"), seg.options)
        assertTrue(seg.numerals)
        assertEquals(0, seg.selected)
        val before = r.overlay.main.rows.filter { it !is HintRow }.map { IRect().also { o -> assertTrue(r.card.rowRect(it, o)) } }
        r.choose("Starting life", 1)
        val after = r.overlay.main.rows.filter { it !is HintRow }.map { IRect().also { o -> assertTrue(r.card.rowRect(it, o)) } }
        assertEquals("the hint never moves a row", before, after)
        assertEquals(30, r.rig.session.state.settings.startingLife)
        assertEquals(1, seg.selected)
        // Applies on the next game: the current lives are untouched; the quiet hint appears at the foot of the page.
        assertTrue(r.rig.session.state.players.all { it.life == 40 })
        assertEquals(8, r.card.shownCount)
        // Last on the page, so no row moves under a finger.
        assertTrue(r.card.shown[7] is HintRow)
        assertEquals("Next game starts at 30", (r.card.shown[7]!!.widget as ListRow).value.toString())
        // Back to 40: no hint.
        r.choose("Starting life", 0)
        assertEquals(40, r.rig.session.state.settings.startingLife)
        assertEquals(7, r.card.shownCount)
        // The game's settings persist with the game (a session change, which Persistence observes).
        r.choose("Starting life", 2)
        assertEquals(20, r.rig.session.state.settings.startingLife)
        r.rig.session.dispatch(au.com.josephmark.dithertracker.core.Action.NewGame, 0)
        assertTrue(r.rig.session.state.players.all { it.life == 20 })
    }

    @Test fun eachAppRowReadsAndWritesItsSetting() {
        val r = Rig()
        r.overlay.open(0)
        r.choose("Scale", 2) // AUTO, 2, 3, 4
        assertEquals(3, r.settings.current.scale)
        assertEquals(2, (r.row("Scale") as ChoiceRow).segmented.selected)
        r.choose("Scale", 0)
        assertEquals(AppSettings.SCALE_AUTO, r.settings.current.scale)

        assertFalse(r.settings.current.inverted)
        r.tapId(r.row("Invert").widget.id)
        assertTrue(r.settings.current.inverted)
        assertTrue((r.row("Invert") as ToggleRow).toggle.checked)

        r.choose("Hold step", 0)
        assertEquals(5, r.settings.current.holdRepeatStep)
        r.choose("Hold step", 2)
        assertEquals(20, r.settings.current.holdRepeatStep)

        assertTrue(r.settings.current.keepScreenAwake)
        r.tapId(r.row("Keep awake").widget.id)
        assertFalse(r.settings.current.keepScreenAwake)

        assertEquals(1, (r.row("Screen clean") as ChoiceRow).segmented.selected) // default 20
        r.choose("Screen clean", 0)
        assertEquals(0, r.settings.current.fullRefreshEvery)
        r.choose("Screen clean", 3)
        assertEquals(100, r.settings.current.fullRefreshEvery)

        // Untouched by the card: haptics, orientation, layout.
        assertEquals(AppSettings().haptics, r.settings.current.haptics)
        assertEquals(AppSettings().orientation, r.settings.current.orientation)
    }

    @Test fun everyChangeGoesThroughTheSettingsHolderOnceAndRetappingIsANoOp() {
        val r = Rig()
        r.overlay.open(0)
        r.choose("Hold step", 0)
        assertEquals(1, r.writes.size)
        r.choose("Hold step", 0) // already selected
        assertEquals(1, r.writes.size)
        r.tapId(r.row("Invert").widget.id)
        r.tapId(r.row("Invert").widget.id)
        assertEquals(3, r.writes.size)
        assertFalse(r.writes.last().inverted)
    }

    @Test fun aValueChangeRedrawsOnlyItsRow() {
        val r = Rig()
        r.overlay.open(0)
        val rowRect = IRect()
        val w = r.row("Hold step")
        r.card.rowRect(w, rowRect)
        val dev = IRect()
        r.l.grid.toDevice(rowRect, dev)
        r.rig.platform.invalidated.clear()
        r.choose("Hold step", 2)
        val last = r.rig.platform.invalidated.last()
        assertEquals(dev, last)
    }

    @Test fun settingsChangedElsewhereAreReReadWhileOpen() {
        val r = Rig()
        r.overlay.open(0)
        r.settings.update(r.settings.current.copy(holdRepeatStep = 20, inverted = true))
        assertEquals(2, (r.row("Hold step") as ChoiceRow).segmented.selected)
        assertTrue((r.row("Invert") as ToggleRow).toggle.checked)
        // A value the card does not offer selects nothing.
        r.settings.update(r.settings.current.copy(holdRepeatStep = 7))
        assertEquals(-1, (r.row("Hold step") as ChoiceRow).segmented.selected)
    }

    @Test fun noAlwaysOnRuleIsOffered() {
        val labels = SettingsTable.main().rows.mapNotNull {
            when (it) {
                is ChoiceRow -> it.label
                is ToggleRow -> it.label
                is LinkRow -> it.label
                else -> null
            }
        }
        assertEquals(listOf("Starting life", "Scale", "Invert", "Hold step", "Keep awake", "Screen clean", "Counters"), labels)
    }

    // ---- scale options -------------------------------------------------------------------------------

    @Test fun scaleOffersOnlyComfortableScales() {
        // Nomad landscape: S = 5 and 6 are not comfortable (PlayLayout's maths).
        assertArrayEquals(intArrayOf(0, 2, 3, 4), SettingsTable.scaleValues(1872, 1404))
        for (s in SettingsTable.scaleValues(1872, 1404)) {
            if (s != 0) assertTrue(PlayLayout.compute(DitherGrid(1872, 1404, s)).comfortable)
        }
        assertFalse(PlayLayout.compute(DitherGrid(1872, 1404, 5)).comfortable)
        // A smaller screen offers fewer; never more than a segmented bar holds.
        val small = SettingsTable.scaleValues(1200, 900)
        assertTrue(small.size < 4 && small[0] == 0)
        assertTrue(SettingsTable.scaleValues(6000, 4500).size <= Segmented.MAX_OPTIONS)
        // The row shows them, AUTO first, numbers in the heavy numerals.
        val r = Rig()
        val seg = (r.overlay.main.rows.first { it is ScaleRow } as ScaleRow).segmented
        assertArrayEquals(arrayOf("AUTO", "2", "3", "4"), seg.options)
        assertTrue(seg.numerals)
        assertSame(seg.optionFont(WidgetMetrics(4), 0), WidgetMetrics(4).ui)
        assertSame(seg.optionFont(WidgetMetrics(4), 1), WidgetMetrics(4).num)
    }

    // ---- counters sub-page ---------------------------------------------------------------------------

    @Test fun countersSubPageTogglesWhichCountersTheDrawerShowsInDefaultOrder() {
        val r = Rig()
        r.overlay.open(0)
        r.tapId(r.row("Counters").widget.id)
        assertEquals(SettingsTable.COUNTERS_TITLE, r.overlay.page.title)
        assertEquals(SettingsTable.COUNTERS_TITLE, r.card.title.toString())
        assertEquals(8, r.card.shownCount)
        assertTrue(r.card.shown[0] is BackRow)
        val toggles = r.overlay.page.rows.filterIsInstance<ToggleRow>()
        assertEquals(7, toggles.size)
        assertEquals(listOf(Icon.POISON, Icon.ENERGY, Icon.EXPERIENCE, Icon.RADS, Icon.STORM, Icon.TICKETS, Icon.GENERIC_COUNTER), toggles.map { it.toggle.icon })
        assertTrue(toggles.all { it.toggle.checked })
        // Hide energy and storm, then bring energy back: order stays the default order.
        r.tapId(r.row("Energy").widget.id)
        r.tapId(r.row("Storm").widget.id)
        assertEquals(listOf(Counter.POISON, Counter.EXPERIENCE, Counter.RAD, Counter.TICKETS, Counter.GENERIC), r.settings.current.drawerCounters)
        assertFalse((r.row("Storm") as ToggleRow).toggle.checked)
        r.tapId(r.row("Energy").widget.id)
        assertEquals(listOf(Counter.POISON, Counter.ENERGY, Counter.EXPERIENCE, Counter.RAD, Counter.TICKETS, Counter.GENERIC), r.settings.current.drawerCounters)
        // Back returns to the main page; close from the sub-page closes the card.
        r.tapId(r.row("Settings").widget.id)
        assertSame(r.overlay.main, r.overlay.page)
        r.tapId(r.row("Counters").widget.id)
        r.tapId(SettingsCard.CLOSE)
        assertFalse(r.overlay.isOpen)
        // Reopening starts on the main page.
        r.overlay.open(0)
        assertSame(r.overlay.main, r.overlay.page)
    }

    @Test fun withCounterKeepsTheFixedOrder() {
        assertEquals(listOf(Counter.POISON, Counter.STORM), SettingsTable.withCounter(listOf(Counter.STORM), Counter.POISON, true))
        assertEquals(emptyList<Counter>(), SettingsTable.withCounter(listOf(Counter.STORM), Counter.STORM, false))
    }

    // ---- extension point and paging ----------------------------------------------------------------

    /** A stage with only the menu and a settings overlay built from [page] (what T11/T13 do via the table). */
    private class CustomRig(page: SettingsPage, s: Int = 4) {
        val platform = StageTest.FakePlatform()
        val settings = SettingsHolder()
        val stage = Stage(GameSession.newGame(4), settings, platform)
        val overlay = stage.add(SettingsOverlay(page))
        val l = PlayLayout.compute(DitherGrid(1872, 1404, s))

        init {
            stage.seal()
            stage.layout(l)
            stage.attach()
        }

        fun tapId(id: Int) {
            val r = IRect()
            assertTrue("id $id", overlay.card.region.hitRect(id, r))
            stage.down(0, (r.left + r.right) / 2, (r.top + r.bottom) / 2, 0)
            stage.up(0)
        }
    }

    @Test fun addedRowsLayOutAndPageWithoutLayoutChanges() {
        // Twenty extra toggles (e.g. what later tasks add): the card keeps its maximum size and pages.
        val extra = Array<SettingsRow>(20) { i ->
            ToggleRow("Extra $i", { it.app.haptics == (i % 2 == 0) }, { s, _ -> s.updateApp(s.app.copy(haptics = !s.app.haptics)) })
        }
        val page = SettingsPage("SETTINGS", SettingsTable.main().rows + extra)
        val r = CustomRig(page)
        r.overlay.open(0)
        val card = r.overlay.card
        val m = WidgetMetrics(4)
        assertEquals(card.maxSide(m, r.l.grid.width, r.l.grid.height), card.side)
        assertTrue(card.list.hasFooter)
        assertEquals(27, card.shownCount) // 7 + 20 (the hint is hidden)
        val pages = card.list.pageCount
        assertTrue(pages >= 3)
        // ‹ is disabled on the first page; › walks to the last; rows there are live.
        assertFalse(card.region.contains(SettingsCard.LIST + PagedList.PREV))
        repeat(pages - 1) { r.tapId(SettingsCard.LIST + PagedList.NEXT) }
        assertEquals(pages - 1, card.list.page)
        assertFalse(card.region.contains(SettingsCard.LIST + PagedList.NEXT))
        val lastRow = extra.last()
        assertTrue(card.region.contains(lastRow.widget.id))
        r.tapId(lastRow.widget.id)
        assertTrue(r.settings.current.haptics)
        r.tapId(SettingsCard.LIST + PagedList.PREV)
        assertEquals(pages - 2, card.list.page)
        // Ids never collide with the card's own and stay inside the zone block.
        assertTrue(page.rows.all { it.widget.id >= SettingsCard.ROW_BASE || it is HintRow })
        assertTrue(page.rows.maxOf { it.widget.id + it.maxIds } <= r.overlay.zonesPerSeat)
    }

    @Test fun drawAndTouchPathsDoNotAllocate() {
        val r = Rig()
        r.overlay.open(0)
        val c = RasterCanvas(r.screen.width, r.screen.height)
        val p = SettingsPainter(c)
        val reg = r.card.region
        val rotate = IRect().also { reg.hitRect(SettingsCard.ROTATE, it) }
        val parts = arrayOf<() -> Unit>(
            { p.draw(r.card) },
            { r.stage.collectNow() },
            { r.stage.hitTest(rotate.left + 2, rotate.top + 2) },
            { r.stage.down(0, rotate.left + 2, rotate.top + 2, 0); r.stage.up(0) }, // rotate a quarter
        )
        for (part in parts) repeat(8) { part() }
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val tid = Thread.currentThread().id
        val runs = 200
        for (i in parts.indices) {
            val before = bean.getThreadAllocatedBytes(tid)
            for (n in 0 until runs) parts[i]()
            val bytes = bean.getThreadAllocatedBytes(tid) - before
            assertTrue("part $i allocated $bytes bytes in $runs runs", bytes < runs * 16L)
        }
    }

    @Test fun detachClosesTheCard() {
        val r = Rig()
        r.overlay.open(0)
        r.stage.detach()
        assertFalse(r.overlay.isOpen)
        assertFalse(r.overlay.cover.active)
    }
}
