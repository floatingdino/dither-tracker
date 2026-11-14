package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.RecentPicks
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.widgets.TextSlice
import kotlin.random.Random

/**
 * Last player standing (T15; T42: the line replaces the life total). When exactly one player is not
 * KO, a line from `Flavour.winner` in caps takes the hero position of that tile at the one quip size
 * ([KoLayout.arrange], the same layout as a dead tile's quip; T47), under the winner emblem
 * (emblem and line centred together; T51: no life total, Sam). The tile stays paper and
 * the counter peeks and tab stay; the caption is drawn on a paper cover over the live number (so its
 * ground is that paper: the dead tile's continuous ground, with nothing round it to cut out of).
 *
 * **Input rule:** the tile stays live underneath — its ± halves, tab and drawer work as ever. Any
 * change to the winner's life (a tap, a hold, commander damage linked to life) dismisses the caption
 * for good and the normal number shows; it comes back only with the next win (somebody revives and
 * the table is down to one again). A revive anywhere ends the win as before. The line is picked once
 * per win, never the previous line.
 *
 * [Kind.CONTENT]: hidden with the rest of the tile's content in commander mode and under overlays;
 * skipped while the winner's drawer is open (compact tile). Registered before the effects component
 * so the winner flourish (T28c) plays over it. The flourish itself is an effect, not this.
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

    /** True once the winner's life changed: the caption stays down until the next win. */
    var dismissed = false
        private set

    /** True while the caption shows on [winner]'s tile (drawer state aside). */
    val showing: Boolean get() = winner >= 0 && !dismissed && caption.isNotEmpty()

    private val block = KoLayout.Block()
    private val band = IRect()
    private val slice = TextSlice()

    fun block(): KoLayout.Block = block

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val nk = KoLayout(play)
        k = nk
        t = tiles
        if (winner >= 0) nk.arrange(winner, caption, block, KoLayout.WINNER)
        update(stage.session.state, null)
    }

    override fun onAttach() = update(stage.session.state, null)

    override fun onPlayersChanged() {
        winner = -1
        update(stage.session.state, null)
    }

    override fun prewarm(f: Frame) {
        val k = k ?: return
        f.icons.prewarm(WARM_ICONS, k.iconK)
        for (rot in k.play.rotations) {
            for (q in 1..k.warmK) f.text.draw(k.ui, warmUi(k.ui), 0, 0, q, rot)
        }
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) = update(new, old)

    private fun update(state: GameState, old: GameState?) {
        val k = k ?: return
        val ps = state.players
        var alive = -1
        var n = 0
        for (s in 0 until minOf(k.play.playerCount, ps.size)) if (!state.isKo(ps[s])) { alive = s; n++ }
        val w = if (n == 1 && ps.size > 1) alive else -1
        if (w == winner) {
            // Same winner: their life moving dismisses the caption (the tile was live all along).
            if (w >= 0 && !dismissed && old != null && w < old.players.size && old.players[w].life != ps[w].life) {
                dismissed = true
                invalidate(k.panels[w])
                stage.log?.invoke("winner $w dismissed")
            }
            return
        }
        val oldW = winner
        val wasShowing = showing
        winner = w
        dismissed = false
        caption = if (w < 0) "" else flavour.winner(random, recent).let { recent = it.recent; it.value.uppercase() }
        if (w >= 0) k.arrange(w, caption, block, KoLayout.WINNER)
        if (oldW >= 0 && wasShowing) invalidate(k.panels[oldW])
        if (w >= 0) invalidate(k.panels[w])
        stage.log?.invoke("winner $oldW -> $w '$caption'")
    }

    override fun draw(f: Frame, seat: Int) {
        if (seat != winner || !showing) return
        val k = k ?: return
        if (t?.compact(seat) == true) return
        val rot = k.play.rotation[seat]
        k.winnerCover(seat, band)
        if (f.hit(band)) {
            f.r.rect(band, ink = false)
            f.icons.icon(k.iconK, EMBLEM, block.emblem, rot, invert = false, opaque = false)
            val b = block
            for (i in 0 until b.lines) {
                val r = b.line[i]
                f.text.draw(k.ui, slice.set(caption, b.start[i], b.end[i]), r.left, r.top, b.k, rot)
            }
        }
    }

    companion object {
        /** The winner's emblem: its own trophy (T51, Sam: not the monarch's crown). */
        val EMBLEM: Icon = Icon.WINNER
        private val WARM_ICONS = arrayOf(EMBLEM)
        /** Every glyph of [font] (quips use punctuation beyond A–Z 0–9; T20: those were built in onDraw). Prewarm only. */
        private fun warmUi(font: Font): String = font.glyphs.joinToString("") { it.char.toString() }
    }
}
