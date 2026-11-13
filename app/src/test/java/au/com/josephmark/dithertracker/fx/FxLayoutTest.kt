package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.IRect
import org.junit.Assert.assertEquals
import org.junit.Test

class FxLayoutTest {

    private val pow = Fx.byName("pow")!!

    /** Independent reference: clockwise rotation of pixel (u, v) in a W×H box (as TextLayoutTest). */
    private fun refX(rot: Int, w: Int, h: Int, u: Int, v: Int) = when (rot) {
        0 -> u; 90 -> h - 1 - v; 180 -> w - 1 - u; else -> v
    }

    private fun refY(rot: Int, w: Int, h: Int, u: Int, v: Int) = when (rot) {
        0 -> v; 90 -> u; 180 -> h - 1 - v; else -> w - 1 - u
    }

    @Test fun uprightSeatPutsFrameCentreAtRefPointPlusOffset() {
        val box = IRect(100, 50, 140, 80) // 40×30 ink box
        val out = FxLayout.place(60, 60, FxAnchorRef.TR, -6, 2, box, 0, IRect()) // a 60×60 frame, anchor tr -6 2
        // tr = (140, 50); + (-6, 2) = (134, 52); minus half the frame (30, 30).
        assertEquals(IRect(104, 22, 164, 82), out)
    }

    @Test fun rotated180SeatMirrorsTheAnchorThroughTheBox() {
        val box = IRect(100, 50, 140, 80)
        val out = FxLayout.place(60, 60, FxAnchorRef.TR, -6, 2, box, 180, IRect())
        // Upright local centre (34, 2) turns to (40-34, 30-2) = (6, 28) → screen (106, 78).
        assertEquals(IRect(76, 48, 136, 108), out)
        // Its upright top-right corner is now at the bottom-left of the box, as the seat sees it.
    }

    @Test fun everyRefOnEveryRotationEqualsRotatingTheUprightComposite() {
        // A small asymmetric frame (an L of pixels) so a wrong rotation can't hide.
        val fw = 6; val fh = 4
        val pixels = listOf(0 to 0, 1 to 0, 0 to 1, 0 to 2, 5 to 3)
        val lw = 23; val lh = 11
        for (ref in FxAnchorRef.entries) for (rot in intArrayOf(0, 90, 180, 270)) {
            val dx = 3; val dy = -5
            // Upright composite in local box coords: frame top-left, then each pixel.
            val ux = FxLayout.refCoord(ref.rx, lw) + dx - fw / 2
            val uy = FxLayout.refCoord(ref.ry, lh) + dy - fh / 2
            // Expected: rotate each composite pixel about the whole local box.
            val sideways = rot == 90 || rot == 270
            val bx = 300; val by = 200
            val box = IRect().setXywh(bx, by, if (sideways) lh else lw, if (sideways) lw else lh)
            val expected = pixels.map { (u, v) -> (bx + refX(rot, lw, lh, ux + u, uy + v)) to (by + refY(rot, lw, lh, ux + u, uy + v)) }.toSet()
            // Actual: place the footprint, then blit-rotate each frame pixel inside it.
            val out = FxLayout.place(fw, fh, ref, dx, dy, box, rot, IRect())
            val actual = pixels.map { (u, v) -> (out.left + refX(rot, fw, fh, u, v)) to (out.top + refY(rot, fw, fh, u, v)) }.toSet()
            assertEquals("ref ${ref.id} rot $rot", expected, actual)
            assertEquals(if (sideways) fh else fw, out.width)
            assertEquals(if (sideways) fw else fh, out.height)
        }
    }

    // ---- anchor tile (round two) ----

    /** A 233×175 seat tile: render2.py's SEAT_BL (upright) and SEAT_TR (turned 180). */
    private val tileBl = IRect().setXywh(0, 176, 233, 175)
    private val tileTr = IRect().setXywh(234, 0, 233, 175)
    private val someInkBox = IRect(80, 230, 150, 290)

    @Test fun tileScaleFrameCoversTheTileOnBothSeats() {
        for (spec in FxOptions.all.filter { it.isTileScale && it.anchorDx == 0 && it.anchorDy == 0 }) {
            assertEquals(spec.id, tileBl, FxLayout.place(spec, someInkBox, tileBl, 0, IRect()))
            assertEquals(spec.id, tileTr, FxLayout.place(spec, someInkBox, tileTr, 180, IRect()))
        }
    }

    @Test fun tileAnchorOffsetsAreSeatUprightAndTurnWithTheSeat() {
        // A 21×11 frame at tile centre (116, 87) + (4, -2) = (120, 85): upright top-left (110, 80).
        val up = FxLayout.place(21, 11, FxAnchorRef.TILE, 4, -2, tileBl, 0, IRect())
        assertEquals(IRect().setXywh(0 + 110, 176 + 80, 21, 11), up)
        // Turned 180 the local rect [110,131)×[80,91) lands at (233-131, 175-91) = (102, 84).
        val down = FxLayout.place(21, 11, FxAnchorRef.TILE, 4, -2, tileTr, 180, IRect())
        assertEquals(IRect().setXywh(234 + 102, 0 + 84, 21, 11), down)
        // Sideways (90°): local (110, 80) of the 233×175 tile → screen x = 175-80-11 = 84, y = 110.
        val side = IRect().setXywh(500, 0, 175, 233)
        assertEquals(IRect().setXywh(500 + 84, 110, 11, 21), FxLayout.place(21, 11, FxAnchorRef.TILE, 4, -2, side, 90, IRect()))
    }

    @Test fun inkBoxAnchoredEffectsIgnoreTheTileRect() {
        val withTile = FxLayout.place(pow, IRect(100, 50, 140, 80), tileTr, 180, IRect())
        assertEquals(FxLayout.place(pow, IRect(100, 50, 140, 80), 180, IRect()), withTile)
        val clawCut = FxOptions.byName("claw-cut")!!
        assertEquals(FxLayout.place(clawCut, someInkBox, 0, IRect()), FxLayout.place(clawCut, someInkBox, tileBl, 0, IRect()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun tileAnchoredEffectWithoutTileRectIsRejected() {
        FxLayout.place(FxOptions.byName("quake")!!, someInkBox, 0, IRect())
    }

    @Test fun textInkBoxIsTheCapBandTurnedWithTheText() {
        // A 30×20 text box whose cap band is rows [4, 16).
        val up = FxLayout.textInkBox(IRect(10, 10, 40, 30), 0, 4, 12, IRect())
        assertEquals(IRect(10, 14, 40, 26), up)
        val down = FxLayout.textInkBox(IRect(10, 10, 40, 30), 180, 4, 12, IRect())
        assertEquals(IRect(10, 14, 40, 26), down) // 20 - 4 - 12 = 4: symmetric band
        val side = FxLayout.textInkBox(IRect(10, 10, 30, 40), 90, 3, 12, IRect())
        // 90°: local row v maps to screen column h-1-v, so band rows [3,15) → columns [5,17).
        assertEquals(IRect(15, 10, 27, 40), side)
    }
}
