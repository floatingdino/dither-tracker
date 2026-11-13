package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.type.Fonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalancedWrapTest {
    private val ui = Fonts.UI

    /** Wraps [text] at [chars] `ui` glyphs a line into "a / b". */
    private fun wrap(text: String, chars: Int, lines: Int = 3): String {
        val w = BalancedWrap(lines)
        val n = w.wrap(text, ui, 1, ui.widthOf(chars, 1))
        return (0 until n).joinToString(" / ") { text.substring(w.start[it], w.end[it]) }
    }

    @Test fun oneLineWhenItFits() {
        assertEquals("Yikes", wrap("Yikes", 13))
        assertEquals("Fair and square", wrap("  Fair and square ", 15))
        assertEquals("", wrap("   ", 10))
    }

    @Test fun twoLinesOfSimilarLength() {
        // Greedy would give "Make of that what / you will" at 17 and leave "will" alone at 22.
        assertEquals("Make of that / what you will", wrap("Make of that what you will", 17))
        assertEquals("Make of that / what you will", wrap("Make of that what you will", 22))
        assertEquals("Heads you / win, probably", wrap("Heads you win, probably", 15))
        assertEquals("Could have / been worse", wrap("Could have been worse", 20))
    }

    @Test fun neverOrphansAShortWordWhenAMoreEvenBreakExists() {
        // Greedy at 13: "Tails. Sorry," / "not sorry" is fine; at 20 greedy would orphan nothing,
        // but at 18 a greedy wrap of this one leaves "it" alone.
        assertEquals("Somebody / breathed on it", wrap("Somebody breathed on it", 21))
        assertEquals("Don't touch / it again", wrap("Don't touch it again", 18))
    }

    @Test fun fewestLinesFirstThenBalanced() {
        assertEquals("one two / three four / five six", wrap("one two three four five six", 10))
        assertEquals("aaaa bbbb / cccc dddd", wrap("aaaa bbbb cccc dddd", 14, lines = 2))
    }

    @Test fun overlongTextFallsBackToTheGreedyCut() {
        val w = BalancedWrap(2)
        val t = "one two three four five six seven"
        assertEquals(2, w.wrap(t, ui, 1, ui.widthOf(9, 1)))
        assertTrue(w.truncated)
        // A word wider than the line is hard-broken (greedy).
        val v = BalancedWrap(2)
        v.wrap("Supercalifragilistic", ui, 1, ui.widthOf(10, 1))
        assertEquals(10, v.lineLength(0))
        assertFalse(BalancedWrap(1).also { it.wrap("ok", ui, 1, ui.widthOf(5, 1)) }.truncated)
    }
}
