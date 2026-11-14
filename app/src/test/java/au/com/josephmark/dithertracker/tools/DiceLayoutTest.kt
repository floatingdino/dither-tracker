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

    private fun layout(s: Int, rs: Int = DiceLayout.RESULT_K) = DiceLayout(PlayLayout.compute(DitherGrid(1872, 1404, s)), rs)

    private fun IRect.meets(o: IRect, margin: Int = 0) =
        left < o.right + margin && o.left < right + margin && top < o.bottom + margin && o.top < bottom + margin

    private fun IRect.inside(o: IRect) = left >= o.left && top >= o.top && right <= o.right && bottom <= o.bottom

    @Test fun resultScaleIsTwoByDefault() {
        assertEquals(2, DiceLayout.RESULT_K)
        assertEquals(2, layout(4).resultK)
        assertEquals(4, layout(2).resultK) // tk 2 at S = 2: the same physical size
    }

    @Test fun nomadMetrics2x() {
        val l = layout(4, 2)
        assertEquals(1, l.k)
        assertFalse(l.stacked)
        assertEquals(157, l.panel.width) // the fold row's: four buttons that hold "coin"
        assertEquals(105, l.panel.height)
        assertEquals(IRect(152, 120, 315, 231), l.panelOuter) // 163×111 (was 163×153 with num-l)
        assertEquals(27, l.fold.height)
        assertEquals(37, l.faces[0].height)
        assertEquals(IRect(160, 194, 204, 222), l.resultBox[0]) // 44×28: two num-m digits at 2×
    }

    @Test fun nomadMetrics1x() {
        val l = layout(4, 1)
        assertTrue(l.stacked) // the label sits under the small result
        assertEquals(157, l.panel.width)
        assertEquals(99, l.panel.height)
        assertEquals(IRect(152, 123, 315, 228), l.panelOuter) // 163×105
        assertEquals(34, l.faces[0].height)
        assertEquals(35, l.resultBox[0].width) // as wide as "nat 20"
        assertEquals(14, l.resultBox[0].height)
    }

    @Test fun centredOddAndPointSymmetricAtEveryScale() {
        for (rs in 1..2) for (s in 2..6) {
            val l = layout(s, rs)
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
            assertEquals(l.resultBox[1], l.reflect(l.resultBox[0], t))
            assertTrue(l.faces[1].bottom < l.fold.top && l.faces[0].top > l.fold.bottom - 1)
            for (f in 0..1) {
                assertTrue(l.resultBox[f].inside(l.faces[f]))
                assertTrue(l.textCol[f].inside(l.faces[f]))
                assertFalse(l.resultBox[f].meets(l.textCol[f]))
                if (l.stacked) assertTrue(l.stackCol[f].inside(l.faces[f]) && l.resultBox[f].inside(l.stackCol[f]))
            }
        }
    }

    /**
     * At every comfortable S the panel hides no ± glyph, readout, tab, peek corner or life total — up
     * to four glyphs (`−999`..`9999`; three-glyph totals, 100+ and −10 and below, were clipped by the
     * num-l panel).
     */
    @Test fun clearOfTheTilesAtComfortableScales() {
        val lives = (-999..-90) + (-20..120) + (990..1010) + (9990..9999)
        for (rs in 1..2) for (s in 2..4) {
            val l = layout(s, rs)
            val p = l.play
            assertTrue(p.comfortable)
            val o = l.panelOuter
            val fp = IRect()
            val chip = IRect()
            val peek = PeekLayout(p)
            for (seat in 0 until PlayLayout.PLAYERS) {
                for (v in lives) {
                    p.numberFootprint(seat, v, fp)
                    assertFalse("${rs}x S=$s seat $seat life $v $fp vs $o", o.meets(fp))
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
                assertTrue("S=$s ${t.label}", t.label.length <= DiceLayout.LONGEST_BUTTON_LABEL)
                assertTrue("S=$s ${t.label} air", l.ui.widthOf(t.label.length, l.k) + 2 + 2 * PlayLayout.Chrome.size(DiceLayout.BTN_AIR, s) <= bt.width)
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

    /**
     * T52: every keyframe of the roll presentation stays inside its face (clear of the border and the
     * fold) on both faces at every S, the upper face being the lower one turned about the crossing,
     * and the landing stamp's plate too.
     */
    @Test fun presentationFitsEveryFace() {
        for (s in 2..6) {
            val l = layout(s)
            for (p in listOf(Presentation.TUMBLE, Presentation.TUMBLE_FAST)) {
            val ss = SpriteSet(p.sprites, l.k)
            val placed = PresentationLayout(l, p, ss.widths, ss.heights, l.k)
            val inner = IRect()
            for (face in 0..1) {
                val f = l.faces[face]
                inner.set(f.left + 1, f.top + 1, f.right - 1, f.bottom - 1)
                for (o in 0 until Outcome.COUNT) for (b in 0 until p.beats(o)) for (i in p.scripts[o].beats[b].places.indices) {
                    val r = placed.rect(o, b, i, face)
                    assertTrue("S=$s face $face outcome $o beat $b place $i $r in $inner", r.inside(inner))
                    if (face == 1) assertEquals(l.reflect(placed.rect(o, b, i, 0), IRect()).toString(), r.toString())
                }
            }
            // The tumbling solids are at most the result's height.
            for (i in 0 until ss.heights.size) if (ss.heights[i] > 20 * l.k) assertTrue(ss.heights[i] <= l.resultBox[0].height)
            }
            for (v in intArrayOf(1, 7, 20)) {
                val pl = l.plateFor(v, IRect())
                assertTrue("S=$s plate $pl", pl.inside(l.faces[0]) && pl.top > l.faces[0].top && pl.bottom < l.faces[0].bottom)
                assertTrue("S=$s plate clear of the text", pl.right <= l.textCol[0].left)
            }
        }
    }

    @Test fun resultsFit() {
        for (rs in 1..2) for (s in 2..6) {
            val l = layout(s, rs)
            val box = l.resultBox[0]
            // One and two num-m digits fit the result box at the result scale, at full height.
            for (v in listOf(1, 6, 9, 10, 17, 20)) {
                assertTrue("${rs}x S=$s $v", l.result.widthOf(v.toString().length, l.resultK) <= box.width)
            }
            assertEquals(l.result.height(l.resultK), box.height)
            assertEquals(14 * l.resultK, box.height)
            // The coin is exactly as tall as the result and fits beside it.
            val art = DiceArt(l.resultK)
            assertEquals(l.result.capHeight * l.resultK, art.diameter)
            assertTrue(art.box <= box.width && art.box <= box.height)
            // Every label fits (the text column beside, or the result column stacked).
            val labelCol = if (l.stacked) l.stackCol[0] else l.textCol[0]
            for (label in listOf("d20", "d6", "coin", "HEADS", "TAILS", "nat 20", "nat 1")) {
                l.arrange(label, "Yikes")
                assertTrue("${rs}x S=$s $label", l.label[0].inside(labelCol) && l.underline[0].inside(labelCol))
                assertFalse(l.label[0].meets(l.resultBox[0]))
            }
        }
    }

    private val kinds = listOf(QuipKind.D20_NATURAL_1, QuipKind.D20_NATURAL_20, QuipKind.DICE_ROLL, QuipKind.COIN_HEADS, QuipKind.COIN_TAILS)

    /** Every quip core can hand the panel wraps whole, in ≤ 2 lines, inside the face, at every S. */
    @Test fun everyCaptionWrapsWholeInsideTheFace() {
        for (rs in 1..2) for (s in 2..6) {
            val l = layout(s, rs)
            for (kind in kinds) for (q in Flavour.quippy.lines(kind)) {
                l.arrange("HEADS", q)
                assertFalse("S=$s '$q'", l.truncated)
                assertTrue(l.lines in 1..2)
                for (f in 0..1) {
                    for (i in 0 until l.lines) {
                        val r = l.line[f * DiceLayout.MAX_LINES + i]
                        assertTrue("S=$s '$q' line $i $r in ${l.textCol[f]}", r.inside(l.textCol[f]))
                    }
                    if (!l.stacked) assertTrue("S=$s '$q' label", l.label[f].inside(l.textCol[f]))
                }
                for (i in 0 until l.lines) {
                    assertFalse(l.line[i].meets(l.underline[0]))
                    assertFalse(l.line[i].meets(l.label[0]))
                    if (i > 0) assertFalse(l.line[i].meets(l.line[i - 1]))
                }
            }
        }
    }

    /**
     * The panel's captions are balanced: two lines whenever one will not do, and no other break with
     * two lines has a shorter longest line — so no word is left alone on a line when a more even
     * break exists.
     */
    @Test fun captionsAreBalanced() {
        for (rs in 1..2) for (s in 2..4) {
            val l = layout(s, rs)
            val cap = l.textCol[0].width
            for (kind in kinds) for (q in Flavour.quippy.lines(kind)) {
                l.arrange("d20", q)
                val oneLine = l.ui.widthOf(q.length, l.k) <= cap
                assertEquals("'$q'", if (oneLine) 1 else 2, l.lines)
                if (l.lines == 2) {
                    val longest = maxOf(l.lineEnd[0] - l.lineStart[0], l.lineEnd[1] - l.lineStart[1])
                    for (i in q.indices) if (q[i] == ' ') {
                        val alt = maxOf(q.substring(0, i).trimEnd().length, q.substring(i + 1).trimStart().length)
                        assertTrue("'$q' break at $i: $alt < $longest", alt >= longest)
                    }
                    assertEquals(q, q.substring(l.lineStart[0], l.lineEnd[0]) + " " + q.substring(l.lineStart[1], l.lineEnd[1]))
                }
            }
        }
    }

    @Test fun twoDigitBalancedExamples() {
        val l = layout(4)
        fun split(q: String): String { l.arrange("d20", q); return (0 until l.lines).joinToString(" / ") { q.substring(l.lineStart[it], l.lineEnd[it]) } }
        assertEquals("Make of that / what you will", split("Make of that what you will"))
        assertEquals("Nobody saw / that coming", split("Nobody saw that coming"))
        assertEquals("Yikes", split("Yikes"))
    }

    /**
     * T61: the landed coin shows its face — at an even result scale the 28-px face (a round ink disc,
     * the emblem knocked out), at an odd one the face's 16×16 icon twin — turned with its face, and
     * heads and tails differ.
     */
    @Test fun coinArtShowsItsFace() {
        for (k in 1..4) {
            val a = DiceArt(k)
            assertEquals(14 * k, a.diameter)
            for (heads in listOf(true, false)) {
                val up = a.coin(heads, 0)
                val turned = a.coin(heads, 180)
                assertEquals(a.box, up.width)
                for (y in 0 until a.box) for (x in 0 until a.box) {
                    assertEquals(up.isInk(x, y), turned.isInk(a.box - 1 - x, a.box - 1 - y))
                }
                if (k % 2 == 0) {
                    var ink = 0
                    for (y in 0 until a.box) for (x in 0 until a.box) {
                        if (up.isInk(x, y)) ink++
                        // Round: nothing outside the disc.
                        if (DiceArt.dist2(x / (k / 2), y / (k / 2), DiceArt.FACE) > DiceArt.FACE.toLong() * DiceArt.FACE) assertFalse(up.isInk(x, y))
                    }
                    assertTrue(ink > a.diameter * a.diameter / 3)
                    assertTrue(up.isInk(0, a.diameter / 2)) // the rim
                    assertTrue(up.isInk(a.diameter / 2, 0))
                }
            }
            val h = a.coin(true, 0)
            val t = a.coin(false, 0)
            var diff = 0
            for (y in 0 until a.box) for (x in 0 until a.box) if (h.isInk(x, y) != t.isInk(x, y)) diff++
            assertTrue(diff > 40 * k * k / (if (k % 2 == 0) 4 else 1))
        }
    }
}
