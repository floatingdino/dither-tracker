package au.com.josephmark.dithertracker.commander

import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.CommanderSlot
import au.com.josephmark.dithertracker.play.PlayLayout

/**
 * Local touch-zone ids of commander-damage mode's tile component (T30; T34: per seat, the stage
 * allocates the global ids). Declared only while the mode is on:
 * - [A_MINUS]/[A_PLUS]: the source's primary commander − / + (the tile's halves, or its left two
 *   quarters when the source has a partner); [B_MINUS]/[B_PLUS]: the partner commander's quarters;
 * - [TARGET]: anywhere on the targeted player's own tile (a tap leaves the mode);
 * - [PARTNER]: every tile's PARTNER tab (T52), on the drawer tab's spot (its hit pad too); over the others.
 * The close X on the diamond is `CommanderClose`'s one zone; the entry point is the drawer bar's
 * button (`Events.COMMANDER_TARGET`).
 */
object CommanderZones {
    const val A_MINUS = 0
    const val A_PLUS = 1
    const val B_MINUS = 2
    const val B_PLUS = 3
    const val TARGET = 4
    const val PARTNER = 5
    const val PER_SEAT = 6

    /** Local id of slot [slot]'s − (side 0) or + (side 1). */
    fun slotLocal(slot: CommanderSlot, side: Int): Int = if (slot == Commander.PARTNER) B_MINUS + side else A_MINUS + side

    fun isSlot(local: Int): Boolean = local in A_MINUS..B_PLUS

    /** Commander slot of a slot zone's local id. */
    fun slotOf(local: Int): CommanderSlot = if (local >= B_MINUS) Commander.PARTNER else Commander.PRIMARY

    /** Side of a slot zone's local id: [PlayLayout.MINUS] or [PlayLayout.PLUS]. */
    fun sideOf(local: Int): Int = (local - A_MINUS) % 2
}
