package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.type.TextLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T41: glyphs on a dither fill drawn on their halo, through the JVM raster canvas — an icon's cut-out
 * turns with it (0/90/180/270), swaps with `invert` and with the canvas's global inversion, and text on
 * its glyph halos turns exactly with the text.
 */
class HaloDrawTest {

    private val m = WidgetMetrics(4)
    private val k = m.k

    /** Draws [icon] opaque (= on its halo) at [at] on a TAB-filled w×h canvas, frame rotation [rot]. */
    private fun draw(icon: Icon, rot: Int, invert: Boolean = false, inverted: Boolean = false, flat: Boolean = false): RasterCanvas {
        val n = 16 * k + 8 * k
        val f = WidgetFrame().set(0, 0, n, n, rot)
        val c = RasterCanvas(f.footprintW, f.footprintH, flat)
        c.inverted = inverted
        val p = WidgetPainter(c)
        p.fill(f, Role.TAB, IRect().setXywh(0, 0, n, n))
        p.icon(f, m, icon, IRect().setXywh(4 * k, 4 * k, 0, 0), invert, opaque = true)
        return c
    }

    /** Expected value at canvas (x, y) for the upright draw: ink on ink, paper on the halo, else the fill. */
    private fun expectUpright(icon: Icon, c: RasterCanvas, x: Int, y: Int, invert: Boolean, inverted: Boolean): Int? {
        val ix = x - 4 * k
        val iy = y - 4 * k
        val ink = if (invert != inverted) RasterCanvas.PAPER else RasterCanvas.INK
        val paper = if (invert != inverted) RasterCanvas.INK else RasterCanvas.PAPER
        if (ix in 0 until 16 * k && iy in 0 until 16 * k && icon.isInk(ix / k, iy / k)) return ink
        val hx = ix + k
        val hy = iy + k
        if (hx in 0 until 18 * k && hy in 0 until 18 * k && icon.halo.isInk(hx / k, hy / k)) return paper
        return null // the fill
    }

    @Test fun iconHaloIsPaperUnderTheIconAndTheFillElsewhere() {
        for (inverted in listOf(false, true)) for (invert in listOf(false, true)) for (icon in listOf(Icon.CLOSE, Icon.MORE, Icon.CHEVRON_RIGHT, Icon.RESET)) {
            val c = draw(icon, 0, invert, inverted)
            val pure = RasterCanvas(c.width, c.height).apply { this.inverted = inverted; fill(Role.TAB, 0, 0, width, height) }
            for (y in 0 until c.height) for (x in 0 until c.width) {
                val e = expectUpright(icon, c, x, y, invert, inverted) ?: pure[x, y]
                assertEquals("${icon.id} invert=$invert inverted=$inverted ($x,$y)", e, c[x, y])
            }
        }
    }

    @Test fun iconHaloTurnsWithTheIcon() {
        for (icon in Icon.entries) {
            val up = draw(icon, 0, flat = true)
            for (rot in intArrayOf(90, 180, 270)) {
                assertEquals("${icon.id} $rot", up.rotated(rot).toList(), draw(icon, rot, flat = true).px.toList())
            }
        }
    }

    @Test fun haloTextSitsOnPaperAndTurnsExactly() {
        val text = "UNDO Setup 20"
        for (font in listOf(Fonts.UI, Fonts.NUM_M)) {
            val run = if (font == Fonts.NUM_M) "1209" else text
            WidgetTesting.assertRotationExact(font.widthOf(run.length, k) + 4 * k, font.height(k) + 4 * k) { p, f ->
                val s = IRect()
                f.map(0, 0, f.width, f.height, s)
                p.c.fill(Role.TAB, s.left, s.top, s.width, s.height)
                p.text(f, m, font, run, 0, run.length, IRect().setXywh(0, 0, f.width, f.height), TextLayout.CENTRE, invert = false, opaque = true)
            }
        }
    }

    @Test fun haloTextNeverLeavesDitherInsideTheRunsCutOut() {
        // Outside the glyphs' halos nothing changes, and between neighbouring glyphs the halos meet (the
        // generator's bridge rule): no 1-px dither column along the line, for every pair of glyphs.
        for (font in listOf(Fonts.UI, Fonts.NUM_M)) {
            val chars = font.glyphs.map { it.char }.filter { !font.glyph(it).isBlank }.joinToString("")
            val pairs = StringBuilder()
            for (a in chars) for (b in chars) pairs.append(a).append(b).append(' ')
            assertRun(font, pairs.toString())
        }
    }

    private fun assertRun(font: au.com.josephmark.dithertracker.type.Font, word: String) {
        val w = font.widthOf(word.length, 1) + 4
        val h = font.height(1) + 4
        val c = RasterCanvas(w, h)
        c.fill(Role.TAB, 0, 0, w, h)
        c.text(font, word, 2, 2, 1, 0, invert = false, opaque = false, pad = 0, halo = true)
        val inHalo = BooleanArray(w * h)
        for (i in word.indices) {
            val g: MonoImage = font.glyph(word[i]).halo ?: continue
            val gx = 2 + i * font.advance - 1
            for (y in 0 until g.height) for (x in 0 until g.width) if (g.isInk(x, y)) inHalo[(1 + y) * w + gx + x] = true
        }
        val pure = RasterCanvas(w, h).apply { fill(Role.TAB, 0, 0, w, h) }
        var slivers = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (inHalo[y * w + x]) continue
            assertEquals("${font.name} outside the halos ($x,$y)", pure[x, y], c[x, y])
            if (x in 1 until w - 1 && inHalo[y * w + x - 1] && inHalo[y * w + x + 1]) slivers++
        }
        assertEquals("${font.name}: 1-px dither slivers between glyph halos", 0, slivers)
    }
}
