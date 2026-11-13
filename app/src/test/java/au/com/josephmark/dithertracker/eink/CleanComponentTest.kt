package au.com.josephmark.dithertracker.eink

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayRig
import au.com.josephmark.dithertracker.settings.SettingsOverlay
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Stage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cleanup on the real Play screen (fake clock): counting, quiet period, scope, cancel, overlays. */
class CleanComponentTest {
    private val rig = PlayRig()
    private val clean = rig.stage.components.filterIsInstance<CleanComponent>().single()
    private val settingsCard = rig.stage.components.filterIsInstance<SettingsOverlay>().single()
    private val clock get() = rig.platform.clock

    @After fun tearDown() {
        EinkConfig.cleanEvery = -1
        EinkConfig.vendorFull = null
    }

    private fun every(n: Int) = rig.stage.settings.update(rig.stage.settings.current.copy(fullRefreshEvery = n))

    private fun tap(x: Int, y: Int) {
        rig.stage.down(0, x, y, clock.now)
        rig.stage.up(0)
    }

    private fun plus(seat: Int) = PlayRig.mid(rig.l.zones[PlayLayout.zone(seat, PlayLayout.PLUS)])
    private fun tapPlus(seat: Int) = plus(seat).let { tap(it[0], it[1]) }
    private fun life(seat: Int) = rig.session.state.players[seat].life

    /** Advances in 50 ms steps until a clean starts (or [maxMs]); returns ms since the table was last busy. */
    private fun runUntilClean(maxMs: Long = 60_000): Long {
        var lastBusy = clock.now
        val end = clock.now + maxMs
        while (clock.now < end && !clean.cleaner.active) {
            if (rig.stage.timersPending(clean)) lastBusy = clock.now
            clock.advance(50)
        }
        return clock.now - lastBusy
    }

    @Test fun registeredLastSoItsInkCoversEverything() {
        assertTrue(rig.stage.components.last() === clean)
    }

    @Test fun oneSeatsTapsCleanOnlyThatTileAfterTheQuietPeriod() {
        every(3)
        repeat(3) { tapPlus(3) }
        assertEquals(43, life(3))
        assertTrue(clean.cadence.due)
        assertFalse(clean.cleaner.active) // never straight away
        val quiet = runUntilClean()
        assertTrue(clean.cleaner.active)
        assertTrue("quiet $quiet", quiet >= CleanCadence.QUIET_MS)
        assertFalse(clean.cleaner.full)
        assertEquals(rig.l.panels[3], clean.cleaner.region)
        assertTrue(clean.cleaner.black)
        clock.advance(ScreenCleaner.HOLD_MS)
        assertFalse(clean.cleaner.active)
        assertEquals(0, clean.cadence.seatCount(3))
        assertFalse(clean.cadence.due)
        // Idle afterwards: nothing armed at all.
        assertFalse(rig.stage.timersPending())
    }

    @Test fun spreadChangesCleanTheWholeScreen() {
        every(4)
        tapPlus(0); tapPlus(1); tapPlus(2); tapPlus(3)
        runUntilClean()
        assertTrue(clean.cleaner.full)
        clock.advance(ScreenCleaner.HOLD_MS)
        assertEquals(0, clean.cadence.count)
    }

    @Test fun aTouchCancelsAtOnceInsideIsSwallowedOutsideStillActs() {
        every(2)
        tapPlus(3); tapPlus(3)
        runUntilClean()
        assertEquals(rig.l.panels[3], clean.cleaner.region)
        rig.platform.invalidated.clear()
        tapPlus(3) // inside the region: cancels, swallowed
        assertFalse(clean.cleaner.active)
        assertEquals(42, life(3))
        assertTrue(rig.platform.invalidated.isNotEmpty()) // redrawn at once
        assertTrue(clean.cadence.due) // cancelled, not done: retried at the next quiet moment
        runUntilClean()
        tapPlus(0) // outside: cancels and acts
        assertFalse(clean.cleaner.active)
        assertEquals(41, life(0))
    }

    @Test fun neverWhileATouchIsDown() {
        every(2)
        tapPlus(3); tapPlus(3)
        clock.advance(30_000) // readout + effects done
        rig.stage.down(1, 1, 1, clock.now) // a finger resting on the table
        clock.advance(30_000)
        assertFalse(clean.cleaner.active)
        rig.stage.up(1)
        runUntilClean()
        assertTrue(clean.cleaner.active)
    }

    @Test fun neverWhileAnOverlayIsOpenAndClosingAFullOverlayCleans() {
        every(2)
        tapPlus(3); tapPlus(3)
        settingsCard.open(0)
        clock.advance(120_000)
        assertFalse(clean.cleaner.active)
        assertFalse(clean.cadence.waiting) // nothing ticking while the card sits open
        settingsCard.close()
        assertTrue(clean.cleaner.active) // the close's full redraw gets the ink keyframe
        assertTrue(clean.cleaner.full)
        clock.advance(ScreenCleaner.HOLD_MS)
        assertFalse(clean.cleaner.active)
        assertEquals(0, clean.cadence.count)
    }

    @Test fun closingAFullOverlayWithTheSettingOffJustRedraws() {
        every(0)
        settingsCard.open(0)
        settingsCard.close()
        assertFalse(clean.cleaner.active)
    }

    @Test fun offNeverCleansAndSchedulesNothing() {
        every(0)
        repeat(50) { tapPlus(it % 4) }
        clock.advance(120_000)
        assertEquals(0, clean.cleaner.count)
        assertFalse(rig.stage.timersPending())
    }

    @Test fun manualCleanWorksWhenOffAndResetsTheCount() {
        every(0)
        rig.stage.send(Events.CLEAN, Stage.SCREEN)
        assertTrue(clean.cleaner.active)
        assertTrue(clean.cleaner.full)
        clock.advance(ScreenCleaner.HOLD_MS)
        assertFalse(clean.cleaner.active)
        every(10)
        repeat(4) { tapPlus(1) }
        assertTrue(clean.cadence.count > 0)
        rig.stage.send(Events.CLEAN, Stage.SCREEN)
        clock.advance(ScreenCleaner.HOLD_MS)
        assertEquals(0, clean.cadence.count)
    }

    @Test fun newGameResetsTheCount() {
        every(50)
        repeat(5) { tapPlus(2) }
        assertTrue(clean.cadence.count > 0)
        rig.stage.dispatch(-1, Action.NewGame)
        assertEquals(0, clean.cadence.count)
    }

    @Test fun debugOverrideBeatsTheSetting() {
        EinkConfig.cleanEvery = 3
        rig.stage.detach(); rig.stage.attach()
        assertEquals(3, clean.cadence.threshold)
    }

    @Test fun pauseDropsThePendingTimer() {
        every(2)
        tapPlus(3); tapPlus(3)
        rig.stage.send(Events.PAUSE, Stage.SCREEN) // backgrounded before the quiet moment
        assertFalse(clean.cadence.waiting)
        clock.advance(30_000)
        assertFalse(clean.cleaner.active)
        rig.stage.send(Events.RESUME, Stage.SCREEN)
        runUntilClean(CleanCadence.QUIET_MS + 100)
        assertTrue(clean.cleaner.active)
    }

    @Test fun vendorReplacesTheFlashForFullCleansOnly() {
        var calls = 0
        EinkConfig.vendorFull = ScreenCleaner.FullRefresh { calls++; true }
        rig.stage.detach(); rig.stage.attach()
        every(2)
        tapPlus(3); tapPlus(3)
        runUntilClean()
        assertTrue(clean.cleaner.black) // seat clean: app flash
        clock.advance(ScreenCleaner.HOLD_MS)
        rig.stage.send(Events.CLEAN, Stage.SCREEN)
        assertFalse(clean.cleaner.black)
        clock.advance(ScreenCleaner.VENDOR_DELAY_MS)
        assertEquals(1, calls)
        assertFalse(clean.cleaner.active)
    }
}
