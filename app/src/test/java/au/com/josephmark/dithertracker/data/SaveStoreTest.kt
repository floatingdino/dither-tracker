package au.com.josephmark.dithertracker.data

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Game
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.core.serial.SaveCodec
import au.com.josephmark.dithertracker.core.serial.SaveFile
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SaveStoreTest {

    /** Virtual-time single-thread scheduler: [post] runs inline, delayed tasks run on [advance]. */
    private class FakeScheduler : SaveStore.Scheduler, SaveStore.Clock {
        var time = 0L
        private val queue = ArrayList<Pair<Long, Runnable>>()
        var posts = 0

        override fun now() = time
        override fun post(task: Runnable) = task.run()
        override fun postDelayed(task: Runnable, delayMillis: Long) {
            posts++
            queue += (time + delayMillis) to task
        }

        fun advance(millis: Long) {
            val end = time + millis
            while (true) {
                val next = queue.filter { it.first <= end }.minByOrNull { it.first } ?: break
                queue.remove(next)
                time = next.first
                next.second.run()
            }
            time = end
        }
    }

    private lateinit var dir: File
    private val sched = FakeScheduler()
    private val writes = ArrayList<ByteArray>()

    @Before fun setUp() {
        dir = Files.createTempDirectory("savestore").toFile()
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    private fun store(
        writeFile: (File, ByteArray) -> Unit = { f, b -> writes += b; SaveStore.writeAndSync(f, b) },
    ) = SaveStore(dir, sched, sched, debounceMillis = 1_000, maxDelayMillis = 10_000, writeFile = writeFile)

    private fun game(life0: Int): Game {
        val g = Game(GameState.create(4))
        return g.apply(Action.ChangeLife(0, life0 - 40), 1_000)
    }

    private fun lifeOf(save: SaveFile?) = save!!.game.state.players[0].life

    @Test fun missingFilesLoadAsNoSave() {
        val r = store().load()
        assertNull(r.save)
        assertEquals(SaveStore.Source.NONE, r.source)
        assertTrue(r.failures.isEmpty())
    }

    @Test fun flushWritesImmediatelyAndRoundTrips() {
        val s = store()
        val settings = AppSettings(inverted = true)
        s.update(game(33), settings)
        assertEquals(0, s.writeCount)
        assertTrue(s.flush())
        assertEquals(1, s.writeCount)

        val r = store().load()
        assertEquals(SaveStore.Source.MAIN, r.source)
        assertEquals(33, lifeOf(r.save))
        assertTrue(r.save!!.settings.inverted)
        assertFalse(s.tempFile.exists())
    }

    @Test fun flushWithNothingNewDoesNotWrite() {
        val s = store()
        s.flush()
        val g = game(30)
        s.update(g, AppSettings())
        s.flush()
        s.flush()
        assertEquals(1, s.writeCount)
        s.markSaved(game(20), AppSettings())
        s.flush()
        assertEquals(1, s.writeCount)
    }

    @Test fun debounceCoalescesABurstIntoOneWriteOfTheLatest() {
        val s = store()
        var postsFromUpdate = 0
        for (i in 1..20) {
            val before = sched.posts
            s.update(game(40 - i), AppSettings())
            postsFromUpdate += sched.posts - before
            sched.advance(200) // taps 200 ms apart: never quiet for a full second
        }
        assertEquals(0, s.writeCount)
        assertEquals(1, postsFromUpdate) // only the first change of the burst touched the scheduler
        sched.advance(1_000)
        assertEquals(1, s.writeCount)
        assertEquals(20, lifeOf(SaveCodec.decodeOrNull(writes.single())))
        sched.advance(5_000)
        assertEquals(1, s.writeCount) // nothing new, nothing written
    }

    @Test fun maxDelayForcesAWriteDuringEndlessTapping() {
        val s = store()
        for (i in 0 until 120) { // 12 s of taps every 100 ms
            s.update(game(i), AppSettings())
            sched.advance(100)
        }
        assertEquals(1, s.writeCount) // at the 10 s cap
        sched.advance(1_000)
        assertEquals(2, s.writeCount)
        assertEquals(119, lifeOf(store().load().save))
    }

    @Test fun flushInsideTheDebounceWindowWritesNowAndTheTimerThenNoOps() {
        val s = store()
        s.update(game(12), AppSettings())
        sched.advance(300)
        s.flush()
        assertEquals(1, s.writeCount)
        sched.advance(2_000)
        assertEquals(1, s.writeCount)
        // A change after the timer fired arms a new burst.
        s.update(game(11), AppSettings())
        sched.advance(1_000)
        assertEquals(2, s.writeCount)
    }

    @Test fun settingsChangeAloneIsSaved() {
        val s = store()
        val g = game(40)
        s.markSaved(g, AppSettings())
        s.update(g, AppSettings(fullRefreshEvery = 7))
        s.flush()
        assertEquals(7, store().load().save!!.settings.fullRefreshEvery)
    }

    @Test fun eachWriteKeepsThePreviousGoodFileAsBackup() {
        val s = store()
        s.update(game(30), AppSettings()); s.flush()
        s.update(game(29), AppSettings()); s.flush()
        assertEquals(29, lifeOf(SaveCodec.decodeOrNull(s.mainFile.readBytes())))
        assertEquals(30, lifeOf(SaveCodec.decodeOrNull(s.backupFile.readBytes())))
    }

    @Test fun crashMidWriteLeavesTheOldFileIntact() {
        var crash = false
        val s = store { f, b ->
            if (crash) {
                f.writeBytes(b.copyOf(b.size / 2)) // partial temp file, then the "process dies"
                throw IOException("simulated crash")
            }
            SaveStore.writeAndSync(f, b)
        }
        s.update(game(25), AppSettings()); s.flush()
        val good = s.mainFile.readBytes()
        crash = true
        s.update(game(24), AppSettings()); s.flush()
        assertArrayEquals(good, s.mainFile.readBytes())
        val r = store().load()
        assertEquals(SaveStore.Source.MAIN, r.source)
        assertEquals(25, lifeOf(r.save))
        // The worker survived: the next write succeeds and replaces the partial temp file.
        crash = false
        s.update(game(23), AppSettings()); s.flush()
        assertEquals(23, lifeOf(store().load().save))
        assertFalse(s.tempFile.exists())
    }

    @Test fun crashBetweenRenamesStillLoadsAWholeFile() {
        val s = store()
        s.update(game(30), AppSettings()); s.flush()
        // State after "main -> bak" but before "tmp -> main": no main, full temp, old backup.
        s.mainFile.renameTo(s.backupFile)
        s.tempFile.writeBytes(SaveCodec.encode(SaveFile(game(29))))
        val r = store().load()
        assertEquals(SaveStore.Source.TEMP, r.source)
        assertEquals(29, lifeOf(r.save))
        // And with only the backup left.
        s.tempFile.delete()
        assertEquals(30, lifeOf(store().load().save))
    }

    @Test fun corruptMainFallsBackToBackupAndIsNotPromotedToBackup() {
        val s = store()
        s.update(game(30), AppSettings()); s.flush()
        s.update(game(29), AppSettings()); s.flush()
        val main = s.mainFile.readBytes()
        s.mainFile.writeBytes(main.copyOf(main.size - 7)) // truncated

        val s2 = store()
        val r = s2.load()
        assertEquals(SaveStore.Source.BACKUP, r.source)
        assertEquals(30, lifeOf(r.save))
        assertEquals(1, r.failures.size)
        assertTrue(r.failures[0], r.failures[0].startsWith("main: CHECKSUM_MISMATCH"))

        // The next write replaces the corrupt main and keeps the good backup.
        s2.markSaved(r.save!!.game, r.save!!.settings)
        s2.update(game(28), AppSettings()); s2.flush()
        assertEquals(28, lifeOf(SaveCodec.decodeOrNull(s2.mainFile.readBytes())))
        assertEquals(30, lifeOf(SaveCodec.decodeOrNull(s2.backupFile.readBytes())))
    }

    @Test fun bothCorruptLoadsNoSaveWithoutThrowing() {
        val s = store()
        s.mainFile.writeBytes(byteArrayOf(1, 2, 3))
        val junk = "not a save at all, just text".toByteArray()
        s.backupFile.writeBytes(junk)
        val r = s.load()
        assertNull(r.save)
        assertEquals(SaveStore.Source.NONE, r.source)
        assertEquals(2, r.failures.size)
        // Writing afterwards works and doesn't promote the junk main.
        s.update(game(40), AppSettings()); s.flush()
        assertEquals(40, lifeOf(store().load().save))
        assertArrayEquals(junk, s.backupFile.readBytes())
    }

    @Test fun emptyMainFileFallsBack() {
        val s = store()
        s.update(game(31), AppSettings()); s.flush()
        s.update(game(32), AppSettings()); s.flush()
        s.mainFile.writeBytes(ByteArray(0))
        assertEquals(31, lifeOf(store().load().save))
    }

    @Test fun saveFromANewerVersionIsSetAsideNotOverwritten() {
        val bytes = SaveCodec.encode(SaveFile(game(10)))
        bytes[5] = 99 // version 99
        val s = store()
        s.mainFile.writeBytes(bytes)
        assertNull(s.load().save)
        assertTrue(File(dir, "${SaveStore.FILE_NAME}.newer").exists())
        s.update(game(40), AppSettings()); s.flush()
        assertArrayEquals(bytes, File(dir, "${SaveStore.FILE_NAME}.newer").readBytes())
    }

    @Test fun flushWaitsForTheWorkerThread() {
        val exec = Executors.newSingleThreadScheduledExecutor()
        try {
            val real = object : SaveStore.Scheduler {
                override fun post(task: Runnable) {
                    exec.execute(task)
                }

                override fun postDelayed(task: Runnable, delayMillis: Long) {
                    exec.schedule(task, delayMillis, TimeUnit.MILLISECONDS)
                }
            }
            val workerWrites = ArrayList<String>()
            val s = SaveStore(dir, real, { System.nanoTime() / 1_000_000 }, writeFile = { f, b ->
                workerWrites += Thread.currentThread().name
                Thread.sleep(50) // slow disk
                SaveStore.writeAndSync(f, b)
            })
            s.update(game(17), AppSettings())
            assertTrue(s.flush(waitMillis = 5_000))
            assertEquals(1, s.writeCount)
            assertFalse(workerWrites.single() == Thread.currentThread().name)
            assertEquals(17, lifeOf(store().load().save))
        } finally {
            exec.shutdownNow()
        }
    }
}
