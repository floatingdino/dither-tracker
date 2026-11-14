package au.com.josephmark.dithertracker.turns

import au.com.josephmark.dithertracker.widgets.CharBuf

/**
 * Turn-time reports as text (T66): whole seconds of active time as `m:ss` under an hour (`0:42`,
 * `2:07`, `59:59`) and `h:mm:ss` from an hour (`1:47:12`), capped at [CAP_MS] (`9:59:59`: no Commander
 * game or turn runs ten hours; past it the report stays at the cap rather than growing a glyph).
 * Seconds are truncated. Allocation-free: writes into a [CharBuf] (at most [MAX_CHARS] chars).
 */
object TimeFormat {

    /** The longest report, `9:59:59`. */
    const val MAX_CHARS = 7

    const val CAP_MS = (10L * 3600 - 1) * 1000

    /** Writes [ms] (negative = 0) into [out] (cleared first) and returns it. */
    fun format(ms: Long, out: CharBuf): CharBuf {
        out.clear()
        val t = (if (ms < 0) 0L else if (ms > CAP_MS) CAP_MS else ms) / 1000
        val h = (t / 3600).toInt()
        val m = ((t / 60) % 60).toInt()
        val sec = (t % 60).toInt()
        if (h > 0) {
            out.append(h)
            out.append(':')
            two(m, out)
        } else {
            out.append(m)
        }
        out.append(':')
        two(sec, out)
        return out
    }

    private fun two(v: Int, out: CharBuf) {
        out.append('0' + v / 10)
        out.append('0' + v % 10)
    }
}
