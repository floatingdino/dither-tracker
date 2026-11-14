package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.TableTools
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.core.flavour.RecentPicks
import au.com.josephmark.dithertracker.play.Scheduler
import kotlin.random.Random

/**
 * The dice panel's state machine (T16, presentation T52; pure, JVM-tested on a fake [Scheduler]):
 *
 * ```
 * CLOSED ──open()──▶ ROLLING ─beat timers─▶ SHOWN ──roll(t) / again()──▶ ROLLING … ─▶ SHOWN
 *    ▲                  │ skip() (a tap), pause()  ▲                                   │
 *    │                  └──────────────────────────┘                                   │
 *    └──────────────── close() / IDLE_MS without a touch ──────────────────────────────┘
 * ```
 *
 * Every roll picks its result and caption **immediately** (the log hook hears it at once); the
 * [presentation] only delays *drawing* it: the roll's [Outcome] script holds each keyframe
 * ([beat], one timer, re-armed per keyframe, scaled by [ToolsDebug.holdScale]) and then shows the
 * result ([SHOWN]). [skip] jumps to the result; a roll during a presentation starts a fresh one (the
 * old one is dropped; sequences never queue). [pause] shows the result and leaves no timer armed
 * (the idle timeout is re-armed by [resume]). With no presentation every roll is one change.
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
    /** The roll presentation; null shows each result at once. */
    var presentation: Presentation? = Presentation.TUMBLE,
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

    /** The current roll's [Outcome] (its presentation script). */
    var outcome: Int = Outcome.D20
        private set

    /** The keyframe showing while [ROLLING] (0-based). */
    var beat: Int = 0
        private set

    val rolling: Boolean get() = phase == ROLLING

    private var paused = false

    /** Opens on a fresh roll of [first] (a d20 from the menu). No-op while open. */
    fun open(first: Tool = Tool.D20) {
        if (phase != CLOSED) return
        roll(first)
    }

    /** Rolls [t] now (opening the panel if closed) and shows it (after its presentation, if on). */
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
        outcome = Outcome.of(t, value)
        beat = 0
        val p = presentation
        if (p != null && !paused) {
            timers.schedule(TIMER_FRAME, ToolsDebug.hold(p.holdMs(outcome, 0)))
            phase = ROLLING
        } else {
            timers.cancel(TIMER_FRAME)
            phase = SHOWN
        }
        if (!paused) timers.schedule(TIMER_IDLE, idleMillis)
        listener.onChanged(old, phase)
    }

    /** Re-rolls the current tool. */
    fun again() {
        if (phase != CLOSED) roll(tool)
    }

    /** A tap during the presentation: show the result now. No-op otherwise. */
    fun skip() {
        if (phase != ROLLING) return
        timers.cancel(TIMER_FRAME)
        phase = SHOWN
        listener.onChanged(ROLLING, SHOWN)
    }

    /** The app went to the background: the result now, and nothing left armed. */
    fun pause() {
        paused = true
        skip()
        timers.cancel(TIMER_IDLE)
    }

    /** Back in the foreground: the idle timeout runs again (from the start). */
    fun resume() {
        paused = false
        if (phase != CLOSED) timers.schedule(TIMER_IDLE, idleMillis)
    }

    fun close() {
        timers.cancel(TIMER_FRAME)
        timers.cancel(TIMER_IDLE)
        if (phase == CLOSED) return
        val old = phase
        phase = CLOSED
        listener.onChanged(old, CLOSED)
    }

    /** Any touch on the panel: the idle timeout starts again. */
    fun touched() {
        if (phase != CLOSED && !paused) timers.schedule(TIMER_IDLE, idleMillis)
    }

    fun onTimer(id: Int) {
        when (id) {
            TIMER_IDLE -> close()
            TIMER_FRAME -> if (phase == ROLLING) {
                val p = presentation
                if (p != null && beat + 1 < p.beats(outcome)) {
                    beat++
                    timers.schedule(TIMER_FRAME, ToolsDebug.hold(p.holdMs(outcome, beat)))
                    listener.onChanged(ROLLING, ROLLING)
                } else {
                    phase = SHOWN
                    listener.onChanged(ROLLING, SHOWN)
                }
            }
        }
    }

    companion object {
        const val CLOSED = 0
        const val ROLLING = 1
        const val SHOWN = 2

        /** Local timer ids (the owner's `timers` bank). */
        const val TIMER_IDLE = 0
        const val TIMER_FRAME = 1
        const val TIMERS = 2

        /**
         * Idle timeout: the open panel takes the tiles' input (a tap outside only closes it), so it
         * should not linger when the table walks away — but long enough to argue about a roll.
         */
        const val IDLE_MS = 30_000L

        /** Longest shipped presentation (unscaled): advance a test clock this far to see any result. */
        val PRESENT_MAX_MS: Long get() = Presentation.TUMBLE.maxMs
    }
}
