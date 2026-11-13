package au.com.josephmark.dithertracker.eink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorRefreshGuardTest {
    private val logs = ArrayList<String>()
    private var resolves = 0
    private val calls = ArrayList<Pair<Boolean, Int>>()

    private fun guard(supernote: Boolean = true, resolve: () -> VendorRefreshGuard.Invoker?) =
        VendorRefreshGuard(supernote, { resolves++; resolve() }, { logs += it })

    private val ok = VendorRefreshGuard.Invoker { a, m -> calls += a to m }

    @Test fun manufacturerCheck() {
        assertTrue(VendorRefreshGuard.isSupernote("Supernote"))
        assertTrue(VendorRefreshGuard.isSupernote("SUPERNOTE"))
        assertFalse(VendorRefreshGuard.isSupernote("Google"))
        assertFalse(VendorRefreshGuard.isSupernote(null))
    }

    @Test fun notASupernoteNeverResolvesOrLogs() {
        val g = guard(supernote = false) { ok }
        assertFalse(g.tryRefresh())
        assertFalse(g.tryRefresh())
        assertEquals(0, resolves)
        assertTrue(calls.isEmpty())
        assertTrue(logs.isEmpty())
    }

    @Test fun resolvesOnceAndCallsFalseOne() {
        val g = guard { ok }
        assertTrue(g.tryRefresh())
        assertTrue(g.tryRefresh())
        assertEquals(1, resolves)
        assertEquals(listOf(false to 1, false to 1), calls)
        assertEquals(2, g.calls)
    }

    @Test fun missingServiceDisablesAndLogsOnce() {
        val g = guard { null }
        assertFalse(g.tryRefresh())
        assertFalse(g.tryRefresh())
        assertTrue(g.disabled)
        assertEquals(1, resolves)
        assertEquals(1, logs.size)
    }

    @Test fun resolverThrowingDisablesAndLogsOnce() {
        val g = guard { throw NoSuchMethodException("screenRefresh") }
        assertFalse(g.tryRefresh())
        assertFalse(g.tryRefresh())
        assertEquals(1, resolves)
        assertEquals(1, logs.size)
    }

    @Test fun callThrowingAnythingDisablesForTheSession() {
        var n = 0
        val g = guard { VendorRefreshGuard.Invoker { _, _ -> n++; throw LinkageError("boom") } }
        assertFalse(g.tryRefresh())
        assertFalse(g.tryRefresh())
        assertEquals(1, n)
        assertTrue(g.disabled)
        assertEquals(1, logs.size)
    }
}
