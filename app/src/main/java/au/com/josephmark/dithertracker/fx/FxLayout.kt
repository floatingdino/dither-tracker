package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * Pure placement maths for effect frames (no Android types; JVM-tested).
 *
 * An effect is anchored in the seat's *upright* frame: its frame centre sits at
 * `ref(inkBox) + (dx, dy)` (meta `anchor <ref> dx dy`, y down). For a rotated seat the whole local
 * picture — ink box, offset and frame — is turned clockwise by the seat's rotation about the ink box,
 * exactly like [TextLayout] does for text, and the frame is blitted with the same rotation. No
 * `Canvas.rotate` is involved.
 *
 * Reference points on an ink box of local size W×H: left/top = 0, centre = W/2 (floor), right/bottom
 * = W (the box's far edge), matching `design/fx/render.py` for even sizes.
 */
object FxLayout {

    /**
     * Writes into [out] the on-screen footprint (dither px) of [fx]'s frames for a life total whose
     * on-screen ink box is [inkBox], on a seat turned by [rotation] (0/90/180/270 clockwise).
     * `out.left/top` is where to blit with that rotation; `out` is also the rect to invalidate.
     * The footprint may extend past the ink box (and, for big effects, past the seat): clip if needed.
     */
    fun place(fx: Fx, inkBox: IRect, rotation: Int, out: IRect): IRect =
        place(fx.width, fx.height, fx.anchorRef, fx.anchorDx, fx.anchorDy, inkBox, rotation, out)

    /** [place] with the frame size and anchor given explicitly. */
    fun place(
        frameW: Int, frameH: Int, ref: FxAnchorRef, dx: Int, dy: Int,
        inkBox: IRect, rotation: Int, out: IRect,
    ): IRect {
        val sideways = TextLayout.isSideways(rotation)
        // The ink box's size in the seat's upright frame.
        val lw = if (sideways) inkBox.height else inkBox.width
        val lh = if (sideways) inkBox.width else inkBox.height
        val lx = refCoord(ref.rx, lw) + dx - frameW / 2
        val ly = refCoord(ref.ry, lh) + dy - frameH / 2
        val x = inkBox.left + TextLayout.mapX(rotation, lw, lh, lx, ly, frameW, frameH)
        val y = inkBox.top + TextLayout.mapY(rotation, lw, lh, lx, ly, frameW, frameH)
        return out.setXywh(x, y, TextLayout.footprintW(rotation, frameW, frameH), TextLayout.footprintH(rotation, frameW, frameH))
    }

    /** Local coordinate of a reference point along a span of [len]: -1 → 0, 0 → len/2, +1 → len. */
    fun refCoord(r: Int, len: Int): Int = when {
        r < 0 -> 0
        r == 0 -> len / 2
        else -> len
    }

    /**
     * The on-screen ink box of a line of text drawn by `TextRenderer.drawInt(..., x, y, k, rotation)`
     * whose footprint is [footprint]: the text's full advance width × its cap band
     * (`font.capTop * k`, `font.capHeight * k`). Approximates the true ink box (side bearings are
     * included), which is what the anchors were designed against.
     */
    fun textInkBox(footprint: IRect, rotation: Int, bandTop: Int, bandH: Int, out: IRect): IRect {
        val sideways = TextLayout.isSideways(rotation)
        val tw = if (sideways) footprint.height else footprint.width
        val th = if (sideways) footprint.width else footprint.height
        val x = footprint.left + TextLayout.mapX(rotation, tw, th, 0, bandTop, tw, bandH)
        val y = footprint.top + TextLayout.mapY(rotation, tw, th, 0, bandTop, tw, bandH)
        return out.setXywh(x, y, TextLayout.footprintW(rotation, tw, bandH), TextLayout.footprintH(rotation, tw, bandH))
    }
}
