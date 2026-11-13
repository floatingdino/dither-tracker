package au.com.josephmark.dithertracker.dither

/**
 * One dither tile. Tiles repeat with their phase anchored to the global grid origin
 * (dither pixel (0,0)), so global pixel (gx, gy) is ink iff `isInk(gx mod width, gy mod height)`.
 *
 * `id` is a dense index into [Patterns.all] (used by the renderer's cache arrays).
 */
class Tile internal constructor(
    val id: Int,
    val name: String,
    width: Int,
    height: Int,
    rows: IntArray,
) : PackedImage(width, height, rows) {

    /** Ink coverage in 0..1. */
    val density: Double get() = inkCount.toDouble() / (width * height)

    /** Ink test in global dither coordinates (handles negatives). */
    fun isInkAt(gx: Int, gy: Int): Boolean = isInk(Math.floorMod(gx, width), Math.floorMod(gy, height))

    override fun toString(): String = name
}

/**
 * The fixed dither tile set — the single source of truth (mirrored as ASCII in `design/patterns/`
 * by `PatternMirrorTest`). UI code should use [Role], not tiles directly.
 *
 * - `D0`..`D16`: ordered 4×4 Bayer levels; `D[k]` has exactly k ink pixels per 16, and each level
 *   contains the previous one (monotone), so stepping through levels never "jumps".
 * - Named tiles are hand-tuned textures for things Bayer can't express.
 *   `CHECKER` is pixel-identical to `D8` and kept as an explicit name because the dotted
 *   divider (1 on / 1 off) is a 1-px-thick slice of it.
 */
object Patterns {
    /** Classic 4×4 Bayer threshold matrix (row-major). */
    private val BAYER4 = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5,
    )

    private val registry = ArrayList<Tile>()

    private fun reg(name: String, vararg ascii: String): Tile {
        val t = Tile(registry.size, name, ascii[0].length, ascii.size, PackedImage.pack(ascii))
        registry += t
        return t
    }

    private fun bayer(level: Int): Tile {
        val rows = Array(4) { y ->
            String(CharArray(4) { x -> if (BAYER4[y * 4 + x] < level) '#' else '.' })
        }
        return reg("D$level", *rows)
    }

    /** Bayer levels, `D[0]` = white … `D[16]` = black. */
    @JvmField val D: List<Tile> = List(17) { bayer(it) }

    /** 50% checkerboard (== D8). Also the source of 1-on/1-off dotted lines. */
    @JvmField val CHECKER = reg(
        "CHECKER",
        "#.",
        ".#",
    )

    /**
     * Drawer body: 6.25% dots on a near-equilateral triangular lattice (neighbour distances 4 and
     * √20≈4.5). Reads as an even, calm texture; lighter than any Bayer level except D1, and without
     * D1's square-grid look. Black text and buttons sit on it legibly.
     */
    @JvmField val SPARSE = reg(
        "SPARSE",
        "#...",
        "....",
        "....",
        "....",
        "..#.",
        "....",
        "....",
        "....",
    )

    /** 25% chunky 2×2 dots — a halftone look distinct from D4's single-pixel grid. */
    @JvmField val DOTS = reg(
        "DOTS",
        "##..",
        "##..",
        "....",
        "....",
    )

    /** 50% horizontal lines (1 on / 1 off). */
    @JvmField val HLINES = reg(
        "HLINES",
        "#",
        ".",
    )

    /** 50% vertical lines (1 on / 1 off). */
    @JvmField val VLINES = reg(
        "VLINES",
        "#.",
    )

    /** 25% diagonal hatch, rising left-to-right ("/"). */
    @JvmField val DIAG = reg(
        "DIAG",
        "...#",
        "..#.",
        ".#..",
        "#...",
    )

    /**
     * 25% X-hatch on an 8×8 cell. Added (not in the SPEC's proposed list) for the "dead" panel:
     * it reads as "struck out" rather than "shaded", so a KO'd seat can't be confused with a
     * player's chosen background texture.
     */
    @JvmField val CROSSHATCH = reg(
        "CROSSHATCH",
        "#......#",
        ".#....#.",
        "..#..#..",
        "...##...",
        "...##...",
        "..#..#..",
        ".#....#.",
        "#......#",
    )

    /** Every tile, indexed by [Tile.id]. */
    @JvmField val all: List<Tile> = registry.toList()

    /** Named (non-Bayer) tiles. */
    @JvmField val named: List<Tile> = all.subList(D.size, all.size)

    fun byName(name: String): Tile? = all.firstOrNull { it.name == name }

    /**
     * Textures a player may pick as their seat background (the 1-bit substitute for colours, T13).
     * All light enough for black numerals to stay legible.
     */
    @JvmField val playerBackgrounds: List<Tile> = listOf(D[0], SPARSE, D[1], D[2], DIAG, DOTS, D[4], HLINES, VLINES)
}

/**
 * Semantic fill roles. UI code draws roles; retuning the look is a one-line change here.
 */
enum class Role(val tile: Tile) {
    /** Default background (paper). */
    SURFACE(Patterns.D[0]),
    /** Solid ink. */
    INK(Patterns.D[16]),
    /** Drawer tab and drawer tab bar (mid dither, Figma). */
    TAB(Patterns.D[6]),
    /** Centre diamond menu button (mid dither, Figma). */
    MENU(Patterns.D[6]),
    /** Drawer body (light sparse dither, Figma). */
    DRAWER(Patterns.SPARSE),
    /** Unavailable control backdrop: diagonal hatch = "not available" by convention. */
    DISABLED(Patterns.DIAG),
    /** Selected item: solid ink; content on it is drawn inverted. */
    SELECTED(Patterns.D[16]),
    /** Auto-KO'd seat overlay. */
    DEAD(Patterns.CROSSHATCH),
    /** Dotted divider lines (a 1-px slice of a checkerboard = 1 on / 1 off, globally phased). */
    DOTTED(Patterns.CHECKER),
    /** Drop shadow of the hero numerals (T27): a 50% checker filled through each glyph's shadow mask. */
    SHADOW(Patterns.CHECKER),
}
