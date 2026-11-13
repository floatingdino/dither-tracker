package au.com.josephmark.dithertracker.play

/** Deterministic [Scheduler] on a fake clock: [advance] fires due timers in deadline order. */
class FakeScheduler(count: Int) : Scheduler {
    var target: Scheduler.Target? = null
    var now = 0L
        private set
    private val due = LongArray(count) { -1 }

    /** Number of schedule() calls (re-arms included). */
    var scheduled = 0
        private set

    override fun schedule(id: Int, delayMillis: Long) { due[id] = now + delayMillis; scheduled++ }
    override fun cancel(id: Int) { due[id] = -1 }

    fun isArmed(id: Int) = due[id] >= 0
    fun dueAt(id: Int) = due[id]

    /** Moves the clock forward by [ms], firing every timer that comes due (in order, ties by id). */
    fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            var next = -1
            for (i in due.indices) if (due[i] in 0..end && (next < 0 || due[i] < due[next])) next = i
            if (next < 0) break
            now = due[next]
            due[next] = -1
            target?.onTimer(next)
        }
        now = end
    }
}
