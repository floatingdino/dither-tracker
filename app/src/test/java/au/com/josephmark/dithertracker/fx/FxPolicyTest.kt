package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.Toggle
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

    @Test fun triggersBySignSizeBandKoAndCommander() {
        val p = FxPolicy(Random(1))
        assertNull(p.trigger(0))
        assertNull(p.trigger(0, ko = true))
        // T55 bands: small 1–3, big 4–9, huge 10+, and for damage crit from 20.
        for (n in 1..3) { assertEquals(Trigger.DAMAGE, p.trigger(-n)); assertEquals(Trigger.HEAL, p.trigger(n)) }
        for (n in 4..9) { assertEquals(Trigger.BIG_HIT, p.trigger(-n)); assertEquals(Trigger.BIG_HEAL, p.trigger(n)) }
        for (n in 10..19) { assertEquals(Trigger.HUGE_HIT, p.trigger(-n)); assertEquals(Trigger.HUGE_HEAL, p.trigger(n)) }
        assertEquals(Trigger.CRIT, p.trigger(-20))
        assertEquals(Trigger.CRIT, p.trigger(-99))
        assertEquals("heals have no crit band", Trigger.HUGE_HEAL, p.trigger(40))
        assertEquals(Trigger.KO, p.trigger(-3, ko = true))
        assertEquals(Trigger.KO, p.trigger(-30, ko = true, commander = true))
        assertEquals(Trigger.COMMANDER, p.trigger(-3, commander = true))
        assertEquals(Trigger.COMMANDER, p.trigger(-12, commander = true))
        assertEquals("a commander group that nets a gain is a heal", Trigger.HEAL, p.trigger(2, commander = true))
        p.hugeFrom = 15
        assertEquals(Trigger.BIG_HIT, p.trigger(-12))
    }

    @Test fun poolsAreTheApprovedTable() {
        val p = FxPolicy(Random(1))
        fun pool(t: Trigger, compact: Boolean = false) = (if (compact) p.compact else p.full)[t.ordinal].toSet()
        assertEquals(names("pow", "crack", "slash", "quake"), pool(Trigger.DAMAGE))
        assertEquals(names("claw-cut", "quake", "pow", "crack", "slash", "chomp"), pool(Trigger.BIG_HIT))
        assertEquals(names("focus", "stomp"), pool(Trigger.HUGE_HIT))
        assertEquals(names("crit"), pool(Trigger.CRIT))
        assertEquals(names("twinkle", "sprout", "chime", "heart"), pool(Trigger.HEAL))
        assertEquals(names("rise", "heart", "twinkle", "chime"), pool(Trigger.BIG_HEAL))
        assertEquals(names("sunrise", "vines", "heart-burst"), pool(Trigger.HUGE_HEAL))
        assertEquals(names("ko-shatter"), pool(Trigger.KO))
        assertEquals(names("winner"), pool(Trigger.WIN))
        assertEquals(names("imp-raspberry", "imp-poke"), pool(Trigger.COMMANDER))
        assertEquals(names("ooze", "brand"), pool(Trigger.POISON))
        assertEquals(names("charge"), pool(Trigger.ENERGY))
        assertEquals(names("level-up"), pool(Trigger.EXPERIENCE))
        assertEquals(names("rad-glow"), pool(Trigger.RAD))
        assertEquals(names("storm-copy"), pool(Trigger.STORM))
        assertEquals(names("crown-drop"), pool(Trigger.MONARCH))
        assertEquals(names("torch"), pool(Trigger.INITIATIVE))
        assertEquals(names("ring-tempt"), pool(Trigger.RING))
        assertEquals(names("dusk"), pool(Trigger.NIGHT))
        assertEquals(names("dawn"), pool(Trigger.DAY))
        assertEquals(names("your-turn", "spotlight"), pool(Trigger.TURN))
        assertEquals(names("start-flag"), pool(Trigger.FIRST_PLAYER))
        assertEquals(names("revive"), pool(Trigger.REVIVE))
        for (t in Trigger.entries) assertTrue("$t has a pool", pool(t).isNotEmpty() && pool(t, compact = true).isNotEmpty())
        // Compact seats (drawer open): number-anchored effects only.
        for (t in Trigger.entries) for (f in pool(t, compact = true)) assertTrue("$t ${f.id}", !f.spec.isTileScale)
        assertEquals(names("fizzle"), pool(Trigger.KO, compact = true))
        assertEquals(p.effects().toSet(), (p.full.flatten() + p.compact.flatten() + p.full.flatten().map(p::variant)).toSet())
        assertTrue("fault is promoted but not in rotation", fx("fault") !in p.effects())
        // Every weight is positive and matches its pool.
        for (t in Trigger.entries) {
            assertEquals(t.name, p.full[t.ordinal].size, p.fullWeights[t.ordinal].size)
            assertEquals(t.name, p.compact[t.ordinal].size, p.compactWeights[t.ordinal].size)
            assertTrue(t.name, p.fullWeights[t.ordinal].all { it > 0 } && p.compactWeights[t.ordinal].all { it > 0 })
        }
        // Every round-three effect except the narrow twins is in a pool; the dropped ones are not shipped.
        for (f in Fx.entries) if (!f.id.endsWith(FxPolicy.NARROW_SUFFIX) && f.id != "fault") assertTrue(f.id, f in p.effects())
    }

    @Test fun eachTriggerFromItsSource() {
        assertEquals(Trigger.POISON, FxPolicy.counterTrigger(Counter.POISON))
        assertEquals(Trigger.ENERGY, FxPolicy.counterTrigger(Counter.ENERGY))
        assertEquals(Trigger.EXPERIENCE, FxPolicy.counterTrigger(Counter.EXPERIENCE))
        assertEquals(Trigger.RAD, FxPolicy.counterTrigger(Counter.RAD))
        assertEquals(Trigger.STORM, FxPolicy.counterTrigger(Counter.STORM))
        assertNull(FxPolicy.counterTrigger(Counter.TICKETS))
        assertNull(FxPolicy.counterTrigger(Counter.GENERIC))
        assertEquals(Trigger.MONARCH, FxPolicy.toggleTrigger(Toggle.MONARCH))
        assertEquals(Trigger.INITIATIVE, FxPolicy.toggleTrigger(Toggle.INITIATIVE))
        assertEquals(Trigger.RING, FxPolicy.toggleTrigger(Toggle.RING_BEARER))
        assertNull("bunting is not approved", FxPolicy.toggleTrigger(Toggle.CITYS_BLESSING))
        assertTrue(Trigger.NIGHT.allSeats && Trigger.DAY.allSeats)
        for (t in Trigger.entries) if (t != Trigger.NIGHT && t != Trigger.DAY) assertTrue(t.name, !t.allSeats)
    }

    @Test fun damageRotatesAndNeverRepeatsTheSeatsPreviousPick() {
        val p = FxPolicy(Random(42))
        for (seat in 0 until 4) {
            var prev: Fx? = null
            val seen = HashSet<Fx>()
            repeat(300) {
                val f = p.choose(seat, -3)!!
                assertNotEquals("seat $seat repeated ${f.id}", prev, f)
                assertEquals(FxRole.DAMAGE, f.role)
                prev = f
                seen += f
            }
            assertEquals(names("pow", "crack", "slash", "quake"), seen) // every one gets played
        }
    }

    @Test fun weightsMakeTheExpensiveOnesRare() {
        val p = FxPolicy(Random(5))
        val counts = HashMap<Fx, Int>()
        // Seat −1: no memory, so the counts follow the weights alone.
        repeat(9_000) { counts.merge(p.choose(-1, Trigger.BIG_HIT)!!, 1, Int::plus) }
        val n = counts.values.sum().toDouble()
        assertEquals("chomp 1 of 9", 1 / 9.0, counts[fx("chomp")]!! / n, 0.02)
        assertEquals("claw-cut 3 of 9", 3 / 9.0, counts[fx("claw-cut")]!! / n, 0.02)
        counts.clear()
        repeat(8_000) { counts.merge(p.choose(-1, Trigger.TURN)!!, 1, Int::plus) }
        assertEquals("spotlight 1 of 4 turns", 0.25, counts[fx("spotlight")]!! / 8_000.0, 0.02)
        // With memory, a seat never repeats: your-turn and spotlight alternate whenever spotlight comes up.
        var prev: Fx? = null
        repeat(200) { val f = p.choose(1, Trigger.TURN)!!; assertNotEquals(prev, f); prev = f }
    }

    @Test fun seatsRotateIndependently() {
        val p = FxPolicy(Random(7))
        val a = p.choose(0, -2)!!
        repeat(50) { p.choose(1, -2) }
        assertNotEquals(a, p.choose(0, -2))
    }

    @Test fun bandPicks() {
        val p = FxPolicy(Random(3))
        repeat(50) {
            assertTrue(p.choose(0, 2)!! in names("twinkle", "sprout", "chime", "heart"))
            assertTrue(p.choose(0, 6)!! in names("rise", "heart", "twinkle", "chime"))
            assertTrue(p.choose(1, -12)!! in names("focus", "stomp"))
            assertEquals(fx("crit"), p.choose(1, -21))
            assertTrue(p.choose(2, 15)!! in names("sunrise", "vines", "heart-burst"))
            assertEquals(fx("ko-shatter"), p.choose(3, -5, ko = true))
        }
        assertNull(p.choose(0, 0))
        // A single-effect pool repeats (nothing else to pick).
        assertEquals(fx("crit"), p.choose(1, -25))
        // Compact.
        assertEquals(fx("fizzle"), p.choose(0, -5, ko = true, compact = true))
        repeat(20) { assertTrue(!p.choose(0, -15, compact = true)!!.spec.isTileScale) }
        assertEquals(fx("winner"), p.choose(2, Trigger.WIN))
    }

    @Test fun narrowTierPlaysTheNarrowTwinWhereThereIsOne() {
        val p = FxPolicy(Random(1))
        for (f in Fx.entries) {
            val twin = Fx.byName(f.id + FxPolicy.NARROW_SUFFIX)
            assertEquals(f.id, twin ?: f, p.variant(f))
        }
        assertEquals(fx("dusk"), p.choose(0, Trigger.NIGHT, narrowTier = true))
    }

    @Test fun aGroupPlaysEachBandOnceAndAgainOnlyWhenItEscalates() {
        val p = FxPolicy(Random(1))
        fun bits(vararg t: Trigger) = t.fold(0) { m, x -> m or (1 shl x.ordinal) }
        assertTrue(p.escalates(Trigger.DAMAGE, 0))
        assertTrue(!p.escalates(Trigger.DAMAGE, bits(Trigger.DAMAGE)))
        assertTrue("small → big plays again", p.escalates(Trigger.BIG_HIT, bits(Trigger.DAMAGE)))
        assertTrue("big → huge → crit", p.escalates(Trigger.HUGE_HIT, bits(Trigger.BIG_HIT)) && p.escalates(Trigger.CRIT, bits(Trigger.HUGE_HIT)))
        assertTrue("big → small never", !p.escalates(Trigger.DAMAGE, bits(Trigger.BIG_HIT)))
        assertTrue("crit → huge never", !p.escalates(Trigger.HUGE_HIT, bits(Trigger.CRIT)))
        assertTrue("the other direction is its own band", p.escalates(Trigger.HEAL, bits(Trigger.BIG_HIT)))
        assertTrue(!p.escalates(Trigger.HEAL, bits(Trigger.BIG_HEAL)))
        assertTrue(p.escalates(Trigger.HUGE_HEAL, bits(Trigger.BIG_HEAL)))
        assertTrue("a KO always plays once", p.escalates(Trigger.KO, bits(Trigger.BIG_HIT, Trigger.DAMAGE)))
        assertTrue(!p.escalates(Trigger.KO, bits(Trigger.KO)))
        assertTrue(p.escalates(Trigger.COMMANDER, 0) && !p.escalates(Trigger.COMMANDER, bits(Trigger.COMMANDER)))
    }

    @Test fun sameSeedSamePicks() {
        val a = FxPolicy(Random(9)); val b = FxPolicy(Random(9))
        repeat(30) { assertEquals(a.choose(it % 4, -1 - it % 12), b.choose(it % 4, -1 - it % 12)) }
    }
}
