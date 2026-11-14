package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.BitMask
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Patterns
import au.com.josephmark.dithertracker.dither.Tile

/**
 * One presentation sprite (T52) at glyph scale [k], split by symbol into masks, upright and turned
 * 180° (built once, off the draw path: the renderer caches one scaled mask per instance, so drawing
 * never allocates). Symbols as `DiceSprites`: paper (`o`), tile layers (`1` D4, `2` D8, `3` D12,
 * `v` VLINES, at global phase) and ink (`#`), drawn in that order; `.` is left alone.
 */
internal class Sprite(rows: Array<String>, val k: Int, allTurns: Boolean = false) {
    /** Upright size (at rotation 0 / 180; turned a quarter it is [height] × [width]). */
    val width: Int = rows[0].length * k
    val height: Int = rows.size * k

    private val paper = arrayOfNulls<MonoImage>(4)
    private val ink = arrayOfNulls<MonoImage>(4)
    private val tiles = arrayOfNulls<MonoImage>(4 * TILE_CODES.length)

    init {
        for (q in 0 until 4) {
            if (!allTurns && q % 2 == 1) continue
            paper[q] = mask(rows, 'o', q)
            ink[q] = mask(rows, '#', q)
            for (t in TILE_CODES.indices) tiles[q * TILE_CODES.length + t] = mask(rows, TILE_CODES[t], q)
        }
    }

    /** The symbol [c]'s mask turned [q] quarter turns clockwise, or null when the sprite has none. */
    private fun mask(rows: Array<String>, c: Char, q: Int): MonoImage? {
        val w = if (q % 2 == 0) width else height
        val h = if (q % 2 == 0) height else width
        val bits = BooleanArray(w * h)
        var any = false
        for (y in 0 until h) for (x in 0 until w) {
            val sx: Int
            val sy: Int
            when (q) {
                0 -> { sx = x; sy = y }
                1 -> { sx = y; sy = height - 1 - x }
                2 -> { sx = width - 1 - x; sy = height - 1 - y }
                else -> { sx = width - 1 - y; sy = x }
            }
            if (rows[sy / k][sx / k] == c) { bits[y * w + x] = true; any = true }
        }
        return if (any) BitMask(w, h, bits) else null
    }

    /** Width / height on screen at [rotation]. */
    fun widthAt(rotation: Int): Int = if (rotation == 90 || rotation == 270) height else width
    fun heightAt(rotation: Int): Int = if (rotation == 90 || rotation == 270) width else height

    /**
     * Draws the sprite with its (turned) top-left at (x, y); [rotation] 0 / 180, or 90 / 270 when built
     * with all turns. Allocation-free.
     */
    fun draw(r: DitherRenderer, x: Int, y: Int, rotation: Int) {
        val q = (rotation / 90) and 3
        paper[q]?.let { r.blit(it, x, y, invert = true) }
        for (t in TILE_CODES.indices) tiles[q * TILE_CODES.length + t]?.let { r.fillMask(it, x, y, TILE[t]) }
        ink[q]?.let { r.blit(it, x, y) }
    }

    companion object {
        const val TILE_CODES = "123v"
        val TILE: Array<Tile> = arrayOf(Patterns.D[4], Patterns.CHECKER, Patterns.D[12], Patterns.VLINES)
    }
}

/** Every sprite of a presentation sprite [table] (shared by a slow and a fast [Presentation]) at scale [k]. */
internal class SpriteSet(val table: Array<Array<String>>, val k: Int) {
    val sprites: Array<Sprite> = Array(table.size) { Sprite(table[it], k) }
    val widths = IntArray(sprites.size) { sprites[it].width }
    val heights = IntArray(sprites.size) { sprites[it].height }
}

/**
 * Debug hooks for the table tools (T52), set by `MainActivity` from intent extras (debuggable builds
 * only): `--ef diceHold X` scales every presentation hold (dice, coin, first player) by X, so a
 * screencap can land in each keyframe.
 */
object ToolsDebug {
    @JvmField var holdScale: Float = 1f

    fun hold(ms: Long): Long = (ms * holdScale).toLong()
}
