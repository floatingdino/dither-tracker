package au.com.josephmark.dithertracker.core

/**
 * The **active clock** (T66): milliseconds the app has been in use. Core never reads the time; the
 * app's session keeps one of these and turns each wall-clock instant into active time with [at]
 * before dispatching, so every timestamp core sees (action timestamps, the turn clock in [TurnState])
 * excludes time spent paused — a turn left overnight, the device asleep, the app in the background.
 *
 * Immutable: [base] is the active time accumulated up to [resumedAt] (wall ms), or the whole of it
 * while paused ([resumedAt] = [TurnState.NO_TIME]). Persisted as the active time at save
 * (`SaveFile.activeClock`), restored paused ([pausedAt]).
 */
data class ActiveClock(val base: Long = 0L, val resumedAt: Long = TurnState.NO_TIME) {

    val running: Boolean get() = resumedAt != TurnState.NO_TIME

    /** Active ms at wall-clock [wall] (a wall clock set back never runs it backwards). */
    fun at(wall: Long): Long = if (!running) base else base + maxOf(0L, wall - resumedAt)

    /** Stops counting at [wall]. No-op when paused. */
    fun pause(wall: Long): ActiveClock = if (!running) this else ActiveClock(at(wall), TurnState.NO_TIME)

    /** Starts counting from [wall]. No-op when running. */
    fun resume(wall: Long): ActiveClock = if (running) this else ActiveClock(base, wall)

    companion object {
        /** Paused at active time [active] (a restored save). */
        fun pausedAt(active: Long): ActiveClock = ActiveClock(maxOf(0L, active))

        /** Running with active time = wall time (tests, and anything that never pauses). */
        val WALL: ActiveClock = ActiveClock(0L, 0L)
    }
}
