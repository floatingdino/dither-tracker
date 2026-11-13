package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.TableTools
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.core.flavour.RecentPicks
import au.com.josephmark.dithertracker.play.Scheduler
import kotlin.random.Random

/**
 * The dice panel's state machine (T16; pure, JVM-tested on a fake [Scheduler]):
 *
 * ```
 * CLOSED ──open()──▶ [BEAT ──BEAT_MS──▶] SHOWN ──roll(t) / again()──▶ [BEAT ──▶] SHOWN …
 *    ▲                                                                      │
 *    └──────────── close() / IDLE_MS without a touch ───────────────────────┘
 * ```
 *
 * Every roll picks its result and caption **immediately** (so what is about to be revealed is fixed
 * the moment the tap lands, and the log hook sees it at once); [beat] only delays the *drawing* of
 * it by one keyframe — the hero knocked back to a dither "?" for [BEAT_MS] (SPEC rule 4). A roll
 * during a beat starts a fresh one (the old reveal is dropped; sequences never queue).
 *
 * Captions come from [Flavour.dice] (natural 1 / natural 20 lines on a d20, the generic table
 * otherwise) and [Flavour.coin], never the same line twice running within a category
 * ([RecentPicks]). All randomness is the one [random] passed in. [listener] hears every change.
 */
class DiceState(
    private val timers: Scheduler,
    private val random: Random,
    private val listener: Listener,
    private val flavour: Flavour = Flavour.quippy,
    /** Results go here too (the future game log, T18). */
    var results: ToolResults = ToolResults.NONE,
    /** Show the "?" keyframe before each reveal. */
    var beat: Boolean = BEAT_DEFAULT,
    val idleMillis: Long = IDLE_MS,
) {
    fun interface Listener {
        /** Phase [old] → [new]; also called with `old == new` when the result changed in place. */
        fun onChanged(old: Int, new: Int)
    }

    var phase: Int = CLOSED
        private set

    val isOpen: Boolean get() = phase != CLOSED

    /** The tool last rolled (the fold's highlighted button; what [again] rolls). */
    var tool: Tool = Tool.D20
        private set

    /** The last result: a die face, or a coin value ([Tool.coinFace]). 0 before the first roll. */
    var value: Int = 0
        private set

    /** The caption under the result. */
    var caption: String = ""
        private set

    /** Which quip table [caption] came from. */
    var captionKind: QuipKind = QuipKind.DICE_ROLL
        private set

    /** Rolls so far (any tool). */
    var rolls: Int = 0
        private set

    var recent: RecentPicks = RecentPicks.EMPTY
        private set

    /** Opens on a fresh roll of [first] (a d20 from the menu). No-op while open. */
    fun open(first: Tool = Tool.D20) {
        if (phase != CLOSED) return
        roll(first)
    }

    /** Rolls [t] now (opening the panel if closed) and shows it (after the beat, if on). */
    fun roll(t: Tool) {
        val old = phase
        tool = t
        value = if (t.isCoin) Tool.coinValue(TableTools.flipCoin(random)) else TableTools.rollDie(t.sides, random)
        val picked = if (t.isCoin) {
            captionKind = QuipKind.coin(Tool.coinFace(value))
            flavour.coin(Tool.coinFace(value), random, recent)
        } else {
            captionKind = QuipKind.dice(t.sides, value)
            flavour.dice(t.sides, value, random, recent)
        }
        caption = picked.value
        recent = picked.recent
        rolls++
        results.onRoll(t, value)
        if (beat) {
            timers.schedule(TIMER_BEAT, BEAT_MS)
            phase = BEAT
        } else {
            timers.cancel(TIMER_BEAT)
            phase = SHOWN
        }
        timers.schedule(TIMER_IDLE, idleMillis)
        listener.onChanged(old, phase)
    }

    /** Re-rolls the current tool. */
    fun again() {
        if (phase != CLOSED) roll(tool)
    }

    fun close() {
        timers.cancel(TIMER_BEAT)
        timers.cancel(TIMER_IDLE)
        if (phase == CLOSED) return
        val old = phase
        phase = CLOSED
        listener.onChanged(old, CLOSED)
    }

    /** Any touch on the panel: the idle timeout starts again. */
    fun touched() {
        if (phase != CLOSED) timers.schedule(TIMER_IDLE, idleMillis)
    }

    fun onTimer(id: Int) {
        when (id) {
            TIMER_IDLE -> close()
            TIMER_BEAT -> if (phase == BEAT) {
                phase = SHOWN
                listener.onChanged(BEAT, SHOWN)
            }
        }
    }

    companion object {
        const val CLOSED = 0
        const val BEAT = 1
        const val SHOWN = 2

        /** Local timer ids (the owner's `timers` bank). */
        const val TIMER_IDLE = 0
        const val TIMER_BEAT = 1
        const val TIMERS = 2

        /**
         * Idle timeout: the open panel takes the tiles' input (a tap outside only closes it), so it
         * should not linger when the table walks away — but long enough to argue about a roll.
         */
        const val IDLE_MS = 30_000L

        /** How long the "?" keyframe holds before the reveal (SPEC rule 4: ≥ ~250 ms). */
        const val BEAT_MS = 250L

        /** The reveal beat is on: two redraws of the panel per roll ("?" then the result). */
        const val BEAT_DEFAULT = true
    }
}
