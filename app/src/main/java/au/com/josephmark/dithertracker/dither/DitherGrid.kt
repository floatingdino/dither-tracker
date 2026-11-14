package au.com.josephmark.dithertracker.dither

import kotlin.math.roundToInt

/** Mutable integer rectangle, half-open: [left, right) × [top, bottom). No Android types. */
class IRect(
    @JvmField var left: Int = 0,
    @JvmField var top: Int = 0,
    @JvmField var right: Int = 0,
    @JvmField var bottom: Int = 0,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun set(left: Int, top: Int, right: Int, bottom: Int): IRect {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom
        return this
    }

    /** Sets from origin + size. */
    fun setXywh(x: Int, y: Int, w: Int, h: Int): IRect = set(x, y, x + w, y + h)

    fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom

    override fun equals(other: Any?): Boolean =
        other is IRect && other.left == left && other.top == top && other.right == right && other.bottom == bottom

    override fun hashCode(): Int = ((left * 31 + top) * 31 + right) * 31 + bottom
    override fun toString(): String = "IRect($left, $top, $right, $bottom)"
}

/**
 * The integer-scale dither grid laid over a device surface.
 *
 * - One dither pixel = [scale]×[scale] device pixels. Never fractional.
 * - Grid size is `floor(device / scale)`; the leftover device pixels become margins, split
 *   deterministically with the extra pixel (if odd) going right/bottom.
 * - Dither pixel (0,0) is the global pattern-phase origin.
 * - [deviceWidth] × [deviceHeight] is the *usable* area; it starts [insetLeft], [insetTop] device px
 *   into the view (T62: a display cutout's safe inset is paper margin, never content). With no insets
 *   (every screen without a cutout, the Nomad) the grid is exactly what it always was.
 *
 * Pure maths; immutable; safe to unit-test on the JVM.
 */
class DitherGrid(val deviceWidth: Int, val deviceHeight: Int, val scale: Int, val insetLeft: Int = 0, val insetTop: Int = 0) {
    init {
        require(scale >= 1) { "scale must be >= 1, was $scale" }
        require(deviceWidth >= 0 && deviceHeight >= 0) { "negative device size" }
        require(insetLeft >= 0 && insetTop >= 0) { "negative inset" }
    }

    /** Grid width in dither pixels. */
    val width: Int = deviceWidth / scale

    /** Grid height in dither pixels. */
    val height: Int = deviceHeight / scale

    /** Device px from the view's left / top edge to the grid (the inset plus half the leftover). */
    val marginLeft: Int = insetLeft + (deviceWidth - width * scale) / 2
    val marginTop: Int = insetTop + (deviceHeight - height * scale) / 2

    /** Leftover device px right of / below the grid inside the usable area. */
    val marginRight: Int = deviceWidth - width * scale - (marginLeft - insetLeft)
    val marginBottom: Int = deviceHeight - height * scale - (marginTop - insetTop)

    /** Device x of the left edge of dither column [dx] (dx may equal [width] for the right edge). */
    fun toDeviceX(dx: Int): Int = marginLeft + dx * scale

    /** Device y of the top edge of dither row [dy]. */
    fun toDeviceY(dy: Int): Int = marginTop + dy * scale

    /** Dither column containing device x (floor; may be <0 or >=width inside the margins). */
    fun toDitherX(deviceX: Int): Int = Math.floorDiv(deviceX - marginLeft, scale)

    /** Dither row containing device y. */
    fun toDitherY(deviceY: Int): Int = Math.floorDiv(deviceY - marginTop, scale)

    /** Dither rect → exact device rect. Returns [out]. */
    fun toDevice(dither: IRect, out: IRect): IRect =
        out.set(toDeviceX(dither.left), toDeviceY(dither.top), toDeviceX(dither.right), toDeviceY(dither.bottom))

    /** Device rect → smallest dither rect covering it (for dirty regions). Returns [out]. */
    fun toDitherCovering(device: IRect, out: IRect): IRect = out.set(
        toDitherX(device.left),
        toDitherY(device.top),
        Math.floorDiv(device.right - marginLeft + scale - 1, scale),
        Math.floorDiv(device.bottom - marginTop + scale - 1, scale),
    )

    /** Device rect → largest dither rect fully inside it. Returns [out]. */
    fun toDitherInside(device: IRect, out: IRect): IRect = out.set(
        Math.floorDiv(device.left - marginLeft + scale - 1, scale),
        Math.floorDiv(device.top - marginTop + scale - 1, scale),
        toDitherX(device.right),
        toDitherY(device.bottom),
    )

    /** The device rect covered by the whole grid (device size minus margins). */
    fun gridDeviceRect(out: IRect): IRect =
        out.set(marginLeft, marginTop, marginLeft + width * scale, marginTop + height * scale)

    override fun equals(other: Any?): Boolean =
        other is DitherGrid && other.deviceWidth == deviceWidth && other.deviceHeight == deviceHeight && other.scale == scale &&
            other.insetLeft == insetLeft && other.insetTop == insetTop

    override fun hashCode(): Int = (((deviceWidth * 31 + deviceHeight) * 31 + scale) * 31 + insetLeft) * 31 + insetTop
    override fun toString(): String =
        "DitherGrid(${deviceWidth}x$deviceHeight${if (insetLeft != 0 || insetTop != 0) " +$insetLeft,$insetTop" else ""} @S=$scale → ${width}x$height, " +
            "margins L$marginLeft T$marginTop R$marginRight B$marginBottom)"

    companion object {
        const val MIN_SCALE = 2
        const val MAX_SCALE = 6

        /** Target physical size of one dither pixel: 1/75 inch (≈0.34 mm). Nomad: 300dpi / 75 = 4. */
        const val TARGET_DITHER_DPI = 75

        /** Below this many dither px on the short side, layouts get too cramped; auto-scale backs off. */
        const val MIN_SHORT_SIDE = 200

        /** Setting value meaning "choose automatically". */
        const val AUTO = 0

        /**
         * Picks the integer scale S.
         *
         * @param override the user setting: [AUTO] (0) or an explicit 2..6 (clamped).
         * @param densityDpi the display's density (Android `DisplayMetrics.densityDpi`, or xdpi).
         *
         * Auto: `round(dpi / 75)` so a dither pixel is ≈0.34 mm on any screen, clamped to 2..6, then
         * reduced (not below 2) while the short side would have fewer than [MIN_SHORT_SIDE] dither px.
         */
        fun chooseScale(deviceWidth: Int, deviceHeight: Int, densityDpi: Int, override: Int = AUTO): Int {
            if (override != AUTO) return override.coerceIn(MIN_SCALE, MAX_SCALE)
            var s = (densityDpi.toDouble() / TARGET_DITHER_DPI).roundToInt().coerceIn(MIN_SCALE, MAX_SCALE)
            val shortSide = minOf(deviceWidth, deviceHeight)
            while (s > MIN_SCALE && shortSide / s < MIN_SHORT_SIDE) s--
            return s
        }
    }
}
