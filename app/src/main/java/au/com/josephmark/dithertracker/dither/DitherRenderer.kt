package au.com.josephmark.dithertracker.dither

import android.os.Build

/**
 * 1-bit drawing in dither-pixel coordinates: the API every component draws with.
 *
 * Every primitive can only produce the two colours ink and paper (#000/#FFF, swapped when
 * [inverted]), every coordinate is an integer dither px (`margin + n*S` on the device), and images
 * are drawn nearest-neighbour at S.
 *
 * Coordinates are dither pixels relative to the grid origin, which is also the global pattern
 * phase origin. Rotate content with [blit]'s `rotation`, never with `Canvas.rotate`.
 *
 * This class holds the device-independent part (fill dispatch, lines, frames, haloed blits) and
 * leaves the device primitives abstract. The app draws with [CanvasDitherRenderer] (an Android
 * `Canvas`); JVM tests draw with a raster implementation (T22), so whole screens are checked pixel
 * by pixel without a device.
 */
abstract class DitherRenderer {

    /** Dark mode: swaps ink and paper for everything drawn after it is set. */
    var inverted: Boolean = false

    var grid: DitherGrid = DitherGrid(0, 0, 1)
        private set

    /** Sets the grid. Implementations drop or re-anchor their caches (call super first). */
    open fun setGrid(newGrid: DitherGrid) {
        grid = newGrid
    }

    /** Builds every tile cache (both polarities) now, e.g. off the first frame. */
    open fun prewarm() {}

    /** Runs [block] with [inverted] flipped (e.g. content on a SELECTED/ink background). */
    inline fun withInverted(block: () -> Unit) {
        inverted = !inverted
        try {
            block()
        } finally {
            inverted = !inverted
        }
    }

    // ---- fills ----------------------------------------------------------------------------

    /** Fills the whole canvas, margins included, with paper. */
    abstract fun clear()

    /** Fills a dither rect with [tile], phase anchored to the grid origin. */
    fun fill(tile: Tile, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        when (tile.inkCount) {
            0 -> rect(x, y, w, h, ink = false)
            tile.width * tile.height -> rect(x, y, w, h, ink = true)
            else -> fillPattern(tile, x, y, w, h)
        }
    }

    fun fill(role: Role, x: Int, y: Int, w: Int, h: Int) = fill(role.tile, x, y, w, h)
    fun fill(tile: Tile, r: IRect) = fill(tile, r.left, r.top, r.width, r.height)
    fun fill(role: Role, r: IRect) = fill(role.tile, r.left, r.top, r.width, r.height)

    /** Solid rect in ink (or paper when [ink] is false). */
    fun rect(x: Int, y: Int, w: Int, h: Int, ink: Boolean = true) {
        if (w <= 0 || h <= 0) return
        solidRect(x, y, w, h, ink)
    }

    fun rect(r: IRect, ink: Boolean = true) = rect(r.left, r.top, r.width, r.height, ink)

    /** A non-empty rect of a mixed (neither all-paper nor all-ink) [tile] at global phase, polarity per [inverted]. */
    protected abstract fun fillPattern(tile: Tile, x: Int, y: Int, w: Int, h: Int)

    /** A non-empty solid rect: ink (paper when ![ink]), polarity per [inverted]. */
    protected abstract fun solidRect(x: Int, y: Int, w: Int, h: Int, ink: Boolean)

    // ---- lines ----------------------------------------------------------------------------

    /** Horizontal 1-dither-px line. */
    fun hline(x: Int, y: Int, len: Int, ink: Boolean = true) = rect(x, y, len, 1, ink)

    /** Vertical 1-dither-px line. */
    fun vline(x: Int, y: Int, len: Int, ink: Boolean = true) = rect(x, y, 1, len, ink)

    /**
     * Dotted horizontal line, 1 on / 1 off. Phase is global (ink where x+y is even), so dotted
     * lines that cross or abut always line up.
     */
    fun dottedH(x: Int, y: Int, len: Int) = fill(Role.DOTTED.tile, x, y, len, 1)

    /** Dotted vertical line, 1 on / 1 off, globally phased. */
    fun dottedV(x: Int, y: Int, len: Int) = fill(Role.DOTTED.tile, x, y, 1, len)

    /** Outline of thickness [t] drawn inside the rect. */
    fun frame(x: Int, y: Int, w: Int, h: Int, t: Int = 1, ink: Boolean = true) {
        if (w <= 0 || h <= 0) return
        if (w <= 2 * t || h <= 2 * t) {
            rect(x, y, w, h, ink); return
        }
        rect(x, y, w, t, ink)
        rect(x, y + h - t, w, t, ink)
        rect(x, y + t, t, h - 2 * t, ink)
        rect(x + w - t, y + t, t, h - 2 * t, ink)
    }

    fun frame(r: IRect, t: Int = 1, ink: Boolean = true) = frame(r.left, r.top, r.width, r.height, t, ink)

    // ---- press feedback -------------------------------------------------------------------

    /**
     * Inverts what is already drawn in the rect (black↔white) — pressed feedback. Requires API 29+
     * ([canInvertInPlace]) on the device; on older devices redraw the region inside [withInverted].
     */
    abstract fun invertRect(x: Int, y: Int, w: Int, h: Int)

    fun invertRect(r: IRect) = invertRect(r.left, r.top, r.width, r.height)

    // ---- bitmaps --------------------------------------------------------------------------

    /**
     * Blits a 1-bit image with its top-left at dither (x, y), each image pixel S×S device px.
     *
     * @param rotation 0, 90, 180 or 270 degrees clockwise. For 90/270 the footprint is h×w.
     * @param invert draw the image's ink as paper (and its paper as ink when [opaque]).
     * @param opaque also paint the image's paper pixels (otherwise they are left untouched).
     */
    abstract fun blit(
        img: MonoImage,
        x: Int,
        y: Int,
        rotation: Int = 0,
        invert: Boolean = false,
        opaque: Boolean = false,
    )

    /**
     * Fills the ink pixels of [mask] (already turned: no rotation is applied) with [tile] at global
     * phase — the tile's ink as ink, its paper as paper; pixels outside the mask are untouched. Used
     * for glyph drop shadows (T27). [invert] swaps the tile's polarity.
     */
    abstract fun fillMask(mask: MonoImage, x: Int, y: Int, tile: Tile, invert: Boolean = false)

    fun fillMask(mask: MonoImage, x: Int, y: Int, role: Role, invert: Boolean = false) = fillMask(mask, x, y, role.tile, invert)

    /**
     * Blits [img] on a paper cut-out in its own shape (T41): [halo] — the same image's halo, a frame g px
     * wider on every side (`Icon.halo`, `Glyph.halo`, [HaloMask.of]) — in paper, then [img] in ink, with
     * [img]'s footprint top-left at (x, y). With [invert] the halo is ink and the glyph paper. Rotation
     * turns both about their common centre. Two cached-mask draws, no allocation.
     */
    fun blitHaloed(img: MonoImage, halo: MonoImage, x: Int, y: Int, rotation: Int = 0, invert: Boolean = false) {
        val g = (halo.width - img.width) / 2
        blit(halo, x - g, y - g, rotation, invert = !invert)
        blit(img, x, y, rotation, invert)
    }

    /**
     * Draws [icon] at (x, y). [halo]: on its paper cut-out ([Icon.halo], see [blitHaloed]) instead of
     * [opaque]'s square backing — the way to put an icon on a dither fill.
     */
    fun icon(
        icon: Icon, x: Int, y: Int, rotation: Int = 0, invert: Boolean = false, opaque: Boolean = false,
        halo: Boolean = false,
    ) = if (halo) blitHaloed(icon, icon.halo, x, y, rotation, invert) else blit(icon, x, y, rotation, invert, opaque)

    // ---- effect masks (T28e) --------------------------------------------------------------

    /** What [drawMask] does under the ink pixels of its mask (pixels outside the mask are untouched). */
    enum class MaskOp {
        /** Inverts what is already drawn (ink↔paper). API 29+; see [drawMask]'s `underlay` below that. */
        INVERT,
        /** Ink where the tile has ink; untouched where it has paper. */
        TILE_INK,
        /** Paper where the tile has ink; untouched where it has paper. */
        TILE_PAPER,
        /** The tile opaque: its ink as ink, its paper as paper. */
        TILE_FILL,
    }

    /**
     * Draws through the ink pixels of [img] — already turned, no rotation is applied, so a tile's
     * phase stays global — with its top-left at dither (x, y) (effects, T28e):
     * - [MaskOp.INVERT]: inverts what is drawn under the mask; colours don't matter (an invert is its
     *   own dark mode). Below API 29 ([canInvertInPlace] false) it instead runs [underlay] — which must
     *   redraw everything under the mask — with [inverted] flipped, clipped to the mask; with no
     *   [underlay] it does nothing.
     * - `TILE_*`: [tile] at global phase through the mask; ink/paper follow [inverted].
     */
    abstract fun drawMask(img: MonoImage, x: Int, y: Int, op: MaskOp, tile: Tile = Patterns.D[16], underlay: Runnable? = null)

    // ---- clip -----------------------------------------------------------------------------

    /**
     * Clips every draw to the dither rect [r] until [unclip] (axis-aligned, never a transform). Pairs
     * must nest: an effect clipped to its seat's panel (T28c).
     */
    abstract fun clipTo(r: IRect)

    /** Ends the innermost [clipTo]. */
    abstract fun unclip()

    companion object {
        /** Whether [invertRect] works on this device (blend mode DIFFERENCE, API 29+). */
        @JvmField val canInvertInPlace: Boolean = Build.VERSION.SDK_INT >= 29
    }
}
