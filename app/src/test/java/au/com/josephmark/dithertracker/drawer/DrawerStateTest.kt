package au.com.josephmark.dithertracker.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerStateTest {

    private val st = DrawerState(4)

    @Test fun pagingMaths() {
        st.setPaging(0, 0, 6); assertEquals(1, st.pageCount(0)); assertEquals(0, st.itemsOnPage(0))
        st.setPaging(0, 6, 6); assertEquals(1, st.pageCount(0)); assertEquals(6, st.itemsOnPage(0))
        st.setPaging(0, 7, 6); assertEquals(2, st.pageCount(0)); assertEquals(6, st.itemsOnPage(0))
        assertTrue(st.nextPage(0)); st.setPaging(0, 7, 6)
        assertEquals(1, st.itemsOnPage(0))
        assertFalse(st.nextPage(0))
        st.setPaging(0, 13, 6); assertEquals(3, st.pageCount(0))
    }

    @Test fun shrinkingClampsThePage() {
        st.setPaging(1, 20, 6)
        st.nextPage(1); st.nextPage(1); st.nextPage(1)
        assertEquals(3, st.page(1))
        st.setPaging(1, 7, 6)
        assertEquals(1, st.page(1)); assertEquals(1, st.itemsOnPage(1))
    }

    @Test fun eachModeRemembersItsPage() {
        st.setPaging(2, 9, 6); st.nextPage(2)
        assertTrue(st.setMode(2, DrawerState.COMMANDER))
        assertEquals(0, st.page(2))
        assertFalse(st.setMode(2, DrawerState.COMMANDER))
        st.setMode(2, DrawerState.TRAY)
        assertEquals(1, st.page(2))
    }

    @Test fun openCloseIsPerSeat() {
        st.toggle(0); st.toggle(3)
        assertTrue(st.isOpen(0)); assertFalse(st.isOpen(1)); assertTrue(st.isOpen(3))
        st.toggle(0)
        assertFalse(st.isOpen(0))
        assertFalse(st.isOpen(9))
        st.closeAll()
        assertFalse(st.isOpen(3))
    }
}
