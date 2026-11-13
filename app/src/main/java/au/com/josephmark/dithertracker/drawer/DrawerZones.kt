package au.com.josephmark.dithertracker.drawer

/**
 * Local touch-zone ids of the drawer component, per seat (T08; T34: the stage allocates the global
 * ids, so these are just `0 until PER_SEAT`). Plain ints so the press tracker stays allocation-free.
 *
 * - [TAB]: the open bar's close area (the closed tab is the `DrawerTab` component's own zone);
 * - [PREV] (back to the first page) / [NEXT]: the bar's page chevron;
 * - two ids per cell on the current page, `CELL0 + slot * 2 + half` (half 0 = the player's left =
 *   `−`, 1 = right = `+`), up to [CELL_END];
 * - [BUTTON]: the commander button in the bar's left segment (T30), which only sends
 *   `Events.COMMANDER_TARGET`; it is outside the `0 until CELL_END` range the drawer cancels when a
 *   page turns or the drawer opens or closes.
 *
 * A cell id names a *position on the current page*, not an item: the item is looked up when the tap
 * applies. Anything that changes what a position shows (page, mode, open/close) cancels the seat's
 * held drawer touches first (`Stage.cancelTouches`).
 */
object DrawerZones {
    /** Grid limits: at most this many columns × rows of cells in a drawer body. */
    const val MAX_COLS = 6
    const val MAX_ROWS = 2
    const val MAX_SLOTS = MAX_COLS * MAX_ROWS

    const val TAB = 0
    const val PREV = 1
    const val NEXT = 2
    const val CELL0 = 3
    const val CELL_END = CELL0 + 2 * MAX_SLOTS
    const val BUTTON = CELL_END
    const val PER_SEAT = BUTTON + 1

    const val MINUS = 0
    const val PLUS = 1

    /** Local id of [slot]'s [half] ([MINUS] / [PLUS]). */
    fun cell(slot: Int, half: Int): Int = CELL0 + slot * 2 + half

    fun isCell(local: Int): Boolean = local in CELL0 until CELL_END
    fun slotOf(local: Int): Int = (local - CELL0) / 2
    fun halfOf(local: Int): Int = (local - CELL0) % 2
}
