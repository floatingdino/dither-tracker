package au.com.josephmark.dithertracker.data

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import au.com.josephmark.dithertracker.core.History
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayLayout

/**
 * Process-wide wiring of [SaveStore] to [GameSession.shared] and [SettingsHolder.shared].
 * Main thread only.
 *
 * - [start] runs once per process (later calls, e.g. from a recreated activity, are no-ops): it
 *   loads the save synchronously (~2 KB, a few ms — avoids a blank-then-populated double e-ink
 *   refresh), restores it into the session only while the session is still pristine, then observes
 *   game and settings changes to schedule debounced background writes.
 * - [flush] on `onPause`/`onStop` writes any pending change before the process can be killed.
 */
object Persistence {
    const val TAG = "DitherSave"

    /** How long `onPause` may block waiting for the flush write (normally a few ms). */
    private const val FLUSH_WAIT_MILLIS = 1_000L

    private var store: SaveStore? = null

    /** Load report from [start] (null before it ran). */
    var lastLoad: SaveStore.LoadResult? = null
        private set

    fun start(
        context: Context,
        session: GameSession = GameSession.shared,
        settings: SettingsHolder = SettingsHolder.shared,
    ) {
        if (store != null) return
        val thread = HandlerThread("dither-save", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        val handler = Handler(thread.looper)
        val s = SaveStore(
            dir = context.filesDir,
            scheduler = object : SaveStore.Scheduler {
                override fun post(task: Runnable) {
                    handler.post(task)
                }

                override fun postDelayed(task: Runnable, delayMillis: Long) {
                    handler.postDelayed(task, delayMillis)
                }
            },
            clock = SaveStore.Clock { SystemClock.uptimeMillis() },
            log = { Log.w(TAG, it) },
            onWritten = { nanos, encode, size -> Log.i(TAG, "saved $size bytes in ${nanos / 1000} us (encode ${encode / 1000} us)") },
        )
        store = s

        val loaded = s.load()
        lastLoad = loaded
        val save = loaded.save
        if (loaded.failures.isNotEmpty()) {
            Log.w(TAG, "save problems: ${loaded.failures.joinToString("; ")} → using ${loaded.source.name.lowercase()}")
        }
        // Any count the layout engine seats (2..6, T11; core rejects anything else on decode).
        var applied = false
        if (save != null) {
            settings.update(save.settings)
            if (save.game.state.players.size in 2..PlayLayout.MAX_PLAYERS && session.isPristine) {
                session.replace(History(save.game))
                applied = true
            }
        }
        // Whatever is in memory now is what's on disk (or a fresh game that needn't be written yet).
        s.markSaved(session.game, settings.current)
        Log.i(TAG, "restore: source=${loaded.source.name.lowercase()} applied=$applied load=${loaded.nanos / 1000} us (read ${loaded.readNanos / 1000} us)")

        session.addListener { _, _ -> s.update(session.game, settings.current) }
        settings.addListener { _, new -> s.update(session.game, new) }
    }

    /** Writes any pending change now and waits briefly for it (call from `onPause`/`onStop`). */
    fun flush() {
        val s = store ?: return
        val t0 = SystemClock.elapsedRealtimeNanos()
        val before = s.writeCount
        val ok = s.flush(FLUSH_WAIT_MILLIS)
        if (s.writeCount != before) {
            Log.i(
                TAG,
                "flush: wrote in ${s.lastWriteNanos / 1000} us (encode ${s.lastEncodeNanos / 1000} us), " +
                    "main thread waited ${(SystemClock.elapsedRealtimeNanos() - t0) / 1000} us",
            )
        } else if (!ok) {
            Log.w(TAG, "flush: timed out after $FLUSH_WAIT_MILLIS ms")
        }
    }
}
