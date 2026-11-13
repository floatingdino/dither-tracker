package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.MonoImage

/**
 * One 1-bit layer of one effect frame: the pixels of one symbol (`#` ink, `o` knock-out, `x` cut, or
 * one tile digit `1`–`9`).
 *
 * Rows are packed MSB-first into 32-bit words, [stride] words per row (`(width + 31) / 32`):
 * column x of row y is bit `31 - (x % 32)` of `words[y * stride + x / 32]`. Unlike
 * [au.com.josephmark.dithertracker.dither.PackedImage] this allows widths over 32. An empty `words`
 * array means "nothing set" (no storage for the many empty layers).
 *
 * Instances live in generated data objects and are never recreated, so the renderer's per-image
 * mask cache (identity-keyed) builds each scaled mask once. Do not override equals/hashCode.
 * The same holds for the turned views from [rotated], which are created once and kept.
 */
class FxLayer(
    override val width: Int,
    override val height: Int,
    private val words: IntArray,
) : MonoImage {

    val stride: Int = (width + 31) / 32

    init {
        require(width >= 1 && height >= 1) { "size ${width}x$height" }
        require(words.isEmpty() || words.size == stride * height) { "expected ${stride * height} words, got ${words.size}" }
    }

    override fun isInk(x: Int, y: Int): Boolean =
        words.isNotEmpty() && (words[y * stride + (x ushr 5)] ushr (31 - (x and 31))) and 1 == 1

    /** Number of set pixels. */
    val inkCount: Int = words.sumOf { Integer.bitCount(it) }

    /** True when nothing is set: the renderer skips the draw (and never builds a mask for it). */
    val isEmpty: Boolean get() = inkCount == 0

    // Turned views for rotation 90/180/270, created on first use and then kept (identity-stable).
    private val turned = arrayOfNulls<MonoImage>(3)

    /**
     * This layer turned clockwise by [rotation] (0/90/180/270) **as an image**: pixel (u, v) of the
     * layer lands where `DitherRenderer.blit(layer, x, y, rotation)` would put it. Used for draws
     * that must not rotate the canvas (tile fills keep global phase; see [FxRenderer]). Rotation 0
     * is the layer itself. The view holds no pixel data; allocates only on the first call per
     * rotation ([FxRenderer.prewarm] makes those calls).
     */
    fun rotated(rotation: Int): MonoImage {
        val i = when (rotation) {
            0 -> return this
            90 -> 0
            180 -> 1
            270 -> 2
            else -> throw IllegalArgumentException("rotation must be 0/90/180/270, was $rotation")
        }
        turned[i]?.let { return it }
        return Turned(this, rotation).also { turned[i] = it }
    }

    private class Turned(private val src: FxLayer, private val rotation: Int) : MonoImage {
        private val sideways = rotation == 90 || rotation == 270
        override val width: Int = if (sideways) src.height else src.width
        override val height: Int = if (sideways) src.width else src.height

        // Inverse of blit's clockwise turn: 90: (u,v)→(h-1-v, u); 180: (w-1-u, h-1-v); 270: (v, w-1-u).
        override fun isInk(x: Int, y: Int): Boolean = when (rotation) {
            90 -> src.isInk(y, src.height - 1 - x)
            180 -> src.isInk(src.width - 1 - x, src.height - 1 - y)
            else -> src.isInk(src.width - 1 - y, x)
        }
    }

    companion object {
        /** Digits of [rle]'s run-length code (64 symbols that need no escaping in Kotlin source). */
        const val RLE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        private val RLE_INDEX = IntArray(128) { -1 }.also { t -> RLE_ALPHABET.forEachIndexed { i, c -> t[c.code] = i } }

        /** An empty W×H layer (no storage). */
        fun empty(width: Int, height: Int): FxLayer = FxLayer(width, height, IntArray(0))

        /**
         * Decodes a layer from the generator's run-length code (class-init only, never per frame).
         *
         * The W×H pixels are taken row-major as alternating runs, starting with a run of unset
         * pixels (which may be 0); a final unset run may be omitted. Each run length is a
         * little-endian varint of 5-bit groups, one [RLE_ALPHABET] char per group, where index
         * ≥ 32 means "more groups follow". [parts] are concatenated (the generator splits long
         * codes between runs to keep each string constant small).
         */
        fun rle(width: Int, height: Int, vararg parts: String): FxLayer {
            val stride = (width + 31) / 32
            val words = IntArray(stride * height)
            val total = width * height
            var pos = 0
            var set = false
            var v = 0
            var shift = 0
            for (part in parts) for (c in part) {
                val d = if (c.code < 128) RLE_INDEX[c.code] else -1
                require(d >= 0) { "bad run-length char '$c'" }
                v = v or ((d and 31) shl shift)
                if (d >= 32) { shift += 5; continue }
                require(pos + v <= total) { "runs overflow ${width}x$height" }
                if (set) for (p in pos until pos + v) {
                    val x = p % width
                    val i = (p / width) * stride + (x ushr 5)
                    words[i] = words[i] or (1 shl (31 - (x and 31)))
                }
                pos += v
                set = !set
                v = 0
                shift = 0
            }
            require(shift == 0) { "truncated run-length code" }
            return if (pos == 0 || words.all { it == 0 }) empty(width, height) else FxLayer(width, height, words)
        }
    }
}
