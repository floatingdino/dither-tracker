package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.play.FakeScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceStateTest {

    private class Rig(seed: Long = 1, beat: Boolean = true) {
        val timers = FakeScheduler(DiceState.TIMERS)
        val changes = mutableListOf<Pair<Int, Int>>()
        val rolled = mutableListOf<Pair<Tool, Int>>()
        val state = DiceState(timers, Random(seed), { o, n -> changes += o to n }, beat = beat).also {
            it.results = object : ToolResults {
                override fun onRoll(tool: Tool, value: Int) { rolled += tool to value }
            }
        }

        init { timers.target = au.com.josephmark.dithertracker.play.Scheduler.Target { state.onTimer(it) } }
    }

    @Test fun opensOnAFreshD20BehindOneBeat() {
        val r = Rig()
        r.state.open()
        assertEquals(DiceState.BEAT, r.state.phase)
        assertEquals(Tool.D20, r.state.tool)
        assertTrue(r.state.value in 1..20)
        assertTrue(r.state.caption.isNotEmpty())
        assertEquals(1, r.rolled.size) // the result is fixed (and reported) at the tap, not at the reveal
        r.timers.advance(DiceState.BEAT_MS - 1)
        assertEquals(DiceState.BEAT, r.state.phase)
        r.timers.advance(1)
        assertEquals(DiceState.SHOWN, r.state.phase)
        assertEquals(listOf(DiceState.CLOSED to DiceState.BEAT, DiceState.BEAT to DiceState.SHOWN), r.changes)
    }

    @Test fun withoutTheBeatEveryRollIsOneChange() {
        val r = Rig(beat = false)
        r.state.open()
        assertEquals(DiceState.SHOWN, r.state.phase)
        assertFalse(r.timers.isArmed(DiceState.TIMER_BEAT))
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

    @Test fun aRollDuringTheBeatStartsAFreshOneNeverQueues() {
        val r = Rig()
        r.state.open()
        r.timers.advance(100)
        r.state.roll(Tool.D6)
        assertEquals(DiceState.BEAT, r.state.phase)
        assertEquals(100 + DiceState.BEAT_MS, r.timers.dueAt(DiceState.TIMER_BEAT))
        r.timers.advance(DiceState.BEAT_MS)
        assertEquals(DiceState.SHOWN, r.state.phase)
        assertEquals(Tool.D6, r.state.tool)
        // Exactly one reveal: the first beat was replaced, not queued.
        assertEquals(1, r.changes.count { it == DiceState.BEAT to DiceState.SHOWN })
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
        assertFalse(r.timers.isArmed(DiceState.TIMER_BEAT))
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
