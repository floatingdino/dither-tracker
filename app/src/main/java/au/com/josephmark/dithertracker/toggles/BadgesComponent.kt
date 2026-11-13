package au.com.josephmark.dithertracker.toggles

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.Toggle
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerItems
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer

/**
 * State-toggle badges (T14): while a seat is full (its drawer closed), each toggle the player holds —
 * monarch, initiative, city's blessing, ring-bearer — shows as its icon in ink on the tile's paper,
 * a centred row in the under-number row between the counter peeks ([BadgeLayout]). Display only: the
 * life halves keep their touches underneath. They are set in the drawer (`drawer.DrawerItems`
 * toggle cells).
 *
 * Hidden while the drawer is open ([TileSlots.compact]; the drawer shows the same toggles as cells),
 * in commander mode and under overlays ([Kind.CONTENT]), under the KO tile (drawn after), and on the
 * sole survivor's tile (the winner's detail chip uses the same band). A toggle change redraws only
 * the changed seats' rows — a monarch moving redraws two rows.
 */
class BadgesComponent : Component(Layer.TILE, Kind.CONTENT) {

    var layout: BadgeLayout? = null
        private set
    private var t: TileSlots? = null
    private val icon = IRect()

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val b = BadgeLayout(play)
        layout = b
        t = tiles
        stage.log?.invoke("$b")
    }

    override fun prewarm(f: Frame) {
        val b = layout ?: return
        f.icons.prewarm(DrawerItems.TOGGLE_ICONS, b.tk)
    }

    override fun draw(f: Frame, seat: Int) {
        val b = layout ?: return
        if (!b.enabled || t?.compact(seat) == true || !f.hit(b.row[seat])) return
        val ps = f.players
        if (seat >= ps.size) return
        val p = ps[seat]
        var n = 0
        for (tg in TOGGLES) if (p.toggles[tg]) n++
        if (n == 0 || soleSurvivor(seat)) return
        val rot = b.play.rotation[seat]
        var i = 0
        for (tg in TOGGLES) {
            if (!p.toggles[tg]) continue
            b.badge(seat, i++, n, icon)
            f.icons.icon(b.tk, DrawerItems.TOGGLE_ICONS[tg.ordinal], icon, rot, invert = false, opaque = false)
        }
    }

    /** True when [seat] is the last player standing (the winner's tile owns the band). */
    private fun soleSurvivor(seat: Int): Boolean {
        val s = stage.session.state
        if (s.players.size < 2 || s.isKo(s.players[seat])) return false
        for (i in s.players.indices) if (i != seat && !s.isKo(s.players[i])) return false
        return true
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        val b = layout ?: return
        if (!b.enabled) return
        val op = old.players
        val np = new.players
        if (op.size != np.size) return // the stage redraws everything on a count change
        for (i in np.indices) {
            val a = op[i]
            val c = np[i]
            if (a === c || a.toggles == c.toggles || t?.compact(i) == true || !visible(i)) continue
            invalidate(b.row[i])
        }
    }

    companion object {
        private val TOGGLES: Array<Toggle> = Toggle.entries.toTypedArray()
    }
}
