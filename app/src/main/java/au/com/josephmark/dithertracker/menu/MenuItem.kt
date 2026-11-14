package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.dither.Icon

/**
 * The eight tools of the table menu (T09, `design/menu/README.md`). [label] is the short `ui` caps
 * label (≤ 5 glyphs, the facets are small). [routed] items open another overlay through a
 * [MenuDestination] (dice/coin T16, starting player, history T18, settings T12); the rest are handled
 * by the menu itself.
 */
enum class MenuItem(val icon: Icon, val label: String, val routed: Boolean) {
    NEW(Icon.NEW_GAME, "NEW", false),
    UNDO(Icon.UNDO, "UNDO", false),
    REDO(Icon.REDO, "REDO", false),
    DICE(Icon.DICE, "DICE", true),
    FIRST(Icon.TURN, "FIRST", true),
    LOG(Icon.HISTORY, "LOG", true),
    SETUP(Icon.SETTINGS, "SETUP", true),
    CLEAN(Icon.REFRESH, "CLEAN", false),
}

/**
 * Where a routed [MenuItem] goes: another overlay (its own component, registered after the menu in
 * `PlayScreen`'s OVERLAY slot) implements this. The menu calls [open] and **stays open** underneath
 * (T51): the destination draws over the gem and its zones win; it must not close the menu, and when
 * it closes, redrawing its own rect repaints the gem below.
 *
 * [facing] is the rotation of the facet that was tapped — 0 (it faces the S edge), 180 (N), or
 * [GemLayout.FOLD] for a two-faced tip on the horizontal divider (it faces both long edges; a
 * two-faced "tent" result fits there, a one-way card should pick 0) — so a panel can open facing the
 * side that asked for it.
 */
fun interface MenuDestination {
    fun open(item: MenuItem, facing: Int)
}

/**
 * The seam later tasks plug into without editing `menu/`: `PlayScreen` keeps the menu component and
 * registers each destination after it, e.g.
 * ```
 * val menu = stage.add(MenuOverlay(random))
 * menu.destinations.register(MenuItem.DICE, stage.add(DiceOverlay(random)))
 * ```
 * A routed item with nothing registered is drawn disabled (`Role.DISABLED`) and takes no tap.
 */
class MenuDestinations {
    private val byItem = arrayOfNulls<MenuDestination>(MenuItem.entries.size)

    fun register(item: MenuItem, destination: MenuDestination) {
        require(item.routed) { "$item is handled by the menu itself" }
        byItem[item.ordinal] = destination
    }

    fun unregister(item: MenuItem) {
        byItem[item.ordinal] = null
    }

    operator fun get(item: MenuItem): MenuDestination? = byItem[item.ordinal]
}
