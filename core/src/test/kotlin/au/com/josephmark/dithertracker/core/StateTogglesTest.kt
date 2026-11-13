package au.com.josephmark.dithertracker.core

import au.com.josephmark.dithertracker.core.Action.*
import au.com.josephmark.dithertracker.core.LogEvent.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** T14: state toggles — exclusivity, KO release, day/night, undo/redo and the log. */
class StateTogglesTest {
    private val s0 = GameState.create(4)
    private fun events(h: History) = h.log.map { it.event }

    @Test fun exclusiveToggleCanBeNobodys() {
        var s = reduce(s0, SetToggle(1, Toggle.MONARCH, true))
        s = reduce(s, SetToggle(1, Toggle.MONARCH, false))
        assertTrue(s.players.none { it.toggles.monarch })
        assertSame(s, reduce(s, SetToggle(2, Toggle.MONARCH, false)), "taking it from someone who hasn't got it is a no-op")
    }

    @Test fun claimingMovesItAndTouchesOnlyTheTwoPlayers() {
        val s = reduce(s0, SetToggle(0, Toggle.INITIATIVE, true))
        val t = reduce(s, SetToggle(3, Toggle.INITIATIVE, true))
        assertEquals(listOf(false, false, false, true), t.players.map { it.toggles.initiative })
        assertSame(s.players[1], t.players[1])
        assertSame(s.players[2], t.players[2])
    }

    @Test fun koDropsMonarchAndInitiativeToNobody() {
        var s = reduce(s0, SetToggle(2, Toggle.MONARCH, true))
        s = reduce(s, SetToggle(2, Toggle.INITIATIVE, true))
        s = reduce(s, SetToggle(2, Toggle.CITYS_BLESSING, true))
        s = reduce(s, SetToggle(2, Toggle.RING_BEARER, true))
        val dead = reduce(s, SetLife(2, 0))
        val t = dead.players[2].toggles
        assertFalse(t.monarch)
        assertFalse(t.initiative)
        assertTrue(t.citysBlessing, "non-exclusive toggles stay (the KO tile hides them)")
        assertTrue(t.ringBearer)
        assertTrue(dead.players.none { it.toggles.monarch || it.toggles.initiative }, "nobody's: the app can't know who gets it")
        for (i in listOf(0, 1, 3)) assertSame(s.players[i], dead.players[i])
    }

    @Test fun everyKoReasonReleases() {
        val crowned = reduce(s0, SetToggle(1, Toggle.MONARCH, true))
        val kos = listOf(
            SetCounter(1, Counter.POISON, 10),
            ChangeCommanderDamage(1, 0, Commander.PRIMARY, 21, linkLife = false),
            MarkDead(1),
            ChangeLife(1, -40),
        )
        for (a in kos) assertFalse(reduce(crowned, a).players[1].toggles.monarch, "$a")
    }

    @Test fun reviveDoesNotGiveItBack() {
        var s = reduce(s0, SetToggle(0, Toggle.MONARCH, true))
        s = reduce(s, MarkDead(0))
        s = reduce(s, Revive(0))
        assertFalse(s.isKo(0))
        assertFalse(s.players[0].toggles.monarch)
    }

    @Test fun aKoPlayerCannotClaimAnExclusiveToggle() {
        val s = reduce(s0, MarkDead(3))
        assertSame(s, reduce(s, SetToggle(3, Toggle.MONARCH, true)))
        assertSame(s, reduce(s, SetToggle(3, Toggle.INITIATIVE, true)))
        assertTrue(reduce(s, SetToggle(3, Toggle.CITYS_BLESSING, true)).players[3].toggles.citysBlessing)
    }

    @Test fun lethalChangeOnAnotherPlayerLeavesTheHolderAlone() {
        val s = reduce(s0, SetToggle(0, Toggle.MONARCH, true))
        val t = reduce(s, SetLife(1, 0))
        assertTrue(t.players[0].toggles.monarch)
        assertSame(s.players[0], t.players[0])
    }

    @Test fun autoKoTurnedOnReleasesTheDead() {
        var s = reduce(s0, UpdateSettings(GameSettings(autoKo = false)))
        s = reduce(s, SetToggle(0, Toggle.MONARCH, true))
        s = reduce(s, SetLife(0, 0))
        assertTrue(s.players[0].toggles.monarch, "alive while auto-KO is off")
        s = reduce(s, UpdateSettings(GameSettings(autoKo = true)))
        assertFalse(s.players[0].toggles.monarch)
    }

    @Test fun dayNightHasThreeStatesAndNewGameClearsIt() {
        var s = reduce(s0, SetDayNight(DayNight.DAY))
        assertEquals(DayNight.DAY, s.dayNight)
        assertSame(s.players, reduce(s, SetDayNight(DayNight.NIGHT)).players, "global: no player changes")
        s = reduce(s, SetDayNight(DayNight.NIGHT))
        assertSame(s, reduce(s, SetDayNight(DayNight.NIGHT)))
        assertEquals(DayNight.UNSET, reduce(s, NewGame).dayNight)
        assertEquals(DayNight.UNSET, reduce(s, SetDayNight(DayNight.UNSET)).dayNight)
    }

    @Test fun newGameClearsEveryToggle() {
        var s = s0
        for (t in Toggle.entries) s = reduce(s, SetToggle(1, t, true))
        assertEquals(Toggles(), reduce(s, NewGame).players[1].toggles)
    }

    @Test fun undoRedoOfAClaimAndOfAKoRelease() {
        var h = History(s0).dispatch(SetToggle(0, Toggle.MONARCH, true), 1)
        h = h.dispatch(SetToggle(2, Toggle.MONARCH, true), 2)
        assertTrue(h.state.players[2].toggles.monarch)
        h = h.undo()
        assertTrue(h.state.players[0].toggles.monarch, "undoing a claim gives it back to the old holder")
        assertFalse(h.state.players[2].toggles.monarch)
        h = h.redo()
        assertTrue(h.state.players[2].toggles.monarch)
        assertFalse(h.state.players[0].toggles.monarch)

        h = h.dispatch(MarkDead(2), 3)
        assertTrue(h.state.players.none { it.toggles.monarch })
        h = h.undo()
        assertTrue(h.state.players[2].toggles.monarch, "undoing the KO restores the crown with it")
        assertFalse(h.state.isKo(2))
    }

    @Test fun undoingALethalTapGroupRestoresTheCrown() {
        var h = History(reduce(s0, SetLife(1, 2))).dispatch(SetToggle(1, Toggle.INITIATIVE, true), 0)
        repeat(3) { h = h.dispatch(ChangeLife(1, -1), 10L + it, "life:1") }
        assertFalse(h.state.players[1].toggles.initiative)
        h = h.undo()
        assertEquals(2, h.state.players[1].life)
        assertTrue(h.state.players[1].toggles.initiative)
    }

    @Test fun logLinesForClaimsMovesKoReleaseAndDayNight() {
        var h = History(s0).dispatch(SetToggle(0, Toggle.MONARCH, true), 1)
        h = h.dispatch(SetToggle(1, Toggle.MONARCH, true), 2)
        h = h.dispatch(SetToggle(1, Toggle.RING_BEARER, true), 3)
        h = h.dispatch(SetDayNight(DayNight.DAY), 4)
        h = h.dispatch(MarkDead(1), 5)
        h = h.dispatch(SetDayNight(DayNight.NIGHT), 6)
        assertEquals(
            listOf(
                ToggleChanged(0, Toggle.MONARCH, true),
                ToggleChanged(0, Toggle.MONARCH, false),
                ToggleChanged(1, Toggle.MONARCH, true),
                ToggleChanged(1, Toggle.RING_BEARER, true),
                DayNightChanged(DayNight.DAY),
                ToggleChanged(1, Toggle.MONARCH, false),
                KnockedOut(1, KoReason.MANUAL),
                DayNightChanged(DayNight.NIGHT),
            ),
            events(h),
        )
    }

    @Test fun togglesAreNeverCoalesced() {
        assertEquals(null, SetToggle(0, Toggle.MONARCH, true).coalesceKey)
        assertEquals(null, SetDayNight(DayNight.DAY).coalesceKey)
    }
}
