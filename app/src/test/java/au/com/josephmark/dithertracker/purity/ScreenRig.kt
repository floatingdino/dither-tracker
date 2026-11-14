package au.com.josephmark.dithertracker.purity

import au.com.josephmark.dithertracker.commander.CommanderTile
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerComponent
import au.com.josephmark.dithertracker.drawer.DrawerTab
import au.com.josephmark.dithertracker.fx.FxComponent
import au.com.josephmark.dithertracker.history.HistoryOverlay
import au.com.josephmark.dithertracker.life.LifeComponent
import au.com.josephmark.dithertracker.menu.GemLayout
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.menu.MenuOverlay
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayScreen
import au.com.josephmark.dithertracker.settings.SettingsOverlay
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.IconPainter
import au.com.josephmark.dithertracker.stage.PlayInput
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.StageTest
import au.com.josephmark.dithertracker.tools.DiceOverlay
import au.com.josephmark.dithertracker.type.TextRenderer
import kotlin.random.Random

/**
 * The real Play screen ([PlayScreen] on a [Stage], every component) drawn onto a [RasterRenderer]
 * (T22): the JVM stand-in for `PlayView`. Drive it like a player — [tap] / [press] a component's zone
 * by its local id, or tap a dither point — then [render] the whole screen in either colour mode.
 * Timers run on a fake clock ([advance]).
 */
class ScreenRig(val w: Int, val h: Int, val s: Int, seed: Long = 7, val players: Int = 4) {
    val platform = StageTest.FakePlatform()
    val grid = DitherGrid(w, h, s)
    val l: PlayLayout = PlayLayout.compute(grid, players)
    val session: GameSession = GameSession.newGame(players)
    val stage: Stage = PlayScreen.build(Stage(session, SettingsHolder(), platform), Random(seed))
    val r = RasterRenderer(grid)

    val frame = object : Frame() {
        // The Nomad is API 30: press feedback is the in-place inversion.
        override fun invert(rect: IRect, layer: Int) = r.invertRect(rect)
    }.also {
        it.r = r
        it.text = TextRenderer(r)
        it.icons = IconPainter(r)
        it.clip.set(0, 0, grid.width, grid.height)
    }

    inline fun <reified C : Component> one(): C = stage.components.filterIsInstance<C>().single()

    val life = one<LifeComponent>()
    val drawer = one<DrawerComponent>()
    val tab = one<DrawerTab>()
    val cmd = one<CommanderTile>()
    val fx = one<FxComponent>()
    val menu = one<MenuOverlay>()
    val settings = one<SettingsOverlay>()
    val history = one<HistoryOverlay>()
    val dice = one<DiceOverlay>()

    init {
        platform.clock.target = stage
        stage.layout(l)
        stage.attach()
        r.scratch = true
        stage.prewarm(frame)
        r.scratch = false
    }

    val now: Long get() = platform.clock.now

    fun advance(ms: Long) = platform.clock.advance(ms)

    fun id(seat: Int) = session.state.players[seat].id

    fun dispatch(a: Action) = session.dispatch(a, now)

    // ---- input ------------------------------------------------------------------------------------

    // MotionEvent-like pointer ids: small, reused (PlayInput ignores ids >= MAX_POINTERS, so a plain
    // counter made a script quietly stop tapping after 32 touches). Ids held by a [press] are skipped
    // until [cancel].
    private var pointer = 0
    private var held = 0

    private fun nextPointer(): Int {
        for (i in 0 until PlayInput.MAX_POINTERS) {
            val p = pointer
            pointer = (pointer + 1) % PlayInput.MAX_POINTERS
            if ((held ushr p) and 1 == 0) return p
        }
        return pointer.also { pointer = (pointer + 1) % PlayInput.MAX_POINTERS } // every id held: reuse
    }

    /** A point inside [c]'s zone ([seat], [local]) that hit-tests to it, or null when it is not declared / fully covered. */
    fun pointOf(c: Component, seat: Int, local: Int): IntArray? {
        val id = stage.zoneId(c, seat, local)
        stage.collectNow()
        for (i in 0 until stage.collectedCount) {
            if (stage.collectedId(i) != id) continue
            val z = stage.collectedRect(i)
            val cx = (z.left + z.right) / 2
            val cy = (z.top + z.bottom) / 2
            if (stage.hitTest(cx, cy) == id) return intArrayOf(cx, cy)
            for (y in z.top until z.bottom) for (x in z.left until z.right) if (stage.hitTest(x, y) == id) return intArrayOf(x, y)
        }
        return null
    }

    private fun need(c: Component, seat: Int, local: Int): IntArray =
        pointOf(c, seat, local) ?: throw AssertionError("${c.javaClass.simpleName} seat $seat zone $local is not tappable")

    fun tapAt(gx: Int, gy: Int) {
        val p = nextPointer()
        stage.down(p, gx, gy, now)
        stage.up(p)
    }

    fun tap(c: Component, seat: Int, local: Int) = need(c, seat, local).let { tapAt(it[0], it[1]) }

    /**
     * Puts a finger down on the zone and leaves it there past the press delay (T57), so the zone
     * shows its inversion (render, then [release] or [cancel]).
     */
    fun press(c: Component, seat: Int, local: Int) = need(c, seat, local).let { pressAt(it[0], it[1]) }

    fun pressAt(gx: Int, gy: Int) {
        val p = nextPointer()
        held = held or (1 shl p)
        stage.down(p, gx, gy, now)
        advance(stage.pressDelayMillis) // held, not tapped: the inversion shows (T57)
    }

    fun cancel() {
        held = 0
        stage.cancel()
    }

    /** Opens the gem (the centre diamond) unless it is open already (T51: it stays open until dismissed). */
    fun openMenu() {
        if (!menu.state.isOpen) tapAt(l.diamondCx, l.diamondCy)
    }

    /** Closes the gem with its centre X (no-op when closed). */
    fun closeMenu() {
        if (menu.state.isOpen) tapAt(l.diamondCx, l.diamondCy)
    }

    fun gemPoint(item: MenuItem): IntArray {
        val g = menu.gem!!
        val slot = GemLayout.slotOf(item)
        return intArrayOf(g.facetX[slot], g.facetY[slot])
    }

    /** Diamond → gem → [item]'s facet. */
    fun openFromMenu(item: MenuItem) {
        openMenu()
        gemPoint(item).let { tapAt(it[0], it[1]) }
    }

    // ---- drawing ----------------------------------------------------------------------------------

    /** Draws the whole screen in the given colour mode, the way `PlayView.onDraw` does. */
    fun render(inverted: Boolean): RasterRenderer {
        r.inverted = inverted
        r.clear()
        stage.draw(frame)
        return r
    }

    val tag: String get() = "${w}x$h S=$s"
}
