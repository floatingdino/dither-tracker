package au.com.josephmark.dithertracker.life

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.ZoneSink

/**
 * A seat's life total and its − / + (T06/T07): the hero number and the half-size ± glyphs, drawn
 * where [TileSlots] says (full tile, or compact above an open drawer), and the two life halves of the
 * panel as touch zones ([MINUS], [PLUS]): a tap is ±1 on release, a hold is ±[holdStep][au.com.josephmark.dithertracker.stage.Stage.holdStep]
 * at 500 ms and then every 1000 ms. The halves cover the whole panel; anything declared above them
 * (drawer, tab, diamond) wins where it overlaps.
 *
 * Life changes go through `stage.changeLife(seat, …)`, so the readout attributes them to the seat.
 */
class LifeComponent : Component(Layer.TILE, Kind.CONTENT, zonesPerSeat = 2) {

    private var l: PlayLayout? = null
    private var t: TileSlots? = null
    private val fp = IRect()

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
        t = tiles
    }

    override fun prewarm(f: Frame) {
        val l = l ?: return
        for (rot in l.rotations) for (k in 1..l.kMax) {
            f.text.draw(l.font, WARM_CHARS, 0, 0, k, rot)
            f.text.draw(l.pmFont, PM_CHARS, 0, 0, k, rot)
        }
    }

    override fun draw(f: Frame, seat: Int) {
        val l = l ?: return
        val t = t ?: return
        val p = f.players[seat]
        val rot = l.rotation[seat]
        val compact = t.compact(seat)
        if (l.pmVisible) {
            val pmK = t.pmK(compact)
            val minus = t.minusGlyph(seat, compact)
            val plus = t.plusGlyph(seat, compact)
            if (f.hit(minus)) f.text.draw(l.pmFont, MINUS_GLYPH, minus.left, minus.top, pmK, rot)
            if (f.hit(plus)) f.text.draw(l.pmFont, PLUS_GLYPH, plus.left, plus.top, pmK, rot)
        }
        if (f.hit(t.numberSlot(seat, compact))) {
            val k = t.numberFootprint(seat, compact, p.life, fp)
            f.text.drawInt(l.font, p.life, fp.left, fp.top, k, rot)
        }
    }

    override fun zones(seat: Int, z: ZoneSink) {
        val l = l ?: return
        z.add(MINUS, l.zones[PlayLayout.zone(seat, PlayLayout.MINUS)])
        z.add(PLUS, l.zones[PlayLayout.zone(seat, PlayLayout.PLUS)])
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        val t = t ?: return false
        val r = t.lifeFeedback(seat, local)
        out.set(r.left, r.top, r.right, r.bottom)
        return true
    }

    override fun repeats(seat: Int, local: Int): Boolean = stage.holdStep > 0

    override fun onTap(seat: Int, local: Int) = stage.changeLife(seat, delta(local))

    override fun onRepeat(seat: Int, local: Int) {
        val step = stage.holdStep
        if (step > 0) stage.changeLife(seat, delta(local) * step)
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        val t = t ?: return
        val op = old.players
        val np = new.players
        for (i in np.indices) {
            if (np[i] === op[i] || np[i].life == op[i].life || !visible(i)) continue
            invalidate(t.numberSlot(i))
        }
    }

    companion object {
        /** Local zone ids: the player's left (−) and right (+) half (= [PlayLayout.MINUS] / [PlayLayout.PLUS]). */
        const val MINUS = PlayLayout.MINUS
        const val PLUS = PlayLayout.PLUS

        fun delta(local: Int): Int = if (local == PLUS) 1 else -1

        private const val MINUS_GLYPH = "-"
        private const val PLUS_GLYPH = "+"
        private const val WARM_CHARS = "0123456789-+−"
        private const val PM_CHARS = "-+"
    }
}
