package au.com.josephmark.dithertracker.type

import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.PackedImage

/**
 * One glyph of a [Font]: a 1-bit bitmap exactly `glyphWidth × glyphHeight` font px.
 * [char] is `'\u0000'` for the face's `.notdef` box.
 */
class Glyph internal constructor(val char: Char, width: Int, height: Int, rows: IntArray) :
    PackedImage(width, height, rows) {

    /** True for glyphs with no ink (space): drawing skips the blit. */
    val isBlank: Boolean get() = inkCount == 0

    private val scaled = arrayOfNulls<MonoImage>(Font.MAX_K + 1)

    /**
     * This glyph enlarged k× (nearest neighbour), as a stable [MonoImage] instance so the renderer's
     * per-image bitmap cache hits. Allocated once per (glyph, k), on first use.
     */
    fun scaled(k: Int): MonoImage {
        if (k == 1) return this
        require(k in 1..Font.MAX_K) { "glyph scale $k out of 1..${Font.MAX_K}" }
        scaled[k]?.let { return it }
        return Scaled(this, k).also { scaled[k] = it }
    }

    override fun toString(): String = if (char == '\u0000') "Glyph(notdef)" else "Glyph('$char')"

    private class Scaled(private val src: Glyph, private val k: Int) : MonoImage {
        override val width: Int = src.width * k
        override val height: Int = src.height * k
        override fun isInk(x: Int, y: Int): Boolean = src.isInk(x / k, y / k)
    }
}

/**
 * A monospace 1-bit bitmap face. Built from `design/font/<face>/glyphs.txt` by `:app:generateFonts`
 * (see [Fonts]); all units are font pixels, which become `k × S` device px when drawn at glyph scale k.
 *
 * Every glyph has the same box [glyphWidth] × [glyphHeight]; glyphs are laid out [advance] apart,
 * i.e. with [tracking] px of paper between boxes. The cap band — rows `[capTop, capTop + capHeight)`
 * — is what vertical centring aligns (caps and digits sit in it; descenders hang below).
 */
class Font(
    val name: String,
    val glyphWidth: Int,
    val glyphHeight: Int,
    /** Default gap between glyph boxes, font px. */
    val tracking: Int,
    val capTop: Int,
    val capHeight: Int,
    chars: String,
    rows: IntArray,
) {
    /** Glyphs in source order (excluding [notdef]). */
    val glyphs: List<Glyph>

    /** The fallback box drawn for characters the face does not have. */
    val notdef: Glyph

    /** Row of the baseline (first row below the cap band). */
    val baseline: Int get() = capTop + capHeight

    /** Distance between successive glyph origins at the default tracking, font px. */
    val advance: Int get() = glyphWidth + tracking

    /** The character used for negative numbers: a true minus (U+2212) when the face has one. */
    val minusChar: Char

    private val ascii = arrayOfNulls<Glyph>(128)
    private val extraChars: CharArray
    private val extraGlyphs: Array<Glyph>

    init {
        require(rows.size == (chars.length + 1) * glyphHeight) {
            "$name: ${rows.size} rows for ${chars.length} glyphs + notdef of height $glyphHeight"
        }
        val list = ArrayList<Glyph>(chars.length)
        val extras = ArrayList<Glyph>()
        for (i in chars.indices) {
            val g = Glyph(chars[i], glyphWidth, glyphHeight, rows.copyOfRange(i * glyphHeight, (i + 1) * glyphHeight))
            list += g
            if (g.char.code < 128) ascii[g.char.code] = g else extras += g
        }
        glyphs = list
        notdef = Glyph('\u0000', glyphWidth, glyphHeight, rows.copyOfRange(chars.length * glyphHeight, rows.size))
        extraChars = CharArray(extras.size) { extras[it].char }
        extraGlyphs = extras.toTypedArray()
        minusChar = if (find(MINUS) != null) MINUS else '-'
    }

    /** The glyph for [c] if the face has it (exactly), else null. */
    fun find(c: Char): Glyph? {
        if (c.code < 128) return ascii[c.code]
        for (i in extraChars.indices) if (extraChars[i] == c) return extraGlyphs[i]
        return null
    }

    fun has(c: Char): Boolean = find(c) != null

    /**
     * The glyph drawn for [c]: exact match, else a fallback (lowercase → uppercase, U+2212 minus → `-`,
     * `-` → U+2212), else [notdef]. Never allocates.
     */
    fun glyph(c: Char): Glyph {
        find(c)?.let { return it }
        if (c in 'a'..'z') find(c - 32)?.let { return it }
        if (c == MINUS) find('-')?.let { return it }
        if (c == '-') find(MINUS)?.let { return it }
        return notdef
    }

    /** Width in font px × [k] of [n] glyphs: `k·(n·w + (n−1)·tracking)`, 0 for none. */
    fun widthOf(n: Int, k: Int = 1, tracking: Int = this.tracking): Int =
        if (n <= 0) 0 else k * (n * glyphWidth + (n - 1) * tracking)

    /** Unrotated width of [text] in dither px at glyph scale [k]. */
    fun measure(text: CharSequence, k: Int = 1, tracking: Int = this.tracking): Int = widthOf(text.length, k, tracking)

    /** Width of [value] as drawn by [TextRenderer.drawInt] (with a `+` when [signed] and positive). */
    fun measureInt(value: Int, k: Int = 1, tracking: Int = this.tracking, signed: Boolean = false): Int =
        widthOf(intLength(value, signed), k, tracking)

    /** Height of a line in dither px at glyph scale [k] (the full glyph box, descenders included). */
    fun height(k: Int = 1): Int = glyphHeight * k

    override fun toString(): String = "Font($name ${glyphWidth}x$glyphHeight)"

    companion object {
        /** Largest supported glyph scale k. */
        const val MAX_K = 16

        const val MINUS = '−'

        /** Number of chars [value] formats to: digits, plus a sign when negative (or positive and [signed]). */
        fun intLength(value: Int, signed: Boolean = false): Int {
            var n = 1
            var v = value
            while (v <= -10 || v >= 10) { v /= 10; n++ }
            if (value < 0 || (signed && value > 0)) n++
            return n
        }

        /**
         * Writes [value] in decimal into [out] (from index 0) without allocating and returns the length.
         * Negative numbers start with [minus]; positive ones with `+` when [signed]. Handles
         * [Int.MIN_VALUE]. [out] needs room for 12 chars.
         */
        fun formatInt(value: Int, signed: Boolean, minus: Char, out: CharArray): Int {
            val len = intLength(value, signed)
            var i = len - 1
            var v = if (value > 0) -value else value // work in negatives: no MIN_VALUE overflow
            do {
                out[i--] = '0' + -(v % 10)
                v /= 10
            } while (v != 0)
            if (value < 0) out[0] = minus else if (signed && value > 0) out[0] = '+'
            return len
        }
    }
}
