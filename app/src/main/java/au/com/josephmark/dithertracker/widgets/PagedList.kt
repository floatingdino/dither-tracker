package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * [count] uniform rows ([WidgetMetrics.rowH] each, dotted dividers between) shown a page at a time —
 * no scrolling anywhere. If all rows fit the rect it is laid out in, there is one page and no footer;
 * otherwise a footer row holds a ‹ prev button, a "2/5" indicator (`ui`) and a › next button, and
 * [perPage] is as many rows as fit above it.
 *
 * Parts: [PREV] (`id`) and [NEXT] (`id + 1`), registered only when there is a page that way (else
 * drawn hatched). Fingertip columns the full footer height; the visible squares are inset.
 *
 * Row content: either the host draws into [slots] (`slots[i]` shows item [firstIndex] + i, for
 * i < [visibleCount]), or pass a [pool] of row widgets (≥ the rows that fit) and a [binder]: each
 * layout binds `pool[i]` to item `firstIndex + i` and lays it out in its slot; the pool rows are
 * drawn and hit-collected with the list.
 *
 * Changing [count] or the page ([setPage]/[onTap]) needs a re-layout (of the containing panel) and
 * a fresh hit collection — both allocation-free.
 */
class PagedList(
    id: Int,
    var count: Int = 0,
    val pool: Array<out Widget>? = null,
    /** Rows of height asked for by [naturalHeight] (plus the footer). */
    var preferredRows: Int = 4,
) : Widget(id) {

    fun interface Binder {
        /** Point [row] at item [index] (e.g. set a [ListRow]'s label). */
        fun bind(row: Widget, index: Int)
    }

    var binder: Binder? = null

    init {
        require(id >= 0) { "PagedList needs an id (it owns id and id + 1)" }
    }

    override val idCount: Int get() = 2

    var page = 0
        private set
    var perPage = 0
        private set
    var pageCount = 1
        private set

    val firstIndex: Int get() = page * perPage
    val visibleCount: Int get() = (count - firstIndex).coerceIn(0, perPage)
    val hasFooter: Boolean get() = pageCount > 1

    val slots: Array<IRect> = Array(MAX_SLOTS) { IRect() }
    val footer = IRect()
    val prev = IconButton(id + PREV, Icon.CHEVRON_LEFT)
    val next = IconButton(id + NEXT, Icon.CHEVRON_RIGHT)
    val indicatorRect = IRect()

    /** "page/pages", 1-based. */
    val indicator = CharBuf(24)

    /** Moves to page [p] (clamped); true when it changed. Re-layout afterwards. */
    fun setPage(p: Int): Boolean {
        val q = p.coerceIn(0, maxOf(0, pageCount - 1))
        if (q == page) return false
        page = q
        updateIndicator()
        return true
    }

    /** Applies a tap on [part] ([PREV]/[NEXT]); true when the page changed. */
    fun onTap(part: Int): Boolean = setPage(if (part == PREV) page - 1 else page + 1)

    override fun naturalWidth(m: WidgetMetrics): Int = 2 * m.rowH + m.textW(5) + 2 * m.padX

    override fun naturalHeight(m: WidgetMetrics, width: Int): Int {
        val rows = maxOf(1, preferredRows)
        val pitch = m.rowH + 1
        val all = rows * pitch - 1
        return if (count <= rows) all else all + pitch
    }

    override fun onLayout(m: WidgetMetrics) {
        val b = bounds
        val pitch = m.rowH + 1
        val cap = minOf(MAX_SLOTS, pool?.size ?: MAX_SLOTS)
        val fitAll = (b.height + 1) / pitch
        if (count <= minOf(fitAll, cap)) {
            perPage = maxOf(1, minOf(fitAll, cap))
            pageCount = 1
            footer.set(0, 0, 0, 0)
        } else {
            perPage = maxOf(1, minOf(cap, (b.height - m.rowH) / pitch))
            pageCount = maxOf(1, (count + perPage - 1) / perPage)
            footer.setXywh(b.left, b.bottom - m.rowH, b.width, m.rowH)
        }
        page = page.coerceIn(0, pageCount - 1)
        updateIndicator()
        for (i in 0 until MAX_SLOTS) {
            if (i < perPage) slots[i].setXywh(b.left, b.top + i * pitch, b.width, m.rowH) else slots[i].set(0, 0, 0, 0)
        }
        if (hasFooter) {
            column(m, prev, footer.left + m.padX - m.inset)
            column(m, next, footer.right - m.padX + m.inset - m.rowH)
            prev.enabled = page > 0
            next.enabled = page < pageCount - 1
            indicatorRect.set(prev.hit.right, footer.top, next.hit.left, footer.bottom)
        } else {
            indicatorRect.set(0, 0, 0, 0)
        }
        val p = pool ?: return
        val bind = binder
        for (i in 0 until visibleCount) {
            if (bind != null) bind.bind(p[i], firstIndex + i)
            val s = slots[i]
            p[i].layout(m, s.left, s.top, s.width, s.height)
        }
    }

    /** A fingertip column [rowH] wide at x in the footer, the button a square inset in it. */
    private fun column(m: WidgetMetrics, b: IconButton, x: Int) {
        val side = m.rowH - 2 * m.inset
        b.layout(m, x + m.inset, footer.top + m.inset, side, side)
        b.hit.setXywh(x, footer.top, m.rowH, m.rowH)
    }

    private fun updateIndicator() {
        indicator.clear().append(page + 1).append('/').append(pageCount)
    }

    override fun collectHits(region: HitRegion, frame: WidgetFrame) {
        if (!enabled) return
        if (hasFooter) {
            prev.collectHits(region, frame)
            next.collectHits(region, frame)
        }
        val p = pool ?: return
        for (i in 0 until visibleCount) p[i].collectHits(region, frame)
    }

    companion object {
        const val PREV = 0
        const val NEXT = 1
        const val MAX_SLOTS = 16
    }
}
