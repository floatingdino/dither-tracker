package au.com.josephmark.dithertracker.type

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FontTest {

    private val digits = "0123456789"

    private val required: Map<Font, String> = mapOf(
        Fonts.UI to "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" + digits +
            "+-−×/:.,!?()[]%#&'\"<>=_| ",
        Fonts.NUM_L to "$digits-+ ",
        Fonts.NUM_M to "$digits+-/: ",
        Fonts.NUM_L_PM to "+-− ",
    )

    @Test fun everyRequiredCharIsPresentExactly() {
        for ((font, chars) in required) for (c in chars) {
            val g = font.find(c)
            assertNotNull("${font.name} is missing '$c' (U+${"%04X".format(c.code)})", g)
            assertSame(g, font.glyph(c))
            assertEquals(c, g!!.char)
        }
    }

    @Test fun allFacesAreGenerated() {
        assertEquals(setOf("ui", "num-l", "num-l-pm", "num-m"), Fonts.all.map { it.name }.toSet())
    }

    @Test fun glyphBoxesAreConsistentAndMetricsSane() {
        for (font in Fonts.all) {
            for (g in font.glyphs + font.notdef) {
                assertEquals("${font.name} $g width", font.glyphWidth, g.width)
                assertEquals("${font.name} $g height", font.glyphHeight, g.height)
            }
            assertTrue(font.capTop >= 0 && font.capHeight > 0 && font.baseline <= font.glyphHeight)
            assertEquals(font.glyphWidth + font.tracking, font.advance)
            assertTrue("${font.name} notdef must have ink", font.notdef.inkCount > 0)
            assertTrue(font.glyph(' ').isBlank)
            if (font !== Fonts.NUM_L_PM) for (c in digits) assertFalse(font.glyph(c).isBlank)
            // No duplicate characters.
            assertEquals(font.glyphs.size, font.glyphs.map { it.char }.toSet().size)
        }
    }

    @Test fun capsAndDigitsSitInTheCapBand() {
        val ui = Fonts.UI
        for (c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789") {
            val g = ui.glyph(c)
            for (y in 0 until g.height) for (x in 0 until g.width) {
                if (g.isInk(x, y)) assertTrue("'$c' ink at row $y outside cap band", y in ui.capTop until ui.baseline)
            }
        }
        // Descenders reach below the baseline.
        for (c in "gjpqy") assertTrue("'$c' has no descender", (0 until 5).any { ui.glyph(c).isInk(it, ui.baseline) })
    }

    @Test fun fallbacks() {
        val nl = Fonts.NUM_L
        assertSame(nl.notdef, nl.glyph('A'))
        assertSame(nl.notdef, nl.glyph('é'))
        assertSame(nl.glyph('-'), nl.glyph('−')) // true minus falls back to hyphen-minus
        assertEquals('-', nl.minusChar)
        assertEquals('−', Fonts.UI.minusChar)
        assertSame(Fonts.UI.notdef, Fonts.UI.glyph('€'))
        assertNull(Fonts.NUM_M.find('a'))
        assertSame(Fonts.NUM_M.notdef, Fonts.NUM_M.glyph('a'))
    }

    @Test fun generatedBitsMatchSourceAscii() {
        val dir = File(System.getProperty("dither.fontDir")!!)
        for (font in Fonts.all) {
            val src = parse(File(dir, "${font.name}/glyphs.txt"))
            assertEquals("${font.name} glyph count", src.size, font.glyphs.size + 1)
            for ((key, rows) in src) {
                val g = if (key == null) font.notdef else font.find(key)!!
                for (y in rows.indices) for (x in rows[y].indices) {
                    assertEquals("${font.name} $g ($x,$y)", rows[y][x] == '#', g.isInk(x, y))
                }
            }
        }
    }

    @Test fun numMIsTheBoldMiniSticker() {
        // T37: the small numerals are the designer's `bold` pick, 10×14 with 3-px stems, no shadow.
        val m = Fonts.NUM_M
        assertEquals(10, m.glyphWidth); assertEquals(14, m.glyphHeight); assertEquals(2, m.tracking)
        assertEquals(0, m.capTop); assertEquals(14, m.capHeight)
        assertEquals(10 * 3 + 2 * 2, m.measureInt(999)); assertEquals(10 * 3 + 2, m.measureInt(999, tracking = 1))
        assertEquals(m.measure("-10998"), m.measureInt(-10998))
        // Built from exactly the candidate's bits (the adopted copy may only differ in its header).
        val dir = File(System.getProperty("dither.fontDir")!!)
        val built = parse(File(dir, "num-m/glyphs.txt"))
        val candidate = parse(File(dir, "candidates/mini2/bold/glyphs.txt"))
        assertEquals(candidate, built)
        // 3-px verticals: every digit has a row with a run of at least 3 ink px.
        for (c in "0123456789") {
            val g = m.glyph(c)
            assertTrue("'$c'", (0 until g.height).any { y -> (0..g.width - 3).any { x -> g.isInk(x, y) && g.isInk(x + 1, y) && g.isInk(x + 2, y) } })
        }
    }

    @Test fun scaledGlyphsReplicatePixelsAndAreCached() {
        val g = Fonts.NUM_M.glyph('4')
        assertSame(g, g.scaled(1))
        for (k in 2..4) {
            val s = g.scaled(k)
            assertSame(s, g.scaled(k))
            assertEquals(g.width * k, s.width)
            assertEquals(g.height * k, s.height)
            for (y in 0 until s.height) for (x in 0 until s.width) assertEquals(g.isInk(x / k, y / k), s.isInk(x, y))
        }
    }

    @Test fun measureMaths() {
        val ui = Fonts.UI // 5 wide, tracking 1
        assertEquals(0, ui.measure(""))
        assertEquals(5, ui.measure("A"))
        assertEquals(5 + 1 + 5, ui.measure("AB"))
        assertEquals(16 * 5 + 15 * 2, ui.measure("COMMANDER DAMAGE", tracking = 2))
        assertEquals(3 * (2 * 5 + 1), ui.measure("AB", k = 3))
        val nl = Fonts.NUM_L // 32 wide, tracking 4 (sticker, T27)
        assertEquals(68, nl.measure("40"))
        assertEquals(68, nl.measureInt(40))
        assertEquals(nl.measure("-12"), nl.measureInt(-12))
        assertEquals(nl.measure("+7"), nl.measureInt(7, signed = true))
        assertEquals(nl.measure("0"), nl.measureInt(0, signed = true))
        assertEquals(2 * 56, nl.height(2))
        assertEquals(140, nl.measureInt(-999)) // the widest life total, k = 1
    }

    @Test fun stickerShadowMasksAreSeparateFromInkAndOutsideCounters() {
        for (font in listOf(Fonts.NUM_L, Fonts.NUM_L_PM)) {
            assertTrue(font.hasShadow)
            for (g in font.glyphs) {
                val sh = g.shadow ?: continue
                for (y in 0 until g.height) for (x in 0 until g.width) {
                    // never both ink and shadow; a 1-px paper keyline separates them
                    if (sh.isInk(x, y)) {
                        assertFalse("$g ($x,$y)", g.isInk(x, y))
                        for (dy in -1..1) for (dx in -1..1) {
                            val u = x + dx; val v = y + dy
                            if (u in 0 until g.width && v in 0 until g.height) assertFalse("$g keyline ($x,$y)", g.isInk(u, v))
                        }
                    }
                }
            }
            // every digit/sign with ink has a shadow, falling down-right of the body
            for (g in font.glyphs) if (!g.isBlank) assertNotNull("$g", g.shadow)
        }
        assertFalse(Fonts.NUM_M.hasShadow); assertFalse(Fonts.UI.hasShadow)
        assertNull(Fonts.NUM_L.glyph(' ').shadow)
    }

    @Test fun turnedShadowMasksMatchARotatedBlit() {
        val g = Fonts.NUM_L.glyph('7')
        val sh = g.shadow!!
        for (k in 1..2) {
            assertSame(g.shadowMask(k, 180), g.shadowMask(k, 180))
            val m0 = g.shadowMask(k, 0)!!
            val m90 = g.shadowMask(k, 90)!!
            val m180 = g.shadowMask(k, 180)!!
            val m270 = g.shadowMask(k, 270)!!
            val w = g.width * k; val h = g.height * k
            assertEquals(w, m180.width); assertEquals(h, m90.width); assertEquals(w, m90.height)
            for (y in 0 until h) for (x in 0 until w) {
                val src = sh.isInk(x / k, y / k)
                assertEquals(src, m0.isInk(x, y))
                assertEquals(src, m180.isInk(w - 1 - x, h - 1 - y))
                assertEquals(src, m90.isInk(h - 1 - y, x))
                assertEquals(src, m270.isInk(y, w - 1 - x))
            }
        }
    }

    @Test fun intFormatting() {
        val buf = CharArray(12)
        fun f(v: Int, signed: Boolean = false, minus: Char = '-') = String(buf, 0, Font.formatInt(v, signed, minus, buf))
        assertEquals("0", f(0))
        assertEquals("0", f(0, signed = true))
        assertEquals("7", f(7))
        assertEquals("+7", f(7, signed = true))
        assertEquals("-12", f(-12, signed = true))
        assertEquals("−12", f(-12, minus = '−'))
        assertEquals("40", f(40))
        assertEquals("1000000", f(1_000_000))
        assertEquals(Int.MAX_VALUE.toString(), f(Int.MAX_VALUE))
        assertEquals(Int.MIN_VALUE.toString(), f(Int.MIN_VALUE))
        for (v in listOf(0, 9, 10, -9, -10, 99, 100, -100, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals(v.toString().length, Font.intLength(v))
        }
    }

    /** Minimal independent reader of the face format: (char or null for notdef) → rows. */
    private fun parse(f: File): Map<Char?, List<String>> {
        val out = LinkedHashMap<Char?, List<String>>()
        val lines = f.readLines()
        var i = lines.indexOfFirst { it.startsWith("--- ") }
        val h = lines.first { it.startsWith("size ") }.split(" ")[2].toInt()
        while (i in lines.indices) {
            val key = lines[i].substring(4)
            val c: Char? = when {
                key == "notdef" -> null
                key.startsWith("U+") -> key.substring(2).toInt(16).toChar()
                else -> key[0]
            }
            out[c] = lines.subList(i + 1, i + 1 + h)
            i = (i + 1 + h until lines.size).firstOrNull { lines[it].startsWith("--- ") } ?: -1
        }
        return out
    }
}
