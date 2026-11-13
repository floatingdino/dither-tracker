package au.com.josephmark.dithertracker.stage

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.FakeScheduler
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host core with fake components: zone allocation, hit order, presses, invalidation, covers. */
class StageTest {

    /** Records everything the stage asks of the platform. */
    class FakePlatform : Stage.Platform {
        val invalidated = ArrayList<IRect>()
        var all = 0
        /** False: [invalidate] only counts (no allocation, for the T20 allocation tests). */
        var record = true
        var count = 0
        val clock = FakeScheduler(1024) // the real component list at six seats needs ~330 ids (T11)
        override fun invalidate(left: Int, top: Int, right: Int, bottom: Int) {
            count++
            if (record) invalidated += IRect(left, top, right, bottom)
        }
        override fun invalidateAll() { all++ }
        override fun schedule(id: Int, delayMillis: Long) = clock.schedule(id, delayMillis)
        override fun cancel(id: Int) = clock.cancel(id)
        override fun cancelAllTimers() { for (i in 0 until 256) clock.cancel(i) }
        override fun now(): Long = clock.now
    }

    /** A configurable component that declares fixed rects and records every call. */
    class Fake(
        val name: String,
        layer: Int,
        kind: Int,
        zones: Int,
        timers: Int = 0,
        private val log: MutableList<String>,
    ) : Component(layer, kind, zones, timers) {
        /** Local id → rect, per seat (screen: seat key -1); declared in insertion order. */
        val rects = LinkedHashMap<Pair<Int, Int>, IRect>()
        val blocks = HashMap<Int, IRect>()
        var repeating = HashSet<Int>()
        var feedbackOk = true
        override fun zones(seat: Int, z: ZoneSink) {
            blocks[seat]?.let { z.block(it) }
            for ((k, r) in rects) if (k.first == seat) z.add(k.second, r)
        }
        override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
            val r = rects[seat to local] ?: return false
            out.set(r.left, r.top, r.right, r.bottom)
            return feedbackOk
        }
        override fun repeats(seat: Int, local: Int) = local in repeating
        override fun onTap(seat: Int, local: Int) { log += "$name.tap($seat,$local)" }
        override fun onRepeat(seat: Int, local: Int) { log += "$name.repeat($seat,$local)" }
        override fun draw(f: Frame, seat: Int) { log += "$name.draw($seat)" }
        override fun onCoversChanged() { log += "$name.covers" }
        override fun onEvent(event: Int, seat: Int) { log += "$name.event($event,$seat)" }
        override fun onTimer(id: Int) { log += "$name.timer($id)" }
        override fun onStateChanged(old: GameState, new: GameState, origin: Int) { log += "$name.state($origin)" }
        override fun bounds(out: IRect): Boolean = rects[Stage.SCREEN to 0]?.let { out.set(it.left, it.top, it.right, it.bottom); true } ?: false
    }

    /** A frame without a renderer: records inversions (fakes never touch `r`). */
    class RecFrame(private val log: MutableList<String>) : Frame() {
        init { clip.set(0, 0, 10_000, 10_000) }
        override fun invert(rect: IRect, layer: Int) { log += "invert(L$layer $rect)" }
    }

    private val log = ArrayList<String>()
    private val platform = FakePlatform()
    private val session = GameSession.newGame(4)
    private val stage = Stage(session, SettingsHolder(), platform, seats = 4)
    private val l = PlayLayout.compute(DitherGrid(1872, 1404, 4))

    private val tile = stage.add(Fake("tile", Layer.TILE, Kind.CONTENT, zones = 3, timers = 2, log = log))
    private val readout = stage.add(Fake("readout", Layer.TILE, Kind.READOUT, zones = 0, log = log))
    private val chrome = stage.add(Fake("chrome", Layer.TILE_CHROME, Kind.CHROME, zones = 1, log = log))
    private val screen = stage.add(Fake("screen", Layer.SCREEN, Kind.CHROME, zones = 2, timers = 1, log = log))
    private val mode = stage.addCover(Cover(Kind.CONTENT or Kind.CHROME))

    init {
        stage.seal()
        platform.clock.target = stage
        stage.layout(l)
        stage.attach()
        log.clear()
        platform.invalidated.clear()
    }

    private fun rect(x: Int, y: Int, w: Int, h: Int) = IRect().setXywh(x, y, w, h)

    // ---- zone ids ------------------------------------------------------------------------------

    @Test fun zoneIdsAreAllocatedPerComponentAndSeat() {
        // tile: 4 seats × 3, chrome: 4 × 1, screen: 2 → 18 ids; readout declares none.
        assertEquals(18, stage.zoneCount)
        assertEquals(0, stage.zoneId(tile, 0, 0))
        assertEquals(5, stage.zoneId(tile, 1, 2))
        assertEquals(12, stage.zoneId(chrome, 0, 0))
        assertEquals(15, stage.zoneId(chrome, 3, 0))
        assertEquals(16, stage.zoneId(screen, Stage.SCREEN, 0))
        val seen = HashSet<Int>()
        for (s in 0 until 4) for (local in 0 until 3) {
            val id = stage.zoneId(tile, s, local)
            assertTrue(seen.add(id))
            assertEquals(tile, stage.zoneOwner(id)); assertEquals(s, stage.zoneSeat(id)); assertEquals(local, stage.zoneLocal(id))
        }
        assertEquals(Stage.SCREEN, stage.zoneSeat(17)); assertEquals(1, stage.zoneLocal(17))
        // Timers: one hold timer per zone, then each component's block.
        assertEquals(18 + 2 + 1, stage.timerCount)
    }

    @Test fun componentTimersRouteToTheirOwner() {
        tile.timers.schedule(1, 100)
        screen.timers.schedule(0, 50)
        platform.clock.advance(200)
        assertEquals(listOf("screen.timer(0)", "tile.timer(1)"), log)
    }

    // ---- hit order -----------------------------------------------------------------------------

    @Test fun topmostLayerWinsThenLatestDeclaration() {
        val p = l.panels[3]
        tile.rects[3 to 0] = IRect(p.left, p.top, p.right, p.bottom)          // whole panel
        tile.rects[3 to 1] = rect(p.left + 10, p.top + 10, 40, 40)             // later: on top of local 0
        chrome.rects[3 to 0] = rect(p.left + 20, p.top + 20, 10, 10)           // higher layer
        screen.rects[Stage.SCREEN to 0] = rect(p.left + 22, p.top + 22, 4, 4)  // highest
        assertEquals(stage.zoneId(screen, Stage.SCREEN, 0), stage.hitTest(p.left + 23, p.top + 23))
        assertEquals(stage.zoneId(chrome, 3, 0), stage.hitTest(p.left + 21, p.top + 21))
        assertEquals(stage.zoneId(tile, 3, 1), stage.hitTest(p.left + 11, p.top + 11))
        assertEquals(stage.zoneId(tile, 3, 0), stage.hitTest(p.left + 100, p.top + 100))
        assertEquals(PlayLayout.NONE, stage.hitTest(l.dividerV.left, 5))
    }

    @Test fun blocksSwallowAndContainsRefines() {
        val p = l.panels[2]
        val s2 = Stage(session, SettingsHolder(), FakePlatform())
        val t2 = s2.add(Fake("tile", Layer.TILE, Kind.CONTENT, 1, log = log))
        val c2 = s2.add(Fake("chrome", Layer.TILE_CHROME, Kind.CHROME, 1, log = log))
        val round = s2.add(object : Component(Layer.SCREEN, Kind.CHROME, 1) {
            override fun zones(seat: Int, z: ZoneSink) = z.add(0, rect(p.left + 100, p.top, 100, 100))
            override fun contains(seat: Int, local: Int, gx: Int, gy: Int) = gx < p.left + 150 // a shape inside its rect
        })
        s2.seal(); s2.layout(l)
        t2.rects[2 to 0] = IRect(p.left, p.top, p.right, p.bottom)
        c2.blocks[2] = rect(p.left, p.top, 50, 50)
        assertEquals(PlayLayout.NONE, s2.hitTest(p.left + 5, p.top + 5)) // blocked: nothing below is hit
        assertEquals(s2.zoneId(t2, 2, 0), s2.hitTest(p.left + 60, p.top + 60))
        assertEquals(s2.zoneId(round, Stage.SCREEN, 0), s2.hitTest(p.left + 120, p.top + 10))
        assertEquals(s2.zoneId(t2, 2, 0), s2.hitTest(p.left + 160, p.top + 10)) // outside the shape: falls through
    }

    // ---- presses -------------------------------------------------------------------------------

    @Test fun multiplePointersPressTapAndRepeatIndependently() {
        val a = l.panels[3]; val b = l.panels[1]
        tile.rects[3 to 0] = IRect(a.left, a.top, a.right, a.bottom)
        tile.rects[1 to 2] = IRect(b.left, b.top, b.right, b.bottom)
        tile.repeating += 2
        stage.down(0, a.left + 5, a.top + 5, 0)
        stage.down(1, b.left + 5, b.top + 5, 0)
        assertTrue(stage.isPressed(tile, 3, 0)); assertTrue(stage.isPressed(tile, 1, 2))
        assertEquals(listOf("tile.event(1,3)", "readout.event(1,3)", "chrome.event(1,3)", "screen.event(1,3)"), log.take(4))
        log.clear()
        platform.clock.advance(PlayInput.HOLD_DELAY_MS) // seat 1 local 2 repeats; seat 3 local 0 does not
        assertEquals(listOf("tile.repeat(1,2)"), log)
        stage.up(0) // release-to-fire
        assertEquals(listOf("tile.repeat(1,2)", "tile.tap(3,0)"), log)
        stage.up(1) // already repeating: no tap
        assertEquals(2, log.size)
        assertFalse(stage.isPressed(tile, 1, 2))
    }

    @Test fun cancelTouchesReleasesARangeWithoutApplying() {
        val p = l.panels[0]
        tile.rects[0 to 1] = IRect(p.left, p.top, p.right, p.bottom)
        stage.down(0, p.left + 1, p.top + 1, 0)
        stage.cancelTouches(tile, 0, 1, 2)
        stage.up(0)
        assertTrue(log.none { it.contains("tap") })
    }

    // ---- invalidation --------------------------------------------------------------------------

    @Test fun invalidationsInOneEntryPointFlushOnceAsTheirUnion() {
        val c = object : Component(Layer.TILE, Kind.CONTENT, 1) {
            override fun zones(seat: Int, z: ZoneSink) { if (seat == 3) z.add(0, l.panels[3]) }
            override fun onTap(seat: Int, local: Int) {
                invalidate(IRect(10, 10, 20, 20))
                invalidate(IRect(100, 5, 110, 15))
            }
        }
        val s2 = Stage(session, SettingsHolder(), platform)
        s2.add(c); s2.seal(); s2.layout(l)
        platform.invalidated.clear()
        val p = l.panels[3]
        s2.down(0, p.left + 1, p.top + 1, 0) // press: no feedback rect declared -> nothing
        assertTrue(platform.invalidated.isEmpty())
        s2.up(0)
        // One call, the union in device px (S = 4, no margin at 1872×1404).
        assertEquals(listOf(IRect(40, 20, 440, 80)), platform.invalidated)
    }

    @Test fun pressAndReleaseInvalidateTheFeedbackRect() {
        val p = l.panels[2]
        val r = rect(p.left + 4, p.top + 4, 10, 10)
        chrome.rects[2 to 0] = r
        stage.down(0, p.left + 5, p.top + 5, 0)
        assertEquals(listOf(IRect(r.left * 4, r.top * 4, r.right * 4, r.bottom * 4)), platform.invalidated)
        stage.up(0)
        assertEquals(2, platform.invalidated.size)
    }

    // ---- drawing -------------------------------------------------------------------------------

    @Test fun drawsLayerByLayerSeatBySeatAndInvertsAfterTheOwningLayer() {
        val p = l.panels[1]
        tile.rects[1 to 0] = IRect(p.left, p.top, p.right, p.bottom)
        chrome.rects[1 to 0] = rect(p.left, p.top, 10, 10)
        stage.down(0, p.left + 50, p.top + 50, 0) // tile zone
        stage.down(1, p.left + 5, p.top + 5, 0)   // chrome zone
        log.clear()
        stage.draw(RecFrame(log))
        val want = ArrayList<String>()
        for (s in 0 until 4) { want += "tile.draw($s)"; want += "readout.draw($s)" }
        want += "invert(L1 ${tile.rects[1 to 0]})"
        for (s in 0 until 4) want += "chrome.draw($s)"
        want += "invert(L2 ${chrome.rects[1 to 0]})"
        want += "screen.draw(-1)"
        assertEquals(want, log)
    }

    // ---- covers --------------------------------------------------------------------------------

    @Test fun coversHideByKindOnTheirSeatsAndCancelTouches() {
        val p = l.panels[1]
        tile.rects[1 to 0] = IRect(p.left, p.top, p.right, p.bottom)
        screen.rects[Stage.SCREEN to 0] = rect(l.diamondCx - 2, l.diamondCy - 2, 4, 4)
        stage.down(0, p.left + 5, p.top + 5, 0)
        assertTrue(stage.isPressed(tile, 1, 0))
        log.clear(); platform.invalidated.clear()

        stage.setCover(mode, 1 shl 1, screen = true)
        assertFalse(stage.isPressed(tile, 1, 0)) // cancelled, not tapped
        assertEquals(listOf("tile.covers", "readout.covers", "chrome.covers", "screen.covers"), log)
        assertFalse(stage.isVisible(tile, 1)); assertTrue(stage.isVisible(tile, 0))
        assertTrue(stage.isVisible(readout, 1)) // READOUT is not in the mask
        assertFalse(stage.isVisible(screen, Stage.SCREEN))
        // Redraw: seat 1's panel and the screen component's bounds, as one union.
        assertEquals(1, platform.invalidated.size)
        stage.up(0)
        assertTrue(log.none { it.contains("tap") })

        assertEquals(PlayLayout.NONE, stage.hitTest(p.left + 5, p.top + 5)) // hidden: not hit
        log.clear()
        stage.draw(RecFrame(log))
        assertFalse(log.contains("tile.draw(1)")); assertTrue(log.contains("tile.draw(0)")); assertTrue(log.contains("readout.draw(1)"))
        assertFalse(log.contains("screen.draw(-1)"))

        stage.setCover(mode, 0, screen = false)
        assertTrue(stage.isVisible(tile, 1))
        assertEquals(stage.zoneId(tile, 1, 0), stage.hitTest(p.left + 5, p.top + 5))
    }

    // ---- state ---------------------------------------------------------------------------------

    @Test fun stateChangesCarryTheirOrigin() {
        stage.changeLife(2, -1)
        assertEquals(listOf("tile.state(2)", "readout.state(2)", "chrome.state(2)", "screen.state(2)"), log)
        log.clear()
        session.changeLife(0, 1, 0) // not through the stage: external
        assertEquals("tile.state(${Origin.EXTERNAL})", log.first())
        log.clear()
        stage.dispatch(Origin.ATTRIBUTED, au.com.josephmark.dithertracker.core.Action.ChangeLife(session.state.players[1].id, 3))
        assertEquals("tile.state(${Origin.ATTRIBUTED})", log.first())
    }

    @Test fun eventsReachEveryComponent() {
        stage.send(Events.COMMANDER_TARGET, 2)
        assertEquals(4, log.count { it.endsWith("event(${Events.COMMANDER_TARGET},2)") })
    }

    @Test fun detachCancelsTimersAndListeners() {
        tile.timers.schedule(0, 10)
        stage.detach()
        platform.clock.advance(100)
        assertTrue(log.none { it.contains("timer") })
        assertTrue(session.listener == null)
    }

    @Test fun tilesAreSharedContext() {
        val t: TileSlots = stage.tiles!!
        assertFalse(t.compact(0))
        t.lower = TileSlots.LowerTile { it == 0 }
        assertTrue(t.compact(0)); assertFalse(t.compact(1))
        assertEquals(l.numberSlot[1], t.numberSlot(1))
    }
}
