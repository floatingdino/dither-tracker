package au.com.josephmark.dithertracker.fx

import kotlin.math.abs

/**
 * Which effect a committed life change plays (design/fx/README.md, "When effects should trigger").
 * Pure; call it once per committed delta group with the group's **net** delta, never per tap.
 *
 * Classification ([trigger]): net 0 → none; a KO → [Trigger.KO]; a loss of ≥ [bigHitThreshold] →
 * [Trigger.BIG_HIT]; any other loss → [Trigger.DAMAGE]; any gain → [Trigger.HEAL].
 * The effect comes from [table]; a trigger with no effect falls back along [Trigger.fallback]
 * (KO → BIG_HIT → DAMAGE), so adding `wham`/`fizzle` later is just `table[...] = Fx.byName(...)`.
 */
class FxPolicy(
    /** Indexed by [Trigger.ordinal]. Defaults: damage → `pow`, heal → `twinkle`, the rest unmapped. */
    val table: Array<Fx?> = defaultTable(),
    var bigHitThreshold: Int = 10,
) {
    enum class Trigger(val fallback: Trigger?) {
        DAMAGE(null),
        HEAL(null),
        BIG_HIT(DAMAGE),
        KO(BIG_HIT),
    }

    init {
        require(table.size == Trigger.entries.size) { "table needs ${Trigger.entries.size} entries" }
    }

    operator fun set(trigger: Trigger, fx: Fx?) { table[trigger.ordinal] = fx }
    operator fun get(trigger: Trigger): Fx? = table[trigger.ordinal]

    /** The trigger for a committed net life change [netDelta] (and whether it KO'd the player), or null. */
    fun trigger(netDelta: Int, ko: Boolean = false): Trigger? = when {
        netDelta == 0 -> null
        ko && netDelta < 0 -> Trigger.KO
        netDelta < 0 && abs(netDelta) >= bigHitThreshold -> Trigger.BIG_HIT
        netDelta < 0 -> Trigger.DAMAGE
        else -> Trigger.HEAL
    }

    /** The effect to play for a committed net life change, or null to play nothing. */
    fun choose(netDelta: Int, ko: Boolean = false): Fx? {
        var t = trigger(netDelta, ko)
        while (t != null) {
            table[t.ordinal]?.let { return it }
            t = t.fallback
        }
        return null
    }

    companion object {
        fun defaultTable(): Array<Fx?> = arrayOfNulls<Fx>(Trigger.entries.size).also {
            it[Trigger.DAMAGE.ordinal] = Fx.byName("pow")
            it[Trigger.HEAL.ordinal] = Fx.byName("twinkle")
        }
    }
}
