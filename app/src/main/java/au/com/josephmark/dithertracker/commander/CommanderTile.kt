package au.com.josephmark.dithertracker.commander

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.Limits
import au.com.josephmark.dithertracker.core.coalesceKey
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Cover
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Origin
import au.com.josephmark.dithertracker.stage.ZoneSink
import kotlin.random.Random

/**
 * Commander-damage mode on the tiles (T30). Entered by [Events.COMMANDER_TARGET] (the drawer bar's
 * imp button); state and rules in [CommanderMode], geometry in [CommanderLayout].
 *
 * While the mode is on, its [Cover] hides every seat's normal content and chrome ([Kind.CONTENT],
 * [Kind.CHROME]: life, peeks, drawers, tabs, the menu diamond) and this component takes the tiles:
 * opponents show the damage they dealt the target (halves, or quarters with a partner), the target
 * shows the quip and its life. The readout ([Kind.READOUT]) stays: damage shows on the source seat,
 * linked life loss on the target ([Origin.ATTRIBUTED] dispatches, fed to the readouts here). The
 * close X on the diamond is [CommanderClose].
 *
 * T52: every tile also shows its player's PARTNER tab (see [CommanderLayout], [CommanderMode]): tap to
 * give their commander a partner (the tile splits into quarters) or take it away; when the partner
 * has damage or tax recorded the tab says HOLD and only a hold drops it. One undo step each.
 */
class CommanderTile(private val random: Random = Random(System.nanoTime())) :
    Component(Layer.TILE, Kind.MODE, zonesPerSeat = CommanderZones.PER_SEAT, timerCount = CommanderMode.TIMERS), CommanderMode.Host {

    lateinit var mode: CommanderMode
        private set

    /** What the mode hides while it is on. */
    lateinit var cover: Cover
        private set

    var layout: CommanderLayout? = null
        private set

    private var l: PlayLayout? = null
    private var t: TileSlots? = null
    private val label = UpperLabel()
    private val fp = IRect()
    private val fi = IRect()
    private val pIcon = IRect()
    private val pLabel = IRect()

    override fun onAdded() {
        mode = CommanderMode(timers, this, random)
        cover = stage.addCover(Cover(Kind.CONTENT or Kind.CHROME))
    }

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
        t = tiles
        layout = CommanderLayout(play)
        mode.exit()
        stage.log?.invoke("$layout")
    }

    override fun prewarm(f: Frame) {
        val c = layout ?: return
        for (rot in c.play.rotations) for (k in 1..c.quipKMax) f.text.draw(c.ui, WARM_UI, 0, 0, k, rot)
        f.icons.prewarm(WARM_ICONS, c.tk)
    }

    override fun onDetach() = mode.exit()

    override fun onTimer(id: Int) = mode.onTimer(id)

    override fun onEvent(event: Int, seat: Int) {
        when (event) {
            Events.COMMANDER_TARGET -> mode.press(seat)
            Events.PAUSE -> mode.clearHint()
        }
    }

    // ---- CommanderMode.Host ------------------------------------------------------------------------

    override val game: GameState get() = stage.session.state

    override fun dispatchDamage(sourceSeat: Int, action: Action.ChangeCommanderDamage) {
        val t = mode.target
        val old = stage.session.state
        val key = action.coalesceKey
        val ro = stage.readouts
        // One readout group per seat per commander slot: switching slot commits the seat's group.
        if (ro.group(sourceSeat) != key) ro.commit(sourceSeat)
        stage.dispatch(Origin.ATTRIBUTED, action, key)
        val new = stage.session.state
        if (new === old || t !in new.players.indices) return
        ro.setGroup(sourceSeat, key)
        ro.add(sourceSeat, mode.damage(sourceSeat, action.slot, new) - mode.damage(sourceSeat, action.slot, old))
        // Linked life loss shows on the target's own readout.
        ro.add(t, new.players[t].life - old.players[t].life)
    }

    override fun dispatchPartner(seat: Int, on: Boolean) {
        val ps = stage.session.state.players
        if (seat !in ps.indices) return
        // Its own undo step (no coalesce key) and log entry; external: open readouts commit first.
        stage.dispatch(Origin.EXTERNAL, Action.SetPartner(ps[seat].id, on))
        stage.log?.invoke("commander mode: seat $seat partner $on")
    }

    override fun onHintChanged(seat: Int) {
        val c = layout ?: return
        if (seat in 0 until c.play.playerCount) invalidate(c.partnerTab[seat])
    }

    override fun onModeChanged(old: Int, new: Int) {
        // Every tile changes meaning: the cover cancels held touches, commits readouts, closes drawers
        // and redraws every panel and the diamond.
        val on = new != CommanderMode.NONE
        stage.setCover(cover, if (on) Cover.ALL_SEATS else 0, on)
        stage.log?.invoke("commander mode $old -> $new '${mode.quip}'")
    }

    // ---- state ---------------------------------------------------------------------------------------

    /** In the mode: redraw exactly the tiles whose shown damage / life / partner split changed. */
    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        if (!mode.active) return
        val l = l ?: return
        val c = layout ?: return
        val tg = mode.target
        val op = old.players
        val np = new.players
        if (tg !in np.indices) { mode.exit(); return }
        if (np[tg].life != op[tg].life) invalidate(c.lifeRow[tg])
        if (np[tg].partner != op[tg].partner) invalidate(c.partnerTab[tg])
        for (s in np.indices) {
            if (s == tg) continue
            if (np[s].partner != op[s].partner) {
                invalidate(l.panels[s]); continue
            }
            if (mode.damage(s, Commander.PRIMARY, old) != mode.damage(s, Commander.PRIMARY, new) ||
                mode.damage(s, Commander.PARTNER, old) != mode.damage(s, Commander.PARTNER, new)
            ) invalidate(c.content[s])
        }
    }

    // ---- touch ---------------------------------------------------------------------------------------

    override fun zones(seat: Int, z: ZoneSink) {
        if (!mode.active) return
        val l = l ?: return
        val c = layout ?: return
        val players = stage.session.state.players
        if (seat == mode.target) {
            z.add(CommanderZones.TARGET, l.panels[seat])
        } else if (seat < players.size && players[seat].partner) {
            for (i in 0 until 4) z.add(CommanderZones.A_MINUS + i, c.quarter[seat * 4 + i])
        } else {
            z.add(CommanderZones.A_MINUS, l.zones[PlayLayout.zone(seat, PlayLayout.MINUS)])
            z.add(CommanderZones.A_PLUS, l.zones[PlayLayout.zone(seat, PlayLayout.PLUS)])
        }
        if (seat < players.size && t?.compact(seat) != true) z.add(CommanderZones.PARTNER, c.partnerHit[seat])
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        val c = layout ?: return false
        return c.feedbackRect(seat, local, stage.session.state.players, out)
    }

    override fun repeats(seat: Int, local: Int): Boolean =
        if (local == CommanderZones.PARTNER) mode.partnerRepeats(seat) else mode.repeats(seat, local)

    override fun onTap(seat: Int, local: Int) {
        if (local == CommanderZones.PARTNER) mode.partnerTap(seat) else mode.onTap(seat, local)
    }

    override fun onRepeat(seat: Int, local: Int) {
        if (local == CommanderZones.PARTNER) { mode.partnerHold(seat); return }
        mode.holdStep = stage.holdStep // damage holds step like life (designer, T30 pass 2)
        mode.onRepeat(seat, local)
    }

    // ---- drawing -------------------------------------------------------------------------------------

    override fun draw(f: Frame, seat: Int) {
        if (!mode.active) return
        val l = l ?: return
        val c = layout ?: return
        if (t?.compact(seat) == true) return
        val players = f.players
        val tg = mode.target
        val rot = l.rotation[seat]
        if (tg !in players.indices) return
        if (seat < players.size) partnerTab(f, c, seat, players[seat].partner, rot)
        if (seat == tg) {
            // The target: one composed block — the quip in caps, a dotted rule, then the life.
            val q = mode.quip
            if (f.hit(c.lifeRow[seat])) {
                label.set("", q, q.length)
                val k = c.quipFootprint(seat, label.length, fp)
                f.text.draw(c.ui, label, fp.left, fp.top, k, rot)
                val life = players[seat].life
                c.ruleFootprint(seat, label.length, life, fp)
                f.r.dottedH(fp.left, fp.top, fp.width)
                val lk = c.lifeFootprint(seat, label.length, life, fi, fp)
                f.icons.icon(c.tk, Icon.LIFE, fi, rot, invert = false, opaque = false)
                f.text.drawInt(c.lifeFont, life, fp.left, fp.top, lk, rot)
            }
            return
        }
        // Opponents: just the damage number(s) and ± (partner tiles: a small icon per commander).
        val src = players[seat]
        if (!src.partner) {
            val v = mode.damage(seat, Commander.PRIMARY)
            if (l.pmVisible) {
                if (f.hit(l.minusGlyph[seat])) f.text.draw(l.pmFont, MINUS, l.minusGlyph[seat].left, l.minusGlyph[seat].top, l.pmK, rot)
                if (f.hit(l.plusGlyph[seat])) f.text.draw(l.pmFont, PLUS, l.plusGlyph[seat].left, l.plusGlyph[seat].top, l.pmK, rot)
            }
            if (f.hit(l.numberSlot[seat])) {
                val k = l.numberFootprint(seat, v, fp)
                drawDamage(f, c, v, k, rot)
            }
        } else {
            for (slot in 0..1) {
                val v = mode.damage(seat, slot)
                if (f.hit(c.content[seat])) {
                    val k = c.partnerNumberFootprint(seat, slot, v, fp)
                    drawDamage(f, c, v, k, rot)
                    if (c.partnerPmClear(v)) {
                        val m = c.partnerMinus[seat * 2 + slot]
                        val pl = c.partnerPlus[seat * 2 + slot]
                        f.text.draw(l.pmFont, MINUS, m.left, m.top, l.pmK, rot)
                        f.text.draw(l.pmFont, PLUS, pl.left, pl.top, l.pmK, rot)
                    }
                }
            }
            if (l.underH > 0 && f.hit(l.underRow[seat])) {
                f.icons.icon(c.tk, Icon.COMMANDER, c.partnerIcon[seat * 2], rot, invert = false, opaque = false)
                f.icons.icon(c.tk, Icon.PARTNER, c.partnerIcon[seat * 2 + 1], rot, invert = false, opaque = false)
            }
        }
    }

    /**
     * [seat]'s PARTNER tab: outlined paper when off, solid ink when on (content in paper), the
     * `partner` icon and PARTNER — or HOLD while [CommanderMode.hint] is this seat.
     */
    private fun partnerTab(f: Frame, c: CommanderLayout, seat: Int, on: Boolean, rot: Int) {
        val tab = c.partnerTab[seat]
        if (!f.hit(tab)) return
        val r = f.r
        if (on) r.rect(tab, ink = true) else { r.rect(tab, ink = false); r.frame(tab, 1, ink = true) }
        val text = if (mode.hint == seat) CommanderLayout.HOLD_LABEL else CommanderLayout.PARTNER_LABEL
        c.partnerContent(seat, text.length, pIcon, pLabel)
        if (c.partnerIconShown) f.icons.icon(c.tk, Icon.PARTNER, pIcon, rot, invert = on, opaque = false)
        if (c.partnerLabelShown) f.text.draw(c.ui, text, pLabel.left, pLabel.top, c.tk, rot, invert = on)
    }

    /** A damage number at footprint [fp]: lethal (≥ 21) on a solid ink card, in paper. */
    private fun drawDamage(f: Frame, c: CommanderLayout, v: Int, k: Int, rot: Int) {
        val lethal = v >= Limits.LETHAL_COMMANDER_DAMAGE
        if (lethal) {
            val pad = c.lethalPad
            f.r.rect(fp.left - pad, fp.top - pad, fp.width + 2 * pad, fp.height + 2 * pad, ink = true)
        }
        f.text.drawInt(c.play.font, v, fp.left, fp.top, k, rot, invert = lethal)
    }

    companion object {
        private const val MINUS = "-"
        private const val PLUS = "+"
        private const val WARM_UI = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 '.,-&"
        private val WARM_ICONS = arrayOf(Icon.LIFE, Icon.COMMANDER, Icon.PARTNER, Icon.CLOSE)
    }
}
