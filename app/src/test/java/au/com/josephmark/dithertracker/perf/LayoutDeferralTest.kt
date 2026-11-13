package au.com.josephmark.dithertracker.perf

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayRig
import au.com.josephmark.dithertracker.settings.SettingsOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T50: the settings card (never visible at launch) is measured on first need — the post-first-frame
 * prewarm or an open — not in the layout pass before the first frame. Same card either way.
 */
class LayoutDeferralTest {

    private fun overlay(r: PlayRig) = r.stage.components.filterIsInstance<SettingsOverlay>().single()

    /** The card as the old eager layout measured it: open right after layout. */
    private fun eagerSide(s: Int, w: Int, h: Int): Int {
        val r = PlayRig(w = w, h = h, s = s)
        overlay(r).open(0)
        return overlay(r).card.side
    }

    @Test fun layoutLeavesTheCardUnmeasuredUntilOpen() {
        val r = PlayRig()
        val o = overlay(r)
        assertEquals("measured in the layout pass", 0, o.card.side)
        o.open(0)
        assertTrue(o.card.side > 0)
        assertTrue(o.card.bounds.width > 0)
    }

    @Test fun anOpenCardIsRemeasuredOnRelayout() {
        val r = PlayRig(s = 4)
        val o = overlay(r)
        o.open(90)
        val at4 = o.card.side
        r.stage.layout(PlayLayout.compute(DitherGrid(1872, 1404, 3)))
        assertTrue(o.isOpen)
        assertEquals(eagerSide(3, 1872, 1404), o.card.side)
        assertTrue(at4 != o.card.side)
        assertEquals(90, o.rotation)
    }

    @Test fun aClosedCardMeasuresForTheLatestLayout() {
        val r = PlayRig(s = 4)
        val o = overlay(r)
        r.stage.layout(PlayLayout.compute(DitherGrid(1404, 1872, 5)))
        o.open(0)
        assertEquals(eagerSide(5, 1404, 1872), o.card.side)
    }
}
