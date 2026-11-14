package au.com.josephmark.dithertracker.layout

/**
 * One way to seat players round the tablet (T11), as data: how many seats sit along the far long
 * edge ([top], content turned 180°), along the near long edge ([bottom], upright), and whether a
 * seat sits at each short end ([left] turned 90°, [right] turned 270°). Always described for a
 * *landscape* table (long axis horizontal); [Seating] turns it 90° for a portrait screen.
 *
 * Every seat gets the **same tile** (one player-frame size per layout, see [Seating]): what differs
 * between seats is only where the tile sits and which way it faces. Leftover dither px are margin.
 *
 * [diamond] says where the centre control sits ([DiamondAt]). The table of arrangements is
 * [Arrangements.ALL]; design notes and diagrams are in `design/layouts/README.md`.
 */
class Arrangement(
    /** Stable id (stored in `AppSettings.layout` to pick an alternate). */
    val id: String,
    val top: Int,
    val bottom: Int,
    val left: Boolean,
    val right: Boolean,
    val diamond: DiamondAt,
    /** Its name under the thumbnail in the settings' layout picker (T53): a word or two, `ui` face. */
    val label: String = id,
) {
    init {
        require(top >= 0 && bottom >= 0) { "negative band" }
    }

    val ends: Int get() = (if (left) 1 else 0) + (if (right) 1 else 0)

    /** Long-edge bands in use (0..2). */
    val rows: Int get() = (if (top > 0) 1 else 0) + (if (bottom > 0) 1 else 0)

    val players: Int get() = top + bottom + ends

    override fun toString(): String = "Arrangement($id: top $top, bottom $bottom${if (left) ", left end" else ""}${if (right) ", right end" else ""}, diamond $diamond)"

    /** Where the centre control (the diamond / the gem) sits. */
    enum class DiamondAt {
        /**
         * On the long divider, at the point where a vertical divider meets it that is nearest the
         * middle of the bands (where tiles meet at their corners); [CENTRE] when there is none.
         * With only a near band (everyone on one side, T53) there is no long divider: the diamond
         * sits on that divider at the band's far edge, or further down it when the band reaches the
         * screen's edge (far enough in for the open table menu), by the far corners of the tiles
         * either side.
         */
        JUNCTION,

        /** The middle of the long divider (or of the whole table when there is no long divider). */
        CENTRE,
    }
}

/**
 * The arrangement table (T11): every candidate, and the default per player count. Alternates stay
 * one table row away. T53: the settings' layout picker (`settings.LayoutPickerRow`) chooses one per
 * player count, stored in `AppSettings.layout` as a spec ([of], [choose]).
 */
object Arrangements {

    /** Every arrangement, grouped by count in the layout picker's order (T53 added the one-sided and end-seat ones). */
    val ALL: List<Arrangement> = listOf(
        // id                  top bottom  left   right  diamond                              picker label
        Arrangement("2-across", 1, 1, false, false, Arrangement.DiamondAt.CENTRE, "Across"),
        Arrangement("2-ends", 0, 0, true, true, Arrangement.DiamondAt.CENTRE, "Ends"),
        Arrangement("2-side", 0, 2, false, false, Arrangement.DiamondAt.JUNCTION, "Side by side"),
        Arrangement("2-corner", 0, 1, false, true, Arrangement.DiamondAt.JUNCTION, "Corner"),
        Arrangement("3-tee", 1, 1, false, true, Arrangement.DiamondAt.JUNCTION, "Tee"),
        Arrangement("3-pyramid", 1, 2, false, false, Arrangement.DiamondAt.JUNCTION, "Pyramid"),
        Arrangement("3-side", 0, 3, false, false, Arrangement.DiamondAt.JUNCTION, "One side"),
        Arrangement("3-end", 0, 2, false, true, Arrangement.DiamondAt.JUNCTION, "Two and an end"),
        Arrangement("4-grid", 2, 2, false, false, Arrangement.DiamondAt.JUNCTION, "Grid"),
        Arrangement("4-dinner", 1, 1, true, true, Arrangement.DiamondAt.CENTRE, "Dinner"),
        Arrangement("5-three-two", 3, 2, false, false, Arrangement.DiamondAt.JUNCTION, "Three and two"),
        Arrangement("5-end", 2, 2, false, true, Arrangement.DiamondAt.JUNCTION, "Two, two, end"),
        Arrangement("6-three-three", 3, 3, false, false, Arrangement.DiamondAt.CENTRE, "Three a side"),
        Arrangement("6-dinner", 2, 2, true, true, Arrangement.DiamondAt.CENTRE, "Dinner"),
    )

    /** The shipped pick per player count (reasons in `design/layouts/README.md`). */
    private val DEFAULTS = mapOf(
        2 to "2-across",
        3 to "3-tee",
        4 to "4-grid",
        5 to "5-three-two",
        6 to "6-three-three",
    )

    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 6

    fun byId(id: String): Arrangement? = ALL.firstOrNull { it.id == id }

    /** The default arrangement for [players]. */
    fun default(players: Int): Arrangement = byId(DEFAULTS[players] ?: error("no layout for $players players"))!!

    /**
     * The arrangement for [players] in a layout [spec] (`AppSettings.layout`): the spec's id with that
     * many seats (an alternate), otherwise the default. A spec holds one id per player count that is
     * not on its default, comma-separated ("3-pyramid,5-end", T53; a single id, as T11 stored it, is
     * a one-entry spec). Unknown ids are ignored. Allocation-free.
     */
    fun of(players: Int, spec: String = ""): Arrangement {
        var start = 0
        while (start < spec.length) {
            var end = spec.indexOf(SEP, start)
            if (end < 0) end = spec.length
            for (a in ALL) if (a.players == players && a.id.length == end - start && spec.startsWith(a.id, start)) return a
            start = end + 1
        }
        return default(players)
    }

    /** [spec] with [players]' arrangement set to [id] (dropped from the spec when it is the default), in count order. */
    fun choose(spec: String, players: Int, id: String): String {
        val a = byId(id)
        require(a != null && a.players == players) { "$id does not seat $players" }
        val sb = StringBuilder()
        for (n in MIN_PLAYERS..MAX_PLAYERS) {
            val pick = if (n == players) a else of(n, spec)
            if (pick.id == DEFAULTS[n]) continue
            if (sb.isNotEmpty()) sb.append(SEP)
            sb.append(pick.id)
        }
        return sb.toString()
    }

    /** Every arrangement for [players], the default first, then in table order (the picker's order). */
    fun candidates(players: Int): List<Arrangement> =
        listOf(default(players)) + ALL.filter { it.players == players && it.id != DEFAULTS[players] }

    private const val SEP = ','
}
