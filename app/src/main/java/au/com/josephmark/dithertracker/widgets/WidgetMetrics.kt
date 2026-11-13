package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts

/**
 * Every size the widget kit uses, in dither px, for one integer scale S (device px per dither px).
 * Pure maths; no Android types.
 *
 * Design values are authored at S = 4 (the Nomad) and converted per S to keep their *physical* size
 * (nearest whole dither px, like `PlayLayout.Chrome`), so a settings row is the same few millimetres
 * on any scale. Glyph/icon scale [k] follows the same rule: 2 at S = 2, 1 at S = 3..6.
 *
 * Fingertip rule: [target] is the smallest hit size in dither px — ceil(9 mm · dpi / 25.4 / S)
 * (27 at S = 4 on a 300-dpi panel). Every widget's hit rect is at least [target] in its short
 * dimension; rows are [rowH] ≥ [target] tall.
 *
 * At S = 4, 300 dpi: k 1, stroke 1, target 27, rowH 27, icon 16, padX 6, gap 4, inset 2.
 */
class WidgetMetrics(val scale: Int, val densityDpi: Int = NOMAD_DPI) {
    init {
        require(scale >= 1) { "scale must be >= 1, was $scale" }
        require(densityDpi > 0) { "densityDpi must be > 0, was $densityDpi" }
    }

    /** Glyph and icon scale (font px → dither px). */
    val k: Int = size(1)

    /** Frame/outline thickness (1 dither px at S = 4, i.e. the Figma 4-device-px line). */
    val stroke: Int = k

    /** Minimum hit size, dither px (≈ 9 mm). */
    val target: Int = minTouch(scale, densityDpi)

    /** Label face (`ui`) and value face (`num-m`). */
    val ui: Font = Fonts.UI
    val num: Font = Fonts.NUM_M

    /** Line heights at [k]. */
    val textH: Int = ui.height(k)
    val numH: Int = num.height(k)

    /** Icon footprint (16×16 art at [k]). */
    val icon: Int = ICON * k

    /** Horizontal padding inside buttons and rows. */
    val padX: Int = size(PAD_X)

    /** Gap between neighbouring items (icon ↔ label, button ↔ button). */
    val gap: Int = size(GAP)

    /** Inset of a visible control inside its row/hit rect. */
    val inset: Int = size(INSET)

    /** Vertical padding inside panels and prompts. */
    val padY: Int = size(PAD_Y)

    /** Paper rim around labels/icons drawn opaque on a dither fill. */
    val halo: Int = k

    /** Gap between wrapped lines of prompt text. */
    val lineGap: Int = size(LINE_GAP)

    /** Height of every row and standalone button: fingertip-sized and fits an icon/number with an inset. */
    val rowH: Int = maxOf(target, maxOf(icon, textH, numH) + 2 * (inset + stroke))

    /** A design value at S = 4 → nearest whole dither px at [scale], at least [min]. */
    fun size(base: Int, min: Int = 1): Int = maxOf(min, (base * BASE_SCALE + scale / 2) / scale)

    /** Width of [chars] glyphs of the `ui` face at [k]. */
    fun textW(chars: Int): Int = ui.widthOf(chars, k)

    override fun toString(): String =
        "WidgetMetrics(S=$scale ${densityDpi}dpi k=$k stroke=$stroke target=$target rowH=$rowH icon=$icon " +
            "padX=$padX gap=$gap inset=$inset padY=$padY)"

    companion object {
        const val NOMAD_DPI = 300
        const val BASE_SCALE = 4

        /** Fingertip minimum, micrometres. */
        const val MIN_TOUCH_UM = 9000

        const val ICON = 16
        const val PAD_X = 6
        const val GAP = 4
        const val INSET = 2
        const val PAD_Y = 4
        const val LINE_GAP = 3

        /** ceil(9 mm in dither px) at [scale] and [dpi]. */
        fun minTouch(scale: Int, dpi: Int): Int {
            val den = 25_400L * scale
            return ((MIN_TOUCH_UM.toLong() * dpi + den - 1) / den).toInt()
        }
    }
}
