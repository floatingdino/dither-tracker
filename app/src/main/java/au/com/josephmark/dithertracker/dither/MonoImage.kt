package au.com.josephmark.dithertracker.dither

/**
 * A 1-bit image in dither pixels: every pixel is either ink or paper. Pure data, no Android types.
 * Tiles, icons and (later) bitmap-font glyphs implement this so the renderer can blit them.
 *
 * Implementations must be immutable and should use identity equality: the renderer caches its
 * scaled bitmap per instance.
 */
interface MonoImage {
    val width: Int
    val height: Int

    /** True when (x, y) is ink (black in normal mode, white when inverted). 0 <= x < width. */
    fun isInk(x: Int, y: Int): Boolean
}

/** Row-packed 1-bit image: `rows[y]` holds `width` bits, MSB-first (bit `width-1-x` is column x). */
open class PackedImage(
    final override val width: Int,
    final override val height: Int,
    private val rows: IntArray,
) : MonoImage {
    init {
        require(width in 1..32) { "width $width out of 1..32" }
        require(rows.size == height && height >= 1) { "expected $height rows, got ${rows.size}" }
    }

    final override fun isInk(x: Int, y: Int): Boolean = (rows[y] ushr (width - 1 - x)) and 1 == 1

    /** Number of ink pixels. */
    val inkCount: Int = run {
        var n = 0
        for (y in 0 until height) for (x in 0 until width) if ((rows[y] ushr (width - 1 - x)) and 1 == 1) n++
        n
    }

    /** ASCII form, `#` ink / `.` paper, one line per row, each terminated by '\n'. */
    fun toAscii(): String = buildString {
        for (y in 0 until height) {
            for (x in 0 until width) append(if (isInk(x, y)) '#' else '.')
            append('\n')
        }
    }

    companion object {
        /** Packs ASCII rows (`#` ink, `.` paper) into row bitmasks. Used at class-init time only. */
        fun pack(ascii: Array<out String>): IntArray {
            require(ascii.isNotEmpty())
            val w = ascii[0].length
            return IntArray(ascii.size) { y ->
                val row = ascii[y]
                require(row.length == w) { "row $y has length ${row.length}, expected $w" }
                var bits = 0
                for (c in row) {
                    bits = bits shl 1
                    bits = bits or when (c) {
                        '#' -> 1
                        '.' -> 0
                        else -> throw IllegalArgumentException("bad char '$c' in row $y")
                    }
                }
                bits
            }
        }
    }
}
