package au.com.josephmark.dithertracker.commander

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.play.FakeScheduler
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.stage.PlayInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CommanderModeTest {

    private val session = GameSession.newGame(4)
    private val timers = FakeScheduler(CommanderMode.TIMERS)
    private val hints = ArrayList<Int>()
    private val changes = ArrayList<Pair<Int, Int>>()
    private val dispatched = ArrayList<Pair<Int, Action.ChangeCommanderDamage>>()

    private val host = object : CommanderMode.Host {
        override val game: GameState get() = session.state
        override fun dispatchDamage(sourceSeat: Int, action: Action.ChangeCommanderDamage) {
            dispatched += sourceSeat to action
            session.dispatch(action, timers.now)
        }
        override fun onModeChanged(old: Int, new: Int) { changes += old to new }
        override fun dispatchPartner(seat: Int, on: Boolean) {
            session.dispatch(Action.SetPartner(session.state.players[seat].id, on), timers.now)
        }
        override fun onHintChanged(seat: Int) { hints += seat }
    }

    private val mode = CommanderMode(timers, host, Random(42)).also { timers.target = it }

    private fun id(seat: Int) = session.state.players[seat].id
    private fun button(seat: Int) = mode.press(seat)
    private fun tap(seat: Int, local: Int) = mode.onTap(seat, local)

    @Test fun buttonEntersSwitchesAndLeaves() {
        assertFalse(mode.active)
        button(3)
        assertEquals(3, mode.target)
        assertTrue(mode.quip in Flavour.quippy.lines(QuipKind.COMMANDER_TARGET))
        val first = mode.quip
        // another player's button switches the target (and picks a fresh line)
        button(1)
        assertEquals(1, mode.target)
        assertNotEquals(first, mode.quip)
        // the target's own button leaves
        button(1)
        assertFalse(mode.active)
        assertEquals("", mode.quip)
        assertEquals(listOf(-1 to 3, 3 to 1, 1 to -1), changes)
    }

    @Test fun targetTileAndDiamondLeave() {
        button(2)
        assertFalse(tap(1, CommanderZones.TARGET)) // not the target's tile: nothing
        assertTrue(tap(2, CommanderZones.TARGET))
        assertFalse(mode.active)
        button(0)
        assertTrue(mode.close())
        assertFalse(mode.active)
        assertFalse(mode.close()) // inactive: the diamond is the menu's
    }

    @Test fun opponentsTapTheirOwnDamageToTheTarget() {
        button(3)
        val life = session.state.players[3].life
        assertTrue(tap(2, CommanderZones.A_PLUS))
        assertTrue(tap(0, CommanderZones.A_PLUS)) // a rotated seat, from its own tile
        tap(0, CommanderZones.A_PLUS)
        assertEquals(
            Action.ChangeCommanderDamage(id(3), id(2), Commander.PRIMARY, 1, linkLife = null),
            dispatched[0].second,
        )
        assertEquals(listOf(2, 0, 0), dispatched.map { it.first })
        assertEquals(1, mode.damage(2, Commander.PRIMARY))
        assertEquals(2, mode.damage(0, Commander.PRIMARY))
        assertEquals(life - 3, session.state.players[3].life) // linked by default
        tap(0, CommanderZones.A_MINUS)
        assertEquals(1, mode.damage(0, Commander.PRIMARY))
        assertEquals(life - 2, session.state.players[3].life) // refunded
    }

    @Test fun holdRepeatsByTenLikeLifeAndTapsOnTheTargetDoNothing() {
        button(3)
        val z = CommanderZones.A_PLUS
        assertTrue(mode.repeats(1, z))
        assertFalse(mode.repeats(1, CommanderZones.TARGET))
        assertFalse(mode.repeats(3 /* the target */, CommanderZones.TARGET))
        assertEquals(PlayInput.DEFAULT_HOLD_STEP, mode.holdStep)
        repeat(2) { mode.onRepeat(1, z) } // +10, +10
        assertEquals(20, mode.damage(1, Commander.PRIMARY))
        mode.onTap(1, z)
        assertEquals(21, mode.damage(1, Commander.PRIMARY))
        mode.onRepeat(1, CommanderZones.A_MINUS)
        assertEquals(11, mode.damage(1, Commander.PRIMARY))
        mode.onTap(1, CommanderZones.A_PLUS); repeat(9) { mode.onTap(1, z) }
        assertEquals(21, mode.damage(1, Commander.PRIMARY))
        assertTrue(session.state.isKo(id(3))) // 21 from one commander: core's auto-KO rule
        assertEquals(21, session.state.players[3].maxCommanderDamage)
        // the target's own slot zones never dispatch
        assertFalse(mode.onTap(3, CommanderZones.A_PLUS))
        mode.exit()
        assertFalse(mode.repeats(1, z))
        assertFalse(mode.onTap(1, z))
    }

    @Test fun holdStepFollowsTheLifeSettingAndZeroDisablesIt() {
        button(3)
        val z = CommanderZones.A_PLUS
        mode.holdStep = 5
        mode.onRepeat(2, z)
        assertEquals(5, mode.damage(2, Commander.PRIMARY))
        mode.holdStep = 0
        assertFalse(mode.onRepeat(2, z))
        assertEquals(5, mode.damage(2, Commander.PRIMARY))
    }

    @Test fun partnerSlotNeedsAPartner() {
        button(3)
        assertFalse(tap(1, CommanderZones.B_PLUS))
        session.dispatch(Action.SetPartner(id(1), true), 0)
        assertTrue(tap(1, CommanderZones.B_PLUS))
        assertTrue(tap(1, CommanderZones.A_PLUS))
        assertTrue(tap(1, CommanderZones.B_PLUS))
        assertEquals(1, mode.damage(1, Commander.PRIMARY))
        assertEquals(2, mode.damage(1, Commander.PARTNER))
        assertEquals(Commander.PARTNER, dispatched[0].second.slot)
    }

    @Test fun idleTimeoutEndsTheModeAndActivityPostponesIt() {
        button(3)
        assertTrue(timers.isArmed(CommanderMode.TIMER))
        timers.advance(CommanderMode.IDLE_MS - 1)
        assertTrue(mode.active)
        tap(2, CommanderZones.A_PLUS) // activity re-arms
        timers.advance(CommanderMode.IDLE_MS - 1)
        assertTrue(mode.active)
        timers.advance(1)
        assertFalse(mode.active)
        assertEquals(3 to -1, changes.last())
        // leaving by hand disarms the timer
        button(2); button(2)
        assertFalse(timers.isArmed(CommanderMode.TIMER))
    }

    @Test fun quipsNeverRepeatBackToBackAndPlainIsTarget() {
        var last = ""
        repeat(30) {
            button(it % 4)
            assertNotEquals(last, mode.quip)
            last = mode.quip
        }
        mode.exit()
        mode.flavour = CommanderMode.flavour(quips = false)
        button(0)
        assertEquals("Target", mode.quip)
    }

    @Test fun localIds() {
        val seen = HashSet<Int>()
        for (local in 0 until CommanderZones.PER_SEAT) assertTrue(seen.add(local))
        for (local in intArrayOf(CommanderZones.A_MINUS, CommanderZones.A_PLUS, CommanderZones.B_MINUS, CommanderZones.B_PLUS)) assertTrue(CommanderZones.isSlot(local))
        assertFalse(CommanderZones.isSlot(CommanderZones.TARGET))
        assertEquals(CommanderZones.B_PLUS, CommanderZones.slotLocal(Commander.PARTNER, PlayLayout.PLUS))
        assertEquals(CommanderZones.A_MINUS, CommanderZones.slotLocal(Commander.PRIMARY, PlayLayout.MINUS))
        assertEquals(Commander.PARTNER, CommanderZones.slotOf(CommanderZones.B_MINUS))
        assertEquals(PlayLayout.MINUS, CommanderZones.sideOf(CommanderZones.B_MINUS))
        assertEquals(PlayLayout.PLUS, CommanderZones.sideOf(CommanderZones.A_PLUS))
    }

    // ---- T52: the PARTNER tab ---------------------------------------------------------------------

    private fun partner(seat: Int) = session.state.players[seat].partner

    @Test fun partnerTabTogglesOnAndOffWhenNothingIsAtStakeOneUndoStepEach() {
        assertFalse("inactive: nothing", mode.partnerTap(1))
        button(3)
        val steps = session.history.past.size
        assertTrue(mode.partnerTap(1))
        assertTrue(partner(1))
        assertEquals(steps + 1, session.history.past.size)
        assertFalse(mode.partnerAtStake(1))
        assertFalse("nothing at stake: a plain tap, no hold needed", mode.partnerRepeats(1))
        assertTrue(mode.partnerTap(1))
        assertFalse(partner(1))
        assertEquals(steps + 2, session.history.past.size)
        // The target can set their own partner too.
        assertTrue(mode.partnerTap(3))
        assertTrue(partner(3))
        session.undo()
        assertFalse(partner(3))
        assertTrue("the mode stays on", mode.active)
    }

    @Test fun droppingAPartnerWithDamageNeedsAHoldAndUndoRestoresIt() {
        button(3)
        mode.partnerTap(1)
        tap(1, CommanderZones.B_PLUS)
        tap(1, CommanderZones.B_PLUS)
        assertEquals(2, mode.damage(1, Commander.PARTNER))
        assertTrue(mode.partnerAtStake(1))
        val life = session.state.players[3].life
        // A tap only shows the hint (and it times out).
        assertFalse(mode.partnerTap(1))
        assertTrue(partner(1))
        assertEquals(1, mode.hint)
        assertEquals(listOf(1), hints)
        assertTrue(mode.partnerRepeats(1))
        timers.advance(CommanderMode.HINT_MS)
        assertEquals(CommanderMode.NONE, mode.hint)
        assertTrue(mode.active)
        // A hold drops it: core zeroes the partner's damage (life is not refunded).
        assertTrue(mode.partnerHold(1))
        assertFalse(partner(1))
        assertEquals(0, mode.damage(1, Commander.PARTNER))
        assertEquals(life, session.state.players[3].life)
        assertFalse("later repeats of the same hold do nothing", mode.partnerHold(1))
        assertFalse(partner(1))
        // One undo step brings partner and damage back.
        session.undo()
        assertTrue(partner(1))
        assertEquals(2, mode.damage(1, Commander.PARTNER))
    }

    @Test fun partnerTaxAlsoCountsAndLeavingClearsTheHint() {
        session.dispatch(Action.SetPartner(id(2), true), 0)
        session.dispatch(Action.ChangeCommanderTax(id(2), Commander.PARTNER, 2), 0)
        button(0)
        assertTrue(mode.partnerAtStake(2))
        mode.partnerTap(2)
        assertEquals(2, mode.hint)
        assertTrue(timers.isArmed(CommanderMode.TIMER_HINT))
        mode.exit()
        assertEquals(CommanderMode.NONE, mode.hint)
        assertFalse(timers.isArmed(CommanderMode.TIMER_HINT))
        assertFalse(timers.isArmed(CommanderMode.TIMER))
    }
}
