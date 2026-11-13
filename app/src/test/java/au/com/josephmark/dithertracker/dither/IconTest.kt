package au.com.josephmark.dithertracker.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class IconTest {

    @Test fun everyIconIs16x16AndFindableByName() {
        for (icon in Icon.entries) {
            assertEquals(16, icon.width); assertEquals(16, icon.height)
            assertSame(icon, Icon.byName(icon.id))
        }
        assertNull(Icon.byName("no-such-icon"))
    }

    @Test fun generatedBitsMatchSourceAscii() {
        val dir = File(System.getProperty("dither.patternsDir")!!).resolveSibling("icons")
        for (icon in Icon.entries) {
            val f = File(dir, "${icon.id}.txt").takeIf { it.exists() }
                ?: dir.listFiles()!!.first { it.nameWithoutExtension.lowercase() == icon.id }
            val lines = f.readLines()
            for (y in 0 until 16) for (x in 0 until 16) {
                assertEquals("${icon.id} ($x,$y)", lines[y][x] == '#', icon.isInk(x, y))
            }
        }
    }
}
