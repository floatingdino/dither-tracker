package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.chrome.MenuDiamond
import au.com.josephmark.dithertracker.commander.CommanderClose
import au.com.josephmark.dithertracker.commander.CommanderTile
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerComponent
import au.com.josephmark.dithertracker.drawer.DrawerTab
import au.com.josephmark.dithertracker.life.LifeComponent
import au.com.josephmark.dithertracker.readout.ReadoutComponent
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.StageTest
import kotlin.random.Random

/**
 * The real Play screen ([PlayScreen] on a [Stage]) without Android: a fake platform (fake clock for
 * timers, recorded invalidations), laid out on [w]×[h] at scale [s]. Hits are reported as short keys
 * so tests read like the screen: `life:3:1`, `drawer:3:<local>`, `tab:2`, `cmd:1:<local>`, `close`,
 * `none`.
 */
class PlayRig(w: Int = 1872, h: Int = 1404, s: Int = 4, val session: GameSession = GameSession.newGame(4)) {
    val platform = StageTest.FakePlatform()
    val l: PlayLayout = PlayLayout.compute(DitherGrid(w, h, s))
    val stage: Stage = PlayScreen.build(Stage(session, SettingsHolder(), platform), Random(1))

    val life = one<LifeComponent>()
    val readout = one<ReadoutComponent>()
    val drawer = one<DrawerComponent>()
    val tab = one<DrawerTab>()
    val cmd = one<CommanderTile>()
    val close = one<CommanderClose>()
    val menu = one<MenuDiamond>()

    init {
        platform.clock.target = stage
        stage.layout(l)
        stage.attach()
    }

    private inline fun <reified C : Component> one(): C = stage.components.filterIsInstance<C>().single()

    fun key(id: Int): String {
        if (id < 0) return "none"
        val seat = stage.zoneSeat(id)
        val local = stage.zoneLocal(id)
        return when (stage.zoneOwner(id)) {
            life -> "life:$seat:$local"
            drawer -> "drawer:$seat:$local"
            tab -> "tab:$seat"
            cmd -> "cmd:$seat:$local"
            close -> "close"
            else -> "?"
        }
    }

    fun hit(gx: Int, gy: Int): String = key(stage.hitTest(gx, gy))

    /** The feedback rect of [c]'s [local] on [seat], or null. */
    fun feedback(c: Component, seat: Int, local: Int): IRect? {
        val out = IRect()
        return if (stage.feedback(stage.zoneId(c, seat, local), out)) out else null
    }

    fun id(seat: Int) = session.state.players[seat].id

    companion object {
        fun mid(r: IRect) = intArrayOf((r.left + r.right) / 2, (r.top + r.bottom) / 2)
    }
}
