package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.TableTools
import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.play.Scheduler
import kotlin.random.Random

/**
 * Who goes first (T59), the pure state machine (JVM-tested on a fake [Scheduler]): every live seat
 * rolls a d20 at once on its own tile; the highest roll goes first.
 *
 * ```
 * IDLE ─start(live)─▶ ROLLING (beat 0 … n−1) ─▶ LANDED ──▶ WINNER (the FIRST flag) ──▶ DONE
 *                        ▲                         │ the highest is tied
 *                        └──── ROLLING (the tied) ◀ TIE   (at most [MAX_ROUNDS]; then a random pick)
 *
 * T65: the numbers land and sit alone for [LANDED_MS]; only then, a beat later, does the winner's flag
 * (or the "TIE" flags) appear — the landing reads before the verdict.
 * ```
 *
 * Every roll is fixed when its round starts (no result is readable until it lands: the tiles show the
 * tumbling die). [skip] (a tap) resolves every remaining round at once and shows [WINNER]; a skip
 * there finishes. [finish] (pause, detach, a second skip) resolves everything and ends with no timer
 * armed. One timer ([TIMER_STEP]), armed only between [start] and [DONE]. Holds are × the debug
 * [ToolsDebug.holdScale]. The pose count follows the display speed read at [start] ([beats]).
 */
class FirstRoll(
    private val timers: Scheduler,
    private val random: Random,
    private val listener: Listener,
    /** The display speed (`eink.DisplaySpeed`), asked at [start]. */
    private val speed: DisplaySpeed.Source = DisplaySpeed.Source { DisplaySpeed.current() },
) {
    fun interface Listener {
        /** Something visible changed ([phase], [beat], values); [done] once the winner is final. */
        fun onChanged(done: Boolean)
    }

    var phase = IDLE
        private set

    /** Tumbling keyframe while [ROLLING] (0-based) and how many this sequence has. */
    var beat = 0
        private set
    var beats = SLOW_POSES
        private set

    /** Seats taking part (live at [start]), bit per seat. */
    var seats = 0
        private set

    /** Seats rolling in the current round (all of [seats], then only the tied). */
    var rolling = 0
        private set

    /** The latest roll of each seat (0: none). */
    val value = IntArray(MAX_SEATS)

    /** Rounds rolled so far (1 = no tie). */
    var round = 0
        private set

    /** Seats tied on the highest roll ([TIE]), bit per seat. */
    var tied = 0
        private set

    /** The seat going first once [LANDED] (−1 before). */
    var winner = -1
        private set

    /** Whether the cap broke a tie at random ([MAX_ROUNDS] rounds all tied). */
    var drawn = false
        private set

    val active: Boolean get() = phase != IDLE && phase != DONE

    /** Starts the sequence for the [live] seats (bit per seat). False (nothing happens) when none. */
    fun start(live: Int): Boolean {
        if (live == 0) return false
        timers.cancel(TIMER_STEP)
        seats = live
        round = 0
        winner = -1
        drawn = false
        tied = 0
        value.fill(0)
        beats = if (speed.current() == DisplaySpeed.FAST) FAST_POSES else SLOW_POSES
        roll(live)
        return true
    }

    private fun roll(who: Int) {
        rolling = who
        round++
        for (s in 0 until MAX_SEATS) if ((who ushr s) and 1 == 1) value[s] = TableTools.rollDie(SIDES, random)
        phase = ROLLING
        beat = 0
        schedule(poseMs())
        listener.onChanged(false)
    }

    private fun poseMs(): Long = if (beats == FAST_POSES) FAST_POSE_MS[beat] else SLOW_POSE_MS[beat]

    private fun schedule(ms: Long) = timers.schedule(TIMER_STEP, ToolsDebug.hold(ms))

    /** Highest value among [seats]; the tied set ([tied]); the winner when it is alone. */
    private fun judge(): Boolean {
        var best = 0
        for (s in 0 until MAX_SEATS) if ((seats ushr s) and 1 == 1 && value[s] > best) best = value[s]
        var top = 0
        for (s in 0 until MAX_SEATS) if ((seats ushr s) and 1 == 1 && value[s] == best) top = top or (1 shl s)
        tied = top
        if (Integer.bitCount(top) == 1) {
            winner = Integer.numberOfTrailingZeros(top)
            tied = 0
            return true
        }
        return false
    }

    /** Resolves every remaining round now (no keyframes): ties re-roll up to the cap, then a random pick. */
    private fun resolve() {
        if (winner >= 0) return
        while (!judge()) {
            if (round >= MAX_ROUNDS) { pickAtRandom(); return }
            val who = tied
            rolling = who
            round++
            for (s in 0 until MAX_SEATS) if ((who ushr s) and 1 == 1) value[s] = TableTools.rollDie(SIDES, random)
        }
    }

    private fun pickAtRandom() {
        var n = random.nextInt(Integer.bitCount(tied))
        for (s in 0 until MAX_SEATS) if ((tied ushr s) and 1 == 1) { if (n == 0) { winner = s; break }; n-- }
        drawn = true
        tied = 0
    }

    fun onTimer(id: Int) {
        if (id != TIMER_STEP) return
        when (phase) {
            ROLLING -> if (beat + 1 < beats) {
                beat++
                schedule(poseMs())
                listener.onChanged(false)
            } else {
                // Landed together: the rolls show, alone, for a beat.
                if (!judge() && round >= MAX_ROUNDS) pickAtRandom()
                toLanded()
            }
            // Then the verdict: the winner's flag, or the tied seats' "TIE" and their re-roll.
            LANDED -> if (winner >= 0) toWinner() else {
                phase = TIE
                schedule(TIE_MS)
                listener.onChanged(false)
            }
            TIE -> roll(tied)
            WINNER -> finish()
        }
    }

    private fun toLanded() {
        phase = LANDED
        rolling = 0
        schedule(LANDED_MS)
        listener.onChanged(false)
    }

    private fun toWinner() {
        phase = WINNER
        rolling = 0
        schedule(WINNER_MS)
        listener.onChanged(false)
    }

    /** A tap: tumbling, landed or tied → every roll lands now with the winner's flag ([WINNER], held as usual); then → [finish]. */
    fun skip() {
        when (phase) {
            ROLLING, TIE, LANDED -> { resolve(); toWinner() }
            WINNER -> finish()
        }
    }

    /** Resolves everything and ends ([DONE]); nothing armed. No-op when idle or done. */
    fun finish() {
        if (!active) return
        timers.cancel(TIMER_STEP)
        resolve()
        phase = DONE
        rolling = 0
        listener.onChanged(true)
    }

    /** Drops the sequence without a winner (the table changed under it). */
    fun cancel() {
        timers.cancel(TIMER_STEP)
        phase = IDLE
        rolling = 0
        seats = 0
    }

    /** Back to idle after [DONE] has been handled. */
    fun reset() {
        if (phase == DONE) phase = IDLE
    }

    companion object {
        const val IDLE = 0
        const val ROLLING = 1
        const val TIE = 2
        const val LANDED = 3
        const val DONE = 4

        /** Landed with the winner's FIRST flag up (T65: a beat after the numbers). */
        const val WINNER = 5

        const val TIMER_STEP = 0

        const val SIDES = 20
        const val MAX_SEATS = 6

        /** Rounds at most (the first and two re-rolls of a tie); then the tied pick at random. */
        const val MAX_ROUNDS = 3

        /** Tumbling poses on a slow display and on a fast one. */
        const val SLOW_POSES = 3
        const val FAST_POSES = 5

        // ---- Timing (T65): uneven on purpose — anticipate, move fast, settle, hold the read. --------
        /** Slow display (each ≥ 250 ms): the toss, the bounce, the skid slowing down. */
        @JvmField val SLOW_POSE_MS = longArrayOf(300L, 250L, 300L)

        /** Fast display (each ≥ 120 ms): a wind-up, three quick poses of the throw, a slower last one. */
        @JvmField val FAST_POSE_MS = longArrayOf(200L, 130L, 120L, 130L, 200L)

        /** The numbers landed, alone: everyone reads their roll before the verdict. */
        const val LANDED_MS = 1_000L

        /** The winner's FIRST flag up beside every roll, before the tiles go back to their life totals. */
        const val WINNER_MS = 1_600L

        /** The "TIE" flags before the tied seats roll again (long enough to read across the table). */
        const val TIE_MS = 1_000L
    }
}
