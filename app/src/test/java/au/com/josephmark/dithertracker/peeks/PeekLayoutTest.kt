package au.com.josephmark.dithertracker.peeks

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeekLayoutTest {

    private val screens = listOf(1872 to 1404, 2560 to 1600, 1280 to 800, 1404 to 1053)
    private val all = sequence {
        for ((w, h) in screens) for (s in 2..6) yield(PeekLayout(PlayLayout.compute(DitherGrid(w, h, s))))
    }.filter { it.play.comfortable && it.enabled }.toList()
    private val nomad4 = PeekLayout(PlayLayout.compute(DitherGrid(1872, 1404, 4)))

    private fun inside(inner: IRect, outer: IRect) =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun overlaps(a: IRect, b: IRect) =
        a.width > 0 && b.width > 0 && a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    @Test fun nomadAtScale4() {
        val k = nomad4
        assertTrue(k.enabled)
        // pad 2 + icon 16 + gap 2 + digits (num-m 8 wide, tracking 2) + pad 2
        assertEquals(30, k.chipWidth(4))
        assertEquals(40, k.chipWidth(12))
        assertEquals(2 + 18 + 2, k.moreWidth(3)) // "+3"
        val xs = IntArray(17)
        // three chips centred as a group
        val v = intArrayOf(4, 2, 7)
        assertEquals(3, k.arrange(v, 3, xs))
        val total = 3 * 30 + 2 * k.gap
        assertEquals(k.play.underX + (k.play.underW - total) / 2, xs[0])
        assertEquals(xs[0] + 30 + k.gap, xs[1])
    }

    @Test fun overflowShowsAsManyAsFitThenPlusN() {
        val k = nomad4
        val xs = IntArray(17)
        val v = IntArray(9) { 10 + it } // nine 2-digit chips, 40 wide each
        val shown = k.arrange(v, 9, xs)
        // 4 × 40 + "+5" (22) + 4 gaps of 6 = 206 ≤ 217; a fifth chip would need 252.
        assertEquals(4, shown)
        assertTrue(k.widthOf(v, shown, 9) <= k.play.underW)
        assertTrue(shown == 8 || k.widthOf(v, shown + 1, 9) > k.play.underW) // the largest that fits
        // the +N chip follows the last shown chip
        assertEquals(xs[shown - 1] + k.chipWidth(v[shown - 1]) + k.gap, xs[shown])
        assertTrue(xs[shown] + k.moreWidth(9 - shown) <= k.play.underX + k.play.underW)
        // nothing active → nothing
        assertEquals(0, k.arrange(v, 0, xs))
    }

    @Test fun chipsStayInTheRowClearOfEverythingAtS2to6() {
        val xs = IntArray(17)
        val chip = IRect(); val icon = IRect(); val value = IRect()
        val v = intArrayOf(2, 10, 999, 3, 1, 25, 7, 8, 4)
        assertTrue("${all.size}", all.size >= 7)
        var overflowed = false
        for (k in all) {
            val l = k.play
            for (n in 1..v.size) {
                val shown = k.arrange(v, n, xs)
                for (s in 0 until 4) {
                    val rects = ArrayList<IRect>()
                    for (i in 0 until shown) {
                        k.chip(s, xs[i], k.chipWidth(v[i]), chip)
                        k.icon(s, xs[i], icon); k.value(s, xs[i], v[i], value)
                        assertTrue("$l $chip", inside(chip, l.underRow[s]))
                        assertTrue(inside(icon, chip)); assertTrue(inside(value, chip)); assertFalse(overlaps(icon, value))
                        rects += IRect(chip.left, chip.top, chip.right, chip.bottom)
                    }
                    if (shown < n) {
                        overflowed = true
                        k.chip(s, xs[shown], k.moreWidth(n - shown), chip)
                        assertTrue("$l more $chip", inside(chip, l.underRow[s]))
                        rects += IRect(chip.left, chip.top, chip.right, chip.bottom)
                    }
                    for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse(overlaps(rects[i], rects[j]))
                    for (r in rects) for (o in listOf(l.numberSlot[s], l.minusGlyph[s], l.plusGlyph[s], l.readoutSlot[s], l.tabHit[s], l.diamond)) {
                        assertFalse("$l $r vs $o", overlaps(r, o))
                    }
                }
            }
        }
        assertTrue("the +N path was exercised", overflowed)
    }

    @Test fun rotatedSeatMirrorsUpright() {
        val k = nomad4
        val l = k.play
        val a = IRect(); val b = IRect()
        k.chip(3, 40, 30, a); k.chip(0, 40, 30, b)
        assertEquals(a.left - l.panels[3].left, l.panels[0].right - b.right)
        assertEquals(a.top - l.panels[3].top, l.panels[0].bottom - b.bottom)
    }
}
