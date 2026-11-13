package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.TextLayout
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * A pure-JVM [WidgetCanvas]: one int per dither px, mirroring `DitherRenderer`/`TextRenderer`
 * semantics (global-phase fills, rotated blits, opaque text backing, inversion).
 *
 * Values: [PAPER] / [INK]. With [flat] = true, dither fills are recorded as a per-tile code
 * ([FLAT] + tile id, or [FLAT_INV] + id when inverted) instead of their pattern, so a rotated render
 * can be compared with a rotated upright one (patterns are phase-anchored to the screen, so their
 * pixels legitimately differ between rotations; the regions they cover must not).
 */
class RasterCanvas(val width: Int, val height: Int, private val flat: Boolean = false) : WidgetCanvas {

    val px = IntArray(width * height)
    var inverted = false
    private var clip: IRect? = null
    private val clipRect = IRect()

    override var canInvertInPlace: Boolean = true

    operator fun get(x: Int, y: Int): Int = px[y * width + x]

    private fun put(x: Int, y: Int, v: Int) {
        if (x < 0 || y < 0 || x >= width || y >= height) throw IllegalStateException("draw outside canvas at ($x,$y)")
        val c = clip
        if (c != null && !c.contains(x, y)) return
        px[y * width + x] = v
    }

    private fun solid(ink: Boolean) = if (ink != inverted) INK else PAPER

    override fun fill(role: Role, x: Int, y: Int, w: Int, h: Int) {
        val t = role.tile
        when (t.inkCount) {
            0 -> return rect(x, y, w, h, ink = false)
            t.width * t.height -> return rect(x, y, w, h, ink = true)
        }
        for (gy in y until y + h) for (gx in x until x + w) {
            put(gx, gy, if (flat) (if (inverted) FLAT_INV else FLAT) + t.id else solid(t.isInkAt(gx, gy)))
        }
    }

    override fun rect(x: Int, y: Int, w: Int, h: Int, ink: Boolean) {
        val v = solid(ink)
        for (gy in y until y + h) for (gx in x until x + w) put(gx, gy, v)
    }

    override fun frame(x: Int, y: Int, w: Int, h: Int, t: Int, ink: Boolean) {
        if (w <= 0 || h <= 0) return
        if (w <= 2 * t || h <= 2 * t) return rect(x, y, w, h, ink)
        rect(x, y, w, t, ink)
        rect(x, y + h - t, w, t, ink)
        rect(x, y + t, t, h - 2 * t, ink)
        rect(x + w - t, y + t, t, h - 2 * t, ink)
    }

    override fun blit(img: MonoImage, x: Int, y: Int, rotation: Int, invert: Boolean, opaque: Boolean) {
        val side = TextLayout.isSideways(rotation)
        val sw = img.width
        val sh = img.height
        val fw = if (side) sh else sw
        val fh = if (side) sw else sh
        if (opaque) rect(x, y, fw, fh, ink = invert)
        val v = solid(!invert)
        for (fy in 0 until fh) for (fx in 0 until fw) {
            val u: Int
            val w: Int
            when (rotation) {
                0 -> { u = fx; w = fy }
                90 -> { u = fy; w = sh - 1 - fx }
                180 -> { u = sw - 1 - fx; w = sh - 1 - fy }
                else -> { u = sw - 1 - fy; w = fx }
            }
            if (img.isInk(u, w)) put(x + fx, y + fy, v)
        }
    }

    override fun text(font: Font, text: CharSequence, x: Int, y: Int, k: Int, rotation: Int, invert: Boolean, opaque: Boolean, pad: Int, halo: Boolean) {
        val n = text.length
        if (n == 0) return
        val gw = font.glyphWidth * k
        val gh = font.glyphHeight * k
        val tw = font.widthOf(n, k)
        val step0 = font.advance * k
        if (halo) {
            for (i in 0 until n) {
                val m = font.glyph(text[i]).haloScaled(k) ?: continue
                val lx = i * step0
                blit(
                    m,
                    x + TextLayout.mapX(rotation, tw, gh, lx, 0, gw, gh) - k,
                    y + TextLayout.mapY(rotation, tw, gh, lx, 0, gw, gh) - k,
                    rotation, invert = !invert, opaque = false,
                )
            }
        } else if (opaque) {
            rect(x - pad, y - pad, TextLayout.footprintW(rotation, tw, gh) + 2 * pad, TextLayout.footprintH(rotation, tw, gh) + 2 * pad, ink = invert)
        }
        val step = font.advance * k
        for (i in 0 until n) {
            val g = font.glyph(text[i])
            if (g.isBlank) continue
            val lx = i * step
            blit(
                g.scaled(k),
                x + TextLayout.mapX(rotation, tw, gh, lx, 0, gw, gh),
                y + TextLayout.mapY(rotation, tw, gh, lx, 0, gw, gh),
                rotation, invert, opaque = false,
            )
        }
    }

    override fun invert(x: Int, y: Int, w: Int, h: Int) {
        for (gy in y until y + h) for (gx in x until x + w) {
            val v = this[gx, gy]
            put(gx, gy, when {
                v == PAPER -> INK
                v == INK -> PAPER
                v >= FLAT_INV -> v - FLAT_INV + FLAT
                else -> v - FLAT + FLAT_INV
            })
        }
    }

    override fun beginInvertedClip(x: Int, y: Int, w: Int, h: Int) {
        clip = clipRect.setXywh(x, y, w, h)
        inverted = !inverted
    }

    override fun endInvertedClip() {
        clip = null
        inverted = !inverted
    }

    fun copy(): IntArray = px.copyOf()

    /** This raster turned [rotation]° clockwise (as a new array, row-major, of the turned size). */
    fun rotated(rotation: Int): IntArray {
        val side = TextLayout.isSideways(rotation)
        val ow = if (side) height else width
        val out = IntArray(px.size)
        for (y in 0 until height) for (x in 0 until width) {
            val sx = TextLayout.pointX(rotation, width, height, x, y)
            val sy = TextLayout.pointY(rotation, width, height, x, y)
            out[sy * ow + sx] = this[x, y]
        }
        return out
    }

    /** Ink pixels inside the rect. */
    fun inkIn(r: IRect): Int {
        var n = 0
        for (y in r.top until r.bottom) for (x in r.left until r.right) if (this[x, y] == INK) n++
        return n
    }

    /** Writes a PNG at [scale] device px per dither px (pure #000/#FFF; flat fills shown as grey). */
    fun writePng(file: File, scale: Int = 1) {
        file.parentFile.mkdirs()
        val img = BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height * scale) for (x in 0 until width * scale) {
            val v = this[x / scale, y / scale]
            img.setRGB(x, y, when (v) { INK -> 0x000000; PAPER -> 0xFFFFFF; else -> 0x888888 })
        }
        ImageIO.write(img, "png", file)
    }

    companion object {
        const val PAPER = 0
        const val INK = 1
        const val FLAT = 100
        const val FLAT_INV = 1000
    }
}

/** Shared helpers for widget tests. */
object WidgetTesting {
    val scales = 2..6
    val rotations = intArrayOf(0, 90, 180, 270)

    /** Where test renders go (gitignored build dir). */
    val outDir = File("build/widgets")

    fun assertFingertip(m: WidgetMetrics, r: IRect, what: String) {
        if (r.isEmpty) return
        val short = minOf(r.width, r.height)
        org.junit.Assert.assertTrue("$m $what $r short side $short < ${m.target}", short >= m.target)
    }

    fun inside(inner: IRect, outer: IRect) =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    fun overlaps(a: IRect, b: IRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /**
     * Draws a local [w]×[h] scene upright and at 90/180/270 (frame at the origin) and asserts each
     * turned render is exactly the upright render turned (dither fills compared by region, [RasterCanvas.flat]).
     */
    fun assertRotationExact(w: Int, h: Int, draw: (WidgetPainter, WidgetFrame) -> Unit) {
        val up = RasterCanvas(w, h, flat = true)
        draw(WidgetPainter(up), WidgetFrame().set(0, 0, w, h, 0))
        for (r in intArrayOf(90, 180, 270)) {
            val f = WidgetFrame().set(0, 0, w, h, r)
            val c = RasterCanvas(f.footprintW, f.footprintH, flat = true)
            draw(WidgetPainter(c), f)
            org.junit.Assert.assertArrayEquals("rotation $r", up.rotated(r), c.px)
        }
    }

    /**
     * For every entry in [region]: the in-place inversion of its feedback rect must equal redrawing its
     * owner inverted inside a clip (the API < 29 path), pixel for pixel.
     */
    fun assertFallbackMatchesInvert(w: Int, h: Int, region: HitRegion, drawAll: (WidgetPainter) -> Unit) {
        for (i in 0 until region.size) {
            val a = RasterCanvas(w, h)
            drawAll(WidgetPainter(a))
            WidgetPainter(a).drawPressed(region, region.idAt(i))
            val b = RasterCanvas(w, h).apply { canInvertInPlace = false }
            drawAll(WidgetPainter(b))
            WidgetPainter(b).drawPressed(region, region.idAt(i))
            org.junit.Assert.assertArrayEquals("id ${region.idAt(i)} fallback differs", a.px, b.px)
        }
    }
}
