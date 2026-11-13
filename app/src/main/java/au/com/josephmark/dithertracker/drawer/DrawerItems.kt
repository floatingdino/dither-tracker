package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.commander.CommanderMode
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.dither.Icon

/**
 * What a drawer cell shows, as one packed int (no allocation): `kind shl 16 or arg`.
 *
 * - [COUNTER]: arg = `Counter.ordinal`.
 * - [TAX]: arg = commander slot ([Commander.PRIMARY] / [Commander.PARTNER]).
 *
 * The tray, in display order: commander tax (primary, then partner when the player has `partner`)
 * followed by the configured `drawerCounters` in their order. Tax goes first because it is used
 * every game. Commander damage is not a drawer item since T30 (see `play.CommanderMode`). The same
 * order is used by the closed-drawer counter peeks (T32).
 */
object DrawerItems {
    const val COUNTER = 1
    const val TAX = 2

    /** Largest tray list (7 counters + 2 tax), with room to spare. */
    const val MAX_ITEMS = 16

    /** Commander tax steps by 2 (each recast costs two more). */
    const val TAX_STEP = 2

    /** Hold-to-repeat amount for counters and commander damage (life uses the life hold step). */
    const val HOLD_STEP = 5

    /** Poison at or above this gets the inverted "nearly lethal" peek (lethal is 10, T32). */
    const val POISON_WARN = 8

    fun item(kind: Int, arg: Int): Int = (kind shl 16) or arg
    fun kind(item: Int): Int = item ushr 16
    fun arg(item: Int): Int = item and 0xFFFF

    private val counters = Counter.entries

    /** Fills [out] (from [offset]) with the tray items for [player]; returns the count. */
    fun tray(player: Player, shown: List<Counter>, out: IntArray, offset: Int = 0): Int {
        var n = 0
        out[offset + n++] = item(TAX, Commander.PRIMARY)
        if (player.partner) out[offset + n++] = item(TAX, Commander.PARTNER)
        for (i in shown.indices) {
            if (n >= MAX_ITEMS) break
            out[offset + n++] = item(COUNTER, shown[i].ordinal)
        }
        return n
    }

    /** Current value of [item] for [player] (the drawer's owner). */
    fun value(item: Int, player: Player): Int = when (kind(item)) {
        COUNTER -> player.counters[counters[arg(item)]]
        TAX -> player.commanderTax[arg(item)]
        else -> 0
    }

    /** Whether [value] of [item] is at or near lethal (poison ≥ [POISON_WARN]): drawn inverted. */
    fun isWarning(item: Int, value: Int): Boolean =
        kind(item) == COUNTER && arg(item) == Counter.POISON.ordinal && value >= POISON_WARN

    /** Whether holding the cell repeats (counters; tax is tapped once per cast). */
    fun repeats(item: Int): Boolean = kind(item) == COUNTER

    /** Signed change for a tap on [item]'s half ([DrawerZones.MINUS]/[DrawerZones.PLUS]). */
    fun tapDelta(item: Int, half: Int): Int {
        val step = if (kind(item) == TAX) TAX_STEP else 1
        return if (half == DrawerZones.PLUS) step else -step
    }

    /** Signed change for one hold repeat. */
    fun holdDelta(half: Int): Int = if (half == DrawerZones.PLUS) HOLD_STEP else -HOLD_STEP

    /** The core action for changing [item] of [player] by [delta]. */
    fun action(item: Int, player: Player, delta: Int): Action? = when (kind(item)) {
        COUNTER -> Action.ChangeCounter(player.id, counters[arg(item)], delta)
        TAX -> Action.ChangeCommanderTax(player.id, arg(item), delta)
        else -> null
    }

    private val COUNTER_ICONS = arrayOf(
        Icon.POISON, Icon.ENERGY, Icon.EXPERIENCE, Icon.RADS, Icon.STORM, Icon.TICKETS, Icon.GENERIC_COUNTER,
    )

    init {
        check(COUNTER_ICONS.size == Counter.entries.size)
    }

    /** The icon of a tray item (drawer cell, counter peek). */
    fun iconFor(item: Int): Icon = when (kind(item)) {
        COUNTER -> COUNTER_ICONS[arg(item)]
        TAX -> if (arg(item) == Commander.PARTNER) Icon.PARTNER else Icon.COMMANDER_TAX
        else -> Icon.COMMANDER
    }

    /** Every icon a tray item can show. */
    val TRAY_ICONS: Array<Icon> = COUNTER_ICONS + arrayOf(Icon.COMMANDER_TAX, Icon.PARTNER)
}
