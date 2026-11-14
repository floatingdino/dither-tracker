package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.MINUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.zone
import au.com.josephmark.dithertracker.stage.PlayInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T57: frames (platform invalidations) per quick tap on the real Play screen. A tap released before the
 * press delay costs ONE frame — its result — instead of two (press inversion, then release + result);
 * on the Nomad the press frame held the region busy ~450 ms before the result could show.
 */
class TapFramesTest {

    private class Taps(delay: Long) {
        val rig = PlayRig()
        val p = rig.platform
        init {
            rig.stage.pressDelayMillis = delay
            p.invalidated.clear(); p.count = 0
        }

        /** Down, [heldMs] on the fake clock, up; returns the device-px rects flushed meanwhile. */
        fun tap(gx: Int, gy: Int, heldMs: Long = 60): List<IRect> {
            p.invalidated.clear()
            rig.stage.down(0, gx, gy, p.clock.now)
            p.clock.advance(heldMs)
            rig.stage.up(0)
            return p.invalidated.toList()
        }

        fun pct(r: IRect) = 100.0 * (r.right - r.left) * (r.bottom - r.top) / (1872.0 * 1404.0)
        fun lifeMinus(seat: Int) = PlayRig.mid(rig.l.zones[zone(seat, MINUS)])
    }

    @Test fun quickLifeTapIsOneFrameOfTheNumberAndReadout() {
        val t = Taps(PlayInput.PRESS_DELAY_MS)
        val at = t.lifeMinus(3)
        val frames = t.tap(at[0], at[1])
        assertEquals(39, t.rig.session.state.players[3].life)
        assertEquals("one frame: the result", 1, frames.size)
        // Only the number and readout slots, not the whole half.
        val half = t.rig.l.zones[zone(3, MINUS)]
        val f = frames[0]
        assertTrue("${t.pct(f)} %", t.pct(f) < 25.0)
        println("T57 life tap: 1 frame, ${"%.1f".format(t.pct(f))} % of the screen (half ${"%.1f".format(100.0 * half.width * half.height * 16 / (1872.0 * 1404.0))} %)")
    }

    @Test fun theOldModelCostTwoFramesPerLifeTap() {
        val t = Taps(0)
        val at = t.lifeMinus(3)
        val frames = t.tap(at[0], at[1])
        assertEquals(2, frames.size)
        println("T57 life tap, delay 0: ${frames.size} frames, ${frames.joinToString { "%.1f".format(t.pct(it)) }} %")
    }

    @Test fun heldLifeTapStillPressesThenReleases() {
        val t = Taps(PlayInput.PRESS_DELAY_MS)
        val at = t.lifeMinus(1)
        val frames = t.tap(at[0], at[1], heldMs = 300)
        assertEquals(2, frames.size)
        assertEquals(39, t.rig.session.state.players[1].life)
    }

    @Test fun quickDrawerOpenIsOneFrame() {
        val t = Taps(PlayInput.PRESS_DELAY_MS)
        val tab = t.rig.l.tab[2]
        val frames = t.tap(tab.left + 3, tab.top + 3)
        assertTrue(t.rig.drawer.state.isOpen(2))
        assertEquals(1, frames.size)
        val old = Taps(0)
        val oldFrames = old.tap(tab.left + 3, tab.top + 3)
        assertEquals(2, oldFrames.size)
        println("T57 drawer open: ${frames.size} frame ${"%.1f".format(t.pct(frames[0]))} % (delay 0: ${oldFrames.size}, ${oldFrames.joinToString { "%.1f".format(old.pct(it)) }} %)")
    }

    @Test fun quickGemOpenIsOneFrame() {
        val t = Taps(PlayInput.PRESS_DELAY_MS)
        val l = t.rig.l
        val frames = t.tap(l.diamondCx, l.diamondCy)
        assertEquals(1, frames.size)
        val old = Taps(0)
        val oldFrames = old.tap(l.diamondCx, l.diamondCy)
        assertEquals(2, oldFrames.size)
        println("T57 gem open: ${frames.size} frame ${"%.1f".format(t.pct(frames[0]))} % (delay 0: ${oldFrames.size}, ${oldFrames.joinToString { "%.1f".format(old.pct(it)) }} %)")
    }

    @Test fun nothingIsArmedAfterAQuickTap() {
        val t = Taps(PlayInput.PRESS_DELAY_MS)
        val at = t.lifeMinus(0)
        t.tap(at[0], at[1])
        // Only the readout's commit timer (and the effect's quiet timer) may follow a life change: no input timer.
        for (id in 0 until t.rig.stage.inputTimerCount) assertTrue(!t.p.clock.isArmed(id))
    }
}
