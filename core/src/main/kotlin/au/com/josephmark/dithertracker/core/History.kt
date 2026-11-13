package au.com.josephmark.dithertracker.core

/** A game state plus its log: the unit that undo/redo snapshots (and T10 persists). */
data class Game(val state: GameState, val log: List<LogEntry> = emptyList()) {
    /** Applies [action] and appends its log entries. Returns this instance for a no-op. */
    fun apply(action: Action, timestamp: Long): Game {
        val next = reduceAt(state, action, timestamp)
        if (next === state) return this
        val entries = describe(state, next, action, timestamp)
        return Game(next, if (action.startsFreshLog) entries else log + entries)
    }
}

private val Action.startsFreshLog get() = this == Action.NewGame || this == Action.Reset || this is Action.NewGameFor

/**
 * One undo step: a coalesced burst of same-key actions (a *group*), or a single action dispatched
 * without a key. [History.past] lists steps oldest-touched first; [History.undo] removes the last.
 */
class UndoStep internal constructor(
    internal val id: Long,
    /** The coalesce key, or null for a non-coalescible action. */
    val key: String?,
    /** This step's log entries (merged across the group), exactly as they appear in the log. */
    val entries: List<LogEntry>,
    /** Timestamp of this step's most recent action. */
    val lastTimestamp: Long,
    /**
     * True while more same-key actions may still fold into this group. Expiry is only noticed on
     * the next dispatch (core never reads the clock), so an open group may already be past its window.
     */
    val isOpen: Boolean,
    /** Whether the step's first action started a fresh log (NewGame / NewGameFor / Reset). */
    internal val freshLog: Boolean,
) {
    internal fun with(entries: List<LogEntry> = this.entries, lastTimestamp: Long = this.lastTimestamp, isOpen: Boolean = this.isOpen) =
        UndoStep(id, key, entries, lastTimestamp, isOpen, freshLog)

    override fun toString() = "UndoStep(key=$key, open=$isOpen, entries=$entries)"
}

/** One applied action in chronological order, with the state it was applied to (for replay). */
internal class Rec(
    val action: Action,
    val timestamp: Long,
    /** [UndoStep.id] of the owning step (which may since have been dropped by the depth bound). */
    val step: Long,
    val before: GameState,
    val after: GameState,
    val entries: List<LogEntry>,
)

/** What [History.redo] restores: everything undo replaced, exactly. */
private class Frame(val present: Game, val baseLog: List<LogEntry>, val steps: List<UndoStep>, val timeline: List<Rec>)

/**
 * Immutable undo/redo wrapper. Every method returns a new [History] (or `this` when nothing changed).
 *
 * **Grouping is per key.** [dispatch] with a non-null `coalesceKey` folds into that key's open group
 * when the key's previous action was within [coalesceWindowMillis] (by the timestamps passed in),
 * however many other keys' actions came in between. So four seats tapping at once make one undo
 * step and one merged log entry per seat (A −1, B −1, A −1, A −1 → B −1, A −3). Each group expires
 * on its own window, measured from its own last action. [endGroup] closes every open group,
 * `endGroup(key)` one; an undo, a redo, or a null-key action also close every open group.
 *
 * **Undo order is "last touched":** [undo] removes the step that owns the most recent surviving
 * action — i.e. the group whose burst was touched last — as a whole, and nothing else. Steps are
 * kept (and listed in [past]) in that order: touching a group moves it to the end.
 *
 * **How a group is undone:** state is always the chronological replay of the surviving actions.
 * Undoing a group restores the snapshot taken just before its first action and re-applies, through
 * [reduce], every later action of other groups that interleaved with it. That keeps every reducer
 * invariant (clamping, LIFO life-link refunds, KO overrides, exclusivity) exact, at the cost of a
 * short replay when (and only when) groups interleaved. Redo restores exactly what undo replaced.
 *
 * **Log:** each step contributes one block of merged entries, placed where the step was last
 * touched (so the log reads in undo order: undo always removes the trailing block). Values in a
 * merged entry are the result after the group's latest action. A group that nets to zero (the
 * state is the same with or without it) disappears, from the log and the undo stack.
 */
class History private constructor(
    /** The current game (state + log). */
    val present: Game,
    /** Log contributed by everything older than [past] (steps dropped by the depth bound). */
    private val baseLog: List<LogEntry>,
    /** Undo steps, oldest-touched first; at most [maxDepth]. The last one is what [undo] removes. */
    val past: List<UndoStep>,
    /** Surviving actions, chronological, from the first action of the oldest live step on. */
    private val timeline: List<Rec>,
    /** Next redo last. */
    private val future: List<Frame>,
    val maxDepth: Int,
    val coalesceWindowMillis: Long,
    private val nextId: Long,
) {
    init {
        require(maxDepth >= 1) { "maxDepth must be >= 1" }
    }

    constructor(
        present: Game,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        coalesceWindowMillis: Long = DEFAULT_COALESCE_WINDOW_MILLIS,
    ) : this(present, present.log, emptyList(), emptyList(), emptyList(), maxDepth, coalesceWindowMillis, 0)

    constructor(
        state: GameState,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        coalesceWindowMillis: Long = DEFAULT_COALESCE_WINDOW_MILLIS,
    ) : this(Game(state), maxDepth = maxDepth, coalesceWindowMillis = coalesceWindowMillis)

    val state: GameState get() = present.state
    val log: List<LogEntry> get() = present.log
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /**
     * Key of the most recently touched open group, if any. Kept for source compatibility; with
     * several seats tapping at once prefer [openGroups] / [isGroupOpen] and `endGroup(key)`.
     */
    val openGroup: String? get() = past.lastOrNull { it.isOpen }?.key

    /** Keys of all open groups, oldest-touched first. */
    val openGroups: List<String> get() = past.mapNotNull { if (it.isOpen) it.key else null }

    fun isGroupOpen(key: String): Boolean = past.any { it.isOpen && it.key == key }

    /** Number of actions retained for replay (bounded; for tests). */
    internal val recordCount: Int get() = timeline.size

    fun dispatch(action: Action, timestamp: Long, coalesceKey: String? = null): History {
        val before = present.state
        val next = reduceAt(before, action, timestamp)
        if (next === before) return this
        val entries = describe(before, next, action, timestamp)

        val steps = if (coalesceKey == null) closeAll(past) else closeExpired(past, timestamp)
        val gi = if (coalesceKey == null) -1 else steps.indexOfLast { it.isOpen && it.key == coalesceKey }

        if (gi < 0) {
            val id = nextId
            val step = UndoStep(id, coalesceKey, entries, timestamp, coalesceKey != null, action.startsFreshLog)
            val log = if (action.startsFreshLog) entries else present.log + entries
            return bounded(
                present = Game(next, log),
                baseLog = baseLog,
                steps = steps + step,
                timeline = timeline + Rec(action, timestamp, id, before, next, entries),
                nextId = id + 1,
            )
        }

        val g = steps[gi]
        val touched = g.with(entries = mergeInto(g.entries, 0, entries), lastTimestamp = timestamp)
        val newSteps = ArrayList<UndoStep>(steps.size).apply {
            for (i in steps.indices) if (i != gi) add(steps[i])
            add(touched)
        }
        val newTimeline = timeline + Rec(action, timestamp, g.id, before, next, entries)

        if (touched.entries.isEmpty()) {
            // Possibly a group that cancelled itself out (e.g. +1 then -1): if the game is the same
            // without it, drop it entirely.
            val without = removeStep(newTimeline, newSteps, touched.id)
            if (without.state == next) {
                return History(
                    Game(next, logOf(baseLog, without.steps)), baseLog, without.steps, without.timeline,
                    emptyList(), maxDepth, coalesceWindowMillis, nextId,
                )
            }
        }
        val log = if (gi == steps.lastIndex) {
            present.log.subList(0, present.log.size - g.entries.size) + touched.entries
        } else {
            logOf(baseLog, newSteps)
        }
        return bounded(Game(next, log), baseLog, newSteps, newTimeline, nextId)
    }

    /** Closes every open group. */
    fun endGroup(): History {
        val steps = closeAll(past)
        return if (steps === past) this else withSteps(steps)
    }

    /** Closes the open group for [key], leaving other keys' groups open. */
    fun endGroup(key: String): History {
        val i = past.indexOfLast { it.isOpen && it.key == key }
        if (i < 0) return this
        return withSteps(past.toMutableList().also { it[i] = it[i].with(isOpen = false) })
    }

    /** Removes the most recently touched step as a whole (closing all open groups). */
    fun undo(): History {
        if (past.isEmpty()) return endGroup()
        val closed = closeAll(past)
        val frame = Frame(present, baseLog, closed, timeline)
        val r = removeStep(timeline, closed, closed.last().id)
        val state = canonical(present.state, r.state)
        return History(
            Game(state, logOf(baseLog, r.steps)), baseLog, r.steps, r.timeline,
            future + frame, maxDepth, coalesceWindowMillis, nextId,
        )
    }

    /** Restores exactly what the last [undo] removed (closing all open groups). */
    fun redo(): History {
        if (future.isEmpty()) return endGroup()
        val f = future.last()
        return History(f.present, f.baseLog, f.steps, f.timeline, future.dropLast(1), maxDepth, coalesceWindowMillis, nextId)
    }

    /** Replaces everything with [state] and no undo history (e.g. after restoring from disk). */
    fun cleared(state: GameState = this.state, log: List<LogEntry> = this.log): History =
        History(Game(state, log), maxDepth = maxDepth, coalesceWindowMillis = coalesceWindowMillis)

    override fun toString() = "History(state=$state, past=$past, canRedo=$canRedo)"

    // ---- internals ------------------------------------------------------------------------

    private fun withSteps(steps: List<UndoStep>) =
        History(present, baseLog, steps, timeline, future, maxDepth, coalesceWindowMillis, nextId)

    private fun closeAll(steps: List<UndoStep>): List<UndoStep> =
        if (steps.none { it.isOpen }) steps else steps.map { if (it.isOpen) it.with(isOpen = false) else it }

    private fun expired(s: UndoStep, timestamp: Long) =
        s.isOpen && timestamp - s.lastTimestamp !in 0..coalesceWindowMillis

    private fun closeExpired(steps: List<UndoStep>, timestamp: Long): List<UndoStep> =
        if (steps.none { expired(it, timestamp) }) steps
        else steps.map { if (expired(it, timestamp)) it.with(isOpen = false) else it }

    /** Applies the depth bound (and the replay-record cap); clears redo. */
    private fun bounded(present: Game, baseLog: List<LogEntry>, steps: List<UndoStep>, timeline: List<Rec>, nextId: Long): History {
        var log0 = baseLog
        var live = steps
        var drop = (live.size - maxDepth).coerceAtLeast(0)
        if (timeline.size > maxRecords) {
            // A group kept alive for very long holds every interleaved action after its start. Past
            // the cap, make the oldest steps permanent up to the owner of the oldest record.
            val owner = live.indexOfFirst { it.id == timeline[0].step }
            drop = maxOf(drop, owner + 1)
        }
        if (drop > 0) {
            for (i in 0 until drop) log0 = appendBlock(log0, live[i])
            live = live.subList(drop, live.size).toList()
        }
        return History(present, log0, live, trimTimeline(timeline, live), emptyList(), maxDepth, coalesceWindowMillis, nextId)
    }

    private val maxRecords: Int get() = maxOf(MAX_RECORDS, maxDepth * 4)

    private class Removed(val state: GameState, val steps: List<UndoStep>, val timeline: List<Rec>)

    /**
     * The game without step [id]: restores the snapshot before its first action and replays every
     * later surviving action of other steps through [reduce]. Steps whose actions all became no-ops
     * in the replay are dropped too; replayed steps get their log blocks rebuilt.
     */
    private fun removeStep(timeline: List<Rec>, steps: List<UndoStep>, id: Long): Removed {
        val i0 = timeline.indexOfFirst { it.step == id }
        val others = steps.filter { it.id != id }
        if (i0 < 0) return Removed(present.state, others, timeline)
        val out = ArrayList<Rec>(timeline.size)
        for (i in 0 until i0) out += timeline[i]
        var s = timeline[i0].before
        val replayed = LinkedHashMap<Long, Boolean>() // step id → any action still effective
        for (j in i0 + 1 until timeline.size) {
            val r = timeline[j]
            if (r.step == id) continue
            val n = reduceAt(s, r.action, r.timestamp)
            out += Rec(r.action, r.timestamp, r.step, s, n, if (n === s) emptyList() else describe(s, n, r.action, r.timestamp))
            replayed[r.step] = (replayed[r.step] ?: false) || n !== s
            s = n
        }
        if (replayed.isEmpty()) return Removed(s, others, trimTimeline(out, others))

        // A step none of whose actions changes anything any more (e.g. its damage was all clamped
        // away) would be an undo that does nothing: drop it with the removed one.
        val candidates = replayed.filterValues { !it }.keys
        val dead = if (candidates.isEmpty()) candidates else {
            val effective = HashSet<Long>()
            for (r in out) if (r.step in candidates && r.after !== r.before) effective += r.step
            candidates - effective
        }
        val timelineOut = if (dead.isEmpty()) out else out.filter { it.step !in dead }
        val stepsOut = others.mapNotNull { st ->
            when (st.id) {
                in dead -> null
                in replayed -> st.with(entries = blockOf(timelineOut, st.id))
                else -> st
            }
        }
        return Removed(s, stepsOut, trimTimeline(timelineOut, stepsOut))
    }

    /** Drops leading actions whose steps are no longer undoable (nothing will replay from them). */
    private fun trimTimeline(timeline: List<Rec>, live: List<UndoStep>): List<Rec> {
        if (timeline.isEmpty() || live.any { it.id == timeline[0].step }) return timeline
        val ids = live.mapTo(HashSet()) { it.id }
        val i = timeline.indexOfFirst { it.step in ids }
        return if (i < 0) emptyList() else timeline.subList(i, timeline.size).toList()
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 200
        const val DEFAULT_COALESCE_WINDOW_MILLIS = 3_000L

        /** Minimum cap on actions retained for replay (see [bounded]). */
        internal const val MAX_RECORDS = 2_048
    }
}

/** A step's merged log block, rebuilt from its records (first raw, later ones merged in). */
private fun blockOf(timeline: List<Rec>, id: Long): List<LogEntry> {
    var block: List<LogEntry>? = null
    for (r in timeline) {
        if (r.step != id) continue
        block = if (block == null) r.entries else mergeInto(block, 0, r.entries)
    }
    return block ?: emptyList()
}

private fun appendBlock(log: List<LogEntry>, step: UndoStep): List<LogEntry> =
    if (step.freshLog) step.entries else log + step.entries

/** The log: [baseLog], then each step's block in order (a fresh-log step discards what came before). */
private fun logOf(baseLog: List<LogEntry>, steps: List<UndoStep>): List<LogEntry> {
    val fresh = steps.indexOfLast { it.freshLog }
    val out = ArrayList<LogEntry>()
    if (fresh < 0) out.addAll(baseLog)
    for (i in maxOf(fresh, 0) until steps.size) out.addAll(steps[i].entries)
    return out
}

/** [new] with every player (and the state itself) equal to one in [old] replaced by the old instance. */
private fun canonical(old: GameState, new: GameState): GameState {
    if (old === new) return new
    if (old == new) return old
    if (old.players.size != new.players.size) return new
    var changed = false
    val players = new.players.mapIndexed { i, p ->
        val o = old.players[i]
        if (o !== p && o == p) {
            changed = true
            o
        } else p
    }
    return if (changed) new.copy(players = players) else new
}
