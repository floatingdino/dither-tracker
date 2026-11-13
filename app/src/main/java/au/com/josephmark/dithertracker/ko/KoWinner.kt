package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.RecentPicks
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import kotlin.random.Random

/**
 * Last player standing (T15): when exactly one player is not KO, a static caption from
 * `Flavour.winner` in caps sits in that tile's under-number row (over the counter peeks, on paper),
 * picked once when the seat becomes the winner (never the previous line) and dropped as soon as
 * anyone revives (or the winner dies). [Kind.CONTENT]: hidden with the rest of the tile's content in
 * commander mode and under overlays; skipped while the winner's drawer is open (compact tile). The
 * winner flourish is an effect (T28c), not this.
 *
 * Captions are sized by maths ([KoLayout.winnerK]): the pick prefers a line that fits the row at
 * the full caption size (up to [FIT_TRIES] picks), otherwise the line steps down a size.
 */
class KoWinner(
    private val random: Random = Random(System.nanoTime()),
    private val flavour: Flavour = Flavour.of(true),
) : Component(Layer.TILE, Kind.CONTENT) {

    private var k: KoLayout? = null
    private var t: TileSlots? = null
    private var recent = RecentPicks.EMPTY
    private val fp = IRect()

    /** The winning seat, or -1. */
    var winner = -1
        private set

    /** The caption (caps), "" when there is no winner. */
    var caption = ""
        private set

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        k = KoLayout(play)
        t = tiles
        update(stage.session.state)
    }

    override fun onAttach() = update(stage.session.state)

    override fun onPlayersChanged() {
        winner = -1
        update(stage.session.state)
    }

    override fun prewarm(f: Frame) {
        val k = k ?: return
        for (rot in ROTATIONS) for (q in 1..k.quipKMax) f.text.draw(k.ui, WARM_UI, 0, 0, q, rot)
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) = update(new)

    private fun update(state: GameState) {
        val k = k ?: return
        val ps = state.players
        var alive = -1
        var n = 0
        for (s in 0 until minOf(PlayLayout.PLAYERS, ps.size)) if (!state.isKo(ps[s])) { alive = s; n++ }
        val w = if (n == 1 && ps.size > 1) alive else -1
        if (w == winner) return
        val oldW = winner
        winner = w
        caption = if (w < 0) "" else pick(k)
        if (oldW >= 0) invalidate(k.play.underRow[oldW])
        if (w >= 0) invalidate(k.play.underRow[w])
        stage.log?.invoke("winner $oldW -> $w '$caption'")
    }

    private fun pick(k: KoLayout): String {
        var line = ""
        for (i in 0 until FIT_TRIES) {
            val p = flavour.winner(random, recent)
            recent = p.recent
            line = p.value.uppercase()
            if (k.winnerK(line.length) == k.quipKMax) break
        }
        return line
    }

    override fun draw(f: Frame, seat: Int) {
        if (seat != winner) return
        val k = k ?: return
        if (t?.compact(seat) == true) return
        val row = k.play.underRow[seat]
        if (!f.hit(row)) return
        val q = k.winnerFootprint(seat, caption.length, fp)
        if (q == 0) return
        f.r.rect(row, ink = false) // over the peeks: the game is over, the drawer still has the counters
        f.text.draw(k.ui, caption, fp.left, fp.top, q, k.play.rotation[seat])
    }

    companion object {
        /** Picks tried for a line that fits at full size before settling for a smaller one. */
        const val FIT_TRIES = 6

        private val ROTATIONS = intArrayOf(0, 180)
        private const val WARM_UI = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 '.,-:?"
    }
}
