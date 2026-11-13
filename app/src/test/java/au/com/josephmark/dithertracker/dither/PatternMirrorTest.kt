package au.com.josephmark.dithertracker.dither

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Keeps `design/patterns/` an exact ASCII mirror of [Patterns]. Fails on drift; regenerate with
 * `./gradlew :app:testDebugUnitTest -PupdatePatterns`.
 */
class PatternMirrorTest {

    private fun expected(): Map<String, String> {
        val m = linkedMapOf<String, String>()
        for (t in Patterns.all) m["${t.name}.txt"] = t.toAscii()
        m["INDEX.txt"] = buildString {
            appendLine("# Generated from app/src/main/java/au/com/josephmark/dithertracker/dither/Patterns.kt")
            appendLine("# by PatternMirrorTest. Do not edit; '#' = ink, '.' = paper.")
            appendLine("# name        size  ink%")
            for (t in Patterns.all) {
                appendLine("%-12s %dx%d  %5.1f".format(t.name, t.width, t.height, t.density * 100))
            }
            appendLine("#")
            appendLine("# role        tile")
            for (r in Role.entries) appendLine("%-12s %s".format(r.name, r.tile.name))
        }
        return m
    }

    @Test fun mirrorMatchesSource() {
        val dir = File(System.getProperty("dither.patternsDir") ?: error("dither.patternsDir not set"))
        val update = System.getProperty("dither.updatePatterns") == "true"
        val want = expected()
        if (update) {
            dir.mkdirs()
            dir.listFiles { f -> f.name.endsWith(".txt") && f.name !in want }?.forEach { it.delete() }
            for ((n, c) in want) File(dir, n).writeText(c)
            return
        }
        val problems = mutableListOf<String>()
        for ((n, c) in want) {
            val f = File(dir, n)
            if (!f.exists()) problems += "missing $n" else if (f.readText() != c) problems += "stale $n"
        }
        dir.listFiles { f -> f.name.endsWith(".txt") && f.name !in want }?.forEach { problems += "orphan ${it.name}" }
        if (problems.isNotEmpty()) {
            fail("design/patterns is out of sync with Patterns.kt: $problems\nRun ./gradlew :app:testDebugUnitTest -PupdatePatterns")
        }
    }
}
