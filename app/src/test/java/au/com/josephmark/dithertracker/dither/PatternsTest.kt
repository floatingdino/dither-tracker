package au.com.josephmark.dithertracker.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternsTest {

    @Test fun bayerLevelsHaveExactDensity() {
        assertEquals(17, Patterns.D.size)
        for (k in 0..16) {
            val t = Patterns.D[k]
            assertEquals("D$k", t.name)
            assertEquals(4, t.width); assertEquals(4, t.height)
            assertEquals("D$k ink count", k, t.inkCount)
        }
    }

    @Test fun bayerLevelsAreMonotone() {
        for (k in 1..16) for (y in 0 until 4) for (x in 0 until 4) {
            if (Patterns.D[k - 1].isInk(x, y)) assertTrue("D$k must contain D${k - 1}", Patterns.D[k].isInk(x, y))
        }
    }

    @Test fun checkerEqualsD8AndDottedAlternates() {
        for (y in 0 until 4) for (x in 0 until 4) assertEquals(Patterns.D[8].isInk(x, y), Patterns.CHECKER.isInkAt(x, y))
        // a 1-px row/column of the dotted role alternates 1 on / 1 off
        for (x in 0 until 9) assertTrue(Role.DOTTED.tile.isInkAt(x, 3) != Role.DOTTED.tile.isInkAt(x + 1, 3))
        for (y in 0 until 9) assertTrue(Role.DOTTED.tile.isInkAt(5, y) != Role.DOTTED.tile.isInkAt(5, y + 1))
    }

    @Test fun namedTileShapesAndDensities() {
        fun check(t: Tile, w: Int, h: Int, ink: Int) {
            assertEquals("${t.name} w", w, t.width); assertEquals("${t.name} h", h, t.height)
            assertEquals("${t.name} ink", ink, t.inkCount)
        }
        check(Patterns.CHECKER, 2, 2, 2)
        check(Patterns.SPARSE, 4, 8, 2)
        check(Patterns.DOTS, 4, 4, 4)
        check(Patterns.HLINES, 1, 2, 1)
        check(Patterns.VLINES, 2, 1, 1)
        check(Patterns.DIAG, 4, 4, 4)
    }

    @Test fun registryIsDenseAndUnique() {
        Patterns.all.forEachIndexed { i, t -> assertEquals(i, t.id) }
        assertEquals(Patterns.all.size, Patterns.all.map { it.name }.toSet().size)
        assertEquals(Patterns.D.size + Patterns.named.size, Patterns.all.size)
        for (t in Patterns.all) assertNotNull(Patterns.byName(t.name))
        // tiles small enough for cheap shader bitmaps and 8 divides every period (seamless at 8)
        for (t in Patterns.all) assertTrue(8 % t.width == 0 && 8 % t.height == 0)
    }

    @Test fun globalPhaseHandlesNegativeCoordinates() {
        val t = Patterns.DIAG
        for (gy in -9..9) for (gx in -9..9) assertEquals(t.isInkAt(gx + 4, gy + 8), t.isInkAt(gx, gy))
    }

    @Test fun rolesResolve() {
        assertEquals(Patterns.D[0], Role.SURFACE.tile)
        assertEquals(Patterns.D[16], Role.INK.tile)
        assertEquals(Patterns.SPARSE, Role.DRAWER.tile)
        assertFalse(Role.DRAWER.tile.density > Role.TAB.tile.density)
        assertTrue(Patterns.playerBackgrounds.all { it.density <= 0.5 })
    }

    @Test fun packRejectsBadInput() {
        try { PackedImage.pack(arrayOf("#.", "#")); throw AssertionError("ragged") } catch (_: IllegalArgumentException) {}
        try { PackedImage.pack(arrayOf("#x")); throw AssertionError("bad char") } catch (_: IllegalArgumentException) {}
    }
}
