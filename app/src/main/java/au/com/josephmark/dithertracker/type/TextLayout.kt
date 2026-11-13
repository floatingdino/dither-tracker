package au.com.josephmark.dithertracker.type

/**
 * Pure rotation/placement maths for text on the dither grid (no Android types; JVM-tested).
 *
 * A string is laid out in its own *local* frame — a box [W]×[H] with glyph i at
 * `(i·advance·k, 0)` — and that box is turned by 0/90/180/270° clockwise about its own extent, so its
 * on-screen *footprint* is W×H (0/180) or H×W (90/270) with the same top-left. Each glyph is then
 * blitted with the same rotation at the position these functions give, which is exactly what
 * `Canvas.rotate` would have produced — without touching the canvas matrix (SPEC engine rule).
 */
object TextLayout {

    fun isSideways(rotation: Int): Boolean = when (rotation) {
        0, 180 -> false
        90, 270 -> true
        else -> throw IllegalArgumentException("rotation must be 0/90/180/270, was $rotation")
    }

    /** On-screen width of a local W×H box turned by [rotation]. */
    fun footprintW(rotation: Int, w: Int, h: Int): Int = if (isSideways(rotation)) h else w

    /** On-screen height of a local W×H box turned by [rotation]. */
    fun footprintH(rotation: Int, w: Int, h: Int): Int = if (isSideways(rotation)) w else h

    /**
     * Screen x (relative to the footprint's top-left) of the top-left corner of the local rect
     * `[lx, lx+w) × [ly, ly+h)` inside a local box W×H turned clockwise by [rotation].
     */
    fun mapX(rotation: Int, boxW: Int, boxH: Int, lx: Int, ly: Int, w: Int, h: Int): Int = when (rotation) {
        0 -> lx
        90 -> boxH - ly - h
        180 -> boxW - lx - w
        270 -> ly
        else -> throw IllegalArgumentException("rotation must be 0/90/180/270, was $rotation")
    }

    /** Screen y counterpart of [mapX]. */
    fun mapY(rotation: Int, boxW: Int, boxH: Int, lx: Int, ly: Int, w: Int, h: Int): Int = when (rotation) {
        0 -> ly
        90 -> lx
        180 -> boxH - ly - h
        270 -> boxW - lx - w
        else -> throw IllegalArgumentException("rotation must be 0/90/180/270, was $rotation")
    }

    /** Maps a single local pixel (u, v) of a W×H box turned by [rotation] to footprint coords; x part. */
    fun pointX(rotation: Int, boxW: Int, boxH: Int, u: Int, v: Int): Int = mapX(rotation, boxW, boxH, u, v, 1, 1)

    /** y part of [pointX]. */
    fun pointY(rotation: Int, boxW: Int, boxH: Int, u: Int, v: Int): Int = mapY(rotation, boxW, boxH, u, v, 1, 1)

    /** Floor-centred offset of a span of [inner] inside [outer] (leftover odd px goes after). */
    fun centre(outer: Int, inner: Int): Int = Math.floorDiv(outer - inner, 2)

    const val START = 0
    const val CENTRE = 1
    const val END = 2

    /** Offset of a span [inner] inside [outer] for [align] = [START]/[CENTRE]/[END]. */
    fun align(align: Int, outer: Int, inner: Int): Int = when (align) {
        START -> 0
        CENTRE -> centre(outer, inner)
        END -> outer - inner
        else -> throw IllegalArgumentException("align must be START/CENTRE/END, was $align")
    }

    /**
     * Footprint top-left x (relative to the rect's top-left) for a text box [textW]×[textH] placed in a
     * screen rect [rectW]×[rectH] whose content is turned by [rotation]: along the reading direction it
     * is aligned by [hAlign]; across it, the band `[bandTop, bandTop+bandH)` of the text box (the cap
     * band) is centred. "Reading direction" rotates with the text, so a 180° seat looks identical to
     * an upright one viewed from its own side.
     */
    fun placeX(rotation: Int, rectW: Int, rectH: Int, textW: Int, textH: Int, bandTop: Int, bandH: Int, hAlign: Int = CENTRE): Int {
        val lw = if (isSideways(rotation)) rectH else rectW
        val lh = if (isSideways(rotation)) rectW else rectH
        return mapX(rotation, lw, lh, align(hAlign, lw, textW), centre(lh, bandH) - bandTop, textW, textH)
    }

    /** y counterpart of [placeX]. */
    fun placeY(rotation: Int, rectW: Int, rectH: Int, textW: Int, textH: Int, bandTop: Int, bandH: Int, hAlign: Int = CENTRE): Int {
        val lw = if (isSideways(rotation)) rectH else rectW
        val lh = if (isSideways(rotation)) rectW else rectH
        return mapY(rotation, lw, lh, align(hAlign, lw, textW), centre(lh, bandH) - bandTop, textW, textH)
    }
}
