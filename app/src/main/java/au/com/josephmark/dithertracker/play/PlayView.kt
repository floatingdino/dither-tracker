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
import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.data.SettingsHolder
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.drawer.DrawerController
import au.com.josephmark.dithertracker.drawer.DrawerLayout
import au.com.josephmark.dithertracker.drawer.DrawerPainter
import au.com.josephmark.dithertracker.drawer.DrawerState
import au.com.josephmark.dithertracker.drawer.DrawerZones
import au.com.josephmark.dithertracker.type.TextRenderer

/**
 * The 4-player Play screen: one opaque custom View, geometry from [PlayLayout] and (T08)
 * [DrawerLayout]; drawer behaviour in [DrawerController], drawer drawing in [DrawerPainter].
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
 * - counter / damage / tax tap: 1 on down (cell half inverted), 1 on up (cell value + restore; with
 *   linked commander damage also the life total and readout in the same frame);
 * - chevron / header tap: 1 on down, 1 on up (drawer redrawn);
 * - readout commit (3 s idle on that seat): 1, readout rect only.
 */
@SuppressLint("ViewConstructor")
class PlayView(context: Context, private val session: GameSession) :
    View(context), GameSession.Listener, PlayInput.Sink, PendingDeltas.Sink, PlayInput.ZoneMap,
    DrawerController.Host, SettingsHolder.Listener {

    private val r = DitherRenderer()
    private val text = TextRenderer(r)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val holdTimers = HandlerScheduler(mainHandler, DrawerZones.COUNT)
    private val readoutTimers = HandlerScheduler(mainHandler, PlayLayout.PLAYERS)
    private val input = PlayInput(this, holdTimers, DrawerZones.COUNT).also { holdTimers.target = it }
    private val pending = PendingDeltas(
        PlayLayout.PLAYERS, readoutTimers, this, session.history.coalesceWindowMillis,
    ).also { readoutTimers.target = it }

    private val drawers = DrawerState(PlayLayout.PLAYERS)
    private val drawer = DrawerController(drawers, this)
    private val painter = DrawerPainter(r, text)
    private val settings = SettingsHolder.shared

    /** Seat whose change is being applied by this View's own tap/repeat/drawer, else -1. */
    private var applyingSeat = -1

    /** Life per hold-repeat step (`AppSettings.holdRepeatStep`, T12); ≤ 0 disables hold on life. */
    var holdStep: Int = PlayInput.DEFAULT_HOLD_STEP

    private var layout: PlayLayout? = null
    private var dl: DrawerLayout? = null

    // Scratch, reused: no allocation in onDraw / touch paths.
    private val clipDev = Rect()
    private val clipDevI = IRect()
    private val clip = IRect()
    private val fp = IRect()
    private val dev = IRect()
    private val fb = IRect()

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
        holdTimers.cancelAll()
        readoutTimers.cancelAll()
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
        layout = l
        dl = d
        input.zones = this // re-binding cancels every touch
        drawer.layout = d
        prewarmGlyphs(l, d)
        if (trace) Log.d(TAG, "layout $l\n$d")
    }

    /**
     * Builds the renderer's scaled glyph/icon masks for everything the Play screen can show, by
     * drawing them once into a throwaway 1×1 canvas — so nothing is built or allocated in onDraw.
     */
    private fun prewarmGlyphs(l: PlayLayout, d: DrawerLayout) {
        val scratch = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        r.begin(scratch)
        for (k in 1..l.kMax) text.draw(l.font, WARM_CHARS, 0, 0, k)
        text.draw(l.font, WARM_CHARS, 0, 0, l.pmK)
        text.draw(l.font, WARM_CHARS, 0, 0, PlayLayout.pmK(d.numberK))
        for (k in 1..l.readoutK) text.draw(l.readoutFont, WARM_CHARS, 0, 0, k)
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
        if (own < 0) pending.commitAll()
        for (i in np.indices) {
            if (np[i] === old.players[i]) continue
            if (np[i].life != old.players[i].life) {
                invalidateDither(if (drawers.isOpen(i)) d.numberSlot[i] else l.numberSlot[i])
            }
            if (i == own) pending.add(i, np[i].life - old.players[i].life)
        }
        drawer.onStateChanged(old, new)
    }

    override fun onSettingsChanged(old: au.com.josephmark.dithertracker.core.serial.AppSettings, new: au.com.josephmark.dithertracker.core.serial.AppSettings) {
        if (old.drawerCounters != new.drawerCounters) drawer.onCountersChanged()
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
        // Core groups undo per coalesce key (T29): close just this seat's life group.
        session.endLifeGroup(seat)
    }

    // ---- input ----------------------------------------------------------------------------

    override fun hitTest(gx: Int, gy: Int): Int = dl?.hitTest(gx, gy, drawers) ?: PlayLayout.NONE

    override fun repeats(zone: Int): Boolean =
        if (DrawerZones.isLife(zone)) holdStep > 0 else drawer.repeats(zone)

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
        if (DrawerZones.isLife(zone)) {
            applyLife(PlayLayout.zoneSeat(zone), PlayLayout.zoneDelta(zone))
        } else {
            drawer.onTap(zone)
        }
        if (trace) Log.d(TAG, "tap zone $zone")
    }

    override fun onZoneRepeat(zone: Int) {
        if (DrawerZones.isLife(zone)) {
            if (holdStep > 0) applyLife(PlayLayout.zoneSeat(zone), PlayLayout.zoneDelta(zone) * holdStep)
        } else {
            drawer.onRepeat(zone)
        }
        if (trace) Log.d(TAG, "repeat zone $zone")
    }

    override fun onZonePressed(zone: Int) {
        val d = dl ?: return
        if (d.feedbackRect(zone, drawers, fb)) invalidateDither(fb)
    }

    override fun onZoneReleased(zone: Int) {
        val d = dl ?: return
        if (d.feedbackRect(zone, drawers, fb)) invalidateDither(fb)
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

        // Press feedback, life halves first (the closed tab is drawn back on top of them).
        for (z in 0 until DrawerZones.LIFE) if (input.isPressed(z)) invertZone(canvas, l, d, z, players)

        for (s in 0 until n) if (!drawers.isOpen(s) && hit(l.tab[s])) drawTab(l, s)
        if (hit(l.diamond)) drawDiamond(l)

        for (z in DrawerZones.LIFE until DrawerZones.COUNT) if (input.isPressed(z)) invertZone(canvas, l, d, z, players)

        if (trace) {
            val us = (SystemClock.elapsedRealtimeNanos() - t0) / 1000
            Log.d(TAG, "onDraw hw=${canvas.isHardwareAccelerated} clip=$clipDev took ${us}us")
        }
    }

    private fun invertZone(canvas: Canvas, l: PlayLayout, d: DrawerLayout, z: Int, players: List<Player>) {
        if (!d.feedbackRect(z, drawers, fb) || !hit(fb)) return
        if (DitherRenderer.canInvertInPlace) {
            r.invertRect(fb)
            return
        }
        // API < 29: redraw the seat inverted, clipped to the rect.
        val s = DrawerZones.seatOf(z)
        l.grid.toDevice(fb, dev)
        canvas.save()
        canvas.clipRect(dev.left, dev.top, dev.right, dev.bottom)
        r.withInverted {
            r.clear()
            drawSeat(l, d, s, players)
            if (!drawers.isOpen(s)) drawTab(l, s)
        }
        canvas.restore()
    }

    private fun drawSeat(l: PlayLayout, d: DrawerLayout, s: Int, players: List<Player>) {
        val p = players[s]
        val rot = l.rotation[s]
        val f = l.font
        val open = drawers.isOpen(s)
        val pmK = if (open) PlayLayout.pmK(d.numberK) else l.pmK
        val minus = if (open) d.minusGlyph[s] else l.minusGlyph[s]
        val plus = if (open) d.plusGlyph[s] else l.plusGlyph[s]
        if (hit(minus)) text.draw(f, MINUS, minus.left, minus.top, pmK, rot)
        if (hit(plus)) text.draw(f, PLUS, plus.left, plus.top, pmK, rot)
        if (hit(if (open) d.numberSlot[s] else l.numberSlot[s])) {
            val k = if (open) d.numberFootprint(s, p.life, fp) else l.numberFootprint(s, p.life, fp)
            text.drawInt(f, p.life, fp.left, fp.top, k, rot)
        }
        val delta = pending.delta(s)
        if (delta != 0 && hit(if (open) d.readoutSlot[s] else l.readoutSlot[s])) {
            val k = if (open) d.readoutFootprint(s, delta, fp) else l.readoutFootprint(s, delta, fp)
            text.drawInt(l.readoutFont, delta, fp.left, fp.top, k, rot, signed = true)
        }
        if (open) painter.draw(d, drawer, s, players, clip)
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
        r.rect(l.menuBarsBacking, ink = false)
        for (b in l.menuBars) r.rect(b, ink = true)
    }

    private fun hit(rect: IRect): Boolean =
        rect.left < clip.right && clip.left < rect.right && rect.top < clip.bottom && clip.top < rect.bottom

    companion object {
        private const val TAG = "PlayView"
        private const val MINUS = "-"
        private const val PLUS = "+"
        private const val WARM_CHARS = "0123456789-+"
    }
}
