package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.type.Fonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextWrapTest {

    private val ui = Fonts.UI

    private fun lines(text: String, chars: Int, max: Int = 2, k: Int = 1): Pair<List<String>, TextWrap> {
        val w = TextWrap(max)
        w.wrap(text, ui, k, ui.widthOf(chars, k))
        return List(w.lines) { text.substring(w.start[it], w.end[it]) } to w
    }

    @Test fun shortTextIsOneLine() {
        val (l, w) = lines("Reset the game?", 20)
        assertEquals(listOf("Reset the game?"), l)
        assertFalse(w.truncated)
        assertEquals(15, w.widest)
        assertEquals(ui.widthOf(15, 1), w.widestWidth)
    }

    @Test fun breaksAtSpaces() {
        val (l, w) = lines("Start a new game? Everyone goes back to 40.", 22)
        assertEquals(listOf("Start a new game?", "Everyone goes back to"), l)
        assertTrue(w.truncated)
    }

    @Test fun exactFitAtASpace() {
        val (l, _) = lines("abcde fghij", 5)
        assertEquals(listOf("abcde", "fghij"), l)
    }

    @Test fun longWordIsHardBroken() {
        val (l, w) = lines("Supercalifragilistic", 8)
        assertEquals(listOf("Supercal", "ifragili"), l)
        assertTrue(w.truncated)
    }

    @Test fun newlineForcesABreakAndSpacesCollapse() {
        val (l, _) = lines("  Reset?\n\n  Sure   about it ", 30)
        assertEquals(listOf("Reset?", "Sure   about it"), l)
    }

    @Test fun everyLineFitsAtEveryScale() {
        val text = "Undo the last change for every player at the table, then go again"
        for (k in 1..2) for (chars in 3..40) {
            val w = TextWrap(3)
            val max = ui.widthOf(chars, k)
            w.wrap(text, ui, k, max)
            for (i in 0 until w.lines) {
                assertTrue(ui.widthOf(w.lineLength(i), k) <= max)
                assertTrue(w.lineLength(i) > 0)
                assertTrue(text[w.end[i] - 1] != ' ')
            }
            assertTrue(w.widestWidth <= max)
        }
    }

    @Test fun nothingFits() {
        val w = TextWrap(2)
        assertEquals(0, w.wrap("Hello", ui, 1, 2))
        assertTrue(w.truncated)
        assertEquals(0, w.wrap("   ", ui, 1, 2))
        assertFalse(w.truncated)
        assertEquals(0, w.wrap("", ui, 1, 100))
    }
}
