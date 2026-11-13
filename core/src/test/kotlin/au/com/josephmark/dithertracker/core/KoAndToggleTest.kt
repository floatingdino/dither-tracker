package au.com.josephmark.dithertracker.core

import au.com.josephmark.dithertracker.core.Action.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KoAndToggleTest {
    private val s0 = GameState.create(4)
    private fun reason(s: GameState, id: Int) = s.koReason(s.player(id)!!)

    @Test fun lifeAtOrBelowZeroIsKo() {
        assertNull(reason(reduce(s0, SetLife(0, 1)), 0))
        assertEquals(KoReason.LIFE, reason(reduce(s0, SetLife(0, 0)), 0))
        assertEquals(KoReason.LIFE, reason(reduce(s0, SetLife(0, -3)), 0))
    }

    @Test fun tenPoisonIsKo() {
        assertNull(reason(reduce(s0, SetCounter(0, Counter.POISON, 9)), 0))
        assertEquals(KoReason.POISON, reason(reduce(s0, SetCounter(0, Counter.POISON, 10)), 0))
    }

    @Test fun twentyOneFromASingleCommanderIsKo() {
        var s = reduce(s0, ChangeCommanderDamage(0, 1, 0, 20, linkLife = false))
        s = reduce(s, ChangeCommanderDamage(0, 2, 0, 20, linkLife = false))
        assertNull(reason(s, 0), "damage from different commanders does not add up")
        s = reduce(s, ChangeCommanderDamage(0, 2, 0, 1, linkLife = false))
        assertEquals(KoReason.COMMANDER_DAMAGE, reason(s, 0))
    }

    @Test fun partnersCountSeparately() {
        var s = reduce(s0, SetPartner(1, true))
        s = reduce(s, ChangeCommanderDamage(0, 1, Commander.PRIMARY, 11, linkLife = false))
        s = reduce(s, ChangeCommanderDamage(0, 1, Commander.PARTNER, 11, linkLife = false))
        assertNull(reason(s, 0))
    }

    @Test fun autoKoOffDisablesDerivedKo() {
        val s = reduce(reduce(s0, UpdateSettings(GameSettings(autoKo = false))), SetLife(0, 0))
        assertFalse(s.isKo(0))
        assertEquals(KoReason.MANUAL, reason(reduce(s, MarkDead(0)), 0), "manual still works with auto-KO off")
    }

    @Test fun markDeadAndReviveFromManual() {
        var s = reduce(s0, MarkDead(2))
        assertEquals(KoReason.MANUAL, reason(s, 2))
        s = reduce(s, Revive(2))
        assertFalse(s.isKo(2))
        assertEquals(KoOverride.NONE, s.players[2].koOverride)
    }

    @Test fun reviveOverridesAutoKoWhileStillLethal() {
        var s = reduce(s0, SetLife(0, 0))
        s = reduce(s, Revive(0))
        assertEquals(KoOverride.ALIVE, s.players[0].koOverride)
        assertFalse(s.isKo(0))
        s = reduce(s, ChangeLife(0, -2))
        assertFalse(s.isKo(0), "still lethal → override stays")
        s = reduce(s, ChangeCounter(0, Counter.ENERGY, 1))
        assertFalse(s.isKo(0))
    }

    @Test fun reviveOverrideClearsOnRecoverySoNextLethalChangeKos() {
        var s = reduce(s0, SetLife(0, 0))
        s = reduce(s, Revive(0))
        s = reduce(s, ChangeLife(0, 3))
        assertEquals(KoOverride.NONE, s.players[0].koOverride)
        s = reduce(s, ChangeLife(0, -3))
        assertTrue(s.isKo(0))
    }

    @Test fun reviveIsANoOpForTheLiving() {
        assertSame(s0, reduce(s0, Revive(1)))
    }

    @Test fun overrideDoesNotTouchOtherPlayers() {
        var s = reduce(s0, SetLife(0, 0))
        s = reduce(s, Revive(0))
        val t = reduce(s, ChangeLife(1, -1))
        assertSame(s.players[0], t.players[0])
    }

    @Test fun monarchAndInitiativeAreExclusive() {
        var s = reduce(s0, SetToggle(0, Toggle.MONARCH, true))
        s = reduce(s, SetToggle(1, Toggle.INITIATIVE, true))
        val t = reduce(s, SetToggle(2, Toggle.MONARCH, true))
        assertFalse(t.players[0].toggles.monarch)
        assertTrue(t.players[2].toggles.monarch)
        assertSame(s.players[1], t.players[1], "initiative holder untouched")
        assertSame(s.players[3], t.players[3])
        val u = reduce(t, SetToggle(3, Toggle.INITIATIVE, true))
        assertEquals(listOf(false, false, false, true), u.players.map { it.toggles.initiative })
        assertEquals(listOf(false, false, true, false), u.players.map { it.toggles.monarch })
    }

    @Test fun cityAndRingAreNotExclusive() {
        var s = s0
        for (id in 0..3) {
            s = reduce(s, SetToggle(id, Toggle.CITYS_BLESSING, true))
            s = reduce(s, SetToggle(id, Toggle.RING_BEARER, true))
        }
        assertTrue(s.players.all { it.toggles.citysBlessing && it.toggles.ringBearer })
        s = reduce(s, SetToggle(1, Toggle.RING_BEARER, false))
        assertEquals(listOf(true, false, true, true), s.players.map { it.toggles.ringBearer })
    }
}
