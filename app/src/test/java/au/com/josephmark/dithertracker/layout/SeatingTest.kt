package au.com.josephmark.dithertracker.layout

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.MINUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.PLUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.zone
import au.com.josephmark.dithertracker.seat.SeatMap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T11: the arrangement table on the grid, 2–6 players, landscape and portrait, S = 2..6. */
class SeatingTest {

    private val nomad = listOf(1872 to 1404, 1404 to 1872)
    private val screens = nomad + listOf(2560 to 1600, 1280 to 800, 1404 to 1053)

    private fun layouts(players: Int, ids: List<String> = listOf("")) = sequence {
        for ((w, h) in screens) for (s in 2..6) for (id in ids) yield(PlayLayout.compute(DitherGrid(w, h, s), players, id))
    }

    private fun inside(inner: IRect, outer: IRect) =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun overlaps(a: IRect, b: IRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun sideways(r: Int) = r == 90 || r == 270

    // ---- the 2×2 is the T06 layout, exactly ------------------------------------------------------

    @Test fun fourPlayersAreTheT06Grid() {
        for ((w, h) in screens + listOf(320 to 240)) for (s in 2..6) {
            val g = DitherGrid(w, h, s)
            val l = PlayLayout.compute(g)
            if (g.height > g.width) continue // portrait turns the table (below)
            val pw = maxOf(1, (g.width - 1) / 2)
            val ph = maxOf(1, (g.height - 1) / 2)
            val ox = (g.width - (2 * pw + 1)) / 2
            val oy = (g.height - (2 * ph + 1)) / 2
            assertEquals("$g", "4-grid", l.arrangement.id)
            assertEquals(pw, l.panelW); assertEquals(ph, l.panelH)
            assertEquals(IRect(ox, oy, ox + pw, oy + ph), l.panels[0])
            assertEquals(IRect(ox + pw + 1, oy, ox + 2 * pw + 1, oy + ph), l.panels[1])
            assertEquals(IRect(ox + pw + 1, oy + ph + 1, ox + 2 * pw + 1, oy + 2 * ph + 1), l.panels[2])
            assertEquals(IRect(ox, oy + ph + 1, ox + pw, oy + 2 * ph + 1), l.panels[3])
            assertArrayEquals(intArrayOf(180, 180, 0, 0), l.rotation)
            assertEquals(2, l.dividers.size)
            assertEquals(IRect(ox + pw, oy, ox + pw + 1, oy + 2 * ph + 1), l.dividers[0]) // vertical first (draw order)
            assertEquals(IRect(ox, oy + ph, ox + 2 * pw + 1, oy + ph + 1), l.dividers[1])
            assertEquals(ox + pw, l.diamondCx); assertEquals(oy + ph, l.diamondCy)
            assertEquals(l.panels.toList(), l.cells.toList())
        }
    }

    // ---- the chosen arrangement per count on the Nomad ---------------------------------------------

    @Test fun nomadTilesAtScale4() {
        // players to (tile, tier, rotations clockwise from the far left)
        val want = mapOf(
            2 to Triple("468x175", PlayLayout.TIER_FULL, intArrayOf(180, 0)),
            3 to Triple("292x175", PlayLayout.TIER_FULL, intArrayOf(180, 270, 0)),
            4 to Triple("233x175", PlayLayout.TIER_FULL, intArrayOf(180, 180, 0, 0)),
            5 to Triple("155x175", PlayLayout.TIER_NARROW, intArrayOf(180, 180, 180, 0, 0)),
            6 to Triple("155x175", PlayLayout.TIER_NARROW, intArrayOf(180, 180, 180, 0, 0, 0)),
        )
        for ((n, w) in want) {
            val l = PlayLayout.compute(DitherGrid(1872, 1404, 4), n)
            assertEquals("$n", w.first, "${l.panelW}x${l.panelH}")
            assertEquals("$n", w.second, l.tier)
            assertTrue(l.comfortable)
            assertEquals(1, l.kMax)
            assertArrayEquals("$n", w.third, l.rotation)
            assertEquals(n == 5 || n == 6, !l.pmVisible) // narrow: ± not drawn
            // The same tiles turned for portrait.
            val p = PlayLayout.compute(DitherGrid(1404, 1872, 4), n)
            assertEquals(w.first, "${p.panelW}x${p.panelH}")
            assertArrayEquals(IntArray(n) { (w.third[it] + 90) % 360 }, p.rotation)
            // Auto scale stays at 4 for every count, both ways up.
            assertEquals(4, PlayLayout.chooseScale(1872, 1404, 300, players = n))
            assertEquals(4, PlayLayout.chooseScale(1404, 1872, 300, players = n))
        }
    }

    @Test fun nomadDiamondPlacement() {
        fun at(n: Int) = PlayLayout.compute(DitherGrid(1872, 1404, 4), n).let { it.diamondCx to it.diamondCy }
        assertEquals(234 to 175, at(2)) // middle of the long divider
        assertEquals(292 to 175, at(3)) // the T: where the end divider meets the long one
        assertEquals(233 to 175, at(4)) // the crossing
        assertEquals(233 to 175, at(5)) // under the far middle seat, between the two near seats
        assertEquals(233 to 175, at(6)) // middle of the long divider
    }

    @Test fun threeSeatTeeOnTheNomad() {
        val l = PlayLayout.compute(DitherGrid(1872, 1404, 4), 3)
        assertEquals(IRect(0, 0, 292, 175), l.panels[0])
        assertEquals(IRect(293, 29, 468, 321), l.panels[1]) // sideways: 175 wide, 292 tall, centred
        assertEquals(IRect(0, 176, 292, 351), l.panels[2])
        assertEquals(IRect(293, 0, 468, 351), l.cells[1]) // the end seat owns its margin
        assertEquals(listOf(IRect(292, 0, 293, 351), IRect(0, 175, 292, 176)), l.dividers.toList())
    }

    // ---- invariants at every count, both orientations, S = 2..6 -------------------------------------

    @Test fun geometryInvariants() {
        for (n in 2..6) for (l in layouts(n, Arrangements.ALL.filter { it.players == n }.map { it.id })) {
            val g = IRect(0, 0, l.grid.width, l.grid.height)
            assertEquals("$l", n, l.playerCount)
            assertTrue("$l", l.diamondCx in 0 until g.right && l.diamondCy in 0 until g.bottom)
            for (s in 0 until n) {
                val p = l.panels[s]
                assertTrue("$l seat $s", inside(p, g))
                val fw = if (sideways(l.rotation[s])) p.height else p.width
                val fh = if (sideways(l.rotation[s])) p.width else p.height
                assertEquals("$l seat $s", l.panelW, fw); assertEquals("$l seat $s", l.panelH, fh)
                assertTrue(inside(p, l.cells[s]))
                for (o in 0 until n) if (o != s) {
                    assertFalse("$l $s/$o", overlaps(p, l.panels[o]))
                    assertFalse("$l cells $s/$o", overlaps(l.cells[s], l.cells[o]))
                }
                for (d in l.dividers) assertFalse("$l divider $d over seat $s", overlaps(d, p))
                // Halves tile the panel exactly.
                val a = l.zones[zone(s, MINUS)]
                val b = l.zones[zone(s, PLUS)]
                assertFalse(overlaps(a, b))
                assertTrue(inside(a, p) && inside(b, p))
                assertEquals(p.width * p.height, a.width * a.height + b.width * b.height)
                if (!l.comfortable) continue // tiny screens: content may touch (never offered)
                // Every per-seat rect sits inside its own panel.
                val rects = mutableListOf(l.numberSlot[s], l.readoutSlot[s], l.tab[s], l.tabHit[s], l.tabDotsBacking[s])
                for (i in 0 until 3) rects += l.tabDots[s * 3 + i]
                if (l.pmVisible) { rects += l.minusGlyph[s]; rects += l.plusGlyph[s] }
                rects += l.underRow[s]
                for (r in rects) assertTrue("$l seat $s $r", inside(r, p))
                // Content near the far edge clears the diamond (it may sit mid-edge).
                for (r in listOf(l.numberSlot[s], l.readoutSlot[s])) assertFalse("$l seat $s $r", overlaps(r, l.diamond))
                if (l.pmVisible) for (r in listOf(l.minusGlyph[s], l.plusGlyph[s])) assertFalse("$l seat $s", overlaps(r, l.diamond))
            }
            for (d in l.dividers) {
                assertTrue("$l $d", inside(d, g))
                assertTrue("$l $d", d.width == 1 || d.height == 1)
            }
        }
    }

    @Test fun defaultsAreComfortableOnTheNomadAtScales2To4() {
        for ((w, h) in nomad) for (n in 2..6) for (s in 2..4) {
            val l = PlayLayout.compute(DitherGrid(w, h, s), n)
            assertTrue("$l", l.comfortable)
        }
    }

    @Test fun portraitIsTheLandscapeTableTurnedClockwise() {
        for (n in 2..6) for (a in Arrangements.candidates(n)) for (s in 2..6) {
            val land = Seating(a, 1872 / s, 1404 / s) { fw, fh -> fw + fh } // any deterministic rank
            val port = Seating(a, 1404 / s, 1872 / s) { fw, fh -> fw + fh }
            assertTrue(port.portrait); assertFalse(land.portrait)
            val th = 1404 / s
            for (i in 0 until n) {
                val r = land.panels[i]
                assertEquals("$a S=$s seat $i", IRect().setXywh(th - r.top - r.height, r.left, r.height, r.width), port.panels[i])
                assertEquals((land.rotation[i] + 90) % 360, port.rotation[i])
            }
            assertEquals(th - 1 - land.diamondY, port.diamondX)
            assertEquals(land.diamondX, port.diamondY)
        }
    }

    @Test fun seatOrderIsClockwise() {
        // Panel centres go round the table clockwise: angle from the table centre increases.
        for ((w, h) in nomad) for (n in 2..6) {
            val l = PlayLayout.compute(DitherGrid(w, h, 4), n)
            val cx = l.grid.width / 2.0
            val cy = l.grid.height / 2.0
            val angles = l.panels.map { Math.atan2((it.top + it.bottom) / 2.0 - cy, (it.left + it.right) / 2.0 - cx) }
            var turns = 0.0
            for (i in 0 until n) {
                var d = angles[(i + 1) % n] - angles[i]
                while (d <= -Math.PI) d += 2 * Math.PI
                while (d > Math.PI) d -= 2 * Math.PI
                assertTrue("$l: seat $i -> ${(i + 1) % n} turns back", d > 0)
                turns += d
            }
            assertEquals(2 * Math.PI, turns, 1e-9)
        }
    }

    @Test fun farReachFindsAMidEdgeDiamond() {
        val l = PlayLayout.compute(DitherGrid(1872, 1404, 4), 2) // diamond in the middle of both far edges
        assertEquals(IRect(213, 0, 254, 20), l.diamondFrame[0]) // turned 180°: mirrored about the tile's middle
        assertEquals(IRect(214, 0, 255, 20), l.diamondFrame[1])
        assertEquals(20, l.farReach(230, 240))
        assertEquals(0, l.farReach(0, 200))
        val four = PlayLayout.compute(DitherGrid(1872, 1404, 4)) // far corners only
        assertEquals(20, four.farReach(0, 1))
        assertEquals(20, four.farReach(232, 233))
        assertEquals(0, four.farReach(20, 213))
    }

    // ---- seat glyph: the miniature is the arrangement ----------------------------------------------

    @Test fun seatGlyphLatticeFollowsTheArrangement() {
        for ((w, h) in nomad) for (n in 2..6) {
            val l = PlayLayout.compute(DitherGrid(w, h, 4), n)
            val m = SeatMap.compute(l.cells, 16)
            for (a in 0 until n) for (b in 0 until n) {
                val pa = l.cells[a]; val pb = l.cells[b]
                val ca = m.cells[a]; val cb = m.cells[b]
                // Same order along each axis as the real cells (centres more than a divider apart).
                val dx = (pa.left + pa.right) - (pb.left + pb.right)
                val dy = (pa.top + pa.bottom) - (pb.top + pb.bottom)
                if (Math.abs(dx) > 2 * SeatMap.MERGE) assertEquals("$l $a/$b x", Integer.signum(dx), Integer.signum((ca.left + ca.right) - (cb.left + cb.right)))
                if (Math.abs(dy) > 2 * SeatMap.MERGE) assertEquals("$l $a/$b y", Integer.signum(dy), Integer.signum((ca.top + ca.bottom) - (cb.top + cb.bottom)))
            }
        }
    }

    @Test fun seatGlyphsOnTheNomad() {
        fun glyph(n: Int, seat: Int) = SeatMap.compute(PlayLayout.compute(DitherGrid(1872, 1404, 4), n).cells, 16).ascii(seat, 1)
        assertEquals(
            """
            ################
            #..............#
            #..............#
            #..............#
            #..............#
            #..............#
            ################
            #..............#
            #.############.#
            #.############.#
            #.############.#
            #..............#
            ################
            """.trimIndent() + "\n",
            glyph(2, 1),
        )
        assertEquals(
            """
            ################
            #........#.....#
            #........#.###.#
            #........#.###.#
            #........#.###.#
            #........#.###.#
            ##########.###.#
            #........#.###.#
            #........#.###.#
            #........#.###.#
            #........#.###.#
            #........#.....#
            ################
            """.trimIndent() + "\n",
            glyph(3, 1), // the end seat
        )
        assertEquals(
            """
            #############
            #...#...#...#
            #...#...#...#
            #...#...#...#
            #...#...#...#
            #############
            ..#...#####..
            ..#...#####..
            ..#...#####..
            ..#...#####..
            ..#########..
            """.trimIndent() + "\n",
            glyph(5, 3), // near band, right
        )
    }

    // ---- the table ---------------------------------------------------------------------------------

    @Test fun arrangementTable() {
        for (n in 2..6) {
            assertEquals(n, Arrangements.default(n).players)
            assertEquals(Arrangements.default(n), Arrangements.of(n, ""))
            assertEquals(Arrangements.default(n), Arrangements.of(n, "no-such-layout"))
            for (a in Arrangements.candidates(n)) assertEquals(a, Arrangements.of(n, a.id))
        }
        assertEquals(Arrangements.default(3), Arrangements.of(3, "4-grid")) // an alternate for another count is ignored
        assertEquals(Arrangements.ALL.size, Arrangements.ALL.map { it.id }.toSet().size)
    }
}
