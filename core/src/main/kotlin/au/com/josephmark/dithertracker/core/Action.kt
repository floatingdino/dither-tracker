package au.com.josephmark.dithertracker.core

/**
 * Everything that can change a [GameState]. Apply with [reduce] (pure rules) or, normally,
 * through [History.dispatch] (adds undo and the game log).
 *
 * Actions naming an unknown player, an invalid commander slot, or that would change nothing
 * are no-ops: [reduce] returns the *same* state instance.
 */
sealed interface Action {
    data class ChangeLife(val player: PlayerId, val delta: Int) : Action
    data class SetLife(val player: PlayerId, val value: Int) : Action

    /** Counters clamp to `0..Limits.COUNTER_MAX`. */
    data class ChangeCounter(val player: PlayerId, val counter: Counter, val delta: Int) : Action
    data class SetCounter(val player: PlayerId, val counter: Counter, val value: Int) : Action

    /** [slot] [Commander.PARTNER] requires the player to have a partner. */
    data class ChangeCommanderTax(val player: PlayerId, val slot: CommanderSlot, val delta: Int) : Action

    /**
     * Commander damage dealt to [target] by [source]'s commander in [slot]. Damage clamps at 0.
     *
     * Increments take life too when [linkLife] is true (null = use
     * [GameSettings.commanderDamageAffectsLife]). Decrements refund exactly the life that this
     * damage previously took (tracked in [DamageSlot.lifeLinked]), never more, regardless of the
     * current setting — unless [linkLife] is explicitly false, which leaves life untouched.
     */
    data class ChangeCommanderDamage(
        val target: PlayerId,
        val source: PlayerId,
        val slot: CommanderSlot,
        val delta: Int,
        val linkLife: Boolean? = null,
    ) : Action

    /** Turning on an exclusive toggle (monarch, initiative) turns it off for everyone else. */
    data class SetToggle(val player: PlayerId, val toggle: Toggle, val on: Boolean) : Action

    data class SetDayNight(val value: DayNight) : Action

    /** Passes the turn to the next non-KO'd player by seat; starts tracking if not started. */
    data object NextTurn : Action

    /** Corrects whose turn it is without counting a turn (starts tracking if not started). */
    data class SetActivePlayer(val player: PlayerId) : Action

    /** Starts turn tracking from [player]: turn 1, round 1. */
    data class SetStartingPlayer(val player: PlayerId) : Action

    data class Rename(val player: PlayerId, val name: String) : Action
    data class SetPattern(val player: PlayerId, val pattern: String) : Action

    /**
     * Turning partner off zeroes that player's partner tax and the partner-slot damage it dealt
     * to every opponent (without refunding linked life).
     */
    data class SetPartner(val player: PlayerId, val partner: Boolean) : Action

    /** Shows the player alive even if an auto-KO rule matches (see [KoOverride.ALIVE]). */
    data class Revive(val player: PlayerId) : Action
    data class MarkDead(val player: PlayerId) : Action

    data class UpdateSettings(val settings: GameSettings) : Action

    /** Keeps players (id, name, seat, pattern, partner) and settings; resets all game values. */
    data object NewGame : Action

    /** Back to defaults for the current player count (default names/patterns, no partners); keeps settings. */
    data object Reset : Action

    /**
     * 2..6. Adds players at the end (lowest unused ids, starting life) or removes the highest
     * seats (and the commander damage they dealt). Existing players keep their values.
     */
    data class SetPlayerCount(val count: Int) : Action

    /**
     * A new game at [players] seats (2..6) as one step (T11: the player count chosen in settings
     * takes effect on the next new game): [SetPlayerCount] then [NewGame]. Logged as a new game.
     */
    data class NewGameFor(val players: Int) : Action
}

/**
 * A sensible key for coalescing rapid repeats of this action into one undo step / log entry
 * (pass it to [History.dispatch]), or null for actions that should never coalesce.
 */
val Action.coalesceKey: String?
    get() = when (this) {
        is Action.ChangeLife -> "life:$player"
        is Action.ChangeCounter -> "counter:$player:$counter"
        is Action.ChangeCommanderTax -> "tax:$player:$slot"
        is Action.ChangeCommanderDamage -> "cmd:$target:$source:$slot"
        else -> null
    }
