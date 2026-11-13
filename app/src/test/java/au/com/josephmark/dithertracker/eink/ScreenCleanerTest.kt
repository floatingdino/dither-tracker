package au.com.josephmark.dithertracker.eink

import au.com.josephmark.dithertracker.play.FakeScheduler
import au.com.josephmark.dithertracker.play.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCleanerTest {
    private val clock = FakeScheduler(4)
    private var redraws = 0
    private val cleaner = ScreenCleaner(clock, 2, { redraws++ })

    init {
        clock.target = Scheduler.Target { cleaner.onTimer(it) }
    }

    @Test fun oneBlackFrameHeldThenRedraw() {
        cleaner.start()
        assertTrue(cleaner.active)
        assertEquals(1, redraws)
        assertEquals(ScreenCleaner.HOLD_MS, clock.dueAt(2))
        clock.advance(ScreenCleaner.HOLD_MS - 1)
        assertTrue(cleaner.active)
        clock.advance(1)
        assertFalse(cleaner.active)
        assertEquals(2, redraws)
        assertEquals(1, cleaner.count)
    }

    @Test fun aTouchCancelsAtOnce() {
        cleaner.start()
        cleaner.cancel()
        assertFalse(cleaner.active)
        assertFalse(clock.isArmed(2))
        assertEquals(2, redraws)
        cleaner.cancel() // idle: nothing
        assertEquals(2, redraws)
    }

    @Test fun foreignTimersAreIgnored() {
        cleaner.start()
        cleaner.onTimer(1)
        assertTrue(cleaner.active)
    }
}
