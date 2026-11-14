package au.com.josephmark.dithertracker.fx

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Tile
import au.com.josephmark.dithertracker.life.LifeComponent
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.FakeScheduler
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayRig
import au.com.josephmark.dithertracker.readout.ReadoutComponent
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.random.Random

/**
 * [FxComponent] on the real Play screen ([PlayRig]: [au.com.josephmark.dithertracker.play.PlayScreen]
 * on a fake platform and clock), plus a minimal stage with an event spy for the KO hand-shake.
 */
class FxComponentTest {

    // ---- helpers ---------------------------------------------------------------------------------

    private val window = 3_000L // the core coalesce window = the readout's idle commit
    private val quiet = FxComponent.QUIET_MS // T55: effects start this long after the last change

    private fun rig(): Pair<PlayRig, FxComponent> {
        val rig = PlayRig()
        val fx = rig.stage.components.filterIsInstance<FxComponent>().single()
        fx.policy.random = Random(1)
        return rig to fx
    }

    /** Taps [seat]'s life half ([side] = MINUS/PLUS) [times] times (press + release each). */
    private fun tap(stage: Stage, l: PlayLayout, seat: Int, side: Int, times: Int = 1) {
        val r = l.zones[PlayLayout.zone(seat, side)]
        // Away from the centre of the half (the number area stays clear of other zones anyway).
        val x = if ((side == PlayLayout.MINUS) == (l.rotation[seat] == 0)) r.left + 8 else r.right - 8
        val y = (r.top + r.bottom) / 2
        repeat(times) {
            stage.down(0, x, y, 0)
            stage.up(0)
        }
    }

    private fun setLife(session: GameSession, seat: Int, life: Int) =
        session.dispatch(Action.SetLife(session.state.players[seat].id, life), 0L)

    private fun playing(fx: FxComponent) = (0 until 4).filter { fx.player.isPlaying(it) }

    // ---- triggers ----------------------------------------------------------------------------------

    @Test fun damagePlaysOnceAShortQuietAfterTheLastTapNotPerTapAndNotAtTheCommit() {
        val (rig, fx) = rig()
        tap(rig.stage, rig.l, 2, PlayLayout.MINUS)
        rig.platform.clock.advance(quiet - 100)
        tap(rig.stage, rig.l, 2, PlayLayout.MINUS) // a burst: the quiet restarts
        rig.platform.clock.advance(quiet - 100)
        tap(rig.stage, rig.l, 2, PlayLayout.MINUS)
        assertEquals(37, rig.session.state.players[2].life)
        assertNull("nothing while the taps keep coming", fx.player.current(2))
        rig.platform.clock.advance(quiet - 1)
        assertNull(fx.player.current(2))
        rig.platform.clock.advance(1)
        val f = fx.player.current(2)
        assertNotNull("plays a short quiet after the last tap", f)
        assertEquals(FxRole.DAMAGE, f!!.role)
        assertEquals(listOf(2), playing(fx))
        val readouts = rig.stage.readouts
        assertEquals("the readout is still up beside it", -3, readouts.delta(2))
        // It runs out to the clean number on its own; the commit plays nothing more.
        rig.platform.clock.advance(5_000)
        assertEquals(0, readouts.delta(2))
        assertEquals(emptyList<Int>(), playing(fx))
    }

    @Test fun moreTapsInTheSameBandDoNotReplayButEscalatingDoes() {
        val (rig, fx) = rig()
        tap(rig.stage, rig.l, 2, PlayLayout.MINUS, times = 2)
        rig.platform.clock.advance(quiet)
        assertEquals(FxRole.DAMAGE, fx.player.current(2)!!.role)
        tap(rig.stage, rig.l, 2, PlayLayout.MINUS) // touch-down cancels it; −3: same band
        assertNull(fx.player.current(2))
        rig.platform.clock.advance(quiet)
        assertNull("same band, same group: no replay", fx.player.current(2))
        // Escalate: a hold step of −10 takes the group's total to −13 → the huge band.
        holdFor(rig, 2, PlayLayout.MINUS, steps = 1)
        rig.platform.clock.advance(quiet)
        assertEquals("escalated into a bigger band", FxRole.BIG_HIT, fx.player.current(2)!!.role)
        fx.player.cancelAll()
        tap(rig.stage, rig.l, 2, PlayLayout.PLUS) // −12: still big; back down never replays a smaller band
        rig.platform.clock.advance(quiet)
        assertNull(fx.player.current(2))
        // A new group (after the commit) starts afresh.
        rig.platform.clock.advance(window)
        tap(rig.stage, rig.l, 2, PlayLayout.MINUS)
        rig.platform.clock.advance(quiet)
        assertEquals(FxRole.DAMAGE, fx.player.current(2)!!.role)
    }

    @Test fun healAndBigChangesPickByNetSignAndSize() {
        val (rig, fx) = rig()
        tap(rig.stage, rig.l, 3, PlayLayout.PLUS, times = 2)
        rig.platform.clock.advance(quiet)
        assertEquals(FxRole.HEAL, fx.player.current(3)!!.role)
        fx.player.cancelAll()
        // −2 then +2: nets to zero → nothing.
        tap(rig.stage, rig.l, 2, PlayLayout.MINUS, times = 2)
        tap(rig.stage, rig.l, 2, PlayLayout.PLUS, times = 2)
        rig.platform.clock.advance(quiet)
        assertNull(fx.player.current(2))
        // A huge hit (one hold step of 10): focus or stomp.
        holdFor(rig, 2, PlayLayout.MINUS, steps = 1)
        assertNull("nothing during the hold", fx.player.current(2))
        rig.platform.clock.advance(quiet)
        assertTrue(fx.player.current(2) in listOf(Fx.byName("focus"), Fx.byName("stomp")))
        fx.player.cancelAll()
        holdFor(rig, 3, PlayLayout.PLUS, steps = 1)
        rig.platform.clock.advance(quiet)
        assertTrue(fx.player.current(3) in listOf(Fx.byName("sunrise"), Fx.byName("vines"), Fx.byName("heart-burst")))
    }

    /** Presses [seat]'s [side] and holds it through [steps] repeat steps, then releases. */
    private fun holdFor(rig: PlayRig, seat: Int, side: Int, steps: Int) {
        val r = rig.l.zones[PlayLayout.zone(seat, side)]
        val x = if ((side == PlayLayout.MINUS) == (rig.l.rotation[seat] == 0)) r.left + 8 else r.right - 8
        val p = 0
        rig.stage.down(p, x, (r.top + r.bottom) / 2, 0)
        rig.platform.clock.advance(500)
        repeat(steps - 1) { rig.platform.clock.advance(1_000) }
        rig.stage.up(p)
    }

    @Test fun aLongHoldPlaysOnceNotPerRepeatStep() {
        val (rig, fx) = rig()
        val r = rig.l.zones[PlayLayout.zone(2, PlayLayout.PLUS)]
        rig.stage.down(0, r.right - 8, (r.top + r.bottom) / 2, 0)
        var starts = 0
        var was: Fx? = null
        repeat(80) {
            rig.platform.clock.advance(100) // hold steps every second (+10 each): one band from the first step on
            val now = fx.player.current(2)
            if (now != null && was == null) starts++
            was = now
        }
        rig.stage.up(0)
        assertTrue(rig.session.state.players[2].life > 40)
        assertEquals("one effect for the whole hold", 1, starts)
        rig.platform.clock.advance(window)
        assertNull(fx.player.current(2))
    }

    @Test fun aHoldThatCrossesZeroStopsAtTheKoAndPlaysKoShatterOnceWithTheDeadTileHeld() {
        val (rig, fx) = rig()
        val ko = rig.stage.components.filterIsInstance<au.com.josephmark.dithertracker.ko.KoComponent>().single()
        val r = rig.l.zones[PlayLayout.zone(2, PlayLayout.MINUS)]
        rig.stage.down(0, r.left + 8, (r.top + r.bottom) / 2, 0)
        rig.platform.clock.advance(500 + quiet) // 40 → 30, quiet: the huge hit plays mid-hold, once
        assertEquals(FxRole.BIG_HIT, fx.player.current(2)!!.role)
        rig.platform.clock.advance(3 * 1_000 - quiet) // → 20 → 10 → 0: KO (no replay for −20, −30)
        assertEquals(0, rig.session.state.players[2].life)
        assertTrue("dead tile held off at the KO (KO_HOLD)", (ko.held ushr 2) and 1 != 0)
        assertNull(fx.player.current(2))
        rig.platform.clock.advance(quiet)
        assertEquals("the KO flourish a short quiet after the KO", Fx.byName("ko-shatter"), fx.player.current(2))
        assertTrue("still held while it plays", (ko.held ushr 2) and 1 != 0)
        rig.platform.clock.advance(2_000)
        assertEquals("the repeat stopped at the KO", 0, rig.session.state.players[2].life)
        assertNull(fx.player.current(2))
        assertEquals("released when it ends (KO_RELEASE)", 0, (ko.held ushr 2) and 1)
        assertTrue((ko.shown ushr 2) and 1 != 0)
        rig.stage.up(0)
        rig.platform.clock.advance(10_000)
        assertNull("once", fx.player.current(2))
    }

    @Test fun touchDownOnTheSeatCancelsItsEffectAndRedrawsClean() {
        val (rig, fx) = rig()
        tap(rig.stage, rig.l, 2, PlayLayout.MINUS)
        tap(rig.stage, rig.l, 3, PlayLayout.MINUS)
        rig.platform.clock.advance(quiet)
        assertEquals(listOf(2, 3), playing(fx))
        rig.platform.invalidated.clear()
        val r = rig.l.zones[PlayLayout.zone(2, PlayLayout.PLUS)]
        rig.stage.down(1, (r.left + r.right) / 2, (r.top + r.bottom) / 2, 0)
        assertEquals("seat 2 stopped at touch-down; seat 3 untouched", listOf(3), playing(fx))
        assertTrue(rig.platform.invalidated.isNotEmpty())
        rig.stage.up(1)
    }

    @Test fun atMostTwoSeatsAnimateAThirdStaysClean() {
        val (rig, fx) = rig()
        for (s in 0 until 4) tap(rig.stage, rig.l, s, PlayLayout.MINUS)
        rig.platform.clock.advance(quiet)
        assertEquals(2, playing(fx).size)
        assertEquals(2, fx.player.active)
    }

    @Test fun undoRedoNewGameAndLayoutStopEverything() {
        val (rig, fx) = rig()
        fun start() {
            tap(rig.stage, rig.l, 2, PlayLayout.MINUS); tap(rig.stage, rig.l, 3, PlayLayout.PLUS)
            rig.platform.clock.advance(quiet)
            assertEquals(2, playing(fx).size)
        }
        start(); rig.session.undo(); assertEquals(emptyList<Int>(), playing(fx))
        start(); rig.session.dispatch(Action.SetToggle(rig.id(0), au.com.josephmark.dithertracker.core.Toggle.MONARCH, true), 0L)
        assertEquals("any external action", emptyList<Int>(), playing(fx))
        start(); rig.stage.layout(rig.l); assertEquals(emptyList<Int>(), playing(fx))
        start(); rig.stage.detach(); assertEquals(emptyList<Int>(), playing(fx))
    }

    @Test fun undoInsideAnOpenGroupPlaysNothing() {
        val (rig, fx) = rig()
        tap(rig.stage, rig.l, 2, PlayLayout.MINUS, times = 4)
        rig.session.undo() // commits every readout as "another kind of action"
        rig.platform.clock.advance(quiet)
        assertEquals(emptyList<Int>(), playing(fx))
    }

    @Test fun countersPlayTheirEffectOnAGainOnceABurstGoesQuiet() {
        val (rig, fx) = rig()
        for ((c, want) in listOf(
            Counter.POISON to listOf("ooze", "brand"), Counter.ENERGY to listOf("charge"),
            Counter.EXPERIENCE to listOf("level-up"), Counter.RAD to listOf("rad-glow"), Counter.STORM to listOf("storm-copy"),
        )) {
            rig.stage.dispatch(2, Action.ChangeCounter(rig.id(2), c, 1))
            rig.platform.clock.advance(quiet - 1)
            assertNull(fx.player.current(2))
            rig.stage.dispatch(2, Action.ChangeCounter(rig.id(2), c, 1)) // a burst: the quiet restarts
            rig.platform.clock.advance(quiet)
            assertTrue("$c → ${fx.player.current(2)}", fx.player.current(2)?.id in want)
            fx.player.cancelAll()
            rig.stage.dispatch(2, Action.ChangeCounter(rig.id(2), c, 1)) // same burst (< 3 s): no replay
            rig.platform.clock.advance(quiet)
            assertNull("$c once per burst", fx.player.current(2))
            rig.platform.clock.advance(FxComponent.BURST_MS)
        }
        // Tickets and the generic counter play nothing; nor does a decrement, or a gain undone in its burst.
        rig.stage.dispatch(2, Action.ChangeCounter(rig.id(2), Counter.TICKETS, 1))
        rig.stage.dispatch(2, Action.ChangeCounter(rig.id(2), Counter.POISON, -1))
        rig.platform.clock.advance(quiet)
        assertNull(fx.player.current(2))
        rig.stage.dispatch(1, Action.ChangeCounter(rig.id(1), Counter.ENERGY, 1))
        rig.stage.dispatch(1, Action.ChangeCounter(rig.id(1), Counter.ENERGY, -1))
        rig.platform.clock.advance(quiet)
        assertNull("+1 −1 inside a burst nets nothing", fx.player.current(1))
    }

    @Test fun togglesPlayWhenTurnedOnNeverOff() {
        val (rig, fx) = rig()
        for ((t, want) in listOf(Toggle.MONARCH to "crown-drop", Toggle.INITIATIVE to "torch", Toggle.RING_BEARER to "ring-tempt")) {
            rig.stage.dispatch(1, Action.SetToggle(rig.id(1), t, true))
            rig.platform.clock.advance(quiet)
            assertEquals(t.name, want, fx.player.current(1)?.id)
            fx.player.cancelAll()
            rig.stage.dispatch(1, Action.SetToggle(rig.id(1), t, false))
            rig.platform.clock.advance(quiet + FxComponent.BURST_MS)
            assertNull("$t off plays nothing", fx.player.current(1))
        }
        rig.stage.dispatch(1, Action.SetToggle(rig.id(1), Toggle.CITYS_BLESSING, true))
        rig.platform.clock.advance(quiet)
        assertNull("bunting is not approved", fx.player.current(1))
        // Claiming the monarch from another seat: the new monarch plays, the old one doesn't.
        rig.stage.dispatch(1, Action.SetToggle(rig.id(1), Toggle.MONARCH, true))
        rig.platform.clock.advance(quiet + FxComponent.BURST_MS)
        fx.player.cancelAll()
        rig.stage.dispatch(3, Action.SetToggle(rig.id(3), Toggle.MONARCH, true))
        rig.platform.clock.advance(quiet)
        assertEquals(Fx.byName("crown-drop"), fx.player.current(3))
        assertNull(fx.player.current(1))
    }

    @Test fun hiddenTrackersPlayNothing() {
        val (rig, fx) = rig()
        val st = rig.stage.settings
        st.update(st.current.copy(drawerCounters = st.current.drawerCounters - Counter.POISON,
            hiddenTrackers = setOf(au.com.josephmark.dithertracker.core.serial.Tracker.MONARCH, au.com.josephmark.dithertracker.core.serial.Tracker.DAY_NIGHT)))
        rig.stage.dispatch(2, Action.ChangeCounter(rig.id(2), Counter.POISON, 1))
        rig.stage.dispatch(1, Action.SetToggle(rig.id(1), Toggle.MONARCH, true))
        rig.stage.dispatch(0, Action.SetDayNight(DayNight.NIGHT))
        rig.platform.clock.advance(quiet)
        assertEquals(emptyList<Int>(), playing(fx))
    }

    @Test fun nightAndDayPlayOnEveryLiveSeatAtOnceInOneFlush() {
        val (rig, fx) = rig()
        setLife(rig.session, 3, 0) // a KO'd seat plays nothing
        rig.platform.clock.advance(10_000)
        rig.stage.dispatch(0, Action.SetDayNight(DayNight.NIGHT))
        rig.platform.clock.advance(quiet)
        assertEquals(listOf(0, 1, 2), playing(fx))
        for (s in 0..2) assertEquals(Fx.byName("dusk"), fx.player.current(s))
        assertEquals("past the cap of two", 3, fx.player.active)
        assertEquals(0b0111, fx.player.group)
        // Each keyframe is one flush for every seat.
        val before = rig.platform.invalidated.size
        rig.platform.clock.advance(fx.player.hold(Fx.byName("dusk")!!, 0))
        assertEquals("every seat's keyframe in one flush", before + 1, rig.platform.invalidated.size)
        // A touch on one seat stops it there only.
        val r = rig.l.zones[PlayLayout.zone(1, PlayLayout.PLUS)]
        rig.stage.down(1, (r.left + r.right) / 2, (r.top + r.bottom) / 2, 0)
        rig.stage.up(1)
        assertEquals(listOf(0, 2), playing(fx))
        rig.platform.clock.advance(5_000)
        assertEquals(emptyList<Int>(), playing(fx))
        rig.stage.dispatch(2, Action.SetDayNight(DayNight.DAY))
        rig.platform.clock.advance(quiet)
        assertEquals(listOf(0, 1, 2), playing(fx))
        assertEquals(Fx.byName("dawn"), fx.player.current(1))
        rig.platform.clock.advance(5_000)
        rig.stage.dispatch(2, Action.SetDayNight(DayNight.UNSET))
        rig.platform.clock.advance(quiet + FxComponent.BURST_MS)
        assertEquals("unset plays nothing", emptyList<Int>(), playing(fx))
    }

    @Test fun theTurnPassingPlaysOnTheNewActiveSeat() {
        val (rig, fx) = rig()
        val turn = rig.stage.components.filterIsInstance<au.com.josephmark.dithertracker.turns.TurnTab>().single()
        rig.stage.dispatch(-1, Action.SetStartingPlayer(rig.id(1)))
        assertEquals(1, turn.shownSeat)
        turn.onTap(1, au.com.josephmark.dithertracker.turns.TurnTab.PASS)
        val now = fx.player.current(2)
        assertTrue("${now?.id}", now?.id in listOf("your-turn", "spotlight"))
        assertEquals(listOf(2), playing(fx))
        // Undoing the pass plays nothing (external) and stops it.
        rig.session.undo()
        assertEquals(emptyList<Int>(), playing(fx))
    }

    @Test fun theFirstPlayerPickPlaysTheStartFlagOnTheirTile() {
        val (rig, fx) = rig()
        val first = rig.stage.components.filterIsInstance<au.com.josephmark.dithertracker.tools.FirstPlayer>().single()
        first.pick()
        var seat = -1
        repeat(200) {
            if (seat < 0) for (s in 0 until 4) if (fx.player.current(s) == Fx.byName("start-flag")) seat = s
            if (seat < 0) rig.platform.clock.advance(25)
        }
        assertTrue("start-flag played", seat >= 0)
        assertEquals(rig.session.state.turn.startingPlayer, rig.id(seat))
    }

    @Test fun aReviveFromTheKoTilePlaysRevive() {
        val (rig, fx) = rig()
        setLife(rig.session, 2, 0)
        rig.platform.clock.advance(10_000)
        rig.stage.dispatch(2, Action.Revive(rig.id(2)))
        rig.platform.clock.advance(quiet)
        assertEquals(Fx.byName("revive"), fx.player.current(2))
    }

    @Test fun nothingPlaysOnUndoRedoOrRestoreOfAGain() {
        val (rig, fx) = rig()
        rig.stage.dispatch(2, Action.ChangeCounter(rig.id(2), Counter.POISON, 2))
        rig.stage.dispatch(1, Action.SetToggle(rig.id(1), Toggle.MONARCH, true))
        rig.session.undo(); rig.session.undo()
        rig.platform.clock.advance(quiet)
        assertEquals("undo before the quiet: nothing", emptyList<Int>(), playing(fx))
        rig.session.redo(); rig.session.redo()
        rig.platform.clock.advance(quiet + FxComponent.BURST_MS)
        assertEquals("redo is external: nothing", emptyList<Int>(), playing(fx))
        // A restored game (state set from outside the screen) plays nothing either.
        rig.session.dispatch(Action.ChangeCounter(rig.id(3), Counter.RAD, 5), 0L)
        rig.session.dispatch(Action.SetDayNight(DayNight.NIGHT), 0L)
        rig.platform.clock.advance(quiet)
        assertEquals(emptyList<Int>(), playing(fx))
    }

    @Test fun effectsNeverPlayOnAKoTileOrUnderAnOverlay() {
        val (rig, fx) = rig()
        setLife(rig.session, 2, 0)
        rig.platform.clock.advance(10_000)
        rig.stage.dispatch(2, Action.ChangeCounter(rig.id(2), Counter.POISON, 1)) // (no drawer on a dead tile; forced)
        rig.platform.clock.advance(quiet)
        assertNull(fx.player.current(2))
        rig.stage.components.filterIsInstance<au.com.josephmark.dithertracker.history.HistoryOverlay>().single()
            .open(au.com.josephmark.dithertracker.menu.MenuItem.LOG, 0) // a full overlay
        rig.stage.dispatch(1, Action.ChangeCounter(rig.id(1), Counter.ENERGY, 1))
        rig.platform.clock.advance(quiet)
        assertNull("under the menu", fx.player.current(1))
    }

    @Test fun aCompactSeatClipsItsEffectToTheAreaAboveTheDrawer() {
        val (rig, fx) = rig()
        val drawer = rig.stage.components.filterIsInstance<au.com.josephmark.dithertracker.drawer.DrawerComponent>().single()
        drawer.controller.onTap(2, au.com.josephmark.dithertracker.drawer.DrawerZones.TAB)
        val tiles = rig.stage.tiles!!
        assertTrue(tiles.compact(2))
        rig.stage.dispatch(2, Action.ChangeCounter(rig.id(2), Counter.POISON, 1))
        rig.platform.clock.advance(quiet)
        assertTrue(fx.player.current(2)?.id in listOf("ooze", "brand"))
        val a = tiles.lifeFeedback(2, PlayLayout.MINUS)
        val b = tiles.lifeFeedback(2, PlayLayout.PLUS)
        val upper = IRect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        val got = fx.areaOf(2)
        assertTrue("$got inside $upper", got.left >= upper.left && got.top >= upper.top && got.right <= upper.right && got.bottom <= upper.bottom)
    }

    @Test fun commanderDamageLifeLossPlaysTheImpOnTheTargetWhenTheModeCloses() {
        val (rig, fx) = rig()
        val target = 2
        rig.cmd.mode.press(target)
        assertTrue(rig.cmd.mode.active)
        repeat(4) { rig.cmd.dispatchDamage(3, Action.ChangeCommanderDamage(rig.id(target), rig.id(3), Commander.PRIMARY, 1)) }
        assertEquals(36, rig.session.state.players[target].life)
        rig.platform.clock.advance(quiet)
        // The number is hidden in the mode: kept, not played over the mode's tile; the source's
        // own group (damage dealt, no life change) plays nothing.
        assertEquals(emptyList<Int>(), playing(fx))
        rig.cmd.mode.exit()
        assertEquals(listOf(target), playing(fx))
        assertEquals(FxRole.COMMANDER, fx.player.current(target)!!.role)
    }

    // ---- KO and winner -------------------------------------------------------------------------------

    /** A stage with just life, readout and effects, plus a spy recording KO hold/release. */
    /** Fake platform: fake clock; records invalidations, or only counts them ([record] false: no allocation). */
    private class Platform(val record: Boolean = true) : Stage.Platform {
        val invalidated = ArrayList<IRect>()
        var count = 0
        val clock = FakeScheduler(256)
        override fun invalidate(left: Int, top: Int, right: Int, bottom: Int) {
            count++
            if (record) invalidated += IRect(left, top, right, bottom)
        }
        override fun invalidateAll() {}
        override fun schedule(id: Int, delayMillis: Long) = clock.schedule(id, delayMillis)
        override fun cancel(id: Int) = clock.cancel(id)
        override fun cancelAllTimers() { for (i in 0 until 256) clock.cancel(i) }
        override fun now(): Long = clock.now
    }

    private class Mini(val platform: Platform = Platform()) {
        val session = GameSession.newGame(4)
        val stage = Stage(session, SettingsHolder(), platform)
        val events = ArrayList<String>()
        val l = PlayLayout.compute(DitherGrid(1872, 1404, 4))
        val fx: FxComponent

        init {
            stage.add(LifeComponent())
            stage.add(ReadoutComponent())
            fx = stage.add(FxComponent(Random(1)))
            stage.add(object : Component(Layer.TILE, Kind.STATUS) {
                override fun onEvent(event: Int, seat: Int) {
                    if (event == Events.KO_HOLD) events += "hold:$seat"
                    if (event == Events.KO_RELEASE) events += "release:$seat"
                }
            })
            stage.seal()
            platform.clock.target = stage
            stage.layout(l)
            stage.attach()
        }
    }

    @Test fun lifeKoHoldsTheDeadTileThenPlaysKoShatterAndReleasesWhenItEnds() {
        val m = Mini()
        setLife(m.session, 2, 2)
        tap(m.stage, m.l, 2, PlayLayout.MINUS, times = 2)
        assertEquals("held at the KO, before the commit", listOf("hold:2"), m.events)
        m.platform.clock.advance(quiet)
        assertEquals(Fx.byName("ko-shatter"), m.fx.player.current(2))
        assertEquals(listOf("hold:2"), m.events)
        m.platform.clock.advance(2_000)
        assertNull(m.fx.player.current(2))
        assertEquals(listOf("hold:2", "release:2"), m.events)
    }

    @Test fun aTouchDuringTheKoFlourishReleasesTheDeadTile() {
        val m = Mini()
        setLife(m.session, 2, 1)
        tap(m.stage, m.l, 2, PlayLayout.MINUS)
        m.platform.clock.advance(quiet)
        assertNotNull(m.fx.player.current(2))
        val r = m.l.zones[PlayLayout.zone(2, PlayLayout.MINUS)]
        m.stage.down(1, r.left + 8, (r.top + r.bottom) / 2, 0)
        assertNull(m.fx.player.current(2))
        assertEquals(listOf("hold:2", "release:2"), m.events)
        m.stage.up(1)
    }

    @Test fun healedBackInsideTheGroupIsAPlainHeal() {
        val m = Mini()
        setLife(m.session, 2, 1)
        tap(m.stage, m.l, 2, PlayLayout.MINUS) // 0: KO, held
        tap(m.stage, m.l, 2, PlayLayout.PLUS, times = 3) // 3: alive again, net +2
        m.platform.clock.advance(quiet)
        assertEquals(FxRole.HEAL, m.fx.player.current(2)!!.role)
    }

    @Test fun lastPlayerStandingGetsWinnerAlongsideTheLastKo() {
        val m = Mini()
        setLife(m.session, 0, 0); setLife(m.session, 1, 0) // already out
        setLife(m.session, 3, 3)
        tap(m.stage, m.l, 3, PlayLayout.MINUS, times = 3)
        m.platform.clock.advance(quiet)
        assertEquals(Fx.byName("ko-shatter"), m.fx.player.current(3))
        assertEquals(Fx.byName("winner"), m.fx.player.current(2))
        m.platform.clock.advance(10_000)
        // Once per game: another commit on the survivor is a plain effect.
        tap(m.stage, m.l, 2, PlayLayout.PLUS)
        m.platform.clock.advance(quiet)
        assertEquals(FxRole.HEAL, m.fx.player.current(2)!!.role)
    }

    @Test fun aKoFlourishMakesRoomPastTheCapOfTwo() {
        val m = Mini()
        setLife(m.session, 3, 1)
        tap(m.stage, m.l, 1, PlayLayout.MINUS)
        tap(m.stage, m.l, 2, PlayLayout.MINUS)
        m.platform.clock.advance(100)
        tap(m.stage, m.l, 3, PlayLayout.MINUS) // KO, commits 100 ms after the other two
        m.platform.clock.advance(quiet - 100)
        assertEquals(listOf(1, 2), playing(m.fx)) // the cap is full of plain hits
        m.platform.clock.advance(100)
        assertEquals(Fx.byName("ko-shatter"), m.fx.player.current(3))
        assertEquals(2, playing(m.fx).size) // one plain hit made room
    }

    @Test fun aNonLifeKoLeavingOnePlayerPlaysWinnerAtOnce() {
        val m = Mini()
        setLife(m.session, 0, 0); setLife(m.session, 1, 0)
        m.stage.dispatch(3, Action.ChangeCounter(m.session.state.players[3].id, Counter.POISON, 10))
        assertEquals(Fx.byName("winner"), m.fx.player.current(2))
        assertNull("poison KO has no flourish", m.fx.player.current(3))
        assertEquals(emptyList<String>(), m.events)
    }

    // ---- invalidation and drawing ----------------------------------------------------------------

    @Test fun invalidatesTheFootprintOncePerKeyframeAndTheWholePanelForTileEffects() {
        val m = Mini()
        val g = m.l.grid
        for ((name, seat) in listOf("quake" to 2, "pow" to 1, "claw-cut" to 3, "twinkle" to 0, "focus" to 1)) {
            val fx = Fx.byName(name)!!
            m.fx.policy.full[FxPolicy.Trigger.DAMAGE.ordinal] = arrayOf(fx)
            tap(m.stage, m.l, seat, PlayLayout.MINUS)
            m.platform.invalidated.clear()
            m.platform.clock.advance(quiet)
            assertEquals(fx, m.fx.player.current(seat))
            val panel = m.l.panels[seat]
            val want = IRect()
            if (fx.spec.isTileScale) {
                want.set(panel.left, panel.top, panel.right, panel.bottom)
            } else {
                val box = IRect()
                m.stage.tiles!!.numberFootprint(seat, m.session.state.players[seat].life, box)
                FxLayout.place(fx, box, panel, m.l.rotation[seat], want)
                want.set(maxOf(want.left, panel.left), maxOf(want.top, panel.top), minOf(want.right, panel.right), minOf(want.bottom, panel.bottom))
            }
            val dev = g.toDevice(want, IRect())
            // The quiet timer's flush: the footprint (the readout stays up until its own commit).
            val first = m.platform.invalidated.single()
            assertTrue("$name start", first.left <= dev.left && first.top <= dev.top && first.right >= dev.right && first.bottom >= dev.bottom)
            // Then exactly the footprint, once per keyframe, the clean step included.
            for (i in 1 until fx.frameCount) {
                m.platform.invalidated.clear()
                m.platform.clock.advance(fx.holdMs(i - 1).toLong())
                assertEquals("$name step $i", listOf(dev), m.platform.invalidated)
            }
            assertNull(m.fx.player.current(seat))
            m.platform.clock.advance(window) // the readout commits (and redraws) before the next round
        }
    }

    /** Records every mask pixel the renderer touches, and whether a clip was set. */
    private class Rec : FxRenderer.Target {
        var ops = 0
        var clips = 0
        override fun blit(img: MonoImage, x: Int, y: Int, rotation: Int, paper: Boolean) { ops++ }
        override fun invert(mask: MonoImage, x: Int, y: Int, underlay: Runnable?) { ops++ }
        override fun tile(mask: MonoImage, x: Int, y: Int, tile: Tile, mode: FxTileMode) { ops++ }
        override fun clip(rect: IRect) { clips++ }
    }

    private class TestFrame(players: List<au.com.josephmark.dithertracker.core.Player>) : Frame() {
        init { this.players = players; clip.set(0, 0, 10_000, 10_000) }
        override fun invert(rect: IRect, layer: Int) {}
    }

    @Test fun drawsOnlyWhilePlayingAndNeverAllocatesPerFrame() {
        val platform = Platform(record = false)
        val m = Mini(platform)
        val rec = Rec()
        m.fx.renderer = FxRenderer(rec)
        val frame = TestFrame(m.session.state.players)
        for (s in 0 until 4) m.fx.draw(frame, s)
        assertEquals("clean seats draw nothing", 0, rec.ops)
        val effects = Fx.entries.toTypedArray()
        m.fx.startGallery(effects, 37) // sets every seat's footprint through the real start path
        frame.players = m.session.state.players
        val p = m.fx.player
        fun cycle() {
            for (f in effects) {
                for (s in 0 until 4) p.play(s, f)
                for (i in 0 until f.frameCount) {
                    for (s in 0 until 4) m.fx.draw(frame, s)
                    for (s in 0 until 4) m.fx.onTimer(s) // one keyframe step per seat
                }
            }
        }
        repeat(30) { cycle() } // warm up: turned views, JIT
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val tid = Thread.currentThread().id
        // Steady state: the JVM itself allocates now and then on this thread (JIT deoptimisation
        // materialising objects), so take the best of a few runs; any real per-frame allocation
        // would show in every run.
        var best = Long.MAX_VALUE
        repeat(5) {
            val before = bean.getThreadAllocatedBytes(tid)
            repeat(3) { cycle() }
            best = minOf(best, bean.getThreadAllocatedBytes(tid) - before)
        }
        assertEquals("bytes allocated by 3 cycles of every effect on four seats (draw + keyframe steps)", 0L, best)
        assertTrue(rec.ops > 0)
        assertTrue(platform.count > 0)
    }

    @Test fun tileScaleFramesNeedNoClip() {
        val m = Mini()
        val rec = Rec()
        m.fx.renderer = FxRenderer(rec)
        val frame = TestFrame(m.session.state.players)
        m.fx.startGallery(arrayOf(Fx.byName("quake")!!), 37)
        for (s in 0 until 4) m.fx.draw(frame, s)
        assertEquals("tile-scale frames fit their panel exactly", 0, rec.clips)
        assertTrue(rec.ops > 0)
    }

    @Test fun galleryCyclesEveryEffectOnAllFourSeatsUntilATouch() {
        val m = Mini()
        val all = Fx.entries.toTypedArray()
        m.fx.startGallery(all, 37)
        for (s in 0 until 4) assertEquals(37, m.session.state.players[s].life)
        val seen = ArrayList<Fx>()
        repeat(all.size) {
            val f = m.fx.player.current(0)!!
            for (s in 0 until 4) assertEquals(f, m.fx.player.current(s))
            seen += f
            var total = 0L
            for (i in 0 until f.frameCount) total += m.fx.player.hold(f, i)
            m.platform.clock.advance(total + FxComponent.GALLERY_GAP_MS)
        }
        assertEquals(all.toList(), seen)
        assertEquals("loops", all[0], m.fx.player.current(0))
        val r = m.l.zones[PlayLayout.zone(2, PlayLayout.MINUS)]
        m.stage.down(1, r.left + 8, (r.top + r.bottom) / 2, 0)
        m.stage.up(1)
        m.platform.clock.advance(20_000)
        assertEquals(emptyList<Int>(), playing(m.fx))
        assertEquals(FxPlayer.DEFAULT_MAX_CONCURRENT, m.fx.player.maxConcurrent)
    }

    @Test fun holdScaleComesFromTheDebugHook() {
        FxDebug.holdScale = 3f
        try {
            val m = Mini()
            assertEquals(3f, m.fx.player.holdScale)
        } finally {
            FxDebug.holdScale = 1f
        }
    }

    @Suppress("unused")
    private fun GameState.lifeOf(seat: Int) = players[seat].life
}
