package au.com.josephmark.dithertracker.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Independent, deliberately naive reader of an effect folder (`meta.txt` + `frame-N.txt`), used to
 * check the generated data against the source, and of the ASCII dither tiles in design/patterns/.
 */
class FxSource(val dir: File) {
    val name: String get() = dir.name

    /** Every meta line as key → values, comments stripped; `tile` lines are collected separately. */
    val meta: Map<String, List<String>>
    /** `tile` lines: digit char → (tile name, mode). */
    val tiles: Map<Char, Pair<String, String>>

    init {
        val m = HashMap<String, List<String>>()
        val t = HashMap<Char, Pair<String, String>>()
        for (raw in File(dir, "meta.txt").readLines()) {
            val l = raw.substringBefore('#').trim()
            if (l.isEmpty()) continue
            val p = l.split(Regex("\\s+"))
            if (p[0] == "tile") t[p[1].single()] = p[2] to p[3] else m[p[0]] = p.drop(1)
        }
        meta = m
        tiles = t
    }

    val frames: Int get() = meta["frames"]!![0].toInt()

    /** Rows of frame [i] (0-based). */
    fun frame(i: Int): List<String> = File(dir, "frame-${i + 1}.txt").readLines().filter { it.isNotEmpty() }

    /** Asserts [spec] holds exactly this source: metadata, tile declarations and every pixel's layer. */
    fun assertMatches(spec: FxSpec) {
        val n = name
        assertEquals(n, meta["size"]!!.map(String::toInt), listOf(spec.width, spec.height))
        assertEquals(n, frames, spec.frameCount)
        assertEquals(n, meta["hold_ms"]!!.map(String::toInt), (0 until spec.frameCount).map(spec::holdMs))
        val a = meta["anchor"]!!
        val (ref, dx, dy) = if (a.size == 3) Triple(a[0], a[1], a[2]) else Triple("centre", a[0], a[1])
        assertEquals(n, ref, spec.anchorRef.id)
        assertEquals(n, dx.toInt(), spec.anchorDx)
        assertEquals(n, dy.toInt(), spec.anchorDy)
        meta["role"]?.let { assertEquals(n, it[0], spec.role.id) }
        // Tile declarations, in ascending digit order.
        assertEquals(n, tiles.keys.sorted().map { it.digitToInt() }, spec.tiles.map { it.digit })
        for (t in spec.tiles) {
            val (tile, mode) = tiles[t.digit.digitToChar()]!!
            assertEquals(n, tile, t.tile.name)
            assertEquals(n, mode, t.mode.id)
        }
        for (f in 0 until spec.frameCount) {
            val rows = frame(f)
            assertEquals("$n f${f + 1} rows", spec.height, rows.size)
            val layers = buildMap {
                put('#', spec.ink(f)); put('o', spec.knockout(f)); put('x', spec.cut(f))
                spec.tiles.forEachIndexed { i, t -> put(t.digit.digitToChar(), spec.tileLayer(i, f)) }
            }
            val counts = HashMap<Char, Int>()
            for (y in 0 until spec.height) {
                assertEquals("$n f${f + 1} row ${y + 1}", spec.width, rows[y].length)
                for (x in 0 until spec.width) {
                    val c = rows[y][x]
                    assertTrue("$n frame-${f + 1} ($x,$y): unexpected '$c'", c == '.' || c in layers)
                    for ((sym, layer) in layers) assertEquals("$n frame-${f + 1} ($x,$y) '$c' in layer '$sym'", c == sym, layer.isInk(x, y))
                    counts.merge(c, 1, Int::plus)
                }
            }
            for ((sym, layer) in layers) {
                assertEquals("$n f${f + 1} '$sym' count", counts[sym] ?: 0, layer.inkCount)
                assertEquals(spec.width, layer.width); assertEquals(spec.height, layer.height)
            }
        }
        val last = spec.frameCount - 1
        assertTrue("$n: last frame empty", spec.ink(last).isEmpty && spec.knockout(last).isEmpty && spec.cut(last).isEmpty &&
            spec.tiles.indices.all { spec.tileLayer(it, last).isEmpty })
        assertEquals(0, spec.holdMs(last))
    }

    companion object {
        fun dirs(root: File): List<FxSource> =
            root.listFiles()!!.filter { it.isDirectory && File(it, "meta.txt").isFile }.sortedBy { it.name }.map(::FxSource)

        val shippedDir: File get() = File(System.getProperty("dither.fxDir") ?: "../design/fx")
        val optionsDir: File get() = File(System.getProperty("dither.fxOptionsDir") ?: "../design/fx/options")
        private val patternsDir: File get() = File(System.getProperty("dither.patternsDir") ?: "../design/patterns")

        private val tileCache = HashMap<String, List<String>>()

        /** A dither tile's ASCII rows straight from design/patterns/<NAME>.txt (not from Patterns.kt). */
        fun tileRows(name: String): List<String> = tileCache.getOrPut(name) {
            File(patternsDir, "$name.txt").readLines().filter { it.isNotEmpty() && it[0] in ".#" }
        }

        /** render2.py's global-phase sample: `t[sy % h][sx % w] == '#'`. */
        fun tileInk(name: String, sx: Int, sy: Int): Boolean {
            val t = tileRows(name)
            return t[Math.floorMod(sy, t.size)][Math.floorMod(sx, t[0].length)] == '#'
        }
    }
}
