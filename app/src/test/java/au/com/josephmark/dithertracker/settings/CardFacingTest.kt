package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.core.serial.SaveCodec
import au.com.josephmark.dithertracker.core.serial.SaveFile
import au.com.josephmark.dithertracker.history.HistoryOverlay
import au.com.josephmark.dithertracker.layout.CountRig
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.widgets.Segmented
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T53 (Sam: "make sure setup menu orientation is remembered"): the settings card reopens facing the
 * side it was last turned to — on every page, after a close and after a process restart (the facing
 * is saved with the settings) — and the history card remembers its own facing the same way.
 */
class CardFacingTest {

    private fun rig(settings: AppSettings = AppSettings()) = CountRig(1872, 1404, 4, 4).also { it.stage.settings.update(settings) }
    private fun overlay(r: CountRig) = r.stage.components.filterIsInstance<SettingsOverlay>().single()
    private fun history(r: CountRig) = r.stage.components.filterIsInstance<HistoryOverlay>().single()

    @Test fun settingsCardFacingSurvivesCloseReopenAndRestart() {
        val r = rig()
        val o = overlay(r)
        o.open(MenuItem.SETUP, 180)
        assertEquals("never turned: it faces the side that asked", 180, o.rotation)
        o.close()
        o.open(MenuItem.SETUP, 0)
        assertEquals(0, o.rotation)
        o.rotate(); o.rotate(); o.rotate() // S -> E -> N -> W
        assertEquals(270, o.rotation)
        assertEquals(270, r.stage.settings.current.settingsFacing)
        o.close()
        o.open(MenuItem.SETUP, 0)
        assertEquals("reopened from the S side, still facing W", 270, o.rotation)
        // Its sub-pages share it: the layout picker and the counters-and-trackers page.
        val players = o.main.rows.first { it is PlayersRow }
        o.onTap(0, players.widget.id + (players.widget as Segmented).chevronPart)
        assertEquals(LayoutPickerRow::class, o.page.rows.last()::class)
        assertEquals(270, o.rotation)
        o.close()
        o.open(MenuItem.SETUP, 180)
        o.onTap(0, o.main.rows.first { it is LinkRow }.widget.id)
        assertEquals(SettingsTable.COUNTERS_TITLE, o.page.title.toString())
        assertEquals(270, o.rotation)
        o.close()
        // A restart: the settings come back from the save file.
        val saved = SaveCodec.decodeOrNull(SaveCodec.encode(SaveFile(r.session.game, r.stage.settings.current)))!!.settings
        val r2 = rig(saved)
        val o2 = overlay(r2)
        o2.open(MenuItem.SETUP, 180)
        assertEquals(270, o2.rotation)
    }

    @Test fun historyCardRemembersItsOwnFacing() {
        val r = rig()
        val h = history(r)
        h.open(MenuItem.LOG, 0)
        assertEquals(0, h.rotation)
        h.rotateNext()
        assertEquals(90, r.stage.settings.current.historyFacing)
        assertEquals("the settings card's facing is its own", AppSettings.FACING_NONE, r.stage.settings.current.settingsFacing)
        h.close()
        h.open(MenuItem.LOG, 180)
        assertEquals(90, h.rotation)
        val saved = SaveCodec.decodeOrNull(SaveCodec.encode(SaveFile(r.session.game, r.stage.settings.current)))!!.settings
        val r2 = rig(saved)
        history(r2).open(MenuItem.LOG, 0)
        assertEquals(90, history(r2).rotation)
    }
}
