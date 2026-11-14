package au.com.josephmark.dithertracker.eink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T58: [EinkDisplay] — the Display setting / debug override → the root view's mode, and the GC clean's switch. */
class EinkDisplayTest {
    private val sets = ArrayList<Int>()
    private var available = true
    private var current = EinkViewMode.NORMAL
    private val port = object : EinkDisplay.Port {
        override fun set(mode: Int): Boolean {
            if (!available) return false
            sets += mode
            current = mode
            return true
        }

        override fun read(): Int = if (available) current else ViewModeGuard.UNKNOWN
    }
    private val logs = ArrayList<String>()

    @Test fun modeForTheSettingOrTheOverride() {
        assertEquals(EinkViewMode.DU, EinkDisplay.modeFor(fast = true, override = EinkDisplay.NO_OVERRIDE))
        assertEquals(EinkViewMode.NORMAL, EinkDisplay.modeFor(fast = false, override = EinkDisplay.NO_OVERRIDE))
        assertEquals(EinkViewMode.A2, EinkDisplay.modeFor(fast = false, override = EinkViewMode.A2))
        assertEquals("einkMode 0 pins NORMAL", EinkViewMode.NORMAL, EinkDisplay.modeFor(fast = true, override = 0))
    }

    @Test fun fastIsDuNormalResets() {
        val d = EinkDisplay(port, log = { logs += it })
        d.apply(fast = true, why = "create")
        d.apply(fast = true, why = "resume")
        d.apply(fast = false, why = "setting")
        assertEquals(listOf(EinkViewMode.DU, EinkViewMode.DU, EinkViewMode.NORMAL), sets)
        assertEquals("create: mode=1 set=true read=1", logs[0])
    }

    @Test fun theOverrideWinsOverTheSetting() {
        val d = EinkDisplay(port, override = EinkViewMode.DUX)
        d.apply(fast = false, why = "create")
        d.apply(fast = true, why = "setting")
        assertEquals(listOf(EinkViewMode.DUX, EinkViewMode.DUX), sets)
    }

    @Test fun gcCleanSwitchesToGcAndBack() {
        val d = EinkDisplay(port)
        d.apply(fast = true, why = "create")
        assertTrue(d.gc(true))
        assertTrue(d.cleaning)
        assertEquals(EinkViewMode.GC, current)
        assertTrue(d.gc(false))
        assertFalse(d.cleaning)
        assertEquals(EinkViewMode.DU, current)
        assertFalse("restore without a clean is a no-op", d.gc(false))
        assertEquals(listOf(EinkViewMode.DU, EinkViewMode.GC, EinkViewMode.DU), sets)
    }

    @Test fun aSettingChangeDuringAGcCleanAppliesWhenItEnds() {
        val d = EinkDisplay(port)
        d.apply(fast = true, why = "create")
        d.gc(true)
        d.apply(fast = false, why = "setting")
        assertEquals("still GC for the clean's frame", EinkViewMode.GC, current)
        d.gc(false)
        assertEquals(EinkViewMode.NORMAL, current)
    }

    @Test fun unavailableMeansNoGcClean() {
        available = false
        val d = EinkDisplay(port)
        d.apply(fast = true, why = "create")
        assertFalse(d.gc(true))
        assertFalse(d.cleaning)
        assertTrue(sets.isEmpty())
    }
}
