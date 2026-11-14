package au.com.josephmark.dithertracker.chrome

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.fx.FxComponent
import au.com.josephmark.dithertracker.history.HistoryOverlay
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.purity.PurityChecks
import au.com.josephmark.dithertracker.purity.ScreenRig
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.tools.FirstPlayer
import au.com.josephmark.dithertracker.turns.TurnTab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** T66: the diamond's egg-timer pulse once a minute of active time while a Turns timer mode is on. */
class HourglassPulseTest {

    @After fun speed() { DisplaySpeed.override = DisplaySpeed.NO_OVERRIDE }

    private class Rig(timer: TurnTimer = TurnTimer.GAME, s: Int = 4, w: Int = 1872, h: Int = 1404) {
        val r = ScreenRig(w, h, s)
        val p = r.stage.components.filterIsInstance<HourglassPulse>().single()
        val turn = r.stage.components.filterIsInstance<TurnTab>().single()

        init {
            r.stage.components.filterIsInstance<FxComponent>().single().player.enabled = false
            r.stage.settings.update(r.stage.settings.current.copy(turnTimer = timer))
        }

        fun start(seat: Int = 0) = r.dispatch(Action.SetStartingPlayer(r.id(seat)))

        fun invalidations(block: () -> Unit): List<IRect> {
            val out = ArrayList<IRect>()
            r.stage.observer = object : Stage.Observer {
                override fun onInvalidate(rect: IRect) { out += IRect(rect.left, rect.top, rect.right, rect.bottom) }
                override fun onPointerDown() {}
                override fun onSettled(cause: Int) {}
            }
            try { block() } finally { r.stage.observer = null }
            return out
        }
    }

    @Test fun gameModePulsesOnEachActiveMinuteRedrawingOnlyTheGlyphBox() {
        DisplaySpeed.override = DisplaySpeed.FAST
        val g = Rig(TurnTimer.GAME)
        g.start()
        assertTrue(g.p.armed)
        val n0 = g.r.platform.count
        g.r.advance(59_999)
        assertEquals("nothing before the minute", n0, g.r.platform.count)
        val rects = g.invalidations {
            g.r.advance(1)
            assertEquals(0, g.p.frame)
            g.r.advance(150); assertEquals(1, g.p.frame)
            g.r.advance(350); assertEquals(2, g.p.frame)
            g.r.advance(200); assertEquals(3, g.p.frame)
            g.r.advance(150); assertEquals(-1, g.p.frame)
        }
        assertEquals("4 keyframes and the glyph back: 5 redraws of the box only", List(5) { g.p.box }, rects)
        // At S = 4 the box is the bars' plate / the sun's halo: 19 × 19 dither px.
        assertEquals(19, g.p.box.width); assertEquals(19, g.p.box.height)
        // Exactly one pulse per minute.
        g.r.advance(10 * 60_000L)
        assertEquals(11, g.p.pulses)
    }

    @Test fun slowDisplaysGetThreeHeldFrames() {
        DisplaySpeed.override = DisplaySpeed.SLOW
        val g = Rig(TurnTimer.TURN)
        g.start()
        g.r.advance(60_000)
        assertEquals(0, g.p.frame)
        g.r.advance(300); assertEquals(1, g.p.frame)
        g.r.advance(450); assertEquals(2, g.p.frame)
        g.r.advance(300); assertEquals(-1, g.p.frame)
    }

    @Test fun turnModeCountsTheMinutesOfTheTurnAndAPassRestartsThem() {
        val g = Rig(TurnTimer.TURN)
        g.start()
        g.r.advance(40_000)
        g.r.tap(g.turn, 0, TurnTab.PASS) // the turn starts again at 40 s
        g.r.advance(59_000)
        assertEquals(0, g.p.pulses)
        g.r.advance(1_000) // 100 s: one minute into the new turn
        assertEquals(1, g.p.pulses)
    }

    @Test fun offOnNeverArmAndPausedTimeDoesNotCount() {
        for (t in listOf(TurnTimer.OFF)) {
            val g = Rig(t)
            g.start()
            assertFalse(g.p.armed)
            val n = g.r.platform.count
            g.r.advance(5 * 3_600_000L)
            assertEquals(n, g.r.platform.count)
            g.r.stage.settings.update(g.r.stage.settings.current.copy(turnTracker = false, turnTimer = TurnTimer.GAME))
            assertFalse("tracker off", g.p.armed)
        }
        val g = Rig(TurnTimer.GAME)
        g.start()
        g.r.advance(30_000)
        g.r.session.pause(g.r.now)
        g.r.stage.send(Events.PAUSE, Stage.SCREEN)
        assertFalse(g.p.armed)
        g.r.advance(3_600_000)
        assertEquals(0, g.p.pulses)
        g.r.session.resume(g.r.now)
        g.r.stage.send(Events.RESUME, Stage.SCREEN)
        assertTrue(g.p.armed)
        g.r.advance(29_000)
        assertEquals("30 active s were left of the minute", 0, g.p.pulses)
        g.r.advance(1_000)
        assertEquals(1, g.p.pulses)
    }

    @Test fun neverUnderTheMenuCommanderModeOverlaysTheRollOffOrAfterAWinner() {
        val g = Rig(TurnTimer.GAME)
        g.start()
        g.r.advance(60_000) // a pulse is running
        assertTrue(g.p.frame >= 0)
        g.r.openMenu() // a press opens the menu as ever, and ends the pulse at once
        assertTrue(g.r.menu.state.isOpen)
        assertEquals(-1, g.p.frame); assertFalse(g.p.armed)
        g.r.advance(5 * 60_000L)
        assertEquals(1, g.p.pulses)
        g.r.closeMenu()
        g.r.advance(1_000)
        assertTrue("re-armed once the gem closes", g.p.armed)
        // A full overlay.
        val history = g.r.stage.components.filterIsInstance<HistoryOverlay>().single()
        history.open(MenuItem.LOG, 0)
        assertFalse(g.p.armed)
        history.close()
        assertTrue(g.p.armed)
        // Commander mode.
        g.r.stage.send(Events.COMMANDER_TARGET, 0)
        assertFalse(g.p.armed)
        g.r.advance(60_000) // the mode times out
        // The first-player roll-off hides the diamond.
        g.r.stage.components.filterIsInstance<FirstPlayer>().single().pick()
        assertFalse(g.p.armed)
        g.r.advance(20_000)
        // A winner: done.
        for (s in 1 until 4) g.r.dispatch(Action.SetLife(g.r.id(s), 0))
        g.r.advance(10_000)
        assertFalse(g.p.armed)
        val p = g.p.pulses
        g.r.advance(10 * 60_000L)
        assertEquals(p, g.p.pulses)
    }

    @Test fun everyKeyframeIsPureInEveryDiamondStateAtBothSpeedsAndScales() {
        for (s in 2..4) for (dn in DayNight.entries) for (sp in intArrayOf(DisplaySpeed.FAST, DisplaySpeed.SLOW)) {
            DisplaySpeed.override = sp
            val g = Rig(TurnTimer.GAME, s)
            if (dn != DayNight.UNSET) g.r.dispatch(Action.SetDayNight(dn))
            g.start()
            val base = g.r.render(false).let { if (s == 4) it.writePng(File(PurityChecks.outDir, "t66/pulse-$dn-base.png"), 1); it.px.copyOf() }
            g.r.advance(60_000)
            var i = 0
            while (g.p.frame >= 0) {
                val img = g.r.render(false)
                val errs = PurityChecks.phaseErrors(img)
                assertTrue("S=$s $dn ${DisplaySpeed.name(sp)} frame ${g.p.frame}: $errs", errs.isEmpty())
                assertTrue("clip balanced", img.clipBalanced)
                // Nothing outside the glyph box changes.
                val b = g.p.box
                for (y in 0 until img.height) for (x in 0 until img.width) {
                    if (b.contains(x, y)) continue
                    if (img.px[y * img.width + x] != base[y * img.width + x]) throw AssertionError("S=$s $dn frame ${g.p.frame}: ($x,$y) outside the box changed")
                }
                if (s == 4) img.writePng(File(PurityChecks.outDir, "t66/pulse-$dn-${DisplaySpeed.name(sp)}-$i.png"), 1)
                val normal = img.px.copyOf()
                val inv = g.r.render(true)
                PurityChecks.complementError(normal, inv.px, inv.width)?.let { throw AssertionError(it) }
                g.r.advance(g.p.let { if (sp == DisplaySpeed.FAST) HourglassPulse.FAST_HOLDS[it.frame] else HourglassPulse.SLOW_HOLDS[it.frame] })
                i++
            }
            assertEquals(if (sp == DisplaySpeed.FAST) 4 else 3, i)
            assertTrue(g.r.r.cold.isEmpty())
        }
    }
}
