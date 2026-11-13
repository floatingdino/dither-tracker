package au.com.josephmark.dithertracker.purity

import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.stage.PlayInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rig itself (T50): scripts of any length keep acting. */
class ScreenRigTest {

    @Test fun moreTapsThanPointerIdsStillAct() {
        val rig = ScreenRig(1872, 1404, 4)
        val start = rig.session.state.players[0].life
        val n = 3 * PlayInput.MAX_POINTERS + 5
        repeat(n) { rig.tap(rig.life, 0, PlayLayout.PLUS) }
        assertEquals(start + n, rig.session.state.players[0].life)
    }

    @Test fun aHeldPressKeepsItsPointerThroughALongScript() {
        val rig = ScreenRig(1872, 1404, 4)
        rig.press(rig.life, 3, PlayLayout.PLUS)
        val start = rig.session.state.players[0].life
        val n = 2 * PlayInput.MAX_POINTERS
        repeat(n) { rig.tap(rig.life, 0, PlayLayout.PLUS) }
        assertEquals(start + n, rig.session.state.players[0].life)
        assertTrue("seat 3's press survives", rig.stage.isPressed(rig.life, 3, PlayLayout.PLUS))
        rig.cancel()
    }
}
