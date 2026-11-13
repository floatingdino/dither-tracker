package au.com.josephmark.dithertracker.core

/**
 * Stable player identifier. Ids survive renames, reseating and player-count changes; seats do not.
 * New players get the lowest unused non-negative id.
 */
typealias PlayerId = Int

/** Index of a commander within a player's command zone: [PRIMARY] or (with partner) [PARTNER]. */
typealias CommanderSlot = Int

object Commander {
    const val PRIMARY: CommanderSlot = 0
    const val PARTNER: CommanderSlot = 1
}

/** Per-player numeric counters shown in the drawer (commander tax is separate: it is per commander). */
enum class Counter { POISON, ENERGY, EXPERIENCE, RAD, STORM, TICKETS, GENERIC }

/** Per-player state toggles. [exclusive] toggles can be held by at most one player at a time. */
enum class Toggle(val exclusive: Boolean) {
    MONARCH(true),
    INITIATIVE(true),
    CITYS_BLESSING(false),
    RING_BEARER(false),
}

enum class DayNight { UNSET, DAY, NIGHT }

/** Manual override of the derived KO status. */
enum class KoOverride {
    /** Status is derived from the auto-KO rules (if enabled). */
    NONE,

    /** Revived: shown alive even though an auto-KO rule matches. Cleared automatically (see [reduce]). */
    ALIVE,

    /** Marked dead manually (concession etc.), regardless of values or the auto-KO setting. */
    DEAD,
}

enum class KoReason { LIFE, POISON, COMMANDER_DAMAGE, MANUAL }

data class Counters(
    val poison: Int = 0,
    val energy: Int = 0,
    val experience: Int = 0,
    val rad: Int = 0,
    val storm: Int = 0,
    val tickets: Int = 0,
    val generic: Int = 0,
) {
    operator fun get(counter: Counter): Int = when (counter) {
        Counter.POISON -> poison
        Counter.ENERGY -> energy
        Counter.EXPERIENCE -> experience
        Counter.RAD -> rad
        Counter.STORM -> storm
        Counter.TICKETS -> tickets
        Counter.GENERIC -> generic
    }

    fun with(counter: Counter, value: Int): Counters = when (counter) {
        Counter.POISON -> copy(poison = value)
        Counter.ENERGY -> copy(energy = value)
        Counter.EXPERIENCE -> copy(experience = value)
        Counter.RAD -> copy(rad = value)
        Counter.STORM -> copy(storm = value)
        Counter.TICKETS -> copy(tickets = value)
        Counter.GENERIC -> copy(generic = value)
    }
}

/** Commander tax per commander, stored as the tax amount (the UI decides whether to step by 2). */
data class CommanderTax(val primary: Int = 0, val partner: Int = 0) {
    operator fun get(slot: CommanderSlot): Int = if (slot == Commander.PARTNER) partner else primary
    fun with(slot: CommanderSlot, value: Int): CommanderTax =
        if (slot == Commander.PARTNER) copy(partner = value) else copy(primary = value)
}

/**
 * Damage received from one enemy commander. [lifeLinked] is how much of [damage] was also taken
 * off life (always `<= damage`); it is what gets refunded when the damage is decremented.
 */
data class DamageSlot(val damage: Int = 0, val lifeLinked: Int = 0) {
    val isZero: Boolean get() = damage == 0 && lifeLinked == 0
}

/** Damage received from one opponent, per commander slot of that opponent. */
data class CommanderDamage(val primary: DamageSlot = DamageSlot(), val partner: DamageSlot = DamageSlot()) {
    operator fun get(slot: CommanderSlot): DamageSlot = if (slot == Commander.PARTNER) partner else primary
    fun with(slot: CommanderSlot, value: DamageSlot): CommanderDamage =
        if (slot == Commander.PARTNER) copy(partner = value) else copy(primary = value)

    val isZero: Boolean get() = primary.isZero && partner.isZero
}

data class Toggles(
    val monarch: Boolean = false,
    val initiative: Boolean = false,
    val citysBlessing: Boolean = false,
    val ringBearer: Boolean = false,
) {
    operator fun get(toggle: Toggle): Boolean = when (toggle) {
        Toggle.MONARCH -> monarch
        Toggle.INITIATIVE -> initiative
        Toggle.CITYS_BLESSING -> citysBlessing
        Toggle.RING_BEARER -> ringBearer
    }

    fun with(toggle: Toggle, on: Boolean): Toggles = when (toggle) {
        Toggle.MONARCH -> copy(monarch = on)
        Toggle.INITIATIVE -> copy(initiative = on)
        Toggle.CITYS_BLESSING -> copy(citysBlessing = on)
        Toggle.RING_BEARER -> copy(ringBearer = on)
    }
}

/**
 * One player. Each player is its own immutable object: the reducer only replaces the [Player]
 * instances it actually changes, so `old.players[i] !== new.players[i]` identifies dirty panels.
 *
 * Commander damage is stored on the *receiver*, keyed by the source player's id. Missing keys mean
 * zero; zeroed entries are removed so structurally equal states compare equal.
 */
data class Player(
    val id: PlayerId,
    val name: String,
    val seat: Int,
    val life: Int,
    /** Opaque background-pattern id; [DEFAULT_PATTERN] means "UI picks a default for the seat". */
    val pattern: String = DEFAULT_PATTERN,
    /** Has a partner commander (enables the second commander slot for tax and dealt damage). */
    val partner: Boolean = false,
    val counters: Counters = Counters(),
    val commanderTax: CommanderTax = CommanderTax(),
    val commanderDamage: Map<PlayerId, CommanderDamage> = emptyMap(),
    val toggles: Toggles = Toggles(),
    val koOverride: KoOverride = KoOverride.NONE,
) {
    val commanderCount: Int get() = if (partner) 2 else 1

    fun commanderDamageFrom(source: PlayerId, slot: CommanderSlot): Int =
        commanderDamage[source]?.get(slot)?.damage ?: 0

    /** Highest damage from any single commander (what the 21-damage rule checks). */
    val maxCommanderDamage: Int
        get() = commanderDamage.values.maxOfOrNull { maxOf(it.primary.damage, it.partner.damage) } ?: 0

    companion object {
        const val DEFAULT_PATTERN = ""
        fun defaultName(seat: Int) = "Player ${seat + 1}"
    }
}

/** Settings that affect rules. Changing [startingLife] only takes effect on the next new game. */
data class GameSettings(
    val startingLife: Int = 40,
    val commanderDamageAffectsLife: Boolean = true,
    val autoKo: Boolean = true,
) {
    init {
        require(startingLife in 1..Limits.LIFE_MAX) { "startingLife out of range: $startingLife" }
    }
}

/**
 * Turn tracking. [number] is the count of individual turns taken (0 = tracking not started);
 * [round] increments each time play passes back to (or beyond) [startingPlayer].
 *
 * The turn clock (T17): [startedAt] is when tracking started (turn 1 began) and [turnStartedAt] when
 * the current turn number began, both wall-clock ms taken from the timestamp passed to
 * [Game.apply] / [History.dispatch] (the reducer itself never knows the time, so a bare [reduce]
 * leaves them [NO_TIME]). A correction ([Action.SetActivePlayer]) keeps both; a pass stamps
 * [turnStartedAt]; (re)starting tracking stamps both. Undo restores them with the rest of the state.
 */
data class TurnState(
    val activePlayer: PlayerId? = null,
    val startingPlayer: PlayerId? = null,
    val number: Int = 0,
    val round: Int = 0,
    val startedAt: Long = NO_TIME,
    val turnStartedAt: Long = NO_TIME,
) {
    val started: Boolean get() = number > 0

    /** This state without its clock stamps (compare turns by who and how many, not when). */
    fun unstamped(): TurnState = if (startedAt == NO_TIME && turnStartedAt == NO_TIME) this else copy(startedAt = NO_TIME, turnStartedAt = NO_TIME)

    companion object {
        /** "Not stamped": tracking not started, or a state built by a bare [reduce]. */
        const val NO_TIME = Long.MIN_VALUE
    }
}

/**
 * Whole-table state. [players] is ordered by seat and `players[i].seat == i` always holds.
 * Build one with [GameState.create].
 */
data class GameState(
    val players: List<Player>,
    val settings: GameSettings = GameSettings(),
    val dayNight: DayNight = DayNight.UNSET,
    val turn: TurnState = TurnState(),
) {
    init {
        require(players.size in MIN_PLAYERS..MAX_PLAYERS) { "player count ${players.size}" }
    }

    fun player(id: PlayerId): Player? = players.firstOrNull { it.id == id }

    fun indexOf(id: PlayerId): Int = players.indexOfFirst { it.id == id }

    /** Effective KO reason for [player] (manual override, then auto rules if enabled), or null if alive. */
    fun koReason(player: Player): KoReason? = when (player.koOverride) {
        KoOverride.DEAD -> KoReason.MANUAL
        KoOverride.ALIVE -> null
        KoOverride.NONE -> if (settings.autoKo) autoKoReason(player) else null
    }

    fun isKo(player: Player): Boolean = koReason(player) != null

    fun isKo(id: PlayerId): Boolean = player(id)?.let(::isKo) ?: false

    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 6

        fun create(playerCount: Int = 4, settings: GameSettings = GameSettings()): GameState {
            require(playerCount in MIN_PLAYERS..MAX_PLAYERS) { "player count $playerCount" }
            return GameState(
                players = List(playerCount) { seat -> newPlayer(id = seat, seat = seat, life = settings.startingLife) },
                settings = settings,
            )
        }

        internal fun newPlayer(id: PlayerId, seat: Int, life: Int) =
            Player(id = id, name = Player.defaultName(seat), seat = seat, life = life)
    }
}

object Limits {
    const val LIFE_MIN = -999
    const val LIFE_MAX = 9999
    const val COUNTER_MAX = 999
    const val LETHAL_POISON = 10
    const val LETHAL_COMMANDER_DAMAGE = 21
}

/** The auto-KO rule alone, ignoring overrides and the auto-KO setting. */
fun autoKoReason(player: Player): KoReason? = when {
    player.life <= 0 -> KoReason.LIFE
    player.counters.poison >= Limits.LETHAL_POISON -> KoReason.POISON
    player.maxCommanderDamage >= Limits.LETHAL_COMMANDER_DAMAGE -> KoReason.COMMANDER_DAMAGE
    else -> null
}
