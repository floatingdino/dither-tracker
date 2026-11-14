package au.com.josephmark.dithertracker.turns

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Game
import au.com.josephmark.dithertracker.core.History
import au.com.josephmark.dithertracker.core.serial.SaveCodec
import au.com.josephmark.dithertracker.core.serial.SaveFile
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.history.HistoryOverlay
import au.com.josephmark.dithertracker.layout.CountRig
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.purity.PurityChecks
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.tools.FirstPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** T17: the turn tab (whose turn, pass, correct); T66: its post-pass reports, on the real Play screen. */
class TurnTabTest {

    private class Rig(players: Int = 4, w: Int = 1872, h: Int = 1404, s: Int = 4) {
        val c = CountRig(w, h, s, players)
        val turn = c.one<TurnTab>()
        val stage get() = c.stage
        val l get() = turn.layout!!

        /** Every rect a component invalidated during [block] (before the stage unions them). */
        fun invalidations(block: () -> Unit): List<IRect> {
            val out = ArrayList<IRect>()
            stage.observer = object : Stage.Observer {
                override fun onInvalidate(rect: IRect) { out += IRect(rect.left, rect.top, rect.right, rect.bottom) }
                override fun onPointerDown() {}
                override fun onSettled(cause: Int) {}
            }
            try { block() } finally { stage.observer = null }
            return out
        }

        fun timer(t: TurnTimer) = stage.settings.update(stage.settings.current.copy(turnTimer = t))
        fun tracker(on: Boolean) = stage.settings.update(stage.settings.current.copy(turnTracker = on))
        fun start(seat: Int) = c.dispatch(Action.SetStartingPlayer(c.id(seat)))
        fun active(): Int = c.session.state.let { it.indexOf(it.turn.activePlayer!!) }
        val clearId: Int get() = stage.timerId(turn, TurnTab.CLEAR)
        val armed: Boolean get() = c.platform.clock.isArmed(clearId)
        val report: String get() = turn.report.toString()
        val history get() = c.one<HistoryOverlay>()
        fun press() {
            val p = c.pointOf(turn, turn.shownSeat, TurnTab.PASS) ?: throw AssertionError("no tab")
            stage.down(0, p[0], p[1], c.now)
        }
        fun release() = stage.up(0)
        fun tapTab() = c.tap(turn, turn.shownSeat, TurnTab.PASS)

        fun png(name: String) = c.render(false).writePng(File(PurityChecks.outDir, "t17/$name.png"), 1)
    }

    private fun overlaps(a: IRect, b: IRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
    private fun inside(a: IRect, b: IRect) = a.left >= b.left && a.top >= b.top && a.right <= b.right && a.bottom <= b.bottom

    // ---- layout ------------------------------------------------------------------------------------

    @Test fun tabSitsInTheFarStripClearOfEverythingAtEveryArrangementScaleAndOrientation() {
        for (a in au.com.josephmark.dithertracker.layout.Arrangements.ALL) for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (s in 2..6) {
            val n = a.players
            val play = PlayLayout.compute(au.com.josephmark.dithertracker.dither.DitherGrid(w, h, s), n, a.id)
            if (!play.comfortable) continue
            val t = TurnLayout(play)
            val tag = "${a.id} ${w}x$h S=$s"
            assertTrue("$tag: $t", t.enabled)
            val icon = IRect(); val num = IRect()
            for (seat in 0 until n) {
                val tab = t.tab[seat]
                assertTrue("$tag seat $seat tab $tab in ${play.panels[seat]}", inside(tab, play.panels[seat]))
                assertTrue("$tag seat $seat hit in panel", inside(t.hit[seat], play.panels[seat]))
                assertTrue("$tag seat $seat tab in hit", inside(tab, t.hit[seat]))
                assertTrue("$tag seat $seat near tab in its hit", inside(t.nearTab[seat], t.nearHit[seat]))
                // A fingertip: the hit is at least the 9 mm target both ways.
                assertTrue("$tag seat $seat hit ${t.hit[seat]} < target ${play.target}", t.hit[seat].width >= play.target && t.hit[seat].height >= play.target)
                for ((what, r) in listOf(
                    "number" to play.numberSlot[seat], "readout" to play.readoutSlot[seat], "minus" to play.minusGlyph[seat],
                    "plus" to play.plusGlyph[seat], "under row" to play.underRow[seat], "drawer tab hit" to play.tabHit[seat],
                    "diamond" to play.diamond,
                )) {
                    assertFalse("$tag seat $seat: tab overlaps the $what", overlaps(tab, r))
                    if (what != "diamond") assertFalse("$tag seat $seat: tab hit overlaps the $what", overlaps(t.hit[seat], r))
                }
                for (other in 0 until n) if (other != seat) assertFalse("$tag: tabs $seat/$other", overlaps(t.hit[seat], t.hit[other]))
                // Content inside the tab at both placements: the icon, and every report length (T66:
                // `0:42` .. `9:59:59`) in the face the tab picks.
                for (place in intArrayOf(TurnLayout.FAR, TurnLayout.NEAR)) {
                    val box = t.tabAt(place, seat)
                    assertTrue("$tag seat $seat icon", inside(t.icon(seat, place, icon), box))
                    for (chars in 4..TimeFormat.MAX_CHARS) {
                        val f = t.reportFont(seat, place, chars)
                        assertTrue("$tag seat $seat place $place report $chars", inside(t.report(seat, place, chars, f, num), box))
                    }
                }
            }
        }
    }

    @Test fun nomadSizes() {
        // 4 players: the drawer tab's size, centred on the far edge; hit 105 × 29 (≈ 35 × 10 mm).
        val t4 = TurnLayout(PlayLayout.compute(au.com.josephmark.dithertracker.dither.DitherGrid(1872, 1404, 4), 4))
        assertEquals(IRect(72, 154, 161, 175), t4.tab[0]) // seat 0 turned 180°: its far edge is the screen centre
        assertEquals(IRect(72, 176, 161, 197), t4.tab[3])
        assertEquals(IRect(64, 176, 169, 205), t4.hit[3])
        // Reports in num-m (10 × 14 a glyph): `2:07` 46 wide, `1:47:12` 82 — both fit the 89 px tab.
        assertEquals(t4.font, t4.reportFont(3, TurnLayout.FAR, 7))
        // 6 players narrow: the middle seats' tabs stand beside the diamond, 45 wide: the `ui` face there.
        val t6 = TurnLayout(PlayLayout.compute(au.com.josephmark.dithertracker.dither.DitherGrid(1872, 1404, 4), 6))
        assertEquals(45, t6.tabW[1]); assertEquals(45, t6.tabW[4])
        assertEquals(t6.small, t6.reportFont(1, TurnLayout.FAR, 4))
        assertEquals(89, t6.tabW[0]); assertEquals(t6.font, t6.reportFont(0, TurnLayout.FAR, 7))
    }

    // ---- behaviour ---------------------------------------------------------------------------------

    @Test fun nothingShowsUntilAFirstPlayerIsPickedThenTheirTabDoes() {
        val r = Rig()
        assertEquals(-1, r.turn.shownSeat)
        assertNull(r.c.pointOf(r.turn, 0, TurnTab.PASS))
        // The menu's FIRST tool picks someone and starts tracking from them.
        r.c.one<FirstPlayer>().pick()
        // T59: the pick lands after the d20 roll-off; until then no tab gives it away.
        assertEquals(-1, r.turn.shownSeat)
        r.c.advance(10 * au.com.josephmark.dithertracker.tools.FirstRoll.LANDED_MS)
        val seat = r.active()
        assertEquals(seat, r.turn.shownSeat)
        assertEquals(1, r.c.session.state.turn.number)
        assertNotNull(r.c.pointOf(r.turn, seat, TurnTab.PASS))
        for (o in 0 until 4) if (o != seat) assertNull(r.c.pointOf(r.turn, o, TurnTab.PASS))
        assertTrue(r.c.check("first").isEmpty())
        r.png("4p-first")
    }

    @Test fun tapPassesClockwiseSkippingKnockedOutSeatsAndRedrawsOnlyTheTwoTabs() {
        val r = Rig()
        r.c.stage.components.filterIsInstance<au.com.josephmark.dithertracker.fx.FxComponent>().single().player.enabled = false // T55's effect (fx tests) aside
        r.start(1)
        r.c.dispatch(Action.MarkDead(r.c.id(2)))
        r.c.advance(5_000)
        val lives = r.c.session.state.players.map { it.life }
        val rects = r.invalidations { r.tapTab() }
        assertEquals(3, r.active())
        assertEquals(2, r.c.session.state.turn.number)
        assertEquals("a pass touches no life", lives, r.c.session.state.players.map { it.life })
        // Press inversion of the old tab, then the pass: the old tab and the new one, nothing else.
        assertTrue(rects.isNotEmpty())
        for (rect in rects) assertTrue("$rect", rect == r.l.tab[1] || rect == r.l.tab[3])
        assertTrue(rects.contains(r.l.tab[3]))
        // At S = 4 each is 89 × 21 dither px (356 × 84 device px).
        assertEquals(89, r.l.tab[3].width); assertEquals(21, r.l.tab[3].height)
        r.tapTab()
        assertEquals("seat 2 is out: on to 0", 0, r.active())
        assertEquals(1, r.c.session.state.turn.round)
        r.tapTab()
        assertEquals(1, r.active())
        assertEquals("back at the starter: round 2", 2, r.c.session.state.turn.round)
    }

    @Test fun undoTakesAPassBackAndHoldCorrectsWithoutCounting() {
        val r = Rig()
        r.start(0)
        r.tapTab()
        assertEquals(1, r.active())
        r.c.session.undo()
        assertEquals(0, r.active())
        assertEquals(0, r.turn.shownSeat)
        assertEquals(1, r.c.session.state.turn.number)
        // Hold: nothing for 1.5 s, then a step a second, each moving the turn one live seat without counting.
        r.c.dispatch(Action.MarkDead(r.c.id(1)))
        r.press()
        r.c.advance(600)
        assertEquals("0.6 s: still pressed, nothing moved", 0, r.active())
        r.c.advance(1_000)
        assertEquals(2, r.active())
        r.c.advance(1_000)
        assertEquals(3, r.active())
        r.release()
        assertEquals("release after a hold is not a pass", 3, r.active())
        assertEquals(1, r.c.session.state.turn.number)
        assertEquals(1, r.c.session.state.turn.round)
        assertTrue(r.c.check("held").isEmpty())
        // Let go between 0.5 and 1.5 s: neither a pass nor a correction.
        r.press()
        r.c.advance(900)
        r.release()
        assertEquals(3, r.active())
        assertEquals(1, r.c.session.state.turn.number)
    }

    @Test fun aKnockedOutActiveSeatKeepsItsTabAndTheGameOverHidesIt() {
        val r = Rig()
        r.start(2)
        r.c.dispatch(Action.SetLife(r.c.id(2), 0))
        r.c.advance(5_000)
        assertTrue(r.c.session.state.isKo(r.c.session.state.players[2]))
        assertEquals(2, r.turn.shownSeat)
        assertEquals("on the dead tile it takes the hidden drawer tab's place, clear of the tombstone", TurnLayout.NEAR, r.turn.place)
        assertNotNull("the tab wins over the KO tile's revive zone", r.c.pointOf(r.turn, 2, TurnTab.PASS))
        assertTrue(r.c.check("ko-active").isEmpty())
        r.png("4p-ko-active")
        r.tapTab()
        assertEquals(3, r.active())
        r.c.dispatch(Action.MarkDead(r.c.id(0)))
        r.c.dispatch(Action.MarkDead(r.c.id(1)))
        r.c.advance(5_000)
        assertEquals("one left standing: no tab", -1, r.turn.shownSeat)
    }

    @Test fun settingOffHidesItAndOverlaysAndCommanderModeHideIt() {
        val r = Rig()
        r.start(3)
        r.tracker(false)
        assertEquals(-1, r.turn.shownSeat)
        r.tracker(true)
        assertEquals(3, r.turn.shownSeat)
        // A full overlay (the history card) covers the table.
        r.history.open(MenuItem.LOG, 0)
        assertEquals(-1, r.turn.shownSeat)
        r.history.close()
        assertEquals(3, r.turn.shownSeat)
        // Commander mode (seat 0 asks to be the target).
        r.stage.send(Events.COMMANDER_TARGET, 0)
        assertEquals(-1, r.turn.shownSeat)
    }

    // ---- reports (T66) -----------------------------------------------------------------------------

    @Test fun nothingIsEverArmedWithoutAPassAndOnOrOffNeverReport() {
        val r = Rig()
        r.start(0)
        for (t in TurnTimer.entries) {
            r.timer(t)
            assertFalse(r.armed)
            val before = r.c.platform.count
            r.c.advance(3_600_000)
            assertFalse("$t: the tab arms nothing idle", r.armed)
            // (GAME / TURN: the diamond's hourglass pulses once a minute — chrome/HourglassPulseTest.)
            if (t == TurnTimer.OFF) assertEquals("an hour idle with Turns ON: not one redraw", before, r.c.platform.count)
        }
        r.timer(TurnTimer.OFF)
        r.tapTab()
        assertEquals("", r.report)
        assertFalse("OFF: a pass reports nothing", r.armed)
    }

    @Test fun turnModeReportsTheTurnThatEndedForFiveSecondsRedrawingOnlyTheTab() {
        val r = Rig()
        r.c.stage.components.filterIsInstance<au.com.josephmark.dithertracker.fx.FxComponent>().single().player.enabled = false // T55's effect aside
        r.timer(TurnTimer.TURN)
        r.start(0)
        r.c.advance(127_400)
        val rects = r.invalidations { r.tapTab() }
        assertEquals("2:07", r.report)
        assertEquals(1, r.active())
        for (rect in rects) assertTrue("$rect", rect == r.l.tab[0] || rect == r.l.tab[1])
        assertTrue(r.armed)
        assertTrue(r.c.check("turn-report").isEmpty())
        r.png("4p-turn-report")
        // Nothing for 4.9 s; then one redraw of the new tab only, and the icon is back.
        val n0 = r.c.platform.count
        r.c.advance(4_900)
        assertEquals(n0, r.c.platform.count)
        val clear = r.invalidations { r.c.advance(100) }
        assertEquals(listOf(r.l.tab[1]), clear)
        assertEquals("", r.report)
        assertFalse(r.armed)
        // Hours: h:mm:ss.
        r.c.advance(3_600_000L + 47 * 60_000L + 12_000)
        r.tapTab()
        assertEquals("1:47:17", r.report) // the turn began 5 s before (the cleared report's)
    }

    @Test fun gameModeReportsTheGameSoFarAndAPassDuringTheReportPassesAgain() {
        val r = Rig()
        r.timer(TurnTimer.GAME)
        r.start(0)
        r.c.advance(61_000)
        r.tapTab()
        assertEquals("1:01", r.report)
        r.c.advance(2_000)
        r.tapTab() // during the report: a pass, with the next report and a fresh 5 s
        assertEquals(2, r.active())
        assertEquals("1:03", r.report)
        r.c.advance(4_000)
        assertEquals("1:03", r.report)
        r.c.advance(1_000)
        assertEquals("", r.report)
    }

    @Test fun correctionKeepsTheReportUndoDropsItRedoShowsItsOwn() {
        val r = Rig()
        r.timer(TurnTimer.TURN)
        r.start(0)
        r.c.advance(30_000)
        r.tapTab()
        assertEquals("0:30", r.report)
        r.c.dispatch(Action.SetActivePlayer(r.c.id(3)))
        assertEquals("a correction is not a pass", "0:30", r.report)
        assertEquals(30_000L, r.c.session.state.turn.lastTurnMillis)
        r.c.session.undo() // the correction
        r.c.session.undo() // the pass
        assertEquals("", r.report)
        assertFalse(r.armed)
        assertEquals(0, r.active())
        r.c.session.redo() // the pass again: its own report
        assertEquals("0:30", r.report)
    }

    @Test fun pauseDropsTheReportAndPausedTimeNeverCounts() {
        val r = Rig()
        r.timer(TurnTimer.TURN)
        r.start(1)
        r.c.advance(20_000)
        r.c.session.pause(r.c.now)
        r.stage.send(Events.PAUSE, Stage.SCREEN)
        r.c.advance(8 * 3_600_000L) // the night
        r.c.session.resume(r.c.now)
        r.stage.send(Events.RESUME, Stage.SCREEN)
        r.c.advance(10_000)
        r.tapTab()
        assertEquals("only the active 30 s count", "0:30", r.report)
        r.c.session.pause(r.c.now)
        r.stage.send(Events.PAUSE, Stage.SCREEN)
        assertEquals("", r.report)
        assertFalse(r.armed)
        r.c.session.resume(r.c.now)
        r.stage.send(Events.RESUME, Stage.SCREEN)
        assertEquals("not re-shown on resume", "", r.report)
    }

    @Test fun settingsChangeOrAnOverlayDropTheReport() {
        val r = Rig()
        r.timer(TurnTimer.GAME)
        r.start(0)
        r.c.advance(5_000)
        r.tapTab()
        assertEquals("0:05", r.report)
        r.history.open(MenuItem.LOG, 0)
        assertEquals("", r.report)
        r.history.close()
        r.tapTab()
        assertTrue(r.report.isNotEmpty())
        r.timer(TurnTimer.TURN)
        assertEquals("", r.report)
        assertFalse(r.armed)
    }

    @Test fun theClockSurvivesProcessDeath() {
        val r = Rig()
        r.timer(TurnTimer.TURN)
        r.start(2)
        r.c.advance(7 * 60_000L)
        r.tapTab()
        val clock = r.c.session.activeAt(r.c.now)
        val bytes = SaveCodec.encode(SaveFile(r.c.session.game, r.stage.settings.current, clock))
        // A new process restores the save; its active clock carries on from the saved one.
        val r2 = Rig()
        val save = SaveCodec.decodeOrNull(bytes)!!
        r2.stage.settings.update(save.settings)
        r2.c.session.replace(History(Game(save.game.state, save.game.log)))
        r2.c.session.restoreClock(save.activeClock)
        r2.c.session.resume(r2.c.now)
        assertEquals(3, r2.turn.shownSeat)
        r2.c.advance(95_000)
        r2.tapTab()
        assertEquals("1:35", r2.turn.report.toString())
        assertEquals(7 * 60_000L, r2.c.session.state.turn.spent[r2.c.id(2)])
    }

    @Test fun sixPlayersNarrowAndTheSidewaysSeatAtThree() {
        val six = Rig(players = 6)
        six.timer(TurnTimer.TURN)
        for (seat in 0 until 6) {
            six.c.dispatch(Action.SetActivePlayer(six.c.id(seat)))
            assertEquals(seat, six.turn.shownSeat)
            assertTrue(six.c.check("6p-seat$seat").isEmpty())
        }
        six.c.dispatch(Action.SetActivePlayer(six.c.id(0)))
        six.c.advance(12 * 60_000L + 34_000)
        six.tapTab() // to seat 1, a middle seat: the 45 px tab, the report in the ui face
        assertEquals(1, six.turn.shownSeat)
        assertEquals("12:34", six.report)
        assertTrue(six.c.check("6p-narrow-report").isEmpty())
        six.png("6p-narrow-middle-seat")
        val three = Rig(players = 3)
        three.start(1) // the end seat, turned 270°
        assertTrue(three.c.check("3p-end").isEmpty())
        three.png("3p-end-seat")
    }

    @Test fun drawerOpenHidesTheTabUntilItCloses() {
        val r = Rig()
        r.start(3)
        r.c.tap(r.c.tab, 3, 0)
        assertTrue(r.c.drawer.state.isOpen(3))
        // Why: the open drawer's compact readout + number stack climbs into the far strip.
        val tiles = r.stage.tiles!!
        assertTrue(overlaps(r.l.hit[3], tiles.readoutSlot(3, true)) || overlaps(r.l.hit[3], tiles.numberSlot(3, true)))
        assertNull(r.c.pointOf(r.turn, 3, TurnTab.PASS))
        assertTrue(r.c.check("drawer").isEmpty())
        r.c.drawer.controller.closeAll()
        assertNotNull(r.c.pointOf(r.turn, 3, TurnTab.PASS))
    }
}
