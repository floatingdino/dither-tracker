package au.com.josephmark.dithertracker.history

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.History
import au.com.josephmark.dithertracker.core.LogEntry
import au.com.josephmark.dithertracker.core.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryIndexTest {

    private val state = GameState.create(4)

    private fun rows(index: HistoryIndex, st: GameState = state) =
        (0 until index.size).map { index.fill(it, st, HistoryRow()).toString() }

    @Test fun emptyLogHasNoRows() {
        val i = HistoryIndex()
        assertTrue(i.update(emptyList()))
        assertEquals(0, i.size)
        assertFalse(i.grouped)
    }

    @Test fun newestFirstWithoutDividersWhenTurnsAreOff() {
        val log = listOf(
            LogEntry(0, 0, LogEvent.LifeChanged(0, -1, 39)),
            LogEntry(1, 0, LogEvent.LifeChanged(1, -2, 38)),
            LogEntry(2, 0, LogEvent.LifeChanged(2, -3, 37)),
        )
        val i = HistoryIndex().apply { update(log) }
        assertEquals(
            listOf("[life] Player 3 | −3 → 37", "[life] Player 2 | −2 → 38", "[life] Player 1 | −1 → 39"),
            rows(i),
        )
    }

    @Test fun turnDividersHeadEachTurnNewestFirst() {
        var h = History(state)
        h = h.dispatch(Action.ChangeLife(0, -1), 0)                   // turn 0 (pregame)
        h = h.dispatch(Action.SetStartingPlayer(1), 10)                // turn 1 starts
        h = h.dispatch(Action.ChangeLife(2, -5), 20)                   // during turn 1
        h = h.dispatch(Action.NextTurn, 30)                            // turn 2 starts
        h = h.dispatch(Action.ChangeLife(3, 2), 40)                    // during turn 2
        val i = HistoryIndex().apply { update(h.log) }
        assertTrue(i.grouped)
        val r = rows(i, h.state)
        assertEquals("--- TURN 2 ---", r[0])
        assertEquals("[life] Player 4 | +2 → 42", r[1])
        assertTrue(r[2], r[2].startsWith("[turn] Player 3 | turn 2"))
        assertEquals("--- TURN 1 ---", r[3])
        assertEquals("[life] Player 3 | −5 → 35", r[4])
        assertTrue(r[5], r[5].startsWith("[turn]"))
        assertEquals("--- PREGAME ---", r[6])
        assertEquals("[life] Player 1 | −1 → 39", r[7])
        assertEquals(8, i.size)
        assertTrue(i.isDivider(0)); assertEquals(2, i.dividerTurn(0))
        assertTrue(i.isDivider(6)); assertEquals(0, i.dividerTurn(6))
    }

    @Test fun rebuildsOnlyWhenTheLogObjectChanges() {
        val log = listOf(LogEntry(0, 0, LogEvent.NewGameStarted))
        val i = HistoryIndex()
        assertTrue(i.update(log))
        assertFalse(i.update(log))
        assertTrue(i.update(log + LogEntry(1, 0, LogEvent.GameReset)))
        assertEquals(2, i.size)
        i.reset()
        assertTrue(i.update(i.log ?: log))
    }

    @Test fun longLogsGrowTheIndex() {
        val log = List(1000) { LogEntry(it.toLong(), 0, LogEvent.LifeChanged(it % 4, -1, 40 - it)) }
        val i = HistoryIndex(initialCapacity = 4).apply { update(log) }
        assertEquals(1000, i.size)
        assertEquals(999, i.entryAt(0))
        assertEquals(0, i.entryAt(999))
    }
}
