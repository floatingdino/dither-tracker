package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic [WidgetTimer] on a fake clock. */
class FakeTimer(count: Int) : WidgetTimer {
    var target: WidgetTimer.Target? = null
    var now = 0L
        private set
    private val due = LongArray(count) { -1 }

    override fun schedule(id: Int, delayMillis: Long) { due[id] = now + delayMillis }
    override fun cancel(id: Int) { due[id] = -1 }
    fun isArmed(id: Int) = due[id] >= 0

    fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            var next = -1
            for (i in due.indices) if (due[i] in 0..end && (next < 0 || due[i] < due[next])) next = i
            if (next < 0) break
            now = due[next]
            due[next] = -1
            target?.onTimer(next)
        }
        now = end
    }
}

class PressTrackerTest {

    private val events = ArrayList<String>()
    private val sink = object : PressTracker.Sink {
        override fun onPressed(id: Int) { events += "press$id" }
        override fun onReleased(id: Int) { events += "release$id" }
        override fun onTap(id: Int) { events += "tap$id" }
        override fun onRepeat(id: Int) { events += "rep$id" }
    }

    // Two buttons side by side; id 1 repeats (a stepper "+").
    private val region = HitRegion().apply {
        add(0, IRect(0, 0, 30, 30))
        add(1, IRect(40, 0, 70, 30), repeats = true)
    }
    private val timer = FakeTimer(64)
    private val tracker = PressTracker(sink, timer).also { it.targets = region; timer.target = it }

    @Test fun firesOnReleaseNotOnDown() {
        tracker.down(0, 5, 5)
        assertEquals(listOf("press0"), events)
        assertTrue(tracker.isPressed(0))
        tracker.up(0)
        assertEquals(listOf("press0", "tap0", "release0"), events)
        assertFalse(tracker.anyPressed())
    }

    @Test fun cancelFiresNothing() {
        tracker.down(0, 5, 5)
        tracker.cancel(0)
        assertEquals(listOf("press0", "release0"), events)
        tracker.down(3, 5, 5)
        tracker.cancelAll()
        assertEquals(listOf("press0", "release0", "press0", "release0"), events)
    }

    @Test fun slidingOffCancels() {
        tracker.down(0, 5, 5)
        tracker.move(0, 6, 7) // still inside
        assertTrue(tracker.isPressed(0))
        tracker.move(0, 45, 5) // onto the neighbour: cancelled, never re-targets
        assertEquals(listOf("press0", "release0"), events)
        assertFalse(tracker.isPressed(1))
        tracker.up(0)
        assertEquals(2, events.size)
    }

    @Test fun missesAreIgnored() {
        tracker.down(0, 35, 5) // gap between the buttons
        tracker.up(0)
        assertTrue(events.isEmpty())
    }

    @Test fun twoFingersTwoIds() {
        tracker.down(0, 5, 5)
        tracker.down(1, 45, 5)
        tracker.up(1)
        tracker.up(0)
        assertEquals(listOf("press0", "press1", "tap1", "release1", "tap0", "release0"), events)
    }

    @Test fun twoFingersOneIdShareThePress() {
        tracker.down(0, 5, 5)
        tracker.down(1, 10, 10)
        tracker.up(0)
        assertTrue(tracker.isPressed(0))
        tracker.up(1)
        assertEquals(listOf("press0", "tap0", "tap0", "release0"), events)
    }

    @Test fun missedUpIsACancel() {
        tracker.down(0, 5, 5)
        tracker.down(0, 45, 5) // same pointer id again: the first press is dropped, not fired
        tracker.up(0)
        assertEquals(listOf("press0", "release0", "press1", "tap1", "release1"), events)
    }

    @Test fun holdRepeatsAndSkipsTheTap() {
        tracker.down(0, 45, 5)
        timer.advance(499)
        assertEquals(listOf("press1"), events)
        timer.advance(1)
        timer.advance(1000)
        timer.advance(1000)
        assertEquals(3, tracker.repeatCount(1))
        tracker.up(0)
        assertEquals(listOf("press1", "rep1", "rep1", "rep1", "release1"), events)
        assertFalse(timer.isArmed(1))
    }

    @Test fun shortPressOnARepeaterIsATap() {
        tracker.down(0, 45, 5)
        timer.advance(200)
        tracker.up(0)
        timer.advance(2000)
        assertEquals(listOf("press1", "tap1", "release1"), events)
    }

    @Test fun nonRepeatingIdNeverArmsATimer() {
        tracker.down(0, 5, 5)
        assertFalse(timer.isArmed(0))
        timer.advance(5000)
        tracker.up(0)
        assertEquals(listOf("press0", "tap0", "release0"), events)
    }

    @Test fun replacingTargetsCancels() {
        tracker.down(0, 45, 5)
        tracker.targets = region
        timer.advance(3000)
        assertEquals(listOf("press1", "release1"), events)
        assertFalse(tracker.isPressed(1))
    }

    @Test fun noTargetsNoPress() {
        tracker.targets = null
        tracker.down(0, 5, 5)
        tracker.up(0)
        assertTrue(events.isEmpty())
    }
}
