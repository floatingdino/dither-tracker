package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.play.FakeScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceStateTest {

    private class Rig(seed: Long = 1, beat: Boolean = true, speed: Int = DisplaySpeed.SLOW) {
        val timers = FakeScheduler(DiceState.TIMERS)
        val changes = mutableListOf<Pair<Int, Int>>()
        val rolled = mutableListOf<Pair<Tool, Int>>()
        val state = DiceState(timers, Random(seed), { o, n -> changes += o to n }, slow = if (beat) Presentation.TUMBLE else null, fast = if (beat) Presentation.TUMBLE_FAST else null, speed = { speed }).also {
            it.results = object : ToolResults {
                override fun onRoll(tool: Tool, value: Int) { rolled += tool to value }
            }
        }

        init { timers.target = au.com.josephmark.dithertracker.play.Scheduler.Target { state.onTimer(it) } }
    }

    /** Plays the current roll's presentation through, beat by beat, checking each hold. */
    private fun playThrough(r: Rig) {
        val p = r.state.presentation!!
        val o = r.state.outcome
        for (i in 0 until p.beats(o)) {
            assertEquals(DiceState.ROLLING, r.state.phase)
            assertEquals(i, r.state.beat)
            r.timers.advance(p.holdMs(o, i) - 1)
            assertEquals("beat $i holds", i, r.state.beat)
            assertEquals(DiceState.ROLLING, r.state.phase)
            r.timers.advance(1)
        }
        assertEquals(DiceState.SHOWN, r.state.phase)
        assertFalse("no frame timer after the result", r.timers.isArmed(DiceState.TIMER_FRAME))
    }

    @Test fun opensOnAFreshD20AndPresentsItInHeldKeyframes() {
        val r = Rig()
        r.state.open()
        assertEquals(DiceState.ROLLING, r.state.phase)
        assertEquals(Tool.D20, r.state.tool)
        assertTrue(r.state.value in 1..20)
        assertTrue(r.state.caption.isNotEmpty())
        assertEquals(1, r.rolled.size) // the result is fixed (and reported) at the tap, not at the reveal
        assertEquals(Outcome.of(Tool.D20, r.state.value), r.state.outcome)
        val n = r.state.presentation!!.beats(r.state.outcome)
        playThrough(r)
        // One change per keyframe: open, n − 1 beat steps, the result.
        assertEquals(DiceState.CLOSED to DiceState.ROLLING, r.changes.first())
        assertEquals(n - 1, r.changes.count { it == DiceState.ROLLING to DiceState.ROLLING })
        assertEquals(DiceState.ROLLING to DiceState.SHOWN, r.changes.last())
        assertEquals(n + 1, r.changes.size)
    }

    @Test fun everyOutcomeHasItsOwnLandingAndStaysInBudget() {
        val slow = Presentation.TUMBLE
        val fast = Presentation.TUMBLE_FAST
        assertTrue("one sprite table", slow.sprites === fast.sprites)
        for (o in 0 until Outcome.COUNT) {
            for ((p, min, beats) in listOf(Triple(slow, Presentation.MIN_HOLD_MS, 3..5), Triple(fast, Presentation.MIN_HOLD_FAST_MS, 5..7))) {
                val total = p.totalMs(o)
                assertTrue("outcome $o: $total ms", total in 800L..1500L)
                for (i in 0 until p.beats(o)) assertTrue(p.holdMs(o, i) >= min)
                assertTrue("tumbling keyframes then a landing: ${p.beats(o)}", p.beats(o) in beats)
            }
            // T59: the fast display gets more poses, never a longer roll.
            assertTrue(fast.beats(o) >= slow.beats(o) + 2)
            assertTrue(fast.totalMs(o) <= slow.totalMs(o))
        }
        val p = slow
        assertEquals(Outcome.NAT_20, Outcome.of(Tool.D20, 20))
        assertEquals(Outcome.NAT_1, Outcome.of(Tool.D20, 1))
        assertEquals(Outcome.D20, Outcome.of(Tool.D20, 7))
        assertEquals(Outcome.D6, Outcome.of(Tool.D6, 6))
        assertEquals(Outcome.HEADS, Outcome.of(Tool.COIN, 1))
        assertEquals(Outcome.TAILS, Outcome.of(Tool.COIN, 2))
        // Naturals and coin faces land differently from a plain d20.
        val land = { o: Int -> p.scripts[o].beats.last() }
        assertTrue(land(Outcome.NAT_20) !== land(Outcome.D20))
        assertTrue(land(Outcome.NAT_1).result == Beat.PLAIN && land(Outcome.D20).result == Beat.PLATE)
        assertTrue(land(Outcome.HEADS) !== land(Outcome.TAILS))
        // Nothing before the landing shows the result.
        for (o in 0 until Outcome.COUNT) for (i in 0 until p.beats(o) - 1) assertEquals(Beat.NONE, p.scripts[o].beats[i].result)
    }

    @Test fun theDisplaySpeedAtTheRollPicksThePresentation() {
        var speed = DisplaySpeed.FAST
        val timers = FakeScheduler(DiceState.TIMERS)
        val st = DiceState(timers, Random(1), { _, _ -> }, speed = { speed })
        timers.target = au.com.josephmark.dithertracker.play.Scheduler.Target { st.onTimer(it) }
        st.open()
        assertTrue(st.presentation === Presentation.TUMBLE_FAST)
        speed = DisplaySpeed.SLOW
        timers.advance(200)
        assertTrue("a roll keeps its presentation", st.presentation === Presentation.TUMBLE_FAST)
        timers.advance(DiceState.PRESENT_MAX_MS)
        st.again()
        assertTrue(st.presentation === Presentation.TUMBLE)
    }

    @Test fun skipJumpsToTheResultAndAgainReplays() {
        val r = Rig()
        r.state.open()
        r.timers.advance(300)
        val v = r.state.value
        r.state.skip()
        assertEquals(DiceState.SHOWN, r.state.phase)
        assertEquals(v, r.state.value)
        assertFalse(r.timers.isArmed(DiceState.TIMER_FRAME))
        r.state.skip() // no-op once shown
        assertEquals(DiceState.ROLLING to DiceState.SHOWN, r.changes.last())
        r.state.again()
        assertEquals(DiceState.ROLLING, r.state.phase)
        assertEquals(0, r.state.beat)
        assertEquals(2, r.state.rolls)
        playThrough(r)
    }

    @Test fun pauseMidRollShowsTheResultAndLeavesNothingArmed() {
        val r = Rig()
        r.state.open()
        r.timers.advance(400)
        assertEquals(DiceState.ROLLING, r.state.phase)
        r.state.pause()
        assertEquals(DiceState.SHOWN, r.state.phase)
        assertFalse(r.timers.isArmed(DiceState.TIMER_FRAME))
        assertFalse(r.timers.isArmed(DiceState.TIMER_IDLE))
        r.timers.advance(3_600_000)
        assertTrue("still open, showing the result", r.state.phase == DiceState.SHOWN)
        r.state.resume()
        assertEquals(DiceState.SHOWN, r.state.phase)
        assertTrue(r.timers.isArmed(DiceState.TIMER_IDLE))
        assertFalse(r.timers.isArmed(DiceState.TIMER_FRAME))
    }

    @Test fun holdScaleStretchesEveryKeyframe() {
        val r = Rig()
        ToolsDebug.holdScale = 4f
        try {
            r.state.open()
            assertEquals(4 * r.state.presentation!!.holdMs(r.state.outcome, 0), r.timers.dueAt(DiceState.TIMER_FRAME))
        } finally {
            ToolsDebug.holdScale = 1f
        }
    }

    @Test fun withoutTheBeatEveryRollIsOneChange() {
        val r = Rig(beat = false)
        r.state.open()
        assertEquals(DiceState.SHOWN, r.state.phase)
        assertFalse(r.timers.isArmed(DiceState.TIMER_FRAME))
        r.state.roll(Tool.D6)
        assertEquals(listOf(DiceState.CLOSED to DiceState.SHOWN, DiceState.SHOWN to DiceState.SHOWN), r.changes)
    }

    @Test fun toolSwitchAndRollAgain() {
        val r = Rig(beat = false)
        r.state.open()
        r.state.roll(Tool.D6)
        assertEquals(Tool.D6, r.state.tool)
        assertTrue(r.state.value in 1..6)
        r.state.again()
        assertEquals(Tool.D6, r.state.tool)
        r.state.roll(Tool.COIN)
        assertEquals(Tool.COIN, r.state.tool)
        assertTrue(r.state.value in 1..2)
        r.state.again()
        assertEquals(Tool.COIN, r.state.tool)
        assertEquals(listOf(Tool.D20, Tool.D6, Tool.D6, Tool.COIN, Tool.COIN), r.rolled.map { it.first })
        assertEquals(5, r.state.rolls)
    }

    @Test fun aRollDuringThePresentationStartsAFreshOneNeverQueues() {
        val r = Rig()
        r.state.open()
        r.timers.advance(260) // into the second keyframe
        assertEquals(1, r.state.beat)
        r.state.roll(Tool.D6)
        assertEquals(DiceState.ROLLING, r.state.phase)
        assertEquals(0, r.state.beat)
        assertEquals(260 + r.state.presentation!!.holdMs(r.state.outcome, 0), r.timers.dueAt(DiceState.TIMER_FRAME))
        playThrough(r)
        assertEquals(Tool.D6, r.state.tool)
        // Exactly one reveal: the first presentation was replaced, not queued.
        assertEquals(1, r.changes.count { it == DiceState.ROLLING to DiceState.SHOWN })
    }

    @Test fun idleTimeoutClosesAndTouchesReArmIt() {
        val r = Rig()
        r.state.open()
        r.timers.advance(DiceState.IDLE_MS - 1_000)
        r.state.touched()
        r.timers.advance(DiceState.IDLE_MS - 1)
        assertTrue(r.state.isOpen)
        r.timers.advance(1)
        assertFalse(r.state.isOpen)
        assertEquals(DiceState.SHOWN to DiceState.CLOSED, r.changes.last())
    }

    @Test fun closeCancelsEverythingAndIgnoresAgain() {
        val r = Rig()
        r.state.open()
        r.state.close()
        assertEquals(DiceState.CLOSED, r.state.phase)
        assertFalse(r.timers.isArmed(DiceState.TIMER_FRAME))
        assertFalse(r.timers.isArmed(DiceState.TIMER_IDLE))
        val n = r.changes.size
        r.state.again()
        r.state.close()
        r.state.touched()
        assertEquals(n, r.changes.size)
        assertFalse(r.timers.isArmed(DiceState.TIMER_IDLE))
        r.state.open(Tool.COIN)
        assertEquals(Tool.COIN, r.state.tool)
    }

    @Test fun openWhileOpenDoesNotReroll() {
        val r = Rig()
        r.state.open()
        val v = r.state.value
        r.state.open()
        assertEquals(1, r.state.rolls)
        assertEquals(v, r.state.value)
    }

    @Test fun captionRoutingNaturalOneAndTwentyOnlyOnAD20() {
        val q = Flavour.quippy
        val r = Rig(seed = 7, beat = false)
        r.state.open()
        val seen = HashSet<QuipKind>()
        repeat(400) {
            r.state.roll(Tool.D20)
            val expect = when (r.state.value) {
                1 -> QuipKind.D20_NATURAL_1
                20 -> QuipKind.D20_NATURAL_20
                else -> QuipKind.DICE_ROLL
            }
            assertEquals(expect, r.state.captionKind)
            assertTrue(r.state.caption in q.lines(expect))
            seen += expect
        }
        assertEquals(setOf(QuipKind.D20_NATURAL_1, QuipKind.D20_NATURAL_20, QuipKind.DICE_ROLL), seen)
        repeat(100) {
            r.state.roll(Tool.D6)
            assertEquals(QuipKind.DICE_ROLL, r.state.captionKind) // a 1 or a 6 on a d6 is nothing special
            assertTrue(r.state.caption in q.lines(QuipKind.DICE_ROLL))
        }
        repeat(100) {
            r.state.roll(Tool.COIN)
            val kind = if (r.state.value == 1) QuipKind.COIN_HEADS else QuipKind.COIN_TAILS
            assertEquals(kind, r.state.captionKind)
            assertTrue(r.state.caption in q.lines(kind))
        }
    }

    @Test fun noImmediateRepeatsWithinAQuipTable() {
        val r = Rig(seed = 3, beat = false)
        r.state.open()
        val last = HashMap<QuipKind, String>()
        repeat(300) { i ->
            r.state.roll(if (i % 3 == 0) Tool.COIN else Tool.D20)
            val prev = last[r.state.captionKind]
            if (prev != null) assertNotEquals(prev, r.state.caption)
            last[r.state.captionKind] = r.state.caption
        }
    }

    @Test fun seededRandomIsReproducible() {
        val a = Rig(seed = 42, beat = false)
        val b = Rig(seed = 42, beat = false)
        a.state.open(); b.state.open()
        repeat(20) { i ->
            val t = Tool.entries[i % 3]
            a.state.roll(t); b.state.roll(t)
            assertEquals(a.state.value, b.state.value)
            assertEquals(a.state.caption, b.state.caption)
        }
    }

    @Test fun coinValuesMapToFaces() {
        assertEquals(1, Tool.coinValue(au.com.josephmark.dithertracker.core.CoinFace.HEADS))
        assertEquals(au.com.josephmark.dithertracker.core.CoinFace.TAILS, Tool.coinFace(2))
    }
}
