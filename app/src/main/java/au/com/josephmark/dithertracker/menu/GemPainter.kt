package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.widgets.TextSlice

/**
 * Draws the gem, its optional bloom keyframe and the restart confirm through a [Frame] (dither px,
 * root coordinates, glyph rotation only — never `Canvas.rotate`). Staircase shapes are drawn row by
 * row; fills are at global phase. Allocation-free.
 */
internal class GemPainter {

    private val slice = TextSlice()
    private val line = IRect()

    // ---- the gem --------------------------------------------------------------------------------

    /** The open gem. [enabled] per slot (disabled = `Role.DISABLED` hatch, content on paper). */
    fun gem(f: Frame, g: GemLayout, enabled: BooleanArray) {
        if (!f.hit(g.bounds)) return
        val r = f.r
        setting(r, g.cx, g.cy, g.outerR)
        for (s in 0 until GemLayout.SLOTS) {
            if (s == GemLayout.CENTRE || !f.hit(g.facetRect[s])) continue
            facet(f, g, s, enabled[s])
        }
        if (f.hit(g.facetRect[GemLayout.CENTRE])) {
            diamond(r, g.cx, g.cy, g.rf, g.rf, ink = true)
            cross(r, g.cx, g.cy, g.xHalf, g.xRun, ink = false)
        }
    }

    /** The optional bloom beat: the doubled, still empty diamond with the close already showing. */
    fun key1(f: Frame, g: GemLayout) {
        if (!f.hit(g.key1Bounds)) return
        val r = f.r
        setting(r, g.cx, g.cy, (g.key1Bounds.width - 1) / 2)
        diamond(r, g.cx, g.cy, g.key1R, g.key1R, ink = false)
        diamond(r, g.cx, g.cy, g.rf, g.rf, ink = true)
        cross(r, g.cx, g.cy, g.xHalf, g.xRun, ink = false)
    }

    /** MENU-dither diamond of radius [rad] with its 1-px 4-connected staircase outline. */
    private fun setting(r: DitherRenderer, cx: Int, cy: Int, rad: Int) {
        for (dy in -rad..rad) {
            val hw = rad - Math.abs(dy)
            r.fill(Role.MENU, cx - hw, cy + dy, 2 * hw + 1, 1)
            val in1 = maxOf(0, hw - 1)
            r.rect(cx - hw, cy + dy, hw - in1 + 1, 1, ink = true)
            r.rect(cx + in1, cy + dy, hw - in1 + 1, 1, ink = true)
        }
    }

    private fun facet(f: Frame, g: GemLayout, s: Int, enabled: Boolean) {
        val r = f.r
        val fx = g.facetX[s]
        val fy = g.facetY[s]
        val rf = g.rf
        diamond(r, fx, fy, rf, rf, ink = false)
        if (!enabled) {
            // The hatch inside a 1-px paper edge, so the facet's staircase still reads against the gutters.
            val h = rf - 1
            for (dy in -h..h) {
                val hw = h - Math.abs(dy)
                r.fill(Role.DISABLED, fx - hw, fy + dy, 2 * hw + 1, 1)
            }
        }
        val item = GemLayout.ITEMS[s] ?: return
        val k = g.k
        val rot = if (g.rotation[s] == GemLayout.FOLD) 0 else g.rotation[s]
        val ic = g.iconRect[s]
        if (g.rotation[s] == GemLayout.FOLD) {
            // Two-faced tip: the fold (the divider) runs into the facet either side of the icon.
            val gap = g.foldGap
            val left = fx - rf
            r.fill(Role.DOTTED, left, fy, ic.left - gap - left, 1)
            r.fill(Role.DOTTED, ic.right + gap, fy, fx + rf + 1 - (ic.right + gap), 1)
        }
        if (!enabled) f.icons.halo(ic, 0)
        f.icons.icon(k, item.icon, ic, rot, invert = false, opaque = false)
        val lr = g.labelRect[s]
        if (!lr.isEmpty) {
            f.text.draw(Fonts.UI, item.label, lr.left, lr.top, k, rot, invert = false, opaque = !enabled, pad = if (enabled) 0 else k)
        }
    }

    // ---- restart confirm -------------------------------------------------------------------------

    /**
     * The two-faced tent panel: the prompt faces S below the fold and N above it; the fold row holds
     * the two-faced buttons (cancel outline, confirm solid = the action) and the close diamond on the
     * crossing (= cancel). [lineRects] are the prompt lines' screen rects, lower face first.
     */
    fun confirm(f: Frame, g: GemLayout, copy: ConfirmCopy, lineRects: Array<IRect>) {
        if (!f.hit(g.panelOuter)) return
        val r = f.r
        val o = g.panelOuter
        r.fill(Role.MENU, o.left + 1, o.top + 1, o.width - 2, o.height - 2)
        r.frame(o, 1, ink = true)
        val p = g.panel
        r.rect(p, ink = true)
        r.rect(g.upperFace, ink = false)
        r.rect(g.lowerFace, ink = false)
        r.rect(g.fold, ink = false)
        r.fill(Role.DOTTED, g.fold.left, g.cy, g.fold.width, 1)
        val k = g.k
        for (face in 0 until 2) {
            val rot = if (face == 0) 0 else 180
            for (i in 0 until copy.lines) {
                val lr = lineRects[face * ConfirmCopy.MAX_LINES + i]
                slice.set(copy.prompt, copy.start[i], copy.end[i])
                f.text.drawIn(Fonts.UI, slice, lr.left, lr.top, lr.width, lr.height, k, rot)
            }
        }
        foldButton(f, g, g.cancelBtn, copy.cancel, copy.cancelChars, filled = false)
        foldButton(f, g, g.confirmBtn, copy.confirm, copy.confirmChars, filled = true)
        for (dy in -g.foldHalf..g.foldHalf) {
            val hw = g.foldR - Math.abs(dy)
            r.rect(g.cx - hw, g.cy + dy, 2 * hw + 1, 1, ink = true)
        }
        cross(r, g.cx, g.cy, g.foldXHalf, g.foldXRun, ink = false)
    }

    private fun foldButton(f: Frame, g: GemLayout, b: IRect, label: String, chars: Int, filled: Boolean) {
        val r = f.r
        if (filled) r.rect(b, ink = true) else { r.rect(b, ink = false); r.frame(b, 1, ink = true) }
        if (chars <= 0) return
        slice.set(label, 0, chars)
        // Lower half (read from S) and upper half (read from N), the fold row's middle line between.
        line.set(b.left + 1, g.cy + 1, b.right - 1, b.bottom - 1)
        f.text.drawIn(Fonts.UI, slice, line.left, line.top, line.width, line.height, g.k, 0, invert = filled)
        line.set(b.left + 1, b.top + 1, b.right - 1, g.cy)
        f.text.drawIn(Fonts.UI, slice, line.left, line.top, line.width, line.height, g.k, 180, invert = filled)
    }

    // ---- shapes ----------------------------------------------------------------------------------

    /** Solid diamond `|dx| + |dy| ≤ rad`, rows limited to `|dy| ≤ half` (flat tips when half < rad). */
    fun diamond(r: DitherRenderer, cx: Int, cy: Int, rad: Int, half: Int, ink: Boolean) {
        for (dy in -half..half) {
            val hw = rad - Math.abs(dy)
            if (hw >= 0) r.rect(cx - hw, cy + dy, 2 * hw + 1, 1, ink)
        }
    }

    /**
     * The chunky close X (Sam: the 1-stroke `close` icon is weak next to the hero numerals): two 45°
     * staircase bars, each row a [run]-px run (odd, so the X is exactly point-symmetric), rows
     * `−half..half`.
     */
    fun cross(r: DitherRenderer, cx: Int, cy: Int, half: Int, run: Int, ink: Boolean) {
        val h = run / 2
        for (t in -half..half) {
            r.rect(cx + t - h, cy + t, run, 1, ink)
            r.rect(cx - t - h, cy + t, run, 1, ink)
        }
    }

    /**
     * Press feedback in the shape of a staircase diamond: the stage inverts the rect [box] after this
     * layer is drawn, so inverting everything in [box] *outside* the shape first leaves exactly the
     * shape inverted. [rowHalf] is the shape's half-width at row dy from ([cx], [cy]) (−1 = none).
     * Without in-place inversion (API < 29) this is a no-op and the whole box inverts.
     */
    inline fun compensate(r: DitherRenderer, box: IRect, cx: Int, cy: Int, rowHalf: (Int) -> Int) {
        if (!DitherRenderer.canInvertInPlace) return
        for (y in box.top until box.bottom) {
            val hw = rowHalf(y - cy)
            if (hw < 0) {
                r.invertRect(box.left, y, box.width, 1)
            } else {
                val l = cx - hw
                val rr = cx + hw + 1
                if (l > box.left) r.invertRect(box.left, y, l - box.left, 1)
                if (rr < box.right) r.invertRect(rr, y, box.right - rr, 1)
            }
        }
    }
}
