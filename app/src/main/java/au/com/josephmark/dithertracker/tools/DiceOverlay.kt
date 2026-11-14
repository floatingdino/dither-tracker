package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.CoinFace
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.menu.GemPainter
import au.com.josephmark.dithertracker.menu.MenuDestination
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.ZoneSink
import kotlin.random.Random

/**
 * The dice / coin panel (T16): the menu's DICE tip opens it **empty** (T59: no result, no tool chosen —
 * a dotted result box, "dice" over "Pick your poison", the roll-again diamond hidden) and the player
 * picks d20, d6 or the coin. A two-faced tent on
 * the crossing ([DiceLayout]) — each long side reads its own face: the result in the bold small
 * numerals (or the sticker coin), the die label and a quip. The fold row rolls a **d20**, a **d6** or
 * the **coin** at once (the current tool is solid), the centre diamond (`dice` knocked out) rolls the
 * current tool again, and **×** closes.
 *
 * - Every roll plays its **presentation** (T52, [Presentation.TUMBLE]): the die or coin tumbles
 *   across each face in three held keyframes (five shorter ones on a fast display, T59:
 *   [Presentation.TUMBLE_FAST], chosen by `eink.DisplaySpeed` at the roll), then the result lands (a stamp, with a little
 *   character for natural 20 / natural 1 / heads / tails), then the plain result with its label and
 *   quip — one redraw of the panel per keyframe, ~1.05–1.15 s in all. A tap anywhere during it (but
 *   ×) jumps to the result; AGAIN replays it with a fresh roll. One frame timer, armed only while a
 *   presentation plays; [Events.PAUSE] shows the result and disarms everything (the idle timeout is
 *   re-armed on [Events.RESUME]).
 * - Like the menu (no [au.com.josephmark.dithertracker.stage.Cover]: the life totals stay readable),
 *   while open a full-screen dismiss zone under the panel swallows any tap outside it and closes the
 *   panel without touching a life total. [DiceState.IDLE_MS] without a touch closes it too.
 * - [results] hears every roll (nothing logs them yet; T18 adds the log entry there).
 */
class DiceOverlay(
    private val random: Random = Random(System.nanoTime()),
    private val flavour: Flavour = Flavour.quippy,
) : Component(Layer.OVERLAY, Kind.OVERLAY, zonesPerSeat = ZONES, timerCount = DiceState.TIMERS), MenuDestination {

    lateinit var state: DiceState
        private set

    /** Current geometry (null before the first layout). */
    var layout: DiceLayout? = null
        private set

    private var art: DiceArt? = null
    private var sprites: SpriteSet? = null
    private var placedSlow: PresentationLayout? = null
    private var placedFast: PresentationLayout? = null

    /**
     * Sets the slow- and fast-display presentations (null: results at once). Both must share one
     * sprite table. Tests swap in variant B for the design sheet.
     */
    fun setPresentations(slow: Presentation?, fast: Presentation?) {
        require(slow == null || fast == null || slow.sprites === fast.sprites) { "one sprite table" }
        state.slow = slow
        state.fast = fast
        layout?.let { place(it) }
    }

    /** Where results go besides the screen (the future game log). */
    var results: ToolResults
        get() = state.results
        set(v) { state.results = v }

    private val painter = DicePainter()
    private val gem = GemPainter()
    private val screen = IRect()

    override fun onAdded() {
        state = DiceState(timers, random, { _, _ -> changed() }, flavour)
    }

    /** [MenuDestination]: DICE opens the panel on a d20. */
    override fun open(item: MenuItem, facing: Int) {
        if (item == MenuItem.DICE) state.openEmpty() // T59: nothing rolled until the player picks
    }

    // ---- layout ----------------------------------------------------------------------------------

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val l = DiceLayout(play)
        layout = l
        if (art?.k != l.resultK) art = DiceArt(l.resultK)
        place(l)
        screen.set(0, 0, play.grid.width, play.grid.height)
        if (state.isOpen) arrange(l)
    }

    /** Builds the presentation's sprites at the chrome scale and places them on [l] (off the draw path). */
    private fun place(l: DiceLayout) {
        val slow = state.slow
        val fast = state.fast
        val table = (slow ?: fast)?.sprites
        if (table == null) { sprites = null; placedSlow = null; placedFast = null; return }
        val ss = sprites?.takeIf { it.table === table && it.k == l.k } ?: SpriteSet(table, l.k)
        sprites = ss
        placedSlow = slow?.let { PresentationLayout(l, it, ss.widths, ss.heights, l.k) }
        placedFast = fast?.let { PresentationLayout(l, it, ss.widths, ss.heights, l.k) }
    }

    override fun prewarm(f: Frame) {
        val l = layout ?: return
        val a = art ?: return
        sprites?.let { ss -> for (sp in ss.sprites) for (rot in ROTATIONS) sp.draw(f.r, 0, 0, rot) }
        for (rot in ROTATIONS) {
            f.text.draw(l.result, DIGITS, 0, 0, l.resultK, rot, shadow = false)
            for (t in Tool.entries) f.text.draw(l.ui, t.label, 0, 0, l.k, rot)
            f.text.draw(l.ui, HEADS, 0, 0, l.k, rot)
            f.text.draw(l.ui, TAILS, 0, 0, l.k, rot)
            f.text.draw(l.ui, NAT_20, 0, 0, l.k, rot)
            f.text.draw(l.ui, EMPTY_LABEL, 0, 0, l.k, rot)
            f.text.draw(l.ui, PROMPT, 0, 0, l.k, rot)
            for (kind in KINDS) for (line in flavour.lines(kind)) f.text.draw(l.ui, line, 0, 0, l.k, rot)
            f.r.blit(a.coin(true, rot), 0, 0)
            f.r.blit(a.coin(false, rot), 0, 0)
        }
        f.icons.prewarm(arrayOf(Icon.DICE), l.k)
    }

    private fun arrange(l: DiceLayout) = l.arrange(label(), if (state.empty) PROMPT else state.caption)

    /** The face label: the die ("d20"; "nat 20" / "nat 1" once revealed), or the coin's side ("HEADS"). */
    fun label(): String {
        if (state.empty) return EMPTY_LABEL
        val t = state.tool
        if (state.phase != DiceState.SHOWN) return t.label
        return when {
            t.isCoin -> if (Tool.coinFace(state.value) == CoinFace.HEADS) HEADS else TAILS
            t == Tool.D20 && state.value == 20 -> NAT_20
            t == Tool.D20 && state.value == 1 -> NAT_1
            else -> t.label
        }
    }

    // ---- drawing ---------------------------------------------------------------------------------

    override fun draw(f: Frame, seat: Int) {
        if (!state.isOpen) return
        val l = layout ?: return
        val a = art ?: return
        painter.draw(f, l, a, state, label(), sprites, placedSlow, placedFast)
        if (stage.isPressed(this, Stage.SCREEN, AGAIN)) {
            gem.compensate(f.r, l.foldRect, l.cx, l.cy) { dy -> l.foldRowHalf(dy) }
        }
    }

    override fun bounds(out: IRect): Boolean {
        val l = layout ?: return false
        out.set(l.panelOuter.left, l.panelOuter.top, l.panelOuter.right, l.panelOuter.bottom)
        return true
    }

    // ---- touch -----------------------------------------------------------------------------------

    override fun zones(seat: Int, z: ZoneSink) {
        if (!state.isOpen) return
        val l = layout ?: return
        z.add(DISMISS, screen)
        z.block(l.panelOuter)
        for (b in 0 until DiceLayout.BUTTONS) z.add(b, l.buttonHit[b])
        if (!state.empty) z.add(AGAIN, l.foldRect) // nothing to roll again yet: the fold row swallows it
    }

    override fun contains(seat: Int, local: Int, gx: Int, gy: Int): Boolean {
        val l = layout ?: return false
        return if (local == AGAIN) l.onFoldDiamond(gx, gy) else true
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        val l = layout ?: return false
        val r = when (local) {
            in 0 until DiceLayout.BUTTONS -> l.button[local]
            AGAIN -> l.foldRect
            else -> return false // DISMISS: no feedback, the tap only closes
        }
        out.set(r.left, r.top, r.right, r.bottom)
        return true
    }

    override fun onTap(seat: Int, local: Int) {
        state.touched()
        // A tap during the presentation shows the result now (× still closes).
        if (state.rolling && local != DiceLayout.CLOSE) {
            state.skip()
            return
        }
        when (local) {
            DISMISS, DiceLayout.CLOSE -> state.close()
            AGAIN -> state.again()
            else -> DiceLayout.toolOf(local)?.let { state.roll(it) }
        }
    }

    // ---- notifications ---------------------------------------------------------------------------

    override fun onTimer(id: Int) = state.onTimer(id)

    override fun onEvent(event: Int, seat: Int) {
        when (event) {
            Events.PAUSE -> state.pause()
            Events.RESUME -> state.resume()
        }
    }

    override fun onDetach() = state.close()

    override fun onPlayersChanged() = state.close()

    private fun changed() {
        val l = layout ?: return
        if (state.isOpen) arrange(l)
        invalidate(l.panelOuter) // one redraw: open, each roll, the reveal, close
    }

    companion object {
        /** Zone locals: the fold buttons (`DiceLayout.D20..CLOSE`), then these. */
        const val AGAIN = DiceLayout.BUTTONS
        const val DISMISS = DiceLayout.BUTTONS + 1
        const val ZONES = DiceLayout.BUTTONS + 2

        const val HEADS = "HEADS"
        const val TAILS = "TAILS"
        const val NAT_20 = "nat 20"
        const val NAT_1 = "nat 1"

        /** The empty panel (T59): the label and the prompt beside the empty result box. */
        const val EMPTY_LABEL = "dice"
        const val PROMPT = "Pick your poison"
        private const val DIGITS = "0123456789"
        private val ROTATIONS = intArrayOf(0, 180)
        private val KINDS = arrayOf(
            QuipKind.D20_NATURAL_1, QuipKind.D20_NATURAL_20, QuipKind.DICE_ROLL, QuipKind.COIN_HEADS, QuipKind.COIN_TAILS,
        )
    }
}
