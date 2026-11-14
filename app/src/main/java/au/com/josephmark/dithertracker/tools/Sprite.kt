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
internal class Sprite(rows: Array<String>, val k: Int) {
    val width: Int = rows[0].length * k
    val height: Int = rows.size * k

    private val paper = arrayOfNulls<MonoImage>(2)
    private val ink = arrayOfNulls<MonoImage>(2)
    private val tiles = arrayOfNulls<MonoImage>(2 * TILE_CODES.length)

    init {
        for (rot in 0..1) {
            paper[rot] = mask(rows, 'o', rot == 1)
            ink[rot] = mask(rows, '#', rot == 1)
            for (t in TILE_CODES.indices) tiles[rot * TILE_CODES.length + t] = mask(rows, TILE_CODES[t], rot == 1)
        }
    }

    private fun mask(rows: Array<String>, c: Char, turned: Boolean): MonoImage? {
        val bits = BooleanArray(width * height)
        var any = false
        for (y in 0 until height) for (x in 0 until width) {
            val sx = if (turned) width - 1 - x else x
            val sy = if (turned) height - 1 - y else y
            if (rows[sy / k][sx / k] == c) { bits[y * width + x] = true; any = true }
        }
        return if (any) BitMask(width, height, bits) else null
    }

    /** Draws the sprite with its top-left at (x, y); [rotation] 0 or 180. Allocation-free. */
    fun draw(r: DitherRenderer, x: Int, y: Int, rotation: Int) {
        val rot = if (rotation == 180) 1 else 0
        paper[rot]?.let { r.blit(it, x, y, invert = true) }
        for (t in TILE_CODES.indices) tiles[rot * TILE_CODES.length + t]?.let { r.fillMask(it, x, y, TILE[t]) }
        ink[rot]?.let { r.blit(it, x, y) }
    }

    companion object {
        const val TILE_CODES = "123v"
        val TILE: Array<Tile> = arrayOf(Patterns.D[4], Patterns.CHECKER, Patterns.D[12], Patterns.VLINES)
    }
}

/** Every sprite of a [Presentation] at scale [k]. */
internal class SpriteSet(val presentation: Presentation, val k: Int) {
    val sprites: Array<Sprite> = Array(presentation.sprites.size) { Sprite(presentation.sprites[it], k) }
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
