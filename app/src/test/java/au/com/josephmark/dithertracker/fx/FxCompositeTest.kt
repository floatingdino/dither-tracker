package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Patterns
import au.com.josephmark.dithertracker.dither.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * [FxRenderer]'s compositing (layer order, cut, tile modes, rotation, `inverted`) run against a
 * software [FxRenderer.Target] and compared with an independent port of
 * `design/fx/options/render2.py`'s `stamp`, on hand-made fixtures and on every round-two option.
 */
class FxCompositeTest {

    /**
     * Pure-Kotlin stand-in for DitherRenderer: a raster of colours (true = #000). Ink/paper colours
     * swap with [inverted] like the real renderer; cut inverts; tile fills sample at global phase.
     */
    private class Raster(val w: Int, val h: Int) : FxRenderer.Target {
        var inverted = false
        val black = BooleanArray(w * h)
        val ops = ArrayList<String>()

        private val inkColour get() = !inverted
        private fun put(x: Int, y: Int, v: Boolean) { if (x in 0 until w && y in 0 until h) black[y * w + x] = v }
        operator fun get(x: Int, y: Int) = black[y * w + x]

        override fun blit(img: MonoImage, x: Int, y: Int, rotation: Int, paper: Boolean) {
            ops += if (paper) "paper" else "ink"
            for (v in 0 until img.height) for (u in 0 until img.width) if (img.isInk(u, v)) {
                // Clockwise turn of pixel (u, v) in a W×H image, as DitherRenderer.blit draws it.
                val (sx, sy) = when (rotation) {
                    0 -> u to v
                    90 -> img.height - 1 - v to u
                    180 -> img.width - 1 - u to img.height - 1 - v
                    else -> v to img.width - 1 - u
                }
                put(x + sx, y + sy, if (paper) !inkColour else inkColour)
            }
        }

        override fun invert(mask: MonoImage, x: Int, y: Int, underlay: Runnable?) {
            ops += "cut"
            for (v in 0 until mask.height) for (u in 0 until mask.width) if (mask.isInk(u, v)) {
                val gx = x + u; val gy = y + v
                if (gx in 0 until w && gy in 0 until h) black[gy * w + gx] = !black[gy * w + gx]
            }
        }

        override fun tile(mask: MonoImage, x: Int, y: Int, tile: Tile, mode: FxTileMode) {
            ops += "tile:${tile.name}:${mode.id}"
            for (v in 0 until mask.height) for (u in 0 until mask.width) if (mask.isInk(u, v)) {
                val gx = x + u; val gy = y + v
                val t = tile.isInkAt(gx, gy)
                when (mode) {
                    FxTileMode.INK -> if (t) put(gx, gy, inkColour)
                    FxTileMode.PAPER -> if (t) put(gx, gy, !inkColour)
                    FxTileMode.FILL -> put(gx, gy, if (t) inkColour else !inkColour)
                }
            }
        }

        /** Paints a logical picture (true = ink) in the current colours. */
        fun paint(logical: BooleanArray) { for (i in black.indices) black[i] = logical[i] == inkColour }

        /** The logical picture back (true = ink), whatever [inverted] is. */
        fun logical() = BooleanArray(black.size) { black[it] == inkColour }
    }

    /**
     * render2.py's `stamp`, ported literally: frame [rows] with its upright top-left at local
     * ([lx0], [ly0]) of a seat tile TW×TH whose screen origin is ([sx0], [sy0]), turned 180 when
     * [rot]; pixels outside the tile are clipped. Works on logical ink (true) in a [w]-wide screen.
     */
    private fun stamp(
        screen: BooleanArray, w: Int, rows: List<String>, tiles: Map<Char, Pair<String, String>>,
        sx0: Int, sy0: Int, rot: Boolean, lx0: Int, ly0: Int, tw: Int = TW, th: Int = TH,
    ) {
        for ((y, row) in rows.withIndex()) for ((x, v) in row.withIndex()) {
            if (v == '.') continue
            val lx = lx0 + x; val ly = ly0 + y
            if (lx !in 0 until tw || ly !in 0 until th) continue
            val sx = if (rot) sx0 + tw - 1 - lx else sx0 + lx
            val sy = if (rot) sy0 + th - 1 - ly else sy0 + ly
            val i = sy * w + sx
            when (v) {
                '#' -> screen[i] = true
                'o' -> screen[i] = false
                'x' -> screen[i] = !screen[i]
                else -> {
                    val (name, mode) = tiles.getValue(v)
                    val ink = FxSource.tileInk(name, sx, sy)
                    when (mode) {
                        "ink" -> if (ink) screen[i] = true
                        "paper" -> if (ink) screen[i] = false
                        else -> screen[i] = ink
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- hand-made fixtures

    /** A 7×5 frame using every symbol: two tile digits, cut, ink, knock-out. */
    private val fixtureRows = listOf(
        "xx11..#",
        "x.11o.#",
        "22..oo.",
        "22x.333",
        "..x#333",
    )

    private fun fixtureSpec(modes: Map<Char, Pair<Tile, FxTileMode>>): FxSpec {
        val w = fixtureRows[0].length; val h = fixtureRows.size
        fun layer(c: Char): FxLayer {
            val stride = (w + 31) / 32
            val words = IntArray(stride * h)
            for (y in 0 until h) for (x in 0 until w) if (fixtureRows[y][x] == c) words[y * stride] = words[y * stride] or (1 shl (31 - x))
            return FxLayer(w, h, words)
        }
        val empty = FxLayer.empty(w, h)
        val digits = modes.keys.sorted()
        return FxSpec(
            "fixture", FxRole.DAMAGE, w, h, FxAnchorRef.TILE, 0, 0, intArrayOf(100, 0),
            digits.map { FxTileSpec(it.digitToInt(), modes.getValue(it).first, modes.getValue(it).second) },
            arrayOf(layer('#'), empty), arrayOf(layer('o'), empty), arrayOf(layer('x'), empty),
            Array(digits.size) { arrayOf(layer(digits[it]), empty) },
        )
    }

    /** A deterministic "numeral": random ink, so cut and every mode meet both colours. */
    private fun under(w: Int, h: Int, seed: Long) = Random(seed).let { r -> BooleanArray(w * h) { r.nextBoolean() } }

    private fun checkFixture(modes: Map<Char, Pair<Tile, FxTileMode>>, rotation: Int, inverted: Boolean, x: Int, y: Int) {
        val spec = fixtureSpec(modes)
        val w = 23; val h = 17
        val base = under(w, h, 7L * rotation + x)
        // Expected: render2 on logical ink, the 7×5 frame upright at local (0,0) of a 7×5 "tile".
        val expected = base.copyOf()
        val tiles = modes.mapValues { (_, v) -> v.first.name to v.second.id }
        stamp(expected, w, fixtureRows, tiles, x, y, rotation == 180, 0, 0, spec.width, spec.height)
        // Actual: FxRenderer on the software raster.
        val r = Raster(w, h).apply { this.inverted = inverted; paint(base) }
        FxRenderer(r).drawAt(spec, 0, x, y, rotation)
        val got = r.logical()
        for (yy in 0 until h) for (xx in 0 until w) {
            assertEquals("modes $tiles rot $rotation inv $inverted at ($xx,$yy)", expected[yy * w + xx], got[yy * w + xx])
        }
        // And the raw colours really are swapped in dark mode.
        if (inverted) assertTrue(r.black.indices.all { r.black[it] == !got[it] })
        // Nothing drawn for the empty end frame.
        val before = r.black.copyOf()
        FxRenderer(r).drawAt(spec, 1, x, y, rotation)
        assertTrue(before.contentEquals(r.black))
    }

    @Test fun cutAndEveryTileModeMatchRender2UprightTurnedAndInverted() {
        val modeSets = listOf(
            mapOf('1' to (Patterns.CHECKER to FxTileMode.INK), '2' to (Patterns.HLINES to FxTileMode.PAPER), '3' to (Patterns.D[4] to FxTileMode.FILL)),
            mapOf('1' to (Patterns.D[12] to FxTileMode.PAPER), '2' to (Patterns.D[4] to FxTileMode.INK), '3' to (Patterns.DIAG to FxTileMode.FILL)),
            mapOf('1' to (Patterns.CHECKER to FxTileMode.FILL), '2' to (Patterns.D[16] to FxTileMode.INK), '3' to (Patterns.D[0] to FxTileMode.PAPER)),
        )
        for (modes in modeSets) for (rot in intArrayOf(0, 180)) for (inv in listOf(false, true)) {
            // Odd and even origins: the tile phase must follow the screen, not the frame.
            for ((x, y) in listOf(3 to 4, 6 to 9, 11 to 5)) checkFixture(modes, rot, inv, x, y)
        }
    }

    @Test fun tilePhaseIsGlobalNotFrameRelative() {
        // One 2×1 'fill' patch of CHECKER drawn at x = 4 and x = 5: the pattern stays put on screen.
        val spec = FxSpec(
            "phase", FxRole.HEAL, 2, 1, FxAnchorRef.CENTRE, 0, 0, intArrayOf(1, 0),
            listOf(FxTileSpec(1, Patterns.CHECKER, FxTileMode.FILL)),
            Array(2) { FxLayer.empty(2, 1) }, Array(2) { FxLayer.empty(2, 1) }, Array(2) { FxLayer.empty(2, 1) },
            arrayOf(arrayOf(FxLayer(2, 1, intArrayOf(0xC0000000.toInt())), FxLayer.empty(2, 1))),
        )
        for (x0 in intArrayOf(4, 5)) for (rot in intArrayOf(0, 180)) {
            val r = Raster(10, 2)
            FxRenderer(r).drawAt(spec, 0, x0, 1, rot)
            for (x in x0 until x0 + 2) assertEquals("x $x rot $rot", Patterns.CHECKER.isInkAt(x, 1), r[x, 1])
        }
    }

    @Test fun drawOrderIsCutThenTilesByDigitThenKnockoutThenInk() {
        val spec = fixtureSpec(mapOf('3' to (Patterns.D[4] to FxTileMode.FILL), '1' to (Patterns.CHECKER to FxTileMode.INK), '2' to (Patterns.HLINES to FxTileMode.PAPER)))
        val r = Raster(20, 20)
        FxRenderer(r).drawAt(spec, 0, 2, 2, 0)
        assertEquals(listOf("cut", "tile:CHECKER:ink", "tile:HLINES:paper", "tile:D4:fill", "paper", "ink"), r.ops)
    }

    @Test fun turnedLayersMatchTheBlitTurnForEveryRotation() {
        val spec = fixtureSpec(mapOf('1' to (Patterns.D[16] to FxTileMode.INK), '2' to (Patterns.D[16] to FxTileMode.INK), '3' to (Patterns.D[16] to FxTileMode.INK)))
        val layer = spec.cut(0)
        for (rot in intArrayOf(0, 90, 180, 270)) {
            val viaBlit = Raster(12, 12).also { it.blit(layer, 2, 3, rot, paper = false) }
            val viaView = Raster(12, 12).also { it.tile(layer.rotated(rot), 2, 3, Patterns.D[16], FxTileMode.INK) }
            assertTrue("rot $rot", viaBlit.black.contentEquals(viaView.black))
            assertTrue(layer.rotated(rot) === layer.rotated(rot))
        }
    }

    // ---------------------------------------------------------------- every round-two option

    @Test fun everyOptionFrameMatchesRender2OnBothSeatsAndInDarkMode() {
        val sources = FxSource.dirs(FxSource.optionsDir).associateBy { it.name }
        for (spec in FxOptions.all) {
            val src = sources.getValue(spec.id)
            val a = src.meta["anchor"]!!
            val (ref, dx, dy) = if (a.size == 3) Triple(a[0], a[1].toInt(), a[2].toInt()) else Triple("centre", a[0].toInt(), a[1].toInt())
            for (f in 0 until spec.frameCount - 1) for ((sx0, sy0, rot) in SEATS) for (inv in listOf(false, true)) {
                val rows = src.frame(f)
                val base = under(SW, SH, f * 31L + sx0)
                // render2.frame_origin over a sticker "37" (box 68×56 centred on the tile centre).
                val (lx0, ly0) = if (ref == "tile") (TCX + dx - spec.width / 2) to (TCY + dy - spec.height / 2)
                else {
                    val (rx, ry) = REFS.getValue(ref)
                    (TCX + rx * (BOX_W / 2) + dx - spec.width / 2) to (TCY + ry * (BOX_H / 2) + dy - spec.height / 2)
                }
                val expected = base.copyOf()
                stamp(expected, SW, rows, src.tiles, sx0, sy0, rot, lx0, ly0)
                // FxRenderer + FxLayout with the seat's on-screen tile rect and ink box.
                val tileRect = IRect().setXywh(sx0, sy0, TW, TH)
                val bl = TCX - BOX_W / 2; val bt = TCY - BOX_H / 2
                val inkBox = if (rot) IRect().setXywh(sx0 + TW - bl - BOX_W, sy0 + TH - bt - BOX_H, BOX_W, BOX_H)
                else IRect().setXywh(sx0 + bl, sy0 + bt, BOX_W, BOX_H)
                val r = Raster(SW, SH).apply { inverted = inv; paint(base) }
                FxRenderer(r).draw(spec, f, inkBox, tileRect, if (rot) 180 else 0)
                val got = r.logical()
                var bad = 0
                for (i in got.indices) if (got[i] != expected[i]) bad++
                assertEquals("${spec.id} f${f + 1} seat ($sx0,$sy0) rot $rot inv $inv: wrong px", 0, bad)
            }
        }
    }

    private companion object {
        const val SW = 468; const val SH = 351
        const val TW = 233; const val TH = 175
        const val TCX = 116; const val TCY = 87
        const val BOX_W = 2 * 32 + 4; const val BOX_H = 56 // sticker "37"
        /** render2.py's SEAT_BL and SEAT_TR. */
        val SEATS = listOf(Triple(0, 176, false), Triple(234, 0, true))
        val REFS = mapOf(
            "centre" to (0 to 0), "tl" to (-1 to -1), "tr" to (1 to -1), "bl" to (-1 to 1), "br" to (1 to 1),
            "top" to (0 to -1), "bottom" to (0 to 1), "left" to (-1 to 0), "right" to (1 to 0),
        )
    }
}
