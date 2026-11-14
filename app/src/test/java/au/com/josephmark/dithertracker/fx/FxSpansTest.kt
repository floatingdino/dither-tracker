package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.Patterns
import au.com.josephmark.dithertracker.purity.RasterRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T55: drawing `#`, `o`, `x` and `fill` tile layers from their rects ([FxLayer.rects]) is pixel for
 * pixel the mask draw it replaces — every frame of every shipped effect, every rotation, both colour
 * modes, over a busy underlay (ink, paper and a dither, so cuts and knock-outs show), at an odd
 * offset (global tile phase).
 */
class FxSpansTest {

    @Test fun rectsCoverExactlyTheLayersPixels() {
        for (fx in Fx.entries) for (f in 0 until fx.frameCount) {
            val layers = listOf(fx.ink(f), fx.knockout(f), fx.cut(f)) + fx.tiles.indices.map { fx.tileLayer(it, f) }
            for (l in layers) {
                val hit = BooleanArray(l.width * l.height)
                val rs = l.rects()
                var i = 0
                while (i < rs.size) {
                    for (y in rs[i + 1] until rs[i + 1] + rs[i + 3]) for (x in rs[i] until rs[i] + rs[i + 2]) {
                        assertTrue("${fx.id} f$f overlap", !hit[y * l.width + x])
                        hit[y * l.width + x] = true
                    }
                    i += 4
                }
                for (y in 0 until l.height) for (x in 0 until l.width) assertEquals("${fx.id} f$f ($x,$y)", l.isInk(x, y), hit[y * l.width + x])
            }
        }
    }

    @Test fun rectDrawsMatchMaskDraws() {
        val grid = DitherGrid(260 * 4, 260 * 4, 4)
        val a = RasterRenderer(grid)
        val b = RasterRenderer(grid)
        val spans = FxRenderer(a).also { it.spans = true }
        val masks = FxRenderer(b).also { it.spans = false }
        assertTrue(spans.spans && !masks.spans)
        fun underlay(r: RasterRenderer) {
            r.clear()
            r.fill(Patterns.D[8], 0, 0, 130, 260)
            r.rect(130, 40, 80, 120, ink = true)
        }
        var compared = 0
        for (fx in Fx.entries) for (rot in intArrayOf(0, 90, 180, 270)) for (inv in listOf(false, true)) {
            a.inverted = inv; b.inverted = inv
            for (f in 0 until fx.frameCount) {
                underlay(a); underlay(b)
                spans.drawAt(fx, f, 7, 11, rot)
                masks.drawAt(fx, f, 7, 11, rot)
                assertTrue("${fx.id} f$f rot $rot inv $inv", a.px.contentEquals(b.px))
                compared++
            }
        }
        assertTrue(compared > Fx.entries.size * 8)
    }
}
