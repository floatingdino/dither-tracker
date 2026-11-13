package au.com.josephmark.dithertracker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import au.com.josephmark.dithertracker.dither.CanvasDitherRenderer
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Patterns
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.type.TextRenderer

/**
 * Two demo pages. Tap the top [PAGE_ZONE] fraction of the screen to switch page; tap anywhere else
 * to toggle global dark mode.
 *
 * Page 0 (default) — type specimen (T05), see [drawTypePage].
 *
 * Page 1 — engine test card (T04). Top to bottom, all in dither px:
 *  1. Bayer D0..D16, left→right, wrapping.
 *  2. Named tiles in [Patterns.named] order (CHECKER, SPARSE, DOTS, HLINES, VLINES, DIAG, CROSSHATCH).
 *  3. Roles in [Role] order.
 *  4. A drawer mock (TAB bar over DRAWER body, ink button) abutting a MENU block — seams must be
 *     invisible — crossed by a dotted horizontal + vertical divider.
 *  5. Every [Icon], in enum order.
 *  6. First icon at 0/90/180/270°, then the same opaque + inverted.
 *  7. Left: swatches + icons with [DitherRenderer.invertRect] over the right half.
 *     Right: the same content redrawn inside [DitherRenderer.withInverted]. Both halves must match.
 */
class DitherDemoView(context: Context) : View(context) {

    private val r = CanvasDitherRenderer()
    private val t = TextRenderer(r)
    private var page = 0

    init {
        setWillNotDraw(false)
    }

    fun setInverted(on: Boolean) {
        r.inverted = on
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val dm = resources.displayMetrics
        val s = DitherGrid.chooseScale(w, h, dm.densityDpi)
        r.setGrid(DitherGrid(w, h, s))
        r.prewarm()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (event.y < height * PAGE_ZONE) page = (page + 1) % 2 else r.inverted = !r.inverted
            invalidate()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        r.begin(canvas)
        r.clear()
        if (page == 0) drawTypePage() else drawEnginePage(canvas)
    }

    private fun drawEnginePage(canvas: Canvas) {
        val g = r.grid
        val w = g.width
        val h = g.height
        r.frame(0, 0, w, h)

        val pad = 6
        val sw = 20
        val gap = 3
        val cols = maxOf(1, (w - 2 * pad + gap) / (sw + gap))
        var y = pad

        // 1. Bayer levels
        y = swatchRow(Patterns.D.size, cols, pad, y, sw, gap, 0)
        y += 4
        // 2. Named tiles
        y = swatchRow(Patterns.named.size, cols, pad, y, sw, gap, 1)
        y += 4
        // 3. Roles
        y = swatchRow(ROLES.size, cols, pad, y, sw, gap, 2)
        y += 6

        // 4. Drawer mock + seamless abutment + dotted dividers
        val bw = w - 2 * pad
        val mw = bw / 3
        r.fill(Role.TAB, pad, y, bw - mw, 10)
        r.fill(Role.DRAWER, pad, y + 10, bw - mw, 40)
        r.fill(Role.MENU, pad + bw - mw, y, mw, 50)
        r.rect(pad + 6, y + 20, 60, 12)
        r.frame(pad + 70, y + 20, 60, 12)
        r.dottedH(pad, y + 56, bw)
        r.dottedV(pad + bw / 2, y - 2, 62)
        y += 64

        // 5. All icons
        val icons = Icon.entries
        val step = Icon.SIZE + 4
        val icols = maxOf(1, (w - 2 * pad + 4) / step)
        for (i in 0 until icons.size) {
            r.icon(icons[i], pad + (i % icols) * step, y + (i / icols) * step)
        }
        if (icons.isNotEmpty()) y += ((icons.size + icols - 1) / icols) * step
        y += 4

        // 6. Rotations, opaque/inverted
        val ic = rotIcon
        if (ic != null) {
            for (k in 0 until 4) r.icon(ic, pad + k * step, y, rotation = k * 90)
            r.icon(ic, pad + 4 * step + 4, y, opaque = true, invert = true)
            r.icon(ic, pad + 5 * step + 4, y, rotation = 90, opaque = true, invert = true)
            r.fill(Role.TAB, pad + 6 * step + 8, y, Icon.SIZE, Icon.SIZE)
            r.icon(ic, pad + 6 * step + 8, y)
            r.icon(ic, pad + 7 * step + 8, y, opaque = true)
            y += step + 4
        }

        // 7. Inversion: invertRect (left) vs withInverted redraw (right). The right card is offset by
        //    a multiple of 8 (every tile period divides 8) so both halves must be pixel-identical.
        val half = ((w - 2 * pad - gap) / 2) and 7.inv()
        val bh = minOf(56, h - pad - y)
        if (bh > 0) {
            card(pad, y, half, bh)
            r.invertRect(pad + half / 2, y, half - half / 2, bh)
            val x2 = pad + ((half + gap + 7) and 7.inv())
            card(x2, y, half, bh)
            r.withInverted { cardClipped(canvas, x2 + half / 2, y, half - half / 2, bh, x2) }
        }
    }

    /** Draws [n] swatches of [kind] (0 Bayer, 1 named, 2 roles); returns the y below them. */
    private fun swatchRow(n: Int, cols: Int, x0: Int, y0: Int, sw: Int, gap: Int, kind: Int): Int {
        for (i in 0 until n) {
            val x = x0 + (i % cols) * (sw + gap)
            val y = y0 + (i / cols) * (sw + gap)
            r.frame(x, y, sw, sw)
            val t = when (kind) {
                0 -> Patterns.D[i]
                1 -> Patterns.named[i]
                else -> ROLES[i].tile
            }
            r.fill(t, x + 1, y + 1, sw - 2, sw - 2)
        }
        return y0 + ((n + cols - 1) / cols) * (sw + gap)
    }

    /** Test content for the inversion block. */
    private fun card(x: Int, y: Int, w: Int, h: Int) {
        r.rect(x, y, w, h, ink = false)
        r.frame(x, y, w, h)
        r.fill(Patterns.D[4], x + 2, y + 2, w / 2 - 2, h / 2 - 2)
        r.fill(Role.DRAWER, x + w / 2, y + 2, w / 2 - 2, h / 2 - 2)
        r.fill(Patterns.DIAG, x + 2, y + h / 2, w - 4, h / 2 - 2)
        val icons = Icon.entries
        for (k in 0 until minOf(icons.size, (w - 4) / 18)) r.icon(icons[k], x + 3 + k * 18, y + h / 2 + 2)
    }

    /** Redraws the right part [cx, cx+cw) of a card at [x] (inverted by the caller). */
    private fun cardClipped(c: Canvas, cx: Int, y: Int, cw: Int, h: Int, x: Int) {
        val g = r.grid
        c.save()
        c.clipRect(g.toDeviceX(cx), g.toDeviceY(y), g.toDeviceX(cx + cw), g.toDeviceY(y + h))
        card(x, y, cx + cw - x, h)
        c.restore()
    }

    /** An asymmetric icon so rotations are visibly distinct. */
    private val rotIcon: Icon? = Icon.byName("undo") ?: Icon.entries.firstOrNull()

    /**
     * Type specimen (T05), top to bottom:
     *  1. Header (ui).
     *  2. A play-panel mock: `-` / hero `40` / `+` in num-l at k=1, as in design/figma/play.png.
     *  3. num-l full set at k=1, then glyph scale k=2: `-12`, `40` (and num-m `21` at k=2).
     *  4. num-m full set, a sample line (deltas, damage, time) and k=2.
     *  5. ui: caps, lowercase, digits + punctuation, a mixed sentence, an unknown char (notdef box).
     *  6. COMMANDER DAMAGE as an outline button and a black button (design/figma/button.png).
     *  7. Drawer mock: text on TAB and DRAWER dither fills with opaque backing.
     *  8. "40" at rotation 0/90/180/270, each centred (cap band) in a framed square via [TextRenderer.drawIntIn].
     */
    private fun drawTypePage() {
        val g = r.grid
        val w = g.width
        val h = g.height
        val ui = Fonts.UI
        val nl = Fonts.NUM_L
        val nm = Fonts.NUM_M
        r.frame(0, 0, w, h)
        val pad = 8
        val iw = w - 2 * pad
        var y = 6

        // 1. Header
        t.draw(ui, "TYPE SPECIMEN  T05", pad, y, tracking = 2)
        t.draw(ui, "TAP TOP: ENGINE PAGE", pad + iw - ui.measure("TAP TOP: ENGINE PAGE"), y)
        y += ui.height() + 4
        r.dottedH(1, y, w - 2)
        y += 2

        // 2. Play-panel mock at the mock's proportions
        val ph = 64
        val third = iw / 3
        t.drawIn(nl, "-", pad, y, third, ph)
        t.drawIntIn(nl, LIFE, pad + third, y, iw - 2 * third, ph)
        t.drawIn(nl, "+", pad + iw - third, y, third, ph)
        y += ph
        r.dottedH(1, y, w - 2)
        y += 6

        // 3. num-l set + glyph scale k=2
        t.draw(nl, "0123456789+-", pad, y)
        y += nl.height() + 6
        t.drawInt(nl, -12, pad, y, k = 2)
        val x2 = pad + nl.measureInt(-12, k = 2) + 36
        t.drawInt(nl, 40, x2, y, k = 2)
        t.drawInt(nm, 21, x2 + nl.measureInt(40, k = 2) + 24, y + nl.height(2) - nm.height(2), k = 2)
        y += nl.height(2) + 8

        // 4. num-m
        t.draw(nm, "0123456789+-/:", pad, y)
        val sx = pad + nm.measure("0123456789+-/:") + 12
        t.drawInt(nm, 7, sx, y, signed = true)
        t.drawInt(nm, -12, sx + nm.measureInt(7, signed = true) + 10, y)
        y += nm.height() + 4
        t.draw(nm, "21/40 12:30", pad, y)
        t.drawInt(nm, 17, pad + nm.measure("21/40 12:30") + 12, y, k = 1, invert = true, opaque = true, pad = 2)
        y += nm.height() + 6

        // 5. ui
        t.draw(ui, "ABCDEFGHIJKLMNOPQRSTUVWXYZ", pad, y); y += ui.height() + 2
        t.draw(ui, "abcdefghijklmnopqrstuvwxyz", pad, y); y += ui.height() + 2
        t.draw(ui, "0123456789 +-\u2212\u00D7/:;.,!?()[]%#&'\"<>=_|*", pad, y); y += ui.height() + 2
        t.draw(ui, "Poison 3/10, Turn 7 (2:15). Unknown: \u00E9\u20AC", pad, y); y += ui.height() + 2
        t.draw(ui, "LIFE ", pad, y)
        t.drawInt(ui, -5, pad + ui.measure("LIFE "), y)
        t.draw(ui, "COMMANDER TAX \u00D72", pad + ui.measure("LIFE -5   "), y)
        y += ui.height() + 6

        // 6. Buttons (outline / black), tracked caps
        val label = "COMMANDER DAMAGE"
        val bw = ui.measure(label, tracking = 2) + 16
        val bh = ui.capHeight + 12
        r.frame(pad, y, bw, bh)
        t.drawIn(ui, label, pad, y, bw, bh, tracking = 2)
        r.rect(pad + bw + 8, y, bw, bh)
        t.drawIn(ui, label, pad + bw + 8, y, bw, bh, tracking = 2, invert = true)
        y += bh + 6

        // 7. Drawer mock: TAB bar over DRAWER body, text opaque on both
        val tabH = 12
        val bodyH = 40
        r.fill(Role.TAB, pad, y, iw, tabH)
        t.drawIn(ui, "POISON", pad, y, iw, tabH, opaque = true, pad = 1, tracking = 2)
        r.fill(Role.DRAWER, pad, y + tabH, iw, bodyH)
        val by = y + tabH + 5
        r.rect(pad + 6, by, bw, bh)
        t.drawIn(ui, label, pad + 6, by, bw, bh, tracking = 2, invert = true)
        t.draw(ui, "ENERGY", pad + 6, by + bh + 6, opaque = true, pad = 1)
        t.drawInt(nm, 12, pad + bw + 20, by + 2, opaque = true, pad = 2)
        t.drawInt(nm, -3, pad + bw + 50, by + 2, opaque = true, pad = 2, invert = true)
        t.draw(ui, "transparent on dither", pad + bw + 20, by + nm.height() + 8)
        y += tabH + bodyH + 6

        // 8. Rotations of a number, centred in framed squares
        val box = minOf(64, (iw - 3 * 6) / 4, h - y - ui.height() - 8)
        if (box >= nl.measureInt(40) + 2) {
            for (k in 0 until 4) {
                val bx = pad + k * (box + 6)
                r.frame(bx, y, box, box)
                t.drawIntIn(nl, 40, bx, y, box, box, rotation = k * 90)
                t.drawIn(ui, ROT_LABELS[k], bx, y + box + 2, box, ui.height())
            }
        }
    }

    private companion object {
        val ROLES = Role.entries
        val ROT_LABELS = arrayOf("R0", "R90", "R180", "R270")
        const val PAGE_ZONE = 0.08f
        const val LIFE = 40
    }
}
