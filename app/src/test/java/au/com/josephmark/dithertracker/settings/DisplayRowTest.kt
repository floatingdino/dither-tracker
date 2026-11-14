package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.eink.EinkConfig
import au.com.josephmark.dithertracker.play.PlayRig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T58: `Display [FAST][NORMAL]` — on a Supernote, on the SCREEN page behind Screen clean's ›, so the
 * main page keeps fitting one card page at S=4; elsewhere Screen clean is a plain row and Display is
 * nowhere.
 */
class DisplayRowTest {

    @After fun reset() {
        EinkConfig.fastDisplayAvailable = false
    }

    private class Rig(available: Boolean) {
        init {
            EinkConfig.fastDisplayAvailable = available // read when the table is built (MainActivity sets it first)
        }

        val rig = PlayRig(s = 4)
        val stage get() = rig.stage
        val overlay = rig.stage.components.filterIsInstance<SettingsOverlay>().single()
        val card get() = overlay.card
        val clean = overlay.main.rows.filterIsInstance<ChoiceRow>().single { it.label == "Screen clean" }

        fun tapId(id: Int) {
            val r = IRect()
            assertTrue("id $id not on the card", card.region.hitRect(id, r))
            stage.down(0, (r.left + r.right) / 2, (r.top + r.bottom) / 2, rig.platform.clock.now)
            stage.up(0)
        }
    }

    @Test fun offASupernoteScreenCleanIsAPlainRowAndDisplayIsNowhere() {
        val r = Rig(available = false)
        assertFalse(r.clean is PlayersRow)
        assertNull(r.clean.linkedPage)
        assertTrue(r.overlay.pages.none { p -> p.rows.any { it is ChoiceRow && it.label == "Display" } })
    }

    @Test fun onASupernoteTheMainPageStillFitsOnePageWithTheHintInEveryRotation() {
        val r = Rig(available = true)
        assertEquals(10, r.overlay.main.rows.size)
        // A pending player count shows the next-game hint: every main row is listed.
        r.stage.settings.update(r.stage.settings.current.copy(playerCount = 5))
        r.overlay.open(0)
        repeat(4) {
            assertEquals("rotation ${r.overlay.rotation}", 10, r.card.shownCount)
            assertEquals("rotation ${r.overlay.rotation}", 1, r.card.list.pageCount)
            assertFalse(r.card.list.hasFooter)
            r.overlay.rotate()
        }
    }

    @Test fun screenCleansChevronOpensDisplayAndChoosingWritesTheSetting() {
        val r = Rig(available = true)
        r.overlay.open(0)
        val seg = r.clean.segmented
        // Screen clean still chooses its threshold.
        r.tapId(seg.id + 1)
        assertEquals(20, r.stage.settings.current.fullRefreshEvery)
        r.tapId(seg.id + 0)
        assertEquals(0, r.stage.settings.current.fullRefreshEvery)
        // Its › opens the SCREEN page: back, then Display.
        r.tapId(seg.id + seg.chevronPart)
        assertEquals(SettingsTable.SCREEN_TITLE, r.overlay.page.title)
        val display = r.overlay.page.rows.filterIsInstance<ChoiceRow>().single()
        assertEquals("Display", display.label)
        assertTrue("FAST by default", r.stage.settings.current.fastDisplay)
        assertEquals(0, display.segmented.selected)
        r.tapId(display.widget.id + 1)
        assertFalse(r.stage.settings.current.fastDisplay)
        assertEquals(1, display.segmented.selected)
        r.tapId(display.widget.id + 0)
        assertTrue(r.stage.settings.current.fastDisplay)
        // Back to the main page.
        r.tapId(r.overlay.page.rows[0].widget.id)
        assertSame(r.overlay.main, r.overlay.page)
    }
}
