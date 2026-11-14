package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.layout.Arrangements
import au.com.josephmark.dithertracker.layout.CountRig
import au.com.josephmark.dithertracker.menu.GemLayout
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.menu.MenuOverlay
import au.com.josephmark.dithertracker.play.FakeScheduler
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayRig
import au.com.josephmark.dithertracker.play.Scheduler
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Stage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** T59: the first-player d20 roll-off (state machine, the screen, geometry at every count and S). */
class FirstPlayerTest {

    @After fun reset() {
        DisplaySpeed.override = DisplaySpeed.NO_OVERRIDE
    }

    /** A Random whose d20 rolls are scripted (then 1s). */
    private class Rolls(vararg v: Int) : Random() {
        val q = ArrayDeque(v.toList())
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(from: Int, until: Int): Int = if (until - from == 20) q.removeFirstOrNull() ?: 1 else from
    }

    private class StateRig(random: Random, var speed: Int = DisplaySpeed.SLOW) {
        val timers = FakeScheduler(1)
        val changes = ArrayList<Boolean>()
        val roll = FirstRoll(timers, random, { changes += it }, { speed })
        init { timers.target = Scheduler.Target { roll.onTimer(it) } }
    }

    private fun playOut(r: StateRig, poses: Int, poseMs: LongArray) {
        assertEquals(poses, poseMs.size)
        for (b in 0 until poses) {
            assertEquals(FirstRoll.ROLLING, r.roll.phase)
            assertEquals(b, r.roll.beat)
            r.timers.advance(poseMs[b] - 1)
            assertEquals("pose $b holds ${poseMs[b]} ms", b, r.roll.beat)
            r.timers.advance(1)
        }
    }

    @Test fun everyLiveSeatRollsInHeldPosesThenTheHighestWins() {
        val r = StateRig(Rolls(7, 15, 3, 12))
        assertTrue(r.roll.start(0b1111))
        assertEquals(0b1111, r.roll.rolling)
        assertEquals(FirstRoll.SLOW_POSES, r.roll.beats)
        assertEquals("fixed at the start", listOf(7, 15, 3, 12), r.roll.value.take(4))
        playOut(r, FirstRoll.SLOW_POSES, FirstRoll.SLOW_POSE_MS)
        // T65: the numbers land and sit alone, then the winner's flag, then back to the game.
        assertEquals(FirstRoll.LANDED, r.roll.phase)
        assertEquals(1, r.roll.winner)
        assertTrue(r.timers.isArmed(FirstRoll.TIMER_STEP))
        r.timers.advance(FirstRoll.LANDED_MS - 1)
        assertEquals(FirstRoll.LANDED, r.roll.phase)
        r.timers.advance(1)
        assertEquals(FirstRoll.WINNER, r.roll.phase)
        r.timers.advance(FirstRoll.WINNER_MS - 1)
        assertEquals(FirstRoll.WINNER, r.roll.phase)
        r.timers.advance(1)
        assertEquals(FirstRoll.DONE, r.roll.phase)
        assertEquals(listOf(true), r.changes.filter { it })
        assertFalse(r.timers.isArmed(FirstRoll.TIMER_STEP))
    }

    @Test fun aFastDisplayGetsMorePosesInTheSameTime() {
        val r = StateRig(Rolls(2, 9), DisplaySpeed.FAST)
        r.roll.start(0b11)
        assertEquals(FirstRoll.FAST_POSES, r.roll.beats)
        playOut(r, FirstRoll.FAST_POSES, FirstRoll.FAST_POSE_MS)
        assertEquals(FirstRoll.LANDED, r.roll.phase)
        assertTrue(FirstRoll.FAST_POSE_MS.sum() <= FirstRoll.SLOW_POSE_MS.sum())
        // T65: uneven holds — the first and last poses longer than the quick middle.
        for (p in listOf(FirstRoll.FAST_POSE_MS, FirstRoll.SLOW_POSE_MS)) {
            assertTrue(p.first() > p[1] && p.last() > p[p.size - 2])
        }
        for (ms in FirstRoll.FAST_POSE_MS) assertTrue(ms >= 120)
        for (ms in FirstRoll.SLOW_POSE_MS) assertTrue(ms >= 250)
        assertTrue("the numbers sit longer than any pose", FirstRoll.LANDED_MS > FirstRoll.SLOW_POSE_MS.max())
    }

    @Test fun aTieAtTheTopRerollsOnlyTheTiedSeats() {
        // Seats 0 and 2 tie on 18; seat 1 has 4, seat 3 has 18? no: 11. Round two: 6 vs 13.
        val r = StateRig(Rolls(18, 4, 18, 11, 6, 13))
        r.roll.start(0b1111)
        playOut(r, FirstRoll.SLOW_POSES, FirstRoll.SLOW_POSE_MS)
        assertEquals("the numbers land first, alone", FirstRoll.LANDED, r.roll.phase)
        r.timers.advance(FirstRoll.LANDED_MS)
        assertEquals(FirstRoll.TIE, r.roll.phase)
        assertEquals(0b0101, r.roll.tied)
        r.timers.advance(FirstRoll.TIE_MS)
        assertEquals(FirstRoll.ROLLING, r.roll.phase)
        assertEquals("only the tied roll", 0b0101, r.roll.rolling)
        assertEquals(2, r.roll.round)
        assertEquals("the others keep their roll", 4, r.roll.value[1])
        playOut(r, FirstRoll.SLOW_POSES, FirstRoll.SLOW_POSE_MS)
        assertEquals(FirstRoll.LANDED, r.roll.phase)
        assertEquals(2, r.roll.winner)
        r.timers.advance(FirstRoll.LANDED_MS)
        assertEquals(FirstRoll.WINNER, r.roll.phase)
        assertFalse(r.roll.drawn)
    }

    @Test fun endlessTiesStopAtTheCapWithARandomPick() {
        val r = StateRig(Rolls(*IntArray(40) { 20 }))
        r.roll.start(0b111)
        var guard = 0
        while (r.roll.phase != FirstRoll.WINNER && guard++ < 200) r.timers.advance(50)
        assertEquals(FirstRoll.WINNER, r.roll.phase)
        assertEquals(FirstRoll.MAX_ROUNDS, r.roll.round)
        assertTrue(r.roll.drawn)
        assertTrue(r.roll.winner in 0..2)
    }

    @Test fun skipLandsEverythingThenASecondSkipFinishes() {
        val r = StateRig(Rolls(5, 5, 9, 1, 2))
        r.roll.start(0b111)
        r.timers.advance(100)
        r.roll.skip()
        assertEquals(FirstRoll.WINNER, r.roll.phase)
        assertTrue(r.roll.winner >= 0)
        r.roll.skip()
        assertEquals(FirstRoll.DONE, r.roll.phase)
        assertFalse(r.timers.isArmed(FirstRoll.TIMER_STEP))
    }

    // ---- the real screen ----------------------------------------------------------------------------

    private class Rig(s: Int = 4) {
        val rig = PlayRig(s = s)
        val menu = rig.stage.components.filterIsInstance<MenuOverlay>().single()
        val first = rig.stage.components.filterIsInstance<FirstPlayer>().single()
        val l get() = first.layout!!

        fun tap(x: Int, y: Int) {
            rig.stage.down(0, x, y, rig.platform.clock.now)
            rig.stage.up(0)
        }

        fun pickFromMenu() {
            tap(rig.l.diamondCx, rig.l.diamondCy)
            val g = menu.gem!!
            val s = GemLayout.slotOf(MenuItem.FIRST)
            tap(g.facetX[s], g.facetY[s])
        }

        /** Lets the roll-off play out (not a moment longer: the flag then clears by itself). */
        fun finish() {
            var i = 0
            while (first.roll.active && i++ < 200) rig.platform.clock.advance(50)
        }
        fun device(r: IRect) = IRect().also { rig.l.grid.toDevice(r, it) }
        val turn get() = rig.session.state.turn
    }

    private fun IRect.meets(o: IRect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    private fun IRect.inside(o: IRect) = left >= o.left && top >= o.top && right <= o.right && bottom <= o.bottom

    @Test fun firstFacetRollsOffOnTheTilesThenStartsTurnsFromTheWinner() {
        DisplaySpeed.override = DisplaySpeed.SLOW
        val r = Rig()
        val before = r.turn
        r.pickFromMenu()
        assertTrue(r.first.roll.active)
        assertEquals(0b1111, r.first.roll.seats)
        assertEquals("nothing in the game yet", before, r.turn)
        // The tiles are taken over: their life zones are gone, a tap only skips.
        val z = PlayRig.mid(r.rig.l.zones[PlayLayout.zone(1, PlayLayout.PLUS)])
        assertEquals(40, r.rig.session.state.players[1].life)
        // Each keyframe redraws the rolling tiles.
        r.rig.platform.invalidated.clear()
        r.rig.platform.clock.advance(FirstRoll.SLOW_POSE_MS[0])
        val p0 = r.device(r.rig.l.panels[0])
        assertTrue(r.rig.platform.invalidated.any { it.left <= p0.left && it.top <= p0.top && it.right >= p0.right && it.bottom >= p0.bottom })
        r.finish()
        assertFalse(r.first.roll.active)
        val w = r.first.seat
        assertTrue(w in 0..3)
        val vals = r.first.roll.value
        for (s in 0..3) if (s != w) assertTrue(vals[w] >= vals[s])
        assertEquals(r.rig.id(w), r.turn.startingPlayer)
        assertTrue(r.first.caption in Flavour.quippy.lines(QuipKind.STARTING_PLAYER).map { it.uppercase() })
        assertTrue(r.rig.session.history.canUndo)
        // The tiles are back: a tap on a life half changes life and takes the flag down.
        r.tap(z[0], z[1])
        assertEquals(41, r.rig.session.state.players[1].life)
        assertEquals(-1, r.first.seat)
    }

    @Test fun aTapLandsTheRollsAndChangesNoLife() {
        val r = Rig()
        r.pickFromMenu()
        val z = PlayRig.mid(r.rig.l.zones[PlayLayout.zone(2, PlayLayout.PLUS)])
        r.tap(z[0], z[1])
        assertEquals(FirstRoll.WINNER, r.first.roll.phase)
        assertEquals(40, r.rig.session.state.players[2].life)
        r.tap(z[0], z[1])
        assertFalse(r.first.roll.active)
        assertEquals(40, r.rig.session.state.players[2].life)
        assertEquals(r.rig.id(r.first.seat), r.turn.startingPlayer)
    }

    @Test fun knockedOutSeatsSitItOutAndPauseLandsWithNothingArmed() {
        val r = Rig()
        r.rig.session.dispatch(Action.MarkDead(r.rig.id(2)), 0)
        r.first.pick()
        assertEquals(0b1011, r.first.roll.seats)
        r.rig.platform.clock.advance(100)
        r.rig.stage.send(Events.PAUSE, Stage.SCREEN)
        assertFalse(r.first.roll.active)
        assertTrue(r.first.seat >= 0 && r.first.seat != 2)
        assertEquals(r.rig.id(r.first.seat), r.turn.startingPlayer)
        assertFalse("nothing armed after pause", r.rig.stage.timersPending())
        val seat = r.first.seat
        r.rig.platform.clock.advance(60_000)
        assertEquals("the flag waits for the player to come back", seat, r.first.seat)
        r.rig.stage.send(Events.RESUME, Stage.SCREEN)
        r.rig.platform.clock.advance(FirstPlayer.SHOW_MS)
        assertEquals(-1, r.first.seat)
    }

    @Test fun resultsHookHearsTheWinnerWhenItLands() {
        val r = Rig()
        val heard = ArrayList<Int>()
        r.first.results = object : ToolResults {
            override fun onStartingPlayer(id: Int) { heard += id }
        }
        r.first.pick()
        assertTrue(heard.isEmpty())
        r.finish()
        assertEquals(listOf(r.rig.id(r.first.seat)), heard)
    }

    @Test fun quipsAreShortAndNeverRepeatTwiceRunning() {
        val r = Rig()
        var last = ""
        repeat(20) {
            r.first.pick()
            r.finish()
            assertFalse(r.first.caption == last)
            last = r.first.caption
        }
        for (q in Flavour.quippy.lines(QuipKind.STARTING_PLAYER)) assertTrue(q, q.length <= 26)
        assertTrue(Flavour.quippy.lines(QuipKind.STARTING_PLAYER).any { it.uppercase() == FirstPlayer.FALLBACK })
    }

    /** Take-over geometry and the flag at every count, every arrangement and S = 2..6, both orientations. */
    @Test fun tileRollGeometryAtEveryArrangementAndScale() {
        for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (a in Arrangements.ALL) for (s in 2..6) {
            val p = PlayLayout.compute(DitherGrid(w, h, s), a.players, a.id)
            if (!p.comfortable) continue
            val t = TileRollLayout(p)
            val f = FirstPlayerLayout(p)
            val tag = "${w}x$h ${a.id} S=$s"
            for (seat in 0 until p.playerCount) {
                val panel = p.panels[seat]
                assertTrue("$tag seat $seat frame", t.frameOuter[seat].inside(panel) && t.frameInner[seat].inside(t.frameOuter[seat]))
                assertTrue("$tag seat $seat label ${t.label[seat]}", t.label[seat].inside(t.frameInner[seat]))
                for (fast in listOf(false, true)) for (b in 0 until if (fast) FirstRoll.FAST_POSES else FirstRoll.SLOW_POSES) {
                    assertTrue("$tag seat $seat die $b", t.dieRect(seat, fast, b).inside(t.frameInner[seat]))
                    assertTrue("$tag seat $seat extra $b", t.extraRect(seat, fast, b).inside(t.frameInner[seat]))
                    assertFalse("$tag seat $seat die clear of the label", t.dieRect(seat, fast, b).meets(t.label[seat]))
                }
                val fp = IRect()
                for (v in intArrayOf(1, 20)) {
                    p.numberFootprint(seat, v, fp)
                    assertTrue("$tag seat $seat roll $v", fp.inside(t.frameInner[seat]))
                    assertFalse(fp.meets(t.label[seat]))
                }
                if (f.enabled) {
                    val band = f.bandRect(seat, IRect())
                    assertTrue("$tag seat $seat flag", band.inside(t.frameInner[seat]))
                    for (q in Flavour.quippy.lines(QuipKind.STARTING_PLAYER)) {
                        f.arrange(seat, q.uppercase())
                        // Lines up to 20 characters fit everywhere; a longer one may not fit a narrow tile,
                        // where the component falls back to FirstPlayer.FALLBACK (checked below).
                        if (s <= 4 && q.length <= 20) assertFalse("$tag '$q' truncated", f.truncated)
                        for (i in 0 until f.lines) assertTrue("$tag '$q'", f.line[i].inside(band))
                    }
                    f.arrange(seat, FirstPlayer.FALLBACK)
                    if (s <= 4) assertFalse("$tag fallback truncated", f.truncated)
                    val k = f.shortFootprint(seat, FirstPlayer.TIE.length, fp)
                    if (k > 0) assertTrue(fp.inside(band))
                }
            }
        }
    }

    @Test fun everyCountRollsOffThroughTheRealScreen() {
        for (n in 2..6) for (speed in intArrayOf(DisplaySpeed.SLOW, DisplaySpeed.FAST)) {
            DisplaySpeed.override = speed
            val c = CountRig(1872, 1404, 4, n)
            val first = c.one<FirstPlayer>()
            first.pick()
            val errs = ArrayList<String>()
            var i = 0
            while (first.roll.active && i < 120) {
                errs += c.check("first-$speed-$i")
                c.advance(50)
                i++
            }
            errs += c.check("first-$speed-flag")
            assertTrue(errs.joinToString("\n"), errs.isEmpty())
            assertEquals(c.id(first.seat), c.session.state.turn.startingPlayer)
        }
    }
}
