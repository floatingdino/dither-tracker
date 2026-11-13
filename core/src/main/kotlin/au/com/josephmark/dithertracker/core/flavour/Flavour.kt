package au.com.josephmark.dithertracker.core.flavour

import au.com.josephmark.dithertracker.core.CoinFace
import au.com.josephmark.dithertracker.core.KoReason
import kotlin.random.Random

/** One category of one-liner. Every kind has at least one line in both [Flavour.quippy] and [Flavour.plain]. */
enum class QuipKind {
    KO_LIFE,
    KO_POISON,
    KO_COMMANDER_DAMAGE,
    KO_MANUAL,
    STARTING_PLAYER,
    D20_NATURAL_1,
    D20_NATURAL_20,
    DICE_ROLL,
    COIN_HEADS,
    COIN_TAILS,
    RESET_PROMPT,
    EMPTY_HISTORY,
    WINNER,

    /** Drawn large in place of the life total of the player targeted in commander-damage mode (T30). */
    COMMANDER_TARGET;

    companion object {
        /** Exhaustive on purpose: a new [KoReason] must get its own lines before this compiles. */
        fun ko(reason: KoReason): QuipKind = when (reason) {
            KoReason.LIFE -> KO_LIFE
            KoReason.POISON -> KO_POISON
            KoReason.COMMANDER_DAMAGE -> KO_COMMANDER_DAMAGE
            KoReason.MANUAL -> KO_MANUAL
        }

        /** Natural 1 / 20 lines only for a single d20; everything else gets [DICE_ROLL]. */
        fun dice(sides: Int, value: Int): QuipKind = when {
            sides == 20 && value == 1 -> D20_NATURAL_1
            sides == 20 && value == 20 -> D20_NATURAL_20
            else -> DICE_ROLL
        }

        fun coin(face: CoinFace): QuipKind = when (face) {
            CoinFace.HEADS -> COIN_HEADS
            CoinFace.TAILS -> COIN_TAILS
        }
    }
}

/** Yes/no labels for the reset confirm. [confirm] resets; [cancel] backs out. */
data class ConfirmButtons(val confirm: String, val cancel: String)

data class ResetConfirm(val prompt: String, val buttons: ConfirmButtons)

/** A themed set of six default player names, by seat. */
data class NameSet(val title: String, val names: List<String>) {
    init {
        require(names.size == 6) { "a name set needs 6 names, got ${names.size}" }
    }

    fun name(seat: Int): String = names[seat]
}

/**
 * The last index picked per category, so the next pick can avoid repeating it. Immutable; the UI
 * keeps the latest one it was handed (in memory is fine — it only matters within a session).
 */
class RecentPicks private constructor(private val last: Map<String, Int>) {
    fun lastIndex(kind: QuipKind): Int = last[kind.name] ?: NONE
    internal fun lastIndex(key: String): Int = last[key] ?: NONE
    internal fun with(key: String, index: Int) = RecentPicks(last + (key to index))

    override fun equals(other: Any?) = other is RecentPicks && other.last == last
    override fun hashCode() = last.hashCode()
    override fun toString() = "RecentPicks($last)"

    companion object {
        const val NONE = -1
        val EMPTY = RecentPicks(emptyMap())
    }
}

/** A pick plus the updated [RecentPicks] to pass to the next call. */
data class Picked<out T>(val value: T, val recent: RecentPicks)

/**
 * Personality copy (see SPEC "Tone"). Pure and clock-free: every pick takes the [Random] to use.
 * [quippy] holds the jokes, [plain] neutral functional strings with the same shape, so the UI
 * honours a "quips off" setting by choosing the instance once: `Flavour.of(settings.quips)`.
 *
 * Each picker comes in two forms: a plain one (independent picks, may repeat) and one taking
 * [RecentPicks] that never repeats the previous line of the same category (when it has >= 2 lines).
 */
class Flavour private constructor(
    val quips: Boolean,
    private val tables: Map<QuipKind, List<String>>,
    val resetButtons: List<ConfirmButtons>,
    val nameSets: List<NameSet>,
) {
    /** All lines of [kind], in table order. Never empty. */
    fun lines(kind: QuipKind): List<String> = tables.getValue(kind)

    fun line(kind: QuipKind, random: Random): String = lines(kind)[pickIndex(lines(kind).size, random)]

    fun line(kind: QuipKind, random: Random, recent: RecentPicks): Picked<String> {
        val i = pickIndex(lines(kind).size, random, recent.lastIndex(kind.name))
        return Picked(lines(kind)[i], recent.with(kind.name, i))
    }

    fun ko(reason: KoReason, random: Random): String = line(QuipKind.ko(reason), random)
    fun ko(reason: KoReason, random: Random, recent: RecentPicks) = line(QuipKind.ko(reason), random, recent)

    fun startingPlayer(random: Random): String = line(QuipKind.STARTING_PLAYER, random)
    fun startingPlayer(random: Random, recent: RecentPicks) = line(QuipKind.STARTING_PLAYER, random, recent)

    /** Caption for a roll of one die with [sides] sides showing [value]. */
    fun dice(sides: Int, value: Int, random: Random): String = line(QuipKind.dice(sides, value), random)
    fun dice(sides: Int, value: Int, random: Random, recent: RecentPicks) =
        line(QuipKind.dice(sides, value), random, recent)

    fun coin(face: CoinFace, random: Random): String = line(QuipKind.coin(face), random)
    fun coin(face: CoinFace, random: Random, recent: RecentPicks) = line(QuipKind.coin(face), random, recent)

    fun emptyHistory(random: Random): String = line(QuipKind.EMPTY_HISTORY, random)
    fun emptyHistory(random: Random, recent: RecentPicks) = line(QuipKind.EMPTY_HISTORY, random, recent)

    fun winner(random: Random): String = line(QuipKind.WINNER, random)
    fun winner(random: Random, recent: RecentPicks) = line(QuipKind.WINNER, random, recent)

    /** The line shown on the targeted player's tile in commander-damage mode (pick once per entry). */
    fun commanderTarget(random: Random): String = line(QuipKind.COMMANDER_TARGET, random)
    fun commanderTarget(random: Random, recent: RecentPicks) = line(QuipKind.COMMANDER_TARGET, random, recent)

    /** A prompt and an independently picked button pair (any pair reads correctly after any prompt). */
    fun resetConfirm(random: Random): ResetConfirm =
        ResetConfirm(line(QuipKind.RESET_PROMPT, random), resetButtons[pickIndex(resetButtons.size, random)])

    fun resetConfirm(random: Random, recent: RecentPicks): Picked<ResetConfirm> {
        val prompt = line(QuipKind.RESET_PROMPT, random, recent)
        val b = pickIndex(resetButtons.size, random, prompt.recent.lastIndex(BUTTONS_KEY))
        return Picked(ResetConfirm(prompt.value, resetButtons[b]), prompt.recent.with(BUTTONS_KEY, b))
    }

    /** The default names (`Player 1..6`), i.e. `nameSets[0]`. */
    val defaultNames: NameSet get() = nameSets.first()

    fun randomNameSet(random: Random): NameSet = nameSets[pickIndex(nameSets.size, random)]

    companion object {
        private const val BUTTONS_KEY = "RESET_BUTTONS"

        val quippy = Flavour(
            quips = true,
            tables = tablesOf { kind ->
                when (kind) {
                    QuipKind.KO_LIFE -> QuipTables.koLife
                    QuipKind.KO_POISON -> QuipTables.koPoison
                    QuipKind.KO_COMMANDER_DAMAGE -> QuipTables.koCommanderDamage
                    QuipKind.KO_MANUAL -> QuipTables.koManual
                    QuipKind.STARTING_PLAYER -> QuipTables.startingPlayer
                    QuipKind.D20_NATURAL_1 -> QuipTables.d20Natural1
                    QuipKind.D20_NATURAL_20 -> QuipTables.d20Natural20
                    QuipKind.DICE_ROLL -> QuipTables.diceRoll
                    QuipKind.COIN_HEADS -> QuipTables.coinHeads
                    QuipKind.COIN_TAILS -> QuipTables.coinTails
                    QuipKind.RESET_PROMPT -> QuipTables.resetPrompt
                    QuipKind.EMPTY_HISTORY -> QuipTables.emptyHistory
                    QuipKind.WINNER -> QuipTables.winner
                    QuipKind.COMMANDER_TARGET -> QuipTables.commanderTarget
                }
            },
            resetButtons = QuipTables.resetButtons,
            nameSets = QuipTables.nameSets,
        )

        val plain = Flavour(
            quips = false,
            tables = tablesOf { kind ->
                when (kind) {
                    QuipKind.KO_LIFE -> QuipTables.Plain.koLife
                    QuipKind.KO_POISON -> QuipTables.Plain.koPoison
                    QuipKind.KO_COMMANDER_DAMAGE -> QuipTables.Plain.koCommanderDamage
                    QuipKind.KO_MANUAL -> QuipTables.Plain.koManual
                    QuipKind.STARTING_PLAYER -> QuipTables.Plain.startingPlayer
                    QuipKind.D20_NATURAL_1 -> QuipTables.Plain.d20Natural1
                    QuipKind.D20_NATURAL_20 -> QuipTables.Plain.d20Natural20
                    QuipKind.DICE_ROLL -> QuipTables.Plain.diceRoll
                    QuipKind.COIN_HEADS -> QuipTables.Plain.coinHeads
                    QuipKind.COIN_TAILS -> QuipTables.Plain.coinTails
                    QuipKind.RESET_PROMPT -> QuipTables.Plain.resetPrompt
                    QuipKind.EMPTY_HISTORY -> QuipTables.Plain.emptyHistory
                    QuipKind.WINNER -> QuipTables.Plain.winner
                    QuipKind.COMMANDER_TARGET -> QuipTables.Plain.commanderTarget
                }
            },
            resetButtons = QuipTables.Plain.resetButtons,
            nameSets = QuipTables.Plain.nameSets,
        )

        fun of(quips: Boolean): Flavour = if (quips) quippy else plain

        private fun tablesOf(table: (QuipKind) -> List<String>): Map<QuipKind, List<String>> =
            QuipKind.entries.associateWith { kind -> table(kind).also { require(it.isNotEmpty()) { "no lines for $kind" } } }

        /**
         * Uniform index in `0 until size`, never [avoid] when there is any other choice.
         * Consumes exactly one `nextInt` from [random], so picks are reproducible from a seed.
         */
        internal fun pickIndex(size: Int, random: Random, avoid: Int = RecentPicks.NONE): Int {
            require(size > 0) { "nothing to pick from" }
            if (size == 1 || avoid !in 0 until size) return random.nextInt(size)
            val i = random.nextInt(size - 1)
            return if (i >= avoid) i + 1 else i
        }
    }
}
