package au.com.josephmark.dithertracker.purity

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Patterns
import au.com.josephmark.dithertracker.dither.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The purity suite's checks catch what they claim to (T22): each is shown a planted violation. */
class PurityChecksTest {

    private fun raster() = RasterRenderer(DitherGrid(64 * 4, 48 * 4, 4)).also { it.clear() }

    /** A [w]×[h] image of [tile] baked at the image's own phase (what a pre-dithered art file is). */
    private fun baked(tile: au.com.josephmark.dithertracker.dither.Tile, w: Int, h: Int) = object : MonoImage {
        override val width = w
        override val height = h
        override fun isInk(x: Int, y: Int) = tile.isInkAt(x, y)
    }

    @Test fun globalPhaseFillsPass() {
        val r = raster()
        for ((i, t) in Patterns.all.withIndex()) r.fill(t, 3 + i, 5 + 2 * i, 20, 17)
        r.fill(Role.DRAWER, IRect(1, 30, 63, 48))
        assertEquals(emptyList<String>(), PurityChecks.phaseErrors(r))
    }

    @Test fun aBakedPatternBlittedOffPhaseIsCaught() {
        for (t in listOf(Patterns.D[4], Patterns.D[2], Patterns.SPARSE, Patterns.DIAG, Patterns.D[12])) {
            val r = raster()
            r.blit(baked(t, 24, 24), 9, 16, opaque = true) // x off the tile's period
            assertTrue("$t", PurityChecks.phaseErrors(r).isNotEmpty())
            val ok = raster()
            ok.blit(baked(t, 24, 24), 8, 16, opaque = true) // on the lattice: in phase
            assertEquals("$t", emptyList<String>(), PurityChecks.phaseErrors(ok))
        }
    }

    @Test fun drawsOutsideTheGridAreRecordedUnlessClipped() {
        val r = raster()
        r.rect(-1, 0, 3, 2)
        r.blit(Patterns.CHECKER, 63, 47)
        assertEquals(3, r.outside) // the rect's 2 px at x = -1, and CHECKER's ink (1,1) at (64,48)
        val c = raster()
        c.clipTo(IRect(0, 0, 64, 48))
        c.rect(-5, -5, 80, 80)
        c.unclip()
        assertEquals(0, c.outside)
        assertTrue(c.clipBalanced)
    }

    @Test fun complementCheck() {
        val r = raster()
        r.fill(Role.TAB, IRect(4, 4, 40, 30))
        val normal = r.px.copyOf()
        r.inverted = true
        r.clear()
        r.fill(Role.TAB, IRect(4, 4, 40, 30))
        assertNull(PurityChecks.complementError(normal, r.px, r.width))
        r.rect(50, 40, 1, 1, ink = true) // white in dark mode where the normal render is white too
        assertNotNull(PurityChecks.complementError(normal, r.px, r.width))
    }
}
