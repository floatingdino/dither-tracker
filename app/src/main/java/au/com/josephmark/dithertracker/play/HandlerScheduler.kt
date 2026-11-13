package au.com.josephmark.dithertracker.play

import android.os.Handler

/**
 * [Scheduler] on an Android [Handler]: one pre-built Runnable per timer id, so arming a timer
 * allocates nothing of ours (the Handler takes a pooled Message). Main-thread only.
 */
class HandlerScheduler(private val handler: Handler, count: Int) : Scheduler {

    var target: Scheduler.Target? = null

    private val runnables = Array(count) { id -> Runnable { target?.onTimer(id) } }

    override fun schedule(id: Int, delayMillis: Long) {
        val r = runnables[id]
        handler.removeCallbacks(r)
        handler.postDelayed(r, delayMillis)
    }

    override fun cancel(id: Int) = handler.removeCallbacks(runnables[id])

    fun cancelAll() {
        for (r in runnables) handler.removeCallbacks(r)
    }
}
