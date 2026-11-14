package au.com.josephmark.dithertracker.eink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T58: [ViewModeGuard] — the per-view e-ink mode's guard (manufacturer, resolve once, fail → off). */
class ViewModeGuardTest {
    private val logs = ArrayList<String>()
    private var resolves = 0
    private val calls = ArrayList<String>()
    private val view = Any()

    private fun guard(supernote: Boolean = true, resolve: () -> ViewModeGuard.Calls?) =
        ViewModeGuard(supernote, { resolves++; resolve() }, { logs += it })

    private inner class Ok(var mode: Int = 0) : ViewModeGuard.Calls {
        override fun set(view: Any, mode: Int) { calls += "set $mode"; this.mode = mode }
        override fun reset(view: Any) { calls += "reset"; mode = 0 }
        override fun read(view: Any): Int = mode
    }

    @Test fun notASupernoteNeverResolvesNorLogs() {
        val g = guard(supernote = false) { Ok() }
        assertFalse(g.available)
        assertFalse(g.set(view, EinkViewMode.DU))
        assertEquals(ViewModeGuard.UNKNOWN, g.read(view))
        assertEquals(0, resolves)
        assertTrue(calls.isEmpty())
        assertTrue(logs.isEmpty())
    }

    @Test fun resolvesOnceLazilyAndSetsReadsResets() {
        val g = guard { Ok() }
        assertEquals("nothing resolved before first use", 0, resolves)
        assertTrue(g.available)
        assertTrue(g.set(view, EinkViewMode.DU))
        assertEquals(EinkViewMode.DU, g.read(view))
        assertTrue(g.set(view, EinkViewMode.NORMAL))
        assertEquals(0, g.read(view))
        assertEquals(1, resolves)
        assertEquals(listOf("set 1", "reset"), calls)
        assertEquals(2, g.sets)
        assertTrue(logs.isEmpty())
    }

    @Test fun missingMethodsDisableAndLogOnce() {
        val g = guard { null }
        assertFalse(g.available)
        assertFalse(g.set(view, EinkViewMode.DU))
        assertEquals(ViewModeGuard.UNKNOWN, g.read(view))
        assertTrue(g.disabled)
        assertEquals(1, resolves)
        assertEquals(1, logs.size)
    }

    @Test fun resolverThrowingDisablesAndLogsOnce() {
        val g = guard { throw NoSuchMethodException("setEinkUpdateMode") }
        assertFalse(g.set(view, EinkViewMode.DU))
        assertFalse(g.set(view, EinkViewMode.DU))
        assertFalse(g.available)
        assertEquals(1, resolves)
        assertEquals(1, logs.size)
    }

    @Test fun aSetThrowingAnythingDisablesForTheSession() {
        var n = 0
        val g = guard {
            object : ViewModeGuard.Calls {
                override fun set(view: Any, mode: Int) { n++; throw SecurityException("hidden api") }
                override fun reset(view: Any) { n++ }
                override fun read(view: Any): Int = 0
            }
        }
        assertFalse(g.set(view, EinkViewMode.DU))
        assertFalse(g.set(view, EinkViewMode.NORMAL))
        assertEquals(1, n)
        assertTrue(g.disabled)
        assertFalse(g.available)
        assertEquals(1, logs.size)
    }

    @Test fun aReadThrowingDisablesToo() {
        val g = guard {
            object : ViewModeGuard.Calls {
                override fun set(view: Any, mode: Int) {}
                override fun reset(view: Any) {}
                override fun read(view: Any): Int = throw ClassCastException()
            }
        }
        assertTrue(g.set(view, EinkViewMode.DU))
        assertEquals(ViewModeGuard.UNKNOWN, g.read(view))
        assertFalse(g.set(view, EinkViewMode.DU))
        assertEquals(1, logs.size)
    }
}
