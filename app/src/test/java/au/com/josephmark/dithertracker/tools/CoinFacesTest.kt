package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.PackedImage
import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.purity.PurityChecks
import au.com.josephmark.dithertracker.purity.RasterRenderer
import au.com.josephmark.dithertracker.purity.ScreenRig
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.type.TextRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T61: the coin's heads / tails faces. The landed coin shows its face on both faces of the panel, the
 * tumbling coin never does (no spoilers), at every S and both display speeds; every frame is pure; and
 * the design sheet `design/captures/t61-coin-faces.png` (every option on the real panel, both faces,
 * and at icon size) is written when `T61_SHEET` names a directory.
 */
class CoinFacesTest {

    /** Where [DicePainter] puts the coin in [face]'s result box. */
    private fun coinRect(l: DiceLayout, art: DiceArt, face: Int, out: IRect): IRect {
        val box = l.resultBox[face]
        val c = art.coin(true, l.rotation(face))
        val capTop = l.result.capTop * l.resultK
        val capH = l.result.capHeight * l.resultK
        val upright = capTop + (capH - c.height) / 2
        val top = if (face == 0) box.top + upright else box.bottom - upright - c.height
        val dx = (box.width - c.width) / 2
        return out.setXywh(if (face == 0) box.left + dx else box.right - dx - c.width, top, c.width, c.height)
    }

    private fun IRect.inside(o: IRect) = left >= o.left && top >= o.top && right <= o.right && bottom <= o.bottom
    private fun IRect.meets(o: IRect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom

    /** The landed coin sits in the result box, inside its face and clear of the text, both faces, S = 2..6, 1× and 2×. */
    @Test fun theCoinFitsEveryFace() {
        for (rs in 1..2) for (s in 2..6) {
            val l = DiceLayout(PlayLayout.compute(DitherGrid(1872, 1404, s)), rs)
            val art = DiceArt(l.resultK)
            val r = IRect()
            val inner = IRect()
            for (face in 0..1) {
                val f = l.faces[face]
                inner.set(f.left + 1, f.top + 1, f.right - 1, f.bottom - 1)
                coinRect(l, art, face, r)
                assertTrue("${rs}x S=$s face $face $r in ${l.resultBox[face]}", r.inside(l.resultBox[face]))
                assertTrue("${rs}x S=$s face $face", r.inside(inner))
                assertFalse(r.meets(l.textCol[face]))
                if (face == 1) assertEquals(l.reflect(coinRect(l, art, 0, IRect()), IRect()), r)
            }
        }
    }

    /** Pixels of [img] that differ from the render at [at]. */
    private fun mismatch(r: RasterRenderer, img: MonoImage, at: IRect): Int {
        var n = 0
        for (y in 0 until at.height) for (x in 0 until at.width) {
            if ((r[at.left + x, at.top + y] == RasterRenderer.INK) != img.isInk(x, y)) n++
        }
        return n
    }

    private fun flipUntil(rig: ScreenRig, outcome: Int) {
        var tries = 0
        while (true) {
            rig.tap(rig.dice, Stage.SCREEN, DiceLayout.COIN)
            if (rig.dice.state.outcome == outcome) return
            rig.tap(rig.dice, Stage.SCREEN, DiceLayout.COIN) // skip to the result
            check(++tries < 500) { "outcome $outcome never came up" }
        }
    }

    /**
     * Heads and tails landings at both speeds and S = 2..6: every keyframe pure (global phase, the
     * inverted render the exact complement); while the coin tumbles neither face is in the result box,
     * from the landing on the right face is there, upright on each face of the panel.
     */
    @Test fun landingsShowTheFaceAndTumblesDoNot() {
        for (s in 2..6) {
            val rig = ScreenRig(1872, 1404, s)
            rig.openFromMenu(MenuItem.DICE)
            val l = rig.dice.layout!!
            val art = DiceArt(l.resultK)
            val at = IRect()
            for (speed in intArrayOf(DisplaySpeed.SLOW, DisplaySpeed.FAST)) try {
                DisplaySpeed.override = speed
                for (o in intArrayOf(Outcome.HEADS, Outcome.TAILS)) {
                    flipUntil(rig, o)
                    val p = rig.dice.state.presentation!!
                    val heads = o == Outcome.HEADS
                    val area = art.box * art.box
                    for (beat in 0..p.beats(o)) { // the last: the settled result
                        val what = "S=$s ${DisplaySpeed.name(speed)} ${p.scripts[o].name} beat $beat"
                        val r = rig.render(false)
                        val normal = r.px.copyOf()
                        assertTrue(what, PurityChecks.phaseErrors(r).isEmpty())
                        assertNull(what, PurityChecks.complementError(normal, rig.render(true).px, r.width))
                        val r2 = rig.render(false)
                        val landed = beat == p.beats(o) || p.scripts[o].beats[beat].result != Beat.NONE
                        for (face in 0..1) {
                            coinRect(l, art, face, at)
                            val rot = l.rotation(face)
                            val mine = mismatch(r2, art.coin(heads, rot), at)
                            val other = mismatch(r2, art.coin(!heads, rot), at)
                            if (landed) {
                                // Only the landing's twinkle / impact may touch the coin.
                                assertTrue("$what face $face: $mine of $area off", mine < area / 12)
                                assertTrue("$what face $face", other > mine + area / 20)
                            } else {
                                assertTrue("$what face $face shows heads", mismatch(r2, art.coin(true, rot), at) > area / 4)
                                assertTrue("$what face $face shows tails", mismatch(r2, art.coin(false, rot), at) > area / 4)
                            }
                        }
                        if (beat < p.beats(o)) rig.advance(p.holdMs(o, beat))
                    }
                }
            } finally {
                DisplaySpeed.override = DisplaySpeed.NO_OVERRIDE
            }
        }
    }

    // ---- the T61 design sheet --------------------------------------------------------------------

    private fun packed(rows: Array<String>): MonoImage = PackedImage(rows[0].length, rows.size, PackedImage.pack(rows))

    private class Option(val label: String, val faces: CoinFaces)

    private val options = listOf(
        Option("a  imp face / imp's tail (built)", CoinFaces.SHIPPED),
        Option("b  skull / shooting star", CoinFaces(
            packed(CoinFacesT61.HEADS_B_COIN), packed(CoinFacesT61.TAILS_B_COIN), packed(CoinFacesT61.HEADS_B_ICON), packed(CoinFacesT61.TAILS_B_ICON),
        )),
        Option("c  fish head / fish tail", CoinFaces(
            packed(CoinFacesT61.HEADS_C_COIN), packed(CoinFacesT61.TAILS_C_COIN), packed(CoinFacesT61.HEADS_C_ICON), packed(CoinFacesT61.TAILS_C_ICON),
        )),
    )

    @Test fun shippedOptionIsTheCandidateA() {
        val c = CoinFaces.SHIPPED
        val a = listOf(CoinFacesT61.HEADS_A_COIN, CoinFacesT61.TAILS_A_COIN, CoinFacesT61.HEADS_A_ICON, CoinFacesT61.TAILS_A_ICON)
        for ((img, rows) in listOf(c.heads, c.tails, c.headsIcon, c.tailsIcon).zip(a)) {
            for (y in rows.indices) for (x in rows[y].indices) assertEquals(rows[y][x] == '#', img.isInk(x, y))
        }
        assertTrue(Icon.COIN_HEADS === c.headsIcon && Icon.COIN_TAILS === c.tailsIcon)
    }

    /** Each option on the real panel (S = 4, slow display): the heads and the tails landing, then the icons. */
    @Test fun everyOptionDrawsCleanAndMakesTheSheet() {
        val rig = ScreenRig(1872, 1404, 4)
        DisplaySpeed.override = DisplaySpeed.SLOW
        val crops = ArrayList<List<ByteArray>>()
        val crop = IRect()
        try {
            rig.openFromMenu(MenuItem.DICE)
            val l = rig.dice.layout!!
            crop.set(l.panelOuter.left, l.panelOuter.top, l.panelOuter.right, l.panelOuter.bottom)
            for (opt in options) {
                rig.dice.coinFaces = opt.faces
                val frames = ArrayList<ByteArray>()
                for (o in intArrayOf(Outcome.HEADS, Outcome.TAILS)) {
                    flipUntil(rig, o)
                    val p = rig.dice.state.presentation!!
                    for (beat in 0 until p.beats(o) - 1) rig.advance(p.holdMs(o, beat))
                    frames += cut(rig, crop) // the landing
                    rig.advance(p.holdMs(o, p.beats(o) - 1))
                }
                crops += frames
            }
        } finally {
            rig.dice.coinFaces = CoinFaces.SHIPPED
            DisplaySpeed.override = DisplaySpeed.NO_OVERRIDE
        }
        val labelW = 0
        val gap = 8
        val icon = Icon.SIZE
        val iconsW = 4 * (icon + gap)
        val rowH = 12 + crop.height + gap
        val w = gap + labelW + 2 * (crop.width + gap) + iconsW + gap
        val h = 14 + options.size * rowH
        val grid = DitherGrid(w * 4, h * 4, 4)
        val r = RasterRenderer(grid)
        r.clear()
        val text = TextRenderer(r)
        text.draw(Fonts.UI, "T61 coin faces: heads lands, tails lands, icons, inverted", gap, 4, 1)
        for ((i, opt) in options.withIndex()) {
            val y = 14 + i * rowH
            text.draw(Fonts.UI, opt.label, gap, y, 1)
            for ((j, f) in crops[i].withIndex()) {
                val img = object : MonoImage {
                    override val width = crop.width
                    override val height = crop.height
                    override fun isInk(x: Int, y: Int) = f[y * crop.width + x] == RasterRenderer.INK
                }
                r.blit(img, gap + j * (crop.width + gap), y + 12, opaque = true)
            }
            val ix = gap + 2 * (crop.width + gap)
            val iy = y + 12 + (crop.height - icon) / 2
            r.blit(opt.faces.headsIcon, ix, iy)
            r.blit(opt.faces.tailsIcon, ix + icon + gap, iy)
            r.rect(ix + 2 * (icon + gap) - 2, iy - 2, 2 * icon + gap + 4, icon + 4, ink = true)
            r.blit(opt.faces.headsIcon, ix + 2 * (icon + gap), iy, invert = true)
            r.blit(opt.faces.tailsIcon, ix + 3 * (icon + gap), iy, invert = true)
        }
        assertTrue(PurityChecks.phaseErrors(r).isEmpty())
        val dir = System.getenv("T61_SHEET") ?: return
        val img = java.awt.image.BufferedImage(w * 4, h * 4, java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
        for (yy in 0 until h * 4) for (xx in 0 until w * 4) img.setRGB(xx, yy, if (r[xx / 4, yy / 4] == RasterRenderer.INK) 0 else 0xFFFFFF)
        javax.imageio.ImageIO.write(img, "png", File(dir, "t61-coin-faces.png").also { it.parentFile.mkdirs() })
    }

    private fun cut(rig: ScreenRig, c: IRect): ByteArray {
        val r = rig.render(false)
        assertTrue(PurityChecks.phaseErrors(r).isEmpty())
        val b = ByteArray(c.width * c.height)
        for (y in 0 until c.height) for (x in 0 until c.width) b[y * c.width + x] = r[c.left + x, c.top + y]
        return b
    }
}
