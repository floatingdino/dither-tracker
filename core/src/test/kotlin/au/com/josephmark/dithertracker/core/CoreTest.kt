package au.com.josephmark.dithertracker.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreTest {
    @Test
    fun nameIsStable() {
        assertEquals("dither-tracker-core", Core.NAME)
    }
}
