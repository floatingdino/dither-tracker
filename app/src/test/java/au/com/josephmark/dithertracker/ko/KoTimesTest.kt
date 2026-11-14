package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.layout.Arrangements
import au.com.josephmark.dithertracker.layout.CountRig
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.purity.PurityChecks
import au.com.josephmark.dithertracker.turns.TimeFormat
import au.com.josephmark.dithertracker.turns.TurnTab
import au.com.josephmark.dithertracker.widgets.CharBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** T66: the winner screen reports each seat's time on turn (TURN) or the game's (GAME). */
class KoTimesTest {

    private fun overlaps(a: IRect, b: IRect) = !a.isEmpty && !b.isEmpty && a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
    private fun inside(a: IRect, b: IRect) = a.left >= b.left && a.top >= b.top && a.right <= b.right && a.bottom <= b.bottom

    @Test fun reportChipFitsEveryTileClearOfTheHeroAndTheCauseChip() {
        val quip = "THOU HAST BEEN SLAIN BY A LONG QUIP"
        val icon = IRect(); val text = IRect(); val chip = IRect()
        val ci = IRect(); val cn = IRect(); val cc = IRect()
        var checked = 0
        for (a in Arrangements.ALL) for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (s in 2..6) {
            val play = PlayLayout.compute(DitherGrid(w, h, s), a.players, a.id)
            if (!play.comfortable) continue
            val k = KoLayout(play)
            for (seat in 0 until a.players) for (mode in intArrayOf(KoLayout.DEAD, KoLayout.DEAD_CAUSE, KoLayout.WINNER)) {
                val tag = "${a.id} ${w}x$h S=$s seat $seat mode $mode"
                val b = KoLayout.Block()
                k.arrange(seat, quip, b, mode, reserve = true)
                for (chars in 4..TimeFormat.MAX_CHARS) {
                    val f = k.report(seat, mode, chars, icon, text, chip)
                    assertNotNull("$tag: no room for $chars: $k", f)
                    assertTrue("$tag chip $chip in ${play.panels[seat]}", inside(chip, play.panels[seat]))
                    assertTrue("$tag text", inside(text, chip))
                    if (!icon.isEmpty) { assertTrue("$tag icon", inside(icon, chip)); assertFalse("$tag icon/text", overlaps(icon, text)) }
                    assertFalse("$tag: chip over the quip", overlaps(chip, b.bounds))
                    assertFalse("$tag: chip over the emblem", overlaps(chip, b.emblem))
                    assertFalse("$tag: chip over the diamond", overlaps(chip, play.diamond))
                    if (mode == KoLayout.DEAD_CAUSE && k.detail(seat, 21, ci, cn, cc)) assertFalse("$tag: chip over the cause chip", overlaps(chip, cc))
                    if (mode == KoLayout.WINNER) assertFalse("$tag: chip over the drawer tab", overlaps(chip, play.tabHit[seat]))
                }
                checked++
            }
        }
        assertTrue("checked $checked", checked > 100)
        // Nomad 4p: `1:47:12` with its clock icon fits the winner's detail band in num-m.
        val k4 = KoLayout(PlayLayout.compute(DitherGrid(1872, 1404, 4), 4))
        assertEquals(k4.causeFont, k4.report(3, KoLayout.WINNER, 7, icon, text, chip))
        assertFalse(icon.isEmpty)
    }

    private class Game(players: Int, timer: TurnTimer, layoutId: String = "") {
        val c = CountRig(1872, 1404, 4, players)
        val ko = c.one<KoComponent>()
        val win = c.one<KoWinner>()
        val turn = c.one<TurnTab>()

        init {
            c.one<au.com.josephmark.dithertracker.fx.FxComponent>().player.enabled = false
            if (layoutId.isNotEmpty()) {
                c.stage.settings.update(c.stage.settings.current.copy(layout = layoutId))
                c.stage.layout(PlayLayout.compute(c.grid, players, layoutId))
                c.prewarm()
            }
            c.stage.settings.update(c.stage.settings.current.copy(turnTimer = timer))
        }

        fun pass(afterMs: Long) { c.advance(afterMs); c.tap(turn, turn.shownSeat, TurnTab.PASS) }
        fun out(seat: Int) { c.dispatch(Action.SetLife(c.id(seat), 0)); c.advance(6_000) }
        fun text(seat: Int): String = KoTimes.format(c.session.state, c.stage.settings.current, seat, CharBuf(8)).toString()
    }

    @Test fun turnModeEachSeatShowsItsOwnTurnsOnlyOnceThereIsAWinner() {
        val g = Game(4, TurnTimer.TURN)
        g.c.dispatch(Action.SetStartingPlayer(g.c.id(0)))
        g.pass(60_000)   // 0: 1:00
        g.pass(125_000)  // 1: 2:05
        g.pass(30_000)   // 2: 0:30
        g.pass(5_000)    // 3: 0:05
        g.pass(10_000)   // 0: +0:10
        g.out(2)
        assertFalse("mid-game: no times on a KO tile", g.ko.times)
        g.out(3)
        g.c.advance(20_000)
        g.out(1) // seat 1 is out; seat 0 (on turn since the last pass) wins
        assertTrue(g.ko.times); assertTrue(g.win.times)
        assertEquals(0, g.win.winner)
        val st = g.c.session.state.turn
        assertEquals("1:10", g.text(0))
        assertEquals("seat 1: 2:05 + its turn in progress to the end", TimeFormat.format(125_000 + (st.endedAt - st.turnStartedAt), CharBuf(8)).toString(), g.text(1))
        assertEquals("0:30", g.text(2))
        assertEquals("0:05", g.text(3))
        assertTrue(g.c.check("winner-turn").isEmpty())
        g.c.render(false).writePng(File(PurityChecks.outDir, "t66/4p-winner-turn.png"), 1)
        // Static: nothing changes or redraws with time.
        val n = g.c.platform.count
        g.c.advance(3_600_000)
        assertEquals(n, g.c.platform.count)
        // A revive ends the win: the times go.
        g.c.dispatch(Action.Revive(g.c.id(1)))
        assertFalse(g.ko.times); assertFalse(g.win.times)
    }

    @Test fun gameModeShowsTheGameTotalOnEverySeatAndOffShowsNothing() {
        val g = Game(4, TurnTimer.GAME, "4-wide-far")
        g.c.dispatch(Action.SetStartingPlayer(g.c.id(1)))
        g.pass(90_000); g.pass(90_000)
        g.out(0); g.out(1)
        g.c.dispatch(Action.SetCounter(g.c.id(2), au.com.josephmark.dithertracker.core.Counter.POISON, 10)) // a cause chip too
        g.c.advance(6_000)
        assertTrue(g.ko.times)
        val all = (0 until 4).map { g.text(it) }.toSet()
        assertEquals(1, all.size)
        assertTrue(g.c.check("winner-game-mixed").isEmpty())
        g.c.render(false).writePng(File(PurityChecks.outDir, "t66/4p-wide-far-winner-game.png"), 1)
        g.c.stage.settings.update(g.c.stage.settings.current.copy(turnTimer = TurnTimer.OFF))
        assertFalse(g.ko.times); assertFalse(g.win.times)
        assertTrue(g.c.check("winner-off").isEmpty())
    }

    @Test fun everyCountReportsCleanlyInBothModes() {
        for (n in 2..6) for (mode in listOf(TurnTimer.TURN, TurnTimer.GAME)) {
            val g = Game(n, mode)
            g.c.dispatch(Action.SetStartingPlayer(g.c.id(0)))
            for (i in 0 until n) g.pass(40_000L + 7_000L * i)
            for (s in 1 until n) g.out(s)
            assertTrue("$n $mode", g.ko.times)
            assertTrue("$n $mode", g.c.check("winner-$n-$mode").isEmpty())
            if (n == 6) g.c.render(false).writePng(File(PurityChecks.outDir, "t66/6p-winner-$mode.png"), 1)
        }
    }
}
