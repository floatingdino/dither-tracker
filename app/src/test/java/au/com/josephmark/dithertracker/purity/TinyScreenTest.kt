package au.com.josephmark.dithertracker.purity

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.play.PlayLayout
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T62: windows far smaller or odder than any screen the layouts were drawn for — a phone's split-screen
 * half, a freeform window, a tiny or empty view — lay out, draw every overlay and take taps without a
 * crash or an exception (the layouts are not comfortable there; they only have to survive).
 */
class TinyScreenTest {

    private val windows = listOf(
        2400 to 520, 520 to 2400, // a phone's split-screen half (landscape / portrait)
        1080 to 1100, 1200 to 1080, // a near-square half of a tablet or foldable
        640 to 360, 480 to 320, 320 to 240, 200 to 150, 100 to 100, 40 to 30, 8 to 8, 1 to 1, 0 to 0,
    )

    @Test fun everyWindowSurvives() {
        for ((w, h) in windows) for (dpi in listOf(160, 300, 560)) for (p in listOf(2, 4, 6)) {
            val s = PlayLayout.chooseScale(w, h, dpi, DitherGrid.AUTO, p)
            assertTrue("$w x $h S=$s", s >= DitherGrid.MIN_SCALE)
            val tag = "${w}x$h @$dpi ${p}p S=$s"
            try {
                val rig = ScreenRig(w, h, s, players = p)
                rig.render(false)
                val l = rig.l
                rig.tapAt(l.diamondCx, l.diamondCy)
                rig.render(false)
                for (item in listOf(MenuItem.SETUP, MenuItem.LOG, MenuItem.DICE)) {
                    val r2 = ScreenRig(w, h, s, players = p)
                    r2.openFromMenu(item)
                    r2.render(false)
                    r2.render(true)
                }
                for (seat in 0 until p) {
                    val t = l.tabHit[seat]
                    rig.tapAt((t.left + t.right) / 2, (t.top + t.bottom) / 2)
                }
                rig.render(false)
            } catch (e: Throwable) {
                throw AssertionError("$tag: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }
    }
}
