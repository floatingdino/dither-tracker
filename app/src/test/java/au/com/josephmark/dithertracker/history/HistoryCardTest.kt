package au.com.josephmark.dithertracker.history

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.LogEntry
import au.com.josephmark.dithertracker.core.LogEvent
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.seat.SeatGlyphs
import au.com.josephmark.dithertracker.widgets.RasterCanvas
import au.com.josephmark.dithertracker.widgets.WidgetMetrics
import au.com.josephmark.dithertracker.widgets.WidgetPainter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCardTest {

    private val longName = "Bartholomew the Unblockable"
    private val state: GameState = GameState.create(4).let { s ->
        s.copy(players = s.players.mapIndexed { i, p -> p.copy(name = if (i == 1) longName else "P${i + 1}name") })
    }

    private fun log(n: Int): List<LogEntry> = List(n) { i ->
        val e = when (i % 4) {
            0 -> LogEvent.LifeChanged(i % 4, -(i + 1), 40 - i)
            1 -> LogEvent.CommanderDamageChanged(1, 3, 0, 7, 21)
            2 -> LogEvent.CounterChanged(1, au.com.josephmark.dithertracker.core.Counter.POISON, 1, 9)
            else -> LogEvent.KnockedOut(1, au.com.josephmark.dithertracker.core.KoReason.COMMANDER_DAMAGE)
        }
        LogEntry(i.toLong(), 0, e)
    }

    private fun screen(s: Int) = DitherGrid(1872, 1404, s).let { IRect().set(0, 0, it.width, it.height) }

    private fun seats(s: Int) = SeatGlyphs.forPanels(PlayLayout.compute(DitherGrid(1872, 1404, s)).panels)

    private fun card(s: Int, rot: Int, n: Int, canUndo: Boolean = true, lg: List<LogEntry> = log(n)) = HistoryCard().apply {
        emptyLine = "Nothing yet. Suspiciously quiet"
        layout(s, screen(s), rot, lg, state, canUndo, seats(s))
    }

    private fun inside(outer: IRect, r: IRect) =
        r.left >= outer.left && r.top >= outer.top && r.right <= outer.right && r.bottom <= outer.bottom

    // ---- paging maths -----------------------------------------------------------------------------

    @Test fun pagingOverZeroOneAndManyEntries() {
        val zero = card(4, 0, 0)
        assertTrue(zero.isEmpty)
        assertEquals(1, zero.list.pageCount)
        assertFalse(zero.list.hasFooter)
        assertFalse(zero.live[HistoryCard.PREV]); assertFalse(zero.live[HistoryCard.NEXT])

        val one = card(4, 0, 1)
        assertEquals(1, one.list.visibleCount)
        assertEquals(1, one.list.pageCount)
        assertFalse(one.list.hasFooter)

        val many = card(4, 0, 300)
        val per = many.list.perPage
        assertEquals(9, per) // S = 4: 9 rows + the pager in the 331-px card
        assertEquals((300 + per - 1) / per, many.list.pageCount)
        assertTrue(many.list.hasFooter)
        assertEquals("1/${many.list.pageCount}", many.list.indicator.toString())
        assertFalse(many.live[HistoryCard.PREV]); assertTrue(many.live[HistoryCard.NEXT])
        // Newest first: row 0 of page 1 is the last log entry.
        assertEquals(299, many.index.entryAt(many.list.firstIndex))
        assertTrue(many.turnPage(HistoryCard.NEXT, state, true))
        assertEquals("2/${many.list.pageCount}", many.list.indicator.toString())
        assertEquals(299 - per, many.index.entryAt(many.list.firstIndex))
        assertTrue(many.live[HistoryCard.PREV])
        // Last page: partial, NEXT dead, further taps do nothing.
        while (many.turnPage(HistoryCard.NEXT, state, true)) Unit
        assertEquals(300 - per * (many.list.pageCount - 1), many.list.visibleCount)
        assertFalse(many.live[HistoryCard.NEXT])
        assertFalse(many.turnPage(HistoryCard.NEXT, state, true))
        assertTrue(many.turnPage(HistoryCard.PREV, state, true))
    }

    @Test fun exactlyAPageHasNoFooter() {
        val c = card(4, 0, 0)
        val fit = (c.body.height + 1) / (c.metrics.rowH + 1)
        val full = card(4, 0, fit)
        assertEquals(1, full.list.pageCount)
        assertEquals(fit, full.list.visibleCount)
        val over = card(4, 0, fit + 1)
        assertEquals(2, over.list.pageCount)
    }

    // ---- fit -------------------------------------------------------------------------------------

    @Test fun cardFitsEveryRotationAtEveryScale() {
        for (s in 2..6) for (rot in intArrayOf(0, 90, 180, 270)) {
            val c = card(s, rot, 50)
            val scr = screen(s)
            val tag = "S=$s rot=$rot"
            assertTrue(tag, inside(scr, c.screenCard))
            assertEquals(tag, c.side, c.screenCard.width)
            assertEquals(tag, c.side, c.screenCard.height)
            assertTrue("$tag at least 5 rows a page", c.list.perPage >= 5)
            // Every row part sits inside its slot and parts never overlap along the row.
            for (i in 0 until c.list.visibleCount) {
                val r = c.rows[i]
                val slot = c.list.slots[i]
                val parts = arrayOf(r.iconRect, r.seatRect, r.pairArrow, r.seat2Rect, r.whoRect, r.noteRect, r.deltaRect, r.arrowRect, r.valueRect)
                var right = Int.MIN_VALUE
                for (p in parts) {
                    if (p.isEmpty) continue
                    assertTrue("$tag row $i $p in $slot", inside(slot, p))
                    assertTrue("$tag row $i parts overlap at $p", p.left >= right)
                    right = p.right
                }
            }
            // Painting touches only the card, and all of it.
            val canvas = RasterCanvas(scr.width, scr.height)
            canvas.px.fill(SENTINEL)
            c.paint(WidgetPainter(canvas))
            for (y in 0 until scr.height) for (x in 0 until scr.width) {
                val inCard = c.screenCard.contains(x, y)
                val v = canvas[x, y]
                if (inCard) assertTrue("$tag unpainted ($x,$y)", v != SENTINEL) else assertEquals("$tag painted outside ($x,$y)", SENTINEL, v)
            }
        }
    }

    @Test fun rotatedCardIsTheImageOfTheUprightOne_exceptTheSeatGlyphs() {
        // At 180° the card is the exact image of the upright one (flat fills: phase-independent) —
        // except inside the seat glyph slots, which keep the table's own orientation (T44).
        val s = 4
        val scr = screen(s)
        val upCard = card(s, 0, 30)
        val downCard = card(s, 180, 30)
        val up = RasterCanvas(scr.width, scr.height, flat = true).also { upCard.paint(WidgetPainter(it)) }
        val down = RasterCanvas(scr.width, scr.height, flat = true).also { downCard.paint(WidgetPainter(it)) }
        val glyphSlots = ArrayList<IRect>()
        for (i in 0 until upCard.list.visibleCount) for (r in arrayOf(upCard.rows[i].seatRect, upCard.rows[i].seat2Rect)) {
            if (!r.isEmpty) glyphSlots += upCard.toScreen(r, IRect())
        }
        assertTrue(glyphSlots.isNotEmpty())
        var differ = 0
        for (y in 0 until scr.height) for (x in 0 until scr.width) {
            if (glyphSlots.any { it.contains(x, y) }) { if (up[x, y] != down[scr.width - 1 - x, scr.height - 1 - y]) differ++; continue }
            assertEquals("($x,$y)", up[x, y], down[scr.width - 1 - x, scr.height - 1 - y])
        }
        assertTrue("seat glyphs are not simply turned with the card", differ > 0)
    }

    @Test fun rowsNeverWrapAndNumbersAreNeverCut() {
        for (sc in 2..6) {
            val c = card(sc, 0, 4)
            val m = c.metrics
            for (i in 0 until c.list.visibleCount) {
                val r = c.rows[i]
                val slot = c.list.slots[i]
                if (!r.whoRect.isEmpty) assertEquals(slot.height, r.whoRect.height)
                if (r.hasValue) assertEquals(m.num.widthOf(r.valueText.length, m.k), r.valueRect.width)
                if (r.hasDelta) assertEquals(m.num.widthOf(r.deltaText.length, m.k), r.deltaRect.width)
                // With names gone a player row always has room for its whole note.
                r.note?.let { assertEquals("S=$sc $r", it.length, r.noteChars) }
            }
        }
    }

    // ---- seat glyphs (T44) ---------------------------------------------------------------------------

    /** Painted pixels of a row's glyph, read back off the screen raster in screen orientation. */
    private fun painted(c: HistoryCard, canvas: RasterCanvas, seat: Int, slot: IRect): String {
        val g = c.toScreen(c.seatGlyphRect(seat, slot, IRect()), IRect())
        val k = c.metrics.k
        return buildString {
            for (y in g.top until g.bottom step k) {
                for (x in g.left until g.right step k) append(if (canvas[x, y] == RasterCanvas.INK) '#' else '.')
                append('\n')
            }
        }
    }

    @Test fun seatGlyphsArePaintedInScreenOrientationAtEveryScaleAndRotation() {
        val lg = listOf(
            LogEntry(0, 0, LogEvent.LifeChanged(2, -7, 33)),
            LogEntry(1, 0, LogEvent.CommanderDamageChanged(1, 3, 0, 7, 7)),
            LogEntry(2, 0, LogEvent.LifeChanged(9, -1, 39)), // no longer seated
            LogEntry(3, 0, LogEvent.DayNightChanged(au.com.josephmark.dithertracker.core.DayNight.NIGHT)),
        )
        for (sc in 2..6) for (rot in intArrayOf(0, 90, 180, 270)) {
            val c = card(sc, rot, 0, lg = lg)
            val map = seats(sc).map(SeatGlyphs.ICON)
            val scr = screen(sc)
            val canvas = RasterCanvas(scr.width, scr.height)
            c.paint(WidgetPainter(canvas))
            val tag = "S=$sc rot=$rot"
            // Newest first: night (no glyph), removed player (empty table), commander 3 > 1, life on seat 2.
            val night = c.rows[0]; val gone = c.rows[1]; val cmd = c.rows[2]; val life = c.rows[3]
            assertFalse(tag, night.hasSeat)
            assertEquals(tag, map.ascii(SeatGlyphs.EMPTY), painted(c, canvas, gone.seat, gone.seatRect))
            assertEquals(tag, map.ascii(3), painted(c, canvas, cmd.seat, cmd.seatRect))
            assertEquals(tag, map.ascii(1), painted(c, canvas, cmd.seat2, cmd.seat2Rect))
            assertFalse(tag, cmd.pairArrow.isEmpty)
            assertEquals(tag, map.ascii(2), painted(c, canvas, life.seat, life.seatRect))
            // Glyph slots are icon-sized squares right after the kind icon; the glyph sits inside.
            for (r in arrayOf(gone, cmd, life)) {
                assertEquals(tag, c.metrics.icon, r.seatRect.width)
                assertEquals(tag, r.iconRect.right + c.metrics.gap, r.seatRect.left)
                val g = c.seatGlyphRect(r.seat, r.seatRect, IRect())
                assertTrue(tag, inside(r.seatRect, g))
            }
            // No row shows a name: the label rect is only used by player-less rows.
            for (r in arrayOf(gone, cmd, life)) assertTrue(tag, r.whoRect.isEmpty)
            assertFalse(tag, night.whoRect.isEmpty)
        }
    }

    // ---- hit-testing -----------------------------------------------------------------------------

    @Test fun hitRectsFollowTheRotation() {
        for (rot in intArrayOf(0, 90, 180, 270)) {
            val c = card(4, rot, 100)
            val tag = "rot=$rot"
            for (id in 0 until HistoryCard.ZONES) {
                if (!c.live[id]) continue
                assertTrue("$tag $id", inside(c.screenCard, c.screenHit[id]))
                assertTrue("$tag $id feedback inside hit", inside(c.screenHit[id], c.screenFeedback[id]))
                assertTrue("$tag $id ≥ fingertip", minOf(c.screenHit[id].width, c.screenHit[id].height) >= c.metrics.target)
            }
            // In the reader's frame the rotate button is top-left and close top-right; map their
            // centres back to local coordinates to check.
            val rc = c.screenHit[HistoryCard.ROTATE]
            val cc = c.screenHit[HistoryCard.CLOSE]
            val f = c.frame
            val rx = f.localX((rc.left + rc.right) / 2, (rc.top + rc.bottom) / 2)
            val ry = f.localY((rc.left + rc.right) / 2, (rc.top + rc.bottom) / 2)
            val cx = f.localX((cc.left + cc.right) / 2, (cc.top + cc.bottom) / 2)
            assertTrue("$tag rotate at local ($rx,$ry)", rx < c.side / 4 && ry < c.metrics.rowH)
            assertTrue("$tag close at local x $cx", cx > c.side * 3 / 4)
            // Hit rects of different buttons never overlap.
            for (a in 0 until HistoryCard.ZONES) for (b in a + 1 until HistoryCard.ZONES) {
                if (!c.live[a] || !c.live[b]) continue
                val ra = c.screenHit[a]; val rb = c.screenHit[b]
                val overlap = ra.left < rb.right && rb.left < ra.right && ra.top < rb.bottom && rb.top < ra.bottom
                assertFalse("$tag $a/$b overlap", overlap)
            }
        }
    }

    @Test fun undoIsLiveOnlyWhenThereIsSomethingToUndo() {
        assertFalse(card(4, 0, 3, canUndo = false).live[HistoryCard.UNDO])
        assertTrue(card(4, 0, 3, canUndo = true).live[HistoryCard.UNDO])
    }

    // ---- empty state -----------------------------------------------------------------------------

    @Test fun emptyStateShowsTheLineCentred() {
        for (s in 2..6) {
            val c = card(s, 0, 0)
            assertEquals(c.emptyLine.length, c.emptyChars)
            val m = WidgetMetrics(s)
            val w = m.ui.widthOf(c.emptyChars, m.k)
            assertTrue(w <= c.body.width)
            val canvas = RasterCanvas(screen(s).width, screen(s).height)
            c.paint(WidgetPainter(canvas))
            // Some ink in the body (the line), and it is centred: ink spans symmetric about the middle.
            var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
            val b = IRect(); c.toScreen(c.body, b)
            for (y in b.top + 1 until b.bottom - 1) for (x in b.left + 1 until b.right - 1) {
                if (canvas[x, y] == RasterCanvas.INK) { minX = minOf(minX, x); maxX = maxOf(maxX, x) }
            }
            assertTrue("S=$s ink", minX <= maxX)
            val mid = (b.left + b.right) / 2
            assertTrue("S=$s centred: $minX..$maxX about $mid", kotlin.math.abs((minX + maxX) / 2 - mid) <= 3 * m.k)
        }
    }

    @Test fun pageTurnsDoNotAllocateMuch() {
        val c = card(4, 0, 500)
        val st = state
        // Warm up.
        repeat(3) { c.turnPage(HistoryCard.NEXT, st, true) }
        val rt = Runtime.getRuntime()
        val bean = java.lang.management.ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        val before = bean?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0
        repeat(40) { c.turnPage(HistoryCard.NEXT, st, true) }
        repeat(40) { c.turnPage(HistoryCard.PREV, st, true) }
        val after = bean?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0
        if (bean != null) assertTrue("allocated ${after - before} bytes over 80 page turns", after - before < 4096)
        assertTrue(rt.totalMemory() > 0)
    }

    private companion object {
        const val SENTINEL = 7
    }
}
