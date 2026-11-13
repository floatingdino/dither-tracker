package au.com.josephmark.dithertracker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Patterns
import au.com.josephmark.dithertracker.dither.Role

/**
 * Engine test card (T04). Top to bottom, all in dither px:
 *  1. Bayer D0..D16, left→right, wrapping.
 *  2. Named tiles in [Patterns.named] order (CHECKER, SPARSE, DOTS, HLINES, VLINES, DIAG, CROSSHATCH).
 *  3. Roles in [Role] order.
 *  4. A drawer mock (TAB bar over DRAWER body, ink button) abutting a MENU block — seams must be
 *     invisible — crossed by a dotted horizontal + vertical divider.
 *  5. Every [Icon], in enum order.
 *  6. First icon at 0/90/180/270°, then the same opaque + inverted.
 *  7. Left: swatches + icons with [DitherRenderer.invertRect] over the right half.
 *     Right: the same content redrawn inside [DitherRenderer.withInverted]. Both halves must match.
 * Tap anywhere toggles global dark mode.
 */
class DitherDemoView(context: Context) : View(context) {

    private val r = DitherRenderer()

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
            r.inverted = !r.inverted
            invalidate()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        r.begin(canvas)
        r.clear()
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

    private companion object {
        val ROLES = Role.entries
    }
}
