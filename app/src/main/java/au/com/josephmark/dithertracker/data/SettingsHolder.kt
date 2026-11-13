package au.com.josephmark.dithertracker.data

import au.com.josephmark.dithertracker.core.serial.AppSettings

/**
 * The live [AppSettings] for the process (main thread only). The settings screen (T12) calls
 * [update]; [Persistence] listens and saves; views that care add their own [Listener].
 * [shared] lives as long as the process, like `GameSession.shared`.
 */
class SettingsHolder(initial: AppSettings = AppSettings()) {

    fun interface Listener {
        fun onSettingsChanged(old: AppSettings, new: AppSettings)
    }

    var current: AppSettings = initial
        private set

    private var listeners: Array<Listener> = emptyArray()

    /** Replaces the settings and notifies listeners (in registration order) if they differ. */
    fun update(new: AppSettings) {
        val old = current
        if (new == old) return
        current = new
        val ls = listeners
        for (i in ls.indices) ls[i].onSettingsChanged(old, new)
    }

    fun addListener(l: Listener) {
        if (listeners.none { it === l }) listeners += l
    }

    fun removeListener(l: Listener) {
        listeners = listeners.filter { it !== l }.toTypedArray()
    }

    companion object {
        val shared: SettingsHolder by lazy { SettingsHolder() }
    }
}
