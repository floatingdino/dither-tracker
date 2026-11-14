package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Chrome

/**
 * Geometry of the first-player roll-off on the tiles (T59, pure; built at layout): per seat the
 * take-over's double frame and, for every keyframe of the die — the tumbling poses on a slow (3) and a
 * fast (5) display, the fast display's wobble and the rest — where the die and its speed ticks / dust
 * go, laid out in the player's frame and mapped to the screen ([PlayLayout.mapRect]), so it works on
 * every arrangement, tier and rotation (sideways seats included: the sprites are built in all four
 * turns).
 *
 * **The path is one roll (T65).** The die travels across the number slot from the player's right
 * towards the middle and turns counter-clockwise as it goes (`author.py` draws every pose as the
 * resting die turned back by what is left of the roll). Its bounces get lower (a high toss, the floor,
 * a smaller hop, the floor), the spacing between poses shrinks as it slows, and the rest continues the
 * path a little past the last pose (where it stood on its trailing corner, about to tip onto the face it
 * rests on); the wobble sits between the two, rocked onto its leading edge. Every floor pose stands
 * with its lowest pixel on the slot's floor ([floorOf]). The die is the dice panel's d20 at [spriteK]:
 * about two thirds of the hero numeral's height.
 */
class TileRollLayout(val play: PlayLayout, reuse: Sprites? = null) {

    /** The d20 poses A, AB, B, BC, C, the speed ticks, the dust, the rest and the wobble, at [k], all four turns. */
    class Sprites internal constructor(val k: Int) {
        internal val all: Array<Sprite> = arrayOf(
            DiceSprites.D20_A, DiceSprites.D20_AB, DiceSprites.D20_B, DiceSprites.D20_BC, DiceSprites.D20_C,
            DiceSprites.TRAIL, DiceSprites.DUST, DiceSprites.D20_REST, DiceSprites.D20_WOBBLE,
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

    /** The REST keyframe's die on a fast and a slow display, and the fast display's WOBBLE (screen). */
    val rest: Array<IRect> = Array(P) { IRect() }
    val restSlow: Array<IRect> = Array(P) { IRect() }
    val wobble: Array<IRect> = Array(P) { IRect() }

    /** Per speed (0 slow, 1 fast), seat and beat: the die's rect, the extra's rect and which sprites. */
    private val die = Array(2) { Array(P * MAX_BEATS) { IRect() } }
    private val extra = Array(2) { Array(P * MAX_BEATS) { IRect() } }
    private val pose = Array(2) { IntArray(P * MAX_BEATS) }
    private val extraSprite = Array(2) { IntArray(P * MAX_BEATS) }

    /** Player-frame x of each pose's centre (`[speed][beat]`), the wobble's and the rest's: the path, for tests. */
    val pathX: Array<IntArray> = Array(2) { IntArray(MAX_BEATS + 2) }

    /** The slot's floor (player frame): where the die stands. */
    val floor: Int = play.numberSlotY + play.font.height(play.kMax)

    private val edge: Int

    init {
        val s = play.grid.scale
        val inset = Chrome.size(FRAME_INSET, s)
        val gap = Chrome.size(FRAME_GAP, s) + 1
        edge = inset + gap + 2
        for (seat in 0 until P) {
            play.mapRect(seat, inset, inset, W - 2 * inset, H - 2 * inset, frameOuter[seat])
            play.mapRect(seat, inset + gap, inset + gap, W - 2 * (inset + gap), H - 2 * (inset + gap), frameInner[seat])
        }
        for (fast in 0..1) {
            val xs = if (fast == 1) FAST_X else SLOW_X
            val lift = if (fast == 1) FAST_LIFT else SLOW_LIFT
            val seq = if (fast == 1) FAST_SEQ else SLOW_SEQ
            val ex = if (fast == 1) FAST_EXTRA else SLOW_EXTRA
            // Where the toss (and its speed ticks) would cross the frame, the whole path moves left together,
            // so the spacing between poses — the slowing — is kept on every tile.
            val first = sprites.all[seq[0]]
            val tossRight = W * xs[0] / 1000 + first.width / 2 + 2 * k + sprites.all[TRAIL].width
            val shift = maxOf(0, tossRight - (W - edge))
            for (b in xs.indices) {
                val sp = sprites.all[seq[b]]
                // Airborne poses rise above the floor by [lift] ‰ of a die's height.
                val cx = W * xs[b] / 1000 - shift
                val x = clampX(cx - sp.width / 2, sp.width)
                // A full lift is one die's height, as far as the frame allows: the toss is clearly in the air.
                val maxLift = minOf(sp.height, floor - edge - sp.height).coerceAtLeast(0)
                val bottom = floor - maxLift * lift[b] / 1000
                val y = clampY(bottom - sp.height, sp.height)
                pathX[fast][b] = x + sp.width / 2
                for (seat in 0 until P) {
                    val i = seat * MAX_BEATS + b
                    pose[fast][i] = seq[b]
                    play.mapRect(seat, x, y, sp.width, sp.height, die[fast][i])
                    val e = ex[b]
                    extraSprite[fast][i] = e
                    if (e < 0) { extra[fast][i].set(0, 0, 0, 0); continue }
                    val es = sprites.all[e]
                    // Speed ticks trail behind (the player's right: it travels left); dust at its feet.
                    val exX = if (e == TRAIL) x + sp.width + 2 * k else x + (sp.width - es.width) / 2
                    val exY = if (e == TRAIL) y + (sp.height - es.height) / 2 else y + sp.height - es.height + k
                    play.mapRect(seat, clampX(exX, es.width), clampY(exY, es.height), es.width, es.height, extra[fast][i])
                }
            }
            pathX[fast][MAX_BEATS] = if (fast == 1) place(WOBBLE, WOBBLE_X, shift, WOBBLE_SHADOW_ROWS, wobble) else -1
            pathX[fast][MAX_BEATS + 1] =
                if (fast == 1) place(REST, FAST_REST_X, shift, REST_SHADOW_ROWS, rest) else place(REST, SLOW_REST_X, shift, REST_SHADOW_ROWS, restSlow)
        }
    }

    /** Places sprite [idx] centred at [xPerMille] with its die's base (above [shadowRows] of shadow) on the floor. */
    private fun place(idx: Int, xPerMille: Int, shift: Int, shadowRows: Int, out: Array<IRect>): Int {
        val sp = sprites.all[idx]
        val x = clampX(W * xPerMille / 1000 - shift - sp.width / 2, sp.width)
        val y = clampY(floor - sp.height + shadowRows * k, sp.height)
        for (seat in 0 until P) play.mapRect(seat, x, y, sp.width, sp.height, out[seat])
        return x + sp.width / 2
    }

    private fun clampX(x: Int, w: Int) = x.coerceIn(edge, maxOf(edge, W - edge - w))
    private fun clampY(y: Int, h: Int) = y.coerceIn(edge, maxOf(edge, H - edge - h))

    fun restRect(seat: Int, fast: Boolean): IRect = if (fast) rest[seat] else restSlow[seat]

    /** Draws [seat]'s die at rest, turned with the seat. */
    fun drawRest(r: DitherRenderer, seat: Int, fast: Boolean, rotation: Int) {
        val d = restRect(seat, fast)
        sprites.all[REST].draw(r, d.left, d.top, rotation)
    }

    /** Draws [seat]'s die wobbling past its face (fast displays). */
    fun drawWobble(r: DitherRenderer, seat: Int, rotation: Int) {
        val d = wobble[seat]
        sprites.all[WOBBLE].draw(r, d.left, d.top, rotation)
    }

    /** Draws [seat]'s tumbling die at [beat] (fast or slow sequence), turned with the seat. */
    fun drawPose(r: DitherRenderer, seat: Int, fast: Boolean, beat: Int, rotation: Int) {
        val f = if (fast) 1 else 0
        val i = seat * MAX_BEATS + beat
        val es = extraSprite[f][i]
        if (es >= 0) {
            val e = extra[f][i]
            sprites.all[es].draw(r, e.left, e.top, rotation)
        }
        val d = die[f][i]
        sprites.all[pose[f][i]].draw(r, d.left, d.top, rotation)
    }

    fun dieRect(seat: Int, fast: Boolean, beat: Int): IRect = die[if (fast) 1 else 0][seat * MAX_BEATS + beat]
    fun extraRect(seat: Int, fast: Boolean, beat: Int): IRect = extra[if (fast) 1 else 0][seat * MAX_BEATS + beat]
    fun hasExtra(seat: Int, fast: Boolean, beat: Int): Boolean = extraSprite[if (fast) 1 else 0][seat * MAX_BEATS + beat] >= 0

    /** Builds every sprite mask in the layout's [rotations] (off the draw path). */
    fun prewarm(r: DitherRenderer, rotations: IntArray) {
        for (sp in sprites.all) for (rot in rotations) sp.draw(r, 0, 0, rot)
    }

    companion object {
        const val MAX_BEATS = 5

        /** Sprite indices in [Sprites.all]. */
        private const val TRAIL = 5
        private const val DUST = 6
        private const val REST = 7
        private const val WOBBLE = 8

        /** Shadow / dust rows under the die in the rest and wobble art (at k = 1). */
        private const val REST_SHADOW_ROWS = 2
        private const val WOBBLE_SHADOW_ROWS = 1

        // ---- The path (T65), player frame: x of the die's centre in ‰ of the tile's width; lift ‰ of
        // a die's height above the floor (0 = on the floor). Right to left, bounces lower, spacing shrinking.
        private val FAST_X = intArrayOf(870, 745, 645, 580, 545)
        private val FAST_LIFT = intArrayOf(1000, 0, 450, 0, 0)
        private val FAST_SEQ = intArrayOf(0, 1, 2, 3, 4)
        private val FAST_EXTRA = intArrayOf(TRAIL, DUST, TRAIL, -1, -1)
        private const val WOBBLE_X = 528
        private const val FAST_REST_X = 518

        private val SLOW_X = intArrayOf(860, 680, 560)
        private val SLOW_LIFT = intArrayOf(1000, 0, 0)
        private val SLOW_SEQ = intArrayOf(0, 2, 4)
        private val SLOW_EXTRA = intArrayOf(TRAIL, DUST, -1)
        private const val SLOW_REST_X = 530

        /** Design values at S = 4, converted per S. */
        const val FRAME_INSET = 2
        const val FRAME_GAP = 1

        /** The die about two thirds as tall as the hero numerals (the d20 art is 24 px at k = 1). */
        fun spriteK(play: PlayLayout): Int = maxOf(1, (play.font.height(play.kMax) * 2 / 3 + 12) / 24)
    }
}
