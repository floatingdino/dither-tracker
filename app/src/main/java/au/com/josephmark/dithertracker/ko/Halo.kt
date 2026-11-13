package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * The filled silhouette of a 1-bit image grown by [grow] px all round: every pixel that is ink, or
 * paper not reachable from the image's edge (an icon's enclosed inside), then dilated (square). Used
 * as a paper backing so an outline icon reads on a dither field — a cut-out in the icon's own shape,
 * not a box. Its top-left is the source's top-left minus [grow]. [rotation] (clockwise, 0/90/180/270)
 * gives the silhouette of the image as `blit(src, rotation)` lays it on the screen (a turned seat),
 * since mask fills are never rotated by the renderer.
 *
 * Pure and immutable once built (built off the draw path; one stable instance per layout, so the
 * renderer's mask cache hits).
 */
class Halo(src: MonoImage, val grow: Int, val rotation: Int = 0) : MonoImage {
    private val lw = src.width + 2 * grow
    private val lh = src.height + 2 * grow
    override val width: Int = TextLayout.footprintW(rotation, lw, lh)
    override val height: Int = TextLayout.footprintH(rotation, lw, lh)
    private val bits = BooleanArray(width * height)

    /** Upright or half a turn (the T15 form). */
    constructor(src: MonoImage, grow: Int, rotate180: Boolean) : this(src, grow, if (rotate180) 180 else 0)

    init {
        val w = src.width
        val h = src.height
        // Flood the paper reachable from the edge.
        val outside = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        fun seed(x: Int, y: Int) {
            val i = y * w + x
            if (!outside[i] && !src.isInk(x, y)) { outside[i] = true; stack[sp++] = i }
        }
        for (x in 0 until w) { seed(x, 0); seed(x, h - 1) }
        for (y in 0 until h) { seed(0, y); seed(w - 1, y) }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w
            val y = i / w
            if (x > 0) seed(x - 1, y)
            if (x < w - 1) seed(x + 1, y)
            if (y > 0) seed(x, y - 1)
            if (y < h - 1) seed(x, y + 1)
        }
        // Dilate the filled shape by `grow` into the larger frame.
        for (y in 0 until h) for (x in 0 until w) {
            if (outside[y * w + x]) continue
            for (dy in -grow..grow) for (dx in -grow..grow) {
                val u = x + grow + dx
                val v = y + grow + dy
                val tx = TextLayout.pointX(rotation, lw, lh, u, v)
                val ty = TextLayout.pointY(rotation, lw, lh, u, v)
                bits[ty * width + tx] = true
            }
        }
    }

    override fun isInk(x: Int, y: Int): Boolean = bits[y * width + x]
}
