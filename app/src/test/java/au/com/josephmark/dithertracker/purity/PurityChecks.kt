package au.com.josephmark.dithertracker.purity

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Patterns
import java.io.File

/**
 * The checks the purity suite runs on every render (T22). On the JVM the raster can only hold ink or
 * paper and every coordinate is an integer dither px, so "two colours" and "edges on the S-grid" hold
 * by construction of the `DitherRenderer` API; what *can* go wrong in our code, and is checked here:
 *
 * - **Out of grid**: a draw that reaches outside the dither grid — on the device it lands in the
 *   margin (`check_1bit.py`: "margins are not a single colour") or off-screen.
 * - **Pattern phase**: an area that is exactly a dither tile at a phase other than the global one
 *   (a pattern baked into an image and blitted, a fill drawn through a shifted mask...). Scanned on
 *   the 8×8 lattice anchored at the grid origin (every tile's size divides 8): a window whose content
 *   equals a tile (or its inverse) shifted off phase, and no tile at the global phase, is an error.
 * - **Dark mode is an exact complement**: the inverted render is the normal render with ink and
 *   paper swapped, pixel for pixel (nothing hard-codes a colour).
 * - **Clips balance**: every `clipTo` is paired with an `unclip` within the frame.
 */
object PurityChecks {

    const val CELL = 8

    /** 8×8 window keys of every tile and its inverse at the global phase. */
    private val onPhase = HashSet<Long>()

    /** 8×8 window keys of a tile (or its inverse) shifted off the global phase → description. */
    private val offPhase = HashMap<Long, String>()

    init {
        for (t in Patterns.all) {
            require(CELL % t.width == 0 && CELL % t.height == 0) { "tile $t (${t.width}x${t.height}) does not divide the $CELL px lattice" }
            val k = key { x, y -> t.isInkAt(x, y) }
            onPhase += k
            onPhase += k.inv()
        }
        for (t in Patterns.all) {
            if (t.inkCount == 0 || t.inkCount == t.width * t.height) continue
            for (dy in 0 until t.height) for (dx in 0 until t.width) {
                if (dx == 0 && dy == 0) continue
                val k = key { x, y -> t.isInkAt(x + dx, y + dy) }
                if (k !in onPhase) offPhase.putIfAbsent(k, "$t shifted ($dx,$dy)")
                if (k.inv() !in onPhase) offPhase.putIfAbsent(k.inv(), "inverted $t shifted ($dx,$dy)")
            }
        }
    }

    private inline fun key(ink: (Int, Int) -> Boolean): Long {
        var k = 0L
        for (y in 0 until CELL) for (x in 0 until CELL) if (ink(x, y)) k = k or (1L shl (y * CELL + x))
        return k
    }

    /** Off-phase pattern windows in [r]: "(x,y) <tile> shifted (dx,dy)" per window, at most [max]. */
    fun phaseErrors(r: RasterRenderer, max: Int = 5): List<String> {
        val out = ArrayList<String>()
        var n = 0
        var y0 = 0
        while (y0 + CELL <= r.height) {
            var x0 = 0
            while (x0 + CELL <= r.width) {
                var k = 0L
                for (y in 0 until CELL) {
                    val row = (y0 + y) * r.width + x0
                    for (x in 0 until CELL) if (r.px[row + x] == RasterRenderer.INK) k = k or (1L shl (y * CELL + x))
                }
                val what = offPhase[k]
                if (what != null && n++ < max) out += "($x0,$y0) $what"
                x0 += CELL
            }
            y0 += CELL
        }
        if (n > max) out += "... ${n - max} more"
        return out
    }

    /** Where [inv] is not the exact complement of [normal]: count and the first pixel, or null. */
    fun complementError(normal: ByteArray, inv: ByteArray, width: Int): String? {
        var n = 0
        var first = -1
        for (i in normal.indices) if (normal[i] == inv[i]) { if (first < 0) first = i; n++ }
        return if (n == 0) null else "inverted render is not the complement: $n px differ, first at (${first % width},${first / width})"
    }

    /** Pixels that differ between [a] and [b] outside every rect of [inside]: count and first, or null. */
    fun diffOutside(a: ByteArray, b: ByteArray, width: Int, inside: Array<IRect>): String? {
        var n = 0
        var first = -1
        for (i in a.indices) {
            if (a[i] == b[i]) continue
            val x = i % width
            val y = i / width
            if (inside.any { it.contains(x, y) }) continue
            if (first < 0) first = i
            n++
        }
        return if (n == 0) null else "$n px changed outside the seat panels, first at (${first % width},${first / width})"
    }

    /** Where failing renders are written (gitignored build dir). */
    val outDir = File("build/purity")
}
