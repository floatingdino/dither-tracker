package au.com.josephmark.dithertracker.play

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.TextRenderer

/**
 * The 4-player Play screen: one opaque custom View, all geometry from [PlayLayout].
 *
 * Drawing: everything goes through [DitherRenderer] in root coordinates (this View fills the
 * window at (0,0)); rotated seats use blit rotation via [TextRenderer], never `Canvas.rotate`.
 * `onDraw` culls by the canvas clip, so under software rendering a dirty rect costs only its own
 * pixels; under HWUI (the default) the clip is the whole view and the whole display list (~60 ops)
 * is re-recorded — measured on the Nomad, see the T06 report.
 *
 * Invalidation: a life change invalidates only that seat's number slot; a press/release only the
 * pressed zone. Seats are diffed by `Player` reference. One frame per user action: the life step
 * and the press inversion are invalidated in the same event, so they land in the same frame.
 */
@SuppressLint("ViewConstructor")
class PlayView(context: Context, private val session: GameSession) :
    View(context), GameSession.Listener, PlayInput.Sink {

    private val r = DitherRenderer()
    private val text = TextRenderer(r)
    private val input = PlayInput(this)

    private var layout: PlayLayout? = null

    // Scratch, reused: no allocation in onDraw / touch paths.
    private val clipDev = Rect()
    private val clipDevI = IRect()
    private val clip = IRect()
    private val fp = IRect()
    private val dev = IRect()

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
        invalidate() // state may have changed while detached
    }

    override fun onDetachedFromWindow() {
        if (session.listener === this) session.listener = null
        input.cancelAll()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val s = PlayLayout.chooseScale(w, h, resources.displayMetrics.densityDpi)
        val grid = DitherGrid(w, h, s)
        r.setGrid(grid)
        r.prewarm()
        val l = PlayLayout.compute(grid)
        layout = l
        input.layout = l
        prewarmGlyphs(l)
        if (trace) Log.d(TAG, "layout $l")
    }

    /**
     * Builds the renderer's scaled glyph masks for every character the Play screen can show, at every
     * k it can use, by drawing them once into a throwaway 1×1 canvas — so the first time a digit
     * appears mid-game, onDraw doesn't allocate or build a bitmap.
     */
    private fun prewarmGlyphs(l: PlayLayout) {
        val scratch = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        r.begin(scratch)
        for (k in 1..l.kMax) text.draw(l.font, WARM_CHARS, 0, 0, k)
        text.draw(l.font, WARM_CHARS, 0, 0, l.pmK)
    }

    // ---- state ----------------------------------------------------------------------------

    override fun onStateChanged(old: GameState, new: GameState) {
        val l = layout ?: return
        val np = new.players
        if (np.size != l.playerCount || old.players.size != np.size) {
            invalidate(); return
        }
        for (i in np.indices) {
            if (np[i] !== old.players[i]) invalidateDither(l.numberSlot[i])
        }
    }

    // ---- input ----------------------------------------------------------------------------

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

    override fun onZoneTap(zone: Int, eventTimeMillis: Long) {
        session.changeLife(PlayLayout.zoneSeat(zone), PlayLayout.zoneDelta(zone), System.currentTimeMillis())
        if (trace) Log.d(TAG, "tap zone $zone → seat ${PlayLayout.zoneSeat(zone)} life ${session.state.players[PlayLayout.zoneSeat(zone)].life}")
    }

    override fun onZonePressed(zone: Int) {
        layout?.let { invalidateDither(it.zones[zone]) }
    }

    override fun onZoneReleased(zone: Int) {
        layout?.let { invalidateDither(it.zones[zone]) }
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
        val t0 = if (trace) SystemClock.elapsedRealtimeNanos() else 0L
        r.begin(canvas)
        if (!canvas.getClipBounds(clipDev)) return
        clipDevI.set(clipDev.left, clipDev.top, clipDev.right, clipDev.bottom)
        l.grid.toDitherCovering(clipDevI, clip)

        r.clear()
        if (hit(l.dividerV)) r.dottedV(l.dividerV.left, l.dividerV.top, l.dividerV.height)
        if (hit(l.dividerH)) r.dottedH(l.dividerH.left, l.dividerH.top, l.dividerH.width)

        val players = session.state.players
        for (s in 0 until minOf(l.playerCount, players.size)) {
            if (!hit(l.panels[s])) continue
            drawSeat(l, s, players[s])
        }

        // Press feedback: invert the pressed half-panels, then put the static chrome back on top.
        for (z in l.zones.indices) {
            if (!input.isPressed(z) || !hit(l.zones[z])) continue
            if (DitherRenderer.canInvertInPlace) {
                r.invertRect(l.zones[z])
            } else {
                // API < 29: redraw the seat inverted, clipped to the zone.
                l.grid.toDevice(l.zones[z], dev)
                canvas.save()
                canvas.clipRect(dev.left, dev.top, dev.right, dev.bottom)
                r.withInverted { r.clear(); drawSeat(l, PlayLayout.zoneSeat(z), players[PlayLayout.zoneSeat(z)]) }
                canvas.restore()
            }
        }

        for (s in 0 until l.playerCount) if (hit(l.tab[s])) drawTab(l, s)
        if (hit(l.diamond)) drawDiamond(l)

        if (trace) {
            val us = (SystemClock.elapsedRealtimeNanos() - t0) / 1000
            Log.d(TAG, "onDraw hw=${canvas.isHardwareAccelerated} clip=$clipDev took ${us}us")
        }
    }

    private fun drawSeat(l: PlayLayout, s: Int, p: Player) {
        val rot = l.rotation[s]
        val f = l.font
        if (hit(l.minusGlyph[s])) text.draw(f, MINUS, l.minusGlyph[s].left, l.minusGlyph[s].top, l.pmK, rot)
        if (hit(l.plusGlyph[s])) text.draw(f, PLUS, l.plusGlyph[s].left, l.plusGlyph[s].top, l.pmK, rot)
        if (hit(l.numberSlot[s])) {
            val k = l.numberFootprint(s, p.life, fp)
            text.drawInt(f, p.life, fp.left, fp.top, k, rot)
        }
    }

    private fun drawTab(l: PlayLayout, s: Int) {
        r.fill(Role.TAB, l.tab[s])
        for (i in 0 until 3) {
            val d = l.tabDots[s * 3 + i]
            r.rect(d.left - 1, d.top - 1, d.width + 2, d.height + 2, ink = false) // paper halo
            r.rect(d, ink = true)
        }
    }

    private fun drawDiamond(l: PlayLayout) {
        val cx = l.diamondCx
        val cy = l.diamondCy
        for (dy in -l.diamondHalf..l.diamondHalf) {
            val hw = l.diamondRowHalf(dy)
            r.fill(Role.MENU, cx - hw, cy + dy, 2 * hw + 1, 1)
        }
        for (b in l.menuBars) r.rect(b.left - 1, b.top - 1, b.width + 2, b.height + 2, ink = false)
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
