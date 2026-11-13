package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.RecentPicks
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.widgets.TextSlice
import kotlin.random.Random

/**
 * Last player standing (T15): when exactly one player is not KO, a static caption from
 * `Flavour.winner` in caps sits in that tile's under-number row, in the free middle between the
 * counter-peek corners ([KoLayout.arrangeWinner]; up to two lines),
 * picked once when the seat becomes the winner (never the previous line) and dropped as soon as
 * anyone revives (or the winner dies). [Kind.CONTENT]: hidden with the rest of the tile's content in
 * commander mode and under overlays; skipped while the winner's drawer is open (compact tile). The
 * winner flourish is an effect (T28c), not this.
 *
 * Captions are sized by maths ([KoLayout.arrangeWinner]): the pick prefers, over up to [FIT_TRIES]
 * picks, a line that shows whole at the largest scale.
 */
class KoWinner(
    private val random: Random = Random(System.nanoTime()),
    private val flavour: Flavour = Flavour.of(true),
) : Component(Layer.TILE, Kind.CONTENT) {

    private var k: KoLayout? = null
    private var t: TileSlots? = null
    private var recent = RecentPicks.EMPTY

    /** The winning seat, or -1. */
    var winner = -1
        private set

    /** The caption (caps), "" when there is no winner. */
    var caption = ""
        private set

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val nk = KoLayout(play)
        k = nk
        t = tiles
        if (winner >= 0) nk.arrangeWinner(winner, caption, block)
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
        if (w >= 0) k.arrangeWinner(w, caption, block)
        if (oldW >= 0) invalidate(k.play.underRow[oldW])
        if (w >= 0) invalidate(k.play.underRow[w])
        stage.log?.invoke("winner $oldW -> $w '$caption'")
    }

    private fun pick(k: KoLayout): String {
        // Prefer a line that shows whole at the largest scale any pick reaches (the free middle of the
        // under row is narrow): up to FIT_TRIES picks, keeping the best.
        var best = ""
        var bestK = -1
        for (i in 0 until FIT_TRIES) {
            val p = flavour.winner(random, recent)
            recent = p.recent
            val line = p.value.uppercase()
            k.arrangeWinner(0, line, block)
            val shownWhole = block.lines > 0 && block.end[block.lines - 1] == line.trimEnd().length
            val score = if (shownWhole) block.k else 0
            if (score > bestK) { best = line; bestK = score }
            if (score == k.quipKMax) break
        }
        return best
    }

    private val block = KoLayout.Block()
    private val slice = TextSlice()

    override fun draw(f: Frame, seat: Int) {
        if (seat != winner || caption.isEmpty()) return
        val k = k ?: return
        if (t?.compact(seat) == true) return
        if (!f.hit(k.play.underRow[seat])) return
        val rot = k.play.rotation[seat]
        val b = block
        for (i in 0 until b.lines) {
            val r = b.line[i]
            f.text.draw(k.ui, slice.set(caption, b.start[i], b.end[i]), r.left, r.top, b.k, rot)
        }
    }

    companion object {
        /** Picks tried for a line that fits at full size before settling for a smaller one. */
        const val FIT_TRIES = 6

        private val ROTATIONS = intArrayOf(0, 180)
        private const val WARM_UI = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 '.,-:?"
    }
}
