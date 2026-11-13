package au.com.josephmark.dithertracker.play

/**
 * A bank of one-shot timers addressed by small int ids, so timing logic (`stage.PlayInput` hold-repeat,
 * `readout.PendingDeltas` idle commit) stays pure and JVM-testable. The app supplies [HandlerScheduler];
 * tests supply a fake clock. Implementations must not allocate per [schedule].
 */
interface Scheduler {
    /** Arms timer [id] to call the target's [Target.onTimer] after [delayMillis]; re-arming replaces it. */
    fun schedule(id: Int, delayMillis: Long)

    /** Disarms timer [id] (no-op when not armed). */
    fun cancel(id: Int)

    fun interface Target {
        fun onTimer(id: Int)
    }

    companion object {
        /** Never fires: hold-repeat and idle commit are simply off. */
        val NONE: Scheduler = object : Scheduler {
            override fun schedule(id: Int, delayMillis: Long) {}
            override fun cancel(id: Int) {}
        }
    }
}
