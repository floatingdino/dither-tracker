package au.com.josephmark.dithertracker.history

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.widgets.CharBuf
import au.com.josephmark.dithertracker.widgets.TextFit
import au.com.josephmark.dithertracker.widgets.WidgetMetrics

/**
 * One display row of the game history (T18): either a log entry or a small "TURN 4" divider. A
 * mutable, pooled object — [HistoryFormat.format] fills it from a `LogEntry`, [layout] places its
 * parts in a list slot (local px), the card paints it. Filling and laying out never allocate.
 *
 * An entry row reads, left to right: [icon] (the event kind) · the affected player's seat glyph
 * ([seat], `seat › seat2` for a pair, e.g. commander damage source → target; T44) · … · [note] (`ui`) ·
 * [delta] `→` [value] (heavy `num-m` numerals, right-aligned). Player names are never shown (Sam,
 * 2026-11-14): a seat is a little picture of the table with that seat filled. Rows with no player (day
 * / night, new game, settings) show a constant label [who] instead. A row never wraps; numbers win the
 * room first, then the note, then the label (cut cleanly, [TextFit]).
 */
class HistoryRow {

    var divider = false
        private set

    /** Turn number a divider stands for (0 = before turn tracking started). */
    var turn = 0
        private set

    var icon: Icon? = null

    /** Seat glyph of the affected player: a layout seat, `SeatGlyphs.EMPTY` (no longer seated) or [NO_SEAT]. */
    var seat = NO_SEAT

    /** Second seat of a pair (commander damage: [seat] is the source, this the target), or [NO_SEAT]. */
    var seat2 = NO_SEAT

    /** Constant label for rows without a player ("Day breaks", "New game"); empty on player rows. */
    var who: CharSequence = ""
    var note: CharSequence? = null
    var hasDelta = false
        private set
    var delta = 0
        private set
    var hasValue = false
        private set
    var value = 0
        private set

    /** Divider label ("TURN 12"). */
    internal val label = CharBuf(NAME_BUF)

    /** Formatted numbers, ASCII `+`/`-` (the `num-m` face maps `-` to its minus). */
    val deltaText = CharBuf(NUM_BUF)
    val valueText = CharBuf(NUM_BUF)

    // ---- layout results (local px; empty when absent) -------------------------------------------
    val iconRect = IRect()
    val whoRect = IRect()
    var whoChars = 0
        private set

    /** Seat glyph slots (an icon-sized square each; the glyph is centred in it) and the pair arrow. */
    val seatRect = IRect()
    val pairArrow = IRect()
    val seat2Rect = IRect()
    val noteRect = IRect()
    var noteChars = 0
        private set
    val deltaRect = IRect()
    val arrowRect = IRect()
    val valueRect = IRect()

    /** Divider: the label's rect and the two dotted rules either side (1 px tall). */
    val labelRect = IRect()
    val ruleLeft = IRect()
    val ruleRight = IRect()

    fun clear(): HistoryRow {
        divider = false; turn = 0; icon = null; seat = NO_SEAT; seat2 = NO_SEAT; who = ""; note = null
        hasDelta = false; delta = 0; hasValue = false; value = 0
        return this
    }

    fun setDivider(turn: Int): HistoryRow {
        clear()
        divider = true
        this.turn = turn
        if (turn > 0) label.clear().appendChars(TURN).append(turn) else label.clear().appendChars(PREGAME)
        who = label
        return this
    }

    fun setDelta(delta: Int, value: Int): HistoryRow {
        hasDelta = true; this.delta = delta
        hasValue = true; this.value = value
        signed(deltaText.clear(), delta)
        valueText.clear().append(value)
        return this
    }

    fun setValue(value: Int): HistoryRow {
        hasValue = true; this.value = value
        valueText.clear().append(value)
        return this
    }

    val isPair: Boolean get() = seat2 != NO_SEAT

    val hasSeat: Boolean get() = seat != NO_SEAT

    /**
     * Places the parts in [slot] (local px, a list row) with metrics [m]; [arrowW]/[arrowH] is the
     * heavy value arrow's size at the metrics' k, [pairW]/[pairH] the thin name-pair arrow's.
     */
    fun layout(m: WidgetMetrics, slot: IRect, arrowW: Int, arrowH: Int, pairW: Int = arrowW, pairH: Int = arrowH) {
        for (r in parts) r.set(0, 0, 0, 0)
        whoChars = 0; noteChars = 0
        val top = slot.top
        val h = slot.height
        val left = slot.left + m.padX
        val right = slot.right - m.padX
        if (divider) {
            val n = TextFit.fit(who, m.ui, m.k, right - left)
            whoChars = n
            val tw = m.ui.widthOf(n, m.k)
            val lx = slot.left + (slot.width - tw) / 2
            labelRect.setXywh(lx, top, tw, h)
            val ry = top + h / 2
            val g = m.gap
            if (lx - g > left) ruleLeft.set(left, ry, lx - g, ry + 1)
            if (right > lx + tw + g) ruleRight.set(lx + tw + g, ry, right, ry + 1)
            return
        }
        val num = m.num
        val k = m.k
        val gap = m.gap
        val ag = arrowGap(m)
        val arrowY = top + (h - arrowH) / 2
        // Right cluster, laid out right → left: value, arrow, delta, note.
        var x = right
        if (hasValue) {
            val w = num.widthOf(valueText.length, k)
            valueRect.set(x - w, top, x, top + h)
            x -= w
        }
        if (hasDelta) {
            if (hasValue) {
                arrowRect.setXywh(x - ag - arrowW, arrowY, arrowW, arrowH)
                x = arrowRect.left - ag
            }
            val w = num.widthOf(deltaText.length, k)
            deltaRect.set(x - w, top, x, top + h)
            x -= w
        }
        // Left cluster: kind icon, seat glyph(s) (fixed size, never cut), then the label.
        val iconY = top + (h - m.icon) / 2
        var l = left
        if (icon != null) {
            iconRect.setXywh(l, iconY, m.icon, m.icon)
            l = iconRect.right + gap
        }
        if (seat != NO_SEAT) {
            seatRect.setXywh(l, iconY, m.icon, m.icon)
            l = seatRect.right + gap
            if (seat2 != NO_SEAT) {
                pairArrow.setXywh(seatRect.right + ag, top + (h - pairH) / 2, pairW, pairH)
                seat2Rect.setXywh(pairArrow.right + ag, iconY, m.icon, m.icon)
                l = seat2Rect.right + gap
            }
        }
        val end = if (x == right) right else x - 2 * gap
        val n = note
        if (n != null) {
            noteChars = TextFit.fit(n, m.ui, k, maxOf(0, end - l - if (who.isEmpty()) 0 else m.textW(MIN_NAME)))
            if (noteChars > 0) {
                val w = m.ui.widthOf(noteChars, k)
                noteRect.set(end - w, top, end, top + h)
            }
        }
        if (who.isEmpty()) return
        val labelRight = if (noteChars > 0) noteRect.left - 2 * gap else end
        whoChars = TextFit.fit(who, m.ui, k, maxOf(0, labelRight - l))
        if (whoChars > 0) whoRect.set(l, top, l + m.ui.widthOf(whoChars, k), top + h)
    }

    /**
     * A one-line snapshot of the row's content, for tests and logs (allocates):
     * `[life] s2 | −7 → 33` (seat glyph of seat 2), `[commander] s3 > s1 | +7 → 7`, `[life] s- | …`
     * (no longer seated), `[day] Day breaks`, `--- TURN 4 ---`.
     */
    override fun toString(): String {
        if (divider) return "--- $who ---"
        val sb = StringBuilder()
        sb.append('[').append(icon?.name?.lowercase()?.replace('_', '-') ?: "-").append("]")
        if (seat != NO_SEAT) sb.append(" ").append(seatTag(seat))
        if (seat2 != NO_SEAT) sb.append(" > ").append(seatTag(seat2))
        if (who.isNotEmpty()) sb.append(' ').append(who)
        val right = StringBuilder()
        note?.let { right.append(it) }
        if (hasDelta) {
            if (right.isNotEmpty()) right.append(' ')
            right.append(deltaText.toString().replace('-', '−')).append(" → ").append(valueText.toString().replace('-', '−'))
        } else if (hasValue) {
            if (right.isNotEmpty()) right.append(' ')
            right.append(valueText.toString().replace('-', '−'))
        }
        if (right.isNotEmpty()) sb.append(" | ").append(right)
        return sb.toString()
    }

    private val parts = arrayOf(
        iconRect, seatRect, pairArrow, seat2Rect, whoRect, noteRect, deltaRect, arrowRect, valueRect, labelRect, ruleLeft, ruleRight,
    )

    companion object {
        const val NAME_BUF = 16
        const val NUM_BUF = 12

        /** No seat glyph on this row (not about a player). */
        const val NO_SEAT = -2

        private fun seatTag(s: Int) = if (s < 0) "s-" else "s$s"

        /** Label glyphs a note never squeezes out. */
        const val MIN_NAME = 4

        /** Paper either side of an arrow: 1.5 × the kit's gap (6 px at S = 4) so `+3 → 3` doesn't clump. */
        fun arrowGap(m: WidgetMetrics): Int = m.gap + m.gap / 2

        const val TURN = "TURN "
        const val PREGAME = "PREGAME"

        private fun signed(buf: CharBuf, v: Int): CharBuf {
            if (v > 0) buf.append('+')
            return buf.append(v)
        }

        private fun CharBuf.appendChars(s: String): CharBuf {
            for (i in s.indices) append(s[i])
            return this
        }
    }
}

/**
 * The little arrows: [heavy] `→` between a delta and its result (a 9×6 arrow matching `num-m`'s 2-px
 * horizontals) and [thin] `→` between a pair of names (7×5, the `ui` face's 1-px pen), so a
 * commander row's "source → target" never reads as a second number change. Stable instances per k
 * (the renderer caches per instance).
 */
class HistoryArrow private constructor(private val rows: Array<String>, private val k: Int) : MonoImage {
    override val width: Int = rows[0].length * k
    override val height: Int = rows.size * k
    override fun isInk(x: Int, y: Int): Boolean = rows[y / k][x / k] == '#'

    companion object {
        private val HEAVY = arrayOf(
            "......#..",
            "......##.",
            "#########",
            "#########",
            "......##.",
            "......#..",
        )
        private val THIN = arrayOf(
            "....#..",
            ".....#.",
            "#######",
            ".....#.",
            "....#..",
        )
        private val heavyCache = arrayOfNulls<HistoryArrow>(8)
        private val thinCache = arrayOfNulls<HistoryArrow>(8)

        fun heavy(k: Int): HistoryArrow = of(HEAVY, heavyCache, k)
        fun thin(k: Int): HistoryArrow = of(THIN, thinCache, k)

        private fun of(rows: Array<String>, cache: Array<HistoryArrow?>, k: Int): HistoryArrow {
            if (k !in cache.indices) return HistoryArrow(rows, k)
            return cache[k] ?: HistoryArrow(rows, k).also { cache[k] = it }
        }
    }
}
