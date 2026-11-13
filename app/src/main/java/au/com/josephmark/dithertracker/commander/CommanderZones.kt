package au.com.josephmark.dithertracker.commander

import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.CommanderSlot
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.stage.PlayInput

/**
 * Touch-zone ids of commander-damage mode (T30), appended after the drawer block
 * ([DrawerZones.COUNT]) so the whole Play screen keeps one flat int id space for `PlayInput`.
 *
 * Per seat ([PER_SEAT] ids from [zone]`(seat, 0)`):
 * - [BUTTON]: the commander button in the tile's corner (live in and out of the mode);
 * - [A_MINUS]/[A_PLUS]: the source's primary commander − / + (the tile's halves, or its left two
 *   quarters when the source has a partner); [B_MINUS]/[B_PLUS]: the partner commander's quarters;
 * - [TARGET]: anywhere on the targeted player's own tile (a tap leaves the mode).
 *
 * Then one global id, [DIAMOND] (the centre diamond while the mode is on: leaves it; otherwise the
 * diamond stays `PlayLayout.RESERVED` for the table menu, T09).
 */
object CommanderZones {
    const val PLAYERS = PlayLayout.PLAYERS
    const val BASE = DrawerZones.COUNT

    const val BUTTON = 0
    const val A_MINUS = 1
    const val A_PLUS = 2
    const val B_MINUS = 3
    const val B_PLUS = 4
    const val TARGET = 5
    const val PER_SEAT = 6

    const val DIAMOND = BASE + PLAYERS * PER_SEAT

    /** One past the last id: the size of the Play screen's zone space. */
    const val END = DIAMOND + 1

    fun zone(seat: Int, local: Int): Int = BASE + seat * PER_SEAT + local

    /** Zone of slot [slot]'s − (side 0) or + (side 1) on [seat]'s tile. */
    fun slotZone(seat: Int, slot: CommanderSlot, side: Int): Int =
        zone(seat, if (slot == Commander.PARTNER) B_MINUS + side else A_MINUS + side)

    fun isCommander(zone: Int): Boolean = zone in BASE until END

    /** Seat of a per-seat commander zone (not [DIAMOND]). */
    fun seatOf(zone: Int): Int = (zone - BASE) / PER_SEAT

    fun localOf(zone: Int): Int = (zone - BASE) % PER_SEAT

    fun isSlot(local: Int): Boolean = local in A_MINUS..B_PLUS

    /** Commander slot of a slot zone's local id. */
    fun slotOf(local: Int): CommanderSlot = if (local >= B_MINUS) Commander.PARTNER else Commander.PRIMARY

    /** Side of a slot zone's local id: [PlayLayout.MINUS] or [PlayLayout.PLUS]. */
    fun sideOf(local: Int): Int = (local - A_MINUS) % 2
}
