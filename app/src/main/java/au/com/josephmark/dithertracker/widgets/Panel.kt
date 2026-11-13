package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Role

/**
 * A framed overlay surface: paper fill, an ink outline ([WidgetMetrics.stroke], 1 dither px at S = 4),
 * an optional title bar ([Role.TAB] dither, the [title] on a paper backing, a [close] icon button at
 * the end) and a vertical stack of [rows] separated by 1-px dotted dividers (inset [WidgetMetrics.padX]
 * from the row edges, like the play screen's dividers).
 *
 * Rows span the panel's inner width (a row pads its own content). With [columnBreak] in
 * `1 until rows.size`, rows `[0, columnBreak)` fill a left column and the rest a right column,
 * split by a dotted vertical rule — a landscape settings layout without scrolling.
 *
 * Layout is in the panel's local frame; place it with [WidgetFrame.setCentred] (facing a seat if
 * need be). [naturalHeight] is the height its rows need; if laid out shorter, rows past the bottom
 * are still placed and [fits] is false — page long content with [PagedList] instead.
 */
class Panel(
    val rows: Array<out Widget>,
    var title: CharSequence? = null,
    closeId: Int = NO_ID,
    var columnBreak: Int = rows.size,
) : Widget(NO_ID) {

    /** The title bar's close button (a [ButtonStyle.PLAIN] icon on the TAB dither), when [closeId] ≥ 0. */
    val close: IconButton? =
        if (closeId >= 0) IconButton(closeId, Icon.CLOSE, ButtonStyle.PLAIN).also { it.backdrop = Role.TAB } else null

    val titleBar = IRect()
    val titleRect = IRect()
    var titleChars = 0
        private set

    /** Inside the outline, below the title bar. */
    val content = IRect()

    /** Divider under row i (empty when row i is the last in its column). */
    val dividers: Array<IRect> = Array(rows.size) { IRect() }

    /** The dotted rule between columns (empty with one column). */
    val columnRule = IRect()

    /** False when the rows needed more height than the panel was given. */
    var fits = true
        private set

    val hasTitleBar: Boolean get() = title != null || close != null

    val columns: Int get() = if (columnBreak in 1 until rows.size) 2 else 1

    /** Width of column [c] for a panel [w] wide. */
    fun columnWidth(m: WidgetMetrics, w: Int, c: Int): Int {
        val inner = w - 2 * m.stroke
        if (columns == 1) return inner
        val left = (inner - 1) / 2
        return if (c == 0) left else inner - 1 - left
    }

    private fun stackHeight(m: WidgetMetrics, from: Int, until: Int, colW: Int): Int {
        var h = 0
        for (i in from until until) h += rows[i].naturalHeight(m, colW)
        return h + maxOf(0, until - from - 1)
    }

    override fun naturalWidth(m: WidgetMetrics): Int {
        var widest = 0
        for (r in rows) widest = maxOf(widest, r.naturalWidth(m))
        return 2 * m.stroke + columns * widest + (columns - 1)
    }

    override fun naturalHeight(m: WidgetMetrics, width: Int): Int {
        val bar = if (hasTitleBar) m.rowH else 0
        val stack = if (columns == 1) {
            stackHeight(m, 0, rows.size, columnWidth(m, width, 0))
        } else {
            maxOf(stackHeight(m, 0, columnBreak, columnWidth(m, width, 0)), stackHeight(m, columnBreak, rows.size, columnWidth(m, width, 1)))
        }
        return 2 * m.stroke + bar + 2 * m.padY + stack
    }

    override fun onLayout(m: WidgetMetrics) {
        val b = bounds
        val t = m.stroke
        var y = b.top + t
        val c = close
        if (hasTitleBar) {
            val barH = m.rowH
            titleBar.setXywh(b.left + t, y, b.width - 2 * t, barH)
            val tx = titleBar.left + m.padX
            var tRight = titleBar.right - m.padX
            if (c != null) {
                c.layout(m, titleBar.right - barH, y, barH, barH)
                tRight = c.bounds.left - m.gap
            }
            titleRect.setXywh(tx, y, maxOf(0, tRight - tx), barH)
            titleChars = title?.let { TextFit.fit(it, m.ui, m.k, titleRect.width) } ?: 0
            y += barH
        } else {
            titleBar.set(0, 0, 0, 0); titleRect.set(0, 0, 0, 0); titleChars = 0
        }
        content.set(b.left + t, y, b.right - t, b.bottom - t)
        val top = y + m.padY
        var bottom = top
        if (columns == 1) {
            bottom = stack(m, 0, rows.size, content.left, top, content.width)
            columnRule.set(0, 0, 0, 0)
        } else {
            val w0 = columnWidth(m, b.width, 0)
            bottom = maxOf(bottom, stack(m, 0, columnBreak, content.left, top, w0))
            bottom = maxOf(bottom, stack(m, columnBreak, rows.size, content.left + w0 + 1, top, columnWidth(m, b.width, 1)))
            columnRule.setXywh(content.left + w0, content.top + m.padY, 1, maxOf(0, content.height - 2 * m.padY))
        }
        fits = bottom + m.padY <= content.bottom
    }

    /** Lays out rows [from, until) as a column at (x, y) [w] wide; returns the bottom. */
    private fun stack(m: WidgetMetrics, from: Int, until: Int, x: Int, y0: Int, w: Int): Int {
        var y = y0
        for (i in from until until) {
            val r = rows[i]
            val h = r.naturalHeight(m, w)
            r.layout(m, x, y, w, h)
            y += h
            // (Statement form on purpose: an if/else whose branches end in different types makes
            // Kotlin box `y` as the expression's value.)
            if (i + 1 < until) {
                dividers[i].setXywh(x + m.padX, y, maxOf(0, w - 2 * m.padX), 1)
                y++
                continue
            }
            dividers[i].set(0, 0, 0, 0)
        }
        return y
    }

    override fun collectHits(region: HitRegion, frame: WidgetFrame) {
        close?.collectHits(region, frame)
        for (r in rows) r.collectHits(region, frame)
    }
}
