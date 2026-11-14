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
        assertEquals(TurnState(3, 1, 2, 1, startedAt = 1_000, turnStartedAt = 61_000, lastTurnMillis = 60_000, spent = mapOf(1 to 60_000L), taken = mapOf(1 to 1)), h.state.turn)
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
        assertEquals(TurnState(1, 0, 2, 1, startedAt = 0, turnStartedAt = 2_000, lastTurnMillis = 2_000, spent = mapOf(0 to 2_000L), taken = mapOf(0 to 1)), h.state.turn)
    }

    @Test fun reduceAloneNeverStamps() {
        val s = reduce(reduce(GameState.create(4), SetStartingPlayer(1)), NextTurn)
        assertEquals(TurnState.NO_TIME, s.turn.startedAt)
        assertEquals(TurnState.NO_TIME, s.turn.turnStartedAt)
    }

    // ---- T66: durations, per-seat totals, the game's end ----------------------------------------------

    @Test fun passesCreditTheEndedTurnToWhoeverHeldItAndUndoGivesItBack() {
        var h = History(GameState.create(4)).dispatch(SetStartingPlayer(0), 0)
        h = h.dispatch(NextTurn, 127_000)            // seat 0: 2:07
        assertEquals(127_000L, h.state.turn.lastTurnMillis)
        assertEquals(127_000L, h.state.turn.gameMillisAtLastPass)
        h = h.dispatch(SetActivePlayer(2), 130_000)  // a correction: who, not how long
        assertEquals(127_000L, h.state.turn.lastTurnMillis)
        h = h.dispatch(NextTurn, 190_000)            // the corrected holder (seat 2) gets the whole turn: 63 s
        val t = h.state.turn
        assertEquals(63_000L, t.lastTurnMillis)
        assertEquals(190_000L, t.gameMillisAtLastPass)
        assertEquals(mapOf(0 to 127_000L, 2 to 63_000L), t.spent)
        assertEquals(1, t.turnsTaken(0)); assertEquals(1, t.turnsTaken(2)); assertEquals(1, t.turnsTaken(3)); assertEquals(0, t.turnsTaken(1))
        // In progress: seat 3 since 190 s.
        assertEquals(10_000L, t.timeOnTurn(3, 200_000))
        assertEquals(200_000L, t.gameMillis(200_000))
        // Undo the pass: seat 2's time comes back off the books, the previous report returns exactly.
        h = h.undo()
        assertEquals(127_000L, h.state.turn.lastTurnMillis)
        assertEquals(mapOf(0 to 127_000L), h.state.turn.spent)
        assertEquals(73_000L, h.state.turn.timeOnTurn(2, 200_000))
        h = h.redo()
        assertEquals(t, h.state.turn)
    }

    @Test fun knockedOutSeatsAreSkippedAndAPlayerOutMidTurnKeepsTheTurnTheyHad() {
        var h = History(GameState.create(3)).dispatch(SetStartingPlayer(0), 0)
        h = h.dispatch(MarkDead(1), 5_000)
        h = h.dispatch(NextTurn, 10_000)  // 0 -> 2 (1 is out)
        h = h.dispatch(MarkDead(2), 15_000) // out on their own turn
        assertEquals(15_000L, h.state.turn.endedAt) // one left: the game is over
        h = h.dispatch(NextTurn, 40_000)  // a late pass: seat 2's turn still ends at the pass
        assertEquals(mapOf(0 to 10_000L, 2 to 30_000L), h.state.turn.spent)
        assertEquals(15_000L, h.state.turn.gameMillis(99_000), "the game's clock stops at its end")
        // Revive: the game runs again, its end unstamped.
        h = h.dispatch(Revive(2), 50_000)
        assertEquals(TurnState.NO_TIME, h.state.turn.endedAt)
        h = h.undo()
        assertEquals(15_000L, h.state.turn.endedAt)
    }

    @Test fun timeOnTurnAtTheEndCountsTheTurnInProgressToTheEnd() {
        var h = History(GameState.create(2)).dispatch(SetStartingPlayer(0), 0)
        h = h.dispatch(NextTurn, 60_000)
        h = h.dispatch(SetLife(0, 0), 90_000) // seat 1 wins during its own turn
        val t = h.state.turn
        assertEquals(90_000L, t.endedAt)
        assertEquals(60_000L, t.timeOnTurn(0, 500_000))
        assertEquals(30_000L, t.timeOnTurn(1, 500_000))
        assertEquals(90_000L, t.gameMillis(500_000))
    }

    @Test fun newGameAndRestartingResetTheClock() {
        var h = History(GameState.create(4)).dispatch(SetStartingPlayer(0), 0)
        h = h.dispatch(NextTurn, 30_000)
        h = h.dispatch(SetStartingPlayer(2), 40_000)
        assertEquals(TurnState(2, 2, 1, 1, startedAt = 40_000, turnStartedAt = 40_000), h.state.turn)
        h = h.dispatch(NextTurn, 50_000)
        h = h.dispatch(NewGame, 60_000)
        assertEquals(TurnState(), h.state.turn)
    }

    @Test fun activeClockExcludesPausedTime() {
        var c = ActiveClock.pausedAt(1_000)
        assertEquals(1_000L, c.at(5_000_000))
        c = c.resume(10_000)
        assertEquals(1_000 + 5_000L, c.at(15_000))
        c = c.pause(20_000)
        assertEquals(11_000L, c.at(9_000_000)) // a night asleep: nothing
        assertEquals(c, c.pause(9_000_000))
        c = c.resume(9_000_000)
        assertEquals(11_000 + 60_000L, c.at(9_060_000))
        assertEquals(c, c.resume(9_500_000)) // already running
        assertEquals(c.base, c.at(8_000_000)) // a clock set back never runs it backwards
        assertEquals(42L, ActiveClock.WALL.at(42))
        // A turn that spans a pause: only the resumed minutes count.
        var clock = ActiveClock.pausedAt(0).resume(0)
        var h = History(GameState.create(2)).dispatch(SetStartingPlayer(0), clock.at(0))
        clock = clock.pause(60_000)           // a minute, then the night
        clock = clock.resume(8 * 3_600_000L)
        h = h.dispatch(NextTurn, clock.at(8 * 3_600_000L + 7_000))
        assertEquals(67_000L, h.state.turn.lastTurnMillis)
    }
}
