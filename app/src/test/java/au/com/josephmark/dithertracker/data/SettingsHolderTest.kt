package au.com.josephmark.dithertracker.data

import au.com.josephmark.dithertracker.core.serial.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHolderTest {

    @Test fun updateNotifiesOnlyOnRealChangesInOrder() {
        val h = SettingsHolder()
        val calls = ArrayList<String>()
        val a = SettingsHolder.Listener { _, new -> calls += "a${new.fullRefreshEvery}" }
        h.addListener(a)
        h.addListener(a) // duplicate ignored
        h.addListener { old, new -> calls += "b${old.fullRefreshEvery}>${new.fullRefreshEvery}" }
        h.update(AppSettings()) // equal: no-op
        h.update(AppSettings(fullRefreshEvery = 5))
        assertEquals(listOf("a5", "b20>5"), calls)
        h.removeListener(a)
        h.update(AppSettings(fullRefreshEvery = 6))
        assertEquals(listOf("a5", "b20>5", "b5>6"), calls)
        assertTrue(h.current.fullRefreshEvery == 6)
    }
}
