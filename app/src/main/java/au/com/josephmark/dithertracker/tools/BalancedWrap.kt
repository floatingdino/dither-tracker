package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.widgets.TextFit
import au.com.josephmark.dithertracker.widgets.TextWrap

/**
 * Balanced word wrap for a monospace face (T46): the fewest lines that hold the text, and among the
 * breaks giving that many lines the one whose **longest line is shortest** (ties: the most even
 * lengths, then the longer line first). So a caption that needs two lines splits into two of similar
 * length — "Make of that / what you will", never "Make of that what you / will" — and a short word is
 * only left alone on a line when no more even break exists.
 *
 * Pure and allocation-free per [wrap] (results in [start]/[end], like `widgets.TextWrap`). Lines
 * break at spaces only (a `\n` counts as a space); a word wider than a whole line, or more than
 * [maxWords] words, falls back to the greedy `TextWrap` (which hard-breaks and truncates).
 */
class BalancedWrap(val maxLines: Int, val maxWords: Int = 48) {
    init {
        require(maxLines >= 1) { "maxLines must be >= 1" }
    }

    /** Line i is `text[start[i], end[i])`. */
    val start = IntArray(maxLines)
    val end = IntArray(maxLines)

    var lines = 0
        private set

    /** True when some non-space text did not fit in [maxLines] lines. */
    var truncated = false
        private set

    fun lineLength(i: Int): Int = end[i] - start[i]

    private val greedy = TextWrap(maxLines)
    private val ws = IntArray(maxWords)
    private val we = IntArray(maxWords)
    private var words = 0
    private var cap = 0

    // Search state: the breaks being tried (word index each line starts at) and the best so far.
    private val trial = IntArray(maxLines + 1)
    private val best = IntArray(maxLines + 1)
    private var bestMax = 0
    private var bestSq = 0L
    private var bestFirst = 0
    private var found = false

    /** Wraps [text] in [font] at glyph scale [k] to lines no wider than [maxWidth]; returns [lines]. */
    fun wrap(text: CharSequence, font: Font, k: Int, maxWidth: Int): Int {
        lines = 0
        truncated = false
        cap = TextFit.maxChars(font, k, maxWidth)
        if (!split(text) || cap <= 0) return fallback(text, font, k, maxWidth)
        if (words == 0) return 0
        for (i in 0 until words) if (we[i] - ws[i] > cap) return fallback(text, font, k, maxWidth)
        for (n in 1..maxLines) {
            found = false
            trial[0] = 0
            trial[n] = words
            search(1, n)
            if (found) {
                for (i in 0 until n) {
                    start[i] = ws[best[i]]
                    end[i] = we[best[i + 1] - 1]
                }
                lines = n
                return n
            }
        }
        // Too long for maxLines lines even balanced: the greedy wrap fills and truncates cleanly.
        return fallback(text, font, k, maxWidth)
    }

    /** Chars of the line holding words [a, b). */
    private fun len(a: Int, b: Int) = we[b - 1] - ws[a]

    /** Chooses where line [i] (of [n]) starts, every earlier start fixed in [trial]. */
    private fun search(i: Int, n: Int) {
        if (i == n) {
            var mx = 0
            var sq = 0L
            for (j in 0 until n) {
                val l = len(trial[j], trial[j + 1])
                if (l > cap) return
                if (l > mx) mx = l
                sq += l.toLong() * l
            }
            val first = len(trial[0], trial[1])
            val better = !found || mx < bestMax || (mx == bestMax && (sq < bestSq || (sq == bestSq && first > bestFirst)))
            if (better) {
                found = true
                bestMax = mx; bestSq = sq; bestFirst = first
                for (j in 0..n) best[j] = trial[j]
            }
            return
        }
        // Every line keeps at least one word.
        for (s in trial[i - 1] + 1..words - (n - i)) {
            if (len(trial[i - 1], s) > cap) break
            trial[i] = s
            search(i + 1, n)
        }
    }

    /** Splits [text] into words at spaces / newlines; false when there are more than [maxWords]. */
    private fun split(text: CharSequence): Boolean {
        words = 0
        var i = 0
        val n = text.length
        while (i < n) {
            while (i < n && isSpace(text[i])) i++
            if (i >= n) break
            if (words == maxWords) return false
            ws[words] = i
            while (i < n && !isSpace(text[i])) i++
            we[words] = i
            words++
        }
        return true
    }

    private fun fallback(text: CharSequence, font: Font, k: Int, maxWidth: Int): Int {
        val n = greedy.wrap(text, font, k, maxWidth)
        for (i in 0 until n) { start[i] = greedy.start[i]; end[i] = greedy.end[i] }
        lines = n
        truncated = greedy.truncated
        return n
    }

    private fun isSpace(c: Char) = c == ' ' || c == '\n'
}
