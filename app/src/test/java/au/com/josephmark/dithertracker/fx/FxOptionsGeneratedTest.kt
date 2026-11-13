package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.Patterns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The round-two options (design/fx/options/, generated test-only into [FxOptions] by
 * `:app:generateFxOptions`) against an independent parse of their source: the new `x` cut symbol,
 * `1`–`9` tile layers with their `tile` declarations and `anchor tile` all come through exactly.
 */
class FxOptionsGeneratedTest {

    private val sources = FxSource.dirs(FxSource.optionsDir)

    @Test fun everyOptionIsGenerated() {
        val names = sources.map { it.name }
        assertTrue("expected the round-two options, got $names", names.containsAll(listOf("claw-cut", "quake", "focus", "sunrise", "ko-shatter", "twinkle2", "winner")))
        assertEquals(names, FxOptions.all.map { it.id }.sorted())
    }

    @Test fun metadataAndEveryPixelOfEveryFrameMatchSource() {
        for (src in sources) src.assertMatches(FxOptions.byName(src.name)!!)
    }

    @Test fun newSymbolsAreExercised() {
        // Guards the test itself: the options really use every new feature.
        val all = FxOptions.all
        assertTrue(all.any { s -> (0 until s.frameCount).any { !s.cut(it).isEmpty } })
        assertEquals(setOf(FxTileMode.INK, FxTileMode.PAPER, FxTileMode.FILL), all.flatMap { s -> s.tiles.map { it.mode } }.toSet())
        assertTrue(all.any { it.anchorRef == FxAnchorRef.TILE })
        val focus = FxOptions.byName("focus")!!
        assertEquals(Patterns.HLINES, focus.tiles.single().tile)
        assertEquals(FxRole.BIG_HIT, focus.role)
        assertEquals(FxRole.WIN, FxOptions.byName("winner")!!.role)
        assertEquals(listOf(1, 2), FxOptions.byName("ko-shatter")!!.tiles.map { it.digit })
    }

    @Test fun layersAreDisjointSoDrawOrderCannotMatter() {
        for (s in FxOptions.all) for (f in 0 until s.frameCount) {
            val layers = listOf(s.ink(f), s.knockout(f), s.cut(f)) + s.tiles.indices.map { s.tileLayer(it, f) }
            for (y in 0 until s.height) for (x in 0 until s.width) {
                assertTrue("${s.id} f$f ($x,$y)", layers.count { it.isInk(x, y) } <= 1)
            }
        }
    }

    @Test fun tileScaleFramesFillTheTile() {
        for (s in FxOptions.all.filter { it.isTileScale }) {
            assertEquals(s.id, 233 to 175, s.width to s.height)
        }
    }
}
