package au.com.josephmark.dithertracker.data

import au.com.josephmark.dithertracker.core.Game
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.core.serial.DecodeResult
import au.com.josephmark.dithertracker.core.serial.FailureReason
import au.com.josephmark.dithertracker.core.serial.SaveCodec
import au.com.josephmark.dithertracker.core.serial.SaveFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The single save file: `SaveCodec.encode(SaveFile(game, settings))`, written atomically, off the
 * main thread, debounced. Pure JVM (takes a [File] dir, a [Scheduler] and a clock) so it is unit
 * tested without Android; [Persistence] wires it to a `HandlerThread` and `SystemClock`.
 *
 * **Threads.** [update] and [flush] are called from the main thread; everything that encodes or
 * touches the disk runs on the one [scheduler] thread, so writes are serialised. [update] only
 * stores two references in volatile fields, reads the clock and — at most once per burst — posts
 * one delayed task: no encoding, no I/O, no allocation on a tap. `Game`/`AppSettings` are immutable,
 * so the worker can encode the snapshot it reads.
 *
 * **Debounce.** The first change of a burst posts the write task [debounceMillis] ahead. When it
 * fires it re-posts itself while changes are still arriving (quiet for < [debounceMillis]), but never
 * defers past [maxDelayMillis] after the burst started, so endless tapping still saves periodically.
 * N changes in a burst ⇒ one write of the latest snapshot. [flush] writes now (used on `onPause`).
 *
 * **Atomicity.** Write `save.dtsv.tmp`, `fsync`, close; rename the current `save.dtsv` (only if it
 * is known good) to `save.dtsv.bak`; rename the temp file to `save.dtsv`. At every instant there is
 * a complete, CRC-checked old or new file on disk. [load] tries main, then a leftover temp (only ever
 * the newest attempt, and only used if its CRC checks out), then the backup; else returns no save.
 */
class SaveStore(
    private val dir: File,
    private val scheduler: Scheduler,
    private val clock: Clock,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    private val log: (String) -> Unit = {},
    /** Called on the worker after each successful write: total ns, encode ns, file size. */
    private val onWritten: (nanos: Long, encodeNanos: Long, size: Int) -> Unit = { _, _, _ -> },
    /** Writes [bytes] fully to [file] and fsyncs it. Injectable so tests can simulate a crash mid-write. */
    private val writeFile: (file: File, bytes: ByteArray) -> Unit = ::writeAndSync,
) {

    /** Runs tasks in order on one background thread. */
    interface Scheduler {
        fun post(task: Runnable)
        fun postDelayed(task: Runnable, delayMillis: Long)
    }

    /** Monotonic milliseconds (e.g. `SystemClock.uptimeMillis`). */
    fun interface Clock {
        fun now(): Long
    }

    enum class Source { MAIN, TEMP, BACKUP, NONE }

    /** Result of [load]: [save] is null when nothing decodable was found (start a fresh game). */
    class LoadResult(
        val save: SaveFile?,
        val source: Source,
        /** One line per candidate that existed but failed, e.g. `main: CHECKSUM_MISMATCH (crc)`. */
        val failures: List<String>,
        /** Total time in [load], and the part spent reading bytes (the rest is decoding). */
        val nanos: Long,
        val readNanos: Long,
    )

    val mainFile = File(dir, FILE_NAME)
    val tempFile = File(dir, "$FILE_NAME.tmp")
    val backupFile = File(dir, "$FILE_NAME.bak")

    // Latest snapshot (written by the main thread, read by the worker).
    @Volatile private var latestGame: Game? = null
    @Volatile private var latestSettings: AppSettings = AppSettings()

    // Last snapshot on disk (worker only, except [markSaved] before any write is scheduled).
    @Volatile private var writtenGame: Game? = null
    @Volatile private var writtenSettings: AppSettings? = null

    /** Whether the current main file is one we decoded or wrote — only then is it promoted to backup. */
    @Volatile private var mainKnownGood = false

    // Debounce bookkeeping.
    private val scheduled = AtomicBoolean(false)
    @Volatile private var burstStart = 0L
    @Volatile private var lastChange = 0L
    private val debounceTask = Runnable { onDebounce() }

    /** Writes completed so far (for tests and logging). */
    @Volatile var writeCount = 0
        private set

    /** Duration of the most recent successful write (encode + write + fsync + renames), in ns. */
    @Volatile var lastWriteNanos = 0L
        private set

    /** Duration of the most recent encode, in ns. */
    @Volatile var lastEncodeNanos = 0L
        private set

    private var loggedWriteFailure = false

    // ---- load -------------------------------------------------------------------------------

    /**
     * Reads and decodes the save. Synchronous (the file is ~2 KB; call it before the first frame).
     * Never throws: unreadable/corrupt candidates are reported in [LoadResult.failures].
     */
    fun load(): LoadResult {
        val t0 = System.nanoTime()
        val failures = ArrayList<String>(0)
        var result: SaveFile? = null
        var source = Source.NONE
        var readNanos = 0L
        for ((file, src) in arrayOf(mainFile to Source.MAIN, tempFile to Source.TEMP, backupFile to Source.BACKUP)) {
            if (!file.exists()) continue
            val r0 = System.nanoTime()
            val bytes = try {
                file.readBytes()
            } catch (e: IOException) {
                failures += "${src.label}: read failed (${e.message})"
                continue
            } finally {
                readNanos += System.nanoTime() - r0
            }
            when (val d = SaveCodec.decode(bytes)) {
                is DecodeResult.Success -> {
                    result = d.save
                    source = src
                }
                is DecodeResult.Failure -> {
                    failures += "${src.label}: ${d.reason} (${d.detail}, ${bytes.size} bytes)"
                    if (src == Source.MAIN && d.reason == FailureReason.UNSUPPORTED_VERSION) setAsideNewer()
                }
            }
            if (result != null) break
        }
        mainKnownGood = source == Source.MAIN
        return LoadResult(result, source, failures, System.nanoTime() - t0, readNanos)
    }

    /** A save from a newer app version: keep it aside rather than overwrite it on the next write. */
    private fun setAsideNewer() {
        val aside = File(dir, "$FILE_NAME.newer")
        if (!aside.exists()) mainFile.renameTo(aside)
    }

    // ---- save -------------------------------------------------------------------------------

    /**
     * Records [game]/[settings] as already on disk (after a restore), so an unchanged state isn't
     * rewritten. Call before the first [update].
     */
    fun markSaved(game: Game, settings: AppSettings) {
        latestGame = game
        latestSettings = settings
        writtenGame = game
        writtenSettings = settings
    }

    /**
     * Main thread, after any change. Stores the snapshot and makes sure a debounced write is pending.
     * Cheap: two volatile writes, one clock read, and a post only on the first change of a burst.
     */
    fun update(game: Game, settings: AppSettings) {
        latestGame = game
        latestSettings = settings
        val now = clock.now()
        lastChange = now
        if (scheduled.compareAndSet(false, true)) {
            burstStart = now
            scheduler.postDelayed(debounceTask, debounceMillis)
        }
    }

    /**
     * Writes the latest snapshot now (on the worker) if it isn't on disk yet. With [waitMillis] > 0
     * the caller blocks until that write is done or the timeout passes — `onPause` uses this so a
     * swipe-away or sleep right after a tap never loses it. Returns false on timeout.
     */
    fun flush(waitMillis: Long = 0): Boolean {
        if (waitMillis <= 0) {
            scheduler.post { writeLatest() }
            return true
        }
        val done = CountDownLatch(1)
        scheduler.post {
            try {
                writeLatest()
            } finally {
                done.countDown()
            }
        }
        return try {
            done.await(waitMillis, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun onDebounce() {
        val now = clock.now()
        val quiet = now - lastChange
        val waited = now - burstStart
        if (quiet < debounceMillis && waited < maxDelayMillis) {
            scheduler.postDelayed(debounceTask, minOf(debounceMillis - quiet, maxDelayMillis - waited))
            return
        }
        // Clear before reading the snapshot: a change racing with this write re-arms the debounce.
        scheduled.set(false)
        writeLatest()
    }

    /** Worker thread only. */
    private fun writeLatest() {
        val game = latestGame ?: return
        val settings = latestSettings
        if (game === writtenGame && settings === writtenSettings) return
        try {
            val t0 = System.nanoTime()
            val bytes = SaveCodec.encode(SaveFile(game, settings))
            val t1 = System.nanoTime()
            writeAtomically(bytes)
            writtenGame = game
            writtenSettings = settings
            lastEncodeNanos = t1 - t0
            lastWriteNanos = System.nanoTime() - t0
            writeCount++
            loggedWriteFailure = false
            onWritten(lastWriteNanos, lastEncodeNanos, bytes.size)
        } catch (e: Exception) {
            // Keep the worker alive; the next change (or flush) retries. Log once per failure streak.
            if (!loggedWriteFailure) {
                loggedWriteFailure = true
                log("save failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun writeAtomically(bytes: ByteArray) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        writeFile(tempFile, bytes)
        if (mainFile.exists()) {
            if (mainKnownGood) {
                if (!mainFile.renameTo(backupFile)) throw IOException("rename main -> bak failed")
            } else if (!mainFile.delete()) {
                throw IOException("delete bad main failed")
            }
        }
        if (!tempFile.renameTo(mainFile)) throw IOException("rename tmp -> main failed")
        mainKnownGood = true
    }

    private val Source.label get() = name.lowercase()

    companion object {
        const val FILE_NAME = "save.dtsv"
        const val DEFAULT_DEBOUNCE_MILLIS = 1_000L
        const val DEFAULT_MAX_DELAY_MILLIS = 10_000L

        /** Write all of [bytes] to [file], fsync, close. */
        fun writeAndSync(file: File, bytes: ByteArray) {
            FileOutputStream(file).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
        }
    }
}
