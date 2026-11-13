package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.fx.FxPolicy.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FxPolicyTest {

    private fun fx(n: String) = Fx.byName(n)!!
    private fun names(vararg n: String) = n.map(::fx).toSet()

    @Test fun triggersBySignSizeAndKo() {
        val p = FxPolicy(Random(1))
        assertNull(p.trigger(0))
        assertNull(p.trigger(0, ko = true))
        assertEquals(Trigger.DAMAGE, p.trigger(-1))
        assertEquals(Trigger.DAMAGE, p.trigger(-9))
        assertEquals(Trigger.BIG_HIT, p.trigger(-10))
        assertEquals(Trigger.HEAL, p.trigger(9))
        assertEquals(Trigger.BIG_HEAL, p.trigger(10))
        assertEquals(Trigger.KO, p.trigger(-3, ko = true))
        assertEquals(Trigger.KO, p.trigger(-30, ko = true))
        p.bigHitThreshold = 20
        assertEquals(Trigger.DAMAGE, p.trigger(-15))
    }

    @Test fun poolsAreTheApprovedTable() {
        val p = FxPolicy(Random(1))
        fun pool(t: Trigger, compact: Boolean = false) = (if (compact) p.compact else p.full)[t.ordinal].toSet()
        assertEquals(names("pow", "crack", "claw-cut", "quake"), pool(Trigger.DAMAGE))
        assertEquals(names("twinkle", "rise", "sprout", "heart"), pool(Trigger.HEAL))
        assertEquals(names("focus"), pool(Trigger.BIG_HIT))
        assertEquals(names("sunrise", "vines"), pool(Trigger.BIG_HEAL))
        assertEquals(names("ko-shatter"), pool(Trigger.KO))
        assertEquals(names("winner"), pool(Trigger.WIN))
        // Compact seats (drawer open): number-anchored effects only.
        for (t in Trigger.entries) for (f in pool(t, compact = true)) assertTrue("$t ${f.id}", !f.spec.isTileScale)
        assertEquals(names("fizzle"), pool(Trigger.KO, compact = true))
        assertEquals(p.effects().toSet(), (p.full.flatten() + p.compact.flatten()).toSet())
        assertTrue("fault is promoted but not in rotation", fx("fault") !in p.effects())
    }

    @Test fun damageRotatesAndNeverRepeatsTheSeatsPreviousPick() {
        val p = FxPolicy(Random(42))
        for (seat in 0 until 4) {
            var prev: Fx? = null
            val seen = HashSet<Fx>()
            repeat(200) {
                val f = p.choose(seat, -3)!!
                assertNotEquals("seat $seat repeated ${f.id}", prev, f)
                assertEquals(FxRole.DAMAGE, f.role)
                prev = f
                seen += f
            }
            assertEquals(names("pow", "crack", "claw-cut", "quake"), seen) // every one gets played
        }
    }

    @Test fun seatsRotateIndependently() {
        val p = FxPolicy(Random(7))
        val a = p.choose(0, -2)!!
        // Seat 1's pick may equal seat 0's; seat 0's next one may not.
        repeat(50) { p.choose(1, -2) }
        assertNotEquals(a, p.choose(0, -2))
    }

    @Test fun healBigAndKoPicks() {
        val p = FxPolicy(Random(3))
        repeat(50) {
            assertTrue(p.choose(0, 4)!! in names("twinkle", "rise", "sprout", "heart"))
            assertEquals(fx("focus"), p.choose(1, -12))
            assertTrue(p.choose(2, 15)!! in names("sunrise", "vines"))
            assertEquals(fx("ko-shatter"), p.choose(3, -5, ko = true))
        }
        assertNull(p.choose(0, 0))
        // A single-effect pool repeats (nothing else to pick).
        assertEquals(fx("focus"), p.choose(1, -10))
        // Compact.
        assertEquals(fx("fizzle"), p.choose(0, -5, ko = true, compact = true))
        repeat(20) { assertTrue(!p.choose(0, -15, compact = true)!!.spec.isTileScale) }
        assertEquals(fx("winner"), p.choose(2, Trigger.WIN))
    }

    @Test fun sameSeedSamePicks() {
        val a = FxPolicy(Random(9)); val b = FxPolicy(Random(9))
        repeat(30) { assertEquals(a.choose(it % 4, -1 - it % 3), b.choose(it % 4, -1 - it % 3)) }
    }
}
