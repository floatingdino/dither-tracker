package au.com.josephmark.dithertracker.core

import au.com.josephmark.dithertracker.core.Action.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReducerTest {
    private val s0 = GameState.create(4)

    @Test fun createDefaults() {
        assertEquals(4, s0.players.size)
        s0.players.forEachIndexed { i, p ->
            assertEquals(i, p.id)
            assertEquals(i, p.seat)
            assertEquals(40, p.life)
            assertEquals("Player ${i + 1}", p.name)
            assertEquals(Player.DEFAULT_PATTERN, p.pattern)
        }
        assertEquals(DayNight.UNSET, s0.dayNight)
        assertEquals(TurnState(), s0.turn)
    }

    @Test fun createRejectsBadCounts() {
        assertFailsWith<IllegalArgumentException> { GameState.create(1) }
        assertFailsWith<IllegalArgumentException> { GameState.create(7) }
        assertFailsWith<IllegalArgumentException> { GameSettings(startingLife = 0) }
    }

    @Test fun lifeChangeAndNegativeLife() {
        val s = reduce(s0, ChangeLife(1, -45))
        assertEquals(-5, s.players[1].life)
        assertEquals(42, reduce(s0, ChangeLife(1, 2)).players[1].life)
        assertEquals(7, reduce(s0, SetLife(1, 7)).players[1].life)
    }

    @Test fun lifeIsClampedToDisplayableRange() {
        assertEquals(Limits.LIFE_MAX, reduce(s0, ChangeLife(0, Int.MAX_VALUE)).players[0].life)
        assertEquals(Limits.LIFE_MIN, reduce(s0, ChangeLife(0, Int.MIN_VALUE)).players[0].life)
    }

    @Test fun onlyTheChangedPlayerIsANewInstance() {
        val s = reduce(s0, ChangeLife(2, -1))
        assertNotSame(s0.players[2], s.players[2])
        for (i in listOf(0, 1, 3)) assertSame(s0.players[i], s.players[i])
        assertSame(s0.settings, s.settings)
        assertSame(s0.turn, s.turn)
    }

    @Test fun noOpsReturnTheSameInstance() {
        assertSame(s0, reduce(s0, ChangeLife(99, -1)))
        assertSame(s0, reduce(s0, ChangeLife(0, 0)))
        assertSame(s0, reduce(s0, ChangeCounter(0, Counter.POISON, -1)))
        assertSame(s0, reduce(s0, SetDayNight(DayNight.UNSET)))
        assertSame(s0, reduce(s0, Rename(0, "Player 1")))
        assertSame(s0, reduce(s0, SetToggle(0, Toggle.MONARCH, false)))
        assertSame(s0, reduce(s0, UpdateSettings(GameSettings())))
        assertSame(s0, reduce(s0, NewGame))
        assertSame(s0, reduce(s0, Reset))
        assertSame(s0, reduce(s0, SetPlayerCount(4)))
        assertSame(s0, reduce(s0, SetPlayerCount(1)))
        assertSame(s0, reduce(s0, SetPlayerCount(7)))
        assertSame(s0, reduce(s0, Revive(0)))
        assertSame(s0, reduce(s0, SetPartner(0, false)))
    }

    @Test fun countersClampAtZeroAndMax() {
        var s = reduce(s0, ChangeCounter(0, Counter.ENERGY, 3))
        assertEquals(3, s.players[0].counters.energy)
        s = reduce(s, ChangeCounter(0, Counter.ENERGY, -10))
        assertEquals(0, s.players[0].counters.energy)
        s = reduce(s, SetCounter(0, Counter.STORM, 5000))
        assertEquals(Limits.COUNTER_MAX, s.players[0].counters.storm)
        s = reduce(s, SetCounter(0, Counter.STORM, -2))
        assertEquals(0, s.players[0].counters.storm)
    }

    @Test fun everyCounterIsIndependentlyAddressable() {
        var s = s0
        Counter.entries.forEachIndexed { i, c -> s = reduce(s, ChangeCounter(1, c, i + 1)) }
        Counter.entries.forEachIndexed { i, c -> assertEquals(i + 1, s.players[1].counters[c]) }
    }

    @Test fun commanderTaxPerCommander() {
        var s = reduce(s0, ChangeCommanderTax(0, Commander.PRIMARY, 2))
        assertEquals(2, s.players[0].commanderTax.primary)
        assertSame(s, reduce(s, ChangeCommanderTax(0, Commander.PARTNER, 2)), "partner slot needs partner")
        s = reduce(s, SetPartner(0, true))
        s = reduce(s, ChangeCommanderTax(0, Commander.PARTNER, 4))
        assertEquals(CommanderTax(2, 4), s.players[0].commanderTax)
        s = reduce(s, ChangeCommanderTax(0, Commander.PRIMARY, -5))
        assertEquals(0, s.players[0].commanderTax.primary)
        assertSame(s, reduce(s, ChangeCommanderTax(0, 2, 1)), "invalid slot")
    }

    @Test fun renamePatternDayNight() {
        var s = reduce(s0, Rename(2, "Sam"))
        s = reduce(s, SetPattern(2, "DIAG"))
        s = reduce(s, SetDayNight(DayNight.NIGHT))
        assertEquals("Sam", s.players[2].name)
        assertEquals("DIAG", s.players[2].pattern)
        assertEquals(DayNight.NIGHT, s.dayNight)
    }

    @Test fun startingLifeSettingAppliesOnNewGame() {
        var s = reduce(s0, UpdateSettings(GameSettings(startingLife = 20)))
        assertEquals(40, s.players[0].life)
        s = reduce(s, NewGame)
        assertTrue(s.players.all { it.life == 20 })
    }

    @Test fun newGameKeepsPlayersAndSettingsResetsValues() {
        val settings = GameSettings(startingLife = 30, autoKo = false)
        var s = reduce(GameState.create(3), UpdateSettings(settings))
        s = reduce(s, Rename(0, "A"))
        s = reduce(s, SetPattern(1, "DOTS"))
        s = reduce(s, SetPartner(2, true))
        s = reduce(s, ChangeLife(0, -10))
        s = reduce(s, ChangeCounter(1, Counter.POISON, 4))
        s = reduce(s, ChangeCommanderDamage(0, 2, Commander.PARTNER, 5))
        s = reduce(s, ChangeCommanderTax(2, Commander.PARTNER, 2))
        s = reduce(s, SetToggle(1, Toggle.MONARCH, true))
        s = reduce(s, SetDayNight(DayNight.DAY))
        s = reduce(s, NextTurn)
        s = reduce(s, MarkDead(1))

        val n = reduce(s, NewGame)
        assertEquals(settings, n.settings)
        assertEquals(listOf("A", "Player 2", "Player 3"), n.players.map { it.name })
        assertEquals("DOTS", n.players[1].pattern)
        assertTrue(n.players[2].partner)
        for (p in n.players) {
            assertEquals(30, p.life)
            assertEquals(Counters(), p.counters)
            assertEquals(CommanderTax(), p.commanderTax)
            assertTrue(p.commanderDamage.isEmpty())
            assertEquals(Toggles(), p.toggles)
            assertEquals(KoOverride.NONE, p.koOverride)
        }
        assertEquals(DayNight.UNSET, n.dayNight)
        assertEquals(TurnState(), n.turn)
    }

    @Test fun resetRestoresDefaultsButKeepsCountAndSettings() {
        val settings = GameSettings(startingLife = 25)
        var s = reduce(GameState.create(5), UpdateSettings(settings))
        s = reduce(s, Rename(0, "A"))
        s = reduce(s, SetPartner(1, true))
        s = reduce(s, ChangeLife(3, -3))
        val r = reduce(s, Reset)
        assertEquals(GameState.create(5, settings), r)
    }

    @Test fun increasingPlayerCountAddsPlayersWithoutTouchingOthers() {
        val s = reduce(GameState.create(2), ChangeLife(0, -5))
        val t = reduce(s, SetPlayerCount(4))
        assertEquals(4, t.players.size)
        assertSame(s.players[0], t.players[0])
        assertSame(s.players[1], t.players[1])
        assertEquals(listOf(2, 3), t.players.drop(2).map { it.id })
        assertEquals(listOf(2, 3), t.players.drop(2).map { it.seat })
        assertTrue(t.players.drop(2).all { it.life == 40 && it.name.startsWith("Player") })
    }

    @Test fun decreasingPlayerCountRemovesHighestSeatsAndTheirDamage() {
        var s = GameState.create(4)
        s = reduce(s, ChangeCommanderDamage(0, 3, Commander.PRIMARY, 6))
        s = reduce(s, ChangeCommanderDamage(0, 1, Commander.PRIMARY, 2))
        s = reduce(s, SetStartingPlayer(3))
        val t = reduce(s, SetPlayerCount(2))
        assertEquals(listOf(0, 1), t.players.map { it.id })
        assertEquals(setOf(1), t.players[0].commanderDamage.keys)
        assertSame(s.players[1], t.players[1])
        assertEquals(0, t.turn.activePlayer)
        assertEquals(0, t.turn.startingPlayer)
    }

    @Test fun reAddedPlayersReuseLowestFreeIds() {
        var s = reduce(GameState.create(4), SetPlayerCount(2))
        s = reduce(s, SetPlayerCount(3))
        assertEquals(listOf(0, 1, 2), s.players.map { it.id })
        assertEquals(listOf(0, 1, 2), s.players.map { it.seat })
    }

    @Test fun playersAreSeatOrdered() {
        var s = GameState.create(6)
        s = reduce(s, SetPlayerCount(3))
        s = reduce(s, SetPlayerCount(6))
        s.players.forEachIndexed { i, p -> assertEquals(i, p.seat) }
        assertNull(s.player(42))
    }
}
