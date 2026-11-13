package au.com.josephmark.dithertracker.seat

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.widgets.RasterCanvas
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Renders `design/captures/t44-seat-glyph-specimen.png` (T44): every seat (and the empty table) at 16
 * and 24 px, the flush fill (chosen) against a 1-px inset, and what a reader on each side sees.
 * Runs only with `T44_SPECIMEN=<out.png>` in the environment:
 * `T44_SPECIMEN=$PWD/design/captures/t44-seat-glyph-specimen.png ./gradlew :app:testDebugUnitTest --tests '*SeatGlyphSpecimen*'`.
 */
class SeatGlyphSpecimen {

    @Test fun render() {
        val out = System.getenv("T44_SPECIMEN")
        assumeTrue(out != null)
        val panels = PlayLayout.compute(DitherGrid(1872, 1404, 4)).panels
        val glyphs = SeatGlyphs.forPanels(panels)
        val c = RasterCanvas(W, H)
        val ui = Fonts.UI
        var y = 4
        fun label(text: String) {
            c.text(ui, text, 4, y, 1, 0, invert = false, opaque = false, pad = 0)
            y += ui.height(1) + 3
        }
        fun row(size: Int, inset: Int) {
            val map = glyphs.map(size)
            var x = 4
            for (seat in 0 until 4) {
                c.blit(SeatGlyph(map, seat, 0, 1, inset), x, y, 0, invert = false, opaque = false)
                x += size + 8
            }
            c.blit(SeatGlyph(map, SeatGlyphs.EMPTY, 0, 1, inset), x + 8, y, 0, invert = false, opaque = false)
            y += size + 6
        }
        label("FLUSH 16, CHOSEN: SEATS 0 1 2 3 AND EMPTY")
        row(16, 0)
        label("FLUSH 24")
        row(24, 0)
        label("INSET 16")
        row(16, 1)
        label("INSET 24")
        row(24, 1)
        // What each reader sees (glyph in their own frame): seat 0 (top-left of the screen) filled.
        label("SEAT 0 IN THE READER FRAME AT 0 90 180 270")
        var x = 4
        for (rot in intArrayOf(0, 90, 180, 270)) {
            val g = glyphs.glyph(0, rot, 24)
            c.blit(g, x, y, 0, invert = false, opaque = false) // the local image, upright: the reader's view
            x += 32
        }
        File(out!!).also { it.parentFile.mkdirs() }.let { c.writePng(it, ZOOM) }
    }

    private companion object {
        const val W = 264
        const val H = 196
        const val ZOOM = 8
    }
}
