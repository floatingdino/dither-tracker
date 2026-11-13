package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * A widget's local coordinate frame and its placement on the screen (dither px). Pure maths.
 *
 * Widgets lay themselves out in a local box [width]×[height] (x right, y down, as the *reader* of
 * that overlay sees it). The box is turned [rotation]° clockwise about its own extent and placed with
 * its on-screen footprint's top-left at ([x], [y]) — exactly the [TextLayout] model, so a panel opened
 * from a seat can face that player (0/90/180/270) without `Canvas.rotate`: painters map each local
 * rect with [map] and draw glyphs/icons with the frame's [rotation].
 */
class WidgetFrame {
    /** Screen top-left of the footprint. */
    var x: Int = 0
        private set
    var y: Int = 0
        private set

    /** Clockwise rotation: 0, 90, 180 or 270. */
    var rotation: Int = 0
        private set

    /** Local (reader's) box size. */
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    /** On-screen footprint size (swapped for 90/270). */
    val footprintW: Int get() = TextLayout.footprintW(rotation, width, height)
    val footprintH: Int get() = TextLayout.footprintH(rotation, width, height)

    /** Places a local [localW]×[localH] box with its footprint's top-left at screen ([x], [y]). */
    fun set(x: Int, y: Int, localW: Int, localH: Int, rotation: Int): WidgetFrame {
        TextLayout.isSideways(rotation) // validates
        this.x = x; this.y = y; this.width = localW; this.height = localH; this.rotation = rotation
        return this
    }

    /** Makes the whole screen rect [r] the footprint; the local box is its size turned back by [rotation]. */
    fun setScreen(r: IRect, rotation: Int): WidgetFrame {
        val side = TextLayout.isSideways(rotation)
        return set(r.left, r.top, if (side) r.height else r.width, if (side) r.width else r.height, rotation)
    }

    /**
     * Centres a local [localW]×[localH] box inside the screen rect [container], as seen by the reader
     * (centring is done in the container's local frame, so a 180° overlay is the exact image of an
     * upright one, odd leftover pixel included).
     */
    fun setCentred(container: IRect, rotation: Int, localW: Int, localH: Int): WidgetFrame {
        val side = TextLayout.isSideways(rotation)
        val cw = if (side) container.height else container.width
        val ch = if (side) container.width else container.height
        val ox = TextLayout.centre(cw, localW)
        val oy = TextLayout.centre(ch, localH)
        return set(
            container.left + TextLayout.mapX(rotation, cw, ch, ox, oy, localW, localH),
            container.top + TextLayout.mapY(rotation, cw, ch, ox, oy, localW, localH),
            localW, localH, rotation,
        )
    }

    /** Screen rect of the local rect (lx, ly, w, h). Returns [out]. */
    fun map(lx: Int, ly: Int, w: Int, h: Int, out: IRect): IRect = out.setXywh(
        x + TextLayout.mapX(rotation, width, height, lx, ly, w, h),
        y + TextLayout.mapY(rotation, width, height, lx, ly, w, h),
        TextLayout.footprintW(rotation, w, h),
        TextLayout.footprintH(rotation, w, h),
    )

    fun map(local: IRect, out: IRect): IRect = map(local.left, local.top, local.width, local.height, out)

    /** Local x of the screen pixel (gx, gy) (inverse of [map] for a single pixel). */
    fun localX(gx: Int, gy: Int): Int = when (rotation) {
        0 -> gx - x
        90 -> gy - y
        180 -> width - 1 - (gx - x)
        else -> width - 1 - (gy - y)
    }

    /** Local y of the screen pixel (gx, gy). */
    fun localY(gx: Int, gy: Int): Int = when (rotation) {
        0 -> gy - y
        90 -> height - 1 - (gx - x)
        180 -> height - 1 - (gy - y)
        else -> gx - x
    }

    override fun toString(): String = "WidgetFrame(at $x,$y local ${width}x$height rot $rotation)"
}
