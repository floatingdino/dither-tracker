package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.commander.CommanderClose
import au.com.josephmark.dithertracker.commander.CommanderLayout
import au.com.josephmark.dithertracker.commander.CommanderMode
import au.com.josephmark.dithertracker.commander.CommanderTile
import au.com.josephmark.dithertracker.commander.CommanderZones
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerComponent
import au.com.josephmark.dithertracker.drawer.DrawerLayout
import au.com.josephmark.dithertracker.drawer.DrawerTab
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.life.LifeComponent
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.StageTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * T34 proof of no input change: for every dither pixel, in every interesting state, the component
 * stage hits the same zone (by meaning), with the same feedback rect and repeat policy, as the
 * pre-T34 hand-written hit-test (`CommanderLayout.hitTest` delegating to `DrawerLayout.hitTest`).
 * Removed together with the old hit-test code once the switch is made.
 */
class StageEquivalenceTest {

    private class Rig(w: Int, h: Int, s: Int) {
        val session = GameSession.newGame(4)
        val l = PlayLayout.compute(DitherGrid(w, h, s))
        val stage = PlayScreen.build(Stage(session, SettingsHolder(), StageTest.FakePlatform()), Random(1))
        val drawer = stage.components.filterIsInstance<DrawerComponent>().single()
        val cmd = stage.components.filterIsInstance<CommanderTile>().single()
        val oldD = DrawerLayout(l)
        val oldC = CommanderLayout(l)

        init {
            stage.layout(l)
            stage.attach()
        }

        fun oldHit(gx: Int, gy: Int): Int {
            val z = oldC.hitTest(gx, gy, cmd.mode.target, session.state.players)
            return if (z != CommanderLayout.DELEGATE) z else oldD.hitTest(gx, gy, drawer.state)
        }

        fun oldKey(z: Int): String = when {
            z < 0 -> "none"
            DrawerZones.isLife(z) -> "life:${z / 2}:${z % 2}"
            DrawerZones.isDrawer(z) -> "drawer:${DrawerZones.seatOf(z)}:${DrawerZones.localOf(z)}"
            z == CommanderZones.DIAMOND -> "close"
            CommanderZones.localOf(z) == CommanderZones.BUTTON -> "drawer:${CommanderZones.seatOf(z)}:${DrawerZones.BUTTON}"
            else -> "cmd:${CommanderZones.seatOf(z)}:${CommanderZones.localOf(z)}"
        }

        fun newKey(id: Int): String {
            if (id < 0) return "none"
            val s = stage.zoneSeat(id)
            val local = stage.zoneLocal(id)
            return when (stage.zoneOwner(id)) {
                is LifeComponent -> "life:$s:$local"
                is DrawerComponent -> "drawer:$s:$local"
                is DrawerTab -> "drawer:$s:${DrawerZones.TAB}"
                is CommanderTile -> "cmd:$s:$local"
                is CommanderClose -> "close"
                else -> "?${stage.zoneOwner(id)}"
            }
        }

        /** The old view's feedbackRect (PlayView pre-T34). */
        fun oldFeedback(z: Int, out: IRect): Boolean {
            if (CommanderZones.isCommander(z) && z != CommanderZones.DIAMOND && CommanderZones.localOf(z) == CommanderZones.BUTTON) {
                val s = CommanderZones.seatOf(z)
                if (!drawer.state.isOpen(s)) return false
                val v = oldD.cmdVis[s]
                out.set(v.left, v.top, v.right, v.bottom)
                return true
            }
            return if (CommanderZones.isCommander(z)) oldC.feedbackRect(z, session.state.players, out) else oldD.feedbackRect(z, drawer.state, out)
        }

        fun oldRepeats(z: Int): Boolean = when {
            DrawerZones.isLife(z) -> stage.holdStep > 0
            CommanderZones.isCommander(z) -> cmd.mode.repeats(z)
            else -> drawer.controller.repeats(z)
        }

        /** Sweeps the grid (every [step]th pixel), comparing hits, then feedback and repeats per zone. */
        fun check(state: String, step: Int) {
            val seen = HashMap<String, Pair<Int, Int>>() // key -> (old id, new id)
            val g = l.grid
            var gy = 0
            while (gy < g.height) {
                var gx = 0
                while (gx < g.width) {
                    val o = oldHit(gx, gy)
                    val n = stage.hitTest(gx, gy)
                    val ok = oldKey(o)
                    assertEquals("$state S=${g.scale} at ($gx,$gy)", ok, newKey(n))
                    if (o >= 0) seen.putIfAbsent(ok, o to n)
                    gx += step
                }
                gy += step
            }
            val a = IRect(); val b = IRect()
            for ((key, ids) in seen) {
                val fo = oldFeedback(ids.first, a)
                val fn = stage.feedback(ids.second, b)
                assertEquals("$state feedback? $key", fo, fn)
                if (fo) assertEquals("$state feedback $key", a, b)
                assertEquals("$state repeats $key", oldRepeats(ids.first), stage.repeats(ids.second))
            }
        }

        fun id(seat: Int) = session.state.players[seat].id
    }

    private fun sweep(w: Int, h: Int, s: Int, step: Int) {
        val r = Rig(w, h, s)
        r.check("fresh", step)

        // Drawers: an upright and a rotated seat open, one with a partner (two pages) on page 2.
        r.session.dispatch(Action.SetPartner(r.id(1), true), 0)
        r.drawer.controller.onTap(3, DrawerZones.TAB)
        r.drawer.controller.onTap(1, DrawerZones.TAB)
        r.check("drawers open", step)
        r.drawer.controller.onTap(1, DrawerZones.NEXT)
        r.drawer.controller.onTap(0, DrawerZones.TAB)
        r.check("page 2", step)

        // Commander mode, an upright then a rotated target (partner on seat 1: quarters).
        r.cmd.mode.press(3)
        assertEquals(3, r.cmd.mode.target)
        r.check("mode target 3", step)
        r.cmd.mode.exit()
        r.check("mode left", step)
        r.cmd.mode.press(0)
        r.check("mode target 0", step)
        r.cmd.mode.exit()
        assertEquals(CommanderMode.NONE, r.cmd.mode.target)
    }

    @Test fun nomadEveryPixel() = sweep(1872, 1404, 4, 1)

    @Test fun nomadOtherScales() {
        for (s in intArrayOf(2, 3, 5, 6)) sweep(1872, 1404, s, 2)
    }

    @Test fun otherScreens() {
        sweep(1404, 1872, 4, 2)
        sweep(1200, 825, 3, 1)
    }
}
