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
 * An entry row reads, left to right: [icon] · [who] (or `who › who2` for a pair, e.g. commander
 * damage source → target) · … · [note] (`ui`) · [delta] `→` [value] (heavy `num-m` numerals,
 * right-aligned). Names are cut cleanly ([TextFit]); a row never wraps. Numbers win the room first,
 * then the note, then the names.
 */
class HistoryRow {

    var divider = false
        private set

    /** Turn number a divider stands for (0 = before turn tracking started). */
    var turn = 0
        private set

    var icon: Icon? = null
    var who: CharSequence = ""
    var who2: CharSequence? = null
    var note: CharSequence? = null
    var hasDelta = false
        private set
    var delta = 0
        private set
    var hasValue = false
        private set
    var value = 0
        private set

    /** Fallback-name scratch ("P3") for [who] / [who2]; reused, never reallocated. */
    internal val nameBuf = CharBuf(NAME_BUF)
    internal val nameBuf2 = CharBuf(NAME_BUF)

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
    val pairArrow = IRect()
    val who2Rect = IRect()
    var who2Chars = 0
        private set
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
        divider = false; turn = 0; icon = null; who = ""; who2 = null; note = null
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

    val isPair: Boolean get() = who2 != null

    /**
     * Places the parts in [slot] (local px, a list row) with metrics [m]; [arrowW]/[arrowH] is the
     * arrow image's size at the metrics' k.
     */
    fun layout(m: WidgetMetrics, slot: IRect, arrowW: Int, arrowH: Int) {
        for (r in parts) r.set(0, 0, 0, 0)
        whoChars = 0; who2Chars = 0; noteChars = 0
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
        val iconLeft = left
        val nameLeft = iconLeft + if (icon != null) m.icon + gap else 0
        if (icon != null) iconRect.setXywh(iconLeft, top + (h - m.icon) / 2, m.icon, m.icon)
        val n = note
        if (n != null) {
            val end = if (x == right) right else x - 2 * gap
            // Keep at least a few glyphs of the name before the note.
            val room = end - nameLeft - m.textW(MIN_NAME)
            noteChars = TextFit.fit(n, m.ui, k, maxOf(0, room))
            if (noteChars > 0) {
                val w = m.ui.widthOf(noteChars, k)
                noteRect.set(end - w, top, end, top + h)
                x = noteRect.left
            }
        }
        val nameRight = if (x == right) right else x - 2 * gap
        val room = nameRight - nameLeft
        if (room <= 0) return
        val w2 = who2
        if (w2 == null) {
            whoChars = TextFit.fit(who, m.ui, k, room)
            whoRect.set(nameLeft, top, nameLeft + m.ui.widthOf(whoChars, k), top + h)
            return
        }
        // A pair: share the room, lending what the shorter name doesn't need to the other.
        val names = room - arrowW - 2 * ag
        if (names <= 0) return
        val nat1 = m.ui.widthOf(who.length, k)
        val nat2 = m.ui.widthOf(w2.length, k)
        val half = names / 2
        val a1 = if (nat1 + nat2 <= names) nat1 else if (nat2 < half) names - nat2 else if (nat1 < half) nat1 else half
        whoChars = TextFit.fit(who, m.ui, k, a1)
        val used1 = m.ui.widthOf(whoChars, k)
        whoRect.set(nameLeft, top, nameLeft + used1, top + h)
        pairArrow.setXywh(whoRect.right + ag, arrowY, arrowW, arrowH)
        val x2 = pairArrow.right + ag
        who2Chars = TextFit.fit(w2, m.ui, k, nameRight - x2)
        who2Rect.set(x2, top, x2 + m.ui.widthOf(who2Chars, k), top + h)
    }

    /**
     * A one-line snapshot of the row's content, for tests and logs (allocates):
     * `[life] Alice | −7 → 33`, `[commander] Bob > Cleo | +7 → 7`, `--- TURN 4 ---`.
     */
    override fun toString(): String {
        if (divider) return "--- $who ---"
        val sb = StringBuilder()
        sb.append('[').append(icon?.name?.lowercase()?.replace('_', '-') ?: "-").append("] ").append(who)
        who2?.let { sb.append(" > ").append(it) }
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
        iconRect, whoRect, pairArrow, who2Rect, noteRect, deltaRect, arrowRect, valueRect, labelRect, ruleLeft, ruleRight,
    )

    companion object {
        const val NAME_BUF = 16
        const val NUM_BUF = 12

        /** Name glyphs a note never squeezes out. */
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
 * The little `→` between a delta and its result (and between a pair of names): a 9×6 heavy arrow
 * matching `num-m`'s 2-px horizontals. Stable instances per k (the renderer caches per instance).
 */
class HistoryArrow private constructor(private val k: Int) : MonoImage {
    override val width: Int = W * k
    override val height: Int = H * k
    override fun isInk(x: Int, y: Int): Boolean = ROWS[y / k][x / k] == '#'

    companion object {
        const val W = 9
        const val H = 6
        private val ROWS = arrayOf(
            "......#..",
            "......##.",
            "#########",
            "#########",
            "......##.",
            "......#..",
        )
        private val cache = arrayOfNulls<HistoryArrow>(8)

        fun of(k: Int): HistoryArrow {
            if (k !in cache.indices) return HistoryArrow(k)
            return cache[k] ?: HistoryArrow(k).also { cache[k] = it }
        }
    }
}
