package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.purity.PurityChecks
import au.com.josephmark.dithertracker.purity.RasterRenderer
import au.com.josephmark.dithertracker.purity.ScreenRig
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.type.TextRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T52 design sheets: both roll presentations — A "tumble" (shipped) and B "dust-up" ([DiceVariantB])
 * — drawn by the real panel (S = 4, the lower face), one strip per outcome, keyframes left to right
 * and the settled result last. Always rendered and purity-checked (both variants stay valid); written
 * to `<T52_SHEET>/t52-dice-presentation.png` and `t52-coin-presentation.png` at 4× when that
 * environment variable is set (`design/captures/`).
 */
class DiceSheetTest {

    private class Strip(val label: String, val frames: List<ByteArray>)

    private fun strips(variant: Presentation, outcomes: IntArray, rig: ScreenRig, crop: IRect): List<Strip> {
        val dice = rig.dice
        dice.setPresentations(variant, variant)
        val out = ArrayList<Strip>()
        for (o in outcomes) {
            val button = when (o) {
                Outcome.D6 -> DiceLayout.D6
                Outcome.HEADS, Outcome.TAILS -> DiceLayout.COIN
                else -> DiceLayout.D20
            }
            var tries = 0
            while (true) {
                rig.tap(dice, Stage.SCREEN, button)
                if (dice.state.outcome == o) break
                rig.tap(dice, Stage.SCREEN, button)
                check(++tries < 500)
            }
            val frames = ArrayList<ByteArray>()
            for (beat in 0 until variant.beats(o)) {
                frames += cut(rig, crop)
                rig.advance(variant.holdMs(o, beat))
            }
            frames += cut(rig, crop)
            out += Strip(variant.scripts[o].name, frames)
        }
        return out
    }

    private fun cut(rig: ScreenRig, c: IRect): ByteArray {
        val r = rig.render(false)
        assertTrue(PurityChecks.phaseErrors(r).isEmpty())
        val b = ByteArray(c.width * c.height)
        for (y in 0 until c.height) for (x in 0 until c.width) b[y * c.width + x] = r[c.left + x, c.top + y]
        return b
    }

    private fun sheet(name: String, rows: List<Pair<String, List<Strip>>>, crop: IRect) {
        val labelW = 70
        val gap = 6
        val rowH = crop.height + 14
        val nFrames = rows.maxOf { r -> r.second.maxOf { it.frames.size } }
        val w = labelW + nFrames * (crop.width + gap) + gap
        val h = 10 + rows.sumOf { it.second.size } * rowH + rows.size * 14
        val grid = DitherGrid(w * 4, h * 4, 4)
        val r = RasterRenderer(grid)
        r.clear()
        val text = TextRenderer(r)
        var y = 6
        for ((title, strips) in rows) {
            text.draw(Fonts.UI, title, gap, y, 1)
            y += 14
            for (s in strips) {
                text.draw(Fonts.UI, s.label, gap + 4, y + crop.height / 2 - 4, 1)
                for ((i, f) in s.frames.withIndex()) {
                    val img = object : MonoImage {
                        override val width = crop.width
                        override val height = crop.height
                        override fun isInk(x: Int, y: Int) = f[y * crop.width + x] == RasterRenderer.INK
                    }
                    r.blit(img, labelW + i * (crop.width + gap), y, opaque = true)
                    val cap = if (i == s.frames.size - 1) "result" else "${i + 1}"
                    text.draw(Fonts.UI, cap, labelW + i * (crop.width + gap), y + crop.height + 2, 1)
                }
                y += rowH
            }
        }
        val dir = System.getenv("T52_SHEET") ?: return
        // 8-bit grey (what tools/check_1bit.py reads), 4 px per dither px.
        val img = java.awt.image.BufferedImage(w * 4, h * 4, java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
        for (yy in 0 until h * 4) for (xx in 0 until w * 4) img.setRGB(xx, yy, if (r[xx / 4, yy / 4] == RasterRenderer.INK) 0 else 0xFFFFFF)
        javax.imageio.ImageIO.write(img, "png", File(dir, name).also { it.parentFile.mkdirs() })
    }

    @Test fun bothVariantsDrawCleanAndMakeTheSheets() {
        val rig = ScreenRig(1872, 1404, 4)
        rig.openFromMenu(MenuItem.DICE)
        rig.advance(DiceState.PRESENT_MAX_MS)
        val l = rig.dice.layout!!
        val f = l.faces[0]
        val crop = IRect().set(f.left - 2, f.top - 2, f.right + 2, f.bottom + 2)
        val dice = intArrayOf(Outcome.D20, Outcome.NAT_20, Outcome.NAT_1, Outcome.D6)
        val coin = intArrayOf(Outcome.HEADS, Outcome.TAILS)
        val a = strips(Presentation.TUMBLE, dice, rig, crop)
        val b = strips(DiceVariantB.DUST_UP, dice, rig, crop)
        sheet("t52-dice-presentation.png", listOf("A  tumble across the face (built)" to a, "B  dust-up on the spot (not built)" to b), crop)
        val ca = strips(Presentation.TUMBLE, coin, rig, crop)
        val cb = strips(DiceVariantB.DUST_UP, coin, rig, crop)
        sheet("t52-coin-presentation.png", listOf("A  flip across the face (built)" to ca, "B  spin on the spot (not built)" to cb), crop)
    }
}
