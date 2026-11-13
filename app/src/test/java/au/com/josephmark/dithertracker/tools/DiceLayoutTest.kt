package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.peeks.PeekLayout
import au.com.josephmark.dithertracker.play.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceLayoutTest {

    private fun layout(s: Int) = DiceLayout(PlayLayout.compute(DitherGrid(1872, 1404, s)))

    private fun IRect.meets(o: IRect, margin: Int = 0) =
        left < o.right + margin && o.left < right + margin && top < o.bottom + margin && o.top < bottom + margin

    private fun IRect.inside(o: IRect) = left >= o.left && top >= o.top && right <= o.right && bottom <= o.bottom

    @Test fun nomadMetrics() {
        val l = layout(4)
        assertEquals(1, l.k)
        assertEquals(153, l.panel.width)
        assertEquals(147, l.panel.height)
        assertEquals(IRect(154, 99, 313, 252), l.panelOuter)
        assertEquals(27, l.fold.height)
        assertEquals(58, l.faces[0].height)
        assertEquals(IRect(161, 191, 229, 247), l.heroBox[0]) // 68×56 two-digit num-l footprint
    }

    @Test fun centredOddAndPointSymmetricAtEveryScale() {
        for (s in 2..6) {
            val l = layout(s)
            val g = l.play.grid
            assertTrue("S=$s", l.panelOuter.inside(IRect(0, 0, g.width, g.height)))
            assertEquals(1, l.panel.width % 2)
            assertEquals(1, l.panel.height % 2)
            assertEquals(l.cx, l.panel.left + l.panel.width / 2)
            assertEquals(l.cy, l.panel.top + l.panel.height / 2)
            assertEquals(l.cy, l.fold.top + l.fold.height / 2)
            // The upper face is the lower turned 180° about the crossing.
            val t = IRect()
            assertEquals(l.faces[1], l.reflect(l.faces[0], t))
            assertEquals(l.faces[0], l.reflect(l.faces[1], t))
            assertEquals(l.heroBox[1], l.reflect(l.heroBox[0], t))
            assertTrue(l.faces[1].bottom < l.fold.top && l.faces[0].top > l.fold.bottom - 1)
            for (f in 0..1) {
                assertTrue(l.heroBox[f].inside(l.faces[f]))
                assertTrue(l.textCol[f].inside(l.faces[f]))
                assertFalse(l.heroBox[f].meets(l.textCol[f]))
            }
        }
    }

    /** At every comfortable S the panel hides no ± glyph, readout, tab, peek corner or two-digit total. */
    @Test fun clearOfTheTilesAtComfortableScales() {
        for (s in 2..4) {
            val l = layout(s)
            val p = l.play
            assertTrue(p.comfortable)
            val o = l.panelOuter
            val fp = IRect()
            val chip = IRect()
            val peek = PeekLayout(p)
            for (seat in 0 until PlayLayout.PLAYERS) {
                for (v in -9..99) {
                    p.numberFootprint(seat, v, fp)
                    assertFalse("S=$s seat $seat life $v $fp vs $o", o.meets(fp))
                }
                assertFalse("S=$s minus $seat", o.meets(p.minusGlyph[seat]))
                assertFalse("S=$s plus $seat", o.meets(p.plusGlyph[seat]))
                assertFalse("S=$s readout $seat", o.meets(p.readoutSlot[seat]))
                assertFalse("S=$s tab $seat", o.meets(p.tabHit[seat]))
                assertFalse("S=$s under $seat", o.meets(p.underRow[seat]))
                for (i in 0 until peek.slots) {
                    peek.chip(seat, i, peek.colW, inverted = true, out = chip)
                    assertFalse("S=$s peek $seat/$i", o.meets(chip))
                }
            }
        }
    }

    @Test fun foldButtonsAreFingertipSizedAndClearOfTheDiamond() {
        for (s in 2..6) {
            val l = layout(s)
            for (b in 0 until DiceLayout.BUTTONS) {
                val bt = l.button[b]
                val hit = l.buttonHit[b]
                assertTrue("S=$s", bt.inside(l.fold))
                assertTrue(bt.inside(hit))
                assertFalse("S=$s button $b on the diamond", hit.meets(l.foldRect))
                if (s <= 4) {
                    assertTrue("S=$s hit ${hit.width}x${hit.height}", hit.width >= l.play.target && hit.height >= l.play.target)
                }
                for (c in b + 1 until DiceLayout.BUTTONS) assertFalse(hit.meets(l.buttonHit[c]))
                // Labels fit inside (two faces, one per half of the button).
                val t = DiceLayout.toolOf(b) ?: continue
                assertTrue("S=$s ${t.label}", l.ui.widthOf(t.label.length, l.k) <= bt.width - 2)
                assertTrue("S=$s half", l.ui.capHeight * l.k <= l.cy - (bt.top + 1))
            }
            assertTrue(l.foldRect.inside(l.fold))
            assertTrue(l.onFoldDiamond(l.cx, l.cy))
            assertFalse(l.onFoldDiamond(l.foldRect.left, l.foldRect.top))
            // Symmetric about the crossing: d20|close and d6|coin mirror.
            val sx = 2 * l.cx + 1
            assertEquals(sx - l.button[DiceLayout.D20].right, l.button[DiceLayout.CLOSE].left)
            assertEquals(sx - l.button[DiceLayout.D6].right, l.button[DiceLayout.COIN].left)
            assertEquals(l.button[DiceLayout.D20].width, l.button[DiceLayout.CLOSE].width)
        }
    }

    @Test fun resultsFit() {
        for (s in 2..6) {
            val l = layout(s)
            // Two hero digits fill the hero box; the coin and the "?" fit inside it.
            assertEquals(l.hero.widthOf(2, l.k), l.heroBox[0].width)
            assertEquals(l.hero.height(l.k), l.heroBox[0].height)
            val art = DiceArt(l.k)
            assertTrue(art.box <= l.heroBox[0].width && art.box <= l.heroBox[0].height)
            assertTrue(art.mystery[0].width <= l.heroBox[0].width && art.mystery[0].height <= l.hero.capHeight * l.k)
            // Every label fits the text column.
            for (label in listOf("d20", "d6", "coin", "HEADS", "TAILS")) {
                assertTrue(l.ui.widthOf(label.length, l.k) <= l.textCol[0].width)
            }
        }
    }

    /** Every quip core can hand the panel wraps whole, inside the face, at every S. */
    @Test fun everyCaptionWrapsWholeInsideTheFace() {
        val kinds = listOf(QuipKind.D20_NATURAL_1, QuipKind.D20_NATURAL_20, QuipKind.DICE_ROLL, QuipKind.COIN_HEADS, QuipKind.COIN_TAILS)
        for (s in 2..6) {
            val l = layout(s)
            for (kind in kinds) for (q in Flavour.quippy.lines(kind)) {
                l.arrange("HEADS", q)
                assertFalse("S=$s '$q'", l.truncated)
                assertTrue(l.lines in 1..DiceLayout.MAX_LINES)
                for (f in 0..1) {
                    assertTrue("S=$s '$q' label", l.label[f].inside(l.textCol[f]))
                    assertTrue(l.underline[f].inside(l.textCol[f]))
                    for (i in 0 until l.lines) {
                        val r = l.line[f * DiceLayout.MAX_LINES + i]
                        assertTrue("S=$s '$q' line $i $r in ${l.textCol[f]}", r.inside(l.textCol[f]))
                    }
                }
                // Lines never overlap each other or the label.
                for (i in 0 until l.lines) {
                    assertFalse(l.line[i].meets(l.underline[0]))
                    if (i > 0) assertFalse(l.line[i].meets(l.line[i - 1]))
                }
            }
        }
    }

    @Test fun coinArtIsASymmetricDiscWithAShadowDownRight() {
        val a = DiceArt(1)
        val up = a.coin(true, 0)
        val turned = a.coin(true, 180)
        val sh = a.coinShadow(0)
        var ink = 0
        for (y in 0 until a.box) for (x in 0 until a.box) {
            assertEquals(up.isInk(x, y), turned.isInk(a.box - 1 - x, a.box - 1 - y))
            if (up.isInk(x, y)) { ink++; assertFalse("shadow under ink at $x,$y", sh.isInk(x, y)) }
        }
        assertTrue(ink > 1000)
        // The shadow peeks out down-right only.
        assertTrue(sh.isInk(a.diameter / 2 + a.shadowOffset, a.diameter + a.shadowOffset - 2))
        assertFalse(sh.isInk(0, 0))
        // The letters differ.
        val tails = a.coin(false, 0)
        var diff = 0
        for (y in 0 until a.box) for (x in 0 until a.box) if (up.isInk(x, y) != tails.isInk(x, y)) diff++
        assertTrue(diff > 50)
    }
}
