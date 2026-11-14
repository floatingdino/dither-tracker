package au.com.josephmark.dithertracker.seat

import au.com.josephmark.dithertracker.dither.IRect
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A miniature of the table (T44): the screen's panel arrangement shrunk to fit a `size`×`size` box,
 * in *screen* orientation (x right, y down, as the screen itself is), on a crisp 1-px lattice. Pure
 * maths, built once per layout and size (allocates; never on a draw path).
 *
 * Every panel becomes a cell bounded by 1-px lines; neighbouring cells share their line, so the
 * outline and the dividers are all single pixels. [cells]`[i]` is seat i's cell *including* its
 * lines: `left`/`top` are the columns/rows of its left/top lines and `right - 1`/`bottom - 1` those of
 * its right/bottom lines; the interior is `left + 1 until right - 1`.
 *
 * Derivation (see [compute]): panel edges are clustered into boundaries per axis (the 1-px dividers
 * between panels are absorbed); the long axis takes the largest length ≤ `size` at which panels of
 * equal real extent get equal cells; the short axis is the length nearest the screen's aspect at which
 * the same holds. So a 2×2 layout is 15×11 at 16 px (cells 6×4 inside), 23×17 at 24 px, and a
 * 3-, 5- or 6-panel layout gets its own lattice without any change here.
 */
class SeatMap private constructor(
    val width: Int,
    val height: Int,
    val cells: Array<IRect>,
) {
    val seats: Int get() = cells.size

    /** True when screen-orientation pixel (x, y) is on any cell's line. */
    fun isLine(x: Int, y: Int): Boolean {
        for (c in cells) {
            if (x < c.left || x >= c.right || y < c.top || y >= c.bottom) continue
            if (x == c.left || x == c.right - 1 || y == c.top || y == c.bottom - 1) return true
        }
        return false
    }

    /** True when (x, y) is inside seat [seat]'s fill (its interior, shrunk by [inset] when there is room). */
    fun isFill(seat: Int, x: Int, y: Int, inset: Int = 0): Boolean {
        if (seat !in cells.indices) return false
        val c = cells[seat]
        val iw = c.width - 2
        val ih = c.height - 2
        val i = if (iw - 2 * inset >= 2 && ih - 2 * inset >= 2) inset else 0
        return x >= c.left + 1 + i && x < c.right - 1 - i && y >= c.top + 1 + i && y < c.bottom - 1 - i
    }

    /** ASCII of the miniature with [seat] filled (`#` ink, `.` paper), screen orientation; for tests. */
    fun ascii(seat: Int, inset: Int = 0): String = buildString {
        for (y in 0 until height) {
            for (x in 0 until width) append(if (isLine(x, y) || isFill(seat, x, y, inset)) '#' else '.')
            append('\n')
        }
    }

    override fun toString(): String = "SeatMap(${width}x$height, ${cells.joinToString { "${it.left},${it.top} ${it.width}x${it.height}" }})"

    companion object {
        /** Panel edges this close (dither px) are one boundary: the divider between two panels. */
        const val MERGE = 2

        /** Smallest box the lattice is built in. */
        const val MIN_SIZE = 5

        /**
         * The miniature of [panels] (screen rects, any number ≥ 1) fitting a [size]×[size] box.
         * Throws if [size] < [MIN_SIZE] or a panel is empty.
         */
        fun compute(panels: Array<out IRect>, size: Int): SeatMap {
            require(size >= MIN_SIZE) { "size $size < $MIN_SIZE" }
            require(panels.isNotEmpty()) { "no panels" }
            for (p in panels) require(!p.isEmpty) { "empty panel $p" }
            val xs = Axis(panels.map { it.left }, panels.map { it.right })
            val ys = Axis(panels.map { it.top }, panels.map { it.bottom })
            val landscape = xs.span >= ys.span
            val long = if (landscape) xs else ys
            val short = if (landscape) ys else xs
            val lLong = long.bestLongest(size)
            val target = 1 + (lLong - 1) * short.span.toDouble() / long.span
            val lShort = short.bestNear(target, size)
            val w = if (landscape) lLong else lShort
            val h = if (landscape) lShort else lLong
            val px = xs.positions(w)
            val py = ys.positions(h)
            val cells = Array(panels.size) { i ->
                IRect().set(px[xs.lo[i]], py[ys.lo[i]], px[xs.hi[i]] + 1, py[ys.hi[i]] + 1)
            }
            return SeatMap(w, h, cells)
        }
    }

    /** One axis: clustered boundaries, each panel's low/high boundary index, and lattice fitting. */
    private class Axis(lows: List<Int>, highs: List<Int>) {
        val bounds: DoubleArray
        val lo: IntArray
        val hi: IntArray
        val span: Int

        init {
            val all = (lows + highs).sorted()
            val clusters = ArrayList<MutableList<Int>>()
            for (v in all) {
                val last = clusters.lastOrNull()
                if (last != null && v - last.last() <= MERGE) last.add(v) else clusters.add(mutableListOf(v))
            }
            bounds = DoubleArray(clusters.size) { clusters[it].average() }
            fun index(v: Int): Int = clusters.indexOfFirst { v in it }
            lo = IntArray(lows.size) { index(lows[it]) }
            hi = IntArray(highs.size) { index(highs[it]) }
            span = all.last() - all.first()
        }

        /** Line position (pixel column/row) of every boundary in a lattice [len] px long. */
        fun positions(len: Int): IntArray {
            val b0 = bounds.first()
            val s = bounds.last() - b0
            if (s <= 0.0) return IntArray(bounds.size) // T62: a degenerate table (a window a few px across) has no extent
            return IntArray(bounds.size) { ((bounds[it] - b0) / s * (len - 1)).roundToInt() }
        }

        /**
         * How badly a lattice of [len] renders this axis: a cell with no interior is fatal
         * ([Int.MAX_VALUE]); otherwise the number of panel pairs of equal real extent whose cells differ.
         */
        fun violations(len: Int): Int {
            val p = positions(len)
            for (i in 1 until p.size) if (p[i] - p[i - 1] < 2) return Int.MAX_VALUE
            var n = 0
            for (a in lo.indices) for (b in a + 1 until lo.size) {
                val ra = bounds[hi[a]] - bounds[lo[a]]
                val rb = bounds[hi[b]] - bounds[lo[b]]
                if (abs(ra - rb) > MERGE) continue
                if (p[hi[a]] - p[lo[a]] != p[hi[b]] - p[lo[b]]) n++
            }
            return n
        }

        /** The longest length ≤ [max] with no violations (else the fewest, longest first). */
        fun bestLongest(max: Int): Int {
            var best = -1
            var bestV = Int.MAX_VALUE
            for (len in max downTo MIN_SIZE / 2 + 1) {
                val v = violations(len)
                if (v == 0) return len
                if (v < bestV) { bestV = v; best = len }
            }
            require(best > 0) { "no lattice fits $max px" }
            return best
        }

        /** The length ≤ [max] nearest [target] with no violations within 2 px of it (else the fewest). */
        fun bestNear(target: Double, max: Int): Int {
            val order = (2..max).sortedWith(compareBy({ abs(it - target) }, { -it }))
            var best = -1
            var bestV = Int.MAX_VALUE
            for (len in order) {
                val v = violations(len)
                if (v == 0 && abs(len - target) <= 2.0) return len
                if (v < bestV || (v == bestV && best < 0)) { bestV = v; best = len }
            }
            require(best > 0) { "no lattice fits $max px" }
            return best
        }
    }
}
