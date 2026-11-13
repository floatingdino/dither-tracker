package au.com.josephmark.dithertracker.core

import au.com.josephmark.dithertracker.core.Action.*
import au.com.josephmark.dithertracker.core.LogEvent.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameLogTest {
    private val s0 = GameState.create(4)
    private fun events(h: History) = h.log.map { it.event }

    @Test fun lifeEntryCarriesDeltaValueTurnAndTimestamp() {
        var h = History(s0).dispatch(SetStartingPlayer(0), 10).dispatch(NextTurn, 20)
        h = h.dispatch(ChangeLife(1, -3), 1234)
        assertEquals(LogEntry(1234, 2, LifeChanged(1, -3, 37)), h.log.last())
        assertEquals(TurnChanged(0, 1, 1), h.log[0].event)
        assertEquals(TurnChanged(1, 2, 1), h.log[1].event)
    }

    @Test fun clampedDeltaIsLogged() {
        val h = History(s0).dispatch(ChangeCounter(0, Counter.POISON, 3), 0).dispatch(ChangeCounter(0, Counter.POISON, -10), 1)
        assertEquals(CounterChanged(0, Counter.POISON, -3, 0), h.log.last().event)
    }

    @Test fun coalescedTapsBecomeOneEntry() {
        var h = History(s0)
        repeat(10) { h = h.dispatch(ChangeLife(2, -1), it.toLong(), "life:2") }
        assertEquals(listOf<LogEvent>(LifeChanged(2, -10, 30)), events(h))
        assertEquals(9L, h.log.single().timestamp)
    }

    @Test fun separateGroupsStaySeparate() {
        var h = History(s0)
        h = h.dispatch(ChangeLife(2, -1), 0, "life:2").endGroup()
        h = h.dispatch(ChangeLife(2, -1), 1, "life:2")
        assertEquals(2, h.log.size)
    }

    @Test fun commanderDamageLogsDamageAndLinkedLife() {
        val h = History(s0).dispatch(ChangeCommanderDamage(0, 3, 0, 4), 5)
        assertEquals(
            listOf(CommanderDamageChanged(0, 3, 0, 4, 4), LifeChanged(0, -4, 36, source = 3, slot = 0)),
            events(h),
        )
    }

    @Test fun coalescedCommanderDamageMergesBothEntries() {
        var h = History(s0)
        repeat(3) {
            val a = ChangeCommanderDamage(0, 3, 0, 1)
            h = h.dispatch(a, it.toLong(), a.coalesceKey)
        }
        assertEquals(
            listOf(CommanderDamageChanged(0, 3, 0, 3, 3), LifeChanged(0, -3, 37, source = 3, slot = 0)),
            events(h),
        )
    }

    @Test fun koAndReviveAreLogged() {
        var h = History(s0).dispatch(SetLife(0, 1), 0)
        h = h.dispatch(ChangeLife(0, -1), 1)
        assertEquals(KnockedOut(0, KoReason.LIFE), h.log.last().event)
        h = h.dispatch(Revive(0), 2)
        assertEquals(Revived(0), h.log.last().event)
        h = h.dispatch(ChangeCounter(1, Counter.POISON, 10), 3)
        assertEquals(KnockedOut(1, KoReason.POISON), h.log.last().event)
        h = h.dispatch(MarkDead(2), 4)
        assertEquals(KnockedOut(2, KoReason.MANUAL), h.log.last().event)
    }

    @Test fun koAndReviveWithinOneGroupCancel() {
        var h = History(s0).dispatch(SetLife(0, 1), 0)
        h = h.dispatch(ChangeLife(0, -1), 1, "life:0")
        h = h.dispatch(ChangeLife(0, 1), 2, "life:0")
        h = h.dispatch(ChangeLife(0, 1), 3, "life:0")
        assertEquals(listOf(LifeChanged(0, -39, 1), LifeChanged(0, 1, 2)), events(h))
    }

    @Test fun monarchMovingLogsBothPlayers() {
        val h = History(s0).dispatch(SetToggle(0, Toggle.MONARCH, true), 0).dispatch(SetToggle(1, Toggle.MONARCH, true), 1)
        assertEquals(
            listOf(ToggleChanged(0, Toggle.MONARCH, true), ToggleChanged(0, Toggle.MONARCH, false), ToggleChanged(1, Toggle.MONARCH, true)),
            events(h),
        )
    }

    @Test fun miscEventsAreLogged() {
        var h = History(s0)
        h = h.dispatch(Rename(0, "Sam"), 0)
        h = h.dispatch(SetPattern(0, "DOTS"), 1)
        h = h.dispatch(SetPartner(0, true), 2)
        h = h.dispatch(ChangeCommanderTax(0, Commander.PARTNER, 2), 3)
        h = h.dispatch(SetDayNight(DayNight.DAY), 4)
        h = h.dispatch(UpdateSettings(GameSettings(autoKo = false)), 5)
        h = h.dispatch(SetPlayerCount(3), 6)
        assertEquals(
            listOf(
                Renamed(0, "Player 1", "Sam"),
                PatternChanged(0, "DOTS"),
                PartnerChanged(0, true),
                CommanderTaxChanged(0, Commander.PARTNER, 2, 2),
                DayNightChanged(DayNight.DAY),
                SettingsChanged(GameSettings(autoKo = false)),
                PlayerCountChanged(3),
            ),
            events(h),
        )
        assertEquals((0L..6L).toList(), h.log.map { it.timestamp })
    }

    @Test fun newGameAndResetStartAFreshLog() {
        var h = History(s0).dispatch(ChangeLife(0, -1), 0).dispatch(NewGame, 1)
        assertEquals(listOf<LogEvent>(LogEvent.NewGameStarted), events(h))
        h = h.dispatch(ChangeLife(0, -1), 2).dispatch(Reset, 3)
        assertEquals(listOf<LogEvent>(GameReset), events(h))
    }

    @Test fun gameApplyWorksWithoutHistory() {
        val g = Game(s0).apply(ChangeLife(0, 2), 9)
        assertEquals(42, g.state.players[0].life)
        assertEquals(LogEntry(9, 0, LifeChanged(0, 2, 42)), g.log.single())
        assertTrue(Game(s0).apply(ChangeLife(5, 1), 0).log.isEmpty())
    }
}
