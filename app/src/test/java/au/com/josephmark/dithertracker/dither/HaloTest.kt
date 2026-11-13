package au.com.josephmark.dithertracker.dither

import au.com.josephmark.dithertracker.type.Fonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * T41 halos: the build step's generated masks ([Icon.halo], [au.com.josephmark.dithertracker.type.Glyph.halo])
 * and the runtime twin ([HaloMask]) against an independent, set-based implementation of the rule —
 * `tools.py` `halo_mask(ink, 'f1')` (holes filled, dilated 1 px 8-connected), then enclosed dither and
 * 1-px slivers filled until stable.
 */
class HaloTest {

    private data class P(val x: Int, val y: Int)

    /** tools.py `fill_holes` + `dilate(_, 1)`, shifted into the (w+2)×(h+2) frame. */
    private fun f1(img: MonoImage): Set<P> {
        val w = img.width
        val h = img.height
        val ink = HashSet<P>()
        for (y in 0 until h) for (x in 0 until w) if (img.isInk(x, y)) ink += P(x, y)
        val outside = HashSet<P>()
        val stack = ArrayDeque<P>()
        for (x in 0 until w) { stack += P(x, 0); stack += P(x, h - 1) }
        for (y in 0 until h) { stack += P(0, y); stack += P(w - 1, y) }
        while (stack.isNotEmpty()) {
            val p = stack.removeLast()
            if (p in outside || p in ink || p.x !in 0 until w || p.y !in 0 until h) continue
            outside += p
            stack += P(p.x + 1, p.y); stack += P(p.x - 1, p.y); stack += P(p.x, p.y + 1); stack += P(p.x, p.y - 1)
        }
        val out = HashSet<P>()
        for (y in 0 until h) for (x in 0 until w) {
            if (P(x, y) in outside) continue
            for (dx in -1..1) for (dy in -1..1) out += P(x + dx + 1, y + dy + 1)
        }
        return out
    }

    /**
     * f1, then (enclosed dither, then slivers on the pass's starting state) until nothing is added; text
     * ([bridge]) then runs every row reaching column 2 / fw − 3 on to the frame edge and cleans again.
     */
    private fun reference(img: MonoImage, bridge: Boolean = false): Set<P> {
        val fw = img.width + 2
        val fh = img.height + 2
        val halo = HashSet(f1(img))
        cleanRef(halo, fw, fh)
        if (bridge) {
            for (y in 0 until fh) {
                if (P(2, y) in halo) { halo += P(0, y); halo += P(1, y) }
                if (P(fw - 3, y) in halo) { halo += P(fw - 1, y); halo += P(fw - 2, y) }
            }
            cleanRef(halo, fw, fh)
        }
        return halo
    }

    private fun cleanRef(halo: MutableSet<P>, fw: Int, fh: Int) {
        while (true) {
            val before = halo.size
            halo += enclosed(halo, fw, fh)
            halo += slivers(halo, fw, fh)
            if (halo.size == before) return
        }
    }

    private fun inFrame(p: P, fw: Int, fh: Int) = p.x in 0 until fw && p.y in 0 until fh

    private fun enclosed(halo: Set<P>, fw: Int, fh: Int): Set<P> {
        val reached = HashSet<P>()
        val q = ArrayDeque<P>()
        for (x in 0 until fw) for (y in 0 until fh) if (x == 0 || y == 0 || x == fw - 1 || y == fh - 1) q += P(x, y)
        while (q.isNotEmpty()) {
            val p = q.removeFirst()
            if (!inFrame(p, fw, fh) || p in halo || p in reached) continue
            reached += p
            q += P(p.x + 1, p.y); q += P(p.x - 1, p.y); q += P(p.x, p.y + 1); q += P(p.x, p.y - 1)
        }
        val out = HashSet<P>()
        for (x in 0 until fw) for (y in 0 until fh) { val p = P(x, y); if (p !in halo && p !in reached) out += p }
        return out
    }

    private fun slivers(halo: Set<P>, fw: Int, fh: Int): Set<P> {
        val out = HashSet<P>()
        for (x in 0 until fw) for (y in 0 until fh) {
            val p = P(x, y)
            if (p in halo) continue
            val lr = P(x - 1, y) in halo && P(x + 1, y) in halo
            val ud = P(x, y - 1) in halo && P(x, y + 1) in halo
            if (lr || ud) out += p
        }
        return out
    }

    private fun pixels(img: MonoImage): Set<P> {
        val s = HashSet<P>()
        for (y in 0 until img.height) for (x in 0 until img.width) if (img.isInk(x, y)) s += P(x, y)
        return s
    }

    private fun assertHaloOf(what: String, glyph: MonoImage, halo: MonoImage, bridge: Boolean = false) {
        assertEquals("$what halo width", glyph.width + 2, halo.width)
        assertEquals("$what halo height", glyph.height + 2, halo.height)
        val got = pixels(halo)
        val f1 = f1(glyph)
        assertEquals("$what: halo != reference", reference(glyph, bridge), got)
        assertTrue("$what: tools.py f1 not inside the halo", got.containsAll(f1))
        // Clean: nothing enclosed, no 1-px sliver, and paper under every ink pixel's 3×3.
        assertTrue("$what: enclosed dither", enclosed(got, halo.width, halo.height).isEmpty())
        assertTrue("$what: 1-px sliver", slivers(got, halo.width, halo.height).isEmpty())
        for (p in pixels(glyph)) for (dx in -1..1) for (dy in -1..1) {
            assertTrue("$what: ink $p not on paper", P(p.x + 1 + dx, p.y + 1 + dy) in got)
        }
    }

    @Test fun everyGeneratedIconHaloMatchesTheReference() {
        for (icon in Icon.entries) assertHaloOf(icon.id, icon, icon.halo)
    }

    @Test fun cleanupOnlyAddsTrappedDitherOnTopOfToolsPyF1() {
        // The clean-up is the only departure from tools.py f1; record where it bites (all small).
        var changed = 0
        for (icon in Icon.entries) {
            val extra = pixels(icon.halo) - f1(icon)
            if (extra.isNotEmpty()) changed++
            assertTrue("${icon.id}: ${extra.size} px added by the clean-up", extra.size <= 40)
        }
        assertTrue("some icon gets cleaned (e.g. reset's enclosed ring)", changed > 0)
        assertTrue(pixels(Icon.CLOSE.halo) == f1(Icon.CLOSE)) // the close X is plain f1
    }

    @Test fun everyHaloFaceGlyphMatchesTheReference() {
        for (font in listOf(Fonts.UI, Fonts.NUM_M)) {
            assertTrue(font.name, font.hasHalo)
            for (g in font.glyphs + font.notdef) {
                if (g.isBlank) { assertNull("${font.name} '${g.char}'", g.halo); continue }
                val h = g.halo
                assertNotNull("${font.name} '${g.char}'", h)
                assertHaloOf("${font.name} '${g.char}'", g, h!!, bridge = true)
            }
        }
        assertFalse(Fonts.NUM_L.hasHalo)
    }

    @Test fun runtimeTwinMatchesTheBuildStep() {
        for (icon in Icon.entries) assertEquals(icon.id, BitMask(18, 18, bitsOf(icon.halo)), HaloMask.of(icon))
    }

    @Test fun runtimeTwinMatchesTheReferenceOnRandomShapes() {
        val rnd = Random(41)
        repeat(300) { n ->
            val w = 1 + rnd.nextInt(14)
            val h = 1 + rnd.nextInt(14)
            val density = rnd.nextDouble(0.1, 0.7)
            val img = BitMask(w, h, BooleanArray(w * h) { rnd.nextDouble() < density })
            assertHaloOf("random #$n", img, HaloMask.of(img))
        }
    }

    @Test fun rectGlyphsBecomeMasksAtTheirBoundingBox() {
        val rects = arrayOf(IRect().setXywh(10, 20, 3, 15), IRect().setXywh(16, 20, 3, 15), IRect().setXywh(22, 20, 3, 15))
        val b = IRect()
        val m = HaloMask.rects(rects, 0, 3, b)
        assertEquals(IRect().set(10, 20, 25, 35), b)
        assertEquals(15, m.width); assertEquals(15, m.height)
        assertTrue(m.isInk(0, 0)); assertFalse(m.isInk(3, 0)); assertTrue(m.isInk(6, 14))
        // Bars 3 apart: a 1-px halo would leave a 1-px dither sliver between them; it is filled, so the
        // menu glyph's cut-out is a tight 17×17 plate.
        val halo = HaloMask.of(m)
        assertEquals(17 * 17, pixels(halo).size)
        // Equal masks are equal values (a relayout hits the renderer's cache).
        assertEquals(m, HaloMask.rects(arrayOf(IRect().setXywh(0, 0, 3, 15), IRect().setXywh(6, 0, 3, 15), IRect().setXywh(12, 0, 3, 15)), 0, 3, IRect()))
        assertEquals(m.hashCode(), HaloMask.rects(rects, 0, 3, IRect()).hashCode())
    }

    @Test fun widerRadiusDilatesFurther() {
        val dot = BitMask(1, 1, booleanArrayOf(true))
        val h2 = HaloMask.of(dot, 2)
        assertEquals(5, h2.width)
        assertEquals(25, pixels(h2).size)
    }

    private fun bitsOf(img: MonoImage) = BooleanArray(img.width * img.height) { img.isInk(it % img.width, it / img.width) }
}
