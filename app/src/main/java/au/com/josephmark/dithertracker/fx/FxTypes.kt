package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.Tile

/** What an effect is drawn for, from its `meta.txt` `role` (see the generator in app/build.gradle.kts). */
enum class FxRole(val id: String) {
    DAMAGE("damage"),
    HEAL("heal"),
    BIG_HIT("big-hit"),
    KO("ko"),
    /** Last player standing (round two's `winner`). */
    WIN("win"),
}

/**
 * The reference point an effect's frame centre is anchored to (`anchor <ref> dx dy` in `meta.txt`).
 * [rx]/[ry] are -1 (left/top), 0 (centre) or +1 (right/bottom) of the reference box: the life total's
 * ink box for every ref except [TILE], whose box is the seat tile (round-two tile-scale frames).
 */
enum class FxAnchorRef(val id: String, val rx: Int, val ry: Int) {
    CENTRE("centre", 0, 0),
    TL("tl", -1, -1),
    TR("tr", 1, -1),
    BL("bl", -1, 1),
    BR("br", 1, 1),
    TOP("top", 0, -1),
    BOTTOM("bottom", 0, 1),
    LEFT("left", -1, 0),
    RIGHT("right", 1, 0),
    /** The centre of the seat's tile rect (`anchor tile dx dy`), not of the number. */
    TILE("tile", 0, 0),
}

/**
 * How a tile layer (frame symbols `1`–`9`) paints its mask, sampling its [Tile] at global screen
 * phase (SPEC rule 3). Matches `design/fx/options/render2.py`.
 */
enum class FxTileMode(val id: String) {
    /** Ink where the tile has ink; transparent elsewhere. */
    INK("ink"),
    /** Paper where the tile has ink; transparent elsewhere (ghosts the numeral). */
    PAPER("paper"),
    /** Ink where the tile has ink, paper elsewhere (opaque). */
    FILL("fill"),
}

/** One `tile <digit> <TILE> <mode>` declaration of an effect's `meta.txt`. */
class FxTileSpec(val digit: Int, val tile: Tile, val mode: FxTileMode) {
    override fun toString(): String = "tile $digit ${tile.name} ${mode.id}"
}
