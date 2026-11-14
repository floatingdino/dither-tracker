package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.core.serial.Tracker
import au.com.josephmark.dithertracker.dither.Icon

/**
 * What a drawer cell shows, as one packed int (no allocation): `kind shl 16 or arg`.
 *
 * - [COUNTER]: arg = `Counter.ordinal`.
 * - [TAX]: arg = commander slot ([Commander.PRIMARY] / [Commander.PARTNER]).
 * - [TOGGLE] (T14): arg = `Toggle.ordinal` (monarch, initiative, city's blessing, ring-bearer). Value
 *   1 when the player has it, else 0.
 * - [DAY_NIGHT] (T14): the table's day/night, the same cell in every seat's drawer. Value
 *   `DayNight.ordinal` (0 unset, 1 day, 2 night).
 *
 * The tray, in display order: commander tax (primary, then partner when the player has `partner`)
 * followed by the configured `drawerCounters` in their order, then (T14) the four toggles and
 * day/night. Tax goes first because it is used every game; the toggles come after the counters so the
 * counters keep page 1 at four players. Commander damage is not a drawer item since T30 (see
 * `commander.CommanderMode`). The closed-drawer counter peeks (T32) use the same order without the
 * toggles (those show as tile badges, `toggles.BadgesComponent`).
 *
 * T53: any of the trackers — tax, the four toggles, day/night — can be hidden in settings
 * (`AppSettings.hiddenTrackers`, passed here as its mask): a hidden one is simply not listed, so the
 * tray has fewer cells and pages. The game state is untouched (display only).
 *
 * A toggle cell is on/off, not `− icon value +`: a tap on either half flips it (an exclusive toggle
 * moves to this player); holding does not repeat. Day/night steps unset → day → night → day … on a tap
 * (once it has started it never goes back to unset in the rules) and a hold resets it to unset, like a
 * held − resets a counter.
 */
object DrawerItems {
    const val COUNTER = 1
    const val TAX = 2
    const val TOGGLE = 3
    const val DAY_NIGHT = 4

    /** Largest tray list (2 tax + 7 counters + 4 toggles + day/night = 14), with room to spare. */
    const val MAX_ITEMS = 16

    /** Commander tax steps by 2 (each recast costs two more). */
    const val TAX_STEP = 2

    /** Hold-to-repeat amount on a counter's + (life uses the life hold step; holding − resets, T43). */
    const val HOLD_STEP = 5

    /** Poison at or above this gets the inverted "nearly lethal" peek (lethal is 10, T32). */
    const val POISON_WARN = 8

    fun item(kind: Int, arg: Int): Int = (kind shl 16) or arg
    fun kind(item: Int): Int = item ushr 16
    fun arg(item: Int): Int = item and 0xFFFF

    private val counters = Counter.entries
    private val toggles = Toggle.entries

    /**
     * Fills [out] (from [offset]) with the tray items for [player]; returns the count. [withToggles]:
     * the drawer's list (the T14 toggles and day/night at the end); the peeks pass false.
     */
    fun tray(player: Player, shown: List<Counter>, out: IntArray, offset: Int = 0, withToggles: Boolean, hidden: Int = 0): Int {
        var n = 0
        if (!isHidden(hidden, Tracker.COMMANDER_TAX)) {
            out[offset + n++] = item(TAX, Commander.PRIMARY)
            if (player.partner) out[offset + n++] = item(TAX, Commander.PARTNER)
        }
        for (i in shown.indices) {
            if (n >= MAX_ITEMS) break
            out[offset + n++] = item(COUNTER, shown[i].ordinal)
        }
        if (withToggles) {
            for (i in toggles.indices) { // index loop: no iterator per refresh
                if (n < MAX_ITEMS && !isHidden(hidden, TOGGLE_TRACKERS[i])) out[offset + n++] = item(TOGGLE, toggles[i].ordinal)
            }
            if (n < MAX_ITEMS && !isHidden(hidden, Tracker.DAY_NIGHT)) out[offset + n++] = item(DAY_NIGHT, 0)
        }
        return n
    }

    /** The settings tracker of each [Toggle], by ordinal. */
    val TOGGLE_TRACKERS: Array<Tracker> = arrayOf(Tracker.MONARCH, Tracker.INITIATIVE, Tracker.CITYS_BLESSING, Tracker.RING_BEARER)

    /** Whether [t] is set in a hidden-trackers [mask] (`AppSettings.hiddenTrackerMask`). */
    fun isHidden(mask: Int, t: Tracker): Boolean = mask and (1 shl t.ordinal) != 0

    /** Whether toggle [t] is hidden in [mask]. */
    fun isHidden(mask: Int, t: Toggle): Boolean = isHidden(mask, TOGGLE_TRACKERS[t.ordinal])

    fun isToggle(item: Int): Boolean = kind(item) == TOGGLE || kind(item) == DAY_NIGHT

    /** Current value of [item] for [player] (the drawer's owner); day/night reads 0 here (see the 3-arg form). */
    fun value(item: Int, player: Player): Int = when (kind(item)) {
        COUNTER -> player.counters[counters[arg(item)]]
        TAX -> player.commanderTax[arg(item)]
        TOGGLE -> if (player.toggles[toggles[arg(item)]]) 1 else 0
        else -> 0
    }

    /** [value], with the table's [dayNight] for the day/night cell. */
    fun value(item: Int, player: Player, dayNight: DayNight): Int =
        if (kind(item) == DAY_NIGHT) dayNight.ordinal else value(item, player)

    /** The toggle of a [TOGGLE] item. */
    fun toggleOf(item: Int): Toggle = toggles[arg(item)]

    /** Day/night after a tap: unset → day, day → night, night → day. */
    fun nextDayNight(d: DayNight): DayNight = if (d == DayNight.DAY) DayNight.NIGHT else DayNight.DAY

    /** The core action for a tap on a toggle cell ([TOGGLE] flips, [DAY_NIGHT] steps), or null. */
    fun toggleAction(item: Int, player: Player, dayNight: DayNight): Action? = when (kind(item)) {
        TOGGLE -> toggleOf(item).let { Action.SetToggle(player.id, it, !player.toggles[it]) }
        DAY_NIGHT -> Action.SetDayNight(nextDayNight(dayNight))
        else -> null
    }

    /** Whether holding a toggle cell does anything: only day/night once set (the hold resets it). */
    fun toggleRepeats(item: Int, dayNight: DayNight): Boolean = kind(item) == DAY_NIGHT && dayNight != DayNight.UNSET

    /** Whether [value] of [item] is at or near lethal (poison ≥ [POISON_WARN]): drawn inverted. */
    fun isWarning(item: Int, value: Int): Boolean =
        kind(item) == COUNTER && arg(item) == Counter.POISON.ordinal && value >= POISON_WARN

    /** Whether holding the cell's + repeats (counters; tax is tapped once per cast). */
    fun repeats(item: Int): Boolean = kind(item) == COUNTER

    /** Signed change for a tap on [item]'s half ([DrawerZones.MINUS]/[DrawerZones.PLUS]). */
    fun tapDelta(item: Int, half: Int): Int {
        val step = if (kind(item) == TAX) TAX_STEP else 1
        return if (half == DrawerZones.PLUS) step else -step
    }

    /** Signed change for one hold repeat (only + repeats; a held − resets, see [resetAction]). */
    fun holdDelta(half: Int): Int = if (half == DrawerZones.PLUS) HOLD_STEP else -HOLD_STEP

    /** The core action for changing [item] of [player] by [delta]. */
    fun action(item: Int, player: Player, delta: Int): Action? = when (kind(item)) {
        COUNTER -> Action.ChangeCounter(player.id, counters[arg(item)], delta)
        TAX -> Action.ChangeCommanderTax(player.id, arg(item), delta)
        else -> null
    }

    /**
     * The core action taking [item] of [player] back to 0 in one step (holding −, T43): `SetCounter`
     * for a counter; tax has no setter, so it is the change by −value. Null when already 0.
     */
    fun resetAction(item: Int, player: Player): Action? {
        val v = value(item, player)
        if (v == 0) return null
        return when (kind(item)) {
            COUNTER -> Action.SetCounter(player.id, counters[arg(item)], 0)
            TAX -> Action.ChangeCommanderTax(player.id, arg(item), -v)
            else -> null
        }
    }

    private val COUNTER_ICONS = arrayOf(
        Icon.POISON, Icon.ENERGY, Icon.EXPERIENCE, Icon.RADS, Icon.STORM, Icon.TICKETS, Icon.GENERIC_COUNTER,
    )

    init {
        check(COUNTER_ICONS.size == Counter.entries.size)
    }

    /** Badge / drawer icon of each [Toggle], by ordinal. */
    val TOGGLE_ICONS: Array<Icon> = arrayOf(Icon.MONARCH, Icon.INITIATIVE, Icon.CITYS_BLESSING, Icon.RING)

    init {
        check(TOGGLE_ICONS.size == Toggle.entries.size)
        for (t in Toggle.entries) check(TOGGLE_TRACKERS[t.ordinal].name == t.name)
    }

    /** The icon of a tray item (drawer cell, counter peek); day/night shows the sun unless it is night ([value] 2). */
    fun iconFor(item: Int, value: Int = 0): Icon = when (kind(item)) {
        COUNTER -> COUNTER_ICONS[arg(item)]
        TAX -> if (arg(item) == Commander.PARTNER) Icon.PARTNER else Icon.COMMANDER_TAX
        TOGGLE -> TOGGLE_ICONS[arg(item)]
        DAY_NIGHT -> if (value == DayNight.NIGHT.ordinal) Icon.NIGHT else Icon.DAY
        else -> Icon.COMMANDER
    }

    /** Every icon a tray item can show. */
    val TRAY_ICONS: Array<Icon> = COUNTER_ICONS + arrayOf(Icon.COMMANDER_TAX, Icon.PARTNER) + TOGGLE_ICONS + arrayOf(Icon.DAY, Icon.NIGHT)
}
