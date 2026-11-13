package au.com.josephmark.dithertracker.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FxLayerTest {

    private fun ascii(l: FxLayer) = (0 until l.height).map { y -> String(CharArray(l.width) { x -> if (l.isInk(x, y)) '#' else '.' }) }

    @Test fun runLengthCodeDecodesRowMajorAlternatingRuns() {
        // .#. / ##.  →  unset 1, set 1, unset 1, set 2 (trailing unset run omitted).
        val l = FxLayer.rle(3, 2, "BBBC")
        assertEquals(listOf(".#.", "##."), ascii(l))
        assertEquals(3, l.inkCount)
    }

    @Test fun longRunsUseContinuationDigitsAndPartsConcatenate() {
        // A set run of 40 = 8 + 1·32: 'o' (index 40 = more | 8) then 'B' (1). Then unset 3, set 2.
        val l = FxLayer.rle(45, 1, "AoB", "DC")
        assertEquals(listOf("#".repeat(40) + "...##"), ascii(l))
        assertEquals(2, l.stride)
    }

    @Test fun emptyCodeIsAnEmptyLayerWithoutStorage() {
        val l = FxLayer.rle(233, 175)
        assertTrue(l.isEmpty)
        assertEquals(233, l.width)
        assertTrue(!l.isInk(232, 174))
    }

    @Test(expected = IllegalArgumentException::class)
    fun overflowingRunsAreRejected() {
        FxLayer.rle(2, 2, "AF")
    }

    @Test fun turnedViewsHaveTurnedSizes() {
        val l = FxLayer.rle(3, 2, "BBBC") // .#. / ##.
        assertEquals(listOf(".#.", "##."), ascii(l))
        fun view(r: Int) = l.rotated(r).let { v -> (0 until v.height).map { y -> String(CharArray(v.width) { x -> if (v.isInk(x, y)) '#' else '.' }) } }
        assertEquals(listOf("#.", "##", ".."), view(90))
        assertEquals(listOf(".##", ".#."), view(180))
        assertEquals(listOf("..", "##", ".#"), view(270))
        assertTrue(l.rotated(0) === l)
    }
}
