package au.com.josephmark.dithertracker.eink

/**
 * Which e-ink waveform the root view asks for (T58): the "Display" setting — FAST = DU
 * ([EinkViewMode.FAST]), NORMAL = no mode set — unless the debug launch extra `--ei einkMode N` pins
 * one ([override]), plus the GC clean's temporary switch ([gc], see [ScreenCleaner.waveform]).
 *
 * The framework keeps the mode in a field of the view and reads it on every invalidation, so it is set
 * once and stays (nothing in the framework resets an app view's mode; `docs/eink.md`). [apply] is still
 * called again on resume and on every setting change; setting the mode it already has is a no-op in the
 * framework. Pure (the [Port] does the vendor call), JVM-tested.
 */
class EinkDisplay(
    private val port: Port,
    private val override: Int = NO_OVERRIDE,
    private val log: ((String) -> Unit)? = null,
) : ScreenCleaner.Waveform {

    /** The root view's mode: `EinkViewMode.set` / `read` on it. */
    interface Port {
        /** Sets [mode] (`EinkViewMode.NORMAL` resets); false when unavailable. */
        fun set(mode: Int): Boolean

        /** The framework's view of the mode (debug log), or `ViewModeGuard.UNKNOWN`. */
        fun read(): Int
    }

    /** The mode wanted outside a GC clean. */
    var mode: Int = EinkViewMode.NORMAL
        private set

    /** True while a GC clean holds the view in GC. */
    var cleaning: Boolean = false
        private set

    /** Applies the mode for [fast] (the setting) or the override; [why] goes to the debug log. */
    fun apply(fast: Boolean, why: String) {
        mode = modeFor(fast, override)
        if (cleaning) return // gc(false) restores it
        val ok = port.set(mode)
        log?.invoke("$why: mode=$mode set=$ok read=${port.read()}")
    }

    /** The debug log's readback, at a lifecycle point that changes nothing ([why]). */
    fun check(why: String) {
        log?.invoke("$why: mode=$mode read=${port.read()}")
    }

    override fun gc(on: Boolean): Boolean {
        if (on) {
            if (!port.set(EinkViewMode.GC)) return false
            cleaning = true
            log?.invoke("gc clean: read=${port.read()}")
            return true
        }
        if (!cleaning) return false
        cleaning = false
        port.set(mode)
        return true
    }

    companion object {
        /** No `--ei einkMode` extra. */
        const val NO_OVERRIDE = -1

        /** The mode for the setting [fast], or [override] when it is set (≥ 0; 0 = NORMAL). */
        fun modeFor(fast: Boolean, override: Int): Int = when {
            override >= 0 -> override
            fast -> EinkViewMode.FAST
            else -> EinkViewMode.NORMAL
        }
    }
}
