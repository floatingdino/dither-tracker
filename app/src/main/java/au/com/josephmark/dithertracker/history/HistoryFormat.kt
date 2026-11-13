package au.com.josephmark.dithertracker.history

import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.KoReason
import au.com.josephmark.dithertracker.core.LogEntry
import au.com.josephmark.dithertracker.core.LogEvent
import au.com.josephmark.dithertracker.core.PlayerId
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.widgets.CharBuf

/**
 * `LogEntry` → [HistoryRow] (T18). Pure and allocation-free: names are references to the current
 * players' `String`s (entries hold ids, not names), a removed player falls back to "P<id + 1>"
 * written into the row's own buffer, and every other text is a constant.
 *
 * Every [LogEvent] subtype goes through one exhaustive `when` *expression* in [format], so a new
 * subtype fails compilation here until it has a row.
 *
 * **Folding rule.** With commander damage linked to life, core logs one action as two events —
 * `CommanderDamageChanged(target, source, slot)` and `LifeChanged(target, source = source, slot = slot)` —
 * next to each other (same undo group, merged in place). The damage row is the one shown (source ›
 * target, damage delta → total); the matching life row is folded away ([isFolded]). A life change
 * carrying a source with no damage row nearby (never seen in practice) is shown as a plain life row.
 */
object HistoryFormat {

    /** How far either side of a linked life entry its damage entry may sit (one action logs ≤ 4 events). */
    const val FOLD_WINDOW = 4

    // Copy (SPEC "Tone": short, a smidge combative, but plainer than the KO tile's quips, which say
    // the funny thing already). All fit the `ui` face.
    const val CLAIMS = "claims"
    const val LOSES = "loses"
    const val KO_LIFE = "out of life"
    const val KO_POISON = "poisoned out"
    const val KO_COMMANDER = "took 21"
    const val KO_MANUAL = "scooped"
    const val REVIVED = "back in"
    const val NEW_LOOK = "new look"
    const val PARTNER_IN = "partner in"
    const val PARTNER_OUT = "partner out"
    const val PARTNER = "partner"
    const val DAY = "Day breaks"
    const val NIGHT = "Night falls"
    const val DAY_NIGHT_OFF = "Day/night off"
    const val TURN = "turn"
    const val TURNS = "Turns"
    const val TURNS_OFF = "off"
    const val SETTINGS = "Settings"
    const val CHANGED = "changed"
    const val PLAYERS = "Players"
    const val NEW_GAME = "New game"
    const val HERE_WE_GO = "here we go"
    const val RESET = "Game reset"
    const val CLEAN_SLATE = "clean slate"

    /** Every constant above, for prewarming glyphs. */
    val ALL_COPY = arrayOf(
        CLAIMS, LOSES, KO_LIFE, KO_POISON, KO_COMMANDER, KO_MANUAL, REVIVED, NEW_LOOK, PARTNER_IN, PARTNER_OUT,
        PARTNER, DAY, NIGHT, DAY_NIGHT_OFF, TURN, TURNS, TURNS_OFF, SETTINGS, CHANGED, PLAYERS, NEW_GAME,
        HERE_WE_GO, RESET, CLEAN_SLATE, HistoryRow.TURN, HistoryRow.PREGAME,
    )

    /** Every icon a row can show, for prewarming. */
    val ICONS = arrayOf(
        Icon.LIFE, Icon.POISON, Icon.ENERGY, Icon.EXPERIENCE, Icon.RADS, Icon.STORM, Icon.TICKETS,
        Icon.GENERIC_COUNTER, Icon.COMMANDER_TAX, Icon.COMMANDER, Icon.PARTNER, Icon.MONARCH, Icon.INITIATIVE,
        Icon.CITYS_BLESSING, Icon.RING, Icon.DEAD, Icon.REFRESH, Icon.PLAYERS, Icon.INVERT, Icon.DAY, Icon.NIGHT,
        Icon.TURN, Icon.SETTINGS, Icon.NEW_GAME, Icon.RESET,
    )

    /** Fills [out] with [entry]'s row, naming players from [state]. Returns [out]. */
    fun format(entry: LogEntry, state: GameState, out: HistoryRow): HistoryRow {
        out.clear()
        val done: HistoryRow = when (val e = entry.event) {
            is LogEvent.LifeChanged -> {
                out.icon = Icon.LIFE
                out.who = name(state, e.player, out.nameBuf)
                out.setDelta(e.delta, e.value)
            }
            is LogEvent.CounterChanged -> {
                out.icon = counterIcon(e.counter)
                out.who = name(state, e.player, out.nameBuf)
                out.setDelta(e.delta, e.value)
            }
            is LogEvent.CommanderTaxChanged -> {
                out.icon = Icon.COMMANDER_TAX
                out.who = name(state, e.player, out.nameBuf)
                if (e.slot == Commander.PARTNER) out.note = PARTNER
                out.setDelta(e.delta, e.value)
            }
            is LogEvent.CommanderDamageChanged -> {
                out.icon = if (e.slot == Commander.PARTNER) Icon.PARTNER else Icon.COMMANDER
                out.who = name(state, e.source, out.nameBuf)
                out.who2 = name(state, e.player, out.nameBuf2)
                out.setDelta(e.delta, e.value)
            }
            is LogEvent.ToggleChanged -> {
                out.icon = toggleIcon(e.toggle)
                out.who = name(state, e.player, out.nameBuf)
                out.note = if (e.on) CLAIMS else LOSES
                out
            }
            is LogEvent.KnockedOut -> {
                out.icon = Icon.DEAD
                out.who = name(state, e.player, out.nameBuf)
                out.note = koNote(e.reason)
                out
            }
            is LogEvent.Revived -> {
                out.icon = Icon.REFRESH
                out.who = name(state, e.player, out.nameBuf)
                out.note = REVIVED
                out
            }
            is LogEvent.Renamed -> {
                out.icon = Icon.PLAYERS
                out.who = e.from
                out.who2 = e.to
                out
            }
            is LogEvent.PatternChanged -> {
                out.icon = Icon.INVERT
                out.who = name(state, e.player, out.nameBuf)
                out.note = NEW_LOOK
                out
            }
            is LogEvent.PartnerChanged -> {
                out.icon = Icon.PARTNER
                out.who = name(state, e.player, out.nameBuf)
                out.note = if (e.partner) PARTNER_IN else PARTNER_OUT
                out
            }
            is LogEvent.DayNightChanged -> {
                out.icon = if (e.value == DayNight.NIGHT) Icon.NIGHT else Icon.DAY
                out.who = when (e.value) {
                    DayNight.DAY -> DAY
                    DayNight.NIGHT -> NIGHT
                    DayNight.UNSET -> DAY_NIGHT_OFF
                }
                out
            }
            is LogEvent.TurnChanged -> {
                out.icon = Icon.TURN
                val active = e.activePlayer
                if (e.number <= 0) {
                    out.who = TURNS
                    out.note = TURNS_OFF
                } else {
                    out.who = if (active != null) name(state, active, out.nameBuf) else TURNS
                    out.note = TURN
                    out.setValue(e.number)
                }
                out
            }
            is LogEvent.SettingsChanged -> {
                out.icon = Icon.SETTINGS
                out.who = SETTINGS
                out.note = CHANGED
                out
            }
            is LogEvent.PlayerCountChanged -> {
                out.icon = Icon.PLAYERS
                out.who = PLAYERS
                out.setValue(e.count)
            }
            LogEvent.NewGameStarted -> {
                out.icon = Icon.NEW_GAME
                out.who = NEW_GAME
                out.note = HERE_WE_GO
                out
            }
            LogEvent.GameReset -> {
                out.icon = Icon.RESET
                out.who = RESET
                out.note = CLEAN_SLATE
                out
            }
        }
        return done
    }

    /** The player's current name, or "P<id + 1>" (written into [buf]) for a player no longer seated. */
    fun name(state: GameState, id: PlayerId, buf: CharBuf): CharSequence {
        val ps = state.players
        for (i in ps.indices) if (ps[i].id == id) return ps[i].name
        return buf.clear().append('P').append(id + 1)
    }

    /**
     * True when `log[i]` is the life half of a linked commander-damage pair (see the class doc): a
     * `LifeChanged` with a source, and the matching `CommanderDamageChanged` within [FOLD_WINDOW].
     */
    fun isFolded(log: List<LogEntry>, i: Int): Boolean {
        val e = log[i].event as? LogEvent.LifeChanged ?: return false
        val src = e.source ?: return false
        val from = maxOf(0, i - FOLD_WINDOW)
        val to = minOf(log.size - 1, i + FOLD_WINDOW)
        for (j in from..to) {
            if (j == i) continue
            val d = log[j].event as? LogEvent.CommanderDamageChanged ?: continue
            if (d.player == e.player && d.source == src && d.slot == (e.slot ?: Commander.PRIMARY)) return true
        }
        return false
    }

    /**
     * The turn an entry is grouped under: the entry's own `turn` (the turn it happened in), except a
     * `TurnChanged`, which opens the turn it starts.
     */
    fun groupTurn(entry: LogEntry): Int {
        val e = entry.event
        return if (e is LogEvent.TurnChanged) e.number else entry.turn
    }

    fun counterIcon(c: Counter): Icon = when (c) {
        Counter.POISON -> Icon.POISON
        Counter.ENERGY -> Icon.ENERGY
        Counter.EXPERIENCE -> Icon.EXPERIENCE
        Counter.RAD -> Icon.RADS
        Counter.STORM -> Icon.STORM
        Counter.TICKETS -> Icon.TICKETS
        Counter.GENERIC -> Icon.GENERIC_COUNTER
    }

    fun toggleIcon(t: Toggle): Icon = when (t) {
        Toggle.MONARCH -> Icon.MONARCH
        Toggle.INITIATIVE -> Icon.INITIATIVE
        Toggle.CITYS_BLESSING -> Icon.CITYS_BLESSING
        Toggle.RING_BEARER -> Icon.RING
    }

    fun koNote(r: KoReason): String = when (r) {
        KoReason.LIFE -> KO_LIFE
        KoReason.POISON -> KO_POISON
        KoReason.COMMANDER_DAMAGE -> KO_COMMANDER
        KoReason.MANUAL -> KO_MANUAL
    }
}

/**
 * Which log entry (or turn divider) each display row shows, newest first (T18). Rebuilt only when the
 * log object changes — O(log size), ints only; the arrays grow by doubling, so a long game allocates a
 * handful of times in total and never on a page turn or a draw.
 *
 * Row r is `log[entryAt(r)]`, or a divider for [dividerTurn] when [isDivider]. Folded entries
 * ([HistoryFormat.isFolded]) get no row. Dividers appear only when turn tracking has been used in
 * this log (some entry is grouped under a turn > 0): one heads each run of rows from the same turn.
 */
class HistoryIndex(initialCapacity: Int = 64) {
    private var rows = IntArray(initialCapacity)

    var size = 0
        private set

    /** The log the rows were built from (identity). */
    var log: List<LogEntry>? = null
        private set

    var grouped = false
        private set

    /** Rebuilds from [log] unless it is the same object; returns true when rebuilt. */
    fun update(log: List<LogEntry>): Boolean {
        if (log === this.log) return false
        this.log = log
        size = 0
        grouped = false
        for (i in log.indices) if (HistoryFormat.groupTurn(log[i]) > 0) { grouped = true; break }
        var prevTurn = Int.MIN_VALUE
        for (i in log.size - 1 downTo 0) {
            if (HistoryFormat.isFolded(log, i)) continue
            if (grouped) {
                val t = HistoryFormat.groupTurn(log[i])
                if (t != prevTurn) {
                    push(-(t + 1))
                    prevTurn = t
                }
            }
            push(i)
        }
        return true
    }

    /** Forgets the log (the next [update] rebuilds). */
    fun reset() {
        log = null
        size = 0
    }

    fun isDivider(row: Int): Boolean = rows[row] < 0
    fun dividerTurn(row: Int): Int = -rows[row] - 1
    fun entryAt(row: Int): Int = rows[row]

    /** Fills [out] with display row [row] of the current log. */
    fun fill(row: Int, state: GameState, out: HistoryRow): HistoryRow {
        val v = rows[row]
        if (v < 0) return out.setDivider(-v - 1)
        return HistoryFormat.format(log!![v], state, out)
    }

    private fun push(v: Int) {
        if (size == rows.size) rows = rows.copyOf(rows.size * 2)
        rows[size++] = v
    }
}
