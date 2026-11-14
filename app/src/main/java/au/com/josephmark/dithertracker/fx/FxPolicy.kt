package au.com.josephmark.dithertracker.fx

import kotlin.math.abs
import kotlin.random.Random

/**
 * Which effect a committed life change plays (T28c; design/fx/README.md, "When effects trigger").
 * Pure; [FxComponent] calls it once per committed delta group with the group's **net** life change,
 * never per tap.
 *
 * [trigger] classifies: net 0 → none; a KO → [Trigger.KO]; |net| ≥ [bigHitThreshold] →
 * [Trigger.BIG_HIT] (loss) or [Trigger.BIG_HEAL] (gain); otherwise [Trigger.DAMAGE] / [Trigger.HEAL].
 * [Trigger.WIN] (last player standing) is asked for directly.
 *
 * Each trigger has a pool for a **full** seat and one for a **compact** seat (the drawer is open and
 * the number sits above it): tile-scale effects are drawn against the full tile's furniture, so the
 * compact pools hold only number-anchored ones. [choose] picks from the pool with [random], never the
 * effect [seat] played last (when the pool has another), so damage rotates instead of always being
 * the same. No allocation.
 */
class FxPolicy(
    var random: Random = Random(System.nanoTime()),
    seats: Int = 4,
    var bigHitThreshold: Int = 10,
) {
    enum class Trigger { DAMAGE, HEAL, BIG_HIT, BIG_HEAL, KO, WIN }

    /** Indexed by [Trigger.ordinal]: the pools for a full seat. */
    val full: Array<Array<Fx>> = Array(Trigger.entries.size) { DEFAULT_FULL[it].map(::fx).toTypedArray() }

    /** Indexed by [Trigger.ordinal]: the pools for a compact seat (no `anchor tile` effects). */
    val compact: Array<Array<Fx>> = Array(Trigger.entries.size) { DEFAULT_COMPACT[it].map(::fx).toTypedArray() }

    private val last = arrayOfNulls<Fx>(seats)

    /** The trigger for a committed net life change [netDelta] that KO'd the player when [ko], or null. */
    fun trigger(netDelta: Int, ko: Boolean = false): Trigger? = when {
        netDelta == 0 -> null
        ko && netDelta < 0 -> Trigger.KO
        netDelta < 0 && abs(netDelta) >= bigHitThreshold -> Trigger.BIG_HIT
        netDelta < 0 -> Trigger.DAMAGE
        netDelta >= bigHitThreshold -> Trigger.BIG_HEAL
        else -> Trigger.HEAL
    }

    /**
     * Whether [trigger] should play for a readout group that already played the triggers in [fired]
     * (bits `1 shl ordinal`), T55: once per band per group, again only when the running total escalates
     * into a bigger band of the same direction (a smaller one after a bigger one plays nothing).
     */
    fun escalates(trigger: Trigger, fired: Int): Boolean {
        if (fired and (1 shl trigger.ordinal) != 0) return false
        val fam = family(trigger)
        if (fam < 0) return true
        for (i in TRIGGERS.indices) {
            val t = TRIGGERS[i]
            if (fired and (1 shl t.ordinal) == 0) continue
            if (family(t) == fam && rank(t) >= rank(trigger)) return false
        }
        return true
    }

    private fun family(t: Trigger): Int = when (t) {
        Trigger.DAMAGE, Trigger.BIG_HIT -> 0
        Trigger.HEAL, Trigger.BIG_HEAL -> 1
        else -> -1
    }

    private fun rank(t: Trigger): Int = when (t) {
        Trigger.BIG_HIT, Trigger.BIG_HEAL -> 1
        else -> 0
    }

    /** The effect for [seat]'s committed net life change, or null to play nothing. */
    fun choose(seat: Int, netDelta: Int, ko: Boolean = false, compact: Boolean = false): Fx? =
        trigger(netDelta, ko)?.let { choose(seat, it, compact) }

    /** An effect from [trigger]'s pool for [seat], never the one it played last when there is another. */
    fun choose(seat: Int, trigger: Trigger, compact: Boolean = false): Fx? {
        val pool = (if (compact) this.compact else full)[trigger.ordinal]
        val n = pool.size
        if (n == 0) return null
        val prev = if (seat in last.indices) last[seat] else null
        var avoid = 0
        for (f in pool) if (f == prev) avoid++
        val pick = if (n == 1 || avoid == n) {
            pool[if (n == 1) 0 else random.nextInt(n)]
        } else {
            var i = random.nextInt(n - avoid)
            var chosen = pool[0]
            for (f in pool) {
                if (f == prev) continue
                if (i == 0) { chosen = f; break }
                i--
            }
            chosen
        }
        if (seat in last.indices) last[seat] = pick
        return pick
    }

    /** Forgets every seat's last pick (new game, player count change). */
    fun reset() = last.fill(null)

    /** Every effect any pool can pick (what to prewarm). */
    fun effects(): List<Fx> = (full.flatten() + compact.flatten()).distinct()

    companion object {
        private val TRIGGERS: Array<Trigger> = Trigger.values()

        private fun fx(name: String): Fx = requireNotNull(Fx.byName(name)) { "no effect '$name' in design/fx/" }

        /** Full-seat pools, by [Trigger] ordinal (DAMAGE, HEAL, BIG_HIT, BIG_HEAL, KO, WIN). */
        val DEFAULT_FULL: List<List<String>> = listOf(
            listOf("pow", "crack", "claw-cut", "quake"),
            listOf("twinkle", "rise", "sprout", "heart"),
            listOf("focus"),
            listOf("sunrise", "vines"),
            listOf("ko-shatter"),
            listOf("winner"),
        )

        /** Compact-seat pools: number-anchored effects only (`fizzle` is the compact KO). */
        val DEFAULT_COMPACT: List<List<String>> = listOf(
            listOf("pow", "crack", "claw-cut"),
            listOf("twinkle", "sprout", "heart"),
            listOf("pow", "crack", "claw-cut"),
            listOf("twinkle", "sprout", "heart"),
            listOf("fizzle"),
            listOf("twinkle", "sprout", "heart"),
        )
    }
}
