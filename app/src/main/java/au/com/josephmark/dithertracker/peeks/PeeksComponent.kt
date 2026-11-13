package au.com.josephmark.dithertracker.peeks

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerItems
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer

/**
 * Counter peeks (T32, `split` corners since T38): while a seat is full (its drawer closed), each
 * non-zero tray item shows as a small icon + value chip in the tile's near corners either side of
 * the tab, alternating left/right from the bottom up, six slots, overflowing as `+N` in the sixth;
 * poison at 8+ is an inverted chip. Layout in [PeekLayout]; the item list and icons come from the
 * drawer's pure tray model ([DrawerItems]). Hidden while the drawer is open ([TileSlots.compact])
 * and in commander mode ([Kind.CONTENT] is covered). Display only: the life halves keep their
 * touches underneath. Chips are drawn opaque on paper (the tile behind them is paper).
 */
class PeeksComponent : Component(Layer.TILE, Kind.CONTENT) {

    private var pk: PeekLayout? = null
    private var t: TileSlots? = null

    // Per-draw scratch, sized for the longest tray.
    private val items = IntArray(DrawerItems.MAX_ITEMS)
    private val values = IntArray(DrawerItems.MAX_ITEMS)
    private val chip = IRect()
    private val icon = IRect()
    private val fp = IRect()
    private val cornerL = Array(PlayLayout.MAX_PLAYERS) { IRect() }
    private val cornerR = Array(PlayLayout.MAX_PLAYERS) { IRect() }

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val k = PeekLayout(play)
        pk = k
        t = tiles
        for (s in 0 until play.playerCount) { k.corner(s, 0, cornerL[s]); k.corner(s, 1, cornerR[s]) }
        stage.log?.invoke("$k")
    }

    override fun prewarm(f: Frame) {
        val k = pk ?: return
        f.icons.prewarm(DrawerItems.TRAY_ICONS, k.tk)
        for (rot in k.play.rotations) f.text.draw(k.num, WARM_CHARS, 0, 0, k.tk, rot)
    }

    override fun draw(f: Frame, seat: Int) {
        val k = pk ?: return
        val t = t ?: return
        if (t.compact(seat) || !k.enabled || !(f.hit(cornerL[seat]) || f.hit(cornerR[seat]))) return
        val p = f.players[seat]
        val all = DrawerItems.tray(p, stage.settings.current.drawerCounters, items)
        var n = 0
        for (i in 0 until all) {
            val v = DrawerItems.value(items[i], p)
            if (v == 0) continue
            items[n] = items[i]
            values[n] = v
            n++
        }
        if (n == 0) return
        val shown = k.shown(n)
        val rot = k.play.rotation[seat]
        for (i in 0 until shown) {
            val v = values[i]
            val warn = DrawerItems.isWarning(items[i], v)
            // Opaque: a paper card (ink when nearly lethal) the size of the content plus its pad.
            k.chip(seat, i, k.contentWidth(v), warn, chip)
            f.r.rect(chip, ink = warn)
            k.icon(seat, i, v, icon)
            f.icons.icon(k.tk, DrawerItems.iconFor(items[i]), icon, rot, invert = warn, opaque = false)
            k.value(seat, i, v, fp)
            f.text.drawInt(k.num, v, fp.left, fp.top, k.tk, rot, invert = warn, tracking = k.tracking(v))
        }
        if (shown < n) {
            val more = n - shown
            k.chip(seat, shown, k.moreWidth(more), false, chip)
            f.r.rect(chip, ink = false)
            k.more(seat, shown, more, fp)
            f.text.drawInt(k.num, more, fp.left, fp.top, k.tk, rot, signed = true)
        }
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        val t = t ?: return
        val op = old.players
        val np = new.players
        for (i in np.indices) {
            val a = op[i]
            val b = np[i]
            if (a === b || t.compact(i) || !visible(i)) continue
            if (a.counters != b.counters || a.commanderTax != b.commanderTax || a.partner != b.partner) invalidateCorners(i)
        }
    }

    override fun onSettingsChanged(old: AppSettings, new: AppSettings) {
        if (pk == null) return
        if (old.drawerCounters != new.drawerCounters) for (s in 0 until (pk?.play?.playerCount ?: 0)) invalidateCorners(s)
    }

    private fun invalidateCorners(seat: Int) {
        invalidate(cornerL[seat])
        invalidate(cornerR[seat])
    }

    companion object {
        private const val WARM_CHARS = "0123456789+-"
    }
}
