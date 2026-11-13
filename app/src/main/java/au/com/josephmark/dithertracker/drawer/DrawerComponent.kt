package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.ZoneSink

/**
 * The open per-player drawer (T08): bar (commander button · close · page) over a body of tray
 * cells, drawn by [DrawerPainter] from [DrawerLayout]; behaviour in [DrawerController], UI state in
 * [DrawerState]. The closed tab is the [DrawerTab] component (it sits in [Layer.TILE_CHROME], above
 * the life halves' press inversion); both belong to this feature and share [controller].
 *
 * Tile context: the drawer is the [TileSlots.lower] occupant — while a seat's drawer is open the
 * life total, ± and readout move up to the compact geometry it publishes from [DrawerLayout].
 *
 * Zones (locals in [DrawerZones]), only while open: the whole drawer blocks; the cells' halves; the
 * bar's three segments (grown upwards by the tab hit pad). The bar's left segment ([DrawerZones.BUTTON])
 * asks for commander-damage mode through [Events.COMMANDER_TARGET]; the mode is another feature.
 *
 * [Kind.CONTENT]: a cover hiding it on a seat closes that seat's drawer.
 */
class DrawerComponent : Component(Layer.TILE, Kind.CONTENT, zonesPerSeat = DrawerZones.PER_SEAT), TileSlots.LowerTile {

    val state = DrawerState(PlayLayout.PLAYERS)
    lateinit var controller: DrawerController
        private set

    /** Set by [DrawerTab] when it is created: its zone is cancelled with the drawer's own. */
    internal var tab: DrawerTab? = null

    private var dl: DrawerLayout? = null
    private var painter: DrawerPainter? = null

    private val host = object : DrawerController.Host {
        override val game: GameState get() = stage.session.state
        override val drawerCounters: List<Counter> get() = stage.settings.current.drawerCounters
        override fun dispatch(seat: Int, action: Action) = stage.dispatch(seat, action)
        override fun invalidate(rect: IRect) = stage.invalidate(rect)
        override fun cancelTouches(seat: Int, from: Int, until: Int) {
            stage.cancelTouches(this@DrawerComponent, seat, from, until)
            // The closed tab shares the TAB meaning (pre-T34 it was the same zone id).
            val t = tab
            if (t != null && from <= DrawerZones.TAB && DrawerZones.TAB < until) stage.cancelTouches(t, seat)
        }
    }

    override fun onAdded() {
        controller = DrawerController(state, host)
    }

    override fun occupies(seat: Int): Boolean = state.isOpen(seat)

    val layout: DrawerLayout? get() = dl

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val d = DrawerLayout(play)
        dl = d
        controller.layout = d
        tiles.lower = this
        tiles.publishCompact(
            d.numberK, d.numberTop, d.readoutK, d.readoutTop,
            d.numberSlot, d.readoutSlot, d.minusGlyph, d.plusGlyph, d.lifeZones,
        )
    }

    override fun onAttach() = controller.refreshAll()

    override fun prewarm(f: Frame) {
        val d = dl ?: return
        painter(f).prewarm(d)
    }

    private fun painter(f: Frame): DrawerPainter = painter ?: DrawerPainter(f.r, f.text, f.icons).also { painter = it }

    override fun draw(f: Frame, seat: Int) {
        val d = dl ?: return
        if (!state.isOpen(seat)) return
        painter(f).draw(d, controller, seat, f.players, f.clip)
    }

    override fun zones(seat: Int, z: ZoneSink) {
        val d = dl ?: return
        if (!state.isOpen(seat)) return
        // Bottom first: the drawer swallows, cells over it, the bar's segments (incl. their upward
        // extension into the upper area) on top.
        z.block(d.drawer[seat])
        val n = minOf(state.itemsOnPage(seat), d.perPage)
        for (slot in 0 until n) {
            val i = (seat * DrawerZones.MAX_SLOTS + slot) * 2
            z.add(DrawerZones.CELL0 + slot * 2 + DrawerZones.MINUS, d.halfHit[i])
            z.add(DrawerZones.CELL0 + slot * 2 + DrawerZones.PLUS, d.halfHit[i + 1])
        }
        z.add(DrawerZones.TAB, d.barHit[seat * 3 + 1])
        val page = when {
            state.hasNext(seat) -> DrawerZones.NEXT
            state.hasPrev(seat) -> DrawerZones.PREV
            else -> DrawerZones.TAB
        }
        z.add(page, d.barHit[seat * 3 + 2])
        z.add(DrawerZones.BUTTON, d.barHit[seat * 3])
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        val d = dl ?: return false
        val open = state.isOpen(seat)
        val r = when {
            local == DrawerZones.TAB -> if (open) d.barSeg[seat * 3 + 1] else d.play.tab[seat]
            !open -> return false
            local == DrawerZones.BUTTON -> d.cmdVis[seat]
            local == DrawerZones.PREV || local == DrawerZones.NEXT -> d.barSeg[seat * 3 + 2]
            else -> {
                val slot = DrawerZones.slotOf(local)
                if (slot >= d.perPage) return false
                d.halfVis[(seat * DrawerZones.MAX_SLOTS + slot) * 2 + DrawerZones.halfOf(local)]
            }
        }
        out.set(r.left, r.top, r.right, r.bottom)
        return true
    }

    override fun repeats(seat: Int, local: Int): Boolean = controller.repeats(seat, local)

    override fun onTap(seat: Int, local: Int) {
        if (local == DrawerZones.BUTTON) stage.send(Events.COMMANDER_TARGET, seat) else controller.onTap(seat, local)
    }

    override fun onRepeat(seat: Int, local: Int) {
        controller.onRepeat(seat, local)
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) = controller.onStateChanged(old, new)

    override fun onPlayersChanged() = controller.refreshAll()

    override fun onSettingsChanged(old: AppSettings, new: AppSettings) {
        if (old.drawerCounters != new.drawerCounters) controller.onCountersChanged()
    }

    /** A cover now hiding a seat closes its drawer (commander mode closes them all). */
    override fun onCoversChanged() {
        for (s in 0 until PlayLayout.PLAYERS) if (state.isOpen(s) && !visible(s)) controller.close(s)
    }
}
