package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.KoReason
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.RecentPicks
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Cover
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.ZoneSink
import au.com.josephmark.dithertracker.widgets.TextSlice
import kotlin.random.Random

/**
 * The knocked-out tile (T15). Always on: a seat is dead while `state.isKo(player)` (life ≤ 0,
 * poison ≥ 10, 21 from one commander, or marked dead), derived from the saved game, so it survives
 * process death; only the quip is re-picked on restore.
 *
 * - **Look** ([KoLayout]): the panel in `Role.DEAD` (crosshatch); the tombstone (and the cause for
 *   poison / commander damage) in the top band; the final life total where it always is, knocked out
 *   of the hatch as a paper cut-out with an ink ring; the KO quip on a paper plate with a sticker
 *   shadow below. The quip is picked once when the KO happens (`Flavour.ko(reason, …)`, never the
 *   previous line), not on redraw; re-picked when the player dies again or the cause changes.
 * - **Cover**: while a seat shows dead, [cover] hides its life, peeks, drawer, tab, readout and
 *   commander-mode content (`CONTENT | CHROME | READOUT | MODE`) — so they neither draw nor take
 *   touches, an open drawer closes and the readout commits (`Stage.setCover`). This component is
 *   [Kind.STATUS]: commander mode does not hide it (a dead player stays dead there), full overlays do.
 * - **Revive** is deliberate: the whole tile is one zone; the first tap arms it (the plate turns ink
 *   and reads `REVIVE?` for [ARM_MS]); a second tap while armed dispatches [Action.Revive]; the
 *   timeout disarms. A press inverts the plate.
 * - **Effects hook** ([Events.KO_HOLD] / [Events.KO_RELEASE]): an effect can hold the dead treatment
 *   off a seat while it plays; released by the effect, by any touch-down on the seat, or after
 *   [HOLD_MAX_MS]. Nobody holding = immediate.
 */
class KoComponent(
    private val random: Random = Random(System.nanoTime()),
    private val flavour: Flavour = Flavour.of(true),
) : Component(Layer.TILE, Kind.STATUS, zonesPerSeat = 1, timerCount = 2 * P) {

    /** What a dead tile hides beneath its treatment. */
    lateinit var cover: Cover
        private set

    var layout: KoLayout? = null
        private set

    private var l: PlayLayout? = null
    private var t: TileSlots? = null

    /** Seats whose player is KO (bit `1 shl seat`), from the game state. */
    var dead = 0
        private set

    /** Seats an effect is holding the dead treatment off. */
    var held = 0
        private set

    /** Seats with a revive armed. */
    var armed = 0
        private set

    /** Seats showing the dead treatment (= the [cover]'s seats). */
    val shown: Int get() = dead and held.inv()

    private val quips = Array(P) { "" }
    private val reasons = arrayOfNulls<KoReason>(P)
    private var recent = RecentPicks.EMPTY
    private val blocks = Array(P) { KoLayout.Block() }

    // Draw scratch.
    private val slice = TextSlice()
    private val tr = IRect()
    private val ci = IRect()
    private val cn = IRect()
    private val fp = IRect()
    private val scratch = IRect()
    private var tomb: MonoImage = Icon.DEAD

    /** The quip shown on [seat] ("" when alive), in caps. */
    fun quip(seat: Int): String = quips[seat]

    fun block(seat: Int): KoLayout.Block = blocks[seat]

    override fun onAdded() {
        cover = stage.addCover(Cover(Kind.CONTENT or Kind.CHROME or Kind.READOUT or Kind.MODE))
    }

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
        t = tiles
        val k = KoLayout(play)
        layout = k
        tomb = if (k.iconK == 1) Icon.DEAD else Zoomed(Icon.DEAD, k.iconK)
        stage.log?.invoke("$k")
        for (s in 0 until P) {
            disarm(s)
            if (quips[s].isNotEmpty()) k.arrange(s, quips[s], blocks[s])
        }
        sync(stage.session.state, invalidate = false)
    }

    override fun onAttach() {
        if (l != null) sync(stage.session.state, invalidate = true)
    }

    override fun onPlayersChanged() {
        for (s in 0 until P) {
            quips[s] = ""; reasons[s] = null; disarm(s); release(s, apply = false)
        }
        dead = 0
        sync(stage.session.state, invalidate = false)
    }

    override fun prewarm(f: Frame) {
        val k = layout ?: return
        f.r.blit(tomb, 0, 0)
        f.icons.prewarm(CAUSE_ICONS, k.causeK)
        for (rot in ROTATIONS) {
            for (q in 1..k.quipKMax) f.text.draw(k.ui, WARM_UI, 0, 0, q, rot)
            f.text.draw(k.causeFont, WARM_NUM, 0, 0, k.causeK, rot)
        }
    }

    // ---- state ---------------------------------------------------------------------------------

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        if (layout == null) return
        val np = new.players
        val op = old.players
        // A dead player's own numbers changed: redraw the cut-out number and the cause.
        val k = layout!!
        for (s in 0 until minOf(P, np.size, op.size)) {
            val a = op[s]
            val b = np[s]
            if (a === b || (shown ushr s) and 1 == 0 || new.koReason(b) != reasons[s]) continue
            if (a.life != b.life) {
                val n = l!!.numberSlot[s]
                val o = k.outline
                scratch.set(n.left - o, n.top - o, n.right + o, n.bottom + o)
                invalidate(scratch)
            }
            if (a.counters.poison != b.counters.poison || a.maxCommanderDamage != b.maxCommanderDamage) {
                k.topBand(s, scratch); invalidate(scratch)
            }
        }
        sync(new, invalidate = true)
    }

    /** Brings [dead], the quips and the [cover] in line with [state]. */
    private fun sync(state: GameState, invalidate: Boolean) {
        val k = layout ?: return
        val ps = state.players
        var mask = 0
        for (s in 0 until minOf(P, ps.size)) {
            val reason = state.koReason(ps[s])
            val bit = 1 shl s
            if (reason == null) {
                if (dead and bit != 0) {
                    quips[s] = ""; reasons[s] = null
                    disarm(s)
                    release(s, apply = false)
                }
                continue
            }
            mask = mask or bit
            if (dead and bit == 0 || reasons[s] != reason || quips[s].isEmpty()) {
                val picked = flavour.ko(reason, random, recent)
                recent = picked.recent
                quips[s] = picked.value.uppercase()
                val changedWhileShown = dead and bit != 0 && (shown ushr s) and 1 != 0
                reasons[s] = reason
                k.arrange(s, quips[s], blocks[s])
                if (changedWhileShown && invalidate) invalidate(k.panels[s])
                stage.log?.invoke("KO seat $s ($reason): '${quips[s]}'")
            }
        }
        dead = mask
        applyCover()
    }

    /** Points the cover at [shown] (no-op when unchanged; `setCover` redraws the flipped panels). */
    private fun applyCover() {
        val want = shown
        if (want != cover.seats) stage.setCover(cover, want, false)
    }

    // ---- effects hook -----------------------------------------------------------------------------

    override fun onEvent(event: Int, seat: Int) {
        if (seat !in 0 until P) return
        val bit = 1 shl seat
        when (event) {
            Events.KO_HOLD -> {
                held = held or bit
                timers.schedule(HOLD_TIMER + seat, HOLD_MAX_MS)
                disarm(seat)
                applyCover()
            }
            Events.KO_RELEASE -> release(seat, apply = true)
            Events.TOUCH_DOWN -> if (held and bit != 0) release(seat, apply = true)
        }
    }

    private fun release(seat: Int, apply: Boolean) {
        val bit = 1 shl seat
        if (held and bit == 0) return
        held = held and bit.inv()
        timers.cancel(HOLD_TIMER + seat)
        if (apply) applyCover()
    }

    override fun onTimer(id: Int) {
        if (id >= HOLD_TIMER) release(id - HOLD_TIMER, apply = true)
        else if ((armed ushr id) and 1 != 0) {
            disarm(id)
        }
    }

    // ---- revive ------------------------------------------------------------------------------------

    private fun arm(seat: Int) {
        armed = armed or (1 shl seat)
        timers.schedule(ARM_TIMER + seat, ARM_MS)
        invalidate(blocks[seat].plate)
    }

    private fun disarm(seat: Int) {
        val bit = 1 shl seat
        if (armed and bit == 0) return
        armed = armed and bit.inv()
        timers.cancel(ARM_TIMER + seat)
        if (layout != null) invalidate(blocks[seat].plate)
    }

    override fun zones(seat: Int, z: ZoneSink) {
        val k = layout ?: return
        if ((shown ushr seat) and 1 != 0) z.add(ZONE, k.panels[seat])
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        if ((shown ushr seat) and 1 == 0) return false
        val p = blocks[seat].plate
        out.set(p.left, p.top, p.right, p.bottom)
        return true
    }

    override fun onTap(seat: Int, local: Int) {
        if ((shown ushr seat) and 1 == 0) return
        if ((armed ushr seat) and 1 == 0) {
            arm(seat)
            return
        }
        disarm(seat)
        val ps = stage.session.state.players
        if (seat < ps.size) stage.dispatch(seat, Action.Revive(ps[seat].id))
    }

    override fun onCoversChanged() {
        // Another cover (an overlay) hid this seat: drop a pending revive.
        for (s in 0 until P) if ((armed ushr s) and 1 != 0 && !visible(s)) disarm(s)
    }

    override fun onDetach() {
        for (s in 0 until P) disarm(s)
    }

    // ---- drawing -------------------------------------------------------------------------------------

    override fun draw(f: Frame, seat: Int) {
        if ((shown ushr seat) and 1 == 0) return
        val k = layout ?: return
        val l = l ?: return
        val b = blocks[seat]
        val p = f.players[seat]
        val rot = l.rotation[seat]
        f.r.fill(Role.DEAD, l.panels[seat])

        // Top band: tombstone, and the cause unless it was plain life loss (the cut-out says that).
        val reason = reasons[seat]
        val withCause = reason == KoReason.POISON || reason == KoReason.COMMANDER_DAMAGE
        val value = if (reason == KoReason.POISON) p.counters.poison else p.maxCommanderDamage
        val nk = k.topRow(seat, withCause, value, tr, ci, cn)
        if (f.hit(tr)) f.r.blit(tomb, tr.left, tr.top, rot)
        if (withCause && (f.hit(ci) || f.hit(cn))) {
            f.icons.icon(k.causeK, if (reason == KoReason.POISON) Icon.POISON else Icon.COMMANDER, ci, rot, invert = false, opaque = false)
            f.text.drawInt(k.causeFont, value, cn.left, cn.top, nk, rot)
        }

        // The final life total, knocked out of the hatch: an ink ring, then the numerals in paper.
        val lk = l.numberFootprint(seat, p.life, fp)
        if (f.hit(l.numberSlot[seat])) {
            val o = k.outline
            for (dy in -o..o) for (dx in -o..o) {
                if (dx == 0 && dy == 0) continue
                f.text.drawInt(l.font, p.life, fp.left + dx, fp.top + dy, lk, rot, shadow = false)
            }
            f.text.drawInt(l.font, p.life, fp.left, fp.top, lk, rot, invert = true, shadow = false)
        }

        // The plate: quip, or the armed revive.
        if (!f.hit(b.shadow) && !f.hit(b.plate)) return
        if (k.shadow > 0) f.r.rect(b.shadow, ink = true)
        if ((armed ushr seat) and 1 != 0) {
            f.r.rect(b.plate, ink = true)
            f.text.draw(k.ui, KoLayout.REVIVE, b.reviveText.left, b.reviveText.top, b.k, rot, invert = true)
            return
        }
        f.r.rect(b.plate, ink = false)
        f.r.frame(b.plate, k.border)
        val q = quips[seat]
        for (i in 0 until b.lines) {
            val r = b.line[i]
            f.text.draw(k.ui, slice.set(q, b.start[i], b.end[i]), r.left, r.top, b.k, rot)
        }
    }

    /** An icon enlarged k× (nearest neighbour), one stable instance per layout for the renderer's mask cache. */
    private class Zoomed(private val src: MonoImage, private val k: Int) : MonoImage {
        override val width: Int = src.width * k
        override val height: Int = src.height * k
        override fun isInk(x: Int, y: Int): Boolean = src.isInk(x / k, y / k)
    }

    companion object {
        private const val P = PlayLayout.PLAYERS

        const val ZONE = 0

        /** Timer ids: `ARM_TIMER + seat` disarms, `HOLD_TIMER + seat` ends a hold. */
        const val ARM_TIMER = 0
        const val HOLD_TIMER = P

        /** How long an armed revive waits for the confirming tap. */
        const val ARM_MS = 4_000L

        /** The longest an effect may hold the dead treatment off (safety net). */
        const val HOLD_MAX_MS = 4_000L

        private val ROTATIONS = intArrayOf(0, 180)
        private val CAUSE_ICONS = arrayOf(Icon.LIFE, Icon.POISON, Icon.COMMANDER)
        private const val WARM_UI = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 '.,-:?"
        private const val WARM_NUM = "0123456789-"
    }
}
