package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Origin
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.ZoneSink
import au.com.josephmark.dithertracker.type.Fonts
import kotlin.random.Random

/**
 * The table menu (T09): the centre diamond blooms into the **gem** ([GemLayout]) — eight tools around
 * a close X — and NEW opens a two-faced restart confirm on the crossing. Opened by the diamond
 * (`chrome.MenuDiamond` sends [Events.MENU_OPEN]).
 *
 * **Cover behaviour.** The gem sits in the plus-shaped free space around the crossing, so nothing is
 * hidden: no [au.com.josephmark.dithertracker.stage.Cover] (a cover hides *drawing* too, and the life
 * totals should stay readable while the menu is open). Input is taken over instead: every pixel
 * outside the gem is some tile's life half, so "tap outside closes" and "tiles outside stay live" can't
 * both hold without a dismiss tap also changing someone's life. While open, a full-screen dismiss
 * zone under the gem swallows the tap and closes the menu; tiles are visible but not live.
 *
 * - **UNDO / REDO** → `stage.session` undo/redo; the menu stays open (repeated undo); a facet is
 *   disabled (`Role.DISABLED`, no zone) while there is nothing to undo/redo.
 * - **NEW** → the confirm; confirm dispatches core `NewGame` (keeps players and settings, resets the
 *   values) and closes; cancel (either button face or the fold's close diamond) returns to the gem.
 * - **CLEAN** → closes and sends [Events.CLEAN] (`eink.CleanComponent`: one black frame, 300 ms, redraw).
 * - **DICE / FIRST / LOG / SETUP** → [destinations]: closes and calls the registered
 *   [MenuDestination]; unregistered ones are drawn disabled and take no tap.
 * - Closes on the centre X, a tap outside, or [MenuState.IDLE_MS] without a touch.
 * - Press feedback inverts the pressed facet's *shape* (see [GemPainter.compensate]).
 */
class MenuOverlay(
    private val random: Random = Random(System.nanoTime()),
    val destinations: MenuDestinations = MenuDestinations(),
    private val flavour: Flavour = Flavour.quippy,
) : Component(Layer.OVERLAY, Kind.OVERLAY, zonesPerSeat = ZONES, timerCount = TIMERS) {

    /** Phase machine (open / confirm / timeout). */
    lateinit var state: MenuState
        private set

    /** Current geometry (null before the first layout). */
    var gem: GemLayout? = null
        private set

    val copy = ConfirmCopy(flavour)

    /** Per slot: drawn enabled (and hit-tested). Refreshed on open and on every state change. */
    val enabled = BooleanArray(GemLayout.SLOTS)

    private val painter = GemPainter()
    private val screen = IRect()
    private val scratch = IRect()
    private val lineRects: Array<IRect> = Array(2 * ConfirmCopy.MAX_LINES) { IRect() }

    override fun onAdded() {
        state = MenuState(timers, { old, new -> onPhaseChanged(old, new) })
    }

    // ---- layout ----------------------------------------------------------------------------------

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val g = GemLayout(play)
        gem = g
        screen.set(0, 0, play.grid.width, play.grid.height)
        if (state.phase == MenuState.CONFIRM) layoutConfirm(g)
    }

    override fun prewarm(f: Frame) {
        val g = gem ?: return
        val k = g.k
        f.icons.prewarm(PREWARM_ICONS, k)
        for (item in MenuItem.entries) f.text.draw(Fonts.UI, item.label, 0, 0, k, halo = true) // halos: disabled facets
        // Every reset prompt and button label (the confirm picks among them).
        for (line in flavour.lines(QuipKind.RESET_PROMPT)) f.text.draw(Fonts.UI, line, 0, 0, k)
        for (b in flavour.resetButtons) {
            f.text.draw(Fonts.UI, b.confirm, 0, 0, k)
            f.text.draw(Fonts.UI, b.cancel, 0, 0, k)
        }
    }

    // ---- drawing ---------------------------------------------------------------------------------

    override fun draw(f: Frame, seat: Int) {
        val g = gem
        if (g != null) {
            when (state.phase) {
                MenuState.OPEN -> {
                    painter.gem(f, g, enabled)
                    compensatePresses(f, g)
                }
                MenuState.BLOOM -> painter.key1(f, g)
                MenuState.CONFIRM -> {
                    painter.confirm(f, g, copy, lineRects)
                    if (stage.isPressed(this, Stage.SCREEN, FOLD_CANCEL)) {
                        painter.compensate(f.r, g.foldRect, g.cx, g.cy) { dy -> g.foldRowHalf(dy) }
                    }
                }
            }
        }
    }

    private fun compensatePresses(f: Frame, g: GemLayout) {
        for (s in 0 until GemLayout.SLOTS) {
            if (!stage.isPressed(this, Stage.SCREEN, s)) continue
            val box = g.facetRect[s]
            if (!f.hit(box)) continue
            painter.compensate(f.r, box, g.facetX[s], g.facetY[s]) { dy -> g.facetRowHalf(dy) }
        }
    }

    override fun bounds(out: IRect): Boolean {
        val g = gem ?: return false
        union(out.set(g.bounds.left, g.bounds.top, g.bounds.right, g.bounds.bottom), g.panelOuter)
        return true
    }

    // ---- touch -----------------------------------------------------------------------------------

    override fun zones(seat: Int, z: ZoneSink) {
        val g = gem ?: return
        when (state.phase) {
            MenuState.OPEN, MenuState.BLOOM -> {
                z.add(DISMISS, screen)
                z.block(g.bounds) // the gem's shape (contains): disabled facets and the rim swallow
                for (s in 0 until GemLayout.SLOTS) if (enabled[s]) z.add(s, g.bounds)
            }
            MenuState.CONFIRM -> {
                z.add(DISMISS, screen)
                z.block(g.panelOuter)
                z.add(CANCEL, g.cancelHit)
                z.add(CONFIRM, g.confirmHit)
                z.add(FOLD_CANCEL, g.foldRect)
            }
        }
    }

    override fun contains(seat: Int, local: Int, gx: Int, gy: Int): Boolean {
        val g = gem ?: return false
        return when {
            local == Stage.BLOCK -> state.phase == MenuState.CONFIRM || g.inGem(gx, gy)
            local < GemLayout.SLOTS -> g.slotAt(gx, gy) == local
            local == FOLD_CANCEL -> g.onFoldDiamond(gx, gy)
            else -> true
        }
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        val g = gem ?: return false
        val r = when {
            local < GemLayout.SLOTS -> g.facetRect[local]
            local == CANCEL -> g.cancelBtn
            local == CONFIRM -> g.confirmBtn
            local == FOLD_CANCEL -> g.foldRect
            else -> return false // DISMISS: no feedback, the tap only closes
        }
        out.set(r.left, r.top, r.right, r.bottom)
        return true
    }

    override fun onTap(seat: Int, local: Int) {
        state.touched()
        when (local) {
            DISMISS -> state.close()
            CANCEL, FOLD_CANCEL -> state.back()
            CONFIRM -> {
                state.close()
                stage.dispatch(Origin.EXTERNAL, Action.NewGame)
            }
            GemLayout.CENTRE -> state.close()
            else -> if (local < GemLayout.SLOTS) tapItem(local)
        }
    }

    private fun tapItem(slot: Int) {
        val item = GemLayout.ITEMS[slot] ?: return
        val s = stage.session
        when (item) {
            MenuItem.UNDO -> if (s.history.canUndo) s.undo()
            MenuItem.REDO -> if (s.history.canRedo) s.redo()
            MenuItem.NEW -> {
                copy.pick(random)
                gem?.let { layoutConfirm(it) }
                state.confirm()
            }
            MenuItem.CLEAN -> {
                state.close()
                stage.send(Events.CLEAN, Stage.SCREEN)
            }
            else -> {
                val d = destinations[item] ?: return
                val facing = gem?.rotation?.get(slot) ?: 0
                state.close()
                d.open(item, facing)
            }
        }
    }

    // ---- notifications ---------------------------------------------------------------------------

    override fun onEvent(event: Int, seat: Int) {
        when (event) {
            Events.MENU_OPEN -> if (!state.isOpen) {
                stage.readouts.commitAll() // "another kind of action"; the confirm may sit on a readout slot
                refreshEnabled()
                state.open()
            }
        }
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        if (state.phase == MenuState.OPEN || state.phase == MenuState.BLOOM) {
            val g = gem ?: return
            val u = GemLayout.slotOf(MenuItem.UNDO)
            val r = GemLayout.slotOf(MenuItem.REDO)
            val wasU = enabled[u]
            val wasR = enabled[r]
            refreshEnabled()
            if (wasU != enabled[u]) invalidate(g.facetRect[u])
            if (wasR != enabled[r]) invalidate(g.facetRect[r])
        }
    }

    override fun onTimer(id: Int) {
        state.onTimer(id)
    }

    override fun onDetach() {
        if (state.isOpen) state.close()
    }

    /** Recomputes [enabled] from the session (undo/redo) and [destinations]. */
    fun refreshEnabled() {
        val h = stage.session.history
        for (s in 0 until GemLayout.SLOTS) {
            val item = GemLayout.ITEMS[s]
            enabled[s] = when {
                item == null -> true
                item == MenuItem.UNDO -> h.canUndo
                item == MenuItem.REDO -> h.canRedo
                item.routed -> destinations[item] != null
                else -> true
            }
        }
    }

    private fun onPhaseChanged(old: Int, new: Int) {
        val g = gem ?: return
        // One redraw per change: the gem's box, plus the panel's when the confirm comes or goes.
        invalidate(g.bounds)
        if (old == MenuState.CONFIRM || new == MenuState.CONFIRM) invalidate(g.panelOuter)
        if (new == MenuState.OPEN) refreshEnabled()
    }

    private fun layoutConfirm(g: GemLayout) {
        val k = g.k
        val ui = Fonts.UI
        copy.layout(ui, k, g.faceTextW, g.cancelBtn.width - 2 * BUTTON_PAD * k)
        val capH = ui.capHeight * k
        val n = copy.lines
        val block = n * capH + (n - 1) * g.lineGap
        val faceH = g.lowerFace.height
        val top = (faceH - block) / 2
        val x = g.lowerFace.left + (g.lowerFace.width - g.faceTextW) / 2
        for (i in 0 until ConfirmCopy.MAX_LINES) {
            val y = top + i * (capH + g.lineGap)
            // Lower face, read from S.
            lineRects[i].setXywh(x, g.lowerFace.top + y, g.faceTextW, capH)
            // Upper face: the 180° image inside the upper face.
            val ux = g.upperFace.right - (x - g.lowerFace.left) - g.faceTextW
            lineRects[ConfirmCopy.MAX_LINES + i].setXywh(ux, g.upperFace.bottom - y - capH, g.faceTextW, capH)
        }
    }

    private fun union(out: IRect, r: IRect) {
        out.set(minOf(out.left, r.left), minOf(out.top, r.top), maxOf(out.right, r.right), maxOf(out.bottom, r.bottom))
    }

    companion object {
        /** Zone locals: the nine slots (`GemLayout.W..E`, CENTRE = close), then these. */
        const val DISMISS = GemLayout.SLOTS
        const val CANCEL = GemLayout.SLOTS + 1
        const val CONFIRM = GemLayout.SLOTS + 2
        const val FOLD_CANCEL = GemLayout.SLOTS + 3
        const val ZONES = GemLayout.SLOTS + 4

        /** Timer locals: the state's (idle, bloom). */
        const val TIMERS = MenuState.TIMERS

        /** Paper between a fold button's outline and its label, in k. */
        const val BUTTON_PAD = 3

        private val PREWARM_ICONS = MenuItem.entries.map { it.icon }.toTypedArray()
    }
}
