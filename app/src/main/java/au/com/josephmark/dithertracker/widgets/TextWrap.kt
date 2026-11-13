package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.type.Font

/**
 * Greedy word wrap for a monospace face into at most [maxLines] lines — pure, reusable, and
 * allocation-free per [wrap] (results live in [start]/[end]).
 *
 * Rules:
 * - lines break at spaces; the breaking space(s) are dropped, as are leading/trailing spaces;
 * - `\n` forces a break (consecutive breaks collapse — prompts never need blank lines);
 * - a word longer than a whole line is broken at the last glyph that fits;
 * - text left over after [maxLines] lines is dropped and [truncated] set; since lines always end on a
 *   word boundary (unless a single word is wider than the line), the cut is clean — no ellipsis.
 */
class TextWrap(val maxLines: Int) {
    init {
        require(maxLines >= 1) { "maxLines must be >= 1" }
    }

    /** Line i is `text[start[i], end[i])`. */
    val start = IntArray(maxLines)
    val end = IntArray(maxLines)

    var lines = 0
        private set

    /** True when some non-space text did not fit. */
    var truncated = false
        private set

    /** Length in chars of the longest line. */
    var widest = 0
        private set

    /** Width in dither px of the longest line at the last [wrap]'s font and scale. */
    var widestWidth = 0
        private set

    fun lineLength(i: Int): Int = end[i] - start[i]

    /** Wraps [text] in [font] at glyph scale [k] to lines no wider than [maxWidth]; returns [lines]. */
    fun wrap(text: CharSequence, font: Font, k: Int, maxWidth: Int): Int {
        val n = text.length
        val cap = TextFit.maxChars(font, k, maxWidth)
        lines = 0; widest = 0; truncated = false
        var pos = skipSpace(text, 0)
        if (cap > 0) {
            while (lines < maxLines && pos < n) {
                val lineStart = pos
                val limit = minOf(n, pos + cap)
                var e: Int
                var next: Int
                val nl = indexOf(text, '\n', pos, limit)
                if (nl >= 0) {
                    e = nl; next = nl + 1
                } else if (limit == n || text[limit] == ' ' || text[limit] == '\n') {
                    e = limit; next = limit
                } else {
                    var sp = limit - 1
                    while (sp > pos && text[sp] != ' ') sp--
                    if (sp > pos) {
                        e = sp; next = sp + 1
                    } else {
                        e = limit; next = limit // one long word: hard break
                    }
                }
                while (e > lineStart && text[e - 1] == ' ') e--
                start[lines] = lineStart
                end[lines] = e
                lines++
                if (e - lineStart > widest) widest = e - lineStart
                pos = skipSpace(text, next)
            }
        }
        truncated = pos < n
        widestWidth = font.widthOf(widest, k)
        return lines
    }

    private fun skipSpace(text: CharSequence, from: Int): Int {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\n')) i++
        return i
    }

    private fun indexOf(text: CharSequence, c: Char, from: Int, until: Int): Int {
        for (i in from until until) if (text[i] == c) return i
        return -1
    }
}
