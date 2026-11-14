package au.com.josephmark.dithertracker.eink

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.FakeScheduler
import au.com.josephmark.dithertracker.play.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCleanerTest {
    private val clock = FakeScheduler(4)
    private val redraws = ArrayList<IRect>()
    private val done = ArrayList<Boolean>()
    private val cleaner = ScreenCleaner(clock, 2, object : ScreenCleaner.Host {
        override fun redraw(region: IRect) { redraws += IRect(region.left, region.top, region.right, region.bottom) }
        override fun onCleaned(full: Boolean) { done += full }
    })
    private val screen = IRect(0, 0, 400, 300)
    private val tile = IRect(200, 150, 400, 300)

    init {
        clock.target = Scheduler.Target { cleaner.onTimer(it) }
        cleaner.screen.set(0, 0, 400, 300)
    }

    @Test fun fullCleanIsOneBlackFrameHeldThenRedraw() {
        cleaner.cleanFull()
        assertTrue(cleaner.active)
        assertTrue(cleaner.black)
        assertTrue(cleaner.full)
        assertEquals(listOf(screen), redraws)
        assertEquals(ScreenCleaner.HOLD_MS, clock.dueAt(2))
        clock.advance(ScreenCleaner.HOLD_MS - 1)
        assertTrue(cleaner.active)
        clock.advance(1)
        assertFalse(cleaner.active)
        assertFalse(cleaner.black)
        assertEquals(listOf(screen, screen), redraws)
        assertEquals(listOf(true), done)
        assertEquals(1, cleaner.count)
    }

    @Test fun regionCleanTouchesOnlyTheRegion() {
        cleaner.cleanRegion(tile)
        assertFalse(cleaner.full)
        assertEquals(tile, cleaner.region)
        clock.advance(ScreenCleaner.HOLD_MS)
        assertEquals(listOf(tile, tile), redraws)
        assertEquals(listOf(false), done)
    }

    @Test fun aTouchCancelsAtOnceAndIsNotReportedDone() {
        cleaner.cleanRegion(tile)
        cleaner.cancel()
        assertFalse(cleaner.active)
        assertFalse(clock.isArmed(2))
        assertEquals(listOf(tile, tile), redraws)
        assertTrue(done.isEmpty())
        cleaner.cancel() // idle: nothing
        assertEquals(2, redraws.size)
    }

    @Test fun restartingCancelsTheRunningCleanFirst() {
        cleaner.cleanRegion(tile)
        cleaner.cleanFull()
        assertEquals(listOf(tile, tile, screen), redraws) // old region redrawn, new one inked
        clock.advance(ScreenCleaner.HOLD_MS)
        assertEquals(listOf(true), done)
    }

    @Test fun foreignTimersAreIgnored() {
        cleaner.cleanFull()
        cleaner.onTimer(1)
        assertTrue(cleaner.active)
    }

    @Test fun vendorRefreshReplacesTheFlashForFullCleansOnly() {
        var calls = 0
        cleaner.vendorFull = ScreenCleaner.FullRefresh { calls++; true }
        cleaner.cleanFull()
        assertTrue(cleaner.active)
        assertFalse(cleaner.black)
        assertTrue(redraws.isEmpty()) // nothing drawn
        clock.advance(ScreenCleaner.VENDOR_DELAY_MS)
        assertEquals(1, calls)
        assertFalse(cleaner.active)
        assertEquals(listOf(true), done)
        assertTrue(redraws.isEmpty())
        // A region clean still flashes.
        cleaner.cleanRegion(tile)
        assertTrue(cleaner.black)
        assertEquals(1, calls)
    }

    @Test fun vendorFailureFallsBackToTheFlash() {
        cleaner.vendorFull = ScreenCleaner.FullRefresh { false }
        cleaner.cleanFull()
        clock.advance(ScreenCleaner.VENDOR_DELAY_MS)
        assertTrue(cleaner.black)
        assertEquals(listOf(screen), redraws)
        clock.advance(ScreenCleaner.HOLD_MS)
        assertEquals(listOf(screen, screen), redraws)
        assertEquals(listOf(true), done)
    }

    @Test fun cancellingAPendingVendorRefreshDrawsNothing() {
        var calls = 0
        cleaner.vendorFull = ScreenCleaner.FullRefresh { calls++; true }
        cleaner.cleanFull()
        cleaner.cancel()
        clock.advance(1_000)
        assertEquals(0, calls)
        assertTrue(redraws.isEmpty())
        assertTrue(done.isEmpty())
    }

    // ---- T58: the GC clean ------------------------------------------------------------------------

    private val gcCalls = ArrayList<Boolean>()

    private fun gc(available: Boolean = true) {
        cleaner.waveform = ScreenCleaner.Waveform { on -> gcCalls += on; available || !on }
    }

    @Test fun gcCleanRedrawsInGcWithNoInkFrameThenRestores() {
        gc()
        cleaner.cleanRegion(tile)
        assertTrue(cleaner.active)
        assertTrue(cleaner.gc)
        assertTrue(cleaner.viaGc)
        assertFalse("no ink frame", cleaner.black)
        assertEquals(listOf(true), gcCalls) // switched before the redraw
        assertEquals(listOf(tile), redraws)
        clock.advance(ScreenCleaner.HOLD_MS - 1)
        assertEquals(listOf(true), gcCalls)
        clock.advance(1)
        assertFalse(cleaner.active)
        assertFalse(cleaner.gc)
        assertEquals(listOf(true, false), gcCalls)
        assertEquals("one redraw only", listOf(tile), redraws)
        assertEquals(listOf(false), done)
    }

    @Test fun gcFullClean() {
        gc()
        cleaner.cleanFull()
        assertEquals(listOf(screen), redraws)
        clock.advance(ScreenCleaner.HOLD_MS)
        assertEquals(listOf(true, false), gcCalls)
        assertEquals(listOf(true), done)
    }

    @Test fun aTouchDuringAGcCleanRestoresTheWaveformAtOnce() {
        gc()
        cleaner.cleanRegion(tile)
        cleaner.cancel()
        assertFalse(cleaner.active)
        assertFalse(cleaner.gc)
        assertFalse(clock.isArmed(2))
        assertEquals(listOf(true, false), gcCalls)
        assertEquals("nothing inked, nothing to redraw", listOf(tile), redraws)
        assertTrue(done.isEmpty())
        cleaner.cancel()
        assertEquals(listOf(true, false), gcCalls)
    }

    @Test fun gcUnavailableFallsBackToTheInkFrame() {
        gc(available = false)
        cleaner.cleanRegion(tile)
        assertTrue(cleaner.black)
        assertFalse(cleaner.viaGc)
        clock.advance(ScreenCleaner.HOLD_MS)
        assertEquals(listOf(true), gcCalls) // asked once, never "restored"
        assertEquals(listOf(tile, tile), redraws)
        assertEquals(listOf(false), done)
    }

    @Test fun vendorRefreshStillTakesFullCleansWhenBothAreOn() {
        gc()
        var calls = 0
        cleaner.vendorFull = ScreenCleaner.FullRefresh { calls++; true }
        cleaner.cleanFull()
        clock.advance(ScreenCleaner.VENDOR_DELAY_MS)
        assertEquals(1, calls)
        assertTrue(gcCalls.isEmpty())
        cleaner.cleanRegion(tile) // seats use GC
        assertEquals(listOf(true), gcCalls)
    }
}
