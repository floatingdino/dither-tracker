package au.com.josephmark.dithertracker.core

import au.com.josephmark.dithertracker.core.Action.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TurnAndToolsTest {
    private val s0 = GameState.create(4)

    @Test fun nextTurnStartsTrackingAtFirstSeat() {
        val s = reduce(s0, NextTurn)
        assertEquals(TurnState(0, 0, 1, 1), s.turn)
    }

    @Test fun nextTurnCyclesAndCountsRounds() {
        var s = reduce(s0, SetStartingPlayer(2))
        assertEquals(TurnState(2, 2, 1, 1), s.turn)
        val seen = mutableListOf(s.turn.activePlayer)
        repeat(4) { s = reduce(s, NextTurn); seen.add(s.turn.activePlayer) }
        assertEquals(listOf<Int?>(2, 3, 0, 1, 2), seen)
        assertEquals(5, s.turn.number)
        assertEquals(2, s.turn.round)
    }

    @Test fun nextTurnSkipsKoPlayers() {
        var s = reduce(s0, SetStartingPlayer(0))
        s = reduce(s, MarkDead(1))
        s = reduce(s, MarkDead(2))
        s = reduce(s, NextTurn)
        assertEquals(3, s.turn.activePlayer)
        s = reduce(s, NextTurn)
        assertEquals(0, s.turn.activePlayer)
        assertEquals(2, s.turn.round)
    }

    @Test fun roundAdvancesWhenStartingPlayerIsSkipped() {
        var s = reduce(s0, SetStartingPlayer(0))
        s = reduce(s, SetActivePlayer(3))
        s = reduce(s, MarkDead(0))
        s = reduce(s, NextTurn)
        assertEquals(1, s.turn.activePlayer)
        assertEquals(2, s.turn.round)
    }

    @Test fun setActivePlayerCorrectsWithoutCounting() {
        var s = reduce(s0, SetStartingPlayer(0))
        s = reduce(s, SetActivePlayer(2))
        assertEquals(TurnState(2, 0, 1, 1), s.turn)
        assertSame(s, reduce(s, SetActivePlayer(2)))
        assertSame(s, reduce(s, SetActivePlayer(9)))
        assertEquals(TurnState(1, 1, 1, 1), reduce(s0, SetActivePlayer(1)).turn)
    }

    @Test fun turnChangesDoNotTouchPlayers() {
        val s = reduce(reduce(s0, NextTurn), NextTurn)
        for (i in 0..3) assertSame(s0.players[i], s.players[i])
    }

    @Test fun dieRollsStayInRangeAndCoverAllFaces() {
        val r = Random(42)
        val rolls = TableTools.rollDice(2000, 20, r)
        assertTrue(rolls.all { it in 1..20 })
        assertEquals((1..20).toSet(), rolls.toSet())
        assertTrue(List(200) { TableTools.rollDie(6, r) }.all { it in 1..6 })
        assertFailsWith<IllegalArgumentException> { TableTools.rollDie(1, r) }
        assertFailsWith<IllegalArgumentException> { TableTools.rollDice(0, 6, r) }
    }

    @Test fun toolsAreDeterministicForASeed() {
        assertEquals(TableTools.rollDice(10, 20, Random(7)), TableTools.rollDice(10, 20, Random(7)))
        assertEquals(TableTools.flipCoin(Random(3)), TableTools.flipCoin(Random(3)))
    }

    @Test fun coinFlipsBothWays() {
        val r = Random(1)
        assertEquals(CoinFace.entries.toSet(), List(100) { TableTools.flipCoin(r) }.toSet())
    }

    @Test fun startingPlayerPickSkipsTheDeadAndCoversEveryone() {
        val r = Random(5)
        assertEquals(setOf(0, 1, 2, 3), List(200) { TableTools.pickStartingPlayer(s0, r) }.toSet())
        val s = reduce(reduce(s0, MarkDead(0)), MarkDead(3))
        assertEquals(setOf(1, 2), List(200) { TableTools.pickStartingPlayer(s, r) }.toSet())
        var all = s0
        for (i in 0..3) all = reduce(all, MarkDead(i))
        assertTrue(TableTools.pickStartingPlayer(all, r) in 0..3)
    }
}
