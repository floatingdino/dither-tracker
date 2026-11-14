package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.play.FakeScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxPlayerTest {

    private val pow = Fx.byName("pow")!!         // holds 350 300 0
    private val twinkle = Fx.byName("twinkle")!! // holds 300 300 250 0

    private val sched = FakeScheduler(5) // 4 seats + the all-seat group's timer
    private val events = ArrayList<String>()
    private val player: FxPlayer = FxPlayer(4, sched, { seat -> events += "$seat:${player.current(seat)?.id ?: "clean"}:${player.frame(seat)}@${sched.now}" })
        .also { sched.target = it }

    @Test fun playsEveryDrawnFrameWithItsHoldThenEndsClean() {
        assertTrue(player.play(1, pow))
        assertEquals(pow, player.current(1))
        assertEquals(0, player.frame(1))
        sched.advance(2000)
        assertEquals(listOf("1:pow:0@0", "1:pow:1@350", "1:clean:-1@650"), events)
        assertNull(player.current(1))
        assertFalse(sched.isArmed(1))
        assertEquals(0, player.active)
    }

    @Test fun fourFrameEffectStepsThroughThreeDrawnFrames() {
        player.play(0, twinkle)
        sched.advance(5000)
        assertEquals(listOf("0:twinkle:0@0", "0:twinkle:1@300", "0:twinkle:2@600", "0:clean:-1@850"), events)
    }

    @Test fun holdScaleScalesEveryHold() {
        player.holdScale = 1.5f
        player.play(2, pow)
        sched.advance(5000)
        assertEquals(listOf("2:pow:0@0", "2:pow:1@525", "2:clean:-1@975"), events)
        assertEquals(525L, player.hold(pow, 0))
    }

    @Test fun nothingChangesBetweenKeyframes() {
        player.play(0, pow)
        sched.advance(349)
        assertEquals(1, events.size)
        assertEquals(0, player.frame(0))
        sched.advance(1)
        assertEquals(1, player.frame(0))
    }

    @Test fun playOnBusySeatReplacesImmediatelyWithoutQueueing() {
        player.play(0, pow)
        sched.advance(400) // on pow frame 1
        assertTrue(player.play(0, twinkle))
        assertEquals(twinkle, player.current(0))
        assertEquals(0, player.frame(0))
        assertEquals(1, player.active)
        sched.advance(5000)
        // Restarted at 400: twinkle frames at 400, 700, 1000, clean at 1250; no pow frames after.
        assertEquals(
            listOf("0:pow:0@0", "0:pow:1@350", "0:twinkle:0@400", "0:twinkle:1@700", "0:twinkle:2@1000", "0:clean:-1@1250"),
            events,
        )
    }

    @Test fun cancelIsImmediateAndDisarmsTheTimer() {
        player.play(3, pow)
        sched.advance(100)
        player.cancel(3)
        assertNull(player.current(3))
        assertEquals(-1, player.frame(3))
        assertFalse(sched.isArmed(3))
        assertEquals(listOf("3:pow:0@0", "3:clean:-1@100"), events)
        player.cancel(3) // idle: no event
        sched.advance(1000)
        assertEquals(2, events.size)
    }

    @Test fun atMostTwoSeatsAnimateFurtherRequestsAreDropped() {
        assertTrue(player.play(0, pow))
        assertTrue(player.play(1, twinkle))
        assertFalse(player.play(2, pow))
        assertNull(player.current(2))
        assertFalse(sched.isArmed(2))
        assertEquals(2, events.size) // the dropped play fired nothing
        assertTrue(player.play(1, pow)) // replacing a busy seat is still allowed at the cap
        player.cancel(0)
        assertTrue(player.play(2, pow)) // a slot freed up
        assertEquals(2, player.active)
    }

    @Test fun maxConcurrentIsConfigurable() {
        player.maxConcurrent = 1
        assertTrue(player.play(0, pow))
        assertFalse(player.play(1, pow))
        sched.advance(1000) // pow ended
        assertTrue(player.play(1, pow))
    }

    @Test fun disabledDropsPlaysAndCancelsRunningOnes() {
        player.play(0, pow)
        player.play(1, pow)
        events.clear()
        player.enabled = false
        assertEquals(listOf("0:clean:-1@0", "1:clean:-1@0"), events)
        assertFalse(player.play(2, pow))
        assertEquals(0, player.active)
        sched.advance(5000)
        assertEquals(2, events.size)
        player.enabled = true
        assertTrue(player.play(2, pow))
    }

    @Test fun cancelAllCleansEverySeat() {
        player.play(0, pow); player.play(3, twinkle)
        player.cancelAll()
        for (s in 0 until 4) assertNull(player.current(s))
        assertEquals(0, player.active)
        assertFalse(sched.isArmed(0) || sched.isArmed(3))
    }

    @Test fun invalidSeatIsIgnored() {
        assertFalse(player.play(7, pow))
        assertNull(player.current(-1))
        player.cancel(9)
        assertTrue(events.isEmpty())
    }

    @Test fun staleTimerOnCleanSeatIsHarmless() {
        player.onTimer(2)
        assertTrue(events.isEmpty())
    }

    // ---- all seats at once (T55: dusk / dawn) ---------------------------------------------------------

    @Test fun playAllStartsEverySeatPastTheCapAndStepsThemOnOneTimer() {
        player.maxConcurrent = 2
        assertTrue(player.playAll(0b1111, twinkle))
        assertEquals(4, player.active)
        assertEquals(0b1111, player.group)
        for (s in 0 until 4) assertFalse("no per-seat timers", sched.isArmed(s))
        assertTrue(sched.isArmed(player.groupTimer))
        sched.advance(300)
        // One timer: every seat's keyframe changes at the same instant (one entry point, one flush).
        assertEquals(listOf("0:twinkle:1@300", "1:twinkle:1@300", "2:twinkle:1@300", "3:twinkle:1@300"), events.takeLast(4))
        sched.advance(5000)
        for (s in 0 until 4) assertNull(player.current(s))
        assertEquals(0, player.active)
        assertEquals(0, player.group)
        assertFalse(sched.isArmed(player.groupTimer))
    }

    @Test fun aSeatLeavesTheGroupOnCancelOrAnotherEffectAndTheRestCarryOn() {
        player.playAll(0b0111, twinkle)
        player.cancel(1)
        assertNull(player.current(1))
        assertEquals(0b0101, player.group)
        assertTrue(player.play(2, pow)) // replaces seat 2's group frame with its own timer
        assertEquals(0b0001, player.group)
        assertTrue(sched.isArmed(2))
        sched.advance(300)
        assertEquals(1, player.frame(0))
        assertEquals(pow, player.current(2))
        player.cancel(0)
        assertEquals(0, player.group)
        assertFalse("the group's timer goes with its last seat", sched.isArmed(player.groupTimer))
    }

    @Test fun playAllReplacesPlainEffectsAndAPreviousGroup() {
        player.play(1, pow)
        player.playAll(0b0011, twinkle)
        assertEquals(twinkle, player.current(1))
        assertFalse(sched.isArmed(1))
        assertEquals(2, player.active)
        player.playAll(0b1100, pow)
        assertNull("the old group stopped", player.current(0))
        assertEquals(0b1100, player.group)
        assertEquals(2, player.active)
        player.enabled = false
        assertFalse(player.playAll(0b1111, pow))
        assertEquals(0, player.active)
    }
}
