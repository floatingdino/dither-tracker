package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.widgets.WidgetTesting.assertFingertip
import au.com.josephmark.dithertracker.widgets.WidgetTesting.inside
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedListTest {

    private val m4 = WidgetMetrics(4)

    private fun list(count: Int, rows: Int = 4) = PagedList(40, count, preferredRows = rows)

    private fun lay(l: PagedList, m: WidgetMetrics = m4, w: Int = 200, h: Int = l.naturalHeight(m, w)): PagedList {
        l.layout(m, 0, 0, w, h); return l
    }

    @Test fun pagesAtScale4() {
        val l = lay(list(12))
        assertEquals(4 * 28 - 1 + 28, l.bounds.height) // 4 rows + dividers + footer
        assertEquals(4, l.perPage)
        assertEquals(3, l.pageCount)
        assertEquals("1/3", l.indicator.toString())
        assertEquals(IRect(0, 0, 200, 27), l.slots[0])
        assertEquals(IRect(0, 3 * 28, 200, 3 * 28 + 27), l.slots[3])
        assertTrue(l.slots[4].isEmpty)
        assertEquals(IRect(0, 139 - 27, 200, 139), l.footer)
        assertTrue(l.setPage(2))
        assertEquals(8, l.firstIndex); assertEquals(4, l.visibleCount)
        lay(l)
        assertEquals("3/3", l.indicator.toString())
        l.count = 10
        lay(l)
        assertEquals(2, l.visibleCount)
        assertFalse(l.setPage(7)) // clamped to the last page, already there
        assertTrue(l.onTap(PagedList.PREV)); assertEquals(1, l.page)
    }

    @Test fun allFitMeansOnePageNoFooter() {
        val l = lay(list(3), h = 139)
        assertEquals(1, l.pageCount)
        assertFalse(l.hasFooter)
        assertEquals(5, l.perPage) // (139 + 1) / 28
        assertEquals(3, l.visibleCount)
        val r = HitRegion()
        l.collectHits(r, WidgetFrame().set(0, 0, 200, 139, 0))
        assertEquals(0, r.size)
        assertEquals(4 * 28 - 1, list(3).naturalHeight(m4, 200))
    }

    @Test fun emptyList() {
        val l = lay(list(0))
        assertEquals(1, l.pageCount); assertEquals(0, l.visibleCount); assertEquals("1/1", l.indicator.toString())
    }

    @Test fun pageCountsForManyViewports() {
        for (count in 0..40) for (h in 27..300 step 7) {
            val l = lay(list(count), h = h)
            assertTrue(l.perPage >= 1)
            val shown = (0 until l.pageCount).sumOf { p -> l.setPage(p); l.visibleCount }
            l.setPage(0)
            assertEquals("count $count h $h", count, shown)
            // Rows (and the footer) stay inside the viewport when there is room for one row + footer.
            if (h >= 2 * 28 - 1) for (i in 0 until l.perPage) {
                assertTrue(inside(l.slots[i], l.bounds))
                if (l.hasFooter) assertTrue(l.slots[i].bottom < l.footer.top)
            }
        }
    }

    @Test fun prevNextRegisterOnlyWhereThereIsAPage() {
        val l = lay(list(12))
        val f = WidgetFrame().set(0, 0, 200, 139, 0)
        fun ids() = HitRegion().also { l.collectHits(it, f) }.let { r -> List(r.size) { r.idAt(it) } }
        assertEquals(listOf(41), ids())
        l.setPage(1); lay(l)
        assertEquals(listOf(40, 41), ids())
        l.setPage(2); lay(l)
        assertEquals(listOf(40), ids())
        assertFalse(l.next.enabled)
    }

    @Test fun footerButtonsAreFingertipAtEveryScale() {
        for (s in WidgetTesting.scales) {
            val m = WidgetMetrics(s)
            val l = list(30)
            val w = 200 * 4 / s
            lay(l, m, w)
            l.setPage(1); lay(l, m, w)
            val r = HitRegion()
            l.collectHits(r, WidgetFrame().set(0, 0, w, l.bounds.height, 0))
            assertEquals(2, r.size)
            for (i in 0 until r.size) {
                assertFingertip(m, r.hitAt(i), "footer ${r.idAt(i)}")
                assertTrue(inside(r.feedbackAt(i), r.hitAt(i)))
                assertTrue(inside(r.hitAt(i), l.footer))
            }
            assertTrue(m.textW(l.indicator.length) <= l.indicatorRect.width)
        }
    }

    @Test fun poolRowsAreBoundLaidOutAndHit() {
        val labels = Array(9) { "Entry ${it + 1}" }
        val pool = Array(6) { ListRow(it, "") }
        val l = PagedList(40, labels.size, pool, preferredRows = 4)
        l.binder = PagedList.Binder { row, index -> (row as ListRow).label = labels[index] }
        lay(l)
        assertEquals(4, l.perPage)
        assertEquals("Entry 1", pool[0].label); assertEquals("Entry 4", pool[3].label)
        assertEquals(l.slots[2], pool[2].bounds)
        l.setPage(2); lay(l)
        assertEquals(1, l.visibleCount)
        assertEquals("Entry 9", pool[0].label)
        val r = HitRegion()
        l.collectHits(r, WidgetFrame().set(0, 0, 200, 139, 0))
        assertEquals(listOf(40, 0), List(r.size) { r.idAt(it) }) // prev + the one visible row
        // A pool smaller than the viewport caps the page size.
        val small = PagedList(40, 9, Array(2) { ListRow(it, "") }, preferredRows = 4)
        lay(small)
        assertEquals(2, small.perPage); assertEquals(5, small.pageCount)
    }

    @Test fun rotationIsExact() {
        val pool = Array(4) { ListRow(it, "Row $it", chevron = true) }
        val l = PagedList(40, 10, pool, preferredRows = 3)
        l.binder = PagedList.Binder { row, index -> (row as ListRow).value = if (index % 2 == 0) "ON" else "OFF" }
        lay(l)
        l.setPage(1); lay(l)
        WidgetTesting.assertRotationExact(200, l.bounds.height) { p, f -> p.draw(f, l) }
    }
}
