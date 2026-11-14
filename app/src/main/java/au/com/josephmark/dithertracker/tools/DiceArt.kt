package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.PackedImage
import au.com.josephmark.dithertracker.type.Fonts

/**
 * The panel's procedural 1-bit art, as stable [MonoImage]s (the renderer caches one mask per
 * instance, so build these once per layout and draw them every frame without allocating). [k] is the
 * result's glyph scale (`DiceLayout.resultK`); everything matches the `num-m` result's height:
 *
 * - [coin]: the landed coin, as tall as a result numeral, showing its face (T61): heads the imp's face,
 *   tails the imp's spade-tipped tail, knocked out of an ink disc inside a paper ring. No drop shadow,
 *   like the `num-m` numerals it stands in for (T46). At an even [k] (the shipped 2× result) it is the
 *   28-px face ([CoinFaces.heads] / [CoinFaces.tails]) at k / 2; at an odd [k] (the 1× result) the
 *   face's 16×16 icon twin ([CoinFaces.headsIcon], art in its inner 14×14) at k.
 * (T52 retired the reveal beat's dither "?": the roll presentation's sprites are [SpriteSet]. The
 * tumbling coin never shows a face: only the landing does.)
 *
 * Every image comes in the two face rotations: index 0 upright, 1 turned 180°.
 */
class DiceArt(val k: Int, val faces: CoinFaces = CoinFaces.SHIPPED) {
    /** Coin disc diameter (dither px): the result's cap height. */
    val diameter: Int = Fonts.NUM_M.capHeight * k
    val box: Int = diameter

    /** `coin[face * 2 + rot]`: face 0 heads, 1 tails; rot 0 upright, 1 turned. */
    val coin: Array<MonoImage> = Array(4) { i ->
        val heads = i / 2 == 0
        val src: MonoImage
        val s: Int
        val inset: Int
        if (k % 2 == 0) {
            src = if (heads) faces.heads else faces.tails
            s = k / 2
            inset = 0
        } else {
            src = if (heads) faces.headsIcon else faces.tailsIcon
            s = k
            inset = 1 // the icon's empty 1-px margin
        }
        Turn(Scaled(src, s, inset, box), i % 2 == 1)
    }

    fun coin(heads: Boolean, rotation: Int): MonoImage = coin[(if (heads) 0 else 2) + (if (rotation == 180) 1 else 0)]

    /** [src] less an [inset]-px margin, scaled by [s], [size]² (identity equality: one cached mask each). */
    private class Scaled(private val src: MonoImage, private val s: Int, private val inset: Int, size: Int) : MonoImage {
        override val width = size
        override val height = size
        override fun isInk(x: Int, y: Int) = src.isInk(x / s + inset, y / s + inset)
    }

    /** [src] as is, or turned 180°. */
    private class Turn(private val src: MonoImage, private val turned: Boolean) : MonoImage {
        override val width = src.width
        override val height = src.height
        override fun isInk(x: Int, y: Int) =
            if (turned) src.isInk(width - 1 - x, height - 1 - y) else src.isInk(x, y)
    }

    companion object {
        /** The coin face's size at the 2× result's half scale (dither px): `num-m`'s cap height × 2. */
        const val FACE = 28

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

/**
 * The coin's two faces (T61): [heads] / [tails] at [DiceArt.FACE]² (from `design/tools/coin/`, via
 * `DiceSprites`), and their 16×16 icon twins (`design/icons/coin-heads.txt` / `coin-tails.txt`).
 * Tests build the other T61 options from `CoinFacesT61` for the contact sheet.
 */
class CoinFaces(
    val heads: MonoImage, val tails: MonoImage, val headsIcon: MonoImage, val tailsIcon: MonoImage,
) {
    init {
        for (f in arrayOf(heads, tails)) require(f.width == DiceArt.FACE && f.height == DiceArt.FACE) { "face ${f.width}x${f.height}" }
        for (f in arrayOf(headsIcon, tailsIcon)) require(f.width == Icon.SIZE && f.height == Icon.SIZE) { "icon ${f.width}x${f.height}" }
    }

    companion object {
        /** Packs 28-px face rows (`#` ink, `.` none). */
        fun face(rows: Array<String>): MonoImage = PackedImage(rows[0].length, rows.size, PackedImage.pack(rows))

        val SHIPPED: CoinFaces by lazy {
            CoinFaces(face(DiceSprites.COIN_HEADS), face(DiceSprites.COIN_TAILS), Icon.COIN_HEADS, Icon.COIN_TAILS)
        }
    }
}
