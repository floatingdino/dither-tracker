package au.com.josephmark.dithertracker.commander

import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.layout.CountRig
import au.com.josephmark.dithertracker.play.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T52: the PARTNER tab in commander-damage mode — one per tile on the drawer tab's spot, at every
 * player count (2–6: sideways seats, the narrow 5–6 seat tier), S = 2..6 and both orientations; and
 * driven through the real Play screen ([CountRig]): tap on / off, hold to drop recorded damage,
 * pixel purity of every state. `T52_DUMP=<dir>` writes the 4- and 6-player renders for the captures.
 */
class PartnerTabTest {

    private fun IRect.inside(o: IRect) = left >= o.left && top >= o.top && right <= o.right && bottom <= o.bottom
    private fun IRect.meets(o: IRect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    private val IRect.empty get() = right <= left || bottom <= top

    @Test fun tabGeometryAtEveryCountScaleAndOrientation() {
        for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) for (n in 2..6) for (s in 2..6) {
            val play = PlayLayout.compute(DitherGrid(w, h, s), n)
            if (!play.comfortable) continue
            val c = CommanderLayout(play)
            val tag = "${w}x$h ${n}p S=$s"
            if (s <= 4 && w > h) assertTrue("$tag: the word fits on the Nomad's scales", c.partnerLabelShown)
            assertTrue("$tag: icon or word", c.partnerIconShown || c.partnerLabelShown)
            val icon = IRect()
            val label = IRect()
            for (seat in 0 until n) {
                val tab = c.partnerTab[seat]
                assertTrue("$tag seat $seat tab in panel", tab.inside(play.panels[seat]))
                assertTrue(tab.inside(c.partnerHit[seat]))
                for (text in listOf(CommanderLayout.PARTNER_LABEL, CommanderLayout.HOLD_LABEL)) {
                    c.partnerContent(seat, text.length, icon, label)
                    if (c.partnerIconShown) assertTrue("$tag seat $seat icon $icon in $tab", icon.inside(tab))
                    if (c.partnerLabelShown) assertTrue("$tag seat $seat '$text' $label in $tab", label.inside(tab))
                    if (!icon.empty && !label.empty) assertFalse(icon.meets(label))
                }
                // Clear of what the mode shows: the number slot, the partner icons, the target block.
                assertFalse("$tag seat $seat", c.partnerHit[seat].meets(play.numberSlot[seat]))
                for (slot in 0..1) assertFalse("$tag seat $seat partner icon", tab.meets(c.partnerIcon[seat * 2 + slot]))
                assertFalse("$tag seat $seat target block", c.lifeRow[seat].meets(c.partnerHit[seat]))
            }
        }
    }

    @Test fun tapHoldAndPurityThroughTheRealScreenAtEveryCount() {
        val dump = System.getenv("T52_DUMP")
        for (n in 2..6) {
            val r = CountRig(1872, 1404, 4, n)
            r.prewarm()
            val errs = ArrayList<String>()
            val target = 0
            r.cmd.mode.press(target)
            errs += r.check("partner-off")
            if (dump != null && (n == 4 || n == 6)) r.render(false).writePng(File(dump, "t52-partner-${n}p-off.png"))
            // Turn it on for every opponent (and the target), through a tap on the tab.
            for (seat in 0 until n) r.tap(r.cmd, seat, CommanderZones.PARTNER)
            for (seat in 0 until n) assertTrue("${n}p seat $seat", r.session.state.players[seat].partner)
            errs += r.check("partner-on")
            // Partner damage from seat 1, then a tap only says HOLD.
            r.tap(r.cmd, 1, CommanderZones.B_PLUS)
            r.tap(r.cmd, 1, CommanderZones.B_PLUS)
            r.tap(r.cmd, 1, CommanderZones.PARTNER)
            assertTrue(r.session.state.players[1].partner)
            assertEquals(1, r.cmd.mode.hint)
            errs += r.check("partner-hold-hint")
            if (dump != null && (n == 4 || n == 6)) r.render(false).writePng(File(dump, "t52-partner-${n}p-on.png"))
            // Press: the tab inverts.
            val p = r.pointOf(r.cmd, n - 1, CommanderZones.PARTNER)!!
            r.stage.down(0, p[0], p[1], r.now)
            errs += r.check("partner-pressed")
            if (dump != null && n == 4) r.render(false).writePng(File(dump, "t52-partner-4p-pressed.png"))
            r.stage.cancel()
            // Hold seat 1's tab: the first repeat drops the partner and its damage.
            val q = r.pointOf(r.cmd, 1, CommanderZones.PARTNER)!!
            r.stage.down(0, q[0], q[1], r.now)
            r.advance(600)
            r.stage.up(0)
            assertFalse("${n}p hold drops it", r.session.state.players[1].partner)
            assertEquals(0, r.cmd.mode.damage(1, Commander.PARTNER))
            r.session.undo()
            assertTrue("${n}p undo brings it back", r.session.state.players[1].partner)
            assertEquals(2, r.cmd.mode.damage(1, Commander.PARTNER))
            assertTrue(errs.joinToString("\n"), errs.isEmpty())
        }
    }
}
