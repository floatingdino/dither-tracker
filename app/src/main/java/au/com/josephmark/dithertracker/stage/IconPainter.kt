package au.com.josephmark.dithertracker.stage

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.MonoImage

/**
 * 16×16 icons at an integer glyph scale k, shared by every component (one instance per view, so each
 * scaled icon is one stable [MonoImage] and the renderer builds its mask once). Allocation-free after
 * the first draw of an icon at the current k; components prewarm the icons they use.
 */
class IconPainter(private val r: DitherRenderer) {

    private var iconK = 1
    private val scaled = arrayOfNulls<MonoImage>(Icon.entries.size)

    /** Blits [icon] at glyph scale [k] with its footprint's top-left at [at]. */
    fun icon(k: Int, icon: Icon, at: IRect, rot: Int, invert: Boolean, opaque: Boolean) {
        r.blit(scaledIcon(icon, k), at.left, at.top, rot, invert, opaque)
    }

    /** Paper behind [rect] grown by [h] all round. */
    fun halo(rect: IRect, h: Int) {
        r.rect(rect.left - h, rect.top - h, rect.width + 2 * h, rect.height + 2 * h, ink = false)
    }

    /** Builds the masks of [icons] at scale [k] (call off the draw path, renderer begun on a scratch canvas). */
    fun prewarm(icons: Array<Icon>, k: Int) {
        for (ic in icons) r.blit(scaledIcon(ic, k), 0, 0)
    }

    fun scaledIcon(icon: Icon, k: Int): MonoImage {
        if (k == 1) return icon
        if (k != iconK) {
            scaled.fill(null); iconK = k
        }
        return scaled[icon.ordinal] ?: Scaled(icon, k).also { scaled[icon.ordinal] = it }
    }

    /** An icon enlarged k× (nearest neighbour): a stable instance so the renderer's mask cache hits. */
    private class Scaled(private val src: MonoImage, private val k: Int) : MonoImage {
        override val width: Int = src.width * k
        override val height: Int = src.height * k
        override fun isInk(x: Int, y: Int): Boolean = src.isInk(x / k, y / k)
    }
}
