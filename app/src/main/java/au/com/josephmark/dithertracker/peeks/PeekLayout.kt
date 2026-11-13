package au.com.josephmark.dithertracker.peeks

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts

/**
 * Counter peeks (T32): while a player's drawer is closed, each non-zero tray item (commander tax,
 * partner tax, then the drawer's counters, in the drawer's order) shows as a small chip — icon then
 * value in `num-m` — in [PlayLayout.underRow] (the row between the life total and the tab), so
 * active counters read at a glance without opening the drawer. Pure maths, JVM-tested.
 *
 * Chips are sized to their content (`icon + gap + digits`, plus [pad] each side for the inverted
 * "nearly lethal" chip) and centred as a group, [gap] apart. When they don't all fit the row, as
 * many as fit are shown followed by a `+N` chip for the rest ([arrange]). Display only: the row
 * keeps whatever touch zones are under it (the life halves).
 */
class PeekLayout(val play: PlayLayout) {

    val num: Font = Fonts.NUM_M
    val tk: Int = play.tk
    val iconSize: Int = PlayLayout.ICON * tk
    val iconGap: Int = PlayLayout.Chrome.size(ICON_GAP, play.grid.scale)
    val pad: Int = PlayLayout.Chrome.size(PAD, play.grid.scale)
    val gap: Int = PlayLayout.Chrome.size(GAP, play.grid.scale)

    /** True when the layout has an under row at all. */
    val enabled: Boolean get() = play.underH > 0

    fun chipWidth(value: Int): Int = pad + iconSize + iconGap + num.measureInt(value, tk) + pad

    fun moreWidth(more: Int): Int = pad + num.measureInt(more, tk, signed = true) + pad

    /**
     * Places [n] chips with values `values[0 until n]` in the row: writes each shown chip's
     * player-frame x into [xs] (`xs[shown]` is the `+N` chip's x when some are left over) and returns
     * the number of chips shown (`n − shown` go into the `+N`). Allocation-free.
     */
    fun arrange(values: IntArray, n: Int, xs: IntArray): Int {
        if (!enabled || n <= 0) return 0
        val avail = play.underW
        var shown = n
        while (shown > 0 && widthOf(values, shown, n) > avail) shown--
        val total = widthOf(values, shown, n)
        if (shown == 0 && total > avail) return 0
        var x = play.underX + (avail - total) / 2
        for (i in 0 until shown) {
            xs[i] = x
            x += chipWidth(values[i]) + gap
        }
        if (shown < n) xs[shown] = x
        return shown
    }

    /** Total width of the first [shown] chips plus, when [shown] < [n], the `+N` chip. */
    fun widthOf(values: IntArray, shown: Int, n: Int): Int {
        var w = 0
        for (i in 0 until shown) w += chipWidth(values[i])
        var parts = shown
        if (shown < n) { w += moreWidth(n - shown); parts++ }
        return w + maxOf(0, parts - 1) * gap
    }

    /** Screen rect of a chip [w] wide at player-frame x [x] on [seat]. */
    fun chip(seat: Int, x: Int, w: Int, out: IRect) = play.mapRect(seat, x, play.underY, w, play.underH, out)

    /** Screen footprint of a chip's icon. */
    fun icon(seat: Int, x: Int, out: IRect) =
        play.mapRect(seat, x + pad, play.underY + (play.underH - iconSize) / 2, iconSize, iconSize, out)

    /** Screen footprint of a chip's value (draw with `TextRenderer.drawInt(num, value, …, tk, rotation)`). */
    fun value(seat: Int, x: Int, value: Int, out: IRect) {
        val w = num.measureInt(value, tk)
        play.mapRect(seat, x + pad + iconSize + iconGap, play.underY + (play.underH - num.height(tk)) / 2, w, num.height(tk), out)
    }

    /** Screen footprint of the `+N` text of a more-chip at x. */
    fun more(seat: Int, x: Int, more: Int, out: IRect) {
        val w = num.measureInt(more, tk, signed = true)
        play.mapRect(seat, x + pad, play.underY + (play.underH - num.height(tk)) / 2, w, num.height(tk), out)
    }

    companion object {
        /** Design values at S = 4. */
        const val ICON_GAP = 2
        const val PAD = 2
        const val GAP = 6
    }
}
