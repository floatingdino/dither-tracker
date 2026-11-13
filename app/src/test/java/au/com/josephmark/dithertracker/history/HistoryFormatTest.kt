package au.com.josephmark.dithertracker.history

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.GameSettings
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.History
import au.com.josephmark.dithertracker.core.KoReason
import au.com.josephmark.dithertracker.core.LogEntry
import au.com.josephmark.dithertracker.core.LogEvent
import au.com.josephmark.dithertracker.core.Toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFormatTest {

    private val state: GameState = GameState.create(4).let { s ->
        val names = arrayOf("Alice", "Bob", "Cleo", "Dev")
        s.copy(players = s.players.mapIndexed { i, p -> p.copy(name = names[i]) })
    }

    private fun row(e: LogEvent, turn: Int = 0, st: GameState = state): String =
        HistoryFormat.format(LogEntry(0, turn, e), st, HistoryRow()).toString()

    /** One sample per subtype (and per enum value where the row differs), with its expected row. */
    private val snapshots: List<Pair<LogEvent, String>> = listOf(
        LogEvent.LifeChanged(0, -7, 33) to "[life] s0 | −7 → 33",
        LogEvent.LifeChanged(1, 12, 52) to "[life] s1 | +12 → 52",
        LogEvent.CounterChanged(1, Counter.POISON, 2, 5) to "[poison] s1 | +2 → 5",
        LogEvent.CounterChanged(1, Counter.ENERGY, -1, 0) to "[energy] s1 | −1 → 0",
        LogEvent.CounterChanged(1, Counter.EXPERIENCE, 1, 1) to "[experience] s1 | +1 → 1",
        LogEvent.CounterChanged(1, Counter.RAD, 3, 3) to "[rads] s1 | +3 → 3",
        LogEvent.CounterChanged(1, Counter.STORM, 4, 4) to "[storm] s1 | +4 → 4",
        LogEvent.CounterChanged(1, Counter.TICKETS, 1, 2) to "[tickets] s1 | +1 → 2",
        LogEvent.CounterChanged(1, Counter.GENERIC, 1, 9) to "[generic-counter] s1 | +1 → 9",
        LogEvent.CommanderTaxChanged(2, Commander.PRIMARY, 2, 4) to "[commander-tax] s2 | +2 → 4",
        LogEvent.CommanderTaxChanged(2, Commander.PARTNER, 2, 2) to "[commander-tax] s2 | partner +2 → 2",
        LogEvent.CommanderDamageChanged(1, 3, Commander.PRIMARY, 7, 7) to "[commander] s3 > s1 | +7 → 7",
        LogEvent.CommanderDamageChanged(1, 3, Commander.PARTNER, -2, 4) to "[partner] s3 > s1 | −2 → 4",
        LogEvent.ToggleChanged(0, Toggle.MONARCH, true) to "[monarch] s0 | claims",
        LogEvent.ToggleChanged(0, Toggle.INITIATIVE, false) to "[initiative] s0 | loses",
        LogEvent.ToggleChanged(0, Toggle.CITYS_BLESSING, true) to "[citys-blessing] s0 | claims",
        LogEvent.ToggleChanged(0, Toggle.RING_BEARER, true) to "[ring] s0 | claims",
        LogEvent.KnockedOut(1, KoReason.LIFE) to "[dead] s1 | out of life",
        LogEvent.KnockedOut(1, KoReason.POISON) to "[dead] s1 | poisoned out",
        LogEvent.KnockedOut(1, KoReason.COMMANDER_DAMAGE) to "[dead] s1 | took 21",
        LogEvent.KnockedOut(1, KoReason.MANUAL) to "[dead] s1 | scooped",
        LogEvent.Revived(1) to "[refresh] s1 | back in",
        LogEvent.Renamed(0, "Player 1", "Alice") to "[players] s0 | renamed",
        LogEvent.PatternChanged(2, "dots") to "[invert] s2 | new look",
        LogEvent.PartnerChanged(3, true) to "[partner] s3 | partner in",
        LogEvent.PartnerChanged(3, false) to "[partner] s3 | partner out",
        LogEvent.DayNightChanged(DayNight.DAY) to "[day] Day breaks",
        LogEvent.DayNightChanged(DayNight.NIGHT) to "[night] Night falls",
        LogEvent.DayNightChanged(DayNight.UNSET) to "[day] Day/night off",
        LogEvent.TurnChanged(2, 5, 2) to "[turn] s2 | turn 5",
        LogEvent.TurnChanged(null, 3, 1) to "[turn] Turns | turn 3",
        LogEvent.TurnChanged(null, 0, 0) to "[turn] Turns | off",
        LogEvent.SettingsChanged(GameSettings(startingLife = 30)) to "[settings] Settings | changed",
        LogEvent.PlayerCountChanged(5) to "[players] Players | 5",
        LogEvent.NewGameStarted to "[new-game] New game | here we go",
        LogEvent.GameReset to "[reset] Game reset | clean slate",
    )

    @Test fun everyEventKindFormatsAsExpected() {
        for ((e, expected) in snapshots) assertEquals("$e", expected, row(e))
    }

    /**
     * Exhaustive-when guard: `HistoryFormat.format` is a `when` expression over the sealed
     * [LogEvent], so a new subtype fails compilation there; this makes the snapshot list keep up too.
     */
    @Test fun everyLogEventSubtypeHasASnapshot() {
        val declared = LogEvent::class.java.declaredClasses.filter { LogEvent::class.java.isAssignableFrom(it) }.toSet()
        val sampled = snapshots.map { it.first.javaClass }.toSet()
        assertEquals(declared.map { it.simpleName }.sorted(), sampled.map { it.simpleName }.sorted())
        assertEquals(16, declared.size)
    }

    @Test fun everyRowHasAnIconAndEitherASeatOrALabel() {
        for ((e, _) in snapshots) {
            val r = HistoryFormat.format(LogEntry(0, 0, e), state, HistoryRow())
            assertTrue("$e", r.icon != null)
            assertTrue("$e", r.hasSeat != r.who.isNotEmpty())
            assertTrue("$e icon not prewarmed", r.icon in HistoryFormat.ICONS)
            r.note?.let { n -> assertTrue("$e note not in ALL_COPY", HistoryFormat.ALL_COPY.any { it == n.toString() }) }
        }
    }

    @Test fun removedPlayersShowTheEmptyTable() {
        assertEquals("[life] s- | −1 → 39", row(LogEvent.LifeChanged(9, -1, 39)))
        assertEquals("[commander] s- > s1 | +3 → 3", row(LogEvent.CommanderDamageChanged(1, 7, 0, 3, 3)))
        assertEquals("[commander] s3 > s- | +3 → 3", row(LogEvent.CommanderDamageChanged(5, 3, 0, 3, 3)))
        assertEquals(au.com.josephmark.dithertracker.seat.SeatGlyphs.EMPTY, HistoryFormat.format(LogEntry(0, 0, LogEvent.LifeChanged(9, -1, 1)), state, HistoryRow()).seat)
        // The row is reused: formatting another row into the same object doesn't leak the old seat.
        val r = HistoryRow()
        HistoryFormat.format(LogEntry(0, 0, LogEvent.CommanderDamageChanged(1, 3, 0, 1, 1)), state, r)
        HistoryFormat.format(LogEntry(0, 0, LogEvent.LifeChanged(42, -1, 1)), state, r)
        assertEquals("[life] s- | −1 → 1", r.toString())
        HistoryFormat.format(LogEntry(0, 0, LogEvent.DayNightChanged(DayNight.DAY)), state, r)
        assertEquals("[day] Day breaks", r.toString())
    }

    @Test fun namesAreNeverShown_seatsComeFromTheCurrentState() {
        // Custom and default names alike never appear in any row (T44: names dropped).
        val named = state.copy(players = state.players.map { if (it.id == 0) it.copy(name = "Zed") else it })
        for ((e, _) in snapshots) for (st in listOf(named, GameState.create(4))) {
            val text = row(e, st = st)
            for (p in st.players) assertFalse("$text shows ${p.name}", text.contains(p.name))
        }
        assertEquals("[life] s0 | −7 → 33", row(LogEvent.LifeChanged(0, -7, 33), st = named))
        // A player's glyph is their current seat: ids and seats differ after a seat-preserving rebuild.
        val swapped = state.copy(players = state.players.mapIndexed { i, p -> p.copy(id = 10 + (3 - i)) })
        assertEquals("[life] s3 | −1 → 39", row(LogEvent.LifeChanged(10, -1, 39), st = swapped))
    }

    @Test fun linkedLifeLossFoldsIntoTheDamageRow() {
        var h = History(state)
        h = h.dispatch(Action.ChangeCommanderDamage(target = 1, source = 3, slot = 0, delta = 5), 0)
        val log = h.log
        // Core logs both halves, damage first, then the linked life change.
        assertEquals(2, log.size)
        assertTrue(log[0].event is LogEvent.CommanderDamageChanged)
        val life = log[1].event as LogEvent.LifeChanged
        assertEquals(3, life.source)
        assertFalse(HistoryFormat.isFolded(log, 0))
        assertTrue(HistoryFormat.isFolded(log, 1))
        val index = HistoryIndex().apply { update(log) }
        assertEquals(1, index.size)
        assertEquals("[commander] s3 > s1 | +5 → 5", index.fill(0, h.state, HistoryRow()).toString())
    }

    @Test fun foldSurvivesCoalescingAndInterleavedSeats() {
        var h = History(state)
        val key = "cmd"
        h = h.dispatch(Action.ChangeCommanderDamage(1, 3, 0, 1), 0, key)
        h = h.dispatch(Action.ChangeLife(0, -1), 100, "life0")
        h = h.dispatch(Action.ChangeCommanderDamage(1, 3, 0, 1), 200, key)
        h = h.dispatch(Action.ChangeCommanderDamage(1, 3, 0, 1), 300, key)
        val index = HistoryIndex().apply { update(h.log) }
        val rows = (0 until index.size).map { index.fill(it, h.state, HistoryRow()).toString() }
        assertEquals(listOf("[commander] s3 > s1 | +3 → 3", "[life] s0 | −1 → 39"), rows)
    }

    @Test fun unlinkedDamageLeavesLifeAlone_andAnUnmatchedSourcedLifeRowStays() {
        var h = History(state)
        h = h.dispatch(Action.ChangeCommanderDamage(1, 3, 0, 4, linkLife = false), 0)
        assertEquals(1, h.log.size)
        // A sourced life entry with no damage entry near it is shown as plain life.
        val lone = listOf(LogEntry(0, 0, LogEvent.LifeChanged(1, -4, 36, source = 3, slot = 0)))
        assertFalse(HistoryFormat.isFolded(lone, 0))
        assertEquals("[life] s1 | −4 → 36", row(lone[0].event))
    }

    @Test fun koFromCommanderDamageShowsDamageAndKo() {
        var h = History(state)
        h = h.dispatch(Action.ChangeCommanderDamage(1, 3, 0, 21), 0)
        val index = HistoryIndex().apply { update(h.log) }
        val rows = (0 until index.size).map { index.fill(it, h.state, HistoryRow()).toString() }
        assertEquals(listOf("[dead] s1 | took 21", "[commander] s3 > s1 | +21 → 21"), rows)
    }

    @Test fun groupTurnUsesTheTurnATurnChangeStarts() {
        assertEquals(3, HistoryFormat.groupTurn(LogEntry(0, 3, LogEvent.LifeChanged(0, -1, 39))))
        assertEquals(4, HistoryFormat.groupTurn(LogEntry(0, 3, LogEvent.TurnChanged(1, 4, 1))))
    }
}
