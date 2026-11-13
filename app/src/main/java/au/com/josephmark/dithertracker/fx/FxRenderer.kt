package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect

/**
 * Draws effect frames through a [DitherRenderer], in dither px, for a seat in any orientation.
 *
 * Each frame is two layers blitted additively (transparent pixels are left untouched):
 * the knock-out layer (`o`) with `blit(invert = true)` — the paper colour — then the black layer
 * (`#`) with a plain blit — the ink colour. Both colours follow the renderer's global
 * [DitherRenderer.inverted] flag, so dark mode swaps them like everything else. Rotation is the
 * blit's own rotation plus [FxLayout] position maths; the canvas matrix is never touched.
 *
 * No allocations per draw: layers are generated singletons, so the renderer's scaled-mask cache
 * builds each mask once per scale (call [prewarm] off the first frame to build them up front).
 */
class FxRenderer(val renderer: DitherRenderer) {

    private val tmp = IRect()

    /** Draws [frame] of [fx] for the life total whose on-screen ink box is [inkBox], seat turned by [rotation]. */
    fun draw(fx: Fx, frame: Int, inkBox: IRect, rotation: Int) {
        FxLayout.place(fx, inkBox, rotation, tmp)
        drawAt(fx, frame, tmp.left, tmp.top, rotation)
    }

    /** Draws [frame] of [fx] with its footprint's top-left at (x, y) (see [FxLayout.place]). */
    fun drawAt(fx: Fx, frame: Int, x: Int, y: Int, rotation: Int) {
        val ko = fx.knockout(frame)
        if (!ko.isEmpty) renderer.blit(ko, x, y, rotation, invert = true, opaque = false)
        val ink = fx.ink(frame)
        if (!ink.isEmpty) renderer.blit(ink, x, y, rotation, invert = false, opaque = false)
    }

    /** Draws whatever [player] is showing for [seat] (nothing when it is clean). */
    fun drawCurrent(player: FxPlayer, seat: Int, inkBox: IRect, rotation: Int) {
        val fx = player.current(seat) ?: return
        draw(fx, player.frame(seat), inkBox, rotation)
    }

    /**
     * Builds the scaled masks of every layer of [effects] now. Call after `renderer.setGrid(...)` and
     * `renderer.begin(scratchCanvas)` on a throwaway canvas (like PlayView's glyph prewarm), so the
     * first effect mid-game doesn't build bitmaps in onDraw.
     */
    fun prewarm(effects: Iterable<Fx> = Fx.entries) {
        for (fx in effects) for (f in 0 until fx.frameCount) drawAt(fx, f, 0, 0, 0)
    }
}
