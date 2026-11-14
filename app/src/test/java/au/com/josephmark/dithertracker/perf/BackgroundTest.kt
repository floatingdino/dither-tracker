package au.com.josephmark.dithertracker.perf

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.purity.ScreenRig
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.turns.TurnTab
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.tools.DiceLayout
import au.com.josephmark.dithertracker.tools.DiceState
import au.com.josephmark.dithertracker.tools.FirstPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import au.com.josephmark.dithertracker.fx.FxComponent
import org.junit.Test

/**
 * T20 battery: once the app is paused nothing on the Play screen is left ticking — no readout
 * commit, no effect keyframes, no e-ink clean — and the game it leaves behind is committed (so the
 * save `onPause` flushes has the closed group). Measured on the Nomad before this: a readout pending
 * at HOME committed 3 s later in the background and its effect drew four frames there (up to 20 ms
 * each).
 */
class BackgroundTest {

    private fun armed(rig: ScreenRig): String {
        val out = StringBuilder()
        for (id in 0 until rig.stage.timerCount) if (rig.platform.clock.isArmed(id)) out.append(id).append(' ')
        return out.toString().trim()
    }

    @Test fun pauseCommitsReadoutsAndStopsEveryEffectAndTimer() {
        val rig = ScreenRig(1872, 1404, 4)
        rig.tap(rig.life, 3, PlayLayout.MINUS)
        rig.tap(rig.life, 3, PlayLayout.MINUS)
        assertEquals(-2, rig.stage.readouts.delta(3))
        assertTrue("the readout's commit timer is armed", rig.stage.timersPending())
        val life = rig.session.state.players[3].life

        rig.stage.send(Events.PAUSE, Stage.SCREEN)

        assertEquals("readout committed on pause", 0, rig.stage.readouts.delta(3))
        assertEquals("the change itself is kept", life, rig.session.state.players[3].life)
        assertFalse("no effect plays in the background", rig.fx.player.isPlaying(3))
        assertEquals("timers armed after pause", "", armed(rig))
        val before = rig.platform.count
        rig.advance(60_000)
        assertEquals("nothing redraws in the background", before, rig.platform.count)
    }

    @Test fun pauseDuringAnEffectStopsIt() {
        val rig = ScreenRig(1872, 1404, 4)
        rig.tap(rig.life, 2, PlayLayout.MINUS)
        rig.advance(FxComponent.QUIET_MS) // quiet (T55): the hit effect is playing beside the readout
        assertTrue(rig.fx.player.isPlaying(2))
        rig.stage.send(Events.PAUSE, Stage.SCREEN)
        assertFalse(rig.fx.player.isPlaying(2))
        assertEquals("timers armed after pause", "", armed(rig))
    }

    @Test fun pauseWithACounterOrDayNightEffectPendingLeavesNothingArmed() {
        val rig = ScreenRig(1872, 1404, 4)
        rig.stage.dispatch(2, Action.ChangeCounter(rig.id(2), au.com.josephmark.dithertracker.core.Counter.POISON, 1))
        rig.stage.dispatch(1, Action.SetDayNight(au.com.josephmark.dithertracker.core.DayNight.NIGHT))
        rig.stage.send(Events.PAUSE, Stage.SCREEN)
        assertEquals("timers armed after pause", "", armed(rig))
        val before = rig.platform.count
        rig.advance(60_000)
        assertEquals(before, rig.platform.count)
        // Mid all-seat effect: stopped, nothing armed.
        rig.stage.send(Events.RESUME, Stage.SCREEN)
        rig.stage.dispatch(1, Action.SetDayNight(au.com.josephmark.dithertracker.core.DayNight.DAY))
        rig.advance(FxComponent.QUIET_MS)
        assertTrue(rig.fx.player.group != 0)
        rig.stage.send(Events.PAUSE, Stage.SCREEN)
        assertEquals(0, rig.fx.player.active)
        assertEquals("timers armed after pause", "", armed(rig))
    }

    @Test fun pauseBeforeTheQuietTimerFiresLeavesNothingArmed() {
        val rig = ScreenRig(1872, 1404, 4)
        rig.tap(rig.life, 2, PlayLayout.MINUS)
        rig.advance(FxComponent.QUIET_MS - 1) // the effect's quiet timer (T55) is still armed
        assertFalse(rig.fx.player.isPlaying(2))
        rig.stage.send(Events.PAUSE, Stage.SCREEN)
        assertFalse(rig.fx.player.isPlaying(2))
        assertEquals("timers armed after pause", "", armed(rig))
        val before = rig.platform.count
        rig.advance(60_000)
        assertEquals("nothing plays or redraws in the background", before, rig.platform.count)
        assertFalse(rig.fx.player.isPlaying(2))
    }

    @Test fun pauseInTheMiddleOfAKoHandsTheTileOver() {
        val rig = ScreenRig(1872, 1404, 4)
        rig.dispatch(Action.SetLife(rig.id(1), 1))
        rig.advance(10_000)
        rig.tap(rig.life, 1, PlayLayout.MINUS) // 1 -> 0: the KO waits for the flourish (KO hold)
        rig.stage.send(Events.PAUSE, Stage.SCREEN)
        assertTrue(rig.session.state.isKo(rig.session.state.players[1]))
        assertFalse(rig.fx.player.isPlaying(1))
        // The KO tile's own safety timer was released with the hold; nothing else remains.
        assertEquals("timers armed after pause", "", armed(rig))
    }

    /**
     * T52: the roll presentation's one frame timer is armed only while it plays. Pausing mid-roll
     * shows the result (one redraw, held until the app is back) and leaves nothing armed — not the
     * frame timer, not the panel's idle timeout; resume re-arms only the idle timeout.
     */
    @Test fun pauseMidRollShowsTheResultAndArmsNothing() {
        val rig = ScreenRig(1872, 1404, 4)
        assertEquals("nothing armed before", "", armed(rig))
        rig.openFromMenu(MenuItem.DICE)
        val state = rig.dice.state
        assertTrue("opens empty (T59), nothing armed but the idle timeout", state.empty)
        rig.tap(rig.dice, Stage.SCREEN, DiceLayout.D20)
        assertEquals(DiceState.ROLLING, state.phase)
        rig.advance(300)
        val value = state.value
        rig.stage.send(Events.PAUSE, Stage.SCREEN)
        assertEquals(DiceState.SHOWN, state.phase)
        assertEquals(value, state.value)
        assertEquals("timers armed after pause", "", armed(rig))
        val before = rig.platform.count
        rig.advance(60 * 60_000L)
        assertEquals("nothing redraws in the background", before, rig.platform.count)
        assertTrue("the panel is still there on return", state.isOpen)
        rig.stage.send(Events.RESUME, Stage.SCREEN)
        assertEquals(DiceState.SHOWN, state.phase)
        rig.advance(DiceState.IDLE_MS)
        assertFalse("the idle timeout runs again after resume", state.isOpen)
        assertEquals("", armed(rig))
        // A presentation that ends by itself disarms its timer too.
        rig.openFromMenu(MenuItem.DICE)
        rig.tap(rig.dice, Stage.SCREEN, DiceLayout.COIN)
        rig.advance(DiceState.PRESENT_MAX_MS)
        assertEquals(DiceState.SHOWN, state.phase)
        rig.tap(rig.dice, Stage.SCREEN, DiceLayout.CLOSE)
        assertEquals("", armed(rig))
    }

    /** T59: the first-player roll-off lands on pause, uncovers the tiles; the flag's clear timer waits for resume. */
    @Test fun pauseDuringTheFirstPlayerRollOffLandsIt() {
        val rig = ScreenRig(1872, 1404, 4)
        val first = rig.stage.components.filterIsInstance<FirstPlayer>().single()
        rig.openFromMenu(MenuItem.FIRST)
        assertTrue(first.roll.active)
        rig.advance(200)
        rig.stage.send(Events.PAUSE, Stage.SCREEN)
        assertFalse(first.roll.active)
        assertTrue(first.seat >= 0)
        assertEquals(rig.id(first.seat), rig.session.state.turn.startingPlayer)
        assertEquals("timers armed after pause", "", armed(rig))
        rig.stage.send(Events.RESUME, Stage.SCREEN)
        rig.advance(FirstPlayer.SHOW_MS)
        assertEquals(-1, first.seat)
    }

    /**
     * T66: the turn feature arms nothing but a pass's 5 s report clear — nothing idle in any Turns
     * mode, nothing in the background; a pause drops a showing report (and its timer).
     */
    @Test fun turnReportsArmNothingIdleAndNothingPaused() {
        val rig = ScreenRig(1872, 1404, 4)
        val turn = rig.stage.components.filterIsInstance<TurnTab>().single()
        rig.dispatch(Action.SetStartingPlayer(rig.id(2)))
        rig.stage.settings.update(rig.stage.settings.current.copy(turnTimer = TurnTimer.OFF))
        assertEquals("Turns ON, idle: nothing armed", "", armed(rig))
        val idle = rig.platform.count
        rig.advance(3 * 3_600_000L)
        assertEquals("Turns ON: three idle hours, no redraw", idle, rig.platform.count)
        for (t in listOf(TurnTimer.GAME, TurnTimer.TURN)) {
            // (GAME / TURN arm the diamond's pulse, hourglassPulsesOncePerActiveMinute…: never the tab.)
            rig.stage.settings.update(rig.stage.settings.current.copy(turnTimer = t))
            rig.advance(3 * 3_600_000L)
            assertFalse("Turns $t: the tab arms nothing idle", turn.reportArmed)
        }
        rig.stage.settings.update(rig.stage.settings.current.copy(turnTimer = TurnTimer.TURN))
        rig.tap(turn, 2, TurnTab.PASS)
        assertTrue(turn.reportArmed)
        rig.stage.send(Events.PAUSE, Stage.SCREEN)
        assertEquals("timers armed after pause", "", armed(rig))
        assertEquals("", turn.report.toString())
        val before = rig.platform.count
        rig.advance(3 * 3_600_000L)
        assertEquals("nothing redraws in the background", before, rig.platform.count)
        rig.stage.send(Events.RESUME, Stage.SCREEN)
        assertFalse("the report is not re-armed on resume", turn.reportArmed)
    }

    /**
     * T66: the diamond's hourglass pulse is the turn feature's only periodic timer — exactly one pulse
     * per active minute with GAME / TURN, nothing with OFF / ON however long, nothing while paused.
     */
    @Test fun hourglassPulsesOncePerActiveMinuteAndNeverPaused() {
        val rig = ScreenRig(1872, 1404, 4)
        val pulse = rig.stage.components.filterIsInstance<au.com.josephmark.dithertracker.chrome.HourglassPulse>().single()
        rig.dispatch(Action.SetStartingPlayer(rig.id(0)))
        for (t in listOf(TurnTimer.OFF)) {
            rig.stage.settings.update(rig.stage.settings.current.copy(turnTimer = t))
            assertFalse(pulse.armed)
            val before = rig.platform.count
            rig.advance(5 * 3_600_000L)
            assertEquals("Turns ON: five idle hours, no redraw", before, rig.platform.count)
        }
        rig.stage.settings.update(rig.stage.settings.current.copy(turnTracker = false))
        rig.advance(3_600_000L)
        assertEquals(0, pulse.pulses)
        rig.stage.settings.update(rig.stage.settings.current.copy(turnTracker = true, turnTimer = TurnTimer.GAME))
        val p0 = pulse.pulses
        rig.advance(30 * 60_000L)
        assertEquals("one pulse per active minute", 30, pulse.pulses - p0)
        rig.session.pause(rig.now)
        rig.stage.send(Events.PAUSE, Stage.SCREEN)
        assertEquals("timers armed after pause", "", armed(rig))
        val before = rig.platform.count
        rig.advance(8 * 3_600_000L)
        assertEquals("nothing redraws in the background", before, rig.platform.count)
        rig.session.resume(rig.now)
        rig.stage.send(Events.RESUME, Stage.SCREEN)
        assertTrue(pulse.armed)
    }
}
