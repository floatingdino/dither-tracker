package au.com.josephmark.dithertracker.core

import au.com.josephmark.dithertracker.core.Action.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommanderDamageTest {
    private val s0 = GameState.create(4)
    private fun dmg(target: Int, source: Int, delta: Int, slot: Int = Commander.PRIMARY, link: Boolean? = null) =
        ChangeCommanderDamage(target, source, slot, delta, link)

    @Test fun damageLinksToLifeByDefault() {
        val s = reduce(s0, dmg(0, 1, 7))
        assertEquals(7, s.players[0].commanderDamageFrom(1, Commander.PRIMARY))
        assertEquals(33, s.players[0].life)
        assertSame(s0.players[1], s.players[1], "damage is stored on the receiver only")
    }

    @Test fun settingOffLeavesLifeAlone() {
        val s = reduce(reduce(s0, UpdateSettings(GameSettings(commanderDamageAffectsLife = false))), dmg(0, 1, 7))
        assertEquals(7, s.players[0].commanderDamageFrom(1, 0))
        assertEquals(40, s.players[0].life)
    }

    @Test fun perActionOverrideBeatsSetting() {
        assertEquals(40, reduce(s0, dmg(0, 1, 5, link = false)).players[0].life)
        val off = reduce(s0, UpdateSettings(GameSettings(commanderDamageAffectsLife = false)))
        assertEquals(35, reduce(off, dmg(0, 1, 5, link = true)).players[0].life)
    }

    @Test fun decrementRefundsLinkedLife() {
        var s = reduce(s0, dmg(0, 1, 5))
        s = reduce(s, dmg(0, 1, -2))
        assertEquals(3, s.players[0].commanderDamageFrom(1, 0))
        assertEquals(37, s.players[0].life)
        s = reduce(s, dmg(0, 1, -3))
        assertEquals(40, s.players[0].life)
        assertTrue(s.players[0].commanderDamage.isEmpty(), "zeroed entries are removed")
        assertEquals(s0, s, "full reversal is structurally identical to the start")
    }

    @Test fun decrementBelowZeroOnlyRefundsWhatWasApplied() {
        var s = reduce(s0, dmg(0, 1, 3))
        s = reduce(s, dmg(0, 1, -10))
        assertEquals(0, s.players[0].commanderDamageFrom(1, 0))
        assertEquals(40, s.players[0].life)
        assertSame(s, reduce(s, dmg(0, 1, -1)), "decrementing zero damage is a no-op")
    }

    @Test fun refundUsesRecordedLinkNotCurrentSetting() {
        var s = reduce(s0, dmg(0, 1, 4))
        s = reduce(s, UpdateSettings(GameSettings(commanderDamageAffectsLife = false)))
        s = reduce(s, dmg(0, 1, -4))
        assertEquals(40, s.players[0].life, "damage taken while linked is refunded even after the setting flips")
    }

    @Test fun unlinkedDamageIsNeverRefunded() {
        var s = reduce(s0, dmg(0, 1, 4, link = false))
        s = reduce(s, dmg(0, 1, -4))
        assertEquals(40, s.players[0].life)
    }

    @Test fun mixedLinkedAndUnlinkedRefundsAtMostLinked() {
        var s = reduce(s0, dmg(0, 1, 5, link = false)) // 5 dmg, life 40
        s = reduce(s, dmg(0, 1, 3)) // 8 dmg, life 37, linked 3
        s = reduce(s, dmg(0, 1, -2)) // refund 2
        assertEquals(DamageSlot(6, 1), s.players[0].commanderDamage[1]!!.primary)
        assertEquals(39, s.players[0].life)
        s = reduce(s, dmg(0, 1, -6))
        assertEquals(40, s.players[0].life)
    }

    @Test fun explicitNoLinkDecrementKeepsLinkedWithinDamage() {
        var s = reduce(s0, dmg(0, 1, 5)) // linked 5, life 35
        s = reduce(s, dmg(0, 1, -3, link = false)) // dmg 2, life 35, linked capped to 2
        assertEquals(DamageSlot(2, 2), s.players[0].commanderDamage[1]!!.primary)
        assertEquals(35, s.players[0].life)
        s = reduce(s, dmg(0, 1, -2))
        assertEquals(37, s.players[0].life)
    }

    @Test fun selfDamageAndUnknownPlayersAreNoOps() {
        assertSame(s0, reduce(s0, dmg(0, 0, 5)))
        assertSame(s0, reduce(s0, dmg(0, 9, 5)))
        assertSame(s0, reduce(s0, dmg(9, 0, 5)))
        assertSame(s0, reduce(s0, dmg(0, 1, 0)))
    }

    @Test fun partnerSlotRequiresSourcePartner() {
        assertSame(s0, reduce(s0, dmg(0, 1, 5, slot = Commander.PARTNER)))
        var s = reduce(s0, SetPartner(1, true))
        s = reduce(s, dmg(0, 1, 5, slot = Commander.PARTNER))
        s = reduce(s, dmg(0, 1, 2, slot = Commander.PRIMARY))
        assertEquals(5, s.players[0].commanderDamageFrom(1, Commander.PARTNER))
        assertEquals(2, s.players[0].commanderDamageFrom(1, Commander.PRIMARY))
        assertEquals(33, s.players[0].life)
        assertEquals(5, s.players[0].maxCommanderDamage)
    }

    @Test fun partnerOffClearsPartnerDamageAndTax() {
        var s = reduce(s0, SetPartner(1, true))
        s = reduce(s, dmg(0, 1, 5, slot = Commander.PARTNER))
        s = reduce(s, dmg(2, 1, 3, slot = Commander.PARTNER))
        s = reduce(s, dmg(2, 1, 1, slot = Commander.PRIMARY))
        s = reduce(s, ChangeCommanderTax(1, Commander.PARTNER, 2))
        val before = s
        s = reduce(s, SetPartner(1, false))
        assertFalse(s.players[1].partner)
        assertEquals(0, s.players[1].commanderTax.partner)
        assertTrue(s.players[0].commanderDamage.isEmpty())
        assertEquals(1, s.players[2].commanderDamageFrom(1, Commander.PRIMARY))
        assertEquals(0, s.players[2].commanderDamageFrom(1, Commander.PARTNER))
        assertEquals(before.players[0].life, s.players[0].life, "no life refund on partner removal")
        assertSame(before.players[3], s.players[3])
    }

    @Test fun damageClampsAtMax() {
        val s = reduce(s0, dmg(0, 1, 5000, link = false))
        assertEquals(Limits.COUNTER_MAX, s.players[0].commanderDamageFrom(1, 0))
    }
}
