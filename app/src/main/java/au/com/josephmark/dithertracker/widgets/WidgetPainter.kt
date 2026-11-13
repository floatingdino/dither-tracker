package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * Draws laid-out widgets on a [WidgetCanvas]. Every local rect goes through the widget's
 * [WidgetFrame] ([WidgetFrame.map]) and every glyph/icon is drawn with the frame's rotation — never
 * `Canvas.rotate`. Allocation-free after each icon's first use at the current glyph scale.
 *
 * Each widget paints its whole [Widget.bounds] (its background first), so a pressed part can be
 * redrawn inverted in isolation ([drawPressed] on devices without in-place inversion).
 *
 * Glyphs and icons on a dither fill (title bar, disabled hatch) are drawn opaque, on a paper backing.
 */
class WidgetPainter(val c: WidgetCanvas) {

    private val s = IRect()
    private val tmp = IRect()
    private val slice = TextSlice()

    private var iconK = 1
    private val scaledIcons = arrayOfNulls<MonoImage>(Icon.entries.size)

    /** Draws [w] (and its children) in frame [f]. */
    fun draw(f: WidgetFrame, w: Widget) {
        when (w) {
            is Button -> button(f, w)
            is ButtonRow -> buttonRow(f, w)
            else -> throw IllegalArgumentException("no painter for ${w::class.simpleName}")
        }
    }

    /** Press feedback for every id in [region] that [tracker] reports pressed. */
    fun drawPressed(region: HitRegion, tracker: PressTracker) {
        for (i in 0 until region.size) if (tracker.isPressed(region.idAt(i))) pressedAt(region, i)
    }

    /** Press feedback for [id]: inverts its feedback rect (or redraws its owner inverted, clipped). */
    fun drawPressed(region: HitRegion, id: Int) {
        val i = region.indexOf(id)
        if (i >= 0) pressedAt(region, i)
    }

    private fun pressedAt(region: HitRegion, i: Int) {
        val fb = region.feedbackAt(i)
        if (c.canInvertInPlace) {
            c.invert(fb.left, fb.top, fb.width, fb.height)
            return
        }
        val owner = region.ownerAt(i) ?: return
        val frame = region.frameAt(i) ?: return
        c.beginInvertedClip(fb.left, fb.top, fb.width, fb.height)
        draw(frame, owner)
        c.endInvertedClip()
    }

    // ---- widgets --------------------------------------------------------------------------------

    private fun button(f: WidgetFrame, b: Button) {
        val m = b.metrics
        val enabled = b.enabled
        val plain = b.style == ButtonStyle.PLAIN
        when {
            !enabled -> {
                paper(f, b.bounds)
                if (plain) fill(f, Role.DISABLED, b.bounds) else hatchedOutline(f, b.bounds, m.stroke)
            }
            plain -> fill(f, b.backdrop, b.bounds)
            b.style == ButtonStyle.FILLED -> ink(f, b.bounds)
            else -> { paper(f, b.bounds); outline(f, b.bounds, m.stroke) }
        }
        val inv = enabled && b.style == ButtonStyle.FILLED
        val onDither = !enabled || (plain && b.backdrop.tile.inkCount != 0)
        val ic = b.icon
        if (ic != null) icon(f, m, ic, b.iconRect, inv, opaque = onDither)
        if (b.labelChars > 0) {
            text(f, m, m.ui, b.label, 0, b.labelChars, b.labelRect, TextLayout.START, inv, onDither)
        }
    }

    private fun buttonRow(f: WidgetFrame, r: ButtonRow) {
        paper(f, r.bounds)
        for (b in r.buttons) button(f, b)
    }

    // ---- primitives (local rects through the frame) -----------------------------------------------

    internal fun paper(f: WidgetFrame, r: IRect) {
        f.map(r, s); c.rect(s.left, s.top, s.width, s.height, ink = false)
    }

    internal fun ink(f: WidgetFrame, r: IRect) {
        f.map(r, s); c.rect(s.left, s.top, s.width, s.height, ink = true)
    }

    internal fun fill(f: WidgetFrame, role: Role, r: IRect) {
        f.map(r, s); c.fill(role, s.left, s.top, s.width, s.height)
    }

    internal fun outline(f: WidgetFrame, r: IRect, t: Int) {
        f.map(r, s); c.frame(s.left, s.top, s.width, s.height, t, ink = true)
    }

    /** Ink outline with the [Role.DISABLED] hatch inside it. */
    internal fun hatchedOutline(f: WidgetFrame, r: IRect, t: Int) {
        f.map(r, s)
        c.frame(s.left, s.top, s.width, s.height, t, ink = true)
        c.fill(Role.DISABLED, s.left + t, s.top + t, s.width - 2 * t, s.height - 2 * t)
    }

    /** A 1-px-thick local line (horizontal or vertical) dotted at global phase. */
    internal fun dotted(f: WidgetFrame, x: Int, y: Int, w: Int, h: Int) {
        f.map(x, y, w, h, s); c.fill(Role.DOTTED, s.left, s.top, s.width, s.height)
    }

    /**
     * Draws `text[from, to)` in [font] at the metrics' k inside the local rect [r]: aligned by [align]
     * along the reader's line, cap band centred across it. Opaque text gets a [WidgetMetrics.halo] rim.
     */
    internal fun text(
        f: WidgetFrame, m: WidgetMetrics, font: Font, text: CharSequence, from: Int, to: Int, r: IRect,
        align: Int, invert: Boolean, opaque: Boolean,
    ) {
        if (to <= from) return
        val run = slice.set(text, from, to)
        f.map(r, s)
        val k = m.k
        val tw = font.widthOf(run.length, k)
        val th = font.height(k)
        val rot = f.rotation
        val x = s.left + TextLayout.placeX(rot, s.width, s.height, tw, th, font.capTop * k, font.capHeight * k, align)
        val y = s.top + TextLayout.placeY(rot, s.width, s.height, tw, th, font.capTop * k, font.capHeight * k, align)
        c.text(font, run, x, y, k, rot, invert, opaque, if (opaque) m.halo else 0)
    }

    /** Blits [icon] (scaled to the metrics' k) with its local footprint at [r]'s top-left. */
    internal fun icon(f: WidgetFrame, m: WidgetMetrics, icon: Icon, r: IRect, invert: Boolean, opaque: Boolean) {
        f.map(r.left, r.top, m.icon, m.icon, tmp)
        c.blit(scaledIcon(icon, m.k), tmp.left, tmp.top, f.rotation, invert, opaque)
    }

    /** [icon] enlarged k× as a stable instance (the renderer caches its bitmap per instance). */
    fun scaledIcon(icon: Icon, k: Int): MonoImage {
        if (k == 1) return icon
        if (k != iconK) { scaledIcons.fill(null); iconK = k }
        return scaledIcons[icon.ordinal] ?: Scaled(icon, k).also { scaledIcons[icon.ordinal] = it }
    }

    private class Scaled(private val src: MonoImage, private val k: Int) : MonoImage {
        override val width: Int = src.width * k
        override val height: Int = src.height * k
        override fun isInk(x: Int, y: Int): Boolean = src.isInk(x / k, y / k)
    }
}
