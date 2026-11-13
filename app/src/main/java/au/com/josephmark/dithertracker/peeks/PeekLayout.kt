package au.com.josephmark.dithertracker.peeks

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts

/**
 * Counter peeks, `split` layout (T38; designer's pick from `design/font/candidates/mini2/`): while a
 * player's drawer is closed, each non-zero tray item (commander tax, partner tax, then the drawer's
 * counters, in the drawer's order) shows as a small chip — icon then value in `num-m` at k=1 — in
 * the two **near corners** of the tile (player's frame), either side of the tab. Pure maths,
 * JVM-tested.
 *
 * ```
 *   far edge   (the centre diamond sits in one far corner on every seat)
 *  ┌───────────────────────────────┐
 *  │  −          number          + │
 *  │ [4]                       [5] │  slot i: column i % 2 (0 left, 1 right),
 *  │ [2]                       [3] │          row i / 2 counted up from the near edge
 *  │ [0]     ┌── tab ──┐       [1] │
 *  └─────────┴─────────┴───────────┘
 *   near edge (the player)
 * ```
 * The left column is flush with [PlayLayout.EDGE_MIN] from the left edge, the right column
 * right-aligned [PlayLayout.EDGE_MIN] from the right edge (chips and `+N` alike). Each column is
 * [colW] wide and stops [PlayLayout.V_MIN] + [pad] short of the tab's hit band; rows are [pitch]
 * apart, their content from [PlayLayout.V_MIN] above the near edge up to [PlayLayout.V_MIN] below
 * the number slot (an inverted chip's [pad] may use part of that), at most [MAX_ROWS] high. The near corners are free on every seat (the diamond is always at a
 * far corner), so the layout is identical for all four players.
 *
 * When more items are active than [slots], the last slot shows `+N` for the rest ([arrange]).
 * A value that is too wide for the column at the face's tracking closes up to [TIGHT_TRACKING]
 * (only 3-digit values ever do). Display only: the life halves keep their touches underneath.
 */
class PeekLayout(val play: PlayLayout) {

    val num: Font = Fonts.NUM_M
    val tk: Int = play.tk
    val iconSize: Int = PlayLayout.ICON * tk
    val iconGap: Int = PlayLayout.Chrome.size(ICON_GAP, play.grid.scale)

    /** Ink margin round an inverted ("nearly lethal") chip's content. */
    val pad: Int = PlayLayout.Chrome.size(PAD, play.grid.scale)

    /** Row pitch: an icon plus [pad] (an inverted card's pad fills the gap, never a neighbour's content). */
    val pitch: Int = iconSize + pad

    /** Player-frame x of the left column's left edge and of the right column's right edge. */
    val leftX: Int = PlayLayout.EDGE_MIN
    val rightX: Int = play.panelW - PlayLayout.EDGE_MIN

    /** Content width available to one chip in either column. */
    val colW: Int

    /** Rows per corner and the player-frame top of the bottom row's slot. */
    val rows: Int
    val bottomY: Int

    /** Slots in all (both corners). */
    val slots: Int get() = 2 * rows

    /** True when the corners hold at least one row and a 2-digit chip fits a column. */
    val enabled: Boolean

    init {
        val c = play.chrome
        val tabX = (play.panelW - minOf(c.tabW, play.panelW)) / 2
        val hitLeft = tabX - c.tabHitPad
        colW = hitLeft - PlayLayout.V_MIN - pad - leftX
        val slotBottom = play.numberSlotY + play.font.height(play.kMax)
        // Chip content stays V_MIN clear of the number slot and the near edge; an inverted chip's
        // pad may take up to [pad] of that clearance.
        val top = slotBottom + PlayLayout.V_MIN
        val bottom = play.panelH - PlayLayout.V_MIN
        val gap = pitch - iconSize
        rows = ((bottom - top + gap) / pitch).coerceIn(0, MAX_ROWS)
        bottomY = bottom - iconSize
        enabled = rows > 0 && contentWidth(99) <= colW
    }

    /** Tracking (font px) for [value] in a column: the face's own, or tighter when that is too wide. */
    fun tracking(value: Int): Int {
        val len = Font.intLength(value)
        var t = num.tracking
        while (t > TIGHT_TRACKING && iconSize + iconGap + num.widthOf(len, tk, t) > colW) t--
        return t
    }

    /** Width of a chip's content (icon, gap, value), unpadded. */
    fun contentWidth(value: Int): Int = iconSize + iconGap + num.measureInt(value, tk, tracking(value))

    /** Width of a `+N` chip's text. */
    fun moreWidth(more: Int): Int = num.measureInt(more, tk, signed = true)

    /**
     * How many of [n] active items get their own chip: all of them when they fit the [slots],
     * otherwise `slots − 1` and the last slot is `+N` with `N = n − shown`. 0 when disabled.
     */
    fun shown(n: Int): Int = if (!enabled || n <= 0) 0 else if (n <= slots) n else slots - 1

    /** Column (0 left, 1 right) and row (0 nearest the player) of slot [i]. */
    fun column(i: Int): Int = i % 2
    fun row(i: Int): Int = i / 2

    /** Player-frame top of slot [i]'s row. */
    fun slotY(i: Int): Int = bottomY - row(i) * pitch

    /** Player-frame left of [w]-wide content in slot [i] (flush left, or right-aligned on the right). */
    fun slotX(i: Int, w: Int): Int = if (column(i) == 0) leftX else rightX - w

    /**
     * Screen rect of slot [i]'s card for content [w] wide: the content box on paper, or grown by
     * [pad] when [inverted] (the ink card). Rows are [pitch] apart, so an ink card's pad fills the
     * gap to its neighbours' content without covering it.
     */
    fun chip(seat: Int, i: Int, w: Int, inverted: Boolean, out: IRect) {
        val p = if (inverted) pad else 0
        play.mapRect(seat, slotX(i, w) - p, slotY(i) - p, w + 2 * p, iconSize + 2 * p, out)
    }

    /** Screen footprint of slot [i]'s icon for a chip showing [value]. */
    fun icon(seat: Int, i: Int, value: Int, out: IRect) =
        play.mapRect(seat, slotX(i, contentWidth(value)), slotY(i), iconSize, iconSize, out)

    /**
     * Screen footprint of slot [i]'s value; draw with
     * `TextRenderer.drawInt(num, value, …, tk, rotation, tracking = tracking(value))`.
     */
    fun value(seat: Int, i: Int, value: Int, out: IRect) {
        val t = tracking(value)
        val w = num.measureInt(value, tk, t)
        val h = num.height(tk)
        play.mapRect(seat, slotX(i, contentWidth(value)) + iconSize + iconGap, slotY(i) + (iconSize - h) / 2, w, h, out)
    }

    /** Screen footprint of the `+N` text in slot [i]. */
    fun more(seat: Int, i: Int, more: Int, out: IRect) {
        val w = moreWidth(more)
        val h = num.height(tk)
        play.mapRect(seat, slotX(i, w), slotY(i) + (iconSize - h) / 2, w, h, out)
    }

    /** Screen rect of one whole corner (column [col]) on [seat], pads included: what to invalidate. */
    fun corner(seat: Int, col: Int, out: IRect) {
        val top = slotY(2 * (rows - 1)) - pad
        val h = bottomY + iconSize + pad - top
        val x = if (col == 0) leftX - pad else rightX - colW - pad
        play.mapRect(seat, x, top, colW + 2 * pad, h, out)
    }

    override fun toString(): String =
        "PeekLayout(S=${play.grid.scale} ${if (enabled) "" else "DISABLED "}col ${colW}w, rows $rows pitch $pitch from y $bottomY, pad $pad)"

    companion object {
        /** Design values at S = 4. */
        const val ICON_GAP = 2
        const val PAD = 2

        /** Chips per corner (designer: three). */
        const val MAX_ROWS = 3

        /** Closest a value's digits ever sit (font px). */
        const val TIGHT_TRACKING = 1
    }
}
