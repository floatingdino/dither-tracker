package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.commander.CommanderZones
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.stage.PlayInput

/**
 * The Play screen's touch-zone id space (T08), shared by [PlayLayout] life zones and the drawers.
 * Plain ints so `PlayInput` stays allocation-free.
 *
 * - `0 until LIFE` — life zones, `seat * 2 + side` exactly as [PlayLayout.zone].
 * - `LIFE until COUNT` — one block of [PER_SEAT] ids per seat: [TAB] (the closed tab, or the open
 *   bar's close area), [PREV] (back to the first page) / [NEXT] (the bar's page chevron), then two
 *   ids per cell on the current page:
 *   `CELL0 + slot * 2 + half` (half 0 = the player's left = `−`, 1 = right = `+`).
 * - `COUNT until` [CommanderZones][au.com.josephmark.dithertracker.commander.CommanderZones]`.END`: the
 *   commander-damage button and mode zones (T30).
 *
 * A cell id names a *position on the current page*, not an item: the item is looked up when the tap
 * applies. Anything that changes what a position shows (page, mode, open/close) cancels the seat's
 * held drawer touches first (see `PlayInput.cancelZones`).
 */
object DrawerZones {
    const val PLAYERS = PlayLayout.PLAYERS
    const val LIFE = PLAYERS * 2

    /** Grid limits: at most this many columns × rows of cells in a drawer body. */
    const val MAX_COLS = 6
    const val MAX_ROWS = 2
    const val MAX_SLOTS = MAX_COLS * MAX_ROWS

    const val TAB = 0
    const val PREV = 1
    const val NEXT = 2
    const val CELL0 = 3
    const val CELL_END = CELL0 + 2 * MAX_SLOTS

    /** The commander button in the open bar's left segment (T30): sends `Events.COMMANDER_TARGET`. */
    const val BUTTON = CELL_END
    const val PER_SEAT = BUTTON + 1

    /** Total number of zone ids. */
    const val COUNT = LIFE + PLAYERS * PER_SEAT

    const val MINUS = 0
    const val PLUS = 1

    fun zone(seat: Int, local: Int): Int = LIFE + seat * PER_SEAT + local
    fun cellZone(seat: Int, slot: Int, half: Int): Int = zone(seat, CELL0 + slot * 2 + half)

    fun isLife(zone: Int): Boolean = zone in 0 until LIFE
    fun isDrawer(zone: Int): Boolean = zone in LIFE until COUNT

    /** Seat of any zone (life or drawer). */
    fun seatOf(zone: Int): Int = if (zone < LIFE) zone / 2 else (zone - LIFE) / PER_SEAT

    /** Local id within the seat's drawer block (drawer zones only). */
    fun localOf(zone: Int): Int = (zone - LIFE) % PER_SEAT

    fun isCell(local: Int): Boolean = local in CELL0 until CELL_END
    fun slotOf(local: Int): Int = (local - CELL0) / 2
    fun halfOf(local: Int): Int = (local - CELL0) % 2

    /** First and one-past-last zone id of [seat]'s drawer block (tab, page, cells; not [BUTTON]). */
    fun seatFirst(seat: Int): Int = zone(seat, 0)
    fun seatEnd(seat: Int): Int = zone(seat, CELL_END)
}
