package au.com.josephmark.dithertracker.stage

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.Scheduler
import au.com.josephmark.dithertracker.play.TileSlots

/**
 * One feature of the Play screen, plugged into a [Stage]. Everything is optional; override what the
 * feature needs. Components never reference another component's concrete class: they talk through the
 * game ([Stage.session], [Stage.dispatch]), the shared read-only tile context ([TileSlots]), the
 * [Readouts] service, explicit [Events] and [Cover]s. Two components of one feature (one package)
 * may share that feature's own state object.
 *
 * Seats: a tile component ([Layer.isTile]) is called once per seat with `seat` in `0 until players`;
 * a screen component is called with [Stage.SCREEN] (-1).
 *
 * Zones: a component owns [zonesPerSeat] local ids (`0 until zonesPerSeat`, per seat for tile
 * components). It declares where they are in [zones]; the stage allocates the global ids, hit-tests,
 * tracks presses (release-to-fire, hold-repeat, multi-touch), inverts the [feedback] rect of a pressed
 * zone and calls [onTap] / [onRepeat]. Cancelled presses call nothing.
 *
 * Threading: main thread only. Allocation: nothing on the draw, touch, timer or state paths —
 * preallocate scratch in the component (see `docs/architecture.md`).
 */
abstract class Component(
    /** [Layer] constant: draw/hit pass. */
    val layer: Int,
    /** [Kind] bit: which [Cover]s hide it. */
    val kind: Int,
    /** Local zone ids this component uses (per seat for tile components). */
    val zonesPerSeat: Int = 0,
    /** Local timer ids this component uses ([timers], `0 until timerCount`). */
    val timerCount: Int = 0,
) {
    /** The stage this component is plugged into (set by [Stage.add]). */
    lateinit var stage: Stage
        internal set

    /** Index in the stage's registration order (set by [Stage.add]). */
    internal var index = -1

    val isTile: Boolean get() = Layer.isTile(layer)

    /**
     * This component's timer bank: local ids `0 until timerCount`; firing calls [onTimer]. Use it for
     * pure state machines (`PendingDeltas(..., timers, ...)`). Usable once added to a stage.
     */
    val timers: Scheduler = object : Scheduler {
        override fun schedule(id: Int, delayMillis: Long) = stage.scheduleTimer(this@Component, id, delayMillis)
        override fun cancel(id: Int) = stage.cancelTimer(this@Component, id)
    }

    // ---- lifecycle -----------------------------------------------------------------------------

    /** Just added to [stage] (before `seal`): create state that needs the stage, register services and covers. */
    open fun onAdded() {}

    /** The view is attached (state may have changed while detached). */
    open fun onAttach() {}

    /** The view is detaching: commit / close anything open. Timers are cancelled by the stage after. */
    open fun onDetach() {}

    /** New geometry (first layout, size or scale change). Every touch has been cancelled. */
    open fun onLayout(play: PlayLayout, tiles: TileSlots) {}

    /** Build glyph/icon masks for everything this component can draw (renderer begun on a scratch canvas). */
    open fun prewarm(f: Frame) {}

    // ---- drawing -------------------------------------------------------------------------------

    /**
     * Draw [seat]'s part (or the screen part, seat [Stage.SCREEN]) through `f.r` / `f.text` /
     * `f.icons`, culling by `f.hit(rect)`. Only called while visible (no [Cover] hides it there) and,
     * for tile components, when the seat's panel intersects the clip.
     */
    open fun draw(f: Frame, seat: Int) {}

    /** Screen components: the rect to redraw when a [Cover] shows or hides it. */
    open fun bounds(out: IRect): Boolean = false

    // ---- touch ---------------------------------------------------------------------------------

    /**
     * Declare [seat]'s touch zones into [z], bottom first (a later rect wins where they overlap, and
     * every rect of a higher layer wins over this layer). Only called while visible. Called at every
     * touch-down, so it must reflect the current state and not allocate.
     */
    open fun zones(seat: Int, z: ZoneSink) {}

    /** Optional shape test inside a declared rect (local -1 = a blocking rect). Default: the whole rect. */
    open fun contains(seat: Int, local: Int, gx: Int, gy: Int): Boolean = true

    /** The rect a press on [local] inverts (SPEC rule 6), computed from the *current* state. */
    open fun feedback(seat: Int, local: Int, out: IRect): Boolean = false

    /** Whether holding [local] repeats ([onRepeat] at 500 ms, then every 1000 ms). */
    open fun repeats(seat: Int, local: Int): Boolean = false

    /** A press on [local] was released before it repeated: apply one step. */
    open fun onTap(seat: Int, local: Int) {}

    /** [local] is being held: apply one repeat step. */
    open fun onRepeat(seat: Int, local: Int) {}

    // ---- notifications -------------------------------------------------------------------------

    /**
     * The game changed. [origin] is the seat whose action caused it ([Stage.changeLife] /
     * [Stage.dispatch] with a seat), [Origin.ATTRIBUTED] (the dispatcher feeds the readouts itself) or
     * [Origin.EXTERNAL] (undo, restore, menu…). Invalidate exactly what changed.
     */
    open fun onStateChanged(old: GameState, new: GameState, origin: Int) {}

    /** The number of players changed: rebuild per-seat state (everything is redrawn). */
    open fun onPlayersChanged() {}

    open fun onSettingsChanged(old: AppSettings, new: AppSettings) {}

    /** A [Cover] changed: every touch has been cancelled; drop whatever is now hidden. */
    open fun onCoversChanged() {}

    /** An explicit event ([Events]) from another component or the stage. */
    open fun onEvent(event: Int, seat: Int) {}

    /** Timer [id] (local) fired. */
    open fun onTimer(id: Int) {}

    // ---- helpers -------------------------------------------------------------------------------

    /** Whether this component is visible on [seat] ([Stage.SCREEN] for screen components). */
    fun visible(seat: Int): Boolean = stage.isVisible(this, seat)

    fun invalidate(rect: IRect) = stage.invalidate(rect)
}

/** Where a component declares its zones ([Component.zones]). */
interface ZoneSink {
    /** Local zone [local] covers [rect] (screen dither px). */
    fun add(local: Int, rect: IRect)

    /** [rect] swallows touches: nothing below it is hit there, and nothing is pressed. */
    fun block(rect: IRect)
}

/** Who caused a state change ([Component.onStateChanged]); seats are `>= 0`. */
object Origin {
    /** Not this screen's own tap: undo, restore, a menu action. Commits every open readout. */
    const val EXTERNAL = -1

    /** This screen's own action whose readout the dispatcher feeds itself ([Readouts]). */
    const val ATTRIBUTED = -2
}

/**
 * Explicit cross-feature events ([Stage.send] → [Component.onEvent]). The list is the contract: add a
 * constant here when a feature needs another to act, rather than referencing its class.
 */
object Events {
    /** A pointer went down on one of [seat]'s zones (before anything applies; effects cancel on it). */
    const val TOUCH_DOWN = 1

    /** [seat]'s player asked to be the commander-damage target (the drawer bar's imp button). */
    const val COMMANDER_TARGET = 2

    /**
     * Hold off [seat]'s dead treatment (T15): an effect (T28c's KO flourish) is about to play over the
     * live tile. Send it from `onStateChanged` when the seat's KO flips on (the effects slot is
     * registered before the KO slot, so no frame shows the dead tile first); sent later, the dead
     * treatment is taken down until [KO_RELEASE]. The KO component releases by itself after
     * `KoComponent.HOLD_MAX_MS` and on any touch-down on the seat. Default (nobody holds): immediate.
     */
    const val KO_HOLD = 3

    /** The effect on [seat] finished (or was cancelled): draw the dead treatment now if still KO. */
    const val KO_RELEASE = 4
}
