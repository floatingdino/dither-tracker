package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.widgets.CharBuf
import au.com.josephmark.dithertracker.turns.TimeFormat
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.KoReason
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.RecentPicks
import au.com.josephmark.dithertracker.dither.HaloMask
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
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.widgets.TextSlice
import kotlin.random.Random

/**
 * The knocked-out tile (T15). Always on: a seat is dead while `state.isKo(player)` (life ≤ 0,
 * poison ≥ 10, 21 from one commander, or marked dead), derived from the saved game, so it survives
 * process death; only the quip is re-picked on restore.
 *
 * - **Look** ([KoLayout], T42): the panel in `Role.DEAD` (a light dither) inset by a paper gutter
 *   and kept off the centre diamond; the tombstone as a small emblem above **the quip, which replaces
 *   the life total** as the hero — caps, `ui` face at one size for every line (T47), on one
 *   continuous paper ground cut out of the field (a bar per line, joined into a stepped shape);
 *   emblem and quip centred together on the tile (T51). No life total anywhere (T51, Sam): only a
 *   poison or commander-damage KO keeps one quiet cause chip below (poison + count, imp + damage)
 *   ([KoLayout.DEAD_CAUSE]); life and marked-dead KOs show none ([KoLayout.DEAD]). The quip is
 *   picked once when the KO happens (`Flavour.ko(reason, …)`, never the previous line), not on
 *   redraw; re-picked when the player dies again or the cause changes.
 * - **Cover**: while a seat shows dead, [cover] hides its life, peeks, drawer, tab, readout and
 *   commander-mode content (`CONTENT | CHROME | READOUT | MODE`) — so they neither draw nor take
 *   touches, an open drawer closes and the readout commits (`Stage.setCover`). This component is
 *   [Kind.STATUS]: commander mode does not hide it (a dead player stays dead there), full overlays do.
 * - **Revive** is deliberate: the whole tile is one zone; the first tap arms it (the plate turns ink
 *   and reads `REVIVE?` for [ARM_MS]); a second tap while armed dispatches [Action.Revive]; the
 *   timeout disarms. The armed plate is ink with a paper keyline between it and its shadow. A press
 *   inverts the quip's ground (or the armed plate).
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
    private val ci = IRect()
    private val cn = IRect()
    private val scratch = IRect()
    private var tomb: MonoImage = Icon.DEAD
    private var tombHalo: MonoImage = Icon.DEAD.halo // the emblem's contour cut-out ([HaloMask.of], k.halo px)
    private val chip = IRect()

    /** T66: the winner screen's time report shows ([KoTimes.on]); the dead heroes then keep the detail band free. */
    var times = false
        private set
    private val timeBuf = CharBuf(TimeFormat.MAX_CHARS)
    private val ti = IRect()
    private val tt = IRect()
    private val tc = IRect()

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
        // The runtime halo, not `Icon.halo` scaled: the rim stays k.halo px when the emblem steps down.
        tombHalo = HaloMask.of(tomb, k.halo)
        stage.log?.invoke("$k")
        for (s in 0 until P) {
            disarm(s)
            if (s < play.playerCount && quips[s].isNotEmpty()) k.arrange(s, quips[s], blocks[s], modeOf(reasons[s]), times)
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
        f.r.blit(tombHalo, 0, 0)
        f.icons.prewarm(CAUSE_ICONS, k.causeK)
        f.icons.prewarm(TIME_ICONS, k.causeK)
        for (rot in k.play.rotations) {
            for (q in 1..k.warmK) f.text.draw(k.ui, warmUi(k.ui), 0, 0, q, rot)
            f.text.draw(k.causeFont, WARM_NUM, 0, 0, k.causeK, rot)
        }
    }

    // ---- state ---------------------------------------------------------------------------------

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        if (layout == null) return
        val np = new.players
        val op = old.players
        // A dead player's cause count changed: redraw the detail chip (life is not shown, T51).
        val k = layout!!
        for (s in 0 until minOf(P, np.size, op.size)) {
            val a = op[s]
            val b = np[s]
            if (a === b || (shown ushr s) and 1 == 0 || new.koReason(b) != reasons[s]) continue
            if (modeOf(reasons[s]) != KoLayout.DEAD_CAUSE) continue
            if (a.counters.poison != b.counters.poison || a.maxCommanderDamage != b.maxCommanderDamage) {
                k.detailBand(s, scratch); invalidate(scratch)
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
                k.arrange(s, quips[s], blocks[s], modeOf(reason), times)
                if (changedWhileShown && invalidate) invalidate(k.panels[s])
                stage.log?.invoke("KO seat $s ($reason): '${quips[s]}'")
            }
        }
        val newlyHeld = mask and dead.inv() and held
        dead = mask
        // T66: a winner with a timer mode on: every dead tile reports its time (re-arranged once).
        val on = KoTimes.on(state, stage.settings.current)
        if (on != times) {
            times = on
            for (s in 0 until minOf(P, ps.size)) {
                if (quips[s].isEmpty()) continue
                k.arrange(s, quips[s], blocks[s], modeOf(reasons[s]), on)
                if (invalidate && (shown ushr s) and 1 != 0) invalidate(k.panels[s])
            }
        }
        applyCover()
        // A held seat (an effect is playing) keeps its life halves: stop a hold-repeat that killed it.
        // Only on that seat (T57): another player's hold keeps going.
        if (newlyHeld != 0) for (s in 0 until P) if ((newlyHeld ushr s) and 1 != 0) stage.cancelSeatTouches(s)
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
        invalidate(blocks[seat].area)
    }

    private fun disarm(seat: Int) {
        val bit = 1 shl seat
        if (armed and bit == 0) return
        armed = armed and bit.inv()
        timers.cancel(ARM_TIMER + seat)
        layout?.let { if (seat < it.play.playerCount) invalidate(blocks[seat].area) }
    }

    override fun zones(seat: Int, z: ZoneSink) {
        val k = layout ?: return
        if ((shown ushr seat) and 1 != 0) z.add(ZONE, k.panels[seat])
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        if ((shown ushr seat) and 1 == 0) return false
        val b = blocks[seat]
        val p = if ((armed ushr seat) and 1 != 0) b.plate else b.bounds
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

    override fun onSettingsChanged(old: AppSettings, new: AppSettings) {
        if (old.turnTracker != new.turnTracker || old.turnTimer != new.turnTimer) sync(stage.session.state, invalidate = true)
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

        // Field: paper, the dead dither inset by the gutter, a paper ring round the diamond.
        val panel = l.seats[seat] // T60: the whole tile
        f.r.rect(panel, ink = false)
        f.r.fill(Role.DEAD, k.field(seat, scratch))
        val cy = l.diamondCy
        val lim = l.diamondHalf + k.gutter
        for (dy in -lim..lim) {
            val y = cy + dy
            if (y < panel.top || y >= panel.bottom) continue
            val hw = k.ringRowHalf(dy)
            val x0 = maxOf(panel.left, l.diamondCx - hw)
            val x1 = minOf(panel.right, l.diamondCx + hw + 1)
            if (x1 > x0) f.r.rect(x0, y, x1 - x0, 1, ink = false)
        }

        // Emblem: the tombstone, cut out of the field in its own shape (placed with the quip).
        val em = b.emblem
        if (f.hit(em)) f.r.blitHaloed(tomb, tombHalo, em.left, em.top, rot)

        // Detail (poison / commander-damage KOs only, T51): the cause and its count. Never the life total.
        val reason = reasons[seat]
        if (b.mode == KoLayout.DEAD_CAUSE) {
            val poison = reason == KoReason.POISON
            val value = if (poison) p.counters.poison else p.maxCommanderDamage
            if (k.detail(seat, value, ci, cn, chip) && f.hit(chip)) {
                f.r.rect(chip, ink = false)
                f.icons.icon(k.causeK, if (poison) Icon.POISON else Icon.COMMANDER, ci, rot, invert = false, opaque = false)
                f.text.drawInt(k.causeFont, value, cn.left, cn.top, k.causeK, rot)
            }
        }

        // T66: the time report (winner decided, a timer mode on): the timer icon and m:ss on a paper chip.
        if (times) {
            val st = stage.session.state
            if (seat < st.players.size) {
                KoTimes.format(st, stage.settings.current, seat, timeBuf)
                val font = k.report(seat, b.mode, timeBuf.length, ti, tt, tc)
                if (font != null && f.hit(tc)) {
                    f.r.rect(tc, ink = false)
                    if (!ti.isEmpty) f.icons.icon(k.causeK, Icon.TIMER, ti, rot, invert = false, opaque = false)
                    f.text.draw(font, timeBuf, tt.left, tt.top, k.causeK, rot)
                }
            }
        }

        // Hero: the armed revive plate, or the quip in ink on one continuous paper ground cut out of the field.
        if ((armed ushr seat) and 1 != 0) {
            if (!f.hit(b.keyline) && !f.hit(b.shadow)) return
            f.r.rect(b.shadow, ink = true)
            f.r.rect(b.keyline, ink = false)
            f.r.rect(b.plate, ink = true)
            f.text.draw(k.ui, KoLayout.REVIVE, b.reviveText.left, b.reviveText.top, b.reviveK, rot, invert = true)
            return
        }
        if (!f.hit(b.bounds)) return
        val q = quips[seat]
        for (i in 0 until b.lines) f.r.rect(b.ground[i], ink = false)
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
        private const val P = PlayLayout.MAX_PLAYERS

        const val ZONE = 0

        /** Timer ids: `ARM_TIMER + seat` disarms, `HOLD_TIMER + seat` ends a hold. */
        const val ARM_TIMER = 0
        const val HOLD_TIMER = P

        /** How long an armed revive waits for the confirming tap. */
        const val ARM_MS = 4_000L

        /** The longest an effect may hold the dead treatment off (safety net). */
        const val HOLD_MAX_MS = 4_000L

        private val CAUSE_ICONS = arrayOf(Icon.POISON, Icon.COMMANDER)
        private val TIME_ICONS = arrayOf(Icon.TIMER)

        /** The layout mode for a KO [reason]: a chip only where the cause has its own count (T51). */
        fun modeOf(reason: KoReason?): Int = when (reason) {
            KoReason.POISON, KoReason.COMMANDER_DAMAGE -> KoLayout.DEAD_CAUSE
            else -> KoLayout.DEAD
        }
        /** Every glyph of [font] (quips use punctuation beyond A–Z 0–9; T20: those were built in onDraw). Prewarm only. */
        private fun warmUi(font: Font): String = font.glyphs.joinToString("") { it.char.toString() }
        private const val WARM_NUM = "0123456789-:"
    }
}
