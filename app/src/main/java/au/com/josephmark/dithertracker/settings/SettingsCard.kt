package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.widgets.ButtonStyle
import au.com.josephmark.dithertracker.widgets.HitRegion
import au.com.josephmark.dithertracker.widgets.IconButton
import au.com.josephmark.dithertracker.widgets.PagedList
import au.com.josephmark.dithertracker.widgets.Panel
import au.com.josephmark.dithertracker.widgets.TextFit
import au.com.josephmark.dithertracker.widgets.WidgetFrame
import au.com.josephmark.dithertracker.widgets.WidgetMetrics

/**
 * Pure layout of the settings card (no Android types; JVM-tested): a **square** card centred on the
 * screen, so it occupies the same footprint whichever of the four sides it faces, drawn through one
 * [WidgetFrame] at rotation 0 / 90 / 180 / 270 (reader at S / E / N / W).
 *
 * Local structure (the reader's frame), outside in:
 * - a 1-stroke ink outline and a [BAND]-wide [Role.TAB] rim (the mock's dithered frame);
 * - a [Panel] (paper, its own outline) whose title bar holds, left to right, the **rotate** button
 *   (paper square with an ink rule on its right), the title centred on a paper backing, and the
 *   **close** button (solid ink square, paper X); an ink rule under the bar;
 * - a [PagedList] filling the rest: one [WidgetMetrics.rowH] row per visible [SettingsRow], dotted
 *   dividers, and a `‹ 1/2 ›` footer only when a page's rows do not fit. Never scrolls.
 *
 * **Size, by maths ([measure]):** the natural side is the larger of the widest row (+ title bar
 * minimum) and the height of the longest page with every row shown (so the card does not jump between
 * pages or when a hint appears), plus the rim; it is capped at the screen's short side less a
 * [MARGIN] each side, which is what makes all four rotations fit. When capped, rows page.
 */
class SettingsCard {

    lateinit var m: WidgetMetrics
        private set

    /** Card side (dither px), local and on screen. */
    var side = 0
        private set

    /** Outline + rim width. */
    var rim = 0
        private set

    val frame = WidgetFrame()

    /** The card in its local frame ((0, 0, side, side)). */
    val local = IRect()

    /** Inside the outline: the rim band (painted [Role.TAB], the panel covers its middle). */
    val band = IRect()

    /** On-screen footprint (identical for every rotation up to the odd pixel). */
    val bounds = IRect()

    val panel = Panel(emptyArray(), title = null, closeId = CLOSE).also {
        val c = it.close!!
        c.style = ButtonStyle.FILLED
    }
    val close: IconButton get() = panel.close!!
    val rotate = IconButton(ROTATE, Icon.ROTATE, ButtonStyle.PLAIN).also { it.backdrop = Role.SURFACE }

    /** Ink rule between the rotate button and the title bar's dither. */
    val rotateRule = IRect()

    /** Ink rule under the title bar. */
    val titleRule = IRect()
    val titleRect = IRect()

    /** Paper behind the title (a [WidgetMetrics.gap] all round its cap band). */
    val titleBack = IRect()
    var title: CharSequence = ""
        private set
    var titleChars = 0
        private set

    val list = PagedList(LIST)

    /** The rows of the current page that are listed (visible), in order, and their count. */
    val shown = arrayOfNulls<SettingsRow>(MAX_ROWS)
    var shownCount = 0
        private set

    /** Live hit rects (screen px) of what is on the card now; rebuilt by every [layout]. */
    val region = HitRegion(MAX_HITS)

    /**
     * Sizes the card for [pages] on a [screenW]×[screenH] dither-px screen with metrics [m] (rows'
     * widgets must exist). Returns [side].
     */
    fun measure(m: WidgetMetrics, screenW: Int, screenH: Int, pages: List<SettingsPage>): Int {
        this.m = m
        var widest = titleBarNatural(m, pages)
        var rows = 1
        for (p in pages) {
            rows = maxOf(rows, p.rows.size)
            for (r in p.rows) widest = maxOf(widest, r.widget.naturalWidth(m))
        }
        rim = m.stroke + m.size(BAND)
        val panelW = 2 * m.stroke + widest
        val panelH = 2 * m.stroke + m.rowH + m.stroke + 2 * m.padY + listHeight(m, rows)
        val natural = maxOf(panelW, panelH) + 2 * rim
        side = minOf(natural, maxSide(m, screenW, screenH))
        local.set(0, 0, side, side)
        band.set(m.stroke, m.stroke, side - m.stroke, side - m.stroke)
        return side
    }

    /** Rows stacked with 1-px dividers, no footer. */
    fun listHeight(m: WidgetMetrics, rows: Int): Int = rows * (m.rowH + 1) - 1

    /** Rotate + close squares and the longest title with a gap each side. */
    private fun titleBarNatural(m: WidgetMetrics, pages: List<SettingsPage>): Int {
        var t = 0
        for (p in pages) t = maxOf(t, p.title.length)
        return 2 * m.rowH + m.stroke + m.textW(t) + 4 * m.gap
    }

    /** Largest side that fits the screen in every rotation. */
    fun maxSide(m: WidgetMetrics, screenW: Int, screenH: Int): Int = minOf(screenW, screenH) - 2 * m.size(MARGIN)

    /** Lists [rows] of [page] that are visible under [s] into [shown]. */
    fun show(page: SettingsPage, s: SettingsAccess) {
        title = page.title
        var n = 0
        for (r in page.rows) if (r.visible(s) && n < MAX_ROWS) shown[n++] = r
        for (i in n until MAX_ROWS) shown[i] = null
        shownCount = n
    }

    /**
     * Places the card centred in [screen] facing [rotation], lays out the title bar, the list (at its
     * current page) and the shown rows, and re-collects [region].
     */
    fun layout(screen: IRect, rotation: Int) {
        val m = m
        frame.setCentred(screen, rotation, side, side)
        frame.map(local, bounds)
        panel.layout(m, rim, rim, side - 2 * rim, side - 2 * rim)
        val bar = panel.titleBar
        val barH = bar.height
        rotate.layout(m, bar.left, bar.top, barH, barH)
        rotateRule.setXywh(rotate.bounds.right, bar.top, m.stroke, barH)
        val tl = rotateRule.right + m.gap
        val tr = close.bounds.left - m.gap
        titleRect.set(tl, bar.top, maxOf(tl, tr), bar.bottom)
        titleChars = TextFit.fit(title, m.ui, m.k, maxOf(0, titleRect.width - 2 * m.gap))
        val tw = m.textW(titleChars) + 2 * m.gap
        val th = m.ui.capHeight * m.k + 2 * m.gap
        titleBack.setXywh(titleRect.left + (titleRect.width - tw) / 2, bar.top + (barH - th) / 2, tw, th)
        val c = panel.content
        titleRule.setXywh(c.left, c.top, c.width, m.stroke)
        val top = titleRule.bottom + m.padY
        list.count = shownCount
        list.layout(m, c.left, top, c.width, maxOf(0, c.bottom - m.padY - top))
        val first = list.firstIndex
        for (i in 0 until list.visibleCount) {
            val s = list.slots[i]
            shown[first + i]!!.widget.layout(m, s.left, s.top, s.width, s.height)
        }
        region.clear()
        rotate.collectHits(region, frame)
        close.collectHits(region, frame)
        list.collectHits(region, frame)
        for (i in 0 until list.visibleCount) shown[first + i]!!.widget.collectHits(region, frame)
    }

    /** The shown row whose widget owns local id [id] on the current list page, or null. */
    fun rowOwning(id: Int): SettingsRow? {
        val first = list.firstIndex
        for (i in 0 until list.visibleCount) {
            val r = shown[first + i]!!
            if (r.widget.owns(id)) return r
        }
        return null
    }

    /** Screen rect of [row]'s widget (if it is on the current list page). */
    fun rowRect(row: SettingsRow, out: IRect): Boolean {
        val first = list.firstIndex
        for (i in 0 until list.visibleCount) {
            if (shown[first + i] === row) {
                frame.map(row.widget.bounds, out)
                return true
            }
        }
        return false
    }

    companion object {
        /** Local ids: the title bar, the list's ‹ › (two ids), then rows from [ROW_BASE]. */
        const val CLOSE = 0
        const val ROTATE = 1
        const val LIST = 2
        const val ROW_BASE = LIST + 2

        /** Rim band (dither px at S = 4, kept physical) and the screen margin each side. */
        const val BAND = 4
        const val MARGIN = 4

        /** Rows a page can list (pages of the list hold up to `PagedList.MAX_SLOTS` each). */
        const val MAX_ROWS = 64
        const val MAX_HITS = 64
    }
}
