package au.com.josephmark.dithertracker.perf

import au.com.josephmark.dithertracker.commander.CommanderZones
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.fx.Fx
import au.com.josephmark.dithertracker.ko.KoComponent
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.purity.ScreenRig
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.tools.DiceLayout
import au.com.josephmark.dithertracker.tools.DiceState
import au.com.josephmark.dithertracker.tools.Outcome
import au.com.josephmark.dithertracker.tools.Presentation
import au.com.josephmark.dithertracker.turns.TurnLayout
import au.com.josephmark.dithertracker.turns.TurnTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory

/**
 * T20: the draw and touch paths allocate nothing, measured (not read off the code) with the JVM's
 * per-thread allocation counter over the real Play screen ([ScreenRig]: every component, the stage
 * host, a raster renderer) at the Nomad's 1872×1404, S = 4.
 *
 * - **Draw**: a full-screen `Stage.draw` in every screen state (play, readouts, drawer, commander
 *   mode, gem, settings, dice, history, KO and winner, every effect keyframe) allocates 0 bytes.
 * - **Touch without a game change** (press + release / cancel on any zone, the hit-test over every
 *   component, feedback, invalidation, cover changes from opening/closing overlays) allocates 0 bytes.
 * - **A life tap / hold step** allocates only inside `:core` (the new immutable `GameState` and its
 *   history entry): pinned here as a byte budget so a regression in the app layer shows up.
 *
 * Steady state only: each step runs a few times first (lazy caches, JIT), and the best of several
 * runs is taken (the JVM itself allocates now and then on this thread when it deoptimises).
 */
class AllocationBudgetTest {

    private val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val tid = Thread.currentThread().id

    private fun bytes(warm: Int = 3, runs: Int = 5, block: () -> Unit): Long {
        repeat(warm) { block() }
        var best = Long.MAX_VALUE
        repeat(runs) {
            val before = bean.getThreadAllocatedBytes(tid)
            block()
            best = minOf(best, bean.getThreadAllocatedBytes(tid) - before)
        }
        return best
    }

    private fun rig() = ScreenRig(1872, 1404, 4).also { it.platform.record = false }

    /** One pointer id, reused (the rig's own taps count pointers up). */
    private fun ScreenRig.click(gx: Int, gy: Int) {
        stage.down(0, gx, gy, now)
        stage.up(0)
    }

    private fun ScreenRig.drawBytes(): Long = bytes { render(false) }

    private fun assertDrawFree(rig: ScreenRig, state: String) {
        assertEquals("bytes allocated by one full-screen draw: $state", 0L, rig.drawBytes())
        assertEquals("bytes allocated by one full-screen draw, dark mode: $state", 0L, bytes { rig.render(true) })
    }

    @Test fun drawAllocatesNothingInAnyScreenState() {
        val rig = rig()
        assertDrawFree(rig, "fresh table")
        rig.tap(rig.life, 3, PlayLayout.PLUS)
        rig.tap(rig.life, 1, PlayLayout.MINUS)
        assertDrawFree(rig, "readouts shown")
        rig.press(rig.life, 3, PlayLayout.PLUS)
        assertDrawFree(rig, "life half pressed")
        rig.cancel()
        rig.advance(10_000)
        rig.tap(rig.tab, 3, 0)
        rig.tap(rig.drawer, 3, DrawerZones.cell(1, DrawerZones.PLUS))
        assertDrawFree(rig, "drawer open, a counter set")
        rig.tap(rig.drawer, 3, DrawerZones.BUTTON) // commander mode, seat 3 the target
        rig.tap(rig.cmd, 2, CommanderZones.A_PLUS)
        assertDrawFree(rig, "commander mode with a readout")
        rig.advance(60_000) // mode times out
        rig.openMenu()
        assertDrawFree(rig, "gem open")
        rig.closeMenu() // the centre X closes it
        rig.openFromMenu(MenuItem.SETUP)
        assertDrawFree(rig, "settings card")
        rig.settings.close()
        rig.openFromMenu(MenuItem.LOG)
        assertDrawFree(rig, "history card")
        rig.tapAt(1, 1)
        rig.openFromMenu(MenuItem.DICE)
        assertDrawFree(rig, "dice panel empty")
        rig.tap(rig.dice, Stage.SCREEN, DiceLayout.D20)
        // T52: every keyframe of the presentation, then the result.
        val o = rig.dice.state.outcome
        val pres = rig.dice.state.presentation!!
        for (beat in 0 until pres.beats(o)) {
            assertDrawFree(rig, "dice keyframe $beat")
            rig.advance(pres.holdMs(o, beat))
        }
        assertDrawFree(rig, "dice result")
        // T61: a heads and a tails flip at both display speeds — every keyframe (the landing shows the
        // coin's face) and the result.
        for (speed in intArrayOf(DisplaySpeed.SLOW, DisplaySpeed.FAST)) try {
            DisplaySpeed.override = speed
            for (face in intArrayOf(Outcome.HEADS, Outcome.TAILS)) {
                var tries = 0
                while (true) {
                    rig.tap(rig.dice, Stage.SCREEN, DiceLayout.COIN)
                    if (rig.dice.state.outcome == face) break
                    rig.tap(rig.dice, Stage.SCREEN, DiceLayout.COIN) // skip to the result
                    check(++tries < 500)
                }
                val p = rig.dice.state.presentation!!
                for (beat in 0 until p.beats(face)) {
                    assertDrawFree(rig, "coin ${DisplaySpeed.name(speed)} $face keyframe $beat")
                    rig.advance(p.holdMs(face, beat))
                }
                assertDrawFree(rig, "coin ${DisplaySpeed.name(speed)} $face result")
            }
        } finally {
            DisplaySpeed.override = DisplaySpeed.NO_OVERRIDE
        }
        rig.tap(rig.dice, Stage.SCREEN, DiceLayout.CLOSE)
        rig.advance(1_000)
        rig.dispatch(Action.SetLife(rig.id(2), 0))
        rig.advance(6_000)
        assertDrawFree(rig, "a KO tile")
        rig.dispatch(Action.SetLife(rig.id(0), 0))
        rig.dispatch(Action.SetLife(rig.id(1), 0))
        rig.advance(10_000)
        assertDrawFree(rig, "winner")
    }

    @Test fun everyEffectKeyframeDrawsWithoutAllocating() {
        val rig = rig()
        for (fx in Fx.entries) {
            rig.fx.startGallery(arrayOf(fx), 37)
            rig.fx.stopGallery()
            for (i in 0 until fx.frameCount) {
                assertEquals("bytes allocated drawing ${fx.id} frame $i", 0L, rig.drawBytes())
                rig.advance(rig.fx.player.hold(fx, i))
            }
            rig.fx.player.cancelAll()
            rig.advance(10_000)
        }
    }

    @Test fun pressAndReleaseWithoutAGameChangeAllocateNothing() {
        val rig = rig()
        val life = rig.pointOf(rig.life, 3, PlayLayout.PLUS)!!
        assertEquals("press + cancel on a life half (hit-test over every zone, feedback, invalidate)", 0L, bytes {
            rig.stage.down(0, life[0], life[1], rig.now)
            rig.stage.cancel()
        })
        val none = noZone(rig)
        assertEquals("press + release on no zone", 0L, bytes { rig.click(none[0], none[1]) })
        assertEquals("gem open + close (cover on/off, both redraws)", 0L, bytes {
            rig.click(rig.l.diamondCx, rig.l.diamondCy)
            rig.click(rig.l.diamondCx, rig.l.diamondCy)
        })
        val tab = rig.pointOf(rig.tab, 3, 0)!!
        rig.click(tab[0], tab[1])
        val bar = rig.pointOf(rig.drawer, 3, DrawerZones.TAB) ?: error("no bar")
        rig.click(bar[0], bar[1])
        assertEquals("drawer open + close", 0L, bytes {
            rig.click(tab[0], tab[1])
            rig.click(bar[0], bar[1])
        })
    }

    /** A dither point no zone claims (a divider gap or a blocking rect). */
    private fun noZone(rig: ScreenRig): IntArray {
        for (y in 0 until rig.grid.height) for (x in 0 until rig.grid.width) {
            if (rig.stage.hitTest(x, y) == PlayLayout.NONE) return intArrayOf(x, y)
        }
        error("every point is a zone")
    }

    @Test fun aLifeTapAllocatesOnlyTheCoreStateChange() {
        val rig = rig()
        val p = rig.pointOf(rig.life, 3, PlayLayout.PLUS)!!
        val m = rig.pointOf(rig.life, 3, PlayLayout.MINUS)!!
        // +1 then -1 keeps the total (and the readout group) in range however often it runs.
        val tap = bytes(warm = 20) { rig.click(p[0], p[1]); rig.click(m[0], m[1]) } / 2
        // The same change straight through the session, no stage listening: what core costs alone.
        rig.stage.detach()
        val core = bytes(warm = 20) {
            rig.session.changeLife(3, 1, rig.now)
            rig.session.changeLife(3, -1, rig.now)
        } / 2
        println("T20 allocation: life tap ${tap} B (core alone ${core} B)")
        assertTrue("a life tap allocates $tap B, core alone $core B: the app layer added ${tap - core} B", tap - core <= APP_SLACK_BYTES)
        assertTrue("core's ChangeLife allocates $core B (budget $CORE_BUDGET_BYTES B)", core <= CORE_BUDGET_BYTES)
    }

    @Test fun readoutCommitAndEffectKeyframesAllocateNothingInTheApp() {
        val rig = rig()
        val p = rig.pointOf(rig.life, 3, PlayLayout.PLUS)!!
        val m = rig.pointOf(rig.life, 3, PlayLayout.MINUS)!!
        // Each round: two taps (net -1 then +1 alternately: the total stays bounded), the readout's
        // commit 3 s later, its effect's keyframes, the clean end frame; a draw after each step.
        var sign = 0
        val round = bytes(warm = 6) {
            val q = if (sign++ % 2 == 0) m else p
            rig.click(q[0], q[1])
            rig.render(false)
            rig.advance(3_100)
            rig.render(false)
            repeat(12) { rig.advance(250); rig.render(false) }
        }
        rig.stage.detach()
        var s2 = 0
        val core = bytes(warm = 6) {
            rig.session.changeLife(3, if (s2++ % 2 == 0) -1 else 1, rig.now)
            rig.session.endLifeGroup(3)
        }
        println("T20 allocation: tap + commit + effect round ${round} B (core alone ${core} B)")
        assertTrue("tap + commit + effect allocates $round B, core alone $core B", round - core <= APP_SLACK_BYTES)
    }

    /**
     * T55: a counter gain, a toggle and day/night (all seats at once) — the state diff, the quiet
     * timer, the effect's keyframes and its clean frame — add nothing to core's own state change.
     */
    @Test fun counterToggleAndDayNightEffectsAllocateNothingInTheApp() {
        val rig = rig()
        val id = rig.id(2)
        var i = 0
        fun step(a: au.com.josephmark.dithertracker.core.Action) {
            rig.stage.dispatch(2, a)
            rig.render(false)
            repeat(16) { rig.advance(250); rig.render(false) } // quiet, keyframes, clean, the burst window
        }
        val actions = listOf(
            au.com.josephmark.dithertracker.core.Action.ChangeCounter(id, au.com.josephmark.dithertracker.core.Counter.POISON, 1),
            au.com.josephmark.dithertracker.core.Action.ChangeCounter(id, au.com.josephmark.dithertracker.core.Counter.POISON, -1),
            au.com.josephmark.dithertracker.core.Action.SetToggle(id, au.com.josephmark.dithertracker.core.Toggle.MONARCH, true),
            au.com.josephmark.dithertracker.core.Action.SetToggle(id, au.com.josephmark.dithertracker.core.Toggle.MONARCH, false),
            au.com.josephmark.dithertracker.core.Action.SetDayNight(au.com.josephmark.dithertracker.core.DayNight.NIGHT),
            au.com.josephmark.dithertracker.core.Action.SetDayNight(au.com.josephmark.dithertracker.core.DayNight.DAY),
        )
        val round = bytes(warm = 4) { for (a in actions) step(a) }
        rig.stage.detach()
        val core = bytes(warm = 4) { for (a in actions) rig.session.dispatch(a, rig.now, null) }
        println("T55 allocation: counter/toggle/day-night round ${round} B (core alone ${core} B)")
        assertTrue("effects for counters/toggles/day-night allocate $round B, core alone $core B", round - core <= APP_SLACK_BYTES)
    }

    companion object {
        /** What the app layer may add on top of core per change (0 today; slack for JVM noise). */
        const val APP_SLACK_BYTES = 64L

        /** Core's immutable state change for one ±1 (measured ~1 KB on JDK 21; a regression guard, not a target). */
        const val CORE_BUDGET_BYTES = 4_096L
    }

    /**
     * T17: the turn tab draws allocation-free (far edge with minutes, pressed, on a KO tile at the near
     * edge), and a minute tick (timer callback, re-sync, digits invalidation, re-arm) allocates nothing.
     */
    @Test fun turnTabDrawAndMinuteTickAllocateNothing() {
        val rig = rig()
        val turn = rig.stage.components.filterIsInstance<TurnTab>().single()
        rig.dispatch(Action.SetStartingPlayer(rig.id(3)))
        assertDrawFree(rig, "turn tab")
        rig.stage.settings.update(rig.stage.settings.current.copy(turnTimer = TurnTimer.GAME))
        rig.advance(12 * 60_000L)
        assertEquals(12, turn.minutes)
        assertDrawFree(rig, "turn tab with minutes")
        rig.press(turn, 3, TurnTab.PASS)
        assertDrawFree(rig, "turn tab pressed")
        rig.cancel()
        assertEquals("a minute tick", 0L, bytes { rig.advance(60_000) })
        val tab = rig.pointOf(turn, 3, TurnTab.PASS)!!
        assertEquals("press + cancel on the turn tab", 0L, bytes {
            rig.stage.down(0, tab[0], tab[1], rig.now)
            rig.stage.cancel()
        })
        rig.dispatch(Action.SetLife(rig.id(3), 0))
        rig.advance(6_000)
        assertEquals(TurnLayout.NEAR, turn.place)
        assertDrawFree(rig, "turn tab on a KO tile")
    }
}
