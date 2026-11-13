package au.com.josephmark.dithertracker.type

import au.com.josephmark.dithertracker.dither.MonoImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutTest {

    private val rotations = intArrayOf(0, 90, 180, 270)

    /** Independent reference: clockwise rotation of pixel (u, v) in a W×H box. */
    private fun refX(rot: Int, w: Int, h: Int, u: Int, v: Int) = when (rot) {
        0 -> u; 90 -> h - 1 - v; 180 -> w - 1 - u; else -> v
    }

    private fun refY(rot: Int, w: Int, h: Int, u: Int, v: Int) = when (rot) {
        0 -> v; 90 -> u; 180 -> h - 1 - v; else -> w - 1 - u
    }

    @Test fun pointMappingMatchesReferenceRotation() {
        val w = 7; val h = 3
        for (rot in rotations) for (u in 0 until w) for (v in 0 until h) {
            assertEquals(refX(rot, w, h, u, v), TextLayout.pointX(rot, w, h, u, v))
            assertEquals(refY(rot, w, h, u, v), TextLayout.pointY(rot, w, h, u, v))
        }
    }

    @Test fun rectMappingIsTheBoundingBoxOfItsRotatedPixels() {
        val bw = 23; val bh = 9
        for (rot in rotations) {
            val lx = 5; val ly = 2; val w = 6; val h = 4
            var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE
            for (u in lx until lx + w) for (v in ly until ly + h) {
                minX = minOf(minX, refX(rot, bw, bh, u, v)); minY = minOf(minY, refY(rot, bw, bh, u, v))
            }
            assertEquals("rot $rot x", minX, TextLayout.mapX(rot, bw, bh, lx, ly, w, h))
            assertEquals("rot $rot y", minY, TextLayout.mapY(rot, bw, bh, lx, ly, w, h))
            assertEquals(if (rot % 180 == 0) bw else bh, TextLayout.footprintW(rot, bw, bh))
            assertEquals(if (rot % 180 == 0) bh else bw, TextLayout.footprintH(rot, bw, bh))
        }
    }

    @Test(expected = IllegalArgumentException::class) fun badRotationThrows() {
        TextLayout.mapX(45, 1, 1, 0, 0, 1, 1)
    }

    /**
     * Composes a string the way [TextRenderer] does (glyph i at local (i·step, 0), placed with
     * mapX/mapY and drawn with the blit's own rotation) and checks the result equals the unrotated
     * string image rotated as a whole.
     */
    @Test fun perGlyphCompositionEqualsWholeStringRotation() {
        for (font in Fonts.all) for (k in 1..2) {
            val text = if (font === Fonts.UI) "Ag(4)" else "-12"
            val tw = font.measure(text, k)
            val th = font.height(k)
            val flat = compose(font, text, k, 0, tw, th)
            for (rot in rotations) {
                val fw = TextLayout.footprintW(rot, tw, th)
                val fh = TextLayout.footprintH(rot, tw, th)
                val got = compose(font, text, k, rot, tw, th)
                assertEquals(fw * fh, got.size)
                for (u in 0 until tw) for (v in 0 until th) {
                    val x = refX(rot, tw, th, u, v); val y = refY(rot, tw, th, u, v)
                    assertEquals("${font.name} k$k rot$rot ($u,$v)", flat[v * tw + u], got[y * fw + x])
                }
            }
        }
    }

    private fun compose(font: Font, text: String, k: Int, rot: Int, tw: Int, th: Int): BooleanArray {
        val fw = TextLayout.footprintW(rot, tw, th)
        val fh = TextLayout.footprintH(rot, tw, th)
        val out = BooleanArray(fw * fh)
        val gw = font.glyphWidth * k; val gh = font.glyphHeight * k
        val step = font.advance * k
        for (i in text.indices) {
            val img: MonoImage = font.glyph(text[i]).scaled(k)
            val gx = TextLayout.mapX(rot, tw, th, i * step, 0, gw, gh)
            val gy = TextLayout.mapY(rot, tw, th, i * step, 0, gw, gh)
            // What DitherRenderer.blit does with `rotation`: the glyph turned about its own box.
            for (u in 0 until gw) for (v in 0 until gh) if (img.isInk(u, v)) {
                val x = gx + refX(rot, gw, gh, u, v); val y = gy + refY(rot, gw, gh, u, v)
                assertTrue(x in 0 until fw && y in 0 until fh)
                out[y * fw + x] = true
            }
        }
        return out
    }

    @Test fun centringPlacesTheCapBandInTheMiddle() {
        // Upright: text 36×28 (cap band = whole box) in a 100×60 rect → (32, 16).
        assertEquals(32, TextLayout.placeX(0, 100, 60, 36, 28, 0, 28))
        assertEquals(16, TextLayout.placeY(0, 100, 60, 36, 28, 0, 28))
        // ui: 9-row box with cap band rows 0..6 — the band, not the box, is centred.
        assertEquals(4, TextLayout.placeY(0, 50, 15, 20, 9, 0, 7))
        // Sideways: footprint is 28×36 inside the 60×100 rect, still centred.
        for (rot in intArrayOf(90, 270)) {
            assertEquals(16, TextLayout.placeX(rot, 60, 100, 36, 28, 0, 28))
            assertEquals(32, TextLayout.placeY(rot, 60, 100, 36, 28, 0, 28))
        }
        // Asymmetric band: 180° mirrors the placement so the seat looks the same from its side.
        val y0 = TextLayout.placeY(0, 50, 20, 20, 9, 0, 7)
        val y180 = TextLayout.placeY(180, 50, 20, 20, 9, 0, 7)
        assertEquals(20 - (y0 + 9), y180)
        // END alignment along the reading direction; for 90° reading runs top→bottom.
        assertEquals(80, TextLayout.placeX(0, 100, 60, 20, 9, 0, 9, TextLayout.END))
        assertEquals(80, TextLayout.placeY(90, 60, 100, 20, 9, 0, 9, TextLayout.END))
        assertEquals(0, TextLayout.placeY(270, 60, 100, 20, 9, 0, 9, TextLayout.END))
    }
}
