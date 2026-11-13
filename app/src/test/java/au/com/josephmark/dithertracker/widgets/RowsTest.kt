package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.widgets.RasterCanvas.Companion.INK
import au.com.josephmark.dithertracker.widgets.RasterCanvas.Companion.PAPER
import au.com.josephmark.dithertracker.widgets.WidgetTesting.assertFingertip
import au.com.josephmark.dithertracker.widgets.WidgetTesting.inside
import au.com.josephmark.dithertracker.widgets.WidgetTesting.overlaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowsTest {

    private val m4 = WidgetMetrics(4)

    /** A row as wide as a 2-column settings panel on the Nomad (~19 mm per 56 px at S = 4). */
    private fun rowW(m: WidgetMetrics) = 220 * 4 / m.scale

    private fun <W : Widget> lay(w: W, m: WidgetMetrics, width: Int = rowW(m), y: Int = 0): W {
        w.layout(m, 0, y, width, w.naturalHeight(m, width)); return w
    }

    private fun hits(w: Widget, f: WidgetFrame = WidgetFrame().set(0, 0, 2000, 2000, 0)) = HitRegion().also { w.collectHits(it, f) }

    private fun everyRow() = listOf(
        ListRow(0, "History", icon = Icon.HISTORY, chevron = true),
        ListRow(0, "Scale", value = "AUTO"),
        Toggle(0, "Auto KO", checked = true),
        Toggle(0, "Keep screen awake"),
        Stepper(0, "Starting life", 40, 1, 999),
        Segmented(0, "Players", arrayOf("2", "3", "4", "5", "6"), 2),
        Segmented(0, "", arrayOf("LIGHT", "DARK")),
    )

    @Test fun everyRowIsFingertipSizedAtEveryScale() {
        for (s in WidgetTesting.scales) {
            val m = WidgetMetrics(s)
            for (row in everyRow()) {
                lay(row, m)
                assertEquals("$m $row", m.rowH, row.bounds.height)
                val r = hits(row)
                assertTrue(r.size >= 1)
                for (i in 0 until r.size) {
                    assertFingertip(m, r.hitAt(i), "${row::class.simpleName} part ${r.idAt(i)}")
                    assertTrue(inside(r.feedbackAt(i), r.hitAt(i)))
                    assertTrue(inside(r.hitAt(i), row.bounds))
                }
            }
        }
    }

    @Test fun listRowAtScale4() {
        val r = lay(ListRow(0, "History", icon = Icon.HISTORY, chevron = true), m4)
        assertEquals(IRect(6, 5, 22, 21), r.iconRect)
        assertEquals(26, r.labelRect.left)
        assertEquals(IRect(220 - 6 - 16, 5, 220 - 6, 21), r.chevronRect)
        assertEquals(7, r.labelChars)
        val v = lay(ListRow(0, "Scale", value = "AUTO"), m4)
        assertEquals(220 - 6, v.valueRect.right)
        assertEquals(4, v.valueChars)
    }

    @Test fun listRowSharesRoomWhenCrowded() {
        val r = lay(ListRow(0, "Commander damage affects life", value = "ALWAYS ON BY DEFAULT"), m4, 160)
        assertTrue(r.labelChars in 1 until r.label.length)
        assertTrue(r.valueChars in 1 until r.value!!.length)
        assertFalse(overlaps(r.labelRect.let { IRect(it.left, it.top, it.left + m4.textW(r.labelChars), it.bottom) }, r.valueRect))
        assertTrue(r.valueRect.width <= (160 - 12 - 4) / 2)
    }

    @Test fun toggleBoxReadsOnAndOff() {
        val f = WidgetFrame().set(0, 0, 220, 27, 0)
        val on = lay(Toggle(0, "Auto KO", checked = true), m4)
        assertEquals(IRect(220 - 6 - 18, 4, 220 - 6, 22), on.box)
        val a = RasterCanvas(220, 27)
        WidgetPainter(a).draw(f, on)
        val off = lay(Toggle(0, "Auto KO", checked = false), m4)
        val b = RasterCanvas(220, 27)
        WidgetPainter(b).draw(f, off)
        val inner = IRect(on.box.left + 1, on.box.top + 1, on.box.right - 1, on.box.bottom - 1)
        // On: mostly ink (check knocked out); off: outline only, empty inside.
        assertTrue(a.inkIn(inner) > inner.width * inner.height * 2 / 3)
        assertEquals(0, b.inkIn(inner))
        assertEquals(INK, b[on.box.left, on.box.top]); assertEquals(INK, b[on.box.right - 1, on.box.bottom - 1])
        assertTrue(on.toggled() == false && off.toggled())
        // The whole row is the button.
        val r = hits(on, f)
        assertEquals(1, r.size); assertEquals(IRect(0, 0, 220, 27), r.hitAt(0)); assertEquals(IRect(0, 0, 220, 27), r.feedbackAt(0))
    }

    @Test fun stepperLayoutAtScale4() {
        val st = lay(Stepper(10, "Starting life", 40, 1, 999), m4)
        // Columns from the right: plus hit 27 (button 23, right edge at row − padX), value 3 digits of num-m + gaps, minus hit 27.
        assertEquals(IRect(220 - 6 + 2 - 27, 0, 220 - 6 + 2, 27), st.plusHit)
        assertEquals(IRect(st.plusHit.left + 2, 2, st.plusHit.right - 2, 25), st.plusBtn)
        assertEquals(3 * 10 + 2 * 2 + 2 * 4, st.valueRect.width)
        assertEquals(st.valueRect.left, st.minusHit.right)
        assertEquals(13, st.labelChars)
        val r = hits(st)
        assertEquals(10, r.hitTest(st.minusHit.left, 0)); assertEquals(11, r.hitTest(st.plusHit.right - 1, 26))
        assertTrue(r.repeats(10) && r.repeats(11))
        assertEquals(HitRegion.NONE, r.hitTest(st.valueRect.left + 5, 10)) // the value itself is not a button
    }

    @Test fun stepperStepsClampsAndDisablesAtLimits() {
        val st = Stepper(0, "Life", 38, 1, 40, step = 1, holdStep = 10)
        assertEquals(39, st.valueAfter(Stepper.PLUS, false))
        assertEquals(40, st.valueAfter(Stepper.PLUS, true))
        assertEquals(28, st.valueAfter(Stepper.MINUS, true))
        assertTrue(st.apply(Stepper.PLUS, true)); assertEquals(40, st.value)
        assertFalse(st.apply(Stepper.PLUS, false))
        lay(st, m4)
        val r = hits(st)
        assertEquals(1, r.size) // + is at its limit: hatched, no hit
        assertEquals(0, r.idAt(0))
        st.value = 1
        assertEquals(listOf(1), hits(st).let { h -> List(h.size) { h.idAt(it) } })
        st.repeats = false
        assertFalse(hits(st).repeats(1))
        // No overflow at the extremes.
        val big = Stepper(0, "", Int.MAX_VALUE - 1, Int.MIN_VALUE, Int.MAX_VALUE, holdStep = 100)
        assertEquals(Int.MAX_VALUE, big.valueAfter(Stepper.PLUS, true))
    }

    @Test fun stepperValueSlotFitsNegativeRanges() {
        for (s in WidgetTesting.scales) {
            val m = WidgetMetrics(s)
            val st = lay(Stepper(0, "Offset", 0, -99, 99), m)
            assertEquals(3, st.valueChars())
            assertTrue(st.valueRect.width >= m.num.widthOf(3, m.k))
            assertTrue(st.minusBtn.left > st.labelRect.left)
        }
    }

    @Test fun segmentedAtScale4() {
        val sg = lay(Segmented(20, "Players", arrayOf("2", "3", "4", "5", "6"), 2), m4)
        assertEquals(27, sg.segmentNatural(m4))
        assertEquals(IRect(220 - 6 - 5 * 27, 2, 220 - 6, 25), sg.bar)
        for (i in 0 until 5) {
            assertEquals(27, sg.segVis[i].width)
            assertEquals(IRect(sg.segVis[i].left, 0, sg.segVis[i].right, 27), sg.segHit[i])
            assertEquals(1, sg.segChars[i])
        }
        assertEquals(7, sg.labelChars)
        val r = hits(sg)
        assertEquals(5, r.size)
        assertEquals(23, r.hitTest(sg.segVis[3].left, 0))
        assertTrue(sg.select(4)); assertFalse(sg.select(4)); assertFalse(sg.select(9))
    }

    @Test fun segmentedWithoutLabelFillsTheRowAndCrowdedDropsTheLabel() {
        val sg = lay(Segmented(0, "", arrayOf("LIGHT", "DARK")), m4)
        assertEquals(IRect(6, 2, 214, 25), sg.bar)
        val crowded = lay(Segmented(0, "Starting life", arrayOf("20", "25", "30", "40", "OTHER")), m4, 160)
        assertEquals(0, crowded.labelChars)
        assertEquals(160 - 12, crowded.bar.width)
        assertTrue(crowded.segChars[4] < 5) // "OTHER" cut in a 29-px segment
    }

    @Test fun selectedSegmentIsInverted() {
        val sg = lay(Segmented(0, "", arrayOf("A", "B", "C")), m4)
        sg.selected = 1
        val c = RasterCanvas(220, 27)
        WidgetPainter(c).draw(WidgetFrame().set(0, 0, 220, 27, 0), sg)
        val v = sg.segVis[1]
        val unselected = sg.segVis[2]
        assertEquals(INK, c[v.left + 3, v.top + 3])
        assertEquals(PAPER, c[unselected.left + 3, unselected.top + 3])
        // The selected label is paper-on-ink: there are paper pixels inside the segment's text band.
        assertTrue(c.inkIn(IRect(v.left + 2, v.top + 2, v.right - 2, v.bottom - 2)) < (v.width - 4) * (v.height - 4))
    }

    @Test fun disabledRowsAreHatchedAndInert() {
        for (row in everyRow()) {
            lay(row, m4).enabled = false
            assertEquals(0, hits(row).size)
            val c = RasterCanvas(220, 27)
            WidgetPainter(c).draw(WidgetFrame().set(0, 0, 220, 27, 0), row)
            assertTrue(c.inkIn(IRect(0, 0, 4, 27)) > 0) // hatch at the row's left edge
        }
    }

    @Test fun truncatesCleanlyAtEveryScale() {
        for (s in WidgetTesting.scales) {
            val m = WidgetMetrics(s)
            val t = lay(Toggle(0, "Commander damage affects life total"), m, 140 * 4 / s)
            val cut = t.label.substring(0, t.labelChars)
            assertTrue("$m '$cut'", cut.isNotEmpty() && cut.length < t.label.length && !TextFit.isSep(cut.last()))
            assertTrue(m.textW(t.labelChars) <= t.labelRect.width)
            assertTrue(t.labelRect.right <= t.box.left - m.gap)
        }
    }

    @Test fun pressFallbackMatchesInversion() {
        for (row in everyRow()) {
            lay(row, m4)
            val f = WidgetFrame().set(0, 0, 220, 27, 0)
            val r = HitRegion()
            // Give multi-part widgets distinct ids so each part is checked.
            row.collectHits(r, f)
            WidgetTesting.assertFallbackMatchesInvert(220, 27, r) { it.draw(f, row) }
        }
    }

    @Test fun rotationIsExact() {
        for (s in intArrayOf(2, 3, 4, 6)) {
            val m = WidgetMetrics(s)
            for (row in everyRow()) {
                lay(row, m)
                WidgetTesting.assertRotationExact(row.bounds.width, row.bounds.height) { p, f -> p.draw(f, row) }
            }
        }
    }

    @Test fun toggleIconSitsBeforeTheLabel() {
        for (s in WidgetTesting.scales) {
            val m = WidgetMetrics(s)
            val plain = lay(Toggle(0, "Poison"), m)
            val t = lay(Toggle(0, "Poison", icon = Icon.POISON), m)
            assertEquals(m.icon, t.iconRect.width)
            assertEquals(t.bounds.left + m.padX, t.iconRect.left)
            assertEquals(t.iconRect.right + m.gap, t.labelRect.left)
            assertTrue(t.iconRect.top >= t.bounds.top && t.iconRect.bottom <= t.bounds.bottom)
            assertTrue(plain.iconRect.isEmpty)
            assertEquals(plain.naturalWidth(m) + m.icon + m.gap, t.naturalWidth(m))
            // The icon is drawn (ink inside its rect), and it turns with the row.
            val c = RasterCanvas(t.bounds.right, t.bounds.bottom)
            WidgetPainter(c).draw(WidgetFrame().set(0, 0, c.width, c.height, 0), t)
            assertTrue(c.inkIn(t.iconRect) > 0)
            WidgetTesting.assertRotationExact(t.bounds.width, t.bounds.height) { p, f -> p.draw(f, t) }
        }
    }

    @Test fun segmentedNumeralsUseTheHeavyFaceForNumbersOnly() {
        val m = m4
        val seg = Segmented(0, "Scale", arrayOf("AUTO", "2", "100"))
        assertEquals(m.ui, seg.optionFont(m, 1))
        seg.numerals = true
        assertEquals(m.ui, seg.optionFont(m, 0))
        assertEquals(m.num, seg.optionFont(m, 1))
        assertEquals(m.num, seg.optionFont(m, 2))
        // Segments are sized for the widest option in its own face.
        assertEquals(maxOf(m.target, m.num.widthOf(3, m.k) + 2 * m.gap + m.stroke), seg.segmentNatural(m))
        lay(seg, m)
        assertEquals(3, seg.segChars[2])
    }
}
