package au.com.josephmark.dithertracker.eink

import android.os.Build
import android.util.Log
import android.view.View

/**
 * The Nomad's per-View e-ink waveform (T58, `docs/eink.md` "Display modes (T58, as built)"): Ratta's
 * framework adds `View.setEinkUpdateMode(int)`, `resetEinkUpdateMode()` and
 * `getEinkFinalUpdateMode()`. Every invalidation of a view carries that view's mode to the vendor's
 * `ViewRootImpl`, so setting it on our one root view sets the waveform for everything we draw.
 *
 * The methods are vendor additions to `android.view.View`, flagged as public SDK in the device's hidden
 * API lists (so a release build may call them too), but absent from the real SDK, so: only on a
 * Supernote, resolved once by reflection and cached, every call inside `try/catch (Throwable)`, any
 * failure → logged once and disabled for the session ([ViewModeGuard]). Nothing else of the vendor's is
 * touched (no `SFCommand`, no `EinkManager` global switches).
 */
object EinkViewMode {
    /** No mode set (`resetEinkUpdateMode()`): the framework's own choice. */
    const val NORMAL = 0

    /** `EINK_SHOW_MODE_*` values (vendor constants on `View`). */
    const val DU = 1
    const val GC = 2
    const val A2 = 4
    const val AUTO = 7
    const val DUX = 11

    /** The mode the "Display: FAST" setting stands for. */
    const val FAST = DU

    private const val TAG = "EinkViewMode"

    private var guard: ViewModeGuard? = null

    private fun guard(): ViewModeGuard = guard ?: ViewModeGuard(
        isSupernote = VendorRefreshGuard.isSupernote(Build.MANUFACTURER),
        resolve = ::resolve,
        log = { Log.w(TAG, it) },
    ).also { guard = it }

    /** Whether per-view modes work here (a Supernote whose framework has the methods). Main thread. */
    val available: Boolean get() = guard().available

    /** Sets [mode] on [view] ([NORMAL] resets it). False when unavailable. Main thread. */
    fun set(view: View, mode: Int): Boolean = guard().set(view, mode)

    /** The view's mode as the framework reports it, or [ViewModeGuard.UNKNOWN]. Main thread. */
    fun read(view: View): Int = guard().read(view)

    private fun resolve(): ViewModeGuard.Calls {
        val c = View::class.java
        val set = c.getMethod("setEinkUpdateMode", Integer.TYPE)
        val reset = c.getMethod("resetEinkUpdateMode")
        val read = c.getMethod("getEinkFinalUpdateMode")
        return object : ViewModeGuard.Calls {
            override fun set(view: Any, mode: Int) {
                set.invoke(view, mode)
            }

            override fun reset(view: Any) {
                reset.invoke(view)
            }

            override fun read(view: Any): Int = read.invoke(view) as Int
        }
    }
}

/**
 * The guard logic of [EinkViewMode] without Android or reflection (JVM-tested with an injected
 * [resolve]): manufacturer check, resolve once (lazily, on first use), any failure — unresolvable or a
 * call throwing anything — logs once and disables for the session; never throws.
 */
class ViewModeGuard(
    isSupernote: Boolean,
    private val resolve: () -> Calls?,
    private val log: (String) -> Unit,
) {
    /** The resolved vendor methods (`view` is an `android.view.View`). */
    interface Calls {
        fun set(view: Any, mode: Int)
        fun reset(view: Any)
        fun read(view: Any): Int
    }

    private var calls: Calls? = null

    /** Disabled for the session (wrong device, unresolvable, or a call threw). */
    var disabled: Boolean = !isSupernote
        private set

    /** Successful [set] calls (tests / trace). */
    var sets: Int = 0
        private set

    /** Whether the calls are usable (resolves on first ask). */
    val available: Boolean get() = calls() != null

    /** Sets [mode] on [view]; [EinkViewMode.NORMAL] resets it. False when unavailable or it failed. */
    fun set(view: Any, mode: Int): Boolean {
        val c = calls() ?: return false
        return try {
            if (mode == EinkViewMode.NORMAL) c.reset(view) else c.set(view, mode)
            sets++
            true
        } catch (t: Throwable) {
            disable("set($mode) failed: $t")
            false
        }
    }

    /** The view's final update mode, or [UNKNOWN]. */
    fun read(view: Any): Int {
        val c = calls() ?: return UNKNOWN
        return try {
            c.read(view)
        } catch (t: Throwable) {
            disable("read failed: $t")
            UNKNOWN
        }
    }

    private fun calls(): Calls? {
        if (disabled) return null
        calls?.let { return it }
        val c = try {
            resolve()
        } catch (t: Throwable) {
            disable("methods not found: $t")
            return null
        }
        if (c == null) disable("methods not found")
        calls = c
        return c
    }

    private fun disable(why: String) {
        disabled = true
        calls = null
        log("e-ink view mode disabled for this session: $why")
    }

    companion object {
        /** [read] when the mode cannot be read. */
        const val UNKNOWN = -1
    }
}
