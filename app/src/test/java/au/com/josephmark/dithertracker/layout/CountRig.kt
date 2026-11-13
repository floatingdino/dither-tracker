package au.com.josephmark.dithertracker.layout

import au.com.josephmark.dithertracker.commander.CommanderTile
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerComponent
import au.com.josephmark.dithertracker.drawer.DrawerTab
import au.com.josephmark.dithertracker.fx.FxComponent
import au.com.josephmark.dithertracker.ko.KoComponent
import au.com.josephmark.dithertracker.life.LifeComponent
import au.com.josephmark.dithertracker.menu.MenuOverlay
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayScreen
import au.com.josephmark.dithertracker.purity.PurityChecks
import au.com.josephmark.dithertracker.purity.RasterRenderer
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.IconPainter
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.StageTest
import au.com.josephmark.dithertracker.type.TextRenderer
import java.io.File
import kotlin.random.Random

/**
 * The real Play screen for [players] seats (T11) drawn onto a [RasterRenderer] — the purity suite's
 * `ScreenRig` with a player count. The stage is sized for six seats like the app's, so the count can
 * change in place ([session] `NewGameFor`) and the stage re-lays itself out.
 */
class CountRig(val w: Int, val h: Int, val s: Int, players: Int, seed: Long = 7) {
    val platform = StageTest.FakePlatform()
    val grid = DitherGrid(w, h, s)
    val session: GameSession = GameSession.newGame(players)
    val stage: Stage = PlayScreen.build(Stage(session, SettingsHolder(), platform), Random(seed))
    val r = RasterRenderer(grid)

    val l: PlayLayout get() = stage.layout!!

    val frame = object : Frame() {
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
    val ko = one<KoComponent>()
    val fx = one<FxComponent>()
    val menu = one<MenuOverlay>()

    init {
        platform.clock.target = stage
        stage.layout(PlayLayout.compute(grid, players))
        stage.attach()
        prewarm()
    }

    fun prewarm() {
        r.scratch = true
        stage.prewarm(frame)
        r.scratch = false
    }

    val now: Long get() = platform.clock.now
    fun advance(ms: Long) = platform.clock.advance(ms)
    fun id(seat: Int) = session.state.players[seat].id
    fun dispatch(a: Action) = session.dispatch(a, now)

    private var pointer = 0

    /** A point that hit-tests to [c]'s zone ([seat], [local]), or null. */
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

    fun tapAt(gx: Int, gy: Int) {
        val p = pointer++
        stage.down(p, gx, gy, now)
        stage.up(p)
    }

    fun tap(c: Component, seat: Int, local: Int) {
        val p = pointOf(c, seat, local) ?: throw AssertionError("${c.javaClass.simpleName} seat $seat zone $local is not tappable on ${l}")
        tapAt(p[0], p[1])
    }

    fun render(inverted: Boolean = false): RasterRenderer {
        r.inverted = inverted
        r.clear()
        stage.draw(frame)
        return r
    }

    /** Purity problems of the current state (both colour modes), empty when clean; writes PNGs of failures. */
    fun check(name: String): List<String> {
        val errs = ArrayList<String>()
        val a = render(false)
        val normal = a.px.copyOf()
        if (a.outside > 0 && l.comfortable) errs += "${a.outside} px outside the grid: ${a.outsideNotes}"
        if (!a.clipBalanced) errs += "clipTo without unclip"
        PurityChecks.phaseErrors(a).let { if (it.isNotEmpty()) errs += "off-phase: $it" }
        val inv = render(true)
        PurityChecks.complementError(normal, inv.px, inv.width)?.let { errs += it }
        if (errs.isNotEmpty()) {
            render(false).writePng(File(PurityChecks.outDir, "t11/$tag-$name.png"))
        }
        return errs.map { "$tag $name: $it" }
    }

    val tag: String get() = "${session.state.players.size}p-${w}x$h-S$s"
}
