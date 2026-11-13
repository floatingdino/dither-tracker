package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.History
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {

    @Test fun changeLifeNotifiesWithOnlyTheTouchedPlayerReplaced() {
        val s = GameSession.newGame(4)
        var calls = 0
        s.listener = GameSession.Listener { old, new ->
            calls++
            for (i in 0 until 4) {
                if (i == 2) assertNotSame(old.players[i], new.players[i]) else assertSame(old.players[i], new.players[i])
            }
        }
        s.changeLife(2, -1, 1_000)
        assertEquals(1, calls)
        assertEquals(39, s.state.players[2].life)
        assertEquals(40, s.state.players[0].life)
    }

    @Test fun samesSeatTapsCoalesceIntoOneUndoStep() {
        val s = GameSession.newGame(4)
        repeat(5) { s.changeLife(1, 1, 1_000L + it * 100) }
        assertEquals(45, s.state.players[1].life)
        assertEquals(1, s.history.past.size)
        s.changeLife(3, -1, 2_000)
        assertEquals(2, s.history.past.size)
        s.undo(); s.undo()
        assertEquals(40, s.state.players[1].life)
    }

    @Test fun replaceSwapsTheWholeGameAndNotifies() {
        val s = GameSession.newGame(4)
        var notified = false
        s.listener = GameSession.Listener { _, _ -> notified = true }
        val restored = History(GameState.create(4)).dispatch(
            au.com.josephmark.dithertracker.core.Action.ChangeLife(0, -10), 5,
        )
        s.replace(restored)
        assertTrue(notified)
        assertEquals(30, s.state.players[0].life)
    }

    @Test fun invalidSeatIsANoOp() {
        val s = GameSession.newGame(4)
        val before = s.history
        s.changeLife(9, 1, 0)
        assertSame(before, s.history)
    }
}
