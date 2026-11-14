package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.layout.Arrangement
import au.com.josephmark.dithertracker.layout.Arrangements
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.type.TextLayout
import au.com.josephmark.dithertracker.widgets.CharBuf
import au.com.josephmark.dithertracker.widgets.HitRegion
import au.com.josephmark.dithertracker.widgets.IconButton
import au.com.josephmark.dithertracker.widgets.TextFit
import au.com.josephmark.dithertracker.widgets.Widget
import au.com.josephmark.dithertracker.widgets.WidgetFrame
import au.com.josephmark.dithertracker.widgets.WidgetMetrics

/**
 * The layout picker (T53, redesigned in T60): the page the Players row's › opens — a wordless
 * thumbnail per arrangement for the player count chosen there, the current one selected, a tap
 * choosing it; paged `‹ n/m ›` when a count has more than a page. Drawn by [LayoutThumbPainter].
 *
 * **When a choice applies.** It is stored per count (`AppSettings.layout`, an [Arrangements] spec). For
 * the count the table is playing it applies at once: the screen re-lays out with the same players in
 * the same clockwise order — nothing is lost. For another count it waits, like the count, for NEW.
 *
 * **What is offered.** Every arrangement for the count. One that is not comfortable at the table's
 * scale is drawn with dotted seat outlines (wordless); tapping it does not choose it but writes the reason on the hint
 * line at the foot of the page ("Needs scale 3 (Settings > Scale)" / "Too small at any scale").
 */
class LayoutPickerRow(private val title: CharBuf) : SettingsRow() {

    val grid: LayoutGrid get() = widget as LayoutGrid

    override val maxIds: Int get() = LayoutGrid.IDS

    override val fills: Boolean get() = true

    private var screen: DitherGrid? = null
    private var builtFor: DitherGrid? = null
    private var builtCount = -1

    override fun build(id: Int): Widget = LayoutGrid(id)

    override fun onScreen(grid: DitherGrid) {
        screen = grid
    }

    override fun onOpen(s: SettingsAccess) {
        grid.hint.clear()
        grid.setPage(-1) // to the selected one's page at the next bind
    }

    override fun bind(s: SettingsAccess) {
        val n = s.app.playerCount
        writeTitle(n)
        val g = screen
        if (g != null && (g !== builtFor || n != builtCount)) rebuild(g, n)
        val pick = Arrangements.of(n, s.app.layout)
        val gw = grid
        gw.selected = -1
        for (i in 0 until gw.count) if (gw.items[i] === pick) gw.selected = i
        if (gw.page < 0) gw.setPage(if (gw.selected >= 0) gw.selected / LayoutGrid.PER_PAGE else 0)
    }

    private fun writeTitle(n: Int) {
        title.clear()
        for (c in TITLE) title.append(c)
        title.append(n)
        for (c in TITLE_END) title.append(c)
    }

    /** The candidates for [n] seats on [g] (allocates: once per page open or count change). */
    private fun rebuild(g: DitherGrid, n: Int) {
        builtFor = g
        builtCount = n
        val gw = grid
        val cs = Arrangements.candidates(n)
        gw.count = minOf(cs.size, LayoutGrid.MAX)
        for (i in 0 until gw.count) {
            val a = cs[i]
            val l = PlayLayout.compute(g, n, a.id)
            gw.items[i] = a
            gw.layouts[i] = l
            gw.enabledAt[i] = l.comfortable
            var at = 0
            if (!l.comfortable) {
                for (sc in g.scale - 1 downTo DitherGrid.MIN_SCALE) {
                    if (PlayLayout.compute(DitherGrid(g.deviceWidth, g.deviceHeight, sc), n, a.id).comfortable) { at = sc; break }
                }
            }
            gw.needsScale[i] = at
        }
        gw.hint.clear()
        gw.setPage(gw.page.coerceAtLeast(0))
        gw.thumbsFor = -1 // rebuilt at the next layout
    }

    override fun tap(part: Int, s: SettingsAccess): Int {
        val gw = grid
        if (part == LayoutGrid.PREV || part == LayoutGrid.NEXT) {
            gw.hint.clear()
            return if (gw.setPage(gw.page + if (part == LayoutGrid.PREV) -1 else 1)) Tap.CHANGED else Tap.NONE
        }
        val i = gw.page * LayoutGrid.PER_PAGE + part
        if (part !in 0 until LayoutGrid.PER_PAGE || i >= gw.count) return Tap.NONE
        if (!gw.enabledAt[i]) {
            // Wordless thumbnails: the reason goes on the hint line.
            gw.hint.clear()
            val at = gw.needsScale[i]
            if (at > 0) { for (c in NEEDS_SCALE) gw.hint.append(c); gw.hint.append(at); for (c in NEEDS_SCALE_END) gw.hint.append(c) }
            else for (c in TOO_SMALL) gw.hint.append(c)
            return Tap.CHANGED
        }
        val a = gw.items[i] ?: return Tap.NONE
        val n = s.app.playerCount
        val hadHint = gw.hint.length > 0
        gw.hint.clear()
        if (Arrangements.of(n, s.app.layout) === a) return if (hadHint) Tap.CHANGED else Tap.NONE
        s.updateApp(s.app.copy(layout = Arrangements.choose(s.app.layout, n, a.id)))
        return Tap.CHANGED
    }

    companion object {
        const val TITLE = "LAYOUT: "
        const val TITLE_END = " PLAYERS"
        const val NEEDS_SCALE = "Needs scale "
        const val NEEDS_SCALE_END = " (Settings > Scale)"
        const val TOO_SMALL = "Too small at any scale"

        /** The page's title buffer: long enough for "LAYOUT: 6 PLAYERS". */
        fun titleBuffer(): CharBuf = CharBuf(TITLE.length + 2 + TITLE_END.length).also { b ->
            for (c in TITLE) b.append(c); b.append(4); for (c in TITLE_END) b.append(c)
        }
    }
}

/**
 * The picker's body (T60): [COLS] × [ROWS] cells a page, a hint line, and a `‹ n/m ›` footer when the
 * count has more than [PER_PAGE] arrangements. Each cell is one fingertip part (`id + slot`, touch and
 * feedback the whole cell) holding a square box with the thumbnail; PREV / NEXT are `id + PREV/NEXT`.
 * No names, no notches.
 *
 * A thumbnail is the real play layout in miniature, in **screen orientation** (whichever side the
 * card faces it lies the way the table does): every seat's whole tile scaled down and drawn as an
 * inset block with a paper gap round it; empty cells marked apart; margins
 * paper (T60 tweak: seats are now outlines, empties the dithered ground; see [LayoutThumbPainter]). Seat rects are precomputed per box size ([seatRects], [emptyRects], in thumbnail px) at
 * layout, so drawing allocates nothing.
 */
class LayoutGrid(id: Int) : Widget(id) {

    override val idCount: Int get() = IDS

    var count = 0
    var selected = -1
    val items = arrayOfNulls<Arrangement>(MAX)
    val layouts = arrayOfNulls<PlayLayout>(MAX)
    val enabledAt = BooleanArray(MAX)

    /** Largest scale below the table's at which an unavailable arrangement would fit (0 = none). */
    val needsScale = IntArray(MAX)

    /** The page shown (−1 until the first bind puts it on the selected one's). */
    var page = -1
        private set

    val pages: Int get() = maxOf(1, (count + PER_PAGE - 1) / PER_PAGE)

    /** The hint line (why a hatched one cannot be chosen); empty most of the time. */
    val hint = CharBuf(48)
    var hintChars = 0
        private set
    val hintRect = IRect()

    /** Cells of this page's slots (local): touch and feedback; the square thumbnail box in each. */
    val cells: Array<IRect> = Array(PER_PAGE) { IRect() }
    val boxes: Array<IRect> = Array(PER_PAGE) { IRect() }

    val prev = IconButton(id + PREV, Icon.CHEVRON_LEFT)
    val next = IconButton(id + NEXT, Icon.CHEVRON_RIGHT)
    val footer = IRect()
    val indicator = CharBuf(8)
    val indicatorRect = IRect()
    val hasFooter: Boolean get() = pages > 1

    /**
     * Thumbnail geometry per item for box side [thumbsFor] (screen orientation, px from the
     * miniature's top-left): the miniature's size, its seats' grounds, its empty cells.
     */
    var thumbsFor = -1
    val thumbW = IntArray(MAX)
    val thumbH = IntArray(MAX)
    val seatRects: Array<Array<IRect>> = Array(MAX) { Array(6) { IRect() } }
    val seatRot: Array<IntArray> = Array(MAX) { IntArray(6) }
    val seatCount = IntArray(MAX)
    val emptyRects: Array<Array<IRect>> = Array(MAX) { Array(6) { IRect() } }
    val emptyCount = IntArray(MAX)

    /** Moves to page [p] (clamped; −1 = "not yet placed"); true when it changed. */
    fun setPage(p: Int): Boolean {
        val q = if (p < 0) -1 else p.coerceIn(0, pages - 1)
        if (q == page) return false
        page = q
        return true
    }

    /** Item shown in [slot] of this page, or −1. */
    fun itemAt(slot: Int): Int {
        val i = maxOf(0, page) * PER_PAGE + slot
        return if (slot in 0 until PER_PAGE && i < count) i else -1
    }

    override fun naturalWidth(m: WidgetMetrics): Int = COLS * m.target * 2

    override fun naturalHeight(m: WidgetMetrics, width: Int): Int = width * 3 / 4

    override fun onLayout(m: WidgetMetrics) {
        val b = bounds
        // Foot: the hint line, then (when paged) the ‹ n/m › row.
        val hintH = m.textH + 2 * m.gap
        var bottom = b.bottom
        indicator.clear().append(maxOf(0, page) + 1).append('/').append(pages)
        if (hasFooter) {
            footer.set(b.left, b.bottom - m.rowH, b.right, b.bottom)
            bottom = footer.top - 1
            val side = m.rowH - 2 * m.inset
            prev.layout(m, footer.left + m.padX, footer.top + m.inset, side, side)
            prev.hit.set(footer.left, footer.top, footer.left + m.rowH + m.padX, footer.bottom)
            next.layout(m, footer.right - m.padX - side, footer.top + m.inset, side, side)
            next.hit.set(footer.right - m.rowH - m.padX, footer.top, footer.right, footer.bottom)
            prev.enabled = page > 0
            next.enabled = page < pages - 1
            indicatorRect.set(prev.hit.right, footer.top, next.hit.left, footer.bottom)
        } else {
            footer.set(0, 0, 0, 0); indicatorRect.set(0, 0, 0, 0)
        }
        hintRect.set(b.left + m.padX, bottom - hintH, b.right - m.padX, bottom)
        hintChars = TextFit.fit(hint, m.ui, m.k, maxOf(0, hintRect.width))
        val gridBottom = hintRect.top
        var side = Int.MAX_VALUE
        for (slot in 0 until PER_PAGE) {
            val c = slot % COLS
            val r = slot / COLS
            val x0 = b.left + b.width * c / COLS
            val x1 = b.left + b.width * (c + 1) / COLS
            val y0 = b.top + (gridBottom - b.top) * r / ROWS
            val y1 = b.top + (gridBottom - b.top) * (r + 1) / ROWS
            cells[slot].set(x0, y0, x1, y1)
            side = minOf(side, x1 - x0 - 2 * PAD, y1 - y0 - 2 * PAD)
        }
        side = maxOf(4, side)
        for (slot in 0 until PER_PAGE) {
            val cell = cells[slot]
            boxes[slot].setXywh(cell.left + (cell.width - side) / 2, cell.top + (cell.height - side) / 2, side, side)
        }
        if (thumbsFor != side) {
            thumbsFor = side
            for (i in 0 until count) layouts[i]?.let { thumb(i, it, side - 2 * (SEL + 2)) }
        }
    }

    /** The miniature of [l] for item [i], fitting a [box]-px square (the selection frame keeps room round it). */
    private fun thumb(i: Int, l: PlayLayout, box: Int) {
        val gw = l.grid.width
        val gh = l.grid.height
        val tw: Int
        val th: Int
        if (gw >= gh) { tw = box; th = maxOf(1, box * gh / gw) } else { th = box; tw = maxOf(1, box * gw / gh) }
        thumbW[i] = tw
        thumbH[i] = th
        fun x(v: Int) = v * tw / gw
        fun y(v: Int) = v * th / gh
        val n = minOf(l.playerCount, 6)
        seatCount[i] = n
        for (s in 0 until n) {
            val t = l.seats[s]
            // Inset a px each side: neighbours get a 2-px paper gap, a seat at the edge 1 px from it.
            seatRects[i][s].set(x(t.left) + 1, y(t.top) + 1, x(t.right) - 1, y(t.bottom) - 1)
            seatRot[i][s] = l.rotation[s]
        }
        val e = minOf(l.empties.size, 6)
        emptyCount[i] = e
        for (k in 0 until e) {
            val t = l.empties[k]
            emptyRects[i][k].set(x(t.left) + 1, y(t.top) + 1, x(t.right) - 1, y(t.bottom) - 1)
        }
    }

    override fun collectHits(region: HitRegion, frame: WidgetFrame) {
        if (id < 0 || !enabled) return
        for (slot in 0 until PER_PAGE) if (itemAt(slot) >= 0) region.addLocal(id + slot, frame, cells[slot], cells[slot])
        if (hasFooter) {
            if (prev.enabled) region.addLocal(id + PREV, frame, prev.hit, prev.bounds)
            if (next.enabled) region.addLocal(id + NEXT, frame, next.hit, next.bounds)
        }
    }

    companion object {
        /** A page: four columns of three (the most any count has today is 12). */
        const val COLS = 4
        const val ROWS = 3
        const val PER_PAGE = COLS * ROWS

        /** Arrangements one count may have. */
        const val MAX = 24

        /** Parts: slots `0 until PER_PAGE`, then the footer's ‹ and ›. */
        const val PREV = PER_PAGE
        const val NEXT = PER_PAGE + 1
        const val IDS = PER_PAGE + 2

        /** Paper round a box inside its cell; the selection frame's stroke. */
        const val PAD = 2
        const val SEL = 2
    }
}

/**
 * Draws a laid-out [LayoutGrid] (T60; the owner's tweak: "border for seat, dither for empty"). Per
 * thumbnail: each seat an inset **ink outline on paper**, paper gaps between; an **empty cell** a
 * block of the play screen's own empty ground ([Role.EMPTY], chunky dots — the same "not a seat"
 * texture the table shows, and unlike anything a seat is drawn with). The current one: an ink frame
 * round the miniature, its seats **solid ink** each with a small paper arrow pointing at the edge its
 * player sits at — the strongest contrast with the outlined others, and the only thumbnail that says
 * which way people face. **Not available at this scale:** seats as **dotted** outlines (broken
 * lines read as "not usable", and stay unfilled so they can't be taken for a dithered empty cell).
 * Then the hint line and the footer. Allocation-free.
 */
class LayoutThumbPainter(private val p: SettingsPainter) {

    private val s = IRect()

    fun draw(f: WidgetFrame, g: LayoutGrid) {
        val m = g.metrics
        val w = p.widgets
        w.paper(f, g.bounds)
        val c = p.canvas
        for (slot in 0 until LayoutGrid.PER_PAGE) {
            val i = g.itemAt(slot)
            if (i < 0 || g.thumbsFor < 0) continue
            f.map(g.boxes[slot], s)
            val tw = g.thumbW[i]
            val th = g.thumbH[i]
            val ox = s.left + (s.width - tw) / 2
            val oy = s.top + (s.height - th) / 2
            val sel = i == g.selected
            if (sel) c.frame(ox - SEL_OUT, oy - SEL_OUT, tw + 2 * SEL_OUT, th + 2 * SEL_OUT, LayoutGrid.SEL, ink = true)
            val offered = g.enabledAt[i]
            for (k in 0 until g.emptyCount[i]) {
                val e = g.emptyRects[i][k]
                if (e.width > 0 && e.height > 0) c.fill(Role.EMPTY, ox + e.left, oy + e.top, e.width, e.height)
            }
            for (k in 0 until g.seatCount[i]) {
                val r = g.seatRects[i][k]
                if (r.width <= 0 || r.height <= 0) continue
                val x = ox + r.left
                val y = oy + r.top
                when {
                    sel -> { c.rect(x, y, r.width, r.height, ink = true); arrow(x, y, r.width, r.height, g.seatRot[i][k]) }
                    offered -> c.frame(x, y, r.width, r.height, 1, ink = true)
                    else -> dottedOutline(x, y, r.width, r.height)
                }
            }
        }
        if (g.hintChars > 0) w.text(f, m, m.ui, g.hint, 0, g.hintChars, g.hintRect, TextLayout.CENTRE, invert = false, opaque = false)
        if (g.hasFooter) {
            w.dotted(f, g.footer.left + m.padX, g.footer.top - 1, g.footer.width - 2 * m.padX, 1)
            w.draw(f, g.prev)
            w.draw(f, g.next)
            w.text(f, m, m.ui, g.indicator, 0, g.indicator.length, g.indicatorRect, TextLayout.CENTRE, invert = false, opaque = false)
        }
    }

    private fun dottedOutline(x: Int, y: Int, w: Int, h: Int) {
        if (w < 2 || h < 2) return
        val r = p.canvas
        r.fill(Role.DOTTED, x, y, w, 1)
        r.fill(Role.DOTTED, x, y + h - 1, w, 1)
        r.fill(Role.DOTTED, x, y, 1, h)
        r.fill(Role.DOTTED, x + w - 1, y, 1, h)
    }

    /**
     * A small paper arrow in a selected seat's ink ground, against the edge its player sits at and
     * pointing at it (rotation 0: the seat's bottom; 180: top; 90: left; 270: right).
     */
    private fun arrow(x: Int, y: Int, w: Int, h: Int, rot: Int) {
        val c = p.canvas
        val a = maxOf(2, minOf(ARROW, minOf(w, h) / 4)) // rows of the arrow (its half-width + 1)
        val inset = maxOf(1, minOf(w, h) / 8)
        for (k in 0 until a) {
            val half = a - 1 - k // the point is the last row, nearest the edge
            when (rot) {
                0 -> c.rect(x + w / 2 - half, y + h - inset - a + k, 2 * half + 1, 1, ink = false)
                180 -> c.rect(x + w / 2 - half, y + inset + a - 1 - k, 2 * half + 1, 1, ink = false)
                90 -> c.rect(x + inset + a - 1 - k, y + h / 2 - half, 1, 2 * half + 1, ink = false)
                else -> c.rect(x + w - inset - a + k, y + h / 2 - half, 1, 2 * half + 1, ink = false)
            }
        }
    }

    companion object {
        private const val SEL_OUT = LayoutGrid.SEL + 1
        private const val ARROW = 4
    }
}
