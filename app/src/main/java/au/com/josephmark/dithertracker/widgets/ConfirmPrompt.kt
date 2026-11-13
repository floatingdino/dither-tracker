package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * "Are you sure?": 1–2 lines of [prompt] (word-wrapped in the `ui` face, see [TextWrap]) over two
 * equal buttons — [cancel] (outline, left) and [confirm] (Dark, right) — in a paper box with an ink
 * outline, sized to its content and centred in the rect it is laid out in ([bounds] is that rect; only
 * [box] is drawn, so it can sit over anything).
 *
 * Parts: [CONFIRM] (`id`) and [CANCEL] (`id + 1`). Box width = the wider of the text block and the two
 * natural-width buttons (+ padding), capped by the rect; text that does not fit two lines is cut at a
 * word ([TextWrap.truncated]).
 */
class ConfirmPrompt(
    id: Int,
    var prompt: CharSequence,
    confirmLabel: CharSequence = "YES",
    cancelLabel: CharSequence = "NO",
) : Widget(id) {

    init {
        require(id >= 0) { "ConfirmPrompt needs an id (it owns id and id + 1)" }
    }

    override val idCount: Int get() = 2

    val confirm = Button(id + CONFIRM, confirmLabel, style = ButtonStyle.FILLED)
    val cancel = Button(id + CANCEL, cancelLabel, style = ButtonStyle.OUTLINE)

    val wrap = TextWrap(MAX_LINES)
    val box = IRect()
    val lineRects: Array<IRect> = Array(MAX_LINES) { IRect() }

    private var boxW = 0
    private var boxH = 0
    private var contentW = 0

    /** Wraps and sizes the box for an available width [availW]. */
    private fun measure(m: WidgetMetrics, availW: Int) {
        val inner = 2 * (m.stroke + m.padX)
        val maxText = maxOf(0, availW - inner)
        wrap.wrap(prompt, m.ui, m.k, maxText)
        val btnW = maxOf(confirm.naturalWidth(m), cancel.naturalWidth(m))
        contentW = minOf(maxOf(wrap.widestWidth, 2 * btnW + m.gap), maxText)
        boxW = contentW + inner
        val lines = wrap.lines
        val textH = if (lines == 0) 0 else lines * m.textH + (lines - 1) * m.lineGap + m.padX
        boxH = 2 * m.stroke + m.padX + textH + m.rowH + m.padX
    }

    override fun naturalWidth(m: WidgetMetrics): Int {
        measure(m, Int.MAX_VALUE / 2)
        return boxW
    }

    override fun naturalHeight(m: WidgetMetrics, width: Int): Int {
        measure(m, width)
        return boxH
    }

    override fun onLayout(m: WidgetMetrics) {
        val b = bounds
        measure(m, b.width)
        box.setXywh(b.left + TextLayout.centre(b.width, boxW), b.top + TextLayout.centre(b.height, boxH), boxW, boxH)
        val cx = box.left + m.stroke + m.padX
        var y = box.top + m.stroke + m.padX
        for (i in 0 until MAX_LINES) {
            if (i < wrap.lines) {
                lineRects[i].setXywh(cx, y, contentW, m.textH)
                y += m.textH + if (i + 1 < wrap.lines) m.lineGap else m.padX
            } else {
                lineRects[i].set(0, 0, 0, 0)
            }
        }
        val bw = (contentW - m.gap) / 2
        cancel.layout(m, cx, y, bw, m.rowH)
        confirm.layout(m, cx + contentW - bw, y, bw, m.rowH)
    }

    override fun collectHits(region: HitRegion, frame: WidgetFrame) {
        if (!enabled) return
        cancel.collectHits(region, frame)
        confirm.collectHits(region, frame)
    }

    companion object {
        const val CONFIRM = 0
        const val CANCEL = 1
        const val MAX_LINES = 2
    }
}
