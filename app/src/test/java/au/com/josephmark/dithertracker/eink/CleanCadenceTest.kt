package au.com.josephmark.dithertracker.eink

import au.com.josephmark.dithertracker.eink.CleanCadence.Companion.CAUSE_ACTION
import au.com.josephmark.dithertracker.eink.CleanCadence.Companion.CAUSE_OTHER
import au.com.josephmark.dithertracker.eink.CleanCadence.Companion.CAUSE_TIMER
import au.com.josephmark.dithertracker.eink.CleanCadence.Companion.FULL
import au.com.josephmark.dithertracker.eink.CleanCadence.Companion.QUIET_MS
import au.com.josephmark.dithertracker.play.FakeScheduler
import au.com.josephmark.dithertracker.play.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanCadenceTest {
    private val clock = FakeScheduler(2)
    private var busy = false
    private val cleans = ArrayList<Int>()
    private val cadence = CleanCadence(clock, 1, object : CleanCadence.Host {
        override fun isBusy() = busy
        override fun clean(seat: Int) { cleans += seat }
    }, seats = 4)

    init {
        clock.target = Scheduler.Target { cadence.onTimer(it) }
    }

    /** One committed action on [seats] (bit masks). */
    private fun act(mask: Int, cause: Int = CAUSE_ACTION) {
        cadence.touched(mask)
        cadence.settled(cause)
    }

    private fun acts(n: Int, mask: Int) = repeat(n) { act(mask) }

    @Test fun offCountsNothingAndArmsNothing() {
        acts(100, 0b1111)
        assertEquals(0, cadence.count)
        assertFalse(clock.isArmed(1))
        assertEquals(0, clock.scheduled)
    }

    @Test fun belowTheThresholdNothingIsScheduled() {
        cadence.setThreshold(5)
        acts(4, 0b0011)
        assertEquals(4, cadence.count)
        assertFalse(cadence.due)
        assertEquals(0, clock.scheduled)
        clock.advance(60_000)
        assertTrue(cleans.isEmpty())
    }

    @Test fun reachingTheThresholdWaitsForTheQuietPeriod() {
        cadence.setThreshold(5)
        acts(5, 0b0011)
        assertTrue(cadence.due)
        assertTrue(cadence.waiting)
        assertEquals(QUIET_MS, clock.dueAt(1))
        clock.advance(QUIET_MS - 1)
        assertTrue(cleans.isEmpty())
        clock.advance(1)
        assertEquals(listOf(FULL), cleans)
        cadence.settled(CAUSE_TIMER) // the quiet timer's own entry point: no re-arm while the clean runs
        assertFalse(clock.isArmed(1))
    }

    @Test fun activityRestartsTheQuietPeriodWithoutStacking() {
        cadence.setThreshold(3)
        acts(3, 0b0001 or 0b0010)
        clock.advance(QUIET_MS - 100)
        act(0b0001) // more play: restart
        assertEquals(clock.now + QUIET_MS, clock.dueAt(1))
        cadence.settled(CAUSE_OTHER) // a touch-down, nothing counted: still restarts
        clock.advance(QUIET_MS - 1)
        assertTrue(cleans.isEmpty())
        clock.advance(1)
        assertEquals(1, cleans.size)
    }

    @Test fun busyAtTheQuietMomentKeepsWaitingAndArmsNothingUntilTheNextActivity() {
        cadence.setThreshold(2)
        acts(2, 0b0001 or 0b0100)
        busy = true // a touch down / overlay open / effect playing
        clock.advance(QUIET_MS)
        assertTrue(cleans.isEmpty())
        // The quiet timer's own entry point settles: it must not re-arm (no ticking while idle-busy).
        cadence.settled(CAUSE_TIMER)
        assertFalse(clock.isArmed(1))
        clock.advance(60_000)
        assertTrue(cleans.isEmpty())
        // The busy state ends through an entry point (release, timer): that re-arms.
        busy = false
        cadence.settled(CAUSE_OTHER)
        assertTrue(clock.isArmed(1))
        clock.advance(QUIET_MS)
        assertEquals(listOf(FULL), cleans)
    }

    @Test fun timerDrivenRedrawsCountOncePerBurst() {
        cadence.setThreshold(100)
        act(0b0001) // a tap
        act(0b0001, CAUSE_TIMER) // readout commits
        act(0b0001, CAUSE_TIMER) // effect keyframe 1
        act(0b0001, CAUSE_TIMER) // effect keyframe 2
        assertEquals(2, cadence.count)
        act(0b0001) // next tap
        act(0b0001, CAUSE_TIMER)
        assertEquals(4, cadence.count)
    }

    @Test fun pressAndLayoutRedrawsDoNotCount() {
        cadence.setThreshold(100)
        act(0b0001, CAUSE_OTHER)
        assertEquals(0, cadence.count)
        cadence.settled(CAUSE_ACTION) // a release that changed nothing
        assertEquals(0, cadence.count)
    }

    @Test fun oneSeatAloneCleansJustThatSeat() {
        cadence.setThreshold(8)
        act(0b0010) // seat 1 twice: within 8 / 4
        act(0b0010)
        acts(6, 0b0100) // seat 2 alone ... count 8, but seat 2 has only 6
        assertEquals(FULL, cadence.scope())
        cadence.cleaned(FULL)
        act(0b0010) // seat 1: 1
        acts(8, 0b0100) // seat 2: 8
        assertEquals(2, cadence.scope())
        clock.advance(QUIET_MS)
        assertEquals(listOf(2), cleans)
        cadence.cleaned(2)
        assertEquals(0, cadence.seatCount(2))
        assertEquals(1, cadence.seatCount(1))
        assertEquals(1, cadence.count) // what is still on screen
        assertFalse(cadence.due)
    }

    @Test fun twoBusySeatsCleanTheWholeScreen() {
        cadence.setThreshold(8)
        acts(4, 0b0001)
        acts(4, 0b1000)
        assertEquals(FULL, cadence.scope())
        // One seat at the threshold but another one well past threshold / 4: full.
        cadence.cleaned(FULL)
        acts(3, 0b0001)
        acts(8, 0b1000)
        assertEquals(FULL, cadence.scope())
    }

    @Test fun centreOnlyChangesCountTowardsAFullClean() {
        cadence.setThreshold(3)
        acts(3, 0)
        assertTrue(cadence.due)
        assertEquals(FULL, cadence.scope())
    }

    @Test fun cleanedAndResetClearEverything() {
        cadence.setThreshold(3)
        acts(3, 0b1111)
        assertTrue(clock.isArmed(1))
        cadence.cleaned(FULL)
        assertEquals(0, cadence.count)
        assertFalse(clock.isArmed(1))
        acts(2, 0b1111)
        cadence.reset() // new game
        assertEquals(0, cadence.count)
        for (s in 0 until 4) assertEquals(0, cadence.seatCount(s))
    }

    @Test fun thresholdChangeResetsAndOffCancels() {
        cadence.setThreshold(3)
        acts(3, 0b0001)
        assertTrue(clock.isArmed(1))
        cadence.setThreshold(0)
        assertFalse(clock.isArmed(1))
        assertEquals(0, cadence.count)
        clock.advance(QUIET_MS)
        assertTrue(cleans.isEmpty())
    }

    @Test fun pauseDropsTheTimerAndResumeRearms() {
        cadence.setThreshold(2)
        acts(2, 0b0001)
        cadence.pause()
        assertFalse(clock.isArmed(1))
        act(0b0001)
        assertFalse(clock.isArmed(1))
        clock.advance(60_000)
        assertTrue(cleans.isEmpty())
        cadence.resume()
        assertTrue(clock.isArmed(1))
        clock.advance(QUIET_MS)
        assertEquals(listOf(0), cleans) // seat 0 alone
    }
}
