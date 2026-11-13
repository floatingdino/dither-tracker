package au.com.josephmark.dithertracker.core

/**
 * One line of the game history. [turn] is [TurnState.number] when the change happened
 * (0 = turn tracking not started); [timestamp] is whatever the caller passed (epoch millis).
 * Player names are not captured: resolve [PlayerId]s against the current state.
 */
data class LogEntry(val timestamp: Long, val turn: Int, val event: LogEvent)

/** Structured log events. Deltas are the change actually applied (after clamping); values are the result. */
sealed interface LogEvent {
    /** [source]/[slot] are set when the life change came from linked commander damage. */
    data class LifeChanged(
        val player: PlayerId,
        val delta: Int,
        val value: Int,
        val source: PlayerId? = null,
        val slot: CommanderSlot? = null,
    ) : LogEvent

    data class CounterChanged(val player: PlayerId, val counter: Counter, val delta: Int, val value: Int) : LogEvent
    data class CommanderTaxChanged(val player: PlayerId, val slot: CommanderSlot, val delta: Int, val value: Int) : LogEvent

    data class CommanderDamageChanged(
        val player: PlayerId,
        val source: PlayerId,
        val slot: CommanderSlot,
        val delta: Int,
        val value: Int,
    ) : LogEvent

    data class ToggleChanged(val player: PlayerId, val toggle: Toggle, val on: Boolean) : LogEvent
    data class KnockedOut(val player: PlayerId, val reason: KoReason) : LogEvent
    data class Revived(val player: PlayerId) : LogEvent
    data class Renamed(val player: PlayerId, val from: String, val to: String) : LogEvent
    data class PatternChanged(val player: PlayerId, val pattern: String) : LogEvent
    data class PartnerChanged(val player: PlayerId, val partner: Boolean) : LogEvent
    data class DayNightChanged(val value: DayNight) : LogEvent
    data class TurnChanged(val activePlayer: PlayerId?, val number: Int, val round: Int) : LogEvent
    data class SettingsChanged(val settings: GameSettings) : LogEvent
    data class PlayerCountChanged(val count: Int) : LogEvent
    data object NewGameStarted : LogEvent
    data object GameReset : LogEvent
}

/** Log entries describing the change [before] → [after] caused by [action]. */
internal fun describe(before: GameState, after: GameState, action: Action, timestamp: Long): List<LogEntry> {
    val out = ArrayList<LogEntry>(4)
    fun add(e: LogEvent) = out.add(LogEntry(timestamp, before.turn.number, e))

    when (action) {
        Action.NewGame -> return listOf(LogEntry(timestamp, 0, LogEvent.NewGameStarted))
        Action.Reset -> return listOf(LogEntry(timestamp, 0, LogEvent.GameReset))
        is Action.SetPlayerCount -> add(LogEvent.PlayerCountChanged(after.players.size))
        else -> Unit
    }
    val cmd = action as? Action.ChangeCommanderDamage

    for (p in after.players) {
        val o = before.player(p.id) ?: continue
        if (o === p) continue
        val id = p.id
        if (o.name != p.name) add(LogEvent.Renamed(id, o.name, p.name))
        if (o.pattern != p.pattern) add(LogEvent.PatternChanged(id, p.pattern))
        if (o.partner != p.partner) add(LogEvent.PartnerChanged(id, p.partner))
        for (src in (o.commanderDamage.keys + p.commanderDamage.keys).sorted()) {
            for (slot in 0..1) {
                val a = o.commanderDamageFrom(src, slot)
                val b = p.commanderDamageFrom(src, slot)
                if (a != b) add(LogEvent.CommanderDamageChanged(id, src, slot, b - a, b))
            }
        }
        if (o.life != p.life) {
            val linked = cmd != null && cmd.target == id
            add(LogEvent.LifeChanged(id, p.life - o.life, p.life, cmd?.source?.takeIf { linked }, cmd?.slot?.takeIf { linked }))
        }
        if (o.counters != p.counters) for (c in Counter.entries) {
            val a = o.counters[c]
            val b = p.counters[c]
            if (a != b) add(LogEvent.CounterChanged(id, c, b - a, b))
        }
        for (slot in 0..1) {
            val a = o.commanderTax[slot]
            val b = p.commanderTax[slot]
            if (a != b) add(LogEvent.CommanderTaxChanged(id, slot, b - a, b))
        }
        if (o.toggles != p.toggles) for (t in Toggle.entries) {
            if (o.toggles[t] != p.toggles[t]) add(LogEvent.ToggleChanged(id, t, p.toggles[t]))
        }
    }
    for (p in after.players) {
        val o = before.player(p.id) ?: continue
        val was = before.koReason(o)
        val now = after.koReason(p)
        if (was == null && now != null) add(LogEvent.KnockedOut(p.id, now))
        if (was != null && now == null) add(LogEvent.Revived(p.id))
    }
    if (before.dayNight != after.dayNight) add(LogEvent.DayNightChanged(after.dayNight))
    if (before.turn != after.turn) {
        add(LogEvent.TurnChanged(after.turn.activePlayer, after.turn.number, after.turn.round))
    }
    if (before.settings != after.settings) add(LogEvent.SettingsChanged(after.settings))
    return out
}

/**
 * Appends [entries] to [log], merging each into a matching entry at index >= [groupStart]
 * (same subject: player + counter/slot/source/toggle). Deltas add up; a merged delta of 0 drops
 * the entry. Merged entries take the newest timestamp.
 */
internal fun mergeInto(log: List<LogEntry>, groupStart: Int, entries: List<LogEntry>): List<LogEntry> {
    if (entries.isEmpty()) return log
    val out = log.toMutableList()
    for (e in entries) {
        val i = (groupStart until out.size).firstOrNull { sameSubject(out[it].event, e.event) }
        if (i == null) {
            out += e
            continue
        }
        val merged = merge(out[i].event, e.event)
        if (merged == null) out.removeAt(i) else out[i] = out[i].copy(timestamp = e.timestamp, event = merged)
    }
    return out
}

private fun sameSubject(a: LogEvent, b: LogEvent): Boolean = when (a) {
    is LogEvent.LifeChanged -> b is LogEvent.LifeChanged && a.player == b.player && a.source == b.source && a.slot == b.slot
    is LogEvent.CounterChanged -> b is LogEvent.CounterChanged && a.player == b.player && a.counter == b.counter
    is LogEvent.CommanderTaxChanged -> b is LogEvent.CommanderTaxChanged && a.player == b.player && a.slot == b.slot
    is LogEvent.CommanderDamageChanged ->
        b is LogEvent.CommanderDamageChanged && a.player == b.player && a.source == b.source && a.slot == b.slot
    is LogEvent.ToggleChanged -> b is LogEvent.ToggleChanged && a.player == b.player && a.toggle == b.toggle
    // A KO and a revive of the same player within one group cancel out.
    is LogEvent.KnockedOut -> b is LogEvent.Revived && a.player == b.player
    is LogEvent.Revived -> b is LogEvent.KnockedOut && a.player == b.player
    else -> false
}

/** Null means the two cancel out. */
private fun merge(a: LogEvent, b: LogEvent): LogEvent? = when (a) {
    is LogEvent.LifeChanged -> (b as LogEvent.LifeChanged).let { a.copy(delta = a.delta + it.delta, value = it.value) }
        .takeIf { it.delta != 0 }
    is LogEvent.CounterChanged -> (b as LogEvent.CounterChanged).let { a.copy(delta = a.delta + it.delta, value = it.value) }
        .takeIf { it.delta != 0 }
    is LogEvent.CommanderTaxChanged -> (b as LogEvent.CommanderTaxChanged)
        .let { a.copy(delta = a.delta + it.delta, value = it.value) }.takeIf { it.delta != 0 }
    is LogEvent.CommanderDamageChanged -> (b as LogEvent.CommanderDamageChanged)
        .let { a.copy(delta = a.delta + it.delta, value = it.value) }.takeIf { it.delta != 0 }
    is LogEvent.ToggleChanged -> if ((b as LogEvent.ToggleChanged).on != a.on) null else b
    is LogEvent.KnockedOut, is LogEvent.Revived -> null
    else -> b
}
