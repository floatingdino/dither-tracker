package au.com.josephmark.dithertracker.eink

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * The optional vendor full refresh (`docs/eink.md` §4 item 5): `getSystemService("eink")` →
 * `screenRefresh(false, 1)`, the call the Supernote launcher's slide-up refresh makes. Hidden,
 * reverse-engineered API, so: only on a Supernote, resolved once by reflection and cached, called
 * inside `try/catch (Throwable)`, disabled for the session (logged once) on any failure, and no other
 * method of that service is ever touched. Off unless [USE_VENDOR_REFRESH] or the debug extra
 * `--ez vendorRefresh true` (see `MainActivity`); when on it replaces the app-drawn flash for full
 * cleans only, and a failure falls back to the flash.
 */
object SupernoteFullRefresh {
    /** Ship default: off until the Nomad check (eink.md E4) shows a clear win. */
    const val USE_VENDOR_REFRESH = false

    private const val TAG = "SupernoteRefresh"

    private var guard: VendorRefreshGuard? = null

    /** Runs the vendor full refresh. False when unavailable (the caller flashes instead). Main thread. */
    fun tryFullRefresh(context: Context): Boolean {
        val g = guard ?: VendorRefreshGuard(
            isSupernote = VendorRefreshGuard.isSupernote(Build.MANUFACTURER),
            resolve = { resolve(context.applicationContext) },
            log = { Log.w(TAG, it) },
        ).also { guard = it }
        return g.tryRefresh()
    }

    private fun resolve(context: Context): VendorRefreshGuard.Invoker? {
        val service = context.getSystemService("eink") ?: return null
        val m = service.javaClass.getMethod(
            "screenRefresh",
            java.lang.Boolean.TYPE,
            Integer.TYPE,
        )
        return VendorRefreshGuard.Invoker { afterWindowHide, mode -> m.invoke(service, afterWindowHide, mode) }
    }
}

/**
 * The guard logic of [SupernoteFullRefresh], without Android or reflection (JVM-tested with an
 * injected [resolve]): manufacturer check, resolve once, call `(false, 1)`, any failure → log once and
 * disable for the session.
 */
class VendorRefreshGuard(
    private val isSupernote: Boolean,
    private val resolve: () -> Invoker?,
    private val log: (String) -> Unit,
) {
    /** The resolved `screenRefresh(boolean, int)`. */
    fun interface Invoker {
        fun screenRefresh(afterWindowHide: Boolean, mode: Int)
    }

    private var invoker: Invoker? = null

    /** Disabled for the session (wrong device, unresolvable, or a call threw). */
    var disabled: Boolean = !isSupernote
        private set

    /** Number of successful calls (tests / trace). */
    var calls: Int = 0
        private set

    fun tryRefresh(): Boolean {
        if (disabled) return false
        try {
            val inv = invoker ?: resolve().also { invoker = it }
            if (inv == null) {
                disable("eink service or screenRefresh(boolean,int) not found")
                return false
            }
            inv.screenRefresh(false, MODE_FULL)
            calls++
            return true
        } catch (t: Throwable) {
            disable("screenRefresh failed: $t")
            return false
        }
    }

    private fun disable(why: String) {
        disabled = true
        invoker = null
        log("vendor full refresh disabled for this session: $why")
    }

    companion object {
        /** `screenRefresh`'s mode argument the launcher's refresh gesture uses. */
        const val MODE_FULL = 1

        fun isSupernote(manufacturer: String?): Boolean = manufacturer.equals("Supernote", ignoreCase = true)
    }
}
