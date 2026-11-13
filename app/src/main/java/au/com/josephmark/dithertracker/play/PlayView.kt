package au.com.josephmark.dithertracker.play

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import au.com.josephmark.dithertracker.commander.CommanderLayout
import au.com.josephmark.dithertracker.commander.CommanderMode
import au.com.josephmark.dithertracker.commander.CommanderZones
import au.com.josephmark.dithertracker.commander.UpperLabel
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.Limits
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.core.coalesceKey
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.drawer.DrawerController
import au.com.josephmark.dithertracker.drawer.DrawerItems
import au.com.josephmark.dithertracker.drawer.DrawerLayout
import au.com.josephmark.dithertracker.drawer.DrawerPainter
import au.com.josephmark.dithertracker.drawer.DrawerState
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.peeks.PeekLayout
import au.com.josephmark.dithertracker.readout.PendingDeltas
import au.com.josephmark.dithertracker.stage.PlayInput
import au.com.josephmark.dithertracker.type.TextRenderer
import kotlin.random.Random

/**
 * The 4-player Play screen: one opaque custom View, geometry from [PlayLayout] and (T08)
 * [DrawerLayout]; drawer behaviour in [DrawerController], drawer drawing in [DrawerPainter];
 * commander-damage mode (T30) in [CommanderMode] / [CommanderLayout]; counter peeks (T32) laid out
 * by [PeekLayout].
 *
 * Drawing: everything goes through [DitherRenderer] in root coordinates (this View fills the
 * window at (0,0)); rotated seats use blit rotation via [TextRenderer], never `Canvas.rotate`.
 * `onDraw` culls by the canvas clip, so under software rendering a dirty rect costs only its own
 * pixels; under HWUI (the default) the whole display list is re-recorded.
 *
 * Touch: [PlayInput] binds pointers to zones ([DrawerZones]: life halves, drawer tab/bar/header/cell
 * halves). Touch-down inverts the zone; a lift applies one step (tap); holding applies the hold step
 * after the delay and then every [PlayInput.repeatMillis]; a cancel applies nothing.
 *
 * Frames per interaction:
 * - life tap: 1 on down (inversion), 1 on up (number + readout + restore);
 * - life hold: the down frame, 1 per repeat (number + readout), 1 on up (restore);
 * - drawer tab tap: 1 on down (tab inverted), 1 on up (whole panel: drawer opens/closes, number moves);
 * - counter / tax tap: 1 on down (cell half inverted), 1 on up (cell value + restore);
 * - commander button: 1 on down (card inverted), 1 on up (every tile: mode on / off / switched);
 * - damage tap in the mode: 1 on down (half/quarter inverted), 1 on up (damage + its readout, and
 *   the target's life + readout when linked, in the same frame);
 * - chevron / header tap: 1 on down, 1 on up (drawer redrawn);
 * - readout commit (3 s idle on that seat): 1, readout rect only.
 */
@SuppressLint("ViewConstructor")
class PlayView(
    context: Context,
    private val session: GameSession,
    /** Picks the commander-mode quip; seedable for the capture harness (`--el seed N`, debug builds). */
    random: Random = Random(System.nanoTime()),
) :
    View(context), GameSession.Listener, PlayInput.Sink, PendingDeltas.Sink, PlayInput.ZoneMap,
    DrawerController.Host, SettingsHolder.Listener, CommanderMode.Host {

    private val r = DitherRenderer()
    private val text = TextRenderer(r)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val holdTimers = HandlerScheduler(mainHandler, CommanderZones.END)
    private val readoutTimers = HandlerScheduler(mainHandler, PlayLayout.PLAYERS)
    private val modeTimers = HandlerScheduler(mainHandler, 1)
    private val input = PlayInput(this, holdTimers, CommanderZones.END).also { holdTimers.target = it }
    private val pending = PendingDeltas(
        PlayLayout.PLAYERS, readoutTimers, this, session.history.coalesceWindowMillis,
    ).also { readoutTimers.target = it }

    private val drawers = DrawerState(PlayLayout.PLAYERS)
    private val drawer = DrawerController(drawers, this)
    private val painter = DrawerPainter(r, text)
    private val settings = SettingsHolder.shared
    private val mode = CommanderMode(modeTimers, this, random).also { modeTimers.target = it }

    /** Core coalesce key of each seat's open readout group when it is not a life group (damage, T30). */
    private val readoutKey = arrayOfNulls<String>(PlayLayout.PLAYERS)

    /** True while this View dispatches a commander-mode action (it attributes readouts itself). */
    private var applyingMode = false

    // Peeks (T32): per-draw scratch, sized for the longest tray.
    private val peekItems = IntArray(DrawerItems.MAX_ITEMS)
    private val peekValues = IntArray(DrawerItems.MAX_ITEMS)
    private val peekX = IntArray(DrawerItems.MAX_ITEMS + 1)
    private val label = UpperLabel()

    /** Seat whose change is being applied by this View's own tap/repeat/drawer, else -1. */
    private var applyingSeat = -1

    /** Life per hold-repeat step (`AppSettings.holdRepeatStep`, T12); ≤ 0 disables hold on life. */
    var holdStep: Int = PlayInput.DEFAULT_HOLD_STEP
        set(value) {
            field = value
            mode.holdStep = value // commander damage holds step like life (designer, T30 pass 2)
        }

    private var layout: PlayLayout? = null
    private var dl: DrawerLayout? = null
    private var cl: CommanderLayout? = null
    private var pk: PeekLayout? = null

    // Scratch, reused: no allocation in onDraw / touch paths.
    private val clipDev = Rect()
    private val clipDevI = IRect()
    private val clip = IRect()
    private val fp = IRect()
    private val dev = IRect()
    private val fb = IRect()
    private val fi = IRect()

    /** Debug (`--ez trace true`): logs every onDraw's clip and duration. Allocates; off by default. */
    var trace = false

    init {
        setWillNotDraw(false)
        isClickable = false
    }

    fun setInverted(on: Boolean) {
        if (r.inverted == on) return
        r.inverted = on
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        session.listener = this
        settings.addListener(this)
        drawer.refreshAll()
        invalidate() // state may have changed while detached
    }

    override fun onDetachedFromWindow() {
        input.cancelAll()
        pending.commitAll()
        mode.exit()
        holdTimers.cancelAll()
        readoutTimers.cancelAll()
        modeTimers.cancelAll()
        settings.removeListener(this)
        if (session.listener === this) session.listener = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val s = PlayLayout.chooseScale(w, h, resources.displayMetrics.densityDpi)
        val grid = DitherGrid(w, h, s)
        r.setGrid(grid)
        r.prewarm()
        val l = PlayLayout.compute(grid)
        val d = DrawerLayout(l)
        mode.exit()
        layout = l
        dl = d
        cl = CommanderLayout(l)
        pk = PeekLayout(l)
        input.zones = this // re-binding cancels every touch
        drawer.layout = d
        prewarmGlyphs(l, d)
        if (trace) Log.d(TAG, "layout $l\n$d\n$cl")
    }

    /**
     * Builds the renderer's scaled glyph/icon masks for everything the Play screen can show, by
     * drawing them once into a throwaway 1×1 canvas — so nothing is built or allocated in onDraw.
     */
    private fun prewarmGlyphs(l: PlayLayout, d: DrawerLayout) {
        val scratch = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        r.begin(scratch)
        val c = CommanderLayout(l)
        for (rot in ROTATIONS) {
            for (k in 1..l.kMax) {
                text.draw(l.font, WARM_CHARS, 0, 0, k, rot)
                text.draw(l.pmFont, PM_CHARS, 0, 0, k, rot)
            }
            for (k in 1..maxOf(l.readoutK, l.tk)) text.draw(l.readoutFont, WARM_CHARS, 0, 0, k, rot)
            for (k in 1..c.quipKMax) text.draw(c.ui, WARM_UI, 0, 0, k, rot)
        }
        painter.prewarm(d)
    }

    // ---- state ----------------------------------------------------------------------------

    override fun onStateChanged(old: GameState, new: GameState) {
        val l = layout ?: return
        val d = dl ?: return
        val np = new.players
        if (np.size != l.playerCount || old.players.size != np.size) {
            drawer.refreshAll()
            invalidate(); return
        }
        // A change this View didn't make (undo, restore, menu…) is "another kind of action":
        // it commits every open readout group first.
        val own = applyingSeat
        if (own < 0 && !applyingMode) pending.commitAll()
        if (mode.active) {
            invalidateMode(l, old, new)
        } else {
            val op = old.players
            for (i in np.indices) {
                if (np[i] === op[i]) continue
                if (np[i].life != op[i].life) {
                    invalidateDither(if (drawers.isOpen(i)) d.numberSlot[i] else l.numberSlot[i])
                }
                if (i == own) pending.add(i, np[i].life - op[i].life)
                // Peeks (T32): same frame as the counter change.
                if (!drawers.isOpen(i) && (np[i].counters != op[i].counters || np[i].commanderTax != op[i].commanderTax ||
                        np[i].partner != op[i].partner)
                ) invalidateDither(l.underRow[i])
            }
        }
        drawer.onStateChanged(old, new)
    }

    /** In the mode: redraw exactly the tiles whose shown damage / life / partner split changed. */
    private fun invalidateMode(l: PlayLayout, old: GameState, new: GameState) {
        val c = cl ?: return
        val t = mode.target
        val op = old.players
        val np = new.players
        if (t !in np.indices) { mode.exit(); return }
        if (np[t].life != op[t].life) invalidateDither(c.lifeRow[t])
        for (s in np.indices) {
            if (s == t) continue
            if (np[s].partner != op[s].partner) {
                invalidateDither(l.panels[s]); continue
            }
            if (mode.damage(s, Commander.PRIMARY, old) != mode.damage(s, Commander.PRIMARY, new) ||
                mode.damage(s, Commander.PARTNER, old) != mode.damage(s, Commander.PARTNER, new)
            ) invalidateDither(c.content[s])
        }
    }

    override fun onSettingsChanged(old: au.com.josephmark.dithertracker.core.serial.AppSettings, new: au.com.josephmark.dithertracker.core.serial.AppSettings) {
        if (old.drawerCounters != new.drawerCounters) {
            drawer.onCountersChanged()
            layout?.let { l -> for (s in 0 until l.playerCount) invalidateDither(l.underRow[s]) }
        }
    }

    // ---- CommanderMode.Host (T30) -------------------------------------------------------------

    override fun dispatchDamage(sourceSeat: Int, action: Action.ChangeCommanderDamage) {
        val t = mode.target
        val old = session.state
        val key = action.coalesceKey
        // One readout group per seat per commander slot: switching slot commits the seat's group.
        if (readoutKey[sourceSeat] != key) pending.commit(sourceSeat)
        applyingMode = true
        try {
            session.dispatch(action, System.currentTimeMillis(), key)
        } finally {
            applyingMode = false
        }
        val new = session.state
        if (new === old || t !in new.players.indices) return
        readoutKey[sourceSeat] = key
        pending.add(sourceSeat, mode.damage(sourceSeat, action.slot, new) - mode.damage(sourceSeat, action.slot, old))
        // Linked life loss shows on the target's own readout.
        pending.add(t, new.players[t].life - old.players[t].life)
    }

    override fun onModeChanged(old: Int, new: Int) {
        val l = layout ?: return
        // Every tile changes meaning: release held touches, close readouts and (entering) drawers.
        input.cancelAll()
        pending.commitAll()
        if (new != CommanderMode.NONE) drawer.closeAll()
        for (s in 0 until l.playerCount) invalidateDither(l.panels[s])
        invalidateDither(l.diamond) // menu bars ↔ close X
        if (trace) Log.d(TAG, "commander mode $old -> $new '${mode.quip}'")
    }

    /** Applies a life step for [seat], attributing the resulting state change to the readout. */
    private fun applyLife(seat: Int, delta: Int) {
        applyingSeat = seat
        try {
            session.changeLife(seat, delta, System.currentTimeMillis())
        } finally {
            applyingSeat = -1
        }
    }

    // ---- DrawerController.Host --------------------------------------------------------------

    override val game: GameState get() = session.state

    override val drawerCounters: List<Counter> get() = settings.current.drawerCounters

    /**
     * Drawer actions are this View's own: a life change they cause (linked commander damage) goes
     * into that seat's pending-delta readout, and they don't commit other seats' readouts.
     */
    override fun dispatch(seat: Int, action: Action) {
        applyingSeat = seat
        try {
            session.dispatch(action, System.currentTimeMillis())
        } finally {
            applyingSeat = -1
        }
    }

    override fun invalidate(rect: IRect) = invalidateDither(rect)

    override fun cancelTouches(from: Int, until: Int) = input.cancelZones(from, until)

    // ---- pending-delta readout ------------------------------------------------------------

    override fun onReadoutChanged(seat: Int) {
        val l = layout ?: return
        val d = dl ?: return
        invalidateDither(if (drawers.isOpen(seat)) d.readoutSlot[seat] else l.readoutSlot[seat])
    }

    override fun onGroupCommitted(seat: Int) {
        // Core groups undo per coalesce key (T29): close just this seat's group (life, or the
        // commander-damage group it was adjusting in the mode).
        val key = readoutKey[seat]
        if (key != null) {
            readoutKey[seat] = null
            session.endGroup(key)
        } else {
            session.endLifeGroup(seat)
        }
    }

    // ---- input ----------------------------------------------------------------------------

    override fun hitTest(gx: Int, gy: Int): Int {
        val d = dl ?: return PlayLayout.NONE
        val c = cl ?: return PlayLayout.NONE
        val z = c.hitTest(gx, gy, mode.target, session.state.players)
        return if (z != CommanderLayout.DELEGATE) z else d.hitTest(gx, gy, drawers)
    }

    override fun repeats(zone: Int): Boolean = when {
        DrawerZones.isLife(zone) -> holdStep > 0
        CommanderZones.isCommander(zone) -> mode.repeats(zone)
        else -> drawer.repeats(zone)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val l = layout ?: return true
        if (trace && event.actionMasked != MotionEvent.ACTION_MOVE) Log.d(TAG, "touch $event")
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                input.down(
                    event.getPointerId(i),
                    l.grid.toDitherX(event.getX(i).toInt()),
                    l.grid.toDitherY(event.getY(i).toInt()),
                    event.eventTime,
                )
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> input.up(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> input.cancelAll()
        }
        return true
    }

    override fun onZoneTap(zone: Int) {
        when {
            DrawerZones.isLife(zone) -> applyLife(PlayLayout.zoneSeat(zone), PlayLayout.zoneDelta(zone))
            CommanderZones.isCommander(zone) -> mode.onTap(zone)
            else -> drawer.onTap(zone)
        }
        if (trace) Log.d(TAG, "tap zone $zone")
    }

    override fun onZoneRepeat(zone: Int) {
        when {
            DrawerZones.isLife(zone) -> if (holdStep > 0) applyLife(PlayLayout.zoneSeat(zone), PlayLayout.zoneDelta(zone) * holdStep)
            CommanderZones.isCommander(zone) -> mode.onRepeat(zone)
            else -> drawer.onRepeat(zone)
        }
        if (trace) Log.d(TAG, "repeat zone $zone")
    }

    override fun onZonePressed(zone: Int) {
        if (feedbackRect(zone, fb)) invalidateDither(fb)
    }

    override fun onZoneReleased(zone: Int) {
        if (feedbackRect(zone, fb)) invalidateDither(fb)
    }

    private fun feedbackRect(zone: Int, out: IRect): Boolean {
        val d = dl ?: return false
        val c = cl ?: return false
        if (CommanderZones.isCommander(zone) && zone != CommanderZones.DIAMOND &&
            CommanderZones.localOf(zone) == CommanderZones.BUTTON
        ) {
            // The commander button lives in the open drawer's bar.
            val s = CommanderZones.seatOf(zone)
            if (!drawers.isOpen(s)) return false
            out.set(d.cmdVis[s].left, d.cmdVis[s].top, d.cmdVis[s].right, d.cmdVis[s].bottom)
            return true
        }
        return if (CommanderZones.isCommander(zone)) c.feedbackRect(zone, session.state.players, out) else d.feedbackRect(zone, drawers, out)
    }

    /** Zones whose inversion is drawn under the tabs/buttons (they cover whole halves/quarters/tiles). */
    private fun isBroadZone(zone: Int): Boolean {
        if (DrawerZones.isLife(zone)) return true
        if (!CommanderZones.isCommander(zone) || zone == CommanderZones.DIAMOND) return false
        val local = CommanderZones.localOf(zone)
        return local == CommanderZones.TARGET || CommanderZones.isSlot(local)
    }

    @Suppress("DEPRECATION")
    private fun invalidateDither(rect: IRect) {
        val l = layout ?: return
        l.grid.toDevice(rect, dev)
        invalidate(dev.left, dev.top, dev.right, dev.bottom)
    }

    // ---- drawing --------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        val d = dl ?: return
        val t0 = if (trace) SystemClock.elapsedRealtimeNanos() else 0L
        r.begin(canvas)
        if (!canvas.getClipBounds(clipDev)) return
        clipDevI.set(clipDev.left, clipDev.top, clipDev.right, clipDev.bottom)
        l.grid.toDitherCovering(clipDevI, clip)

        r.clear()
        if (hit(l.dividerV)) r.dottedV(l.dividerV.left, l.dividerV.top, l.dividerV.height)
        if (hit(l.dividerH)) r.dottedH(l.dividerH.left, l.dividerH.top, l.dividerH.width)

        val players = session.state.players
        val n = minOf(l.playerCount, players.size)
        for (s in 0 until n) {
            if (!hit(l.panels[s])) continue
            drawSeat(l, d, s, players)
        }

        // Press feedback, broad zones first (life / damage halves, the target tile): the closed tab
        // is drawn back on top of them. In commander mode there are no tabs and the diamond is a close X.
        for (z in 0 until CommanderZones.END) if (isBroadZone(z) && input.isPressed(z)) invertZone(canvas, l, d, z, players)

        if (!mode.active) for (s in 0 until n) if (!drawers.isOpen(s) && hit(l.tab[s])) drawTab(l, s)
        if (hit(l.diamond)) drawDiamond(l)

        for (z in DrawerZones.LIFE until CommanderZones.END) if (!isBroadZone(z) && input.isPressed(z)) invertZone(canvas, l, d, z, players)

        if (trace) {
            val us = (SystemClock.elapsedRealtimeNanos() - t0) / 1000
            Log.d(TAG, "onDraw hw=${canvas.isHardwareAccelerated} clip=$clipDev took ${us}us")
        }
    }

    private fun invertZone(canvas: Canvas, l: PlayLayout, d: DrawerLayout, z: Int, players: List<Player>) {
        if (!feedbackRect(z, fb) || !hit(fb)) return
        if (DitherRenderer.canInvertInPlace) {
            r.invertRect(fb)
            return
        }
        // API < 29: redraw the seat inverted, clipped to the rect.
        if (z == CommanderZones.DIAMOND) return
        val s = if (CommanderZones.isCommander(z)) CommanderZones.seatOf(z) else DrawerZones.seatOf(z)
        l.grid.toDevice(fb, dev)
        canvas.save()
        canvas.clipRect(dev.left, dev.top, dev.right, dev.bottom)
        r.withInverted {
            r.clear()
            drawSeat(l, d, s, players)
            if (!drawers.isOpen(s) && !mode.active) drawTab(l, s)
        }
        canvas.restore()
    }

    private fun drawSeat(l: PlayLayout, d: DrawerLayout, s: Int, players: List<Player>) {
        val p = players[s]
        val rot = l.rotation[s]
        val f = l.font
        val open = drawers.isOpen(s)
        if (mode.active && !open) {
            drawSeatInMode(l, s, players)
            drawReadout(l, d, s, open = false)
            return
        }
        val pmK = if (open) PlayLayout.pmK(d.numberK) else l.pmK
        val minus = if (open) d.minusGlyph[s] else l.minusGlyph[s]
        val plus = if (open) d.plusGlyph[s] else l.plusGlyph[s]
        if (l.pmVisible) {
            if (hit(minus)) text.draw(l.pmFont, MINUS, minus.left, minus.top, pmK, rot)
            if (hit(plus)) text.draw(l.pmFont, PLUS, plus.left, plus.top, pmK, rot)
        }
        if (hit(if (open) d.numberSlot[s] else l.numberSlot[s])) {
            val k = if (open) d.numberFootprint(s, p.life, fp) else l.numberFootprint(s, p.life, fp)
            text.drawInt(f, p.life, fp.left, fp.top, k, rot)
        }
        drawReadout(l, d, s, open)
        if (open) painter.draw(d, drawer, s, players, clip) else drawPeeks(l, s, p)
    }

    private fun drawReadout(l: PlayLayout, d: DrawerLayout, s: Int, open: Boolean) {
        val delta = pending.delta(s)
        if (delta != 0 && hit(if (open) d.readoutSlot[s] else l.readoutSlot[s])) {
            val k = if (open) d.readoutFootprint(s, delta, fp) else l.readoutFootprint(s, delta, fp)
            text.drawInt(l.readoutFont, delta, fp.left, fp.top, k, l.rotation[s], signed = true)
        }
    }

    // ---- commander-damage mode (T30) ------------------------------------------------------------

    private fun drawSeatInMode(l: PlayLayout, s: Int, players: List<Player>) {
        val c = cl ?: return
        val t = mode.target
        val rot = l.rotation[s]
        if (t !in players.indices) return
        if (s == t) {
            // The target: one composed block — the quip in caps, then the life on paper.
            val q = mode.quip
            if (hit(c.lifeRow[s])) {
                label.set("", q, q.length)
                val k = c.quipFootprint(s, label.length, fp)
                text.draw(c.ui, label, fp.left, fp.top, k, rot)
                val life = players[s].life
                val lk = c.lifeFootprint(s, label.length, life, fi, fp)
                painter.icon(c.tk, Icon.LIFE, fi, rot, invert = false, opaque = false)
                text.drawInt(c.lifeFont, life, fp.left, fp.top, lk, rot)
            }
            return
        }
        // Opponents: just the damage number(s) and ± (partner tiles: a small icon per commander).
        val src = players[s]
        if (!src.partner) {
            val v = mode.damage(s, Commander.PRIMARY)
            if (l.pmVisible) {
                if (hit(l.minusGlyph[s])) text.draw(l.pmFont, MINUS, l.minusGlyph[s].left, l.minusGlyph[s].top, l.pmK, rot)
                if (hit(l.plusGlyph[s])) text.draw(l.pmFont, PLUS, l.plusGlyph[s].left, l.plusGlyph[s].top, l.pmK, rot)
            }
            if (hit(l.numberSlot[s])) {
                val k = l.numberFootprint(s, v, fp)
                drawDamage(c, v, k, rot)
            }
        } else {
            for (slot in 0..1) {
                val v = mode.damage(s, slot)
                if (hit(c.content[s])) {
                    val k = c.partnerNumberFootprint(s, slot, v, fp)
                    drawDamage(c, v, k, rot)
                    if (c.partnerPmClear(v)) {
                        val m = c.partnerMinus[s * 2 + slot]
                        val pl = c.partnerPlus[s * 2 + slot]
                        text.draw(l.pmFont, MINUS, m.left, m.top, l.pmK, rot)
                        text.draw(l.pmFont, PLUS, pl.left, pl.top, l.pmK, rot)
                    }
                }
            }
            if (l.underH > 0 && hit(l.underRow[s])) {
                painter.icon(c.tk, Icon.COMMANDER, c.partnerIcon[s * 2], rot, invert = false, opaque = false)
                painter.icon(c.tk, Icon.PARTNER, c.partnerIcon[s * 2 + 1], rot, invert = false, opaque = false)
            }
        }
    }

    /** A damage number at footprint [fp]: lethal (≥ 21) on a solid ink card, in paper. */
    private fun drawDamage(c: CommanderLayout, v: Int, k: Int, rot: Int) {
        val f = c.play.font
        val lethal = v >= Limits.LETHAL_COMMANDER_DAMAGE
        if (lethal) {
            val pad = c.lethalPad
            r.rect(fp.left - pad, fp.top - pad, fp.width + 2 * pad, fp.height + 2 * pad, ink = true)
        }
        text.drawInt(f, v, fp.left, fp.top, k, rot, invert = lethal)
    }

    // ---- counter peeks (T32) --------------------------------------------------------------------

    private fun drawPeeks(l: PlayLayout, s: Int, p: Player) {
        val k = pk ?: return
        if (!k.enabled || !hit(l.underRow[s])) return
        val all = DrawerItems.tray(p, settings.current.drawerCounters, peekItems)
        var n = 0
        for (i in 0 until all) {
            val v = DrawerItems.value(peekItems[i], p)
            if (v == 0) continue
            peekItems[n] = peekItems[i]
            peekValues[n] = v
            n++
        }
        if (n == 0) return
        val shown = k.arrange(peekValues, n, peekX)
        val rot = l.rotation[s]
        for (i in 0 until shown) {
            val v = peekValues[i]
            val warn = DrawerItems.isWarning(peekItems[i], v)
            if (warn) {
                k.chip(s, peekX[i], k.chipWidth(v), fb)
                r.rect(fb, ink = true)
            }
            k.icon(s, peekX[i], fi)
            painter.icon(k.tk, DrawerPainter.iconFor(peekItems[i]), fi, rot, invert = warn, opaque = false)
            k.value(s, peekX[i], v, fp)
            text.drawInt(k.num, v, fp.left, fp.top, k.tk, rot, invert = warn)
        }
        if (shown < n) {
            k.more(s, peekX[shown], n - shown, fp)
            text.drawInt(k.num, n - shown, fp.left, fp.top, k.tk, rot, signed = true)
        }
    }

    private fun drawTab(l: PlayLayout, s: Int) {
        r.fill(Role.TAB, l.tab[s])
        r.rect(l.tabDotsBacking[s], ink = false) // paper backing
        for (i in 0 until 3) r.rect(l.tabDots[s * 3 + i], ink = true)
    }

    private fun drawDiamond(l: PlayLayout) {
        val cx = l.diamondCx
        val cy = l.diamondCy
        for (dy in -l.diamondHalf..l.diamondHalf) {
            val hw = l.diamondRowHalf(dy)
            r.fill(Role.MENU, cx - hw, cy + dy, 2 * hw + 1, 1)
        }
        val c = cl
        if (mode.active && c != null) {
            // Commander mode: the menu gives way to a close X (opaque, on its paper square).
            painter.icon(c.tk, Icon.CLOSE, c.closeIcon, 0, invert = false, opaque = true)
            return
        }
        r.rect(l.menuBarsBacking, ink = false)
        for (b in l.menuBars) r.rect(b, ink = true)
    }

    private fun hit(rect: IRect): Boolean =
        rect.left < clip.right && clip.left < rect.right && rect.top < clip.bottom && clip.top < rect.bottom

    companion object {
        private const val TAG = "PlayView"
        private const val MINUS = "-"
        private const val PLUS = "+"
        private const val WARM_CHARS = "0123456789-+−"
        private const val WARM_UI = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 '.,-&"
        private const val PM_CHARS = "-+"
        private val ROTATIONS = intArrayOf(0, 180)
    }
}
