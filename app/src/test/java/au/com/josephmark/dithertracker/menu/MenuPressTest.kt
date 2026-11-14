package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.chrome.MenuDiamond
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.purity.ScreenRig
import au.com.josephmark.dithertracker.stage.Stage
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T51 (Sam: "make the menu tap/hover colour the menu ground only, rather than painting a larger
 * square"): every press in the menu inverts only the pressed item's own shape — the centre diamond,
 * a facet's diamond inside the gem, the close X, the confirm's buttons and fold diamond — never a
 * pixel outside it. Checked on the rendered raster (in-place inversion, as on the Nomad) at every
 * player count and S = 2..6, landscape and portrait.
 */
class MenuPressTest {

    private fun diff(a: ByteArray, b: ByteArray, w: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (i in a.indices) if (a[i] != b[i]) out += intArrayOf(i % w, i / w)
        return out
    }

    private fun pressDiff(rig: ScreenRig, x: Int, y: Int): List<IntArray> {
        val before = rig.render(false).px.copyOf()
        rig.pressAt(x, y)
        val after = rig.render(false).px.copyOf()
        rig.cancel()
        return diff(before, after, rig.r.width)
    }

    private fun check(tag: String, changed: List<IntArray>, inside: (Int, Int) -> Boolean) {
        assertTrue("$tag: the press shows", changed.isNotEmpty())
        val bad = changed.firstOrNull { !inside(it[0], it[1]) }
        assertTrue("$tag: ${changed.count { !inside(it[0], it[1]) }} px outside the shape, first at ${bad?.toList()}", bad == null)
    }

    @Test fun everyMenuPressStaysInsideItsShape() {
        for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (s in 2..6) for (n in 2..6) {
            val rig = ScreenRig(w, h, s, players = n)
            val l = rig.l
            if (!l.comfortable) continue
            val tag = "${w}x$h S=$s ${n}p"
            // The closed centre diamond: its stepped shape, not its bounding square.
            check("$tag diamond", pressDiff(rig, l.diamondCx, l.diamondCy)) { x, y ->
                val hw = l.diamondRowHalf(y - l.diamondCy)
                hw >= 0 && Math.abs(x - l.diamondCx) <= hw
            }
            rig.dispatch(Action.ChangeLife(rig.id(0), -1)) // something to undo
            rig.advance(10_000)
            rig.openMenu()
            val m = rig.menu
            val g = m.gem!!
            assertTrue(m.state.isOpen)
            for (slot in 0 until GemLayout.SLOTS) {
                if (!m.enabled[slot]) continue
                check("$tag facet $slot", pressDiff(rig, g.facetX[slot], g.facetY[slot])) { x, y ->
                    g.onFacet(slot, x, y) && g.inGem(x, y)
                }
            }
            // The confirm: buttons inside their outlines, the fold's close diamond in its shape.
            rig.tapAt(g.facetX[GemLayout.slotOf(MenuItem.NEW)], g.facetY[GemLayout.slotOf(MenuItem.NEW)])
            assertTrue(m.state.phase == MenuState.CONFIRM)
            for (b in listOf(g.cancelBtn, g.confirmBtn)) {
                check("$tag button $b", pressDiff(rig, (b.left + b.right) / 2, b.top + 1)) { x, y ->
                    x >= b.left && x < b.right && y >= b.top && y < b.bottom
                }
            }
            check("$tag fold", pressDiff(rig, g.cx, g.cy)) { x, y -> g.onFoldDiamond(x, y) }
            assertTrue(rig.stage.zoneOwner(rig.stage.hitTest(g.cx, g.cy)) === m)
            assertTrue(rig.one<MenuDiamond>().visible(Stage.SCREEN))
        }
    }
}
