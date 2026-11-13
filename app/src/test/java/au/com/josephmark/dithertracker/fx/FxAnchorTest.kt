package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Tile
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The shipped effects land where `design/fx/options/render2.py` drew them: every frame composited
 * through [FxRenderer] against the **real** layout (the Nomad's 1872×1404 at S = 4, the hero number's
 * real [TileSlots.numberFootprint]) puts each symbol on exactly the screen pixels render2's
 * `frame_origin` + `to_screen` + tile clip give, for an upright seat and a turned one, and for 1-, 2-
 * and 3-digit totals. render2 anchors on the sticker box (`n·32 + (n−1)·4` × 56, centred on the tile
 * centre), which is the number's whole footprint — not the cap band.
 */
class FxAnchorTest {

    private val l = PlayLayout.compute(DitherGrid(1872, 1404, 4))
    private val tiles = TileSlots(l)

    // render2.py constants.
    private val tw = 233
    private val th = 175
    private val tcx = 116
    private val tcy = 87

    private fun boxW(n: String) = n.length * 32 + (n.length - 1) * 4

    /** Records every pixel each layer kind sets, clipped to [clip] (as the panel clip does). */
    private class Rec(val clip: IRect) : FxRenderer.Target {
        val px = HashMap<String, MutableSet<Pair<Int, Int>>>()
        private fun put(k: String, x: Int, y: Int) {
            if (clip.contains(x, y)) px.getOrPut(k) { HashSet() } += x to y
        }

        override fun blit(img: MonoImage, x: Int, y: Int, rotation: Int, paper: Boolean) {
            for (v in 0 until img.height) for (u in 0 until img.width) if (img.isInk(u, v)) {
                val (sx, sy) = when (rotation) {
                    0 -> u to v
                    90 -> img.height - 1 - v to u
                    180 -> img.width - 1 - u to img.height - 1 - v
                    else -> v to img.width - 1 - u
                }
                put(if (paper) "o" else "#", x + sx, y + sy)
            }
        }

        override fun invert(mask: MonoImage, x: Int, y: Int, underlay: Runnable?) {
            for (v in 0 until mask.height) for (u in 0 until mask.width) if (mask.isInk(u, v)) put("x", x + u, y + v)
        }

        override fun tile(mask: MonoImage, x: Int, y: Int, tile: Tile, mode: FxTileMode) {
            for (v in 0 until mask.height) for (u in 0 until mask.width) if (mask.isInk(u, v)) put("t", x + u, y + v)
        }
    }

    /** render2.py: `frame_origin` (tile-local, upright) for an effect over [number]. */
    private fun origin(fx: FxSpec, number: String): Pair<Int, Int> {
        if (fx.anchorRef == FxAnchorRef.TILE) return (tcx + fx.anchorDx - fx.width / 2) to (tcy + fx.anchorDy - fx.height / 2)
        val bw = boxW(number)
        return (tcx + fx.anchorRef.rx * (bw / 2) + fx.anchorDx - fx.width / 2) to
            (tcy + fx.anchorRef.ry * (56 / 2) + fx.anchorDy - fx.height / 2)
    }

    /** render2.py's composite of [frame] for a seat whose tile is at [panel], turned 180 when [rot]. */
    private fun expected(fx: FxSpec, frame: Int, number: String, panel: IRect, rot: Boolean): Map<String, Set<Pair<Int, Int>>> {
        val (x0, y0) = origin(fx, number)
        val out = HashMap<String, MutableSet<Pair<Int, Int>>>()
        fun mark(k: String, layer: FxLayer) {
            for (y in 0 until fx.height) for (x in 0 until fx.width) if (layer.isInk(x, y)) {
                val lx = x0 + x; val ly = y0 + y
                if (lx !in 0 until tw || ly !in 0 until th) continue
                val s = if (rot) (panel.left + tw - 1 - lx) to (panel.top + th - 1 - ly) else (panel.left + lx) to (panel.top + ly)
                out.getOrPut(k) { HashSet() } += s
            }
        }
        mark("#", fx.ink(frame)); mark("o", fx.knockout(frame)); mark("x", fx.cut(frame))
        for (i in fx.tiles.indices) mark("t", fx.tileLayer(i, frame))
        return out
    }

    @Test fun theLayoutIsTheOneTheDesignsWereDrawnFor() {
        for (s in 0 until 4) assertEquals(tw to th, l.panels[s].width to l.panels[s].height)
        assertEquals(180, l.rotation[1]); assertEquals(0, l.rotation[3])
        // The hero number's footprint is render2's sticker box, centred on (116, 87) of the tile.
        for (n in listOf(8, 37, 108)) {
            val fp = IRect()
            assertEquals("k", 1, tiles.numberFootprint(3, n, fp))
            val bw = boxW(n.toString())
            assertEquals("$n upright", IRect().setXywh(l.panels[3].left + tcx - bw / 2, l.panels[3].top + tcy - 28, bw, 56), fp)
            tiles.numberFootprint(1, n, fp)
            val p = l.panels[1]
            // Turned: local box [116 - bw/2, 116 + bw/2) maps to [p.left + 233 - (116 + bw/2), …).
            assertEquals("$n turned", IRect().setXywh(p.left + tw - (tcx - bw / 2) - bw, p.top + th - (tcy - 28) - 56, bw, 56), fp)
        }
    }

    @Test fun everyShippedFrameLandsWhereRender2DrewIt() {
        val box = IRect()
        for (fx in Fx.entries) for (seat in intArrayOf(3, 1)) for (n in intArrayOf(8, 37, 108)) {
            val panel = l.panels[seat]
            tiles.numberFootprint(seat, n, box)
            for (f in 0 until fx.frameCount) {
                val rec = Rec(panel)
                FxRenderer(rec).draw(fx.spec, f, box, panel, l.rotation[seat], panel)
                val want = expected(fx.spec, f, n.toString(), panel, l.rotation[seat] == 180)
                assertEquals("${fx.id} seat $seat ($n) frame ${f + 1}", want, rec.px)
            }
        }
    }

    @Test fun theCapBandWouldMisplaceTheDesigns() {
        // FxLayout.textInkBox (the cap band, rows 0..53 of the 56-row sticker) is not the anchor box.
        val fp = IRect()
        val k = tiles.numberFootprint(3, 37, fp)
        val band = FxLayout.textInkBox(fp, 0, l.font.capTop * k, l.font.capHeight * k, IRect())
        val crack = Fx.byName("crack")!! // anchor centre
        assertNotEquals(
            FxLayout.place(crack, fp, l.panels[3], 0, IRect()),
            FxLayout.place(crack, band, l.panels[3], 0, IRect()),
        )
    }
}
