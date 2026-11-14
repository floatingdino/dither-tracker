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
 * Glyphs and icons on a dither fill (disabled hatch, plain buttons on dither) are drawn `opaque`: on
 * their paper cut-out (T41 halo: `Icon.halo`, `Glyph.halo`), or on a paper box for a face without halo
 * rows. Title-bar text keeps its paper strip (`box`).
 */
class WidgetPainter(val c: WidgetCanvas) {

    private val s = IRect()
    private val tmp = IRect()
    private val slice = TextSlice()
    private val local = IRect()
    private val numBuf = CharBuf(16)

    // Per (k, halo), indexed by icon ordinal: every k in use keeps its instances (T20: a single-k cache
    // made new instances, so new renderer masks, whenever two metrics alternated).
    private var byK = arrayOfNulls<Array<MonoImage?>>(16)

    private fun slot(k: Int, halo: Boolean): Array<MonoImage?> {
        val i = 2 * k + if (halo) 1 else 0
        if (i >= byK.size) byK = byK.copyOf(i + 8)
        return byK[i] ?: arrayOfNulls<MonoImage>(Icon.entries.size).also { byK[i] = it }
    }

    /** Draws [w] (and its children) in frame [f]. */
    fun draw(f: WidgetFrame, w: Widget) {
        when (w) {
            is Button -> button(f, w)
            is ButtonRow -> buttonRow(f, w)
            is ListRow -> listRow(f, w)
            is Toggle -> toggle(f, w)
            is Stepper -> stepper(f, w)
            is Segmented -> segmented(f, w)
            is ConfirmPrompt -> confirmPrompt(f, w)
            is PagedList -> pagedList(f, w)
            is Panel -> panel(f, w)
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

    /** Paper under a row; disabled rows get the hatch. Returns true when content sits on a dither. */
    private fun rowBackground(f: WidgetFrame, w: Widget): Boolean {
        paper(f, w.bounds)
        if (w.enabled) return false
        fill(f, Role.DISABLED, w.bounds)
        return true
    }

    private fun listRow(f: WidgetFrame, r: ListRow) {
        val m = r.metrics
        val d = rowBackground(f, r)
        r.icon?.let { icon(f, m, it, r.iconRect, invert = false, opaque = d) }
        text(f, m, m.ui, r.label, 0, r.labelChars, r.labelRect, TextLayout.START, invert = false, opaque = d)
        r.value?.let { text(f, m, m.ui, it, 0, r.valueChars, r.valueRect, TextLayout.END, invert = false, opaque = d) }
        if (r.chevron) icon(f, m, Icon.CHEVRON_RIGHT, r.chevronRect, invert = false, opaque = d)
    }

    private fun toggle(f: WidgetFrame, t: Toggle) {
        val m = t.metrics
        val d = rowBackground(f, t)
        t.icon?.let { icon(f, m, it, t.iconRect, invert = false, opaque = d) }
        text(f, m, m.ui, t.label, 0, t.labelChars, t.labelRect, TextLayout.START, invert = false, opaque = d)
        if (t.checked) {
            ink(f, t.box)
            icon(f, m, Icon.CHECK, t.checkRect, invert = true, opaque = false)
        } else {
            paper(f, t.box)
            outline(f, t.box, m.stroke)
        }
    }

    private fun stepper(f: WidgetFrame, st: Stepper) {
        val m = st.metrics
        val d = rowBackground(f, st)
        text(f, m, m.ui, st.label, 0, st.labelChars, st.labelRect, TextLayout.START, invert = false, opaque = d)
        stepButton(f, m, st.minusBtn, st.minusIcon, Icon.MINUS, st.enabled && st.canDecrease)
        stepButton(f, m, st.plusBtn, st.plusIcon, Icon.PLUS, st.enabled && st.canIncrease)
        numBuf.clear().append(st.value)
        text(f, m, m.num, numBuf, 0, numBuf.length, st.valueRect, TextLayout.CENTRE, invert = false, opaque = d)
    }

    private fun stepButton(f: WidgetFrame, m: WidgetMetrics, btn: IRect, at: IRect, ic: Icon, live: Boolean) {
        if (live) {
            paper(f, btn); outline(f, btn, m.stroke)
        } else {
            paper(f, btn); hatchedOutline(f, btn, m.stroke)
        }
        icon(f, m, ic, at, invert = false, opaque = !live)
    }

    private fun segmented(f: WidgetFrame, sg: Segmented) {
        val m = sg.metrics
        val d = rowBackground(f, sg)
        text(f, m, m.ui, sg.label, 0, sg.labelChars, sg.labelRect, TextLayout.START, invert = false, opaque = d)
        paper(f, sg.bar)
        outline(f, sg.bar, m.stroke)
        for (i in 1 until sg.options.size) {
            ink(f, local.setXywh(sg.segVis[i].left, sg.bar.top, m.stroke, sg.bar.height))
        }
        val sel = sg.selected
        if (sel in sg.options.indices) ink(f, sg.segVis[sel])
        for (i in sg.options.indices) {
            text(f, m, sg.optionFont(m, i), sg.options[i], 0, sg.segChars[i], sg.segVis[i], TextLayout.CENTRE, invert = i == sel, opaque = false)
        }
        if (sg.chevron) icon(f, m, Icon.CHEVRON_RIGHT, sg.chevronRect, invert = false, opaque = d)
    }

    private fun confirmPrompt(f: WidgetFrame, p: ConfirmPrompt) {
        val m = p.metrics
        paper(f, p.box)
        outline(f, p.box, m.stroke)
        val w = p.wrap
        for (i in 0 until w.lines) {
            text(f, m, m.ui, p.prompt, w.start[i], w.end[i], p.lineRects[i], TextLayout.CENTRE, invert = false, opaque = false)
        }
        button(f, p.cancel)
        button(f, p.confirm)
    }

    private fun panel(f: WidgetFrame, p: Panel) {
        val m = p.metrics
        paper(f, p.bounds)
        outline(f, p.bounds, m.stroke)
        if (p.hasTitleBar) {
            fill(f, Role.TAB, p.titleBar)
            p.title?.let { text(f, m, m.ui, it, 0, p.titleChars, p.titleRect, TextLayout.START, invert = false, opaque = true, box = true) }
            p.close?.let { button(f, it) }
        }
        for (i in p.rows.indices) {
            draw(f, p.rows[i])
            dottedRect(f, p.dividers[i])
        }
        dottedRect(f, p.columnRule)
    }

    private fun pagedList(f: WidgetFrame, l: PagedList) {
        val m = l.metrics
        paper(f, l.bounds)
        val n = l.visibleCount
        val pool = l.pool
        for (i in 0 until n) {
            if (pool != null) draw(f, pool[i])
            if (i + 1 < n) {
                val s = l.slots[i]
                dotted(f, s.left + m.padX, s.bottom, s.width - 2 * m.padX, 1)
            }
        }
        if (l.hasFooter) {
            val ft = l.footer
            dotted(f, ft.left + m.padX, ft.top - 1, ft.width - 2 * m.padX, 1)
            button(f, l.prev)
            button(f, l.next)
            text(f, m, m.ui, l.indicator, 0, l.indicator.length, l.indicatorRect, TextLayout.CENTRE, invert = false, opaque = false)
        }
    }

    private fun dottedRect(f: WidgetFrame, r: IRect) {
        if (!r.isEmpty) dotted(f, r.left, r.top, r.width, r.height)
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
     * along the reader's line, cap band centred across it. Opaque text sits on its glyphs' cut-outs
     * ([Font.hasHalo] faces), else — or with [box] — on a box with a [WidgetMetrics.halo] rim.
     * [box] is for title bars: a long word on the `TAB` checker reads calmer on one paper strip (T41).
     */
    internal fun text(
        f: WidgetFrame, m: WidgetMetrics, font: Font, text: CharSequence, from: Int, to: Int, r: IRect,
        align: Int, invert: Boolean, opaque: Boolean, box: Boolean = false,
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
        val halo = opaque && !box && font.hasHalo
        c.text(font, run, x, y, k, rot, invert, opaque && !halo, if (opaque && !halo) m.halo else 0, halo)
    }

    /**
     * Blits [icon] (scaled to the metrics' k) with its local footprint at [r]'s top-left; [opaque] puts it
     * on its paper cut-out ([Icon.halo], k px beyond the footprint; the halo is ink with [invert]).
     */
    internal fun icon(f: WidgetFrame, m: WidgetMetrics, icon: Icon, r: IRect, invert: Boolean, opaque: Boolean) {
        f.map(r.left, r.top, m.icon, m.icon, tmp)
        val k = m.k
        if (opaque) c.blit(scaledHalo(icon, k), tmp.left - k, tmp.top - k, f.rotation, !invert, opaque = false)
        c.blit(scaledIcon(icon, k), tmp.left, tmp.top, f.rotation, invert, opaque = false)
    }

    /** [Icon.halo] enlarged k× as a stable instance. */
    fun scaledHalo(icon: Icon, k: Int): MonoImage {
        if (k == 1) return icon.halo
        val a = slot(k, halo = true)
        return a[icon.ordinal] ?: Scaled(icon.halo, k).also { a[icon.ordinal] = it }
    }

    /** [icon] enlarged k× as a stable instance (the renderer caches its bitmap per instance). */
    fun scaledIcon(icon: Icon, k: Int): MonoImage {
        if (k == 1) return icon
        val a = slot(k, halo = false)
        return a[icon.ordinal] ?: Scaled(icon, k).also { a[icon.ordinal] = it }
    }

    private class Scaled(private val src: MonoImage, private val k: Int) : MonoImage {
        override val width: Int = src.width * k
        override val height: Int = src.height * k
        override fun isInk(x: Int, y: Int): Boolean = src.isInk(x / k, y / k)
    }
}
