package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.play.PlayLayout.Companion.MINUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.PLUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.zone
import au.com.josephmark.dithertracker.stage.PlayInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T57 (owner, on the device: "±10 feels finicky if held after tapping, and sometimes holds are not
 * recognised"): the life-half hold path on the real Play screen, with every other component (readout,
 * effects, KO, clean cadence) running on the fake clock.
 */
class HoldPathTest {

    private val rig = PlayRig()
    private val clock = rig.platform.clock
    private fun life(seat: Int) = rig.session.state.players[seat].life
    private fun at(seat: Int, side: Int) = PlayRig.mid(rig.l.zones[zone(seat, side)])
    private fun zid(seat: Int, side: Int) = rig.stage.zoneId(rig.life, seat, side)

    private fun down(pointer: Int, seat: Int, side: Int, dx: Int = 0) {
        val p = at(seat, side)
        rig.stage.down(pointer, p[0] + dx, p[1], clock.now)
    }

    private fun tap(pointer: Int, seat: Int, side: Int, heldMs: Long = 60) {
        down(pointer, seat, side)
        clock.advance(heldMs)
        rig.stage.up(pointer)
    }

    @Test fun holdRightAfterTapsStepsFromItsOwnTouchDown() {
        repeat(3) { tap(0, 3, MINUS); clock.advance(90) } // quick taps, readout pending
        assertEquals(37, life(3))
        assertTrue(rig.stage.readouts.delta(3) != 0)
        down(0, 3, MINUS) // same pointer id, same half
        clock.advance(PlayInput.PRESS_DELAY_MS - 1)
        assertFalse(rig.stage.input.isPressed(zid(3, MINUS)))
        clock.advance(1)
        assertTrue("inverted at the press delay", rig.stage.input.isPressed(zid(3, MINUS)))
        clock.advance(PlayInput.HOLD_DELAY_MS - PlayInput.PRESS_DELAY_MS - 1)
        assertEquals(37, life(3))
        clock.advance(1)
        assertEquals("first step 500 ms after THIS touch-down", 27, life(3))
        clock.advance(PlayInput.REPEAT_MS)
        assertEquals(17, life(3))
        rig.stage.up(0)
        assertEquals("no ±1 on release after stepping", 17, life(3))
    }

    @Test fun holdStartedAsTheReadoutCommitsKeepsGoing() {
        tap(0, 2, PLUS)
        clock.advance(3_000 - 60 - 200) // the readout commits (3 s idle) and its effect starts mid-hold
        down(0, 2, PLUS)
        clock.advance(PlayInput.HOLD_DELAY_MS + 3 * PlayInput.REPEAT_MS)
        assertEquals(41 + 40, life(2))
        rig.stage.up(0)
        assertEquals(81, life(2))
    }

    @Test fun shortPressBelowTheHoldDelayIsExactlyOneTap() {
        tap(0, 1, PLUS, heldMs = PlayInput.HOLD_DELAY_MS - 1)
        assertEquals(41, life(1))
        clock.advance(10_000)
        assertEquals(41, life(1))
    }

    @Test fun otherSeatsTappingDoNotCancelOrRestartAHold() {
        down(0, 3, PLUS)
        clock.advance(300)
        tap(1, 0, MINUS)           // another seat taps (second pointer down + pointer up)
        tap(2, 1, PLUS, heldMs = 30)
        clock.advance(PlayInput.HOLD_DELAY_MS - 300 - 60 - 30)
        assertEquals(50, life(3))  // on time: 500 ms after its own down
        tap(1, 2, MINUS)
        clock.advance(PlayInput.REPEAT_MS - 60)
        assertEquals(60, life(3))
        rig.stage.up(0)
        assertEquals(60, life(3))
        assertEquals(39, life(0)); assertEquals(41, life(1)); assertEquals(39, life(2))
    }

    @Test fun anotherSeatsKnockOutDoesNotStopAHold() {
        down(0, 3, PLUS)
        clock.advance(PlayInput.HOLD_DELAY_MS)
        assertEquals(50, life(3))
        rig.session.changeLife(0, -40, clock.now) // seat 0 is knocked out: its KO tile (a seat cover) arrives
        clock.advance(6 * PlayInput.REPEAT_MS)    // through the flourish and the tile appearing
        assertTrue(rig.session.state.isKo(rig.id(0)))
        assertFalse("seat 0's KO tile covers its life halves", rig.stage.isVisible(rig.life, 0))
        assertEquals("seat 3 kept stepping", 110, life(3))
        rig.stage.up(0)
    }

    @Test fun aHoldThatKillsItsOwnSeatStops() {
        rig.session.changeLife(1, -25, clock.now) // 15
        clock.advance(10_000)
        down(0, 1, MINUS)
        clock.advance(PlayInput.HOLD_DELAY_MS + 6 * PlayInput.REPEAT_MS)
        assertTrue(life(1) <= 0)
        assertTrue("stopped at or just after 0", life(1) >= -15)
        rig.stage.up(0)
    }

    @Test fun aSecondFingerOnTheSameHalfDoesNotRestartTheHold() {
        down(0, 0, PLUS)
        clock.advance(400)
        down(1, 0, PLUS) // a second contact (palm, thumb) on the same half
        clock.advance(100)
        assertEquals(50, life(0))
        rig.stage.up(1)
        clock.advance(PlayInput.REPEAT_MS)
        assertEquals(60, life(0))
        rig.stage.up(0)
        assertEquals(60, life(0))
    }
}
