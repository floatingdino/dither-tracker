package au.com.josephmark.dithertracker.layout

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

/** T60: lattice arrangements — seats of their own sizes, empty cells — placed on the grid. */
class LatticeSeatingTest {

    private fun seat(l: Lattice, w: Int = 468, h: Int = 351, reach: Int = 90) =
        LatticeSeating(l, Arrangement.DiamondAt.JUNCTION, w, h, reach)

    private fun overlaps(a: IRect, b: IRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    @Test fun parsing() {
        val l = Lattice.of("aa", "bc", facing = "nss")
        assertEquals(3, l.seats)
        assertEquals("aa/bc:nss", l.toString())
        assertEquals(1, Lattice.of("ab", ".c", facing = "nns").emptyCells)
        try { Lattice.of("ab", "ba", facing = "ns"); throw AssertionError("accepted a non-rectangular seat") } catch (_: IllegalArgumentException) {}
        try { Lattice.of("ab", "cc", facing = "ns"); throw AssertionError("accepted too few facings") } catch (_: IllegalArgumentException) {}
    }

    @Test fun fullWidthSeatOverTwoHalves() {
        val s = seat(Lattice.of("aa", "bc", facing = "nss"))
        // Seat 0: far, the whole width; then clockwise: near right, near left.
        assertEquals(IRect(0, 0, 467, 175), s.seats[0])
        assertEquals(IRect(234, 176, 467, 351), s.seats[1])
        assertEquals(IRect(0, 176, 233, 351), s.seats[2])
        assertArrayEquals(intArrayOf(180, 0, 0), s.rotation)
        // One content frame: the smallest seat, 233 x 175; the wide seat holds it centred on its near edge
        // (the far seat's near edge is the top... its player sits at the screen top: content at y 0).
        assertEquals(233, s.frameW); assertEquals(175, s.frameH)
        assertEquals(IRect(117, 0, 350, 175), s.panels[0])
        assertEquals(s.seats[1], s.panels[1])
        // Dividers: the long one whole, the near vertical under it.
        assertTrue(s.dividers.any { it == IRect(0, 175, 467, 176) })
        assertTrue("${s.dividers.toList()}", s.dividers.any { it == IRect(233, 175, 234, 351) })
        assertEquals(233, s.diamondX); assertEquals(175, s.diamondY)
    }

    @Test fun emptyCornerIsGroundAndNotASeat() {
        val s = seat(Lattice.of("ab", ".c", facing = "nns"))
        assertEquals(3, s.players)
        assertEquals(listOf(IRect(0, 176, 233, 351)), s.empties.toList())
        for (p in s.seats) for (e in s.empties) assertFalse(overlaps(p, e))
        // The empty cell is outlined where it meets seats.
        assertTrue(s.dividers.any { it.contains(233, 200) })
        assertTrue(s.dividers.any { it.contains(100, 175) })
        // The diamond still sits at the crossing (where three tiles and the hole meet).
        assertEquals(233, s.diamondX); assertEquals(175, s.diamondY)
    }

    @Test fun tilingInvariantsBothOrientations() {
        val ls = listOf(
            Lattice.of("aa", "bc", facing = "nss"), Lattice.of("ab", "cc", facing = "nns"),
            Lattice.of("ab", ".c", facing = "nns"), Lattice.of("a.", ".b", facing = "ns"),
            Lattice.of("aabbcc", "dddeee", facing = "nnnss"), Lattice.of("abc", "de.", facing = "nnnss"),
            Lattice.of("ab", "ac", facing = "wns"),
        )
        for (l in ls) for ((w, h) in listOf(1872 to 1404, 1404 to 1872, 2560 to 1600)) for (sc in 2..6) {
            val g = DitherGrid(w, h, sc)
            val s = seat(l, g.width, g.height)
            val all = IRect(0, 0, g.width, g.height)
            for (i in 0 until s.players) {
                val t = s.seats[i]
                assertTrue("$l $g $i", t.left >= 0 && t.top >= 0 && t.right <= all.right && t.bottom <= all.bottom)
                val p = s.panels[i]
                assertTrue("$l $g frame in tile", p.left >= t.left && p.top >= t.top && p.right <= t.right && p.bottom <= t.bottom)
                for (j in 0 until s.players) if (j != i) assertFalse("$l $g $i/$j", overlaps(t, s.seats[j]))
                for (e in s.empties) assertFalse(overlaps(t, e))
                for (d in s.dividers) assertFalse("$l $g divider over seat $i", overlaps(d, t))
            }
            for (d in s.dividers) assertTrue(d.width == 1 || d.height == 1)
        }
    }

    @Test fun clockwiseOrder() {
        val s = seat(Lattice.of("ab", "dc", facing = "nnss"))
        assertEquals(IRect(0, 0, 233, 175), s.seats[0])
        assertEquals(IRect(234, 0, 467, 175), s.seats[1])
        assertEquals(IRect(234, 176, 467, 351), s.seats[2])
        assertEquals(IRect(0, 176, 233, 351), s.seats[3])
    }

    /** The 4-grid default renders exactly as before T60 (CRC of the idle render, both colour modes). */
    @Test fun fourGridDefaultIsByteIdentical() {
        val rig = CountRig(1872, 1404, 4, 4)
        rig.dispatch(Action.SetLife(rig.id(1), 27))
        rig.advance(10_000)
        fun crc(b: ByteArray) = CRC32().apply { update(b) }.value
        val normal = crc(rig.render(false).px.copyOf())
        val inverted = crc(rig.render(true).px.copyOf())
        println("T60 4-grid crc $normal $inverted")
        assertEquals(GRID_CRC, normal)
        assertEquals(GRID_CRC_INV, inverted)
    }

    companion object {
        const val GRID_CRC = 599215263L // pre-T60 HEAD ac0f65f
        const val GRID_CRC_INV = 3045648888L
    }
}
