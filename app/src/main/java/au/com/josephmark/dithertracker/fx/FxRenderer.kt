package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Tile

/**
 * Draws effect frames through a [DitherRenderer], in dither px, for a seat in any orientation.
 *
 * A frame's layers are disjoint (one symbol per pixel), drawn in this order:
 * 1. **cut** (`x`): inverts what is already drawn under its mask — paper over the numeral's ink,
 *    ink over paper — via [DitherRenderer.drawMask] `INVERT` (blend DIFFERENCE, API 29+). So the
 *    number (and anything else under the effect) must already be drawn. On API < 29 set
 *    [cutUnderlay]: it is run inside a layer clipped to the mask with `inverted` flipped and must
 *    redraw whatever lies under the effect (panel fill, shadow, number); without it the cut is
 *    skipped there (like `invertRect`'s fallback). It runs first so it only ever sees the clean seat.
 * 2. **tile layers** (`1`–`9`) in ascending digit order: the declared tile, sampled at **global**
 *    screen phase, painted through the mask per [FxTileMode] (`TILE_INK`/`TILE_PAPER`/`TILE_FILL`).
 * 3. **knock-out** (`o`) with `blit(invert = true)` — the paper colour.
 * 4. **ink** (`#`) with a plain blit — the ink colour.
 * Because the layers never overlap, this order composes exactly like `design/fx/options/render2.py`,
 * which stamps each pixel's one symbol over the clean seat.
 *
 * Colours follow the renderer's global [DitherRenderer.inverted] flag (dark mode needs nothing; an
 * invert is the same either way). Rotation never touches the canvas from here: `#`/`o` use the
 * blit's own rotation; `x` and tile layers are drawn as the layer's turned view
 * ([FxLayer.rotated]) on the untransformed canvas, so the tile's phase stays global — rotating the
 * mask, not the pattern.
 *
 * No allocations per draw: layers and their turned views are singletons, and the renderer caches
 * each scaled mask once per scale (call [prewarm] off the first frame to build them up front).
 */
class FxRenderer internal constructor(private val target: Target) {

    constructor(renderer: DitherRenderer) : this(RendererTarget(renderer))

    /**
     * What [FxRenderer] needs from a renderer; [DitherRenderer] in the app, a software raster in the
     * JVM tests. Masks given to [invert] and [tile] are already turned (no rotation applied).
     */
    internal interface Target {
        fun blit(img: MonoImage, x: Int, y: Int, rotation: Int, paper: Boolean)
        fun invert(mask: MonoImage, x: Int, y: Int, underlay: Runnable?)
        fun tile(mask: MonoImage, x: Int, y: Int, tile: Tile, mode: FxTileMode)

        /** [prewarm] only: [tile] in both colour modes (the paint a tile layer needs depends on dark mode). */
        fun warmTile(mask: MonoImage, x: Int, y: Int, tile: Tile, mode: FxTileMode) = tile(mask, x, y, tile, mode)

        /** Clip every draw to [rect] (dither px, axis-aligned) until [unclip]. */
        fun clip(rect: IRect) {}

        fun unclip() {}
    }

    /**
     * API < 29 fallback for cut (`x`) layers: redraws everything under the effect footprint with
     * the renderer's current colours (the renderer flips `inverted` around it). Ignored on API 29+,
     * where the cut is a single DIFFERENCE draw. Keep one instance (no per-draw lambda).
     */
    var cutUnderlay: Runnable? = null

    private val tmp = IRect()

    /** Inside [prewarmSpecs]: tile layers warm both colour modes' paints (T20: dark mode built them in onDraw). */
    private var warming = false

    /**
     * Draws [frame] of [fx] for the life total whose on-screen ink box is [inkBox], seat turned by
     * [rotation]. Not for `anchor tile` effects (use the overload with the tile rect).
     */
    fun draw(fx: Fx, frame: Int, inkBox: IRect, rotation: Int) {
        FxLayout.place(fx.spec, inkBox, rotation, tmp)
        drawAt(fx.spec, frame, tmp.left, tmp.top, rotation)
    }

    /** Draws [frame] of [fx] given both reference boxes (see [FxLayout.place]); works for every anchor. */
    fun draw(fx: Fx, frame: Int, inkBox: IRect, tileRect: IRect, rotation: Int) =
        draw(fx.spec, frame, inkBox, tileRect, rotation)

    /** [draw] for an [FxSpec]. */
    fun draw(fx: FxSpec, frame: Int, inkBox: IRect, tileRect: IRect, rotation: Int) {
        FxLayout.place(fx, inkBox, tileRect, rotation, tmp)
        drawAt(fx, frame, tmp.left, tmp.top, rotation)
    }

    /**
     * [draw] clipped to [clip] (the seat's panel): an effect never draws outside its player's tile.
     * The clip is only set up when the footprint actually crosses it (tile-scale frames fit exactly).
     */
    fun draw(fx: FxSpec, frame: Int, inkBox: IRect, tileRect: IRect, rotation: Int, clip: IRect) {
        FxLayout.place(fx, inkBox, tileRect, rotation, tmp)
        val inside = tmp.left >= clip.left && tmp.top >= clip.top && tmp.right <= clip.right && tmp.bottom <= clip.bottom
        if (inside) {
            drawAt(fx, frame, tmp.left, tmp.top, rotation)
            return
        }
        target.clip(clip)
        try {
            drawAt(fx, frame, tmp.left, tmp.top, rotation)
        } finally {
            target.unclip()
        }
    }

    /** Draws [frame] of [fx] with its footprint's top-left at (x, y) (see [FxLayout.place]). */
    fun drawAt(fx: Fx, frame: Int, x: Int, y: Int, rotation: Int) = drawAt(fx.spec, frame, x, y, rotation)

    /** [drawAt] for an [FxSpec]. */
    fun drawAt(fx: FxSpec, frame: Int, x: Int, y: Int, rotation: Int) {
        val cut = fx.cut(frame)
        if (!cut.isEmpty) target.invert(cut.rotated(rotation), x, y, cutUnderlay)
        val tiles = fx.tiles
        for (i in 0 until tiles.size) {
            val layer = fx.tileLayer(i, frame)
            if (layer.isEmpty) continue
            val t = tiles[i]
            if (warming) target.warmTile(layer.rotated(rotation), x, y, t.tile, t.mode)
            else target.tile(layer.rotated(rotation), x, y, t.tile, t.mode)
        }
        val ko = fx.knockout(frame)
        if (!ko.isEmpty) target.blit(ko, x, y, rotation, paper = true)
        val ink = fx.ink(frame)
        if (!ink.isEmpty) target.blit(ink, x, y, rotation, paper = false)
    }

    /** Draws whatever [player] is showing for [seat] (nothing when it is clean). Not for `anchor tile` effects. */
    fun drawCurrent(player: FxPlayer, seat: Int, inkBox: IRect, rotation: Int) {
        val fx = player.current(seat) ?: return
        draw(fx, player.frame(seat), inkBox, rotation)
    }

    /** [drawCurrent] with both reference boxes; works for every anchor. */
    fun drawCurrent(player: FxPlayer, seat: Int, inkBox: IRect, tileRect: IRect, rotation: Int) {
        val fx = player.current(seat) ?: return
        draw(fx.spec, player.frame(seat), inkBox, tileRect, rotation)
    }

    /**
     * Builds the scaled masks of every layer of [effects] now, for each of [rotations] (cut and tile
     * layers get one mask per rotation, so pass only the rotations the layout uses). Call after
     * `renderer.setGrid(...)` and `renderer.begin(scratchCanvas)` on a throwaway canvas (like
     * PlayView's glyph prewarm), so the first effect mid-game doesn't build bitmaps in onDraw.
     */
    fun prewarm(effects: Iterable<Fx> = Fx.entries, rotations: IntArray = DEFAULT_ROTATIONS) =
        prewarmSpecs(effects.map { it.spec }, rotations)

    /** [prewarm] for [FxSpec]s. */
    fun prewarmSpecs(effects: Iterable<FxSpec>, rotations: IntArray = DEFAULT_ROTATIONS) {
        val underlay = cutUnderlay
        cutUnderlay = null // never run the fallback redraw on the scratch canvas
        warming = true
        try {
            for (fx in effects) for (f in 0 until fx.frameCount) for (r in rotations) drawAt(fx, f, 0, 0, r)
        } finally {
            cutUnderlay = underlay
            warming = false
        }
    }

    private class RendererTarget(private val r: DitherRenderer) : Target {
        override fun blit(img: MonoImage, x: Int, y: Int, rotation: Int, paper: Boolean) =
            r.blit(img, x, y, rotation, invert = paper, opaque = false)

        override fun invert(mask: MonoImage, x: Int, y: Int, underlay: Runnable?) =
            r.drawMask(mask, x, y, DitherRenderer.MaskOp.INVERT, underlay = underlay)

        override fun tile(mask: MonoImage, x: Int, y: Int, tile: Tile, mode: FxTileMode) = r.drawMask(
            mask, x, y,
            when (mode) {
                FxTileMode.INK -> DitherRenderer.MaskOp.TILE_INK
                FxTileMode.PAPER -> DitherRenderer.MaskOp.TILE_PAPER
                FxTileMode.FILL -> DitherRenderer.MaskOp.TILE_FILL
            },
            tile,
        )

        override fun warmTile(mask: MonoImage, x: Int, y: Int, tile: Tile, mode: FxTileMode) {
            tile(mask, x, y, tile, mode)
            r.withInverted { tile(mask, x, y, tile, mode) }
        }

        override fun clip(rect: IRect) = r.clipTo(rect)

        override fun unclip() = r.unclip()
    }

    companion object {
        /** The seat rotations of the 4-player layout (upright and turned seats). */
        @JvmField val DEFAULT_ROTATIONS: IntArray = intArrayOf(0, 180)
    }
}
