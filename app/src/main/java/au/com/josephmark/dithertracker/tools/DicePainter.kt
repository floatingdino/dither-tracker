package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.CoinFace
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.menu.GemPainter
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.type.TextLayout
import au.com.josephmark.dithertracker.widgets.TextSlice

/**
 * Draws the dice panel through a [Frame] (dither px, root coordinates, glyph rotation only — never
 * `Canvas.rotate`), in the gem's language: the restart confirm's frame (`MENU` rim, ink border, dotted
 * fold), its two-faced fold buttons (outline; the current tool solid) and its fold diamond, here with
 * the `dice` icon knocked out (roll again). Shapes come from [GemPainter]. Allocation-free.
 */
internal class DicePainter {

    private val gem = GemPainter()
    private val slice = TextSlice()
    private val tmp = IRect()

    /**
     * The whole panel for [state]'s current result. [art] matches the layout's result scale. While the
     * state is in its beat the result shows the dither "?" and the caption is held back.
     */
    fun draw(f: Frame, l: DiceLayout, art: DiceArt, state: DiceState, labelText: CharSequence) {
        if (!f.hit(l.panelOuter)) return
        val r = f.r
        val o = l.panelOuter
        r.fill(Role.MENU, o.left + 1, o.top + 1, o.width - 2, o.height - 2)
        r.frame(o, 1, ink = true)
        r.rect(l.panel, ink = true)
        r.rect(l.faces[0], ink = false)
        r.rect(l.faces[1], ink = false)
        r.rect(l.fold, ink = false)
        r.fill(Role.DOTTED, l.fold.left, l.cy, l.fold.width, 1)

        val beat = state.phase == DiceState.BEAT
        for (face in 0 until 2) {
            if (!f.hit(l.faces[face])) continue
            val rot = l.rotation(face)
            result(f, l, art, state, face, rot, beat)
            f.text.draw(l.ui, labelText, l.label[face].left, l.label[face].top, l.k, rot)
            r.rect(l.underline[face], ink = true)
            if (beat) continue
            for (i in 0 until l.lines) {
                val lr = l.line[face * DiceLayout.MAX_LINES + i]
                slice.set(state.caption, l.lineStart[i], l.lineEnd[i])
                f.text.draw(l.ui, slice, lr.left, lr.top, l.k, rot)
            }
        }

        for (b in 0 until DiceLayout.BUTTONS) {
            val t = DiceLayout.toolOf(b)
            val bt = l.button[b]
            if (t == null) {
                r.rect(bt, ink = false)
                r.frame(bt, 1, ink = true)
                gem.cross(r, (bt.left + bt.right - 1) / 2, l.cy, l.xHalf, l.xRun, ink = true)
            } else {
                foldButton(f, l, bt, t.label, filled = t == state.tool)
            }
        }
        gem.diamond(r, l.cx, l.cy, l.foldR, l.foldHalf, ink = true)
        val ic = 16 * l.k
        tmp.setXywh(l.cx - ic / 2, l.cy - ic / 2, ic, ic)
        f.icons.icon(l.k, Icon.DICE, tmp, 0, invert = true, opaque = false)
    }

    private fun result(f: Frame, l: DiceLayout, art: DiceArt, state: DiceState, face: Int, rot: Int, beat: Boolean) {
        val box = l.resultBox[face]
        val r = f.r
        if (beat) {
            val m = art.mystery(rot)
            r.fillMask(m, box.left + (box.width - m.width) / 2, centredTop(l, box, m.height, rot), Role.SHADOW)
            return
        }
        if (state.tool.isCoin) {
            val heads = Tool.coinFace(state.value) == CoinFace.HEADS
            val c = art.coin(heads, rot)
            r.blit(c, box.left + (box.width - c.width) / 2, centredTop(l, box, c.height, rot))
            return
        }
        f.text.drawIntIn(l.result, state.value, box.left, box.top, box.width, box.height, l.resultK, rot, shadow = false)
    }

    /** Top of an [h]-tall cap-band mask centred on the result's cap band in [box] (turned with [rot]). */
    private fun centredTop(l: DiceLayout, box: IRect, h: Int, rot: Int): Int {
        val capTop = l.result.capTop * l.resultK
        val capH = l.result.capHeight * l.resultK
        val upright = capTop + (capH - h) / 2
        return if (rot == 180) box.bottom - upright - h else box.top + upright
    }

    /** A two-faced fold button: the label upright below the fold line and turned above it. */
    private fun foldButton(f: Frame, l: DiceLayout, b: IRect, label: String, filled: Boolean) {
        val r = f.r
        if (filled) r.rect(b, ink = true) else { r.rect(b, ink = false); r.frame(b, 1, ink = true) }
        tmp.set(b.left + 1, l.cy + 1, b.right - 1, b.bottom - 1)
        f.text.drawIn(l.ui, label, tmp.left, tmp.top, tmp.width, tmp.height, l.k, 0, invert = filled, align = TextLayout.CENTRE)
        tmp.set(b.left + 1, b.top + 1, b.right - 1, l.cy)
        f.text.drawIn(l.ui, label, tmp.left, tmp.top, tmp.width, tmp.height, l.k, 180, invert = filled, align = TextLayout.CENTRE)
    }
}
