package au.com.josephmark.dithertracker.core

import au.com.josephmark.dithertracker.core.Action.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HistoryTest {
    private val s0 = GameState.create(4)
    private fun life(h: History, id: Int = 0) = h.state.player(id)!!.life

    @Test fun undoRedo() {
        var h = History(s0)
        assertFalse(h.canUndo)
        h = h.dispatch(ChangeLife(0, -1), 1)
        h = h.dispatch(ChangeLife(0, -1), 2)
        assertEquals(38, life(h))
        h = h.undo()
        assertEquals(39, life(h))
        assertTrue(h.canRedo)
        h = h.undo()
        assertSame(s0, h.state)
        assertSame(h, h.undo(), "undo with nothing to undo is a no-op")
        h = h.redo().redo()
        assertEquals(38, life(h))
        assertFalse(h.canRedo)
    }

    @Test fun newActionClearsRedo() {
        var h = History(s0).dispatch(ChangeLife(0, -1), 1).undo()
        h = h.dispatch(ChangeLife(1, -1), 2)
        assertFalse(h.canRedo)
    }

    @Test fun noOpsAreNotRecorded() {
        val h = History(s0)
        assertSame(h, h.dispatch(ChangeCounter(0, Counter.POISON, -1), 1))
        assertSame(h, h.dispatch(ChangeLife(99, 1), 1))
    }

    @Test fun depthIsBounded() {
        var h = History(s0, maxDepth = 5)
        repeat(20) { h = h.dispatch(ChangeLife(0, -1), it.toLong()) }
        assertEquals(5, h.past.size)
        repeat(10) { h = h.undo() }
        assertEquals(40 - 20 + 5, life(h))
        assertFalse(h.canUndo)
    }

    @Test fun sameKeyCoalescesIntoOneStep() {
        var h = History(s0)
        repeat(7) { h = h.dispatch(ChangeLife(0, -1), 100L + it, "life:0") }
        assertEquals(33, life(h))
        assertEquals(1, h.past.size)
        assertSame(s0, h.undo().state)
        assertEquals(33, life(h.undo().redo()))
    }

    @Test fun defaultCoalesceKeysAreStableAndDistinct() {
        assertEquals(ChangeLife(0, 1).coalesceKey, ChangeLife(0, -5).coalesceKey)
        val keys = listOf(
            ChangeLife(0, 1), ChangeLife(1, 1),
            ChangeCounter(0, Counter.POISON, 1), ChangeCounter(0, Counter.ENERGY, 1),
            ChangeCommanderTax(0, 0, 1), ChangeCommanderTax(0, 1, 1),
            ChangeCommanderDamage(0, 1, 0, 1), ChangeCommanderDamage(0, 1, 1, 1), ChangeCommanderDamage(0, 2, 0, 1),
        ).map { it.coalesceKey }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(null, NextTurn.coalesceKey)
        assertEquals(null, SetToggle(0, Toggle.MONARCH, true).coalesceKey)
    }

    @Test fun differentKeyNullKeyEndGroupAndUndoAllBreakTheGroup() {
        fun burst(h0: History, t: Long): History {
            val a = ChangeLife(0, -1)
            return h0.dispatch(a, t, a.coalesceKey).dispatch(a, t + 1, a.coalesceKey)
        }
        var h = burst(History(s0), 0)
        h = h.dispatch(ChangeLife(1, -1), 2, ChangeLife(1, -1).coalesceKey)
        h = burst(h, 3)
        assertEquals(3, h.past.size)

        h = burst(History(s0), 0).endGroup()
        h = burst(h, 2)
        assertEquals(2, h.past.size)

        h = burst(History(s0), 0).dispatch(ChangeLife(0, -1), 2)
        assertEquals(2, h.past.size)
        h = h.dispatch(ChangeLife(0, -1), 3)
        assertEquals(3, h.past.size, "null key never coalesces")

        h = burst(burst(History(s0), 0).dispatch(ChangeLife(3, 1), 2).undo(), 3)
        assertEquals(2, h.past.size)
    }

    @Test fun coalescingRespectsTheTimeWindow() {
        var h = History(s0, coalesceWindowMillis = 1000)
        h = h.dispatch(ChangeLife(0, -1), 0, "k")
        h = h.dispatch(ChangeLife(0, -1), 900, "k")
        h = h.dispatch(ChangeLife(0, -1), 1800, "k") // window is measured from the last dispatch
        assertEquals(1, h.past.size)
        h = h.dispatch(ChangeLife(0, -1), 5000, "k")
        assertEquals(2, h.past.size)
    }

    @Test fun groupThatCancelsOutLeavesNoStep() {
        var h = History(s0).dispatch(ChangeLife(1, 3), 0)
        h = h.dispatch(ChangeLife(0, 1), 1, "k").dispatch(ChangeLife(0, -1), 2, "k")
        assertEquals(1, h.past.size)
        assertEquals(40, life(h))
        assertEquals(1, h.log.size)
        assertEquals(43, life(h.dispatch(ChangeLife(0, 1), 3, "k").undo(), 1))
    }

    @Test fun undoAcrossNewGameRestoresStateAndLog() {
        var h = History(s0).dispatch(ChangeLife(0, -5), 1).dispatch(NewGame, 2)
        assertEquals(listOf<LogEvent>(LogEvent.NewGameStarted), h.log.map { it.event })
        h = h.undo()
        assertEquals(35, life(h))
        assertEquals(1, h.log.size)
    }

    @Test fun clearedDropsUndo() {
        val h = History(s0).dispatch(ChangeLife(0, -5), 1).cleared()
        assertFalse(h.canUndo)
        assertEquals(35, life(h))
        assertEquals(1, h.log.size)
    }
}
