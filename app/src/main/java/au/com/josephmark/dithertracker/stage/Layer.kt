package au.com.josephmark.dithertracker.stage

/**
 * The draw/hit passes of the Play screen, bottom to top (see `docs/architecture.md`). A component
 * lives in exactly one layer. The host draws layer by layer and, after each layer, inverts the
 * pressed zones *owned by that layer's components* — so a press inverts everything drawn up to its
 * own layer and nothing drawn above it (a pressed life half is inverted under the closed tab, as
 * before T34). Hit-testing runs the other way: the top layer wins.
 *
 * - [TILE] and [TILE_CHROME] components are per seat: drawn once per seat, in the player's frame
 *   (rotation comes from the layout's mapping; never `Canvas.rotate`).
 * - [BACKDROP], [SCREEN] and [OVERLAY] components are drawn once, with seat [Stage.SCREEN].
 */
object Layer {
    /** Table-wide, under every tile: the dotted dividers. */
    const val BACKDROP = 0

    /** Per-seat content: life, commander-mode content, readout, peeks, drawer; effects and KO slots. */
    const val TILE = 1

    /** Per-seat chrome drawn over the content (and over its press inversions): the closed drawer tab. */
    const val TILE_CHROME = 2

    /** Table-wide controls: the centre diamond (menu), the commander-mode close X. */
    const val SCREEN = 3

    /** Table-wide overlays that take over input for a region or the whole screen: menu, settings, confirm. */
    const val OVERLAY = 4

    const val COUNT = 5

    fun isTile(layer: Int): Boolean = layer == TILE || layer == TILE_CHROME
}

/**
 * Visibility classes, a bit each. A [Cover] hides every component whose [Component.kind] is in its
 * mask on the seats (and/or the screen) it covers: the component is neither drawn nor hit-tested
 * there. Kinds are about *what a thing is to the table*, not which feature built it, so a new overlay
 * can say "hide the tiles' content and the chrome" without knowing which components exist.
 */
object Kind {
    /** Dividers and other table furniture that stays under everything. */
    const val BACKDROP = 1 shl 0

    /** A seat's normal content: life total and ±, counter peeks, the drawer. */
    const val CONTENT = 1 shl 1

    /** The pending-delta readout (kept visible by commander mode: damage and linked life use it). */
    const val READOUT = 1 shl 2

    /** Chrome that opens something: drawer tabs, the centre menu diamond. */
    const val CHROME = 1 shl 3

    /** A mode's own content (commander-damage mode): shown only while its mode is on. */
    const val MODE = 1 shl 4

    /** Short keyframe effects over the content (T28c). */
    const val EFFECT = 1 shl 5

    /** Full overlays (menu, settings, history, confirm prompts). */
    const val OVERLAY = 1 shl 6

    /** Everything except [OVERLAY]: what a full-screen overlay hides beneath it. */
    const val ALL_BELOW_OVERLAY = BACKDROP or CONTENT or READOUT or CHROME or MODE or EFFECT
}
