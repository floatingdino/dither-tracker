package au.com.josephmark.dithertracker.eink

import android.util.Log
import android.view.View

/**
 * EXPERIMENT (docs/eink.md, "Faster display modes"): the Ratta framework adds a per-View e-ink update
 * mode — `View.setEinkUpdateMode(int)` with `EINK_SHOW_MODE_*` (DU = 1, GC = 2, GL = 3, A2 = 4,
 * AUTO = 7, DUX = 11, …). Hidden vendor API: reflection, guarded, logged, debug extra only.
 */
object EinkViewMode {
    private const val TAG = "EinkViewMode"

    /** Tries to set [mode] on [view]; returns a one-line report (also logged). */
    fun apply(view: View, mode: Int): String {
        val sb = StringBuilder("mode=$mode")
        try {
            val set = View::class.java.getMethod("setEinkUpdateMode", Integer.TYPE)
            set.invoke(view, mode)
            sb.append(" set=ok")
        } catch (t: Throwable) {
            sb.append(" set=FAIL(${t.javaClass.simpleName}: ${t.message})")
        }
        for (name in arrayOf("getEinkFinalUpdateMode", "getEinkDataMode", "isEinkModeSet", "getEinkA2Gate")) {
            try {
                val v = View::class.java.getMethod(name).invoke(view)
                sb.append(" $name=$v")
            } catch (t: Throwable) {
                sb.append(" $name=FAIL(${t.javaClass.simpleName})")
            }
        }
        val s = sb.toString()
        Log.w(TAG, s)
        return s
    }
}
