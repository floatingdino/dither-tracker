package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.type.Fonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFitTest {

    private val ui = Fonts.UI

    private fun cut(text: String, chars: Int, k: Int = 1): String = text.substring(0, TextFit.fit(text, ui, k, ui.widthOf(chars, k)))

    @Test fun maxCharsMatchesWidthOf() {
        for (k in 1..3) for (n in 0..30) {
            val w = ui.widthOf(n, k)
            assertEquals(n, TextFit.maxChars(ui, k, w))
            if (n > 0) assertEquals(n - 1, TextFit.maxChars(ui, k, w - 1))
        }
        assertEquals(0, TextFit.maxChars(ui, 1, 0))
    }

    @Test fun fittingTextIsUntouched() {
        assertEquals("Starting life", cut("Starting life", 13))
        assertEquals("Starting life", cut("Starting life", 40))
    }

    @Test fun cutsAtWordBoundaryWhenItKeepsEnough() {
        // 12 glyphs of room: "Commander da" → word boundary at 9 (≥ 2/3 of 12) → "Commander".
        assertEquals("Commander", cut("Commander damage", 12))
    }

    @Test fun cutsAtGlyphWhenTheBoundaryIsTooEarly() {
        // 6 of room: "Starti|ng" has no earlier boundary → plain glyph cut.
        assertEquals("Starti", cut("Starting life", 6))
        // 11 of room: "Turn timer o|n": boundary at 10 keeps ≥ 2/3 → "Turn timer".
        assertEquals("Turn timer", cut("Turn timer on", 11))
        // 7 of room: "Keep sc|reen": boundary at 5 (≥ 4.67) → "Keep".
        assertEquals("Keep", cut("Keep screen awake", 7))
        // 9 of room: "Day/night": fits exactly.
        assertEquals("Day/night", cut("Day/night", 9))
        // 9 of room: "Abcdefghij" one long word → glyph cut.
        assertEquals("Abcdefghi", cut("Abcdefghij", 9))
        // 8 of room: boundary at 3 is too early (< 5.33) → "Big cros".
        assertEquals("Big cros", cut("Big crossover", 8))
    }

    @Test fun neverEndsOnASeparator() {
        assertEquals("Hold-to", cut("Hold-to-repeat step", 10))
        assertEquals("Life", cut("Life: 40", 5))
        assertEquals("Players", cut("Players / seats", 9))
        for (n in 1..20) {
            val s = cut("Full refresh every N changes", n)
            assertTrue("'$s'", s.isEmpty() || !TextFit.isSep(s.last()))
            assertTrue("'$s' over $n", s.length <= n)
        }
    }

    @Test fun nothingFitsGivesEmpty() {
        assertEquals("", cut("Anything", 0))
        assertEquals(0, TextFit.fit("Anything", ui, 1, ui.glyphWidth - 1))
    }

    @Test fun charBufFormatsInts() {
        val b = CharBuf(16)
        b.append(2).append('/').append(15)
        assertEquals("2/15", b.toString())
        b.clear().append(-307).append(0)
        assertEquals("-3070", b.toString())
        b.clear().append(Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE.toString(), b.toString())
    }

    @Test fun sliceIsAWindow() {
        val s = TextSlice().set("Commander damage", 10, 16)
        assertEquals(6, s.length)
        assertEquals("damage", s.toString())
        assertEquals('d', s[0])
    }
}
