package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.Toggle
import kotlin.math.abs
import kotlin.random.Random

/**
 * Which effect plays for what (T28c; T55 added the round-three triggers, the size bands and the
 * weights). Pure; the table is in `design/fx/README.md` and `fx/README.md`.
 *
 * [trigger] classifies a readout group's **net** life change (never a single tap): 0 → none; a KO →
 * [Trigger.KO]; commander damage taken → [Trigger.COMMANDER]; otherwise by size band — small
 * 1…[bigFrom]−1, big [bigFrom]…[hugeFrom]−1, huge [hugeFrom] or more, and for damage a critical band
 * from [critFrom] (`crit`, once a natural 20's effect). The other triggers (counters, toggles,
 * day/night, turn, first player, revive, last player standing) are asked for directly.
 *
 * Each trigger has a pool for a **full** seat and one for a **compact** seat (the drawer is open and the
 * number sits above it): tile-scale effects are drawn against the full tile's furniture, so the
 * compact pools hold only number-anchored ones. Every pool member has a **weight** (cost-aware: cheap
 * effects common, expensive ones rare). [choose] picks by weight with [random], never the effect [seat]
 * played last (when the pool has another), so the pools rotate. A `<name>-narrow` effect, when the
 * design has one, replaces `<name>` on the narrow tier ([variant]). No allocation.
 */
class FxPolicy(
    var random: Random = Random(System.nanoTime()),
    seats: Int = 4,
    /** Size bands (T55): small below [bigFrom], big below [hugeFrom], huge from it; damage crit from [critFrom]. */
    var bigFrom: Int = 4,
    var hugeFrom: Int = 10,
    var critFrom: Int = 20,
) {
    enum class Trigger(
        /** Escalation family ([escalates]): 0 damage, 1 heal, −1 none. */
        val family: Int = -1,
        /** Band rank inside the family. */
        val rank: Int = 0,
        /** Plays on every live seat at once ([FxComponent]'s all-seat path). */
        val allSeats: Boolean = false,
    ) {
        DAMAGE(0, 0), BIG_HIT(0, 1), HUGE_HIT(0, 2), CRIT(0, 3),
        HEAL(1, 0), BIG_HEAL(1, 1), HUGE_HEAL(1, 2),
        KO, WIN, COMMANDER,
        POISON, ENERGY, EXPERIENCE, RAD, STORM,
        MONARCH, INITIATIVE, RING,
        NIGHT(allSeats = true), DAY(allSeats = true),
        TURN, FIRST_PLAYER, REVIVE,
    }

    /** Indexed by [Trigger.ordinal]: the pools for a full seat. */
    val full: Array<Array<Fx>> = Array(TRIGGERS.size) { pool(DEFAULT_FULL, it) }

    /** Weights parallel to [full] (a pool whose weights don't match its size is uniform). */
    val fullWeights: Array<IntArray> = Array(TRIGGERS.size) { weights(DEFAULT_FULL, it) }

    /** Indexed by [Trigger.ordinal]: the pools for a compact seat (no `anchor tile` effects). */
    val compact: Array<Array<Fx>> = Array(TRIGGERS.size) { pool(DEFAULT_COMPACT, it) }

    val compactWeights: Array<IntArray> = Array(TRIGGERS.size) { weights(DEFAULT_COMPACT, it) }

    /** Per [Fx.ordinal]: its `-narrow` twin, or null. */
    private val narrow: Array<Fx?> = Array(ENTRIES.size) { Fx.byName(ENTRIES[it].id + NARROW_SUFFIX) }

    private val last = arrayOfNulls<Fx>(seats)

    /** The trigger for a net life change [netDelta] that KO'd the player when [ko], or null. */
    fun trigger(netDelta: Int, ko: Boolean = false, commander: Boolean = false): Trigger? = when {
        netDelta == 0 -> null
        ko && netDelta < 0 -> Trigger.KO
        commander && netDelta < 0 -> Trigger.COMMANDER
        netDelta < 0 -> {
            val hit = abs(netDelta)
            if (hit >= critFrom) Trigger.CRIT else if (hit >= hugeFrom) Trigger.HUGE_HIT else if (hit >= bigFrom) Trigger.BIG_HIT else Trigger.DAMAGE
        }
        netDelta >= hugeFrom -> Trigger.HUGE_HEAL
        netDelta >= bigFrom -> Trigger.BIG_HEAL
        else -> Trigger.HEAL
    }

    /**
     * Whether [trigger] should play for a readout group that already played the triggers in [fired]
     * (bits `1 shl ordinal`), T55: once per band per group, again only when the running total escalates
     * into a bigger band of the same direction (a smaller one after a bigger one plays nothing).
     */
    fun escalates(trigger: Trigger, fired: Int): Boolean {
        if (fired and (1 shl trigger.ordinal) != 0) return false
        val fam = trigger.family
        if (fam < 0) return true
        for (i in TRIGGERS.indices) {
            val t = TRIGGERS[i]
            if (fired and (1 shl t.ordinal) == 0) continue
            if (t.family == fam && t.rank >= trigger.rank) return false
        }
        return true
    }

    /** The effect for [seat]'s net life change, or null to play nothing. */
    fun choose(seat: Int, netDelta: Int, ko: Boolean = false, compact: Boolean = false): Fx? =
        trigger(netDelta, ko)?.let { choose(seat, it, compact) }

    /**
     * An effect from [trigger]'s pool for [seat] by weight, never the one it played last when there is
     * another; [narrowTier] swaps in a `-narrow` twin. [seat] −1 (an all-seat effect) remembers nothing.
     */
    fun choose(seat: Int, trigger: Trigger, compact: Boolean = false, narrowTier: Boolean = false): Fx? {
        val pool = (if (compact) this.compact else full)[trigger.ordinal]
        val w = (if (compact) compactWeights else fullWeights)[trigger.ordinal]
        val n = pool.size
        if (n == 0) return null
        val uniform = w.size != n
        val prev = if (seat in last.indices) last[seat] else null
        var total = 0
        for (i in 0 until n) if (pool[i] != prev) total += if (uniform) 1 else w[i]
        val pick = if (total == 0) {
            pool[if (n == 1) 0 else random.nextInt(n)]
        } else {
            var r = random.nextInt(total)
            var chosen = pool[0]
            for (i in 0 until n) {
                if (pool[i] == prev) continue
                r -= if (uniform) 1 else w[i]
                if (r < 0) { chosen = pool[i]; break }
            }
            chosen
        }
        if (seat in last.indices) last[seat] = pick
        return if (narrowTier) variant(pick) else pick
    }

    /** [fx]'s narrow-tier twin (`<name>-narrow`), or [fx] itself. */
    fun variant(fx: Fx): Fx = narrow[fx.ordinal] ?: fx

    /** Forgets every seat's last pick (new game, player count change). */
    fun reset() = last.fill(null)

    /** Every effect any pool can pick, narrow twins included (what to prewarm). */
    fun effects(): List<Fx> {
        val all = (full.flatten() + compact.flatten()).distinct()
        return (all + all.mapNotNull { narrow[it.ordinal] }).distinct()
    }

    companion object {
        private val TRIGGERS: Array<Trigger> = Trigger.values()
        private val ENTRIES: Array<Fx> = Fx.values()
        const val NARROW_SUFFIX = "-narrow"

        /** The trigger for a gained [counter] (null: tickets and generic play nothing). */
        fun counterTrigger(counter: Counter): Trigger? = when (counter) {
            Counter.POISON -> Trigger.POISON
            Counter.ENERGY -> Trigger.ENERGY
            Counter.EXPERIENCE -> Trigger.EXPERIENCE
            Counter.RAD -> Trigger.RAD
            Counter.STORM -> Trigger.STORM
            Counter.TICKETS, Counter.GENERIC -> null
        }

        /** The trigger for a [toggle] turned on (null: the city's blessing plays nothing; `bunting` is not approved). */
        fun toggleTrigger(toggle: Toggle): Trigger? = when (toggle) {
            Toggle.MONARCH -> Trigger.MONARCH
            Toggle.INITIATIVE -> Trigger.INITIATIVE
            Toggle.RING_BEARER -> Trigger.RING
            Toggle.CITYS_BLESSING -> null
        }

        private fun fx(name: String): Fx = requireNotNull(Fx.byName(name)) { "no effect '$name' in design/fx/" }

        private fun pool(table: Map<Trigger, List<Pair<String, Int>>>, i: Int): Array<Fx> =
            (table[TRIGGERS[i]] ?: emptyList()).map { fx(it.first) }.toTypedArray()

        private fun weights(table: Map<Trigger, List<Pair<String, Int>>>, i: Int): IntArray =
            (table[TRIGGERS[i]] ?: emptyList()).map { it.second }.toIntArray()

        /**
         * Full-seat pools and weights (T55). Weights follow cost (flips per beat over `37`; a number
         * change is ~940): the cheap ones common, `chomp` (3198 in its bite), `claw-cut` (~1300 a beat)
         * and `spotlight` (1351) rare where their trigger is frequent.
         */
        val DEFAULT_FULL: Map<Trigger, List<Pair<String, Int>>> = mapOf(
            Trigger.DAMAGE to listOf("pow" to 3, "crack" to 3, "slash" to 3, "quake" to 2),
            Trigger.BIG_HIT to listOf("claw-cut" to 3, "quake" to 2, "pow" to 1, "crack" to 1, "slash" to 1, "chomp" to 1),
            Trigger.HUGE_HIT to listOf("focus" to 1, "stomp" to 1),
            Trigger.CRIT to listOf("crit" to 1),
            Trigger.HEAL to listOf("twinkle" to 3, "sprout" to 3, "chime" to 3, "heart" to 2),
            Trigger.BIG_HEAL to listOf("rise" to 2, "heart" to 2, "twinkle" to 1, "chime" to 1),
            Trigger.HUGE_HEAL to listOf("sunrise" to 1, "vines" to 1, "heart-burst" to 1),
            Trigger.KO to listOf("ko-shatter" to 1),
            Trigger.WIN to listOf("winner" to 1),
            Trigger.COMMANDER to listOf("imp-raspberry" to 1, "imp-poke" to 1),
            Trigger.POISON to listOf("ooze" to 1, "brand" to 1),
            Trigger.ENERGY to listOf("charge" to 1),
            Trigger.EXPERIENCE to listOf("level-up" to 1),
            Trigger.RAD to listOf("rad-glow" to 1),
            Trigger.STORM to listOf("storm-copy" to 1),
            Trigger.MONARCH to listOf("crown-drop" to 1),
            Trigger.INITIATIVE to listOf("torch" to 1),
            Trigger.RING to listOf("ring-tempt" to 1),
            Trigger.NIGHT to listOf("dusk" to 1),
            Trigger.DAY to listOf("dawn" to 1),
            Trigger.TURN to listOf("your-turn" to 3, "spotlight" to 1),
            Trigger.FIRST_PLAYER to listOf("start-flag" to 1),
            Trigger.REVIVE to listOf("revive" to 1),
        )

        /**
         * Compact-seat pools: number-anchored effects only (`fizzle` is the compact KO). Life bands keep
         * the light ones (the frame is clipped to the area above the drawer); the other triggers use
         * their full pools, all of them number-anchored.
         */
        val DEFAULT_COMPACT: Map<Trigger, List<Pair<String, Int>>> = DEFAULT_FULL + mapOf(
            Trigger.DAMAGE to listOf("pow" to 3, "crack" to 3, "slash" to 3),
            Trigger.BIG_HIT to listOf("pow" to 2, "crack" to 2, "slash" to 2, "claw-cut" to 1),
            Trigger.HUGE_HIT to listOf("pow" to 2, "crack" to 2, "slash" to 2, "claw-cut" to 1),
            Trigger.CRIT to listOf("pow" to 2, "crack" to 2, "slash" to 2, "claw-cut" to 1),
            Trigger.HEAL to listOf("twinkle" to 3, "sprout" to 3, "chime" to 3, "heart" to 2),
            Trigger.BIG_HEAL to listOf("twinkle" to 1, "sprout" to 1, "chime" to 1, "heart" to 1),
            Trigger.HUGE_HEAL to listOf("twinkle" to 1, "sprout" to 1, "chime" to 1, "heart" to 1),
            Trigger.KO to listOf("fizzle" to 1),
            Trigger.WIN to listOf("twinkle" to 1, "sprout" to 1, "chime" to 1, "heart" to 1),
        )
    }
}
