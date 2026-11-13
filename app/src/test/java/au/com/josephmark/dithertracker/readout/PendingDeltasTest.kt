package au.com.josephmark.dithertracker.readout

import au.com.josephmark.dithertracker.play.FakeScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDeltasTest {

    private val events = ArrayList<String>()
    private val clock = FakeScheduler(4)
    private val pending = PendingDeltas(4, clock, object : PendingDeltas.Sink {
        override fun onReadoutChanged(seat: Int) { events += "draw$seat" }
        override fun onGroupCommitted(seat: Int) { events += "commit$seat" }
    }, 3_000).also { clock.target = it }

    @Test fun appearsUpdatesAndClearsAfterIdle() {
        pending.add(2, -1)
        assertEquals(-1, pending.delta(2)); assertTrue(pending.isShown(2))
        clock.advance(1_000)
        pending.add(2, -1)
        clock.advance(2_999) // window restarts at each change
        assertEquals(-2, pending.delta(2))
        assertEquals(listOf("draw2", "draw2"), events)
        clock.advance(1)
        assertEquals(listOf("draw2", "draw2", "commit2", "draw2"), events) // exactly one clearing redraw
        assertEquals(0, pending.delta(2)); assertFalse(pending.isOpen(2))
        clock.advance(10_000)
        assertEquals(4, events.size)
    }

    @Test fun holdSequenceReadsRoundTens() {
        pending.add(3, -1); pending.add(3, -9); pending.add(3, -10)
        assertEquals(-20, pending.delta(3))
        assertEquals(3, events.count { it == "draw3" })
    }

    @Test fun netZeroShowsNothingAndCommitsWithoutAFrame() {
        pending.add(0, 1)
        pending.add(0, -1)
        assertEquals(0, pending.delta(0)); assertFalse(pending.isShown(0)); assertTrue(pending.isOpen(0))
        assertEquals(listOf("draw0", "draw0"), events) // appeared, then cleared
        pending.add(0, 1) // the group continues
        assertEquals(1, pending.delta(0))
        pending.add(0, -1)
        clock.advance(3_000)
        assertEquals(listOf("draw0", "draw0", "draw0", "draw0", "commit0"), events) // no redraw at commit
    }

    @Test fun seatsAreIndependent() {
        pending.add(0, -1)
        clock.advance(1_000)
        pending.add(1, 5)
        clock.advance(2_000) // seat 0 idle 3 s, seat 1 only 2 s
        assertEquals(0, pending.delta(0)); assertEquals(5, pending.delta(1))
        assertTrue("commit0" in events); assertFalse("commit1" in events)
        clock.advance(1_000)
        assertEquals(0, pending.delta(1))
    }

    @Test fun otherActionCommitsEverything() {
        pending.add(0, -3); pending.add(2, 4)
        events.clear()
        pending.commitAll()
        assertEquals(listOf("commit0", "draw0", "commit2", "draw2"), events)
        assertFalse(clock.isArmed(0)); assertFalse(clock.isArmed(2))
        pending.commitAll()
        assertEquals(4, events.size)
    }

    @Test fun clampedNoOpIsIgnored() {
        pending.add(1, 0)
        assertTrue(events.isEmpty()); assertFalse(pending.isOpen(1)); assertFalse(clock.isArmed(1))
    }
}
