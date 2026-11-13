package au.com.josephmark.dithertracker.peeks

import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerItems
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

    private fun copy(r: IRect) = IRect(r.left, r.top, r.right, r.bottom)

    @Test fun dump() {
        for (k in all) println("${k.play.grid.width}x${k.play.grid.height} $k")
    }

    @Test fun nomadAtScale4() {
        val k = nomad4
        assertTrue(k.enabled)
        assertEquals(3, k.rows); assertEquals(6, k.slots)
        // Columns: x 8..58 on the left, 175..225 on the right (tab hit 64..169, V_MIN 4, pad 2).
        assertEquals(8, k.leftX); assertEquals(225, k.rightX); assertEquals(50, k.colW)
        // Rows bottom-up at 155, 137, 119 (pitch 18): content 4 px above the near edge (175) and
        // 4 px below the number slot (bottom 115).
        assertEquals(18, k.pitch); assertEquals(155, k.bottomY)
        assertEquals(155, k.slotY(0)); assertEquals(155, k.slotY(1)); assertEquals(137, k.slotY(2)); assertEquals(119, k.slotY(5))
        // icon 16 + gap 2 + digits (num-m 10 wide, tracking 2)
        assertEquals(28, k.contentWidth(4))
        assertEquals(40, k.contentWidth(12))
        assertEquals(22, k.moreWidth(3)) // "+3"
        // Seat 3 (upright, bottom-left): slot 0 bottom-left, slot 1 bottom-right right-aligned.
        val r = IRect()
        k.icon(3, 0, 4, r); assertEquals(IRect(8, 176 + 155, 24, 176 + 171), r)
        k.value(3, 0, 4, r); assertEquals(IRect(26, 176 + 156, 36, 176 + 170), r)
        k.icon(3, 1, 12, r); assertEquals(IRect(185, 176 + 155, 201, 176 + 171), r)
        k.value(3, 1, 12, r); assertEquals(IRect(203, 176 + 156, 225, 176 + 170), r)
        k.chip(3, 1, k.contentWidth(12), false, r); assertEquals(IRect(185, 176 + 155, 225, 176 + 171), r)
        k.chip(3, 1, k.contentWidth(12), true, r); assertEquals(IRect(183, 176 + 153, 227, 176 + 173), r)
    }

    @Test fun theNomadGetsSixSlotsAtEveryComfortableScale() {
        for (s in 2..4) {
            val k = PeekLayout(PlayLayout.compute(DitherGrid(1872, 1404, s)))
            assertTrue("$k", k.enabled); assertEquals("$k", 3, k.rows); assertEquals(6, k.slots)
        }
    }

    @Test fun threeDigitValuesCloseUpRatherThanClip() {
        val k = nomad4
        assertEquals(2, k.tracking(99)); assertEquals(1, k.tracking(100)); assertEquals(1, k.tracking(999))
        assertEquals(50, k.contentWidth(999)) // 16 + 2 + 3 × 10 + 2 × 1: exactly the column
        for (p in all) for (v in intArrayOf(0, 7, 10, 99, 100, 999)) {
            assertTrue("$p $v", p.contentWidth(v) <= p.colW)
            assertTrue(p.tracking(v) >= PeekLayout.TIGHT_TRACKING)
            if (v < 100) assertEquals(p.num.tracking, p.tracking(v))
        }
    }

    @Test fun slotsAlternateLeftRightBottomUpAndOverflowTakesTheSixth() {
        val k = nomad4
        assertEquals(listOf(0, 1, 0, 1, 0, 1), (0 until 6).map { k.column(it) })
        assertEquals(listOf(0, 0, 1, 1, 2, 2), (0 until 6).map { k.row(it) })
        assertEquals(0, k.shown(0))
        for (n in 1..6) assertEquals(n, k.shown(n)) // six fit without overflow
        assertEquals(5, k.shown(7)); assertEquals(5, k.shown(8)); assertEquals(5, k.shown(17)) // 5 + "+N"
        // The +N sits in slot 5: the right column's top row, right-aligned.
        val r = IRect()
        k.more(3, 5, 3, r)
        assertEquals(IRect(225 - 22, 176 + 120, 225, 176 + 134), r)
    }

    @Test fun poisonAtEightInvertsItsChip() {
        val poison = DrawerItems.item(DrawerItems.COUNTER, Counter.POISON.ordinal)
        assertFalse(DrawerItems.isWarning(poison, 7))
        assertTrue(DrawerItems.isWarning(poison, 8)); assertTrue(DrawerItems.isWarning(poison, 10))
        assertFalse(DrawerItems.isWarning(DrawerItems.item(DrawerItems.COUNTER, Counter.ENERGY.ordinal), 12))
        // The inverted card is the content grown by pad, and still clears its neighbours' content.
        val k = nomad4
        val card = IRect(); val above = IRect(); val beside = IRect()
        k.chip(3, 0, k.contentWidth(10), true, card)
        k.chip(3, 2, k.contentWidth(99), false, above); k.chip(3, 1, k.contentWidth(99), false, beside)
        assertEquals(k.contentWidth(10) + 2 * k.pad, card.width)
        assertFalse(overlaps(card, above)); assertFalse(overlaps(card, beside))
    }

    @Test fun cornersClearEverythingOnEverySeatAtS2to6() {
        val chip = IRect(); val icon = IRect(); val value = IRect(); val corner = IRect()
        val v = intArrayOf(2, 10, 999, 3, 1, 25, 7, 8, 4)
        assertTrue("${all.size}", all.size >= 7)
        var overflowed = false
        for (k in all) {
            val l = k.play
            assertTrue("$k", k.rows in 1..PeekLayout.MAX_ROWS)
            for (n in 1..v.size) {
                val shown = k.shown(n)
                for (s in 0 until 4) {
                    val rects = ArrayList<IRect>()
                    val inks = ArrayList<IRect>()
                    for (i in 0 until shown) {
                        // Every chip may be an ink card (worst case for clearances).
                        k.chip(s, i, k.contentWidth(v[i]), true, chip)
                        inks += copy(chip)
                        k.chip(s, i, k.contentWidth(v[i]), false, chip)
                        k.icon(s, i, v[i], icon); k.value(s, i, v[i], value)
                        assertTrue("$k $chip", inside(chip, l.panels[s]))
                        assertTrue(inside(icon, chip)); assertTrue(inside(value, chip)); assertFalse(overlaps(icon, value))
                        k.corner(s, k.column(i), corner)
                        assertTrue("$k $chip in $corner", inside(inks.last(), corner))
                        rects += copy(chip)
                    }
                    if (shown < n) {
                        overflowed = true
                        assertEquals(k.slots - 1, shown)
                        k.chip(s, shown, k.moreWidth(n - shown), false, chip)
                        k.corner(s, k.column(shown), corner)
                        assertTrue("$k more $chip", inside(chip, corner))
                        rects += copy(chip)
                    }
                    for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse("$k ${rects[i]} ${rects[j]}", overlaps(rects[i], rects[j]))
                    // An ink card never covers another chip's content.
                    for (i in inks.indices) for (j in rects.indices) if (i != j) assertFalse("$k ink ${inks[i]} ${rects[j]}", overlaps(inks[i], rects[j]))
                    val keepOut = listOf(l.numberSlot[s], l.minusGlyph[s], l.plusGlyph[s], l.readoutSlot[s], l.tabHit[s], l.diamond)
                    for (r in rects + inks) {
                        for (o in keepOut) assertFalse("$k seat $s $r vs $o", overlaps(r, o))
                        // Not even the diamond's touch area (its hit pad) reaches a near corner.
                        for (y in r.top until r.bottom) for (x in r.left until r.right) assertFalse("$k $s", l.inDiamondHit(x, y))
                    }
                }
            }
        }
        assertTrue("the +N path was exercised", overflowed)
    }

    @Test fun cornersAreTheNearCornersBesideTheTab() {
        // The diamond is at a far corner on every seat, so the near corners are free on all four.
        for (k in all) {
            val l = k.play
            val c = IRect()
            for (s in 0 until 4) for (col in 0..1) {
                k.corner(s, col, c)
                val p = l.panels[s]
                // In the player's frame the corner hugs the near edge (screen bottom upright, top rotated).
                if (l.rotation[s] == 0) assertTrue(p.bottom - c.bottom <= PlayLayout.V_MIN) else assertTrue(c.top - p.top <= PlayLayout.V_MIN)
                // The diamond is on the opposite (far) side of the panel.
                val farSide = if (l.rotation[s] == 0) l.diamondCy < p.top + p.height / 2 else l.diamondCy > p.top + p.height / 2
                assertTrue("$k seat $s", farSide)
                // Same side as the tab, either side of it.
                assertTrue(c.right <= l.tabHit[s].left || c.left >= l.tabHit[s].right)
            }
        }
    }

    @Test fun rotatedSeatMirrorsUpright() {
        val k = nomad4
        val l = k.play
        val a = IRect(); val b = IRect()
        for (i in 0 until 6) {
            k.chip(3, i, 40, true, a); k.chip(0, i, 40, true, b)
            assertEquals(a.left - l.panels[3].left, l.panels[0].right - b.right)
            assertEquals(a.top - l.panels[3].top, l.panels[0].bottom - b.bottom)
        }
    }
}
