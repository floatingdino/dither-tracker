package au.com.josephmark.dithertracker.perf

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.purity.ScreenRig
import au.com.josephmark.dithertracker.stage.Stage
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T20: the host prewarms in two phases — [Stage.Prewarm.FIRST_FRAME] before the first frame, effects
 * and overlays ([Stage.Prewarm.LATER]) just after it. Whatever can be on screen when the app opens (a
 * restored game: readouts no, drawers no, KO and winner tiles yes, dark mode either way) must be in
 * the first phase, or the device builds it inside the first onDraw.
 */
class PrewarmPhaseTest {

    private fun firstFrameOnly(rig: ScreenRig) {
        rig.r.warm.clear()
        rig.r.cold.clear()
        rig.r.scratch = true
        rig.stage.prewarm(rig.frame, Stage.Prewarm.FIRST_FRAME)
        rig.r.scratch = false
    }

    private fun assertWarm(rig: ScreenRig, what: String) {
        rig.render(false)
        rig.render(true)
        assertTrue("$what (${rig.tag}): first frame needs masks the first prewarm phase skipped: ${rig.r.cold.values}", rig.r.cold.isEmpty())
    }

    @Test fun whatCanShowAtLaunchIsInTheFirstPhase() {
        for (s in 2..6) for ((w, h) in listOf(1872 to 1404, 1404 to 1872)) {
            val rig = ScreenRig(w, h, s)
            firstFrameOnly(rig)
            assertWarm(rig, "fresh table")
            rig.dispatch(Action.SetLife(rig.id(2), 0))
            rig.dispatch(Action.SetLife(rig.id(0), 0))
            rig.advance(10_000)
            firstFrameOnly(rig)
            assertWarm(rig, "two KO tiles")
            rig.dispatch(Action.SetLife(rig.id(1), 0))
            rig.advance(10_000)
            firstFrameOnly(rig)
            assertWarm(rig, "a winner")
        }
    }

    @Test fun thePhasesTogetherAreEverything() {
        val rig = ScreenRig(1872, 1404, 4)
        rig.r.warm.clear()
        rig.r.scratch = true
        rig.stage.prewarm(rig.frame, Stage.Prewarm.FIRST_FRAME)
        rig.stage.prewarm(rig.frame, Stage.Prewarm.LATER)
        val split = HashSet(rig.r.warm.keys.map { System.identityHashCode(it) })
        rig.r.warm.clear()
        rig.stage.prewarm(rig.frame, Stage.Prewarm.ALL)
        val all = HashSet(rig.r.warm.keys.map { System.identityHashCode(it) })
        rig.r.scratch = false
        assertTrue("first + later == all", split == all)
    }
}
