package au.com.josephmark.dithertracker.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The generated [Fx] enum against the source ASCII in design/fx/, read here with an independent
 * (deliberately naive) parser: every effect directory with a meta.txt is present, with the same size,
 * holds, anchor and every pixel of every frame in the right layer.
 */
class FxGeneratedTest {

    private val fxDir = File(System.getProperty("dither.fxDir") ?: "../design/fx")

    private fun effectDirs(): List<File> =
        fxDir.listFiles()!!.filter { it.isDirectory && File(it, "meta.txt").isFile }.sortedBy { it.name }

    private fun meta(dir: File): Map<String, List<String>> =
        File(dir, "meta.txt").readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .associate { l -> l.split(Regex("\\s+")).let { it[0] to it.drop(1) } }

    @Test fun everyEffectDirectoryIsGenerated() {
        val names = effectDirs().map { it.name }
        assertTrue("expected pow and twinkle in $names", "pow" in names && "twinkle" in names)
        assertEquals(names, Fx.entries.map { it.id }.sorted())
        for (n in names) assertNotNull(n, Fx.byName(n))
        assertEquals(Fx.byName("pow"), Fx.byName("POW"))
        assertEquals(null, Fx.byName("options"))
    }

    @Test fun metadataMatchesSource() {
        for (dir in effectDirs()) {
            val fx = Fx.byName(dir.name)!!
            val m = meta(dir)
            assertEquals(dir.name, m["size"]!!.map(String::toInt), listOf(fx.width, fx.height))
            assertEquals(dir.name, m["frames"]!![0].toInt(), fx.frameCount)
            assertEquals(dir.name, m["hold_ms"]!!.map(String::toInt), (0 until fx.frameCount).map(fx::holdMs))
            val a = m["anchor"]!!
            assertEquals(dir.name, a[0], fx.anchorRef.id)
            assertEquals(dir.name, a[1].toInt(), fx.anchorDx)
            assertEquals(dir.name, a[2].toInt(), fx.anchorDy)
            m["role"]?.let { assertEquals(dir.name, it[0], fx.role.id) }
        }
        assertEquals(FxRole.DAMAGE, Fx.byName("pow")!!.role)
        assertEquals(FxRole.HEAL, Fx.byName("twinkle")!!.role)
    }

    @Test fun everyPixelOfEveryFrameMatchesSource() {
        for (dir in effectDirs()) {
            val fx = Fx.byName(dir.name)!!
            for (f in 0 until fx.frameCount) {
                val rows = File(dir, "frame-${f + 1}.txt").readLines().filter { it.isNotEmpty() }
                assertEquals("${dir.name} f${f + 1} rows", fx.height, rows.size)
                val ink = fx.ink(f)
                val ko = fx.knockout(f)
                assertEquals(fx.width, ink.width); assertEquals(fx.height, ink.height)
                var inkN = 0; var koN = 0
                for (y in 0 until fx.height) {
                    assertEquals("${dir.name} f${f + 1} row ${y + 1}", fx.width, rows[y].length)
                    for (x in 0 until fx.width) {
                        val c = rows[y][x]
                        val at = "${dir.name} frame-${f + 1} ($x,$y) '$c'"
                        assertEquals(at, c == '#', ink.isInk(x, y))
                        assertEquals(at, c == 'o', ko.isInk(x, y))
                        if (c == '#') inkN++
                        if (c == 'o') koN++
                    }
                }
                assertEquals(inkN, ink.inkCount)
                assertEquals(koN, ko.inkCount)
            }
            val last = fx.frameCount - 1
            assertTrue("${dir.name}: last frame empty", fx.ink(last).isEmpty && fx.knockout(last).isEmpty)
            assertEquals(0, fx.holdMs(last))
        }
    }

    @Test fun layersAreStableSingletons() {
        val pow = Fx.byName("pow")!!
        assertTrue(pow.ink(0) === pow.ink(0))
        assertTrue(pow.ink(0) !== pow.knockout(0))
    }
}
