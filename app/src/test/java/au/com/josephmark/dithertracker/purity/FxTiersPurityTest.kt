package au.com.josephmark.dithertracker.purity

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.fx.Fx
import au.com.josephmark.dithertracker.fx.FxComponent
import au.com.josephmark.dithertracker.fx.FxDebug
import au.com.josephmark.dithertracker.layout.CountRig
import au.com.josephmark.dithertracker.play.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * T55: every keyframe of every shipped effect on the other seat tiers — the narrow tier (6 players,
 * 155-wide tiles, the diamond biting the middle seats) and sideways seats (3 players, seats turned
 * 90/270) — at S = 2..6, both colour modes, global pattern phase enforced (no `KNOWN_OFF_PHASE`),
 * nothing drawn outside its own seat's panel. And the effects the triggers can reach there are all
 * prewarmed (nothing built inside a draw). The 4-player tier is [PuritySuiteTest]'s `effects`.
 */
@RunWith(Parameterized::class)
class FxTiersPurityTest(private val players: Int, private val s: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}p S={1}")
        fun configs(): List<Array<Any>> = listOf(6, 3).flatMap { p -> (2..6).map { arrayOf<Any>(p, it) } }
    }

    private fun rig(gallery: Boolean): CountRig {
        FxDebug.gallery = if (gallery) "all" else null // prewarm everything the gallery can show
        try {
            return CountRig(1872, 1404, s, players).also { it.fx.stopGallery() }
        } finally {
            FxDebug.gallery = null
        }
    }

    @Test fun everyKeyframeOfEveryEffectIsPureAndStaysInItsSeat() {
        val rig = rig(gallery = true)
        if (!rig.l.comfortable) return // never offered (SettingsTable.scaleValues)
        val errs = ArrayList<String>()
        rig.fx.startGallery(arrayOf(Fx.entries.first()), 37)
        rig.fx.stopGallery()
        rig.fx.player.cancelAll()
        rig.advance(10_000)
        val clean = rig.render(false).px.copyOf()
        var shots = 0
        for (fx in Fx.entries) {
            rig.fx.startGallery(arrayOf(fx), 37)
            rig.fx.stopGallery()
            for (i in 0 until fx.frameCount) {
                errs += rig.check("${fx.id}_$i")
                PurityChecks.diffOutside(clean, rig.render(false).px, rig.r.width, rig.l.panels)?.let { errs += "${rig.tag} ${fx.id}_$i: $it" }
                shots++
                rig.advance(rig.fx.player.hold(fx, i))
            }
            rig.fx.player.cancelAll()
            rig.advance(10_000)
        }
        assertTrue("$shots shots", shots > Fx.entries.size * 2)
        assertTrue(errs.joinToString("\n"), errs.isEmpty())
        assertTrue("the gallery's prewarm covered every effect", rig.r.cold.isEmpty())
    }

    @Test fun whatTheTriggersPlayIsPrewarmed() {
        val rig = rig(gallery = false)
        if (!rig.l.comfortable) return
        val q = FxComponent.QUIET_MS
        fun settle() { repeat(8) { rig.advance(250); rig.render(false); rig.render(true) } }
        val seat = 0
        rig.stage.dispatch(seat, Action.ChangeCounter(rig.id(seat), Counter.POISON, 1)); rig.advance(q); settle()
        for ((c, _) in listOf(Counter.ENERGY to 0, Counter.EXPERIENCE to 0, Counter.RAD to 0, Counter.STORM to 0)) {
            rig.advance(FxComponent.BURST_MS)
            rig.stage.dispatch(seat, Action.ChangeCounter(rig.id(seat), c, 1)); rig.advance(q); settle()
        }
        for (t in listOf(Toggle.MONARCH, Toggle.INITIATIVE, Toggle.RING_BEARER)) {
            rig.advance(FxComponent.BURST_MS)
            rig.stage.dispatch(seat, Action.SetToggle(rig.id(seat), t, true)); rig.advance(q); settle()
        }
        rig.stage.dispatch(seat, Action.SetDayNight(DayNight.NIGHT)); rig.advance(q); settle()
        rig.advance(FxComponent.BURST_MS)
        rig.stage.dispatch(seat, Action.SetDayNight(DayNight.DAY)); rig.advance(q); settle()
        assertTrue("${rig.tag}: built in a draw: ${rig.r.cold.keys}", rig.r.cold.isEmpty())
        if (players == 6 && s == 4) assertEquals("6 players on the Nomad: the narrow tier", PlayLayout.TIER_NARROW, rig.l.tier)
    }
}
