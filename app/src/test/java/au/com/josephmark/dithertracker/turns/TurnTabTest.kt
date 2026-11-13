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

/** T17: the turn tab (whose turn, pass, correct) and its minutes timer on the real Play screen. */
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
        val tickId: Int get() = stage.zoneCount + timerBase()
        val armed: Boolean get() = c.platform.clock.isArmed(tickId)
        val history get() = c.one<HistoryOverlay>()
        private fun timerBase(): Int {
            // TurnTab's block: count the timers of the components before it.
            var t = 0
            for (comp in stage.components) { if (comp === turn) break; t += comp.timerCount }
            return t + TurnTab.TICK
        }

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

    @Test fun tabSitsInTheFarStripClearOfEverythingAtEveryCountScaleAndOrientation() {
        for (n in 2..6) for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (s in 2..6) {
            val play = PlayLayout.compute(au.com.josephmark.dithertracker.dither.DitherGrid(w, h, s), n)
            if (!play.comfortable) continue
            val t = TurnLayout(play)
            val tag = "${n}p ${w}x$h S=$s"
            assertTrue("$tag: $t", t.enabled)
            val icon = IRect(); val num = IRect()
            for (seat in 0 until n) {
                val tab = t.tab[seat]
                assertTrue("$tag seat $seat tab $tab in ${play.panels[seat]}", inside(tab, play.panels[seat]))
                assertTrue("$tag seat $seat hit in panel", inside(t.hit[seat], play.panels[seat]))
                assertTrue("$tag seat $seat tab in hit", inside(tab, t.hit[seat]))
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
                // Content inside the tab, with and without the minutes (3 digits worst case).
                for (d in 0..3) {
                    val bits = t.content(seat, d, icon, num)
                    if (bits and TurnLayout.ICON != 0) assertTrue("$tag seat $seat icon", inside(icon, tab))
                    if (bits and TurnLayout.NUM != 0) assertTrue("$tag seat $seat digits $d", inside(num, tab))
                    if (d > 0) assertTrue("$tag seat $seat: the minutes fit", bits and TurnLayout.NUM != 0)
                    if (bits == (TurnLayout.ICON or TurnLayout.NUM)) assertFalse(overlaps(icon, num))
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
        // 6 players narrow: the middle seats' tabs stand beside the diamond, 45 wide, minutes alone.
        val t6 = TurnLayout(PlayLayout.compute(au.com.josephmark.dithertracker.dither.DitherGrid(1872, 1404, 4), 6))
        assertEquals(45, t6.tabW[1]); assertEquals(45, t6.tabW[4])
        assertFalse(t6.iconWithTimer(1)); assertTrue(t6.timerFits(1))
        assertEquals(89, t6.tabW[0]); assertTrue(t6.iconWithTimer(0))
    }

    // ---- behaviour ---------------------------------------------------------------------------------

    @Test fun nothingShowsUntilAFirstPlayerIsPickedThenTheirTabDoes() {
        val r = Rig()
        assertEquals(-1, r.turn.shownSeat)
        assertNull(r.c.pointOf(r.turn, 0, TurnTab.PASS))
        // The menu's FIRST tool picks someone and starts tracking from them.
        r.c.one<FirstPlayer>().pick()
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

    // ---- timer -------------------------------------------------------------------------------------

    @Test fun timerIsOffByDefaultAndNeverArmed() {
        val r = Rig()
        r.start(0)
        assertEquals(-1, r.turn.minutes)
        assertFalse(r.armed)
        val before = r.c.platform.count
        r.c.advance(3_600_000)
        assertEquals("an hour idle: not one redraw", before, r.c.platform.count)
    }

    @Test fun gameClockTicksOncePerMinuteRedrawingOnlyTheDigits() {
        val r = Rig()
        r.start(0)
        r.timer(TurnTimer.GAME)
        assertEquals(0, r.turn.minutes)
        assertTrue(r.armed)
        // 59 s: nothing.
        val n0 = r.c.platform.count
        r.c.advance(59_000)
        assertEquals(n0, r.c.platform.count)
        // The minute: one redraw of the digits only (`num-m` at k = 1: 10 × 14).
        val rects = r.invalidations { r.c.advance(1_000) }
        assertEquals(1, r.turn.minutes)
        assertEquals(1, rects.size)
        assertTrue(inside(rects[0], r.l.tab[0]))
        assertEquals(10, rects[0].width); assertEquals(14, rects[0].height)
        // 9 → 10 changes the digit count: the tab redraws (still only the tab).
        val r10 = r.invalidations { r.c.advance(9 * 60_000L) }
        assertEquals(10, r.turn.minutes)
        assertEquals(r.l.tab[0], r10.last())
        // Exactly one tick per minute: 50 more minutes, 50 redraws.
        val n1 = r.c.platform.count
        r.c.advance(50 * 60_000L)
        assertEquals(60, r.turn.minutes)
        assertEquals(50, r.c.platform.count - n1)
        // The game clock runs on across passes; a life tap does not re-arm it.
        r.tapTab()
        assertEquals(60, r.turn.minutes)
        val due = r.c.platform.clock.dueAt(r.tickId)
        r.c.tap(r.c.life, 2, PlayLayout.MINUS)
        r.c.advance(5_000)
        assertEquals("the tick is not re-armed by play", due, r.c.platform.clock.dueAt(r.tickId))
        assertTrue(r.c.check("game-clock").isEmpty())
        r.png("4p-game-clock")
    }

    @Test fun turnTimerRestartsAtEachPassButNotAtACorrection() {
        val r = Rig()
        r.start(0)
        r.timer(TurnTimer.TURN)
        r.c.advance(5 * 60_000L + 10_000)
        assertEquals(5, r.turn.minutes)
        r.tapTab()
        assertEquals(0, r.turn.minutes)
        r.c.advance(3 * 60_000L)
        assertEquals(3, r.turn.minutes)
        r.c.dispatch(Action.SetActivePlayer(r.c.id(3)))
        assertEquals("a correction keeps the turn's clock", 3, r.turn.minutes)
        r.c.session.undo() // the correction
        r.c.session.undo() // the pass: back to seat 0's turn, its clock from the start
        assertEquals(8, r.turn.minutes)
    }

    @Test fun pauseDisarmsAndResumeResyncsFromTheWallClock() {
        val r = Rig()
        r.start(1)
        r.timer(TurnTimer.GAME)
        r.c.advance(2 * 60_000L)
        r.stage.send(Events.PAUSE, Stage.SCREEN)
        assertFalse(r.armed)
        val before = r.c.platform.count
        r.c.advance(45 * 60_000L + 30_000)
        assertEquals("nothing ticks or draws in the background", before, r.c.platform.count)
        val rects = r.invalidations { r.stage.send(Events.RESUME, Stage.SCREEN) }
        assertEquals(47, r.turn.minutes)
        assertEquals(1, rects.size)
        assertTrue(r.armed)
        // Next tick at the next minute boundary of the clock (2:47:30 → 48:00), not a minute from resume.
        r.c.advance(29_000)
        assertEquals(47, r.turn.minutes)
        r.c.advance(1_000)
        assertEquals(48, r.turn.minutes)
    }

    @Test fun timerDisarmsWhenOffHiddenOrTheTrackerIsOff() {
        val r = Rig()
        r.timer(TurnTimer.GAME)
        assertFalse("tracking not started", r.armed)
        r.start(0)
        assertTrue(r.armed)
        r.timer(TurnTimer.OFF)
        assertFalse(r.armed)
        r.timer(TurnTimer.TURN)
        assertTrue(r.armed)
        r.tracker(false)
        assertFalse(r.armed)
        r.tracker(true)
        assertTrue(r.armed)
        r.history.open(MenuItem.LOG, 0) // a full overlay covers the tab
        assertFalse(r.armed)
        r.history.close()
        assertTrue(r.armed)
        r.stage.detach()
        assertFalse(r.turn.ticking)
    }

    @Test fun theClockSurvivesProcessDeath() {
        val r = Rig()
        r.start(2)
        r.timer(TurnTimer.GAME)
        r.c.advance(7 * 60_000L)
        r.tapTab()
        val bytes = SaveCodec.encode(SaveFile(r.c.session.game, r.stage.settings.current))
        // A new process 20 minutes later restores the save.
        val r2 = Rig()
        r2.c.advance(27 * 60_000L)
        val save = SaveCodec.decodeOrNull(bytes)!!
        r2.stage.settings.update(save.settings)
        r2.c.session.replace(History(Game(save.game.state, save.game.log)))
        assertEquals(3, r2.turn.shownSeat)
        assertEquals(27, r2.turn.minutes)
        assertTrue(r2.armed)
    }

    @Test fun sixPlayersNarrowAndTheSidewaysSeatAtThree() {
        val six = Rig(players = 6)
        six.timer(TurnTimer.TURN)
        for (seat in 0 until 6) {
            six.c.dispatch(Action.SetActivePlayer(six.c.id(seat)))
            assertEquals(seat, six.turn.shownSeat)
            assertTrue(six.c.check("6p-seat$seat").isEmpty())
        }
        six.c.dispatch(Action.SetActivePlayer(six.c.id(1)))
        six.c.advance(12 * 60_000L)
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
        assertNull(r.c.pointOf(r.turn, 3, TurnTab.PASS))
        assertTrue(r.c.check("drawer").isEmpty())
        r.c.drawer.controller.closeAll()
        assertNotNull(r.c.pointOf(r.turn, 3, TurnTab.PASS))
    }
}
