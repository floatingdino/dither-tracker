package au.com.josephmark.dithertracker.layout

/**
 * A lattice arrangement's table (T60): [cols] × [rows] equal cells, each owned by a seat or empty.
 * A seat is a rectangle of cells (so seats can be different sizes: a full-width seat over two halves,
 * two tall seats beside a wide one), facing one edge; an empty cell is table with nobody at it.
 *
 * Written as text, one string per row, a letter per cell (`'.'` empty), and one facing per seat letter
 * in order of first appearance — `n` far edge (content 180°), `s` near edge (0°), `w` left end (90°),
 * `e` right end (270°):
 * ```
 *   Lattice.of("aa", "bc", facing = "nss")   // a full-width far seat over two near halves
 *   Lattice.of("ab", ".c", facing = "nns")   // a 2×2 with the near-left corner empty
 * ```
 * Always described for a landscape table; `Seating` turns it for a portrait screen.
 */
class Lattice private constructor(
    val cols: Int,
    val rows: Int,
    /** Owner of cell (c, r) at `r * cols + c`: a seat definition index, or [EMPTY]. */
    val owner: IntArray,
    /** Rotation of each seat definition (0, 90, 180, 270). */
    val facing: IntArray,
    /** Each seat definition's cell rectangle: `c0, r0, c1, r1` (inclusive) at `4 * i`. */
    val span: IntArray,
) {
    val seats: Int get() = facing.size

    fun ownerAt(c: Int, r: Int): Int = owner[r * cols + c]

    val emptyCells: Int get() = owner.count { it == EMPTY }

    /** The text form (rows joined with `/`, then `:` and the facings): stable, for ids and tests. */
    override fun toString(): String = buildString {
        for (r in 0 until rows) {
            if (r > 0) append('/')
            for (c in 0 until cols) ownerAt(c, r).let { append(if (it == EMPTY) '.' else 'a' + it) }
        }
        append(':')
        for (f in facing) append(when (f) { 180 -> 'n'; 90 -> 'w'; 270 -> 'e'; else -> 's' })
    }

    companion object {
        const val EMPTY = -1

        fun of(vararg rowText: String, facing: String): Lattice {
            require(rowText.isNotEmpty()) { "no rows" }
            val cols = rowText[0].length
            require(cols > 0 && rowText.all { it.length == cols }) { "ragged lattice" }
            val rows = rowText.size
            val letters = ArrayList<Char>()
            val owner = IntArray(cols * rows)
            for (r in 0 until rows) for (c in 0 until cols) {
                val ch = rowText[r][c]
                owner[r * cols + c] = if (ch == '.') EMPTY else {
                    var i = letters.indexOf(ch)
                    if (i < 0) { letters += ch; i = letters.size - 1 }
                    i
                }
            }
            require(facing.length == letters.size) { "${letters.size} seats, ${facing.length} facings" }
            val rot = IntArray(letters.size) {
                when (facing[it]) {
                    'n' -> 180; 's' -> 0; 'w' -> 90; 'e' -> 270
                    else -> throw IllegalArgumentException("facing '${facing[it]}'")
                }
            }
            val span = IntArray(4 * letters.size)
            for (i in letters.indices) {
                var c0 = cols; var r0 = rows; var c1 = -1; var r1 = -1
                for (r in 0 until rows) for (c in 0 until cols) if (owner[r * cols + c] == i) {
                    c0 = minOf(c0, c); r0 = minOf(r0, r); c1 = maxOf(c1, c); r1 = maxOf(r1, r)
                }
                for (r in r0..r1) for (c in c0..c1) require(owner[r * cols + c] == i) { "seat '${letters[i]}' is not a rectangle" }
                span[4 * i] = c0; span[4 * i + 1] = r0; span[4 * i + 2] = c1; span[4 * i + 3] = r1
            }
            return Lattice(cols, rows, owner, rot, span)
        }

        /** [rowText] with the cell at ([c], [r]) emptied — for generating the "corner missing" variants. */
        fun without(rowText: Array<String>, c: Int, r: Int): Array<String> =
            Array(rowText.size) { i -> if (i != r) rowText[i] else rowText[i].substring(0, c) + "." + rowText[i].substring(c + 1) }
    }
}
