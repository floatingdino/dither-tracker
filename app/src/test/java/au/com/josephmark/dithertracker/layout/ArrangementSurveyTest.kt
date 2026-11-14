package au.com.josephmark.dithertracker.layout

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.menu.GemLayout
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.purity.PurityChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T53: the arrangement table as the layout picker offers it — tile sizes and tiers on the Nomad (the
 * numbers in `design/layouts/README.md`), the one-sided diamond, and the per-count layout spec.
 */
class ArrangementSurveyTest {

    private fun nomad(a: Arrangement, s: Int = 4, portrait: Boolean = false) =
        PlayLayout.compute(if (portrait) DitherGrid(1404, 1872, s) else DitherGrid(1872, 1404, s), a.players, a.id)

    @Test fun nomadTilesAndTiersAtScale4() {
        // id to (tile, tier) at S = 4, landscape: what the README lists and the picker offers.
        val want = mapOf(
            "2-across" to ("468x175" to PlayLayout.TIER_FULL),
            "2-ends" to ("351x233" to PlayLayout.TIER_FULL),
            "2-side" to ("233x351" to PlayLayout.TIER_FULL),
            "2-corner" to ("233x234" to PlayLayout.TIER_FULL),
            "3-tee" to ("292x175" to PlayLayout.TIER_FULL),
            "3-pyramid" to ("233x175" to PlayLayout.TIER_FULL),
            "3-side" to ("155x351" to PlayLayout.TIER_NARROW),
            "3-end" to ("148x170" to PlayLayout.TIER_NARROW),
            "4-grid" to ("233x175" to PlayLayout.TIER_FULL),
            "4-dinner" to ("232x117" to PlayLayout.TIER_NONE),
            "5-three-two" to ("155x175" to PlayLayout.TIER_NARROW),
            "5-end" to ("148x170" to PlayLayout.TIER_NARROW),
            "6-three-three" to ("155x175" to PlayLayout.TIER_NARROW),
            "6-dinner" to ("116x116" to PlayLayout.TIER_NONE),
            // T60 lattices: the content frame (the largest every seat holds) and its tier.
            "2-diagonal" to ("233x175" to PlayLayout.TIER_FULL),
            "2-diagonal-flip" to ("233x175" to PlayLayout.TIER_FULL),
            "3-wide-far" to ("233x175" to PlayLayout.TIER_FULL),
            "3-wide-near" to ("233x175" to PlayLayout.TIER_FULL),
            "3-tall-left" to ("233x175" to PlayLayout.TIER_FULL),
            "3-tall-right" to ("233x175" to PlayLayout.TIER_FULL),
            "3-l-far-left" to ("233x175" to PlayLayout.TIER_FULL),
            "3-l-far-right" to ("233x175" to PlayLayout.TIER_FULL),
            "3-l-near-left" to ("233x175" to PlayLayout.TIER_FULL),
            "3-l-near-right" to ("233x175" to PlayLayout.TIER_FULL),
            "4-wide-far" to ("155x175" to PlayLayout.TIER_NARROW),
            "4-wide-near" to ("155x175" to PlayLayout.TIER_NARROW),
            "5-wide-near" to ("155x175" to PlayLayout.TIER_NARROW),
            "5-wide-far" to ("155x175" to PlayLayout.TIER_NARROW),
            "5-hole-far-left" to ("155x175" to PlayLayout.TIER_NARROW),
            "5-hole-far-mid" to ("155x175" to PlayLayout.TIER_NARROW),
            "5-hole-far-right" to ("155x175" to PlayLayout.TIER_NARROW),
            "5-hole-near-left" to ("155x175" to PlayLayout.TIER_NARROW),
            "5-hole-near-mid" to ("155x175" to PlayLayout.TIER_NARROW),
            "5-hole-near-right" to ("155x175" to PlayLayout.TIER_NARROW),
        )
        assertEquals(want.keys, Arrangements.ALL.map { it.id }.toSet())
        for (a in Arrangements.ALL) {
            val l = nomad(a)
            assertEquals(a.id, want[a.id]!!.first, "${l.panelW}x${l.panelH}")
            assertEquals(a.id, want[a.id]!!.second, l.tier)
            // Portrait is the same tile.
            val p = nomad(a, portrait = true)
            assertEquals(a.id, "${l.panelW}x${l.panelH}", "${p.panelW}x${p.panelH}")
        }
    }

    @Test fun oneSidedDiamondSitsOnTheFarEdgeAtADivider() {
        for (a in Arrangements.ALL.filter { it.top == 0 && it.bottom > 0 }) for (s in 2..6) for (portrait in listOf(false, true)) {
            val l = nomad(a, s, portrait)
            val g = IRect(0, 0, l.grid.width, l.grid.height)
            assertTrue("$l", l.diamond.left >= g.left && l.diamond.top >= g.top && l.diamond.right <= g.right && l.diamond.bottom <= g.bottom)
            // On a divider (where two tiles meet), not in the middle of a tile.
            assertTrue("$l", l.dividers.any { it.contains(l.diamondCx, l.diamondCy) })
            // It stays in the far half of every tile it touches (the tabs and peeks are at the near edge),
            // and the table menu opens whole around it.
            for (seat in 0 until l.playerCount) {
                val f = l.diamondFrame[seat]
                if (!f.isEmpty) assertTrue("$l seat $seat $f", f.bottom <= l.panelH / 2)
            }
            val gem = GemLayout(l).bounds
            assertTrue("$l gem $gem", gem.left >= 0 && gem.top >= 0 && gem.right <= g.right && gem.bottom <= g.bottom)
        }
    }

    @Test fun layoutSpecHoldsOneChoicePerCount() {
        assertEquals("", Arrangements.choose("", 3, "3-tee"))
        assertEquals("3-pyramid", Arrangements.choose("", 3, "3-pyramid"))
        val s = Arrangements.choose(Arrangements.choose("3-pyramid", 5, "5-end"), 2, "2-ends")
        assertEquals("2-ends,3-pyramid,5-end", s)
        assertEquals("2-ends", Arrangements.of(2, s).id)
        assertEquals("3-pyramid", Arrangements.of(3, s).id)
        assertEquals("4-grid", Arrangements.of(4, s).id)
        assertEquals("5-end", Arrangements.of(5, s).id)
        assertEquals("6-three-three", Arrangements.of(6, s).id)
        // Back to a default drops the entry; a T11 single id is a one-entry spec; junk is ignored.
        assertEquals("2-ends,5-end", Arrangements.choose(s, 3, "3-tee"))
        assertEquals("3-pyramid", Arrangements.of(3, "3-pyramid").id)
        assertEquals("3-tee", Arrangements.of(3, "3-pyra,,x,4-grid").id)
        assertEquals("3-pyramid", Arrangements.of(3, ",junk,3-pyramid,").id)
        for (n in 2..6) for (a in Arrangements.candidates(n)) {
            assertEquals(a, Arrangements.of(n, Arrangements.choose(s, n, a.id)))
            for (m in 2..6) if (m != n) assertEquals(Arrangements.of(m, s), Arrangements.of(m, Arrangements.choose(s, n, a.id)))
        }
        try { Arrangements.choose("", 3, "4-grid"); throw AssertionError("accepted a 4-seat id for 3") } catch (_: IllegalArgumentException) {}
    }

    @Test fun everyCountHasMoreThanOneOfferedArrangementOrSaysWhyNot() {
        for (n in 2..6) {
            val offered = Arrangements.candidates(n).filter { nomad(it).comfortable }
            assertTrue("$n: the default is offered", offered.first() == Arrangements.default(n))
            assertFalse("$n: offered twice", offered.size != offered.distinct().size)
        }
    }

    /** Writes the survey table (every arrangement × S, both orientations) for the README; never fails. */
    @Test fun writeSurvey() {
        val sb = StringBuilder("id, S, tile, tier (landscape 1872x1404)\n")
        for (a in Arrangements.ALL) for (s in 2..6) {
            val l = nomad(a, s)
            sb.append("${a.id}, $s, ${l.panelW}x${l.panelH}, ${when (l.tier) { PlayLayout.TIER_FULL -> "full"; PlayLayout.TIER_NARROW -> "narrow"; else -> "none" }}, k${l.kMax}\n")
        }
        File(PurityChecks.outDir, "t53").mkdirs()
        File(PurityChecks.outDir, "t53/arrangement-survey.txt").writeText(sb.toString())
    }
}
