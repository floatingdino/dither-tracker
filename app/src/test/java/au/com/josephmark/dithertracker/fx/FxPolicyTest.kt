package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.fx.FxPolicy.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FxPolicyTest {

    private val pow = Fx.byName("pow")!!
    private val twinkle = Fx.byName("twinkle")!!

    @Test fun defaultsDamagePowHealTwinkleZeroNothing() {
        val p = FxPolicy()
        assertEquals(pow, p.choose(-1))
        assertEquals(pow, p.choose(-7))
        assertEquals(twinkle, p.choose(1))
        assertEquals(twinkle, p.choose(25))
        assertNull(p.choose(0))
    }

    @Test fun bigHitAndKoFallBackToDamageUntilMapped() {
        val p = FxPolicy()
        assertEquals(Trigger.BIG_HIT, p.trigger(-10))
        assertEquals(Trigger.DAMAGE, p.trigger(-9))
        assertEquals(Trigger.KO, p.trigger(-3, ko = true))
        assertEquals(Trigger.HEAL, p.trigger(12))
        assertNull(p.trigger(0, ko = true))
        assertEquals(pow, p.choose(-15))
        assertEquals(pow, p.choose(-2, ko = true))
    }

    @Test fun mappingMoreEffectsIsATableChange() {
        val p = FxPolicy()
        val wham = Fx.byName("wham") ?: return // optional sketch; skip if removed
        p[Trigger.BIG_HIT] = wham
        assertEquals(wham, p.choose(-10))
        assertEquals(pow, p.choose(-9))
        assertEquals(wham, p.choose(-3, ko = true)) // KO → BIG_HIT fallback
        val fizzle = Fx.byName("fizzle") ?: return
        p[Trigger.KO] = fizzle
        assertEquals(fizzle, p.choose(-3, ko = true))
        p[Trigger.HEAL] = null
        assertNull(p.choose(4))
        p.bigHitThreshold = 20
        assertEquals(pow, p.choose(-15))
    }
}
