package au.com.josephmark.dithertracker.core

import au.com.josephmark.dithertracker.core.Action.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** T17: turn order at every table size with knock-outs, and the turn clock stamped by History. */
class TurnTrackingTest {

    /** Seats visited by [passes] NextTurns from [start], with the seats in [dead] KO'd first. */
    private fun order(players: Int, start: Int, dead: Set<Int>, passes: Int): List<Int> {
        var s = reduce(GameState.create(players), SetStartingPlayer(start))
        for (d in dead) s = reduce(s, MarkDead(d))
        val seen = mutableListOf(s.indexOf(s.turn.activePlayer!!))
        repeat(passes) { s = reduce(s, NextTurn); seen += s.indexOf(s.turn.activePlayer!!) }
        return seen
    }

    @Test fun passingGoesClockwiseAndSkipsKnockedOutSeatsAtEveryCount() {
        for (n in 2..6) for (start in 0 until n) {
            // Nobody dead: every seat in turn.
            assertEquals(List(2 * n + 1) { (start + it) % n }, order(n, start, emptySet(), 2 * n), "$n players from $start")
            // Every subset of the other seats dead (the starter stays alive): never lands on a dead seat,
            // and visits the live ones in seat order.
            for (mask in 0 until (1 shl n)) {
                if ((mask ushr start) and 1 != 0) continue
                val dead = (0 until n).filter { (mask ushr it) and 1 != 0 }.toSet()
                val live = (0 until n).filter { it !in dead }
                val got = order(n, start, dead, 2 * live.size)
                assertTrue(got.none { it in dead }, "$n players, dead $dead: $got")
                val i0 = live.indexOf(start)
                assertEquals(List(got.size) { live[(i0 + it) % live.size] }, got, "$n players from $start, dead $dead")
            }
        }
    }

    @Test fun roundsCountEachTimePlayReachesTheStarterEvenIfTheStarterIsOut() {
        for (n in 2..6) {
            var s = reduce(GameState.create(n), SetStartingPlayer(0))
            repeat(n - 1) { s = reduce(s, NextTurn) }
            assertEquals(1, s.turn.round, "$n: still round 1 at the last seat")
            s = reduce(s, NextTurn)
            assertEquals(2, s.turn.round)
            assertEquals(n + 1, s.turn.number)
            // The starter goes out at the end of round 2: passing over their seat still starts round 3.
            repeat(n - 1) { s = reduce(s, NextTurn) }
            s = reduce(s, MarkDead(0))
            s = reduce(s, NextTurn)
            assertEquals(3, s.turn.round, "$n players, starter dead")
            assertEquals(1, s.indexOf(s.turn.activePlayer!!))
        }
    }

    @Test fun anActivePlayerKnockedOutOnTheirOwnTurnPassesOn() {
        var s = reduce(GameState.create(4), SetStartingPlayer(2))
        s = reduce(s, SetLife(2, 0)) // dies on their own turn: still marked active until passed
        assertEquals(2, s.turn.activePlayer)
        s = reduce(s, NextTurn)
        assertEquals(3, s.turn.activePlayer)
        assertEquals(2, s.turn.number)
    }

    @Test fun historyStampsTheClockWhenTrackingStartsAndAtEachPass() {
        var h = History(GameState.create(4))
        h = h.dispatch(SetStartingPlayer(1), 1_000)
        assertEquals(TurnState(1, 1, 1, 1, startedAt = 1_000, turnStartedAt = 1_000), h.state.turn)
        h = h.dispatch(NextTurn, 61_000)
        assertEquals(1_000, h.state.turn.startedAt)
        assertEquals(61_000, h.state.turn.turnStartedAt)
        // A correction moves the turn but neither counts it nor restarts its clock.
        h = h.dispatch(SetActivePlayer(3), 70_000)
        assertEquals(TurnState(3, 1, 2, 1, startedAt = 1_000, turnStartedAt = 61_000), h.state.turn)
        // Undo restores the previous turn's clock with it.
        h = h.undo()
        h = h.undo()
        assertEquals(TurnState(1, 1, 1, 1, startedAt = 1_000, turnStartedAt = 1_000), h.state.turn)
        h = h.redo()
        assertEquals(61_000, h.state.turn.turnStartedAt)
        // Picking a new first player restarts tracking and both clocks.
        h = h.dispatch(SetStartingPlayer(0), 500_000)
        assertEquals(TurnState(0, 0, 1, 1, startedAt = 500_000, turnStartedAt = 500_000), h.state.turn)
        // Re-picking the same player on turn 1 changes nothing (the clock keeps running).
        assertSame(h, h.dispatch(SetStartingPlayer(0), 600_000))
        // A new game stops tracking and the clock.
        h = h.dispatch(NewGame, 700_000)
        assertEquals(TurnState(), h.state.turn)
    }

    @Test fun nextTurnBeforeTrackingStartsItAndTheClock() {
        val g = Game(GameState.create(3)).apply(NextTurn, 42)
        assertEquals(TurnState(0, 0, 1, 1, startedAt = 42, turnStartedAt = 42), g.state.turn)
        val c = Game(GameState.create(3)).apply(SetActivePlayer(2), 7)
        assertEquals(TurnState(2, 2, 1, 1, startedAt = 7, turnStartedAt = 7), c.state.turn)
    }

    @Test fun otherActionsLeaveTheClockAlone() {
        var h = History(GameState.create(4)).dispatch(SetStartingPlayer(0), 10)
        val t = h.state.turn
        h = h.dispatch(ChangeLife(1, -3), 20, "life")
        h = h.dispatch(SetPlayerCount(5), 30)
        h = h.dispatch(SetToggle(2, Toggle.MONARCH, true), 40)
        assertEquals(t, h.state.turn)
    }

    @Test fun undoingAnInterleavedGroupReplaysPassesAtTheirOwnTime() {
        var h = History(GameState.create(4)).dispatch(SetStartingPlayer(0), 0)
        h = h.dispatch(ChangeLife(2, -1), 1_000, "life-2")
        // A keyed pass (a null key would close the life group): the only way a pass lands inside a group.
        h = h.dispatch(NextTurn, 2_000, "turn")
        h = h.dispatch(ChangeLife(2, -1), 2_500, "life-2") // folds into the group opened before the pass
        h = h.endGroup()
        // Undo removes the life group (touched last); the pass is replayed and keeps its 2 000 stamp.
        h = h.undo()
        assertEquals(40, h.state.players[2].life)
        assertEquals(TurnState(1, 0, 2, 1, startedAt = 0, turnStartedAt = 2_000), h.state.turn)
    }

    @Test fun reduceAloneNeverStamps() {
        val s = reduce(reduce(GameState.create(4), SetStartingPlayer(1)), NextTurn)
        assertEquals(TurnState.NO_TIME, s.turn.startedAt)
        assertEquals(TurnState.NO_TIME, s.turn.turnStartedAt)
    }
}
