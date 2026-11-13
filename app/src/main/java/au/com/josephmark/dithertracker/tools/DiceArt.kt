package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.type.Fonts

/**
 * The panel's procedural 1-bit art, as stable [MonoImage]s (the renderer caches one mask per
 * instance, so build these once per layout and draw them every frame without allocating). [k] is the
 * result's glyph scale (`DiceLayout.resultK`); everything matches the `num-m` result's height:
 *
 * - [coin]: a "sticker" coin as tall as a result numeral — an ink disc with a paper milled ring and
 *   the face's letter (H / T, the `ui` glyph enlarged) knocked out. No drop shadow, like the `num-m`
 *   numerals it stands in for (T46).
 * - [mystery]: the reveal beat's "?" (the `ui` glyph enlarged to the result's height), a mask filled
 *   with the shadow checker — the result "knocked back" to dither for one keyframe.
 *
 * Every image comes in the two face rotations: index 0 upright, 1 turned 180°.
 */
class DiceArt(val k: Int) {
    /** Coin disc diameter (dither px): the result's cap height. */
    val diameter: Int = Fonts.NUM_M.capHeight * k
    val box: Int = diameter

    /** `coin[face * 2 + rot]`: face 0 heads, 1 tails; rot 0 upright, 1 turned. */
    val coin: Array<MonoImage> = Array(4) { i -> Turn(Coin(k, if (i / 2 == 0) 'H' else 'T'), i % 2 == 1) }

    /** The "?" mask, upright and turned. */
    val mysteryK: Int = maxOf(1, (Fonts.NUM_M.capHeight * k) / Fonts.UI.capHeight)
    val mystery: Array<MonoImage> = Array(2) { i -> Turn(Cap(Fonts.UI.glyph('?').scaled(mysteryK), Fonts.UI.capHeight * mysteryK), i == 1) }

    fun coin(heads: Boolean, rotation: Int): MonoImage = coin[(if (heads) 0 else 2) + (if (rotation == 180) 1 else 0)]
    fun mystery(rotation: Int): MonoImage = mystery[if (rotation == 180) 1 else 0]

    private inner class Coin(k: Int, letter: Char) : MonoImage {
        override val width = box
        override val height = box
        private val d = diameter
        private val ringIn = d - 2 * RING_INSET * k
        private val ringOut = ringIn - 2 * RING_W * k
        private val glyph = Fonts.UI.glyph(letter).scaled(LETTER_K * k)
        private val gw = Fonts.UI.glyphWidth * LETTER_K * k
        private val capH = Fonts.UI.capHeight * LETTER_K * k
        private val gx = (d - gw) / 2
        private val gy = (d - capH) / 2 - Fonts.UI.capTop * LETTER_K * k

        override fun isInk(x: Int, y: Int): Boolean {
            if (x >= d || y >= d) return false
            val q = dist2(x, y, d)
            if (q > d.toLong() * d) return false
            if (q <= ringIn.toLong() * ringIn && q > ringOut.toLong() * ringOut) return false // the milled ring
            val lx = x - gx
            val ly = y - gy
            if (lx in 0 until glyph.width && ly in 0 until glyph.height && glyph.isInk(lx, ly)) return false
            return true
        }
    }

    /** [src]'s top [h] rows (drops a glyph's empty descender band). */
    private class Cap(private val src: MonoImage, h: Int) : MonoImage {
        override val width = src.width
        override val height = minOf(h, src.height)
        override fun isInk(x: Int, y: Int) = src.isInk(x, y)
    }

    /** [src] as is, or turned 180°. */
    private class Turn(private val src: MonoImage, private val turned: Boolean) : MonoImage {
        override val width = src.width
        override val height = src.height
        override fun isInk(x: Int, y: Int) =
            if (turned) src.isInk(width - 1 - x, height - 1 - y) else src.isInk(x, y)
    }

    companion object {
        /** Milled ring: inset from the rim and width, per k (the 14-px disc at k = 1: 1 px each). */
        const val RING_INSET = 1
        const val RING_W = 1

        /** The coin letter's `ui` scale, per k (H/T 5×7 in the 14-px disc at k = 1, 10×14 at k = 2). */
        const val LETTER_K = 1

        /**
         * Squared distance, ×4, of pixel (x, y)'s centre from the centre of a [d]-px disc: compare with
         * `d²` (doubled coordinates keep the disc exactly symmetric for odd and even d).
         */
        fun dist2(x: Int, y: Int, d: Int): Long {
            val dx = (2 * x + 1 - d).toLong()
            val dy = (2 * y + 1 - d).toLong()
            return dx * dx + dy * dy
        }
    }
}
