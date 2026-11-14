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

    /** T55: the owner's round-three approvals, authored by design/fx/round3/author3.py into design/fx/. */
    private val round3 = mapOf(
        "chomp" to FxRole.DAMAGE, "slash" to FxRole.DAMAGE, "stomp" to FxRole.BIG_HIT, "crit" to FxRole.BIG_HIT,
        "chime" to FxRole.HEAL, "heart-burst" to FxRole.HEAL,
        "ooze" to FxRole.POISON, "brand" to FxRole.POISON, "imp-raspberry" to FxRole.COMMANDER, "imp-poke" to FxRole.COMMANDER,
        "charge" to FxRole.ENERGY, "level-up" to FxRole.EXPERIENCE, "rad-glow" to FxRole.RAD, "storm-copy" to FxRole.STORM,
        "crown-drop" to FxRole.MONARCH, "torch" to FxRole.INITIATIVE,
        // The owner's art fixes (T55): full-tier frames plus a narrow-tier twin each (FxPolicy.variant).
        "crown-drop-narrow" to FxRole.MONARCH, "torch-narrow" to FxRole.INITIATIVE, "dusk" to FxRole.NIGHT, "dawn" to FxRole.DAY,
        "ring-tempt" to FxRole.RING, "your-turn" to FxRole.TURN, "spotlight" to FxRole.TURN,
        "start-flag" to FxRole.FIRST_PLAYER, "revive" to FxRole.REVIVE,
    )

    /** Not approved (still candidates in design/fx/round3/) or dropped on principle (coin and die: who acted?). */
    private val notShipped = listOf("bonk", "nibble", "refill", "bunting", "crown-cut", "coin-spin", "fumble")

    private val roles = mapOf(
        "pow" to FxRole.DAMAGE, "crack" to FxRole.DAMAGE, "claw-cut" to FxRole.DAMAGE, "quake" to FxRole.DAMAGE,
        "fault" to FxRole.DAMAGE, "focus" to FxRole.BIG_HIT, "twinkle" to FxRole.HEAL, "rise" to FxRole.HEAL,
        "sunrise" to FxRole.HEAL, "vines" to FxRole.HEAL, "heart" to FxRole.HEAL, "sprout" to FxRole.HEAL,
        "ko-shatter" to FxRole.KO, "fizzle" to FxRole.KO, "winner" to FxRole.WIN,
    )

    @Test fun shippedSetIsExactlyTheApprovedOne() {
        val all = (promoted.keys + round3.keys).sorted()
        assertEquals(all, Fx.entries.map { it.id }.sorted())
        assertEquals(all, FxSource.dirs(FxSource.shippedDir).map { it.name })
        for (n in notShipped) {
            assertNull(n, Fx.byName(n))
            assertTrue("$n stays a candidate", File(FxSource.shippedDir, "round3/$n/meta.txt").isFile)
        }
        for (retired in listOf("claw", "wham", "zap", "pow2", "crack2", "twinkle2", "claw-paper", "claw-swipe")) {
            assertNull(retired, Fx.byName(retired))
        }
    }

    @Test fun theArtFixesHold() {
        // crown-drop: frame 1's crown on the vertical line it lands on in frame 2 (the owner's note).
        for (name in listOf("crown-drop", "crown-drop-narrow")) {
            val f = Fx.byName(name)!!.spec
            fun centreX(fr: Int): Double {
                val l = f.ink(fr); var sum = 0.0; var n = 0
                // The crown's columns only (the landing's bounce ticks and glint lie further out).
                val c = f.width / 2
                for (y in 0 until l.height) for (x in c - 20..c + 20) if (l.isInk(x, y) && y < 72) { sum += x; n++ }
                return sum / n
            }
            assertEquals("$name crown column, frame 1 vs 2", centreX(1), centreX(0), 1.0)
        }
        // torch: the firelight disc (tile 2, D1) is whole — nothing of it touches the frame's edge.
        for (name in listOf("torch", "torch-narrow")) {
            val f = Fx.byName(name)!!.spec
            val i = f.tiles.indexOfFirst { it.digit == 2 }
            val l = f.tileLayer(i, 1)
            assertTrue("$name has its light", l.inkCount > 0)
            for (y in 0 until l.height) assertTrue("$name light at the left edge, row $y", !l.isInk(0, y))
            for (x in 0 until l.width) assertTrue("$name light at the top edge", !l.isInk(x, 0))
        }
        // The full torch reaches past the narrow tile's half-width (77) to sit behind the − glyph.
        val t = Fx.byName("torch")!!.spec
        assertEquals(173, t.width)
        assertEquals(-12, t.anchorDx)
    }

    @Test fun everyShippedMetaDeclaresItsRole() {
        for (src in FxSource.dirs(FxSource.shippedDir)) {
            val role = src.meta["role"]
            assertTrue("${src.name}: explicit role line", role != null && role.size == 1)
            assertEquals(src.name, roles[src.name] ?: round3[src.name], Fx.byName(src.name)!!.role)
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
            // meta: same lines except the title comment (renamed), the added role line and the holds
            // (T65 re-timed the shipped effects; the archive keeps the timings they were designed with).
            fun body(f: File) = f.readLines().drop(1).filterNot { it.startsWith("role ") || it.startsWith("hold_ms ") }
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
