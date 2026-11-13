package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.widgets.WidgetTesting.assertFingertip
import au.com.josephmark.dithertracker.widgets.WidgetTesting.inside
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmPromptTest {

    private val m4 = WidgetMetrics(4)
    private val text = "Start a new game? Everyone goes back to 40."

    private fun prompt() = ConfirmPrompt(30, text, confirmLabel = "RESET", cancelLabel = "CANCEL")

    @Test fun oneLineSizedToContentAndCentred() {
        val p = prompt()
        p.layout(m4, 0, 0, 300, 200)
        assertEquals(1, p.wrap.lines)
        assertFalse(p.wrap.truncated)
        // 43 glyphs = 257 px of text; padding 6 + stroke 1 each side.
        assertEquals(IRect(14, 72, 14 + 271, 72 + 56), p.box)
        assertEquals(IRect(21, 79, 21 + 257, 79 + 9), p.lineRects[0])
        assertTrue(p.lineRects[1].isEmpty)
        // Equal buttons fill the content width: cancel left, confirm right.
        assertEquals(p.cancel.bounds.width, p.confirm.bounds.width)
        assertEquals(21, p.cancel.bounds.left); assertEquals(21 + 257, p.confirm.bounds.right)
        assertEquals(p.box.bottom - 1 - 6, p.confirm.bounds.bottom)
        assertEquals(56, p.naturalHeight(m4, 300))
    }

    @Test fun narrowRectWrapsToTwoLines() {
        val p = prompt()
        p.layout(m4, 0, 0, 150, 200)
        assertEquals(2, p.wrap.lines)
        assertTrue(p.wrap.truncated) // "40." does not fit: cut at a word
        assertEquals("Start a new game?", text.substring(p.wrap.start[0], p.wrap.end[0]))
        assertEquals("Everyone goes back to", text.substring(p.wrap.start[1], p.wrap.end[1]))
        assertEquals(125 + 14, p.box.width)
        assertEquals(2 + 6 + 9 + 3 + 9 + 6 + 27 + 6, p.box.height)
        assertEquals(p.lineRects[0].bottom + 3, p.lineRects[1].top)
    }

    @Test fun shortPromptKeepsButtonsAtNaturalWidth() {
        val p = ConfirmPrompt(0, "Sure?", "YES", "NO")
        p.layout(m4, 0, 0, 400, 300)
        // The buttons drive the width: "YES" is 17 px + padding 14 = 31, both buttons take the wider.
        assertEquals(2 * 31 + 4 + 14, p.box.width)
        assertEquals(p.confirm.bounds.width, p.cancel.bounds.width)
    }

    @Test fun buttonsAreFingertipAndInsideAtEveryScale() {
        for (s in WidgetTesting.scales) {
            val m = WidgetMetrics(s)
            val p = prompt()
            val w = 300 * 4 / s
            p.layout(m, 0, 0, w, 250 * 4 / s)
            assertTrue(inside(p.box, p.bounds))
            for (b in listOf(p.cancel, p.confirm)) {
                assertFingertip(m, b.bounds, "prompt button")
                assertTrue(inside(b.bounds, p.box))
                assertEquals(b.label.length, b.labelChars)
            }
            for (i in 0 until p.wrap.lines) {
                assertTrue(inside(p.lineRects[i], p.box))
                assertTrue(m.ui.widthOf(p.wrap.lineLength(i), m.k) <= p.lineRects[i].width)
            }
        }
    }

    @Test fun hitIdsAndFallback() {
        val p = prompt()
        p.layout(m4, 0, 0, 300, 120)
        val f = WidgetFrame().set(0, 0, 300, 120, 0)
        val r = HitRegion()
        p.collectHits(r, f)
        assertEquals(2, r.size)
        assertEquals(30 + ConfirmPrompt.CONFIRM, r.hitTest(p.confirm.bounds.left + 1, p.confirm.bounds.top + 1))
        assertEquals(30 + ConfirmPrompt.CANCEL, r.hitTest(p.cancel.bounds.left + 1, p.cancel.bounds.top + 1))
        assertEquals(HitRegion.NONE, r.hitTest(p.box.left + 2, p.box.top + 2)) // the text is not a button
        assertTrue(p.owns(31)); assertFalse(p.owns(32))
        WidgetTesting.assertFallbackMatchesInvert(300, 120, r) { it.draw(f, p) }
    }

    @Test fun rotationIsExact() {
        val p = prompt()
        p.layout(m4, 0, 0, 161, 101)
        WidgetTesting.assertRotationExact(161, 101) { pt, f -> pt.draw(f, p) }
    }
}
