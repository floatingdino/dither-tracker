package au.com.josephmark.dithertracker.history

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.LogEntry
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.TextLayout
import au.com.josephmark.dithertracker.widgets.Button
import au.com.josephmark.dithertracker.widgets.ButtonStyle
import au.com.josephmark.dithertracker.widgets.IconButton
import au.com.josephmark.dithertracker.widgets.PagedList
import au.com.josephmark.dithertracker.widgets.TextFit
import au.com.josephmark.dithertracker.widgets.WidgetFrame
import au.com.josephmark.dithertracker.widgets.WidgetMetrics
import au.com.josephmark.dithertracker.widgets.WidgetPainter

/**
 * The history card (T18), pure: layout in its own local frame, placement facing a side
 * ([WidgetFrame], rotation 0/90/180/270), hit rects on the screen, and painting through a
 * [WidgetPainter] (so JVM tests can raster it). No Android types.
 *
 * ```
 * +--------------------------------------+
 * |[rot]::::: HISTORY :::::[undo UNDO][X]|  title bar (TAB dither), rowH tall
 * | (life) Alice               -7 -> 33  |  rows: rowH + 1-px dotted rule
 * | (dead) Bob                beefed it  |
 * | ............ TURN 4 ...............  |  turn divider (only with turn tracking)
 * | (imp)  Cleo -> Bob         +7 -> 21  |
 * | <              2/5                > |  pager footer (only with > 1 page)
 * +--------------------------------------+
 * ```
 *
 * A square ([SIDE] at S = 4, physical size kept per S, shrunk to fit the short screen side), so it
 * has the same footprint whichever way it faces. Rows come from [index] a page at a time: only the
 * visible rows are formatted, into a fixed pool of [HistoryRow]s — a page turn re-formats ≤ 16 rows
 * and allocates nothing.
 */
class HistoryCard {

    val rotate = IconButton(ROTATE, Icon.ROTATE)
    val undo = Button(UNDO, UNDO_LABEL, Icon.UNDO)
    val close = IconButton(CLOSE, Icon.CLOSE, ButtonStyle.FILLED)
    val list = PagedList(LIST)
    val rows: Array<HistoryRow> = Array(PagedList.MAX_SLOTS) { HistoryRow() }
    val index = HistoryIndex()
    val frame = WidgetFrame()

    lateinit var metrics: WidgetMetrics
        private set

    /** Card side, dither px (local and on screen). */
    var side = 0
        private set

    /** Local rects. */
    val card = IRect()
    val titleBar = IRect()
    val titleRect = IRect()
    var titleChars = 0
        private set
    val body = IRect()
    val emptyRect = IRect()
    var emptyChars = 0
        private set

    /** The line shown when there is nothing to list (`Flavour.emptyHistory`, picked by the host). */
    var emptyLine: CharSequence = ""

    /** On-screen footprint of the card. */
    val screenCard = IRect()

    /** Per zone id ([ROTATE]..[NEXT]): screen touch rect, screen feedback rect, live (hit-tested). */
    val screenHit: Array<IRect> = Array(ZONES) { IRect() }
    val screenFeedback: Array<IRect> = Array(ZONES) { IRect() }
    val live = BooleanArray(ZONES)

    var arrow: HistoryArrow = HistoryArrow.heavy(1)
        private set
    var pairArrow: HistoryArrow = HistoryArrow.thin(1)
        private set

    val isEmpty: Boolean get() = index.size == 0

    /**
     * Full layout: metrics for [scale], the card centred in [screen] facing [rotation], rows from
     * [log] (rebuilt only if it is a different object) named from [state], undo enabled per [canUndo].
     */
    fun layout(scale: Int, screen: IRect, rotation: Int, log: List<LogEntry>, state: GameState, canUndo: Boolean) {
        if (!::metrics.isInitialized || metrics.scale != scale) metrics = WidgetMetrics(scale)
        val m = metrics
        arrow = HistoryArrow.heavy(m.k)
        pairArrow = HistoryArrow.thin(m.k)
        side = sideFor(m, screen.width, screen.height)
        frame.setCentred(screen, rotation, side, side)
        frame.map(0, 0, side, side, screenCard)
        val t = m.stroke
        val h = m.rowH
        card.set(0, 0, side, side)
        titleBar.setXywh(t, t, side - 2 * t, h)
        column(m, rotate, titleBar.left)
        column(m, close, titleBar.right - h)
        val uw = undo.naturalWidth(m)
        val ux = close.hit.left - m.gap - uw
        undo.layout(m, ux, titleBar.top + m.inset, uw, h - 2 * m.inset)
        undo.hit.set(ux, titleBar.top, ux + uw, titleBar.bottom)
        undo.enabled = canUndo
        // Title: centred on the card when that clears the buttons, else centred between them.
        val l0 = rotate.hit.right + m.gap
        val r0 = undo.hit.left - m.gap
        titleChars = TextFit.fit(TITLE, m.ui, m.k, maxOf(0, r0 - l0))
        val tw = m.ui.widthOf(titleChars, m.k)
        val tx = ((side - tw) / 2).coerceIn(l0, maxOf(l0, r0 - tw))
        titleRect.set(tx, titleBar.top, tx + tw, titleBar.bottom)
        body.set(t, titleBar.bottom, side - t, side - t)
        index.update(log)
        layoutPage(state, canUndo)
    }

    /**
     * Re-lays the list for the current page and log ([index] already up to date) and refreshes the
     * hit rects. Allocation-free: the page turn path.
     */
    fun layoutPage(state: GameState, canUndo: Boolean) {
        val m = metrics
        undo.enabled = canUndo
        list.count = index.size
        // Whole rows only, centred in the body: the leftover px sit evenly above and below the list
        // rather than as a gap over the pager.
        val pitch = m.rowH + 1
        val listH = maxOf(m.rowH, ((body.height + 1) / pitch) * pitch - 1)
        list.layout(m, body.left, body.top + (body.height - listH) / 2, body.width, listH)
        for (i in 0 until list.visibleCount) {
            index.fill(list.firstIndex + i, state, rows[i])
            rows[i].layout(m, list.slots[i], arrow.width, arrow.height, pairArrow.width, pairArrow.height)
        }
        emptyChars = TextFit.fit(emptyLine, m.ui, m.k, body.width - 2 * m.padX)
        emptyRect.set(body.left, body.top, body.right, body.bottom)
        collect()
    }

    /** Applies a tap on the pager ([PREV]/[NEXT]); true when the page changed (then re-layout). */
    fun turnPage(id: Int, state: GameState, canUndo: Boolean): Boolean {
        if (!list.onTap(id - LIST)) return false
        layoutPage(state, canUndo)
        return true
    }

    /** Rebuilds rows when [log] changed (live update); keeps the page (clamped). True when rebuilt. */
    fun refresh(log: List<LogEntry>, state: GameState, canUndo: Boolean): Boolean {
        val rebuilt = index.update(log)
        layoutPage(state, canUndo)
        return rebuilt
    }

    /** Screen footprint of a local rect. */
    fun toScreen(local: IRect, out: IRect): IRect = frame.map(local, out)

    private fun collect() {
        set(ROTATE, rotate.hit, rotate.bounds, true)
        set(UNDO, undo.hit, undo.bounds, undo.enabled)
        set(CLOSE, close.hit, close.bounds, true)
        val footer = list.hasFooter
        set(PREV, list.prev.hit, list.prev.bounds, footer && list.prev.enabled)
        set(NEXT, list.next.hit, list.next.bounds, footer && list.next.enabled)
    }

    private fun set(id: Int, hit: IRect, fb: IRect, on: Boolean) {
        live[id] = on
        frame.map(hit, screenHit[id])
        frame.map(fb, screenFeedback[id])
    }

    /** A title-bar button: a square inset in a fingertip column [rowH] wide at local x. */
    private fun column(m: WidgetMetrics, b: Button, x: Int) {
        val h = m.rowH
        val s = h - 2 * m.inset
        b.layout(m, x + m.inset, titleBar.top + m.inset, s, s)
        b.hit.setXywh(x, titleBar.top, h, h)
    }

    // ---- painting --------------------------------------------------------------------------------

    /** Paints the whole card (it owns every pixel of [screenCard]). */
    fun paint(p: WidgetPainter) {
        val f = frame
        val m = metrics
        p.paper(f, card)
        p.outline(f, card, m.stroke)
        p.fill(f, Role.TAB, titleBar)
        if (titleChars > 0) p.text(f, m, m.ui, TITLE, 0, titleChars, titleRect, TextLayout.START, invert = false, opaque = true)
        p.draw(f, rotate)
        p.draw(f, undo)
        p.draw(f, close)
        if (isEmpty) {
            p.paper(f, body)
            if (emptyChars > 0) p.text(f, m, m.ui, emptyLine, 0, emptyChars, emptyRect, TextLayout.CENTRE, invert = false, opaque = false)
            return
        }
        p.draw(f, list)
        for (i in 0 until list.visibleCount) paintRow(p, rows[i])
    }

    private fun paintRow(p: WidgetPainter, r: HistoryRow) {
        val f = frame
        val m = metrics
        val start = TextLayout.START
        if (r.divider) {
            if (!r.ruleLeft.isEmpty) p.dotted(f, r.ruleLeft.left, r.ruleLeft.top, r.ruleLeft.width, 1)
            if (!r.ruleRight.isEmpty) p.dotted(f, r.ruleRight.left, r.ruleRight.top, r.ruleRight.width, 1)
            p.text(f, m, m.ui, r.who, 0, r.whoChars, r.labelRect, start, invert = false, opaque = false)
            return
        }
        r.icon?.let { p.icon(f, m, it, r.iconRect, invert = false, opaque = false) }
        p.text(f, m, m.ui, r.who, 0, r.whoChars, r.whoRect, start, invert = false, opaque = false)
        val w2 = r.who2
        if (w2 != null && r.who2Chars > 0) {
            blitArrow(p, pairArrow, r.pairArrow)
            p.text(f, m, m.ui, w2, 0, r.who2Chars, r.who2Rect, start, invert = false, opaque = false)
        }
        r.note?.let { p.text(f, m, m.ui, it, 0, r.noteChars, r.noteRect, start, invert = false, opaque = false) }
        if (r.hasDelta) p.text(f, m, m.num, r.deltaText, 0, r.deltaText.length, r.deltaRect, start, invert = false, opaque = false)
        if (r.hasDelta && r.hasValue) blitArrow(p, arrow, r.arrowRect)
        if (r.hasValue) p.text(f, m, m.num, r.valueText, 0, r.valueText.length, r.valueRect, start, invert = false, opaque = false)
    }

    private val tmp = IRect()

    private fun blitArrow(p: WidgetPainter, img: HistoryArrow, local: IRect) {
        if (local.isEmpty) return
        frame.map(local, tmp)
        p.c.blit(img, tmp.left, tmp.top, frame.rotation, invert = false, opaque = false)
    }

    companion object {
        const val TITLE = "HISTORY"
        const val UNDO_LABEL = "UNDO"

        /** Zone ids (local to the card; the overlay maps them to its zones). */
        const val ROTATE = 0
        const val UNDO = 1
        const val CLOSE = 2
        const val LIST = 3
        const val PREV = LIST + PagedList.PREV
        const val NEXT = LIST + PagedList.NEXT
        const val ZONES = NEXT + 1

        /** Design side at S = 4 (the Nomad's 351-px short side less a 10-px margin each way). */
        const val SIDE = 331
        const val MARGIN = 10

        /** The card side for metrics [m] on a [w]×[h] screen: [SIDE] kept physical, never past the short side. */
        fun sideFor(m: WidgetMetrics, w: Int, h: Int): Int = minOf(m.size(SIDE), minOf(w, h) - 2 * m.size(MARGIN))
    }
}
