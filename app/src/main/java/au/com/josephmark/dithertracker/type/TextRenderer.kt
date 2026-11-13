package au.com.josephmark.dithertracker.type

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.Role

/**
 * Draws [Font] text through a [DitherRenderer], in dither-px coordinates.
 *
 * - `x, y` are the top-left of the text's on-screen *footprint* (the unpadded box: width
 *   [Font.measure] × [Font.height], swapped for 90/270).
 * - `k` is the integer glyph scale: each font px becomes k dither px (k·S device px).
 * - `rotation` (0/90/180/270, clockwise) turns the *whole string* about its footprint — composed
 *   from per-glyph [DitherRenderer.blit] rotation and [TextLayout] position maths, never
 *   `Canvas.rotate`.
 * - `invert` draws glyph ink as paper; `opaque` first paints the footprint (grown by `pad` on every
 *   side) in the background colour (paper, or ink when inverted).
 * - `halo` (T41, faces with [Font.hasHalo]: `ui`) first paints every glyph's cut-out ([Glyph.halo],
 *   k px beyond its box) in the background colour — the way to put text on a dither fill.
 * - `tracking` overrides the face's gap between glyphs (font px, scaled by k).
 * - Unknown characters draw the face's `.notdef` box; see [Font.glyph] for fallbacks.
 * - Faces with drop-shadow masks ([Font.hasShadow], T27: the sticker `num-l`) draw in two passes:
 *   every glyph's shadow mask filled with [Role.SHADOW] at global pattern phase (the mask turns
 *   with the text, so the shadow falls down-right *in the reader's frame*), then every glyph's ink.
 *   `invert` swaps the shadow tile's polarity along with the ink. Pass `shadow = false` to skip it.
 *
 * Draw calls never allocate (after each glyph's first use at a given k): strings are walked by index
 * and [drawInt] formats into an internal buffer. Not thread-safe; use from the UI thread.
 */
class TextRenderer(val renderer: DitherRenderer) {

    private val digits = CharArray(12)
    private var digitsLen = 0

    /** Draws [text] with its footprint's top-left at (x, y). */
    fun draw(
        font: Font, text: CharSequence, x: Int, y: Int,
        k: Int = 1, rotation: Int = 0, invert: Boolean = false, opaque: Boolean = false, pad: Int = 0,
        tracking: Int = font.tracking, shadow: Boolean = true, halo: Boolean = false,
    ) = drawRun(font, text, text.length, x, y, k, rotation, invert, opaque, pad, tracking, shadow, halo)

    /** Draws [value] in decimal (U+2212 minus when the face has it; `+` prefix when [signed] and > 0). */
    fun drawInt(
        font: Font, value: Int, x: Int, y: Int,
        k: Int = 1, rotation: Int = 0, invert: Boolean = false, opaque: Boolean = false, pad: Int = 0,
        tracking: Int = font.tracking, signed: Boolean = false, shadow: Boolean = true, halo: Boolean = false,
    ) {
        format(font, value, signed)
        drawRun(font, null, digitsLen, x, y, k, rotation, invert, opaque, pad, tracking, shadow, halo)
    }

    /**
     * Draws [text] inside the rect (rx, ry, rw, rh): aligned by [align] ([TextLayout.START] /
     * [TextLayout.CENTRE] / [TextLayout.END]) along the reading direction, cap band centred across it.
     * With a rotation, the alignment turns with the text.
     */
    fun drawIn(
        font: Font, text: CharSequence, rx: Int, ry: Int, rw: Int, rh: Int,
        k: Int = 1, rotation: Int = 0, invert: Boolean = false, opaque: Boolean = false, pad: Int = 0,
        tracking: Int = font.tracking, align: Int = TextLayout.CENTRE, shadow: Boolean = true, halo: Boolean = false,
    ) {
        val tw = font.widthOf(text.length, k, tracking)
        val th = font.height(k)
        val x = rx + TextLayout.placeX(rotation, rw, rh, tw, th, font.capTop * k, font.capHeight * k, align)
        val y = ry + TextLayout.placeY(rotation, rw, rh, tw, th, font.capTop * k, font.capHeight * k, align)
        drawRun(font, text, text.length, x, y, k, rotation, invert, opaque, pad, tracking, shadow, halo)
    }

    /** [drawIn] for an integer (see [drawInt]). */
    fun drawIntIn(
        font: Font, value: Int, rx: Int, ry: Int, rw: Int, rh: Int,
        k: Int = 1, rotation: Int = 0, invert: Boolean = false, opaque: Boolean = false, pad: Int = 0,
        tracking: Int = font.tracking, align: Int = TextLayout.CENTRE, signed: Boolean = false,
        shadow: Boolean = true, halo: Boolean = false,
    ) {
        format(font, value, signed)
        val tw = font.widthOf(digitsLen, k, tracking)
        val th = font.height(k)
        val x = rx + TextLayout.placeX(rotation, rw, rh, tw, th, font.capTop * k, font.capHeight * k, align)
        val y = ry + TextLayout.placeY(rotation, rw, rh, tw, th, font.capTop * k, font.capHeight * k, align)
        drawRun(font, null, digitsLen, x, y, k, rotation, invert, opaque, pad, tracking, shadow, halo)
    }

    // ---- internals ------------------------------------------------------------------------

    /** Draws [n] chars from [text], or from [digits] when [text] is null. */
    private fun drawRun(
        font: Font, text: CharSequence?, n: Int, x: Int, y: Int,
        k: Int, rotation: Int, invert: Boolean, opaque: Boolean, pad: Int, tracking: Int, shadow: Boolean,
        halo: Boolean,
    ) {
        if (n <= 0) return
        val r = renderer
        val gw = font.glyphWidth * k
        val gh = font.glyphHeight * k
        val tw = font.widthOf(n, k, tracking)
        val th = gh
        if (opaque) {
            val fw = TextLayout.footprintW(rotation, tw, th)
            val fh = TextLayout.footprintH(rotation, tw, th)
            r.rect(x - pad, y - pad, fw + 2 * pad, fh + 2 * pad, ink = invert)
        } else {
            TextLayout.isSideways(rotation) // validates rotation even when nothing is opaque
        }
        val step = (font.glyphWidth + tracking) * k
        if (halo) {
            // Pass 0 (T41): every glyph's paper cut-out first, so no halo lands on a neighbour's ink.
            require(font.hasHalo) { "${font.name} has no halo rows (GenerateFontsTask.HALO_FACES)" }
            for (i in 0 until n) {
                val c = if (text != null) text[i] else digits[i]
                val m = font.glyph(c).haloScaled(k) ?: continue
                val lx = i * step
                val gx = x + TextLayout.mapX(rotation, tw, th, lx, 0, gw, gh)
                val gy = y + TextLayout.mapY(rotation, tw, th, lx, 0, gw, gh)
                r.blit(m, gx - k, gy - k, rotation, invert = !invert)
            }
        }
        if (shadow && font.hasShadow) {
            // Pass 1: every shadow first, so no glyph's shadow lands on a neighbour's ink.
            for (i in 0 until n) {
                val c = if (text != null) text[i] else digits[i]
                val m = font.glyph(c).shadowMask(k, rotation) ?: continue
                val lx = i * step
                val gx = x + TextLayout.mapX(rotation, tw, th, lx, 0, gw, gh)
                val gy = y + TextLayout.mapY(rotation, tw, th, lx, 0, gw, gh)
                r.fillMask(m, gx, gy, Role.SHADOW, invert)
            }
        }
        for (i in 0 until n) {
            val c = if (text != null) text[i] else digits[i]
            val g = font.glyph(c)
            if (g.isBlank) continue
            val lx = i * step
            val gx = x + TextLayout.mapX(rotation, tw, th, lx, 0, gw, gh)
            val gy = y + TextLayout.mapY(rotation, tw, th, lx, 0, gw, gh)
            r.blit(g.scaled(k), gx, gy, rotation, invert, opaque = false)
        }
    }

    private fun format(font: Font, value: Int, signed: Boolean) {
        digitsLen = Font.formatInt(value, signed, font.minusChar, digits)
    }
}
