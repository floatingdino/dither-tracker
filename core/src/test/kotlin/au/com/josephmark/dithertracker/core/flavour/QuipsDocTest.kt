package au.com.josephmark.dithertracker.core.flavour

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** `design/copy/quips.md` must list exactly the lines in [QuipTables], section by section. */
class QuipsDocTest {
    @Test fun docMirrorsTables() {
        val doc = findDoc()
        assertNotNull(doc, "design/copy/quips.md not found")
        val text = doc.readText()
        val (quippyPart, plainPart) = text.substringAfter("\n## Lines\n").split("\n## Plain").let { it[0] to it[1] }

        assertEquals(expected(Flavour.quippy), bullets(quippyPart), "quips.md (Lines) is out of sync with QuipTables.kt")
        assertEquals(expected(Flavour.plain), bullets(plainPart), "quips.md (Plain) is out of sync with QuipTables.kt")
    }

    /** The doc's bullets, in order: one list per `###` section. */
    private fun bullets(md: String): List<List<String>> =
        md.split("\n### ").drop(1).map { section ->
            section.lines().filter { it.startsWith("- ") }.map { it.removePrefix("- ") }
        }

    private fun expected(f: Flavour): List<List<String>> =
        QuipKind.entries.map { f.lines(it) }.let { lines ->
            // Doc order: QuipKind order, with the button pairs right after the reset prompt, names last.
            val i = QuipKind.RESET_PROMPT.ordinal + 1
            lines.take(i) +
                listOf(f.resetButtons.map { "${it.confirm} / ${it.cancel}" }) +
                lines.drop(i) +
                listOf(f.nameSets.map { "${it.title}: ${it.names.joinToString(", ")}" })
        }

    private fun findDoc(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "design/copy/quips.md")
            if (f.isFile) return f
            dir = dir.parentFile
        }
        return null
    }
}
