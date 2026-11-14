package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.TextLayout
import au.com.josephmark.dithertracker.widgets.WidgetCanvas
import au.com.josephmark.dithertracker.widgets.WidgetFrame
import au.com.josephmark.dithertracker.widgets.WidgetPainter

/**
 * Draws a laid-out [SettingsCard] through the widget kit's painter (so every row looks like every
 * other overlay's), adding only the card's own furniture: outline + dithered rim, the rotate square and
 * its rule, the centred title on a paper backing, the rule under the title bar, and no divider above a
 * [HintRow] (it reads as a footnote to the row before). Allocation-free; press feedback is the host's
 * inversion of the region's feedback rects.
 */
class SettingsPainter(val canvas: WidgetCanvas) {

    val widgets = WidgetPainter(canvas)
    private val scratch = IRect()
    private val warmFrame = WidgetFrame()

    fun draw(card: SettingsCard) {
        val f = card.frame
        val m = card.m
        val p = widgets
        p.outline(f, card.local, m.stroke)
        p.fill(f, Role.TAB, card.band)
        p.draw(f, card.panel)
        p.draw(f, card.rotate)
        p.ink(f, card.rotateRule)
        if (card.titleChars > 0) p.paper(f, card.titleBack)
        p.text(f, m, m.ui, card.title, 0, card.titleChars, card.titleBack, TextLayout.CENTRE, invert = false, opaque = false)
        p.ink(f, card.titleRule)
        val list = card.list
        p.draw(f, list)
        val first = list.firstIndex
        for (i in 0 until list.visibleCount) {
            val r = card.shown[first + i]!!
            p.draw(f, r.widget)
            if (r is HintRow && i > 0) {
                val s = list.slots[i - 1]
                p.paper(f, scratch.setXywh(s.left, s.bottom, s.width, 1))
            }
        }
    }

    /**
     * Builds every glyph and icon mask the card can show at the current glyph scale by drawing it
     * (renderer begun on a scratch canvas): the current layout plus the icons only some states use.
     */
    fun prewarm(card: SettingsCard, pages: List<SettingsPage>) {
        draw(card)
        val m = card.m
        val w = card.side
        warmFrame.set(0, 0, w, m.rowH, 0)
        for (page in pages) {
            scratch.setXywh(0, 0, w, m.rowH)
            widgets.text(warmFrame, m, m.ui, page.title, 0, page.title.length, scratch, TextLayout.START, invert = false, opaque = true)
            for (r in page.rows) {
                r.widget.layout(m, 0, 0, w, m.rowH)
                widgets.draw(warmFrame, r.widget)
            }
        }
        scratch.setXywh(0, 0, w, m.rowH)
        widgets.text(warmFrame, m, m.ui, DIGITS, 0, DIGITS.length, scratch, TextLayout.START, invert = false, opaque = false)
        for (ic in PREWARM_ICONS) canvas.blit(widgets.scaledIcon(ic, m.k), 0, 0, 0, invert = false, opaque = false)
        // A paged list's footer chevron at the end of its pages is a disabled (hatched) button: its icon sits on its halo (T53: the counters page pages).
        for (ic in PAGER_ICONS) canvas.blit(widgets.scaledHalo(ic, m.k), 0, 0, 0, invert = true, opaque = false)
    }

    companion object {
        private const val DIGITS = "0123456789/"
        private val PAGER_ICONS = arrayOf(Icon.CHEVRON_LEFT, Icon.CHEVRON_RIGHT)
        private val PREWARM_ICONS = arrayOf(Icon.CHECK, Icon.CHEVRON_LEFT, Icon.CHEVRON_RIGHT, Icon.ROTATE, Icon.CLOSE) +
            SettingsTable.COUNTER_ICONS + SettingsTable.TRACKER_ICONS
    }
}
