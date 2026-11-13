package au.com.josephmark.dithertracker.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generated [Fx] enum against the source ASCII in design/fx/, read here with an independent
 * (deliberately naive) parser ([FxSource]): every effect directory with a meta.txt is present, with
 * the same size, holds, anchor and every pixel of every frame in the right layer.
 */
class FxGeneratedTest {

    private val sources = FxSource.dirs(FxSource.shippedDir)

    @Test fun everyEffectDirectoryIsGenerated() {
        val names = sources.map { it.name }
        assertTrue("expected pow and twinkle in $names", "pow" in names && "twinkle" in names)
        assertEquals(names, Fx.entries.map { it.id }.sorted())
        for (n in names) assertNotNull(n, Fx.byName(n))
        assertEquals(Fx.byName("pow"), Fx.byName("POW"))
        assertEquals(null, Fx.byName("options"))
    }

    @Test fun metadataAndEveryPixelOfEveryFrameMatchSource() {
        for (src in sources) {
            val fx = Fx.byName(src.name)!!
            src.assertMatches(fx.spec)
            // The enum's own accessors are the spec's.
            assertEquals(fx.spec.width, fx.width)
            assertEquals(fx.spec.anchorRef, fx.anchorRef)
            for (f in 0 until fx.frameCount) {
                assertTrue(fx.ink(f) === fx.spec.ink(f))
                assertTrue(fx.knockout(f) === fx.spec.knockout(f))
                assertTrue(fx.cut(f) === fx.spec.cut(f))
            }
        }
        assertEquals(FxRole.DAMAGE, Fx.byName("pow")!!.role)
        assertEquals(FxRole.HEAL, Fx.byName("twinkle")!!.role)
    }

    @Test fun layersAreStableSingletons() {
        val pow = Fx.byName("pow")!!
        assertTrue(pow.ink(0) === pow.ink(0))
        assertTrue(pow.ink(0) !== pow.knockout(0))
        assertTrue(pow.ink(0).rotated(180) === pow.ink(0).rotated(180))
    }
}
