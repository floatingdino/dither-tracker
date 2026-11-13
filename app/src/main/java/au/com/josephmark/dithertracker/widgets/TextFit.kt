package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.type.Font

/**
 * Fitting monospace text into a width — pure, allocation-free.
 *
 * **Clean truncation rule (no ellipsis).** When a label is too long it is cut, never squeezed or
 * given "…" (a 1-bit ellipsis costs three glyphs and reads as noise at this size):
 * 1. keep as many whole glyphs as fit;
 * 2. if that cuts a word and an earlier word boundary keeps at least [WORD_KEEP_NUM]/[WORD_KEEP_DEN]
 *    of the room, cut at that boundary instead (whole words read better than a stub);
 * 3. drop trailing separators (space, `-`, `:`, `,`, `.`, `/`) so the cut never ends on punctuation.
 */
object TextFit {

    const val WORD_KEEP_NUM = 2
    const val WORD_KEEP_DEN = 3

    /** How many glyphs of [font] at scale [k] fit in [width] dither px (0 when not even one). */
    fun maxChars(font: Font, k: Int, width: Int): Int {
        if (width < font.glyphWidth * k) return 0
        return (width + font.tracking * k) / (font.advance * k)
    }

    /** Number of leading chars of [text] to draw in [width] (see the class rule). */
    fun fit(text: CharSequence, font: Font, k: Int, width: Int): Int {
        val n = text.length
        val cap = maxChars(font, k, width)
        if (n <= cap) return n
        var end = cap
        if (end > 0 && !isSep(text[end])) {
            // The cut lands inside a word: back up to the last separator if that keeps enough.
            var i = end
            while (i > 0 && !isSep(text[i - 1])) i--
            if (i > 0 && i * WORD_KEEP_DEN >= cap * WORD_KEEP_NUM) end = i
        }
        while (end > 0 && isSep(text[end - 1])) end--
        return end
    }

    /** Separator characters a clean cut never ends on. */
    fun isSep(c: Char): Boolean = c == ' ' || c == '-' || c == ':' || c == ',' || c == '.' || c == '/' || c == '\n'
}

/**
 * A reusable window onto part of another [CharSequence] (for drawing a truncated label or one
 * wrapped line without allocating a String).
 */
class TextSlice : CharSequence {
    private var src: CharSequence = ""
    private var start = 0
    private var end = 0

    fun set(src: CharSequence, start: Int, end: Int): TextSlice {
        require(start in 0..end && end <= src.length) { "slice $start..$end of ${src.length}" }
        this.src = src; this.start = start; this.end = end
        return this
    }

    override val length: Int get() = end - start
    override fun get(index: Int): Char = src[start + index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = src.subSequence(start + startIndex, start + endIndex)
    override fun toString(): String = src.subSequence(start, end).toString()
}

/** A small mutable char buffer (e.g. a "2/5" page indicator) that is its own [CharSequence]. */
class CharBuf(capacity: Int) : CharSequence {
    private val chars = CharArray(capacity)
    private var len = 0

    fun clear(): CharBuf { len = 0; return this }

    fun append(c: Char): CharBuf { chars[len++] = c; return this }

    /** Appends a non-negative or negative int in decimal (ASCII '-'). */
    fun append(value: Int): CharBuf {
        if (value < 0) append('-')
        val n = Font.intLength(value) - if (value < 0) 1 else 0
        var v = if (value > 0) -value else value // negatives: no MIN_VALUE overflow
        for (i in n - 1 downTo 0) {
            chars[len + i] = '0' + -(v % 10)
            v /= 10
        }
        len += n
        return this
    }

    override val length: Int get() = len
    override fun get(index: Int): Char = chars[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = String(chars, startIndex, endIndex - startIndex)
    override fun toString(): String = String(chars, 0, len)
}
