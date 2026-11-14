package au.com.josephmark.dithertracker.eink

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** T59: the global display speed — FAST off e-ink or with the fast waveform set, SLOW otherwise; the debug override wins. */
class DisplaySpeedTest {

    @After fun reset() {
        DisplaySpeed.einkPanel = false
        DisplaySpeed.fastMode = DisplaySpeed.FastMode { false }
        DisplaySpeed.override = DisplaySpeed.NO_OVERRIDE
    }

    @Test fun notEinkIsFast() {
        assertEquals(DisplaySpeed.FAST, DisplaySpeed.current())
    }

    @Test fun einkFollowsTheWaveformInEffect() {
        var mode = EinkViewMode.NORMAL
        var available = true
        DisplaySpeed.einkPanel = true
        DisplaySpeed.fastMode = DisplaySpeed.FastMode { DisplaySpeed.isFastWaveform(mode) && available }
        assertEquals("normal waveform", DisplaySpeed.SLOW, DisplaySpeed.current())
        mode = EinkViewMode.FAST
        assertEquals(DisplaySpeed.FAST, DisplaySpeed.current())
        available = false
        assertEquals("the guard disabled itself", DisplaySpeed.SLOW, DisplaySpeed.current())
        available = true
        mode = EinkViewMode.GC
        assertEquals(DisplaySpeed.SLOW, DisplaySpeed.current())
        for (m in intArrayOf(EinkViewMode.DU, EinkViewMode.A2, EinkViewMode.DUX)) { mode = m; assertEquals(DisplaySpeed.FAST, DisplaySpeed.current()) }
    }

    /** T62: known e-ink vendors are e-ink panels (SLOW on their normal waveform); LCD phones and the emulator are not. */
    @Test fun einkVendors() {
        fun eink(man: String?, brand: String? = man, model: String? = null) = DisplaySpeed.isEinkDevice(man, brand, model)
        assertEquals(true, eink("Supernote"))
        assertEquals(true, eink("ONYX", "Onyx", "NoteAir3C"))
        assertEquals(true, eink("unknown", "BOOX"))
        assertEquals(true, eink("Bigme", "Bigme", "HiBreak Pro"))
        assertEquals(true, eink("Boyue", "Meebook"))
        assertEquals(true, eink("Hisense", "Hisense", "A9"))
        assertEquals(true, eink("Hisense", "Hisense", "A7 CC"))
        assertEquals(false, eink("Hisense", "Hisense", "A90"))
        assertEquals(false, eink("Hisense", "Hisense", "E50"))
        assertEquals(false, eink("Google", "google", "sdk_gphone64_arm64"))
        assertEquals(false, eink("samsung", "samsung", "SM-X910"))
        assertEquals(false, eink(null, null, null))
    }

    @Test fun overrideWins() {
        DisplaySpeed.override = DisplaySpeed.parse("slow")
        assertEquals(DisplaySpeed.SLOW, DisplaySpeed.current())
        DisplaySpeed.einkPanel = true
        DisplaySpeed.override = DisplaySpeed.parse("FAST")
        assertEquals(DisplaySpeed.FAST, DisplaySpeed.current())
        assertEquals(DisplaySpeed.NO_OVERRIDE, DisplaySpeed.parse("medium"))
        assertEquals(DisplaySpeed.NO_OVERRIDE, DisplaySpeed.parse(null))
    }
}
