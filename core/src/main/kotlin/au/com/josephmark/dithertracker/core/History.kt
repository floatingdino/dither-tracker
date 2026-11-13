package au.com.josephmark.dithertracker.core

/** A game state plus its log: the unit that undo/redo snapshots (and T10 persists). */
data class Game(val state: GameState, val log: List<LogEntry> = emptyList()) {
    /** Applies [action] and appends its log entries. Returns this instance for a no-op. */
    fun apply(action: Action, timestamp: Long): Game {
        val next = reduce(state, action)
        if (next === state) return this
        val entries = describe(state, next, action, timestamp)
        return Game(next, if (action.startsFreshLog) entries else log + entries)
    }
}

private val Action.startsFreshLog get() = this == Action.NewGame || this == Action.Reset

/**
 * Immutable undo/redo wrapper. Every method returns a new [History] (or `this` when nothing changed).
 *
 * Coalescing: [dispatch] with a non-null `coalesceKey` equal to the key of the previous dispatch
 * (and within [coalesceWindowMillis] of it, by the timestamps passed in) folds into the same undo
 * step, and its log entries merge with that step's entries (e.g. ten `-1` taps → one `-10` entry).
 * Call [endGroup] to close a group explicitly (e.g. when the pending-delta readout commits).
 * Undo, redo, a null key, or a different key also close the group.
 */
data class History(
    val present: Game,
    /** Oldest first; at most [maxDepth]. */
    val past: List<Game> = emptyList(),
    /** Next redo last. */
    val future: List<Game> = emptyList(),
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
    val coalesceWindowMillis: Long = DEFAULT_COALESCE_WINDOW_MILLIS,
    /** Key of the open coalescing group, if any. */
    val openGroup: String? = null,
    /** Index in [present]'s log where the open group's entries start. */
    val groupLogStart: Int = 0,
    /** Timestamp of the last dispatch in the open group. */
    val lastTimestamp: Long = 0,
) {
    init {
        require(maxDepth >= 1) { "maxDepth must be >= 1" }
    }

    constructor(
        state: GameState,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        coalesceWindowMillis: Long = DEFAULT_COALESCE_WINDOW_MILLIS,
    ) : this(Game(state), maxDepth = maxDepth, coalesceWindowMillis = coalesceWindowMillis)

    val state: GameState get() = present.state
    val log: List<LogEntry> get() = present.log
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun dispatch(action: Action, timestamp: Long, coalesceKey: String? = null): History {
        val before = present.state
        val next = reduce(before, action)
        if (next === before) return this

        val coalesce = coalesceKey != null &&
            coalesceKey == openGroup &&
            past.isNotEmpty() &&
            timestamp - lastTimestamp in 0..coalesceWindowMillis
        if (!coalesce) {
            val game = present.apply(action, timestamp)
            return copy(
                present = game,
                past = (past + present).takeLast(maxDepth),
                future = emptyList(),
                openGroup = coalesceKey,
                groupLogStart = if (action.startsFreshLog) 0 else present.log.size,
                lastTimestamp = timestamp,
            )
        }

        val groupBase = past.last()
        if (next == groupBase.state) {
            // The group cancelled itself out (e.g. +1 then -1): drop the step entirely.
            return copy(present = groupBase, past = past.dropLast(1), future = emptyList(), openGroup = null)
        }
        val entries = describe(before, next, action, timestamp)
        return copy(
            present = Game(next, mergeInto(present.log, groupLogStart, entries)),
            future = emptyList(),
            lastTimestamp = timestamp,
        )
    }

    fun endGroup(): History = if (openGroup == null) this else copy(openGroup = null)

    fun undo(): History {
        if (past.isEmpty()) return endGroup()
        return copy(present = past.last(), past = past.dropLast(1), future = future + present, openGroup = null)
    }

    fun redo(): History {
        if (future.isEmpty()) return endGroup()
        return copy(present = future.last(), future = future.dropLast(1), past = (past + present).takeLast(maxDepth), openGroup = null)
    }

    /** Replaces everything with [state] and no undo history (e.g. after restoring from disk). */
    fun cleared(state: GameState = this.state, log: List<LogEntry> = this.log): History =
        History(Game(state, log), maxDepth = maxDepth, coalesceWindowMillis = coalesceWindowMillis)

    companion object {
        const val DEFAULT_MAX_DEPTH = 200
        const val DEFAULT_COALESCE_WINDOW_MILLIS = 3_000L
    }
}
