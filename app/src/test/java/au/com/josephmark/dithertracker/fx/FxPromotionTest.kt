package au.com.josephmark.dithertracker.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T28c promotion: the shipped set in design/fx/ is exactly the approved one, every promoted effect is
 * its design/fx/options/ source unchanged (frames byte for byte; meta identical apart from the title
 * comment and the added `role` line), every shipped meta declares its role, and the retired round-one
 * effects are gone.
 */
class FxPromotionTest {

    /** Shipped name → its source in design/fx/options/ (null = a round-one effect kept as is). */
    private val promoted = mapOf(
        "pow" to "pow2", "crack" to "crack2", "twinkle" to "twinkle2",
        "claw-cut" to "claw-cut", "quake" to "quake", "fault" to "fault", "focus" to "focus",
        "vines" to "vines", "rise" to "rise", "sunrise" to "sunrise", "ko-shatter" to "ko-shatter",
        "winner" to "winner",
        "heart" to null, "sprout" to null, "fizzle" to null,
    )

    private val roles = mapOf(
        "pow" to FxRole.DAMAGE, "crack" to FxRole.DAMAGE, "claw-cut" to FxRole.DAMAGE, "quake" to FxRole.DAMAGE,
        "fault" to FxRole.DAMAGE, "focus" to FxRole.BIG_HIT, "twinkle" to FxRole.HEAL, "rise" to FxRole.HEAL,
        "sunrise" to FxRole.HEAL, "vines" to FxRole.HEAL, "heart" to FxRole.HEAL, "sprout" to FxRole.HEAL,
        "ko-shatter" to FxRole.KO, "fizzle" to FxRole.KO, "winner" to FxRole.WIN,
    )

    @Test fun shippedSetIsExactlyTheApprovedOne() {
        assertEquals(promoted.keys.sorted(), Fx.entries.map { it.id }.sorted())
        assertEquals(promoted.keys.sorted(), FxSource.dirs(FxSource.shippedDir).map { it.name })
        for (retired in listOf("claw", "wham", "zap", "pow2", "crack2", "twinkle2", "claw-paper", "claw-swipe")) {
            assertNull(retired, Fx.byName(retired))
        }
    }

    @Test fun everyShippedMetaDeclaresItsRole() {
        for (src in FxSource.dirs(FxSource.shippedDir)) {
            val role = src.meta["role"]
            assertTrue("${src.name}: explicit role line", role != null && role.size == 1)
            assertEquals(src.name, roles[src.name], Fx.byName(src.name)!!.role)
        }
    }

    @Test fun promotedEffectsAreTheirOptionSourcesUnchanged() {
        for ((name, option) in promoted) {
            if (option == null) continue
            val dst = File(FxSource.shippedDir, name)
            val src = File(FxSource.optionsDir, option)
            val frames = src.listFiles()!!.filter { it.name.startsWith("frame-") }.map { it.name }.sorted()
            assertEquals(name, frames, dst.listFiles()!!.filter { it.name.startsWith("frame-") }.map { it.name }.sorted())
            for (f in frames) assertTrue("$name/$f", File(src, f).readBytes().contentEquals(File(dst, f).readBytes()))
            // meta: same lines except the title comment (renamed) and the added role line.
            fun body(f: File) = f.readLines().drop(1).filterNot { it.startsWith("role ") }
            assertEquals(name, body(File(src, "meta.txt")), body(File(dst, "meta.txt")))
            assertTrue(name, File(dst, "meta.txt").readLines()[0].startsWith("# $name:"))
            // And the generated data match (the options build is an independent generator run).
            val a = Fx.byName(name)!!.spec
            val b = FxOptions.byName(option)!!
            assertEquals(name, b.width to b.height, a.width to a.height)
            for (fr in 0 until a.frameCount) {
                assertEquals("$name f$fr ink", b.ink(fr).inkCount, a.ink(fr).inkCount)
                assertEquals("$name f$fr cut", b.cut(fr).inkCount, a.cut(fr).inkCount)
                assertEquals("$name f$fr ko", b.knockout(fr).inkCount, a.knockout(fr).inkCount)
            }
        }
    }
}
