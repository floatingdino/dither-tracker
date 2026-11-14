package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Chrome
import au.com.josephmark.dithertracker.type.Fonts

/**
 * Geometry of the first-player roll-off on the tiles (T59, pure; built at layout): per seat the
 * take-over's double frame, the "D20" label (where the readout sits) and, for every tumbling pose on a
 * slow (3) and a fast (5) display, where the die and its speed ticks / dust go — laid out in the
 * player's frame and mapped to the screen ([PlayLayout.mapRect]), so it works on every arrangement,
 * tier and rotation (sideways seats included: the sprites are built in all four turns).
 *
 * The die is the dice panel's d20 ([DiceSprites]) at [spriteK]: about two thirds of the hero numeral's
 * height. Its path runs across the number slot from the player's right towards the middle, where the
 * roll then lands in the hero numerals: high, low with dust (the bounce), and in between.
 */
class TileRollLayout(val play: PlayLayout, reuse: Sprites? = null) {

    /** The d20 poses A, AB, B, BC, C, then the speed ticks and the dust, at [k], all four turns. */
    class Sprites internal constructor(val k: Int) {
        internal val all: Array<Sprite> = arrayOf(
            DiceSprites.D20_A, DiceSprites.D20_AB, DiceSprites.D20_B, DiceSprites.D20_BC, DiceSprites.D20_C,
            DiceSprites.TRAIL, DiceSprites.DUST,
        ).map { Sprite(it, k, allTurns = true) }.toTypedArray()
    }

    val k: Int = spriteK(play)
    val sprites: Sprites = reuse?.takeIf { it.k == k } ?: Sprites(k)

    private val P = play.playerCount
    private val W = play.panelW
    private val H = play.panelH

    /** The take-over's two frame lines (screen). */
    val frameOuter: Array<IRect> = Array(P) { IRect() }
    val frameInner: Array<IRect> = Array(P) { IRect() }

    /** "D20" text box (screen; top-left is the draw origin at the seat's rotation). */
    val label: Array<IRect> = Array(P) { IRect() }

    /** Per speed (0 slow, 1 fast), seat and beat: the die's rect, the extra's rect and which sprites. */
    private val die = Array(2) { Array(P * MAX_BEATS) { IRect() } }
    private val extra = Array(2) { Array(P * MAX_BEATS) { IRect() } }
    private val pose = Array(2) { IntArray(P * MAX_BEATS) }
    private val extraSprite = Array(2) { IntArray(P * MAX_BEATS) }

    init {
        val s = play.grid.scale
        val inset = Chrome.size(FRAME_INSET, s)
        val gap = Chrome.size(FRAME_GAP, s) + 1
        for (seat in 0 until P) {
            play.mapRect(seat, inset, inset, W - 2 * inset, H - 2 * inset, frameOuter[seat])
            play.mapRect(seat, inset + gap, inset + gap, W - 2 * (inset + gap), H - 2 * (inset + gap), frameInner[seat])
            val ui = Fonts.UI
            val tk = play.tk
            val tw = ui.widthOf(LABEL_LEN, tk)
            val ly = play.readoutY + (play.readoutFont.height(play.readoutK) - ui.height(tk)) / 2
            play.mapRect(seat, (W - tw) / 2, maxOf(inset + gap + 2, ly), tw, ui.height(tk), label[seat])
        }
        val dw = sprites.all[0].width
        val dh = sprites.all[0].height
        val slotY = play.numberSlotY
        val slotH = play.font.height(play.kMax)
        val edge = inset + gap + 2
        for (fast in 0..1) {
            val xs = if (fast == 1) FAST_X else SLOW_X
            val ys = if (fast == 1) FAST_Y else SLOW_Y
            val seq = if (fast == 1) FAST_SEQ else SLOW_SEQ
            val ex = if (fast == 1) FAST_EXTRA else SLOW_EXTRA
            for (seat in 0 until P) for (b in xs.indices) {
                val i = seat * MAX_BEATS + b
                // Each seat starts its tumble on a different pose, so the table isn't in lockstep.
                pose[fast][i] = seq[(b + seat) % seq.size]
                val cx = W * xs[b] / 1000
                val x = (cx - dw / 2).coerceIn(edge, maxOf(edge, W - edge - dw))
                val y = (slotY + (slotH - dh) * ys[b] / 1000).coerceIn(edge, maxOf(edge, H - edge - dh))
                play.mapRect(seat, x, y, dw, dh, die[fast][i])
                val e = ex[b]
                extraSprite[fast][i] = e
                val sp = sprites.all[e]
                val exX: Int
                val exY: Int
                if (e == TRAIL) { exX = x + dw + 2 * k; exY = y + (dh - sp.height) / 2 }
                else { exX = x + (dw - sp.width) / 2; exY = y + dh - sp.height + k }
                val cxX = exX.coerceIn(edge, maxOf(edge, W - edge - sp.width))
                val cyY = exY.coerceIn(edge, maxOf(edge, H - edge - sp.height))
                play.mapRect(seat, cxX, cyY, sp.width, sp.height, extra[fast][i])
            }
        }
    }

    /** Draws [seat]'s tumbling die at [beat] (fast or slow sequence), turned with the seat. */
    fun drawPose(r: DitherRenderer, seat: Int, fast: Boolean, beat: Int, rotation: Int) {
        val f = if (fast) 1 else 0
        val i = seat * MAX_BEATS + beat
        val e = extra[f][i]
        sprites.all[extraSprite[f][i]].draw(r, e.left, e.top, rotation)
        val d = die[f][i]
        sprites.all[pose[f][i]].draw(r, d.left, d.top, rotation)
    }

    fun dieRect(seat: Int, fast: Boolean, beat: Int): IRect = die[if (fast) 1 else 0][seat * MAX_BEATS + beat]
    fun extraRect(seat: Int, fast: Boolean, beat: Int): IRect = extra[if (fast) 1 else 0][seat * MAX_BEATS + beat]

    /** Builds every sprite mask in the layout's [rotations] (off the draw path). */
    fun prewarm(r: DitherRenderer, rotations: IntArray) {
        for (sp in sprites.all) for (rot in rotations) sp.draw(r, 0, 0, rot)
    }

    companion object {
        const val MAX_BEATS = 5
        const val LABEL_LEN = 3

        /** Sprite indices in [Sprites.all]. */
        private const val TRAIL = 5
        private const val DUST = 6

        // Player frame, per mille of the tile's width (die centre) and of the number slot's free height.
        private val SLOW_X = intArrayOf(800, 620, 440)
        private val SLOW_Y = intArrayOf(0, 1000, 450)
        private val SLOW_SEQ = intArrayOf(0, 2, 4)
        private val SLOW_EXTRA = intArrayOf(TRAIL, DUST, TRAIL)
        private val FAST_X = intArrayOf(840, 720, 600, 500, 410)
        private val FAST_Y = intArrayOf(0, 300, 1000, 500, 250)
        private val FAST_SEQ = intArrayOf(0, 1, 2, 3, 4)
        private val FAST_EXTRA = intArrayOf(TRAIL, TRAIL, DUST, TRAIL, TRAIL)

        /** Design values at S = 4, converted per S. */
        const val FRAME_INSET = 2
        const val FRAME_GAP = 1

        /** The die about two thirds as tall as the hero numerals (the d20 art is 24 px at k = 1). */
        fun spriteK(play: PlayLayout): Int = maxOf(1, (play.font.height(play.kMax) * 2 / 3 + 12) / 24)
    }
}
