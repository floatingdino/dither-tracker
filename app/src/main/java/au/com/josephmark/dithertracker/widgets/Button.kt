package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.TextLayout

/** Button looks (Figma `button.png`). */
enum class ButtonStyle {
    /** Default: paper with a 1-stroke ink outline, ink label. */
    OUTLINE,

    /** Dark: solid ink, paper label. The primary/destructive action. */
    FILLED,

    /** No outline: the label/icon sits on [Button.backdrop] (opaque when that is a dither), e.g. a title bar's close. */
    PLAIN,
}

/**
 * A push button: optional leading [icon], `ui` caps label (pass it in the case you want drawn),
 * centred. Disabled = [Role.DISABLED] hatch inside the outline with the label on a paper backing,
 * and no hit. Natural size: [WidgetMetrics.rowH] tall; label + padding wide, never under the
 * fingertip [WidgetMetrics.target]. A label that does not fit is cut cleanly ([TextFit]).
 */
open class Button(
    id: Int,
    var label: CharSequence = "",
    var icon: Icon? = null,
    var style: ButtonStyle = ButtonStyle.OUTLINE,
) : Widget(id) {

    /** What a [ButtonStyle.PLAIN] button sits on (drawn as its background). */
    var backdrop: Role = Role.SURFACE

    /** Layout results (local). */
    val iconRect = IRect()
    val labelRect = IRect()
    var labelChars = 0
        private set

    override fun naturalWidth(m: WidgetMetrics): Int {
        val ic = icon
        if (label.isEmpty()) return maxOf(m.target, if (ic != null) m.icon + 2 * (m.inset + m.stroke) else 0)
        val content = (if (ic != null) m.icon + m.gap else 0) + m.textW(label.length)
        return maxOf(m.target, content + 2 * (m.stroke + m.padX))
    }

    override fun onLayout(m: WidgetMetrics) {
        val b = bounds
        val ic = icon
        var iconW = if (ic != null) m.icon else 0
        labelChars = 0
        if (label.isNotEmpty()) {
            val avail = b.width - 2 * (m.stroke + m.padX) - if (ic != null) m.icon + m.gap else 0
            labelChars = TextFit.fit(label, m.ui, m.k, maxOf(0, avail))
            // Next to an icon, a stub ("⟳ R") reads worse than the icon alone.
            if (ic != null && labelChars < minOf(label.length, MIN_LABEL_WITH_ICON)) labelChars = 0
            if (labelChars > 0 && ic != null) iconW += m.gap
        }
        val contentW = iconW + m.textW(labelChars)
        val sx = b.left + TextLayout.centre(b.width, contentW)
        if (ic != null) iconRect.setXywh(sx, b.top + TextLayout.centre(b.height, m.icon), m.icon, m.icon) else iconRect.set(0, 0, 0, 0)
        labelRect.setXywh(sx + iconW, b.top, m.textW(labelChars), b.height)
    }

    companion object {
        /** Fewest label glyphs kept beside an icon; any fewer and the button shows the icon only. */
        const val MIN_LABEL_WITH_ICON = 3
    }
}

/** An icon-only [Button]: a fingertip-sized square. */
class IconButton(id: Int, icon: Icon, style: ButtonStyle = ButtonStyle.OUTLINE) : Button(id, "", icon, style)

/**
 * A row of buttons. Each button is laid out [WidgetMetrics.inset] short of the row's top and bottom
 * (so outlines never touch the row dividers) while its touch rect spans the full row height.
 * [align] places them at the row's [TextLayout.START] / [TextLayout.CENTRE] / [TextLayout.END]
 * (at natural widths) or [FILL]s the row with equal widths; if they do not fit, they share the row
 * equally and labels cut cleanly.
 */
class ButtonRow(vararg val buttons: Button, var align: Int = TextLayout.END) : Widget(NO_ID) {

    private val nat = IntArray(buttons.size)

    override fun naturalWidth(m: WidgetMetrics): Int {
        var w = 2 * m.padX + m.gap * (buttons.size - 1)
        for (b in buttons) w += b.naturalWidth(m)
        return w
    }

    override fun onLayout(m: WidgetMetrics) {
        val n = buttons.size
        if (n == 0) return
        val left = bounds.left + m.padX
        val innerW = bounds.width - 2 * m.padX
        val top = bounds.top + m.inset
        val h = bounds.height - 2 * m.inset
        var total = m.gap * (n - 1)
        for (i in 0 until n) { nat[i] = buttons[i].naturalWidth(m); total += nat[i] }
        if (align == FILL || total > innerW) {
            val avail = innerW - m.gap * (n - 1)
            for (i in 0 until n) {
                val x0 = avail * i / n
                val x1 = avail * (i + 1) / n
                place(m, buttons[i], left + x0 + m.gap * i, top, x1 - x0, h)
            }
            return
        }
        var x = left + TextLayout.align(align, innerW, total)
        for (i in 0 until n) {
            place(m, buttons[i], x, top, nat[i], h)
            x += nat[i] + m.gap
        }
    }

    private fun place(m: WidgetMetrics, b: Button, x: Int, y: Int, w: Int, h: Int) {
        b.layout(m, x, y, w, h)
        b.hit.set(x, bounds.top, x + w, bounds.bottom)
    }

    override fun collectHits(region: HitRegion, frame: WidgetFrame) {
        for (b in buttons) b.collectHits(region, frame)
    }

    companion object {
        /** [align] value: equal widths across the row. */
        const val FILL = 3
    }
}
