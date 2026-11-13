package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Game
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.History
import au.com.josephmark.dithertracker.core.coalesceKey

/**
 * The one owner of the live game: a [History] (undo/redo + log) from `:core`. Pure Kotlin.
 *
 * - Views read [state] and get [Listener.onStateChanged] with the old and new [GameState] after
 *   every change, so they can diff `Player` objects by reference (`old.players[i] !== new.players[i]`
 *   ⇒ seat i changed).
 * - [shared] lives as long as the process, so it survives activity recreation (config change) and
 *   back-then-relaunch. `data.Persistence` restores the saved game into it once per process (with
 *   [replace], only while [isPristine]) and saves by observing changes via [addListener].
 * - Life taps go through [changeLife], which reuses pre-built `ChangeLife(±1)` actions and coalesce
 *   keys per seat: the only allocations on a tap are inside `History.dispatch` itself.
 */
class GameSession(history: History) {

    fun interface Listener {
        fun onStateChanged(old: GameState, new: GameState)
    }

    var history: History = history
        private set

    val state: GameState get() = history.state

    /** The current game (state + log) — what persistence saves. */
    val game: Game get() = history.present

    /** The primary listener (the play view). Notified before the [addListener] observers. */
    var listener: Listener? = null

    // Extra observers (persistence, later screens). Copy-on-write so notify iterates without allocating.
    private var observers: Array<Listener> = emptyArray()

    /** Adds an observer notified after [listener] on every state change (main thread). */
    fun addListener(l: Listener) {
        if (observers.none { it === l }) observers += l
    }

    fun removeListener(l: Listener) {
        observers = observers.filter { it !== l }.toTypedArray()
    }

    /**
     * True until the first change since this session was created: nothing has been dispatched, so
     * restoring a saved game can't overwrite anything newer.
     */
    var isPristine: Boolean = true
        private set

    // Per-seat cached actions, rebuilt only when the player ids at a seat change.
    private var cachedIds = IntArray(0)
    private var minusOne = arrayOf<Action.ChangeLife>()
    private var plusOne = arrayOf<Action.ChangeLife>()
    private var lifeKeys = arrayOf<String?>()

    /** Dispatches [action] at [timestamp] (wall clock, ms). Notifies the listener if anything changed. */
    fun dispatch(action: Action, timestamp: Long, coalesceKey: String? = action.coalesceKey) {
        set(history.dispatch(action, timestamp, coalesceKey))
    }

    /**
     * ±1 (or any [delta]) life for the player at [seat]. Same-seat taps within the History coalesce
     * window fold into one undo step / log entry.
     */
    fun changeLife(seat: Int, delta: Int, timestamp: Long) {
        val players = state.players
        if (seat !in players.indices) return
        ensureCache(players.size)
        val id = players[seat].id
        if (cachedIds[seat] != id) rebuild(seat, id)
        val action = when (delta) {
            -1 -> minusOne[seat]
            1 -> plusOne[seat]
            else -> Action.ChangeLife(id, delta)
        }
        dispatch(action, timestamp, lifeKeys[seat])
    }

    /** Closes the open coalescing group (T07: when the pending-delta readout commits). */
    fun endGroup() = set(history.endGroup())

    fun undo() = set(history.undo())
    fun redo() = set(history.redo())

    /** Replaces the whole history, e.g. with a game restored from disk (T10b). */
    fun replace(newHistory: History) = set(newHistory)

    private fun set(next: History) {
        if (next === history) return
        val old = history.state
        history = next
        isPristine = false
        if (next.state !== old) {
            val new = next.state
            listener?.onStateChanged(old, new)
            val obs = observers
            for (i in obs.indices) obs[i].onStateChanged(old, new)
        }
    }

    private fun ensureCache(n: Int) {
        if (cachedIds.size == n) return
        cachedIds = IntArray(n) { Int.MIN_VALUE }
        minusOne = Array(n) { Action.ChangeLife(0, -1) }
        plusOne = Array(n) { Action.ChangeLife(0, 1) }
        lifeKeys = arrayOfNulls(n)
    }

    private fun rebuild(seat: Int, id: Int) {
        cachedIds[seat] = id
        minusOne[seat] = Action.ChangeLife(id, -1)
        plusOne[seat] = Action.ChangeLife(id, 1)
        lifeKeys[seat] = plusOne[seat].coalesceKey
    }

    companion object {
        fun newGame(playerCount: Int = 4): GameSession = GameSession(History(GameState.create(playerCount)))

        /** The process-wide session (restored from disk by `data.Persistence`). */
        val shared: GameSession by lazy { newGame(PlayLayout.PLAYERS) }
    }
}
