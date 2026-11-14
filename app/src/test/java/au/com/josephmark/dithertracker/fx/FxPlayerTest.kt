package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.play.FakeScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxPlayerTest {

    private val pow = Fx.byName("pow")!!         // holds 400 250 0 (T65: the burst holds longest)
    private val twinkle = Fx.byName("twinkle")!!
    private val t0 = twinkle.holdMs(0).toLong()
    private val t1 = twinkle.holdMs(1).toLong()
    private val t2 = twinkle.holdMs(2).toLong()

    private val sched = FakeScheduler(5) // 4 seats + the all-seat group's timer
    private val events = ArrayList<String>()
    // A slow display: the authored holds (the fast ones are derived, see fastDisplayDerivesThePushPull).
    private val player: FxPlayer = FxPlayer(4, sched, { seat -> events += "$seat:${player.current(seat)?.id ?: "clean"}:${player.frame(seat)}@${sched.now}" }, speed = { DisplaySpeed.SLOW })
        .also { sched.target = it }

    @Test fun playsEveryDrawnFrameWithItsHoldThenEndsClean() {
        assertTrue(player.play(1, pow))
        assertEquals(pow, player.current(1))
        assertEquals(0, player.frame(1))
        sched.advance(2000)
        assertEquals(listOf("1:pow:0@0", "1:pow:1@400", "1:clean:-1@650"), events)
        assertNull(player.current(1))
        assertFalse(sched.isArmed(1))
        assertEquals(0, player.active)
    }

    @Test fun fourFrameEffectStepsThroughThreeDrawnFrames() {
        player.play(0, twinkle)
        sched.advance(5000)
        assertEquals(listOf("0:twinkle:0@0", "0:twinkle:1@$t0", "0:twinkle:2@${t0 + t1}", "0:clean:-1@${t0 + t1 + t2}"), events)
    }

    @Test fun holdScaleScalesEveryHold() {
        player.holdScale = 1.5f
        player.play(2, pow)
        sched.advance(5000)
        assertEquals(listOf("2:pow:0@0", "2:pow:1@600", "2:clean:-1@975"), events)
        assertEquals(600L, player.hold(pow, 0))
    }

    @Test fun nothingChangesBetweenKeyframes() {
        player.play(0, pow)
        sched.advance(399)
        assertEquals(1, events.size)
        assertEquals(0, player.frame(0))
        sched.advance(1)
        assertEquals(1, player.frame(0))
    }

    @Test fun playOnBusySeatReplacesImmediatelyWithoutQueueing() {
        player.play(0, pow)
        sched.advance(450) // on pow frame 1
        assertTrue(player.play(0, twinkle))
        assertEquals(twinkle, player.current(0))
        assertEquals(0, player.frame(0))
        assertEquals(1, player.active)
        sched.advance(5000)
        // Restarted at 450: twinkle's frames from there; no pow frames after.
        assertEquals(
            listOf("0:pow:0@0", "0:pow:1@400", "0:twinkle:0@450", "0:twinkle:1@${450 + t0}", "0:twinkle:2@${450 + t0 + t1}", "0:clean:-1@${450 + t0 + t1 + t2}"),
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
        sched.advance(t0)
        // One timer: every seat's keyframe changes at the same instant (one entry point, one flush).
        assertEquals(listOf("0:twinkle:1@$t0", "1:twinkle:1@$t0", "2:twinkle:1@$t0", "3:twinkle:1@$t0"), events.takeLast(4))
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

    /**
     * T65: on a fast display the read frame (the longest hold) keeps its hold and every other frame is
     * cut to FAST_IN_OUT of it, floored at FAST_FLOOR_MS; the speed is read when the effect starts.
     */
    @Test fun fastDisplayDerivesThePushPull() {
        var speed = DisplaySpeed.FAST
        val ev = ArrayList<String>()
        val s2 = FakeScheduler(5)
        lateinit var p: FxPlayer
        p = FxPlayer(4, s2, { seat -> ev += "$seat:${p.frame(seat)}@${s2.now}" }, speed = { speed }).also { s2.target = it }
        assertEquals(400L, p.hold(pow, 0, fast = true))
        assertEquals(150L, p.hold(pow, 1, fast = true))
        p.play(0, pow)
        speed = DisplaySpeed.SLOW // mid-effect: the effect keeps the speed it started with
        s2.advance(5000)
        assertEquals(listOf("0:0@0", "0:1@400", "0:-1@550"), ev)
        for (fx in Fx.entries) {
            var peak = 0
            for (i in 1 until fx.frameCount) if (fx.holdMs(i) > fx.holdMs(peak)) peak = i
            for (i in 0 until fx.frameCount - 1) {
                val fast = FxPlayer.fastHold(fx, i)
                assertTrue("${fx.id} $i", fast >= FxPlayer.FAST_FLOOR_MS && fast <= fx.holdMs(i))
                if (i == peak) assertEquals(fx.holdMs(i).toLong(), fast)
            }
        }
    }
}
