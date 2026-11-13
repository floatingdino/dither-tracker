package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.Limits
import au.com.josephmark.dithertracker.core.Player

/**
 * What a drawer cell shows, as one packed int (no allocation): `kind shl 16 or arg`.
 *
 * - [COUNTER]: arg = `Counter.ordinal`.
 * - [TAX]: arg = commander slot ([Commander.PRIMARY] / [Commander.PARTNER]).
 * - [DAMAGE]: arg = `sourceSeat * 2 + slot` — damage *received* from that opponent's commander.
 *
 * Item lists per view (in display order):
 * - tray: commander tax (primary, then partner when the player has `partner`) followed by the
 *   configured `drawerCounters` in their order. Tax goes first because it sits next to the
 *   COMMANDER DAMAGE button (both are about commanders) and is used every game.
 * - commander: every opponent clockwise from the player (seat + 1, seat + 2, …), primary commander,
 *   then the partner slot when that opponent has `partner`.
 */
object DrawerItems {
    const val COUNTER = 1
    const val TAX = 2
    const val DAMAGE = 3

    /** Largest list either view can produce (5 opponents × 2 commanders; 7 counters + 2 tax). */
    const val MAX_ITEMS = 16

    /** Commander tax steps by 2 (each recast costs two more). */
    const val TAX_STEP = 2

    /** Hold-to-repeat amount for counters and commander damage (life uses the life hold step). */
    const val HOLD_STEP = 5

    fun item(kind: Int, arg: Int): Int = (kind shl 16) or arg
    fun kind(item: Int): Int = item ushr 16
    fun arg(item: Int): Int = item and 0xFFFF

    fun damageSource(item: Int): Int = arg(item) / 2
    fun damageSlot(item: Int): Int = arg(item) % 2

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

    /** Fills [out] with the commander-damage items for the player at [seat]; returns the count. */
    fun commander(seat: Int, players: List<Player>, out: IntArray, offset: Int = 0): Int {
        var n = 0
        val count = players.size
        for (d in 1 until count) {
            val src = (seat + d) % count
            out[offset + n++] = item(DAMAGE, src * 2 + Commander.PRIMARY)
            if (players[src].partner && n < MAX_ITEMS) out[offset + n++] = item(DAMAGE, src * 2 + Commander.PARTNER)
        }
        return n
    }

    /** Current value of [item] for [player] (the drawer's owner). */
    fun value(item: Int, player: Player, players: List<Player>): Int = when (kind(item)) {
        COUNTER -> player.counters[counters[arg(item)]]
        TAX -> player.commanderTax[arg(item)]
        DAMAGE -> {
            val src = damageSource(item)
            if (src in players.indices) player.commanderDamageFrom(players[src].id, damageSlot(item)) else 0
        }
        else -> 0
    }

    /** Whether the value deserves the inverted "lethal" treatment (commander damage ≥ 21). */
    fun isLethal(item: Int, value: Int): Boolean = kind(item) == DAMAGE && value >= Limits.LETHAL_COMMANDER_DAMAGE

    /** Whether holding the cell repeats (counters and damage; tax is tapped once per cast). */
    fun repeats(item: Int): Boolean = kind(item) == COUNTER || kind(item) == DAMAGE

    /** Signed change for a tap on [item]'s half ([DrawerZones.MINUS]/[DrawerZones.PLUS]). */
    fun tapDelta(item: Int, half: Int): Int {
        val step = if (kind(item) == TAX) TAX_STEP else 1
        return if (half == DrawerZones.PLUS) step else -step
    }

    /** Signed change for one hold repeat. */
    fun holdDelta(half: Int): Int = if (half == DrawerZones.PLUS) HOLD_STEP else -HOLD_STEP

    /**
     * The core action for changing [item] of [player] by [delta]. Commander damage passes
     * `linkLife = null`, so the game's "commander damage affects life" setting decides.
     */
    fun action(item: Int, player: Player, players: List<Player>, delta: Int): Action? = when (kind(item)) {
        COUNTER -> Action.ChangeCounter(player.id, counters[arg(item)], delta)
        TAX -> Action.ChangeCommanderTax(player.id, arg(item), delta)
        DAMAGE -> {
            val src = damageSource(item)
            if (src in players.indices) Action.ChangeCommanderDamage(player.id, players[src].id, damageSlot(item), delta, linkLife = null) else null
        }
        else -> null
    }
}
