package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.MonoImage

/**
 * One 1-bit layer of one effect frame (its black `#` pixels, or its knock-out `o` pixels).
 *
 * Rows are packed MSB-first into 32-bit words, [stride] words per row (`(width + 31) / 32`):
 * column x of row y is bit `31 - (x % 32)` of `words[y * stride + x / 32]`. Unlike
 * [au.com.josephmark.dithertracker.dither.PackedImage] this allows widths over 32.
 *
 * Instances live in the generated [Fx] enum and are never recreated, so the renderer's per-image
 * mask cache (identity-keyed) builds each scaled mask once. Do not override equals/hashCode.
 */
class FxLayer(
    override val width: Int,
    override val height: Int,
    private val words: IntArray,
) : MonoImage {

    val stride: Int = (width + 31) / 32

    init {
        require(width >= 1 && height >= 1) { "size ${width}x$height" }
        require(words.size == stride * height) { "expected ${stride * height} words, got ${words.size}" }
    }

    override fun isInk(x: Int, y: Int): Boolean =
        (words[y * stride + (x ushr 5)] ushr (31 - (x and 31))) and 1 == 1

    /** Number of set pixels. */
    val inkCount: Int = words.sumOf { Integer.bitCount(it) }

    /** True when nothing is set: the renderer skips the blit (and never builds a mask for it). */
    val isEmpty: Boolean get() = inkCount == 0
}
