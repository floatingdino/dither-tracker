package au.com.josephmark.dithertracker.commander

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.RecentPicks
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.Scheduler
import au.com.josephmark.dithertracker.stage.PlayInput
import kotlin.random.Random

/**
 * Commander-damage mode (T30): state + behaviour, pure Kotlin (no Android, no clock — the idle
 * timeout runs on the injected [Scheduler], timer id 0), allocation-free on the tap path apart from
 * the core action itself.
 *
 * Pressing player X's commander button makes X the **target**: every other tile then shows (and
 * adjusts, from its own seat) the damage its player's commander has dealt X; X's tile shows a quip
 * ([quip], picked once per entry, never the previous one) and X's life. The mode ends when:
 * - X's button is pressed again, or X's tile is tapped ([CommanderZones.TARGET]);
 * - the centre diamond is pressed ([CommanderZones.DIAMOND]);
 * - nothing happened in the mode for [idleMillis] ([IDLE_MS]);
 * - [exit] is called (player count change, another screen…).
 * Pressing another player's button switches the target (new quip).
 *
 * Damage taps go through [Host.dispatchDamage] as
 * `ChangeCommanderDamage(target = X, source = that seat's player, slot, ±1, linkLife = null)`:
 * tap ±1 on release, hold [holdStep] per repeat (±10 like life, with life's slow repeat timing,
 * `PlayInput`).
 */
class CommanderMode(
    private val scheduler: Scheduler,
    private val host: Host,
    private val random: Random = Random.Default,
) : Scheduler.Target {

    interface Host {
        val game: GameState

        /** Applies a commander-damage action on behalf of [sourceSeat]'s tile. */
        fun dispatchDamage(sourceSeat: Int, action: Action.ChangeCommanderDamage)

        /** The target changed from [old] to [new] (either may be [NONE]): redraw the tiles, close drawers. */
        fun onModeChanged(old: Int, new: Int)
    }

    /** Damage per hold repeat: the life hold step (the View keeps it in step with its own). */
    var holdStep: Int = PlayInput.DEFAULT_HOLD_STEP

    /** Idle time before the mode ends by itself. */
    var idleMillis: Long = IDLE_MS

    /** The copy to use. `Flavour.of(true)` until the quips setting exists (T12): see [flavour]. */
    var flavour: Flavour = flavour()

    /** Seat of the target, or [NONE]. */
    var target: Int = NONE
        private set

    val active: Boolean get() = target != NONE

    /** The target tile's line, picked when the target was set (empty when inactive). */
    var quip: String = ""
        private set

    private var recent = RecentPicks.EMPTY

    /** The player at [seat] pressed their commander button. */
    fun press(seat: Int) {
        if (seat !in host.game.players.indices) return
        if (seat == target) exit() else setTarget(seat)
    }

    /** Leaves the mode (no-op when inactive). */
    fun exit() {
        if (!active) return
        scheduler.cancel(TIMER)
        val old = target
        target = NONE
        quip = ""
        host.onModeChanged(old, NONE)
    }

    private fun setTarget(seat: Int) {
        val old = target
        target = seat
        val p = flavour.commanderTarget(random, recent)
        quip = p.value
        recent = p.recent
        scheduler.schedule(TIMER, idleMillis)
        host.onModeChanged(old, seat)
    }

    /** Something happened in the mode: restart the idle timer. */
    fun touch() {
        if (active) scheduler.schedule(TIMER, idleMillis)
    }

    override fun onTimer(id: Int) = exit()

    // ---- zones --------------------------------------------------------------------------------

    /** Whether holding commander [zone] repeats (the damage slot zones, while active). */
    fun repeats(zone: Int): Boolean =
        active && CommanderZones.isCommander(zone) && zone != CommanderZones.DIAMOND &&
            CommanderZones.isSlot(CommanderZones.localOf(zone))

    /** A tap (release) on commander [zone]. Returns true if anything happened. */
    fun onTap(zone: Int): Boolean {
        if (!CommanderZones.isCommander(zone)) return false
        if (zone == CommanderZones.DIAMOND) {
            if (!active) return false
            exit(); return true
        }
        val s = CommanderZones.seatOf(zone)
        val local = CommanderZones.localOf(zone)
        return when {
            local == CommanderZones.BUTTON -> { press(s); true }
            !active -> false
            local == CommanderZones.TARGET -> { if (s == target) { exit(); true } else false }
            CommanderZones.isSlot(local) -> apply(s, local, if (CommanderZones.sideOf(local) == PlayLayout.PLUS) 1 else -1)
            else -> false
        }
    }

    /** One hold repeat on commander [zone]. */
    fun onRepeat(zone: Int): Boolean {
        if (!repeats(zone)) return false
        val local = CommanderZones.localOf(zone)
        if (holdStep <= 0) return false
        val step = if (CommanderZones.sideOf(local) == PlayLayout.PLUS) holdStep else -holdStep
        return apply(CommanderZones.seatOf(zone), local, step)
    }

    private fun apply(sourceSeat: Int, local: Int, delta: Int): Boolean {
        val g = host.game
        val t = target
        if (sourceSeat == t || sourceSeat !in g.players.indices || t !in g.players.indices) return false
        val src = g.players[sourceSeat]
        val slot = CommanderZones.slotOf(local)
        if (slot != Commander.PRIMARY && !src.partner) return false
        touch()
        host.dispatchDamage(sourceSeat, Action.ChangeCommanderDamage(g.players[t].id, src.id, slot, delta, linkLife = null))
        return true
    }

    /** Damage [sourceSeat]'s commander in [slot] has dealt the current target (0 when inactive). */
    fun damage(sourceSeat: Int, slot: Int, game: GameState = host.game): Int {
        val t = target
        val ps = game.players
        if (t !in ps.indices || sourceSeat !in ps.indices) return 0
        return ps[t].commanderDamageFrom(ps[sourceSeat].id, slot)
    }

    companion object {
        const val NONE = -1
        const val TIMER = 0

        /**
         * The mode ends by itself after 45 s without a press in it: long enough to talk through a
         * combat step, short enough that the table never stays stuck showing damage instead of life.
         */
        const val IDLE_MS = 45_000L


        /** The one place the quips on/off choice is made for the mode; T12 wires the setting here. */
        fun flavour(quips: Boolean = true): Flavour = Flavour.of(quips)
    }
}
