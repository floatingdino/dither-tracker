package au.com.josephmark.dithertracker.stage

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.MonoImage

/**
 * 16×16 icons at an integer glyph scale k, shared by every component (one instance per view, so each
 * scaled icon is one stable [MonoImage] and the renderer builds its mask once). Allocation-free after
 * the first draw of an icon at the current k; components prewarm the icons they use.
 *
 * **On a dither fill, draw with `halo = true`** (T41): the icon sits on a paper cut-out in its own
 * shape ([Icon.halo], k px wide at glyph scale k) instead of a square backing.
 */
class IconPainter(private val r: DitherRenderer) {

    private var iconK = 1
    private val scaled = arrayOfNulls<MonoImage>(Icon.entries.size)
    private val scaledHalo = arrayOfNulls<MonoImage>(Icon.entries.size)

    /**
     * Blits [icon] at glyph scale [k] with its footprint's top-left at [at]. [halo]: on its paper cut-out
     * (k px beyond the footprint; ink with [invert]); [opaque] is then ignored.
     */
    fun icon(k: Int, icon: Icon, at: IRect, rot: Int, invert: Boolean, opaque: Boolean, halo: Boolean = false) {
        if (halo) r.blitHaloed(scaledIcon(icon, k), scaledHalo(icon, k), at.left, at.top, rot, invert)
        else r.blit(scaledIcon(icon, k), at.left, at.top, rot, invert, opaque)
    }

    /** Paper behind [rect] grown by [h] all round. */
    fun halo(rect: IRect, h: Int) {
        r.rect(rect.left - h, rect.top - h, rect.width + 2 * h, rect.height + 2 * h, ink = false)
    }

    /** Builds the masks of [icons] (and their halos) at scale [k] (call off the draw path, renderer begun on a scratch canvas). */
    fun prewarm(icons: Array<Icon>, k: Int) {
        for (ic in icons) {
            r.blit(scaledIcon(ic, k), 0, 0)
            r.blit(scaledHalo(ic, k), 0, 0)
        }
    }

    fun scaledIcon(icon: Icon, k: Int): MonoImage {
        if (k == 1) return icon
        rescale(k)
        return scaled[icon.ordinal] ?: Scaled(icon, k).also { scaled[icon.ordinal] = it }
    }

    /** [icon]'s halo ([Icon.halo]) enlarged k×: a (18k)² cut-out to draw at the icon's top-left − k. */
    fun scaledHalo(icon: Icon, k: Int): MonoImage {
        if (k == 1) return icon.halo
        rescale(k)
        return scaledHalo[icon.ordinal] ?: Scaled(icon.halo, k).also { scaledHalo[icon.ordinal] = it }
    }

    private fun rescale(k: Int) {
        if (k != iconK) {
            scaled.fill(null); scaledHalo.fill(null); iconK = k
        }
    }

    /** An icon enlarged k× (nearest neighbour): a stable instance so the renderer's mask cache hits. */
    private class Scaled(private val src: MonoImage, private val k: Int) : MonoImage {
        override val width: Int = src.width * k
        override val height: Int = src.height * k
        override fun isInk(x: Int, y: Int): Boolean = src.isInk(x / k, y / k)
    }
}
