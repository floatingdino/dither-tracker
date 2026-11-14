package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.layout.Arrangement
import au.com.josephmark.dithertracker.layout.Arrangements
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.seat.SeatMap
import au.com.josephmark.dithertracker.type.TextLayout
import au.com.josephmark.dithertracker.widgets.CharBuf
import au.com.josephmark.dithertracker.widgets.HitRegion
import au.com.josephmark.dithertracker.widgets.TextFit
import au.com.josephmark.dithertracker.widgets.Widget
import au.com.josephmark.dithertracker.widgets.WidgetFrame
import au.com.josephmark.dithertracker.widgets.WidgetMetrics

/**
 * The layout picker (T53): the page the Players row's › opens — one thumbnail per arrangement for the
 * player count chosen there, the current one selected, a tap choosing it. Drawn by [LayoutThumbPainter].
 *
 * **When a choice applies.** It is stored per count (`AppSettings.layout`, an [Arrangements] spec). For
 * the count the table is playing it applies at once: the screen re-lays out with the same players in
 * the same clockwise order (seat i stays seat i; only where the tiles sit changes), like a scale
 * change — nothing is lost, and it is how you fix a table that is sitting the wrong way. For another
 * count (the Players row set to the next game's) it waits, like the count itself, for NEW.
 *
 * **What is offered.** Every arrangement for the count; one whose tiles are not comfortable at the
 * scale the table uses now is shown hatched and cannot be picked, its label naming the largest scale
 * at which it would work (Settings → Scale).
 */
class LayoutPickerRow(private val title: CharBuf) : SettingsRow() {

    val grid: LayoutGrid get() = widget as LayoutGrid

    override val maxIds: Int get() = LayoutGrid.MAX

    override val fills: Boolean get() = true

    private var screen: DitherGrid? = null
    private var builtFor: DitherGrid? = null
    private var builtCount = -1

    override fun build(id: Int): Widget = LayoutGrid(id)

    override fun onScreen(grid: DitherGrid) {
        screen = grid
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
            val label = gw.labels[i].clear()
            for (c in a.label) label.append(c)
            if (!l.comfortable) {
                var at = 0
                for (sc in g.scale - 1 downTo DitherGrid.MIN_SCALE) {
                    if (PlayLayout.compute(DitherGrid(g.deviceWidth, g.deviceHeight, sc), n, a.id).comfortable) { at = sc; break }
                }
                if (at > 0) { for (c in NEEDS_SCALE) label.append(c); label.append(at); label.append(')') } else for (c in TOO_SMALL) label.append(c)
            }
        }
        gw.mapsFor = -1 // the thumbnails' lattices are rebuilt at the next layout
    }

    override fun tap(part: Int, s: SettingsAccess): Int {
        val gw = grid
        if (part !in 0 until gw.count || !gw.enabledAt[part]) return Tap.NONE
        val a = gw.items[part] ?: return Tap.NONE
        val n = s.app.playerCount
        if (Arrangements.of(n, s.app.layout) === a) return Tap.NONE
        s.updateApp(s.app.copy(layout = Arrangements.choose(s.app.layout, n, a.id)))
        return Tap.CHANGED
    }

    companion object {
        const val TITLE = "LAYOUT: "
        const val TITLE_END = " PLAYERS"
        const val NEEDS_SCALE = " (scale "
        const val TOO_SMALL = " (too small)"

        /** The page's title buffer: long enough for "LAYOUT: 6 PLAYERS". */
        fun titleBuffer(): CharBuf = CharBuf(TITLE.length + 2 + TITLE_END.length).also { b ->
            for (c in TITLE) b.append(c); b.append(4); for (c in TITLE_END) b.append(c)
        }
    }
}

/**
 * The picker's body: up to [MAX] cells in a grid filling the rest of the card (two columns for up to
 * four, else three), each a square thumbnail box over a one-line label. A cell is one fingertip part
 * (`id + i`, touch and feedback = the whole cell) unless its arrangement is not offered.
 *
 * Thumbnails are miniatures of the real play layout ([SeatMap] over its panels plus the screen's own
 * rect, so equal tiles get equal cells and the paper margins show), in **screen orientation** like the
 * seat glyphs: whichever side the card faces, the map lies the way the table does.
 */
class LayoutGrid(id: Int) : Widget(id) {

    override val idCount: Int get() = MAX

    var count = 0
    var selected = -1
    val items = arrayOfNulls<Arrangement>(MAX)
    val layouts = arrayOfNulls<PlayLayout>(MAX)
    val enabledAt = BooleanArray(MAX)
    val labels: Array<CharBuf> = Array(MAX) { CharBuf(LABEL_CHARS) }
    val labelChars = IntArray(MAX)

    /** Each cell (local): touch and feedback. */
    val cells: Array<IRect> = Array(MAX) { IRect() }

    /** The square thumbnail box in each cell (local), and the label line under it. */
    val boxes: Array<IRect> = Array(MAX) { IRect() }
    val labelRects: Array<IRect> = Array(MAX) { IRect() }

    /** The miniature per cell, built for box side [mapsFor] (screen orientation). */
    val maps = arrayOfNulls<SeatMap>(MAX)
    var mapsFor = -1

    override fun naturalWidth(m: WidgetMetrics): Int = 2 * (4 * m.target)

    override fun naturalHeight(m: WidgetMetrics, width: Int): Int = width / 2

    fun columns(): Int = if (count <= 4) 2 else 3

    override fun onLayout(m: WidgetMetrics) {
        val b = bounds
        val cols = columns()
        // Always two rows, so a thumbnail is the same size whichever count is shown.
        val rows = maxOf(ROWS, (count + cols - 1) / cols)
        val lh = m.textH + 2 * m.gap
        var side = Int.MAX_VALUE
        for (i in 0 until MAX) {
            if (i >= count) { cells[i].set(0, 0, 0, 0); boxes[i].set(0, 0, 0, 0); labelRects[i].set(0, 0, 0, 0); continue }
            val c = i % cols
            val r = i / cols
            val x0 = b.left + b.width * c / cols
            val x1 = b.left + b.width * (c + 1) / cols
            val y0 = b.top + b.height * r / rows
            val y1 = b.top + b.height * (r + 1) / rows
            cells[i].set(x0, y0, x1, y1)
            side = minOf(side, x1 - x0 - 2 * m.padX - 2 * (SEL + 1), y1 - y0 - lh - 3 * m.gap - 2 * (SEL + 1))
        }
        side = maxOf(SeatMap.MIN_SIZE, side)
        for (i in 0 until count) {
            val cell = cells[i]
            val bx = cell.left + (cell.width - side) / 2
            val by = cell.top + m.gap + SEL + 1
            boxes[i].setXywh(bx, by, side, side)
            val ly = by + side + SEL + 1 + m.gap
            labelChars[i] = TextFit.fit(labels[i], m.ui, m.k, maxOf(0, cell.width - 4 * m.gap))
            // The label's box hugs its text (a gap each side): the selected one is an ink chip, like a segment.
            val tw = minOf(cell.width - 2 * m.gap, m.textW(labelChars[i]) + 2 * m.gap)
            labelRects[i].setXywh(cell.left + (cell.width - tw) / 2, ly, tw, minOf(lh, cell.bottom - ly))
        }
        if (mapsFor != side) {
            mapsFor = side
            for (i in 0 until MAX) maps[i] = if (i < count) layouts[i]?.let { mapOf(it, side) } else null
        }
    }

    override fun collectHits(region: HitRegion, frame: WidgetFrame) {
        if (id < 0 || !enabled) return
        for (i in 0 until count) if (enabledAt[i]) region.addLocal(id + i, frame, cells[i], cells[i])
    }

    companion object {
        /** Most arrangements a count has (the table has at most four; room for six). */
        const val MAX = 6
        const val LABEL_CHARS = 32

        /** Rows of cells (at least): two, so thumbnails keep one size across counts. */
        const val ROWS = 2

        /** The selected thumbnail's ink frame stroke, plus a paper px, outside its miniature (the box keeps room for it). */
        const val SEL = 2

        /** The miniature of [l] (its panels and the screen) fitting a [side]-px box. */
        fun mapOf(l: PlayLayout, side: Int): SeatMap {
            val rects = Array(l.playerCount + 1) { if (it < l.playerCount) l.panels[it] else IRect(0, 0, l.grid.width, l.grid.height) }
            return SeatMap.compute(rects, side)
        }
    }
}

/**
 * Draws a laid-out [LayoutGrid] (T53). Per cell: the miniature table in screen orientation — the
 * screen's outline, every tile outlined on paper (hatched [Role.DISABLED] when not offered), a solid
 * notch on each tile's **near edge** (where its player sits: the drawer tab's edge, so you can read
 * which way everyone faces) and a small ink diamond where the menu diamond is; under it the label.
 * The current choice: an ink frame round the thumbnail and its label inverted (ink box, paper text),
 * like a selected segment. Allocation-free.
 */
class LayoutThumbPainter(private val p: SettingsPainter) {

    private val s = IRect()
    private val r = IRect()

    fun draw(f: WidgetFrame, g: LayoutGrid) {
        val m = g.metrics
        val w = p.widgets
        w.paper(f, g.bounds)
        for (i in 0 until g.count) {
            val map = g.maps[i] ?: continue
            val l = g.layouts[i] ?: continue
            val box = g.boxes[i]
            val sel = i == g.selected
            f.map(box, s)
            // The miniature sits against the box's edge nearest its label (the reader's "down"), so
            // the label stays under the picture whichever way it lies in the square box.
            var ox = s.left + (s.width - map.width) / 2
            var oy = s.top + (s.height - map.height) / 2
            when (f.rotation) {
                0 -> oy = s.bottom - map.height
                90 -> ox = s.left
                180 -> oy = s.top
                else -> ox = s.right - map.width
            }
            // The current choice: an ink frame hugging the miniature, a paper px between.
            if (sel) p.canvas.frame(ox - SEL_OUT, oy - SEL_OUT, map.width + 2 * SEL_OUT, map.height + 2 * SEL_OUT, LayoutGrid.SEL, ink = true)
            thumb(map, l, ox, oy, g.enabledAt[i])
            val lr = g.labelRects[i]
            if (sel) w.ink(f, lr)
            w.text(f, m, m.ui, g.labels[i], 0, g.labelChars[i], lr, TextLayout.CENTRE, invert = sel, opaque = false)
        }
    }

    /** The miniature at screen (ox, oy). */
    private fun thumb(map: SeatMap, l: PlayLayout, ox: Int, oy: Int, offered: Boolean) {
        val c = p.canvas
        val n = l.playerCount
        val cells = map.cells
        // Tiles: paper (or the disabled hatch) inside, then every outline (screen last, on top).
        for (i in 0 until n) {
            val e = cells[i]
            if (!offered) c.fill(Role.DISABLED, ox + e.left + 1, oy + e.top + 1, e.width - 2, e.height - 2)
        }
        for (i in 0..n) {
            val e = cells[i]
            c.frame(ox + e.left, oy + e.top, e.width, e.height, 1, ink = true)
        }
        // Each player's near edge: a notch like the drawer tab, inside the tile against its edge line.
        for (i in 0 until n) {
            val e = cells[i]
            val iw = e.width - 2
            val ih = e.height - 2
            val t = maxOf(2, minOf(iw, ih) / NOTCH_DEPTH)
            when (l.rotation[i]) {
                0 -> { val len = notch(iw); c.rect(ox + e.left + 1 + (iw - len) / 2, oy + e.bottom - 1 - t, len, t, ink = true) }
                180 -> { val len = notch(iw); c.rect(ox + e.left + 1 + (iw - len) / 2, oy + e.top + 1, len, t, ink = true) }
                90 -> { val len = notch(ih); c.rect(ox + e.left + 1, oy + e.top + 1 + (ih - len) / 2, t, len, ink = true) }
                else -> { val len = notch(ih); c.rect(ox + e.right - 1 - t, oy + e.top + 1 + (ih - len) / 2, t, len, ink = true) }
            }
        }
        // The diamond, where the screen's is (linear along each axis, as the lattice places lines).
        val gw = l.grid.width
        val gh = l.grid.height
        val dx = ox + (2 * l.diamondCx * (map.width - 1) + gw) / (2 * gw)
        val dy = oy + (2 * l.diamondCy * (map.height - 1) + gh) / (2 * gh)
        val rad = maxOf(2, map.width / DIAMOND_DIV)
        for (y in -rad..rad) {
            val hw = rad - Math.abs(y)
            c.rect(dx - hw - 1, dy + y, 2 * hw + 3, 1, ink = false) // a paper rim so it reads on the lines
        }
        for (y in -rad..rad) {
            val hw = rad - Math.abs(y)
            c.rect(dx - hw, dy + y, 2 * hw + 1, 1, ink = true)
        }
    }

    /** Notch length along an edge of [inner] px: the tab's share of the tile (89 of 233 at S = 4). */
    private fun notch(inner: Int): Int = maxOf(2, inner * PlayLayout.Chrome.TAB_W / TAB_OF)

    companion object {
        /** Tile width the tab's share is taken from (the 2×2's tile at S = 4). */
        private const val TAB_OF = 233
        private const val NOTCH_DEPTH = 10
        private const val DIAMOND_DIV = 36

        /** The selection frame's outer offset from the miniature (its [LayoutGrid.SEL] stroke plus a paper px). */
        private const val SEL_OUT = LayoutGrid.SEL + 1
    }
}
